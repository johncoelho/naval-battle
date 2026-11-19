// Lógica pura da compra de dobrões no iPhone (#65), sem rede nem Deno.env: montar o JWT da
// App Store Server API, ler o JWS, decidir o que a transação vale e se o app pode finalizá-la.
// Fica num módulo à parte para o `deno test` cobrir (appstore_test.ts). As Edges
// `dobroes-verify-ios` e `apple-iap-notifications` só ligam isto ao banco e à rede.
//
// Decisão (HISTORY): a cadeia de certificados x5c do JWS NÃO é validada. A verdade vem da
// chamada autenticada à App Store Server API (TLS com a Apple + JWT assinado com a nossa chave);
// o JWS que chega do aparelho ou do POST da notificação só serve para ler o transactionId.
//
// Nada aqui lê segredo nem escreve em log: quem chama passa o que precisa e decide o que registrar.

export const BUNDLE_ID = "br.com.navalbattle";
export const PRODUCTS = new Set(["dobroes_bolsa", "dobroes_bau", "dobroes_cofre", "dobroes_tesouro"]);

export const HOSTS = {
  Production: "https://api.storekit.itunes.apple.com",
  Sandbox: "https://api.storekit-sandbox.itunes.apple.com",
} as const;
export type Ambiente = keyof typeof HOSTS;

// ------------------------------------------------------------------ base64url e JWS

export function b64urlEncode(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64urlDecode(s: string): Uint8Array {
  let b = s.replace(/-/g, "+").replace(/_/g, "/").replace(/\s+/g, "");
  while (b.length % 4) b += "=";
  return Uint8Array.from(atob(b), (c) => c.charCodeAt(0));
}

// Lê o corpo (payload) de um JWS SEM conferir a assinatura. Nunca confie no resultado: serve só
// de dica (transactionId). Devolve null para qualquer coisa malformada.
export function decodeJwsPayload(jws: unknown): Record<string, unknown> | null {
  if (typeof jws !== "string" || jws.length > 16384) return null;
  const partes = jws.split(".");
  if (partes.length !== 3 || !partes[1]) return null;
  try {
    const obj = JSON.parse(new TextDecoder().decode(b64urlDecode(partes[1])));
    return obj && typeof obj === "object" && !Array.isArray(obj) ? obj as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

// transactionId da Apple: só dígitos (cabe como texto; evita injetar caminho na URL da API)
export function parseTransactionId(v: unknown): string | null {
  const s = typeof v === "number" && Number.isSafeInteger(v) ? String(v) : v;
  return typeof s === "string" && /^[0-9]{1,20}$/.test(s) ? s : null;
}

// ------------------------------------------------------------------ JWT da App Store Server API

// Chave .p8 (PKCS8, PEM) -> chave de assinatura ES256. Aceita "\n" literal (segredo em uma linha).
export async function importP8(p8: string): Promise<CryptoKey> {
  const pem = p8.replace(/\\n/g, "\n").replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey("pkcs8", der, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
}

// JWT ES256 da App Store Server API. O WebCrypto já devolve a assinatura em r||s, o formato do JWT.
// A Apple aceita exp até 60 min; usamos 20.
export async function buildApiJwt(
  key: CryptoKey,
  p: { keyId: string; issuerId: string; bundleId?: string; now: number },
): Promise<string> {
  const header = b64urlEncode(JSON.stringify({ alg: "ES256", kid: p.keyId, typ: "JWT" }));
  const claims = b64urlEncode(JSON.stringify({
    iss: p.issuerId,
    iat: p.now,
    exp: p.now + 20 * 60,
    aud: "appstoreconnect-v1",
    bid: p.bundleId ?? BUNDLE_ID,
  }));
  const sig = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  return `${header}.${claims}.${b64urlEncode(sig)}`;
}

// ------------------------------------------------------------------ Get Transaction Info

export type Consulta =
  | { tipo: "ok"; ambiente: Ambiente; tx: Record<string, unknown> }
  | { tipo: "nao_achou" }
  | { tipo: "erro"; etapa: "rede" | "apple_api" | "resposta" | "ambiente_divergente"; status?: number };

// Consulta a transação na Production e, se a Apple disser 404 (TransactionIdNotFound), na Sandbox.
// O ambiente vem do host que respondeu e é cruzado com o campo `environment` do JWS devolvido.
// O resultado leva só etapa e status HTTP (nunca corpo, JWS nem token): quem chama registra.
export async function consultarTransacao(
  transactionId: string,
  jwt: string,
  fetchFn: typeof fetch,
): Promise<Consulta> {
  for (const ambiente of ["Production", "Sandbox"] as Ambiente[]) {
    let res: Response;
    try {
      res = await fetchFn(`${HOSTS[ambiente]}/inApps/v1/transactions/${transactionId}`, {
        headers: { Authorization: `Bearer ${jwt}` },
      });
    } catch {
      return { tipo: "erro", etapa: "rede" };
    }
    if (res.status === 404) continue;
    if (!res.ok) return { tipo: "erro", etapa: "apple_api", status: res.status };
    let tx: Record<string, unknown> | null = null;
    try {
      tx = decodeJwsPayload((await res.json())?.signedTransactionInfo);
    } catch {
      tx = null;
    }
    if (!tx) return { tipo: "erro", etapa: "resposta" };
    if (tx.environment !== undefined && tx.environment !== ambiente) {
      return { tipo: "erro", etapa: "ambiente_divergente" };
    }
    return { tipo: "ok", ambiente, tx };
  }
  return { tipo: "nao_achou" };
}

// ------------------------------------------------------------------ o que a transação vale

export type Avaliacao =
  | { ok: true; revogada: boolean; contaOk: boolean; produto: string; transactionId: string }
  | { ok: false; motivo: "bundle" | "produto" | "tipo" | "transacao" };

// Confere a transação que a Apple devolveu contra o que o app disse e o JWT do jogador.
// `esperado.produto` pode ser null (notificação: o produto vem da própria transação).
export function avaliarTransacao(
  tx: Record<string, unknown>,
  esperado: { userId: string; transactionId: string; produto: string | null },
): Avaliacao {
  if (tx.bundleId !== BUNDLE_ID) return { ok: false, motivo: "bundle" };
  if (parseTransactionId(tx.transactionId) !== esperado.transactionId) return { ok: false, motivo: "transacao" };
  const produto = tx.productId;
  if (typeof produto !== "string" || !PRODUCTS.has(produto)) return { ok: false, motivo: "produto" };
  if (esperado.produto !== null && produto !== esperado.produto) return { ok: false, motivo: "produto" };
  if (tx.type !== "Consumable") return { ok: false, motivo: "tipo" };
  const token = typeof tx.appAccountToken === "string" ? tx.appAccountToken.toLowerCase() : null;
  return {
    ok: true,
    revogada: tx.revocationDate !== undefined && tx.revocationDate !== null,
    contaOk: token !== null && token === esperado.userId.toLowerCase(),
    produto,
    transactionId: esperado.transactionId,
  };
}

// Status do RPC em que a transação é do servidor e o app pode chamar `transaction.finish()`.
// Os outros (billing_off, account_mismatch, unknown_product, not_found) ficam sem finalizar:
// a Apple devolve a transação no próximo início e ela entra quando a causa passar.
//  - sandbox_refused finaliza de propósito: senão volta para sempre no Transaction.updates.
//  - not_purchased (revogada) finaliza: é terminal, não há o que entregar.
const FINALIZA = new Set(["credited", "already", "sandbox_refused", "not_purchased"]);
export const deveFinalizar = (status: string): boolean => FINALIZA.has(status);

// ------------------------------------------------------------------ notificações V2

export type AcaoNotificacao = "estorno" | "teste" | "ignorar";

export function acaoDaNotificacao(notificationType: unknown): AcaoNotificacao {
  if (notificationType === "REFUND" || notificationType === "REVOKE") return "estorno";
  if (notificationType === "TEST") return "teste";
  return "ignorar";
}

// Dicas (não confiáveis) extraídas do POST da Apple: tipo e transactionId. Quem decide é a API.
export function lerNotificacao(
  signedPayload: unknown,
): { tipo: unknown; transactionId: string | null } | null {
  const p = decodeJwsPayload(signedPayload);
  if (!p) return null;
  const data = p.data && typeof p.data === "object" ? p.data as Record<string, unknown> : {};
  const info = decodeJwsPayload(data.signedTransactionInfo);
  return { tipo: p.notificationType, transactionId: parseTransactionId(info?.transactionId) };
}
