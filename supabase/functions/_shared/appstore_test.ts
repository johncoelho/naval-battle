// deno test supabase/functions/_shared/appstore_test.ts
// Cobre a lógica pura da compra no iPhone (#65). Nenhum segredo real: a chave é gerada aqui.
import {
  acaoDaNotificacao,
  avaliarTransacao,
  b64urlDecode,
  b64urlEncode,
  buildApiJwt,
  BUNDLE_ID,
  consultarTransacao,
  decodeJwsPayload,
  deveFinalizar,
  importP8,
  lerNotificacao,
  parseTransactionId,
} from "./appstore.ts";

const USER = "3f2b1c9e-1111-4222-8333-444455556666";

// JWS de mentira (assinatura não importa: a lógica nunca a confere)
const jws = (payload: unknown) => `${b64urlEncode('{"alg":"ES256"}')}.${b64urlEncode(JSON.stringify(payload))}.${b64urlEncode("sig")}`;

const txOk = (extra: Record<string, unknown> = {}) => ({
  transactionId: "2000000123456789",
  bundleId: BUNDLE_ID,
  productId: "dobroes_bolsa",
  type: "Consumable",
  appAccountToken: USER,
  environment: "Production",
  ...extra,
});

function assert(cond: unknown, msg = "falhou") {
  if (!cond) throw new Error(msg);
}
function igual<T>(a: T, b: T) {
  if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${JSON.stringify(a)} != ${JSON.stringify(b)}`);
}

Deno.test("JWT da API: cabeçalho, claims e assinatura ES256 em r||s verificável", async () => {
  const par = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", par.privateKey));
  let bin = "";
  for (const b of pkcs8) bin += String.fromCharCode(b);
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(bin)}\n-----END PRIVATE KEY-----`;

  for (const p8 of [pem, pem.replace(/\n/g, "\\n")]) { // multilinha e "\n" literal
    const key = await importP8(p8);
    const jwt = await buildApiJwt(key, { keyId: "KEY123", issuerId: "issuer-uuid", now: 1_700_000_000 });
    const [h, c, s] = jwt.split(".");
    const header = JSON.parse(new TextDecoder().decode(b64urlDecode(h)));
    const claims = JSON.parse(new TextDecoder().decode(b64urlDecode(c)));
    igual(header, { alg: "ES256", kid: "KEY123", typ: "JWT" });
    igual(claims, {
      iss: "issuer-uuid",
      iat: 1_700_000_000,
      exp: 1_700_000_000 + 1200,
      aud: "appstoreconnect-v1",
      bid: BUNDLE_ID,
    });
    assert(claims.exp - claims.iat <= 20 * 60, "exp até 20 min");
    const sig = b64urlDecode(s);
    igual(sig.length, 64); // r||s, não DER
    const ok = await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      par.publicKey,
      sig as unknown as BufferSource,
      new TextEncoder().encode(`${h}.${c}`),
    );
    assert(ok, "assinatura não confere");
  }
});

Deno.test("decodeJwsPayload: lê o corpo e recusa lixo", () => {
  igual(decodeJwsPayload(jws({ a: 1 })), { a: 1 });
  igual(decodeJwsPayload("abc"), null);
  igual(decodeJwsPayload("a.b"), null);
  igual(decodeJwsPayload("a.!!!.c"), null);
  igual(decodeJwsPayload(`a.${b64urlEncode("[1]")}.c`), null); // array não é objeto
  igual(decodeJwsPayload(42), null);
  igual(decodeJwsPayload(undefined), null);
});

Deno.test("parseTransactionId: só dígitos, sem injetar caminho", () => {
  igual(parseTransactionId("2000000123456789"), "2000000123456789");
  igual(parseTransactionId(123), "123");
  igual(parseTransactionId("1/../../x"), null);
  igual(parseTransactionId("12a"), null);
  igual(parseTransactionId(""), null);
  igual(parseTransactionId("1".repeat(21)), null);
  igual(parseTransactionId(null), null);
});

const esperado = { userId: USER, transactionId: "2000000123456789", produto: "dobroes_bolsa" as string | null };

Deno.test("avaliarTransacao: compra boa", () => {
  const r = avaliarTransacao(txOk(), esperado);
  igual(r, { ok: true, revogada: false, contaOk: true, produto: "dobroes_bolsa", transactionId: "2000000123456789" });
});

Deno.test("avaliarTransacao: conta diferente, sem token e maiúsculas", () => {
  igual((avaliarTransacao(txOk({ appAccountToken: "outra" }), esperado) as { contaOk: boolean }).contaOk, false);
  const sem = txOk();
  delete (sem as Record<string, unknown>).appAccountToken;
  igual((avaliarTransacao(sem, esperado) as { contaOk: boolean }).contaOk, false);
  igual((avaliarTransacao(txOk({ appAccountToken: USER.toUpperCase() }), esperado) as { contaOk: boolean }).contaOk, true);
});

Deno.test("avaliarTransacao: revocationDate marca revogada", () => {
  const r = avaliarTransacao(txOk({ revocationDate: 1_700_000_000_000 }), esperado);
  igual((r as { revogada: boolean }).revogada, true);
});

Deno.test("avaliarTransacao: recusas (bundle, produto, tipo, id)", () => {
  igual(avaliarTransacao(txOk({ bundleId: "com.outro" }), esperado), { ok: false, motivo: "bundle" });
  igual(avaliarTransacao(txOk({ productId: "dobroes_ouro" }), esperado), { ok: false, motivo: "produto" });
  igual(avaliarTransacao(txOk({ productId: "dobroes_bau" }), esperado), { ok: false, motivo: "produto" }); // difere do pedido
  igual(avaliarTransacao(txOk({ type: "Auto-Renewable Subscription" }), esperado), { ok: false, motivo: "tipo" });
  igual(avaliarTransacao(txOk({ transactionId: "999" }), esperado), { ok: false, motivo: "transacao" });
});

Deno.test("avaliarTransacao: notificação sem produto esperado usa o da transação", () => {
  const r = avaliarTransacao(txOk({ productId: "dobroes_cofre" }), { ...esperado, produto: null, userId: "" });
  igual((r as { produto: string }).produto, "dobroes_cofre");
});

Deno.test("deveFinalizar: só status terminais finalizam", () => {
  for (const s of ["credited", "already", "sandbox_refused", "not_purchased"]) assert(deveFinalizar(s), s);
  for (const s of ["billing_off", "account_mismatch", "unknown_product", "not_found", "unknown"]) {
    assert(!deveFinalizar(s), s);
  }
});

Deno.test("acaoDaNotificacao e lerNotificacao", () => {
  igual(acaoDaNotificacao("REFUND"), "estorno");
  igual(acaoDaNotificacao("REVOKE"), "estorno");
  igual(acaoDaNotificacao("TEST"), "teste");
  igual(acaoDaNotificacao("DID_RENEW"), "ignorar");
  igual(acaoDaNotificacao(undefined), "ignorar");

  const payload = jws({
    notificationType: "REFUND",
    data: { environment: "Sandbox", signedTransactionInfo: jws({ transactionId: "77" }) },
  });
  igual(lerNotificacao(payload), { tipo: "REFUND", transactionId: "77" });
  igual(lerNotificacao(jws({ notificationType: "TEST" })), { tipo: "TEST", transactionId: null });
  igual(lerNotificacao("lixo"), null);
});

// ------------------------------------------------------------------ consultarTransacao (fetch falso)

const resp = (status: number, body: unknown = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.test("consultarTransacao: Production acha e não consulta a Sandbox", async () => {
  const urls: string[] = [];
  const f = ((u: string) => {
    urls.push(u);
    return Promise.resolve(resp(200, { signedTransactionInfo: jws(txOk()) }));
  }) as unknown as typeof fetch;
  const r = await consultarTransacao("2000000123456789", "jwt", f);
  igual(r.tipo, "ok");
  igual((r as { ambiente: string }).ambiente, "Production");
  igual(urls.length, 1);
  assert(urls[0].startsWith("https://api.storekit.itunes.apple.com/inApps/v1/transactions/2000000123456789"));
});

Deno.test("consultarTransacao: 404 na Production cai na Sandbox", async () => {
  const f = ((u: string) =>
    Promise.resolve(
      u.includes("sandbox")
        ? resp(200, { signedTransactionInfo: jws(txOk({ environment: "Sandbox" })) })
        : resp(404, { errorCode: 4040010 }),
    )) as unknown as typeof fetch;
  const r = await consultarTransacao("2000000123456789", "jwt", f);
  igual((r as { ambiente: string }).ambiente, "Sandbox");
});

Deno.test("consultarTransacao: 404 nos dois = nao_achou", async () => {
  const f = (() => Promise.resolve(resp(404))) as unknown as typeof fetch;
  igual((await consultarTransacao("1", "jwt", f)).tipo, "nao_achou");
});

Deno.test("consultarTransacao: ambiente do JWS diferente do host é erro", async () => {
  const f = (() => Promise.resolve(resp(200, { signedTransactionInfo: jws(txOk({ environment: "Sandbox" })) }))) as unknown as typeof fetch;
  igual(await consultarTransacao("1", "jwt", f), { tipo: "erro", etapa: "ambiente_divergente" });
});

Deno.test("consultarTransacao: erros da Apple, rede e corpo ruim (sem corpo no resultado)", async () => {
  igual(
    await consultarTransacao("1", "jwt", (() => Promise.resolve(resp(401, { segredo: "x" }))) as unknown as typeof fetch),
    { tipo: "erro", etapa: "apple_api", status: 401 },
  );
  igual(
    await consultarTransacao("1", "jwt", (() => Promise.reject(new TypeError("boom"))) as unknown as typeof fetch),
    { tipo: "erro", etapa: "rede" },
  );
  igual(
    await consultarTransacao("1", "jwt", (() => Promise.resolve(resp(200, { signedTransactionInfo: "lixo" }))) as unknown as typeof fetch),
    { tipo: "erro", etapa: "resposta" },
  );
});
