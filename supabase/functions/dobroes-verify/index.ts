// Confere uma compra de dobrões feita na Google Play (#15) e manda o resultado para
// o banco (RPC apply_dobroes_purchase, supabase/dobroes.sql). O servidor decide o que
// a compra vale; o aparelho só soma o que dobroes_pending devolver.
//
// Chamada pelo app do jogador: Authorization = JWT do jogador (verify_jwt ligado).
// Entrada: { product_id, purchase_token }. O usuário vem do JWT, nunca do corpo.
//
// Segredo necessário: PLAY_BILLING_SERVICE_ACCOUNT = JSON da conta de serviço
// dedicada a billing (menor privilégio: ver informações financeiras e gerenciar
// pedidos). Nunca escreva o valor em arquivo, log ou resposta.
//
// Falha registrada (#27): uma linha JSON em Logs da Edge com evento e etapa
// (secret_ausente, secret_invalido, chave_privada, oauth, rede, compra_api) e um
// motivo fixo ou o status HTTP. Nunca segredo, chave, token de acesso, token da
// compra, corpo de resposta da Google nem mensagem de erro (podem trazer a URL).
import { createClient } from "jsr:@supabase/supabase-js@2";

const PACKAGE = "aigamesfactory.navalbattleclassic";
const PRODUCTS = new Set(["dobroes_bolsa", "dobroes_bau", "dobroes_cofre", "dobroes_tesouro"]);
// status em que a compra é dela: a Edge consome na Play (senão a Play devolve em 3 dias)
const CONSUME = new Set(["credited", "premio", "already", "premio_done", "not_beta"]);

const sb = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

type ServiceAccount = { client_email: string; private_key: string };

let cached: { token: string; exp: number } | null = null;

// uma linha JSON por falha; só recebe etapa e dados já seguros (status, nome do erro)
function registrar(etapa: string, extra: Record<string, string | number | undefined> = {}) {
  console.error(JSON.stringify({ evento: "dobroes_verify_falha", etapa, ...extra }));
}

// só o nome da classe do erro (ex.: TypeError), nunca a mensagem
const nomeErro = (err: unknown): string => (err instanceof Error ? err.name : "desconhecido");

function b64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// token de acesso da API Android Publisher (escopo androidpublisher), com cache
async function playAccessToken(): Promise<string> {
  const raw = Deno.env.get("PLAY_BILLING_SERVICE_ACCOUNT");
  if (!raw) {
    registrar("secret_ausente");
    throw new Error("play_unavailable");
  }
  let sa: ServiceAccount;
  try {
    sa = JSON.parse(raw) as ServiceAccount;
  } catch {
    // sem err.message: o SyntaxError do JSON.parse pode trazer trecho do próprio secret
    registrar("secret_invalido");
    throw new Error("play_unavailable");
  }
  if (typeof sa?.client_email !== "string" || typeof sa?.private_key !== "string") {
    registrar("secret_invalido");
    throw new Error("play_unavailable");
  }
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  let signature: Uint8Array;
  try {
    const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
    const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    const key = await crypto.subtle.importKey(
      "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
    );
    signature = new Uint8Array(
      await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)),
    );
  } catch (err) {
    registrar("chave_privada", { erro: nomeErro(err) });
    throw new Error("play_unavailable");
  }
  let res: Response;
  try {
    res = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: `${header}.${claims}.${b64url(signature)}`,
      }),
    });
  } catch (err) {
    registrar("rede", { origem: "oauth", erro: nomeErro(err) });
    throw new Error("play_unavailable");
  }
  let dados: { access_token?: string; expires_in?: number } = {};
  try {
    dados = await res.json();
  } catch {
    // corpo que não é JSON: cai no registro de oauth abaixo, só com o status
  }
  if (!dados.access_token) {
    // só o status HTTP; o corpo da Google não entra no log
    registrar("oauth", { status: res.status });
    throw new Error("play_unavailable");
  }
  cached = { token: dados.access_token, exp: now + (dados.expires_in ?? 3600) };
  return cached.token;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);

  // quem está comprando: o JWT do jogador
  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: who, error: whoErr } = await sb.auth.getUser(jwt);
  if (whoErr || !who?.user) return json({ error: "auth" }, 401);
  const userId = who.user.id;

  const body = await req.json().catch(() => null);
  const productId = body?.product_id;
  const token = body?.purchase_token;
  if (!PRODUCTS.has(productId) || typeof token !== "string" || token.length < 10 || token.length > 4096) {
    return json({ error: "bad_request" }, 400);
  }
  // origem opcional (#28): "vitrine" = pacote do app 0.33.3+; ausente = app antigo (Prêmio legado)
  const origem = body?.origem === "vitrine" ? "vitrine" : null;

  let access: string;
  try {
    access = await playAccessToken();
  } catch {
    // configuração ou Play fora: não grava nada, não consome; a compra fica na Play e volta no próximo início
    return json({ error: "play_unavailable" }, 503);
  }

  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}`;
  const purchaseUrl = `${base}/purchases/products/${productId}/tokens/${encodeURIComponent(token)}`;
  let res: Response;
  try {
    res = await fetch(purchaseUrl, { headers: { Authorization: `Bearer ${access}` } });
  } catch (err) {
    // a rede caiu antes da resposta da Play: 502 como qualquer erro da Play (o app tenta de novo)
    registrar("rede", { origem: "compra", erro: nomeErro(err) });
    return json({ error: "play_error", code: 0 }, 502);
  }
  if (res.status === 404) return json({ status: "not_found" });
  if (!res.ok) {
    // só o status HTTP e o código de erro da Google, se tiver o formato simples; nunca o corpo
    let motivo: string | undefined;
    try {
      const corpo = await res.json();
      const s = corpo?.error?.status;
      if (typeof s === "string" && /^[A-Z_]{1,40}$/.test(s)) motivo = s;
    } catch {
      // corpo ilegível: fica só o status
    }
    registrar("compra_api", { status: res.status, motivo });
    return json({ error: "play_error", code: res.status }, 502);
  }
  const p = await res.json();

  const { data, error } = await sb.rpc("apply_dobroes_purchase", {
    p_user: userId,
    p_product: productId,
    p_token: token,
    p_order: p.orderId ?? null,
    p_purchase_type: typeof p.purchaseType === "number" ? p.purchaseType : null,
    p_state: typeof p.purchaseState === "number" ? p.purchaseState : null,
    p_account_ok: p.obfuscatedExternalAccountId === userId,
    p_origin: origem,
  });
  if (error) return json({ error: "db" }, 500);
  const row = Array.isArray(data) ? data[0] : data;
  const status: string = row?.status ?? "unknown";

  if (CONSUME.has(status)) {
    const c = await fetch(`${purchaseUrl}:consume`, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Length": "0" },
    });
    // consumida de novo (já era) conta como ok; o que falhar fica para a próxima conferência
    if (!c.ok) {
      const text = await c.text();
      if (!/already|consumed|ja foi/i.test(text)) {
        return json({ status, doubloons: row?.doubloons ?? 0, consumed: false });
      }
    }
    return json({ status, doubloons: row?.doubloons ?? 0, consumed: true });
  }
  return json({ status, doubloons: row?.doubloons ?? 0, consumed: false });
});
