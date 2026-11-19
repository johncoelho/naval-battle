// Confere uma compra de dobrões feita no iPhone com StoreKit 2 (#65) e manda o resultado para o
// banco (RPC apply_dobroes_purchase_ios, supabase/dobroes_ios.sql). O servidor decide o que a
// compra vale; o aparelho só soma o que dobroes_pending devolver. A Edge `dobroes-verify` do
// Android não é tocada.
//
// Chamada pelo app do jogador: Authorization = JWT do jogador (verify_jwt LIGADO).
// Entrada: { product_id, signed_transaction }. O usuário vem do JWT, nunca do corpo.
//
// Nada que vem do aparelho é confiável: o JWS serve só para ler o transactionId. A verdade é a
// resposta da App Store Server API (Get Transaction Info), autenticada com a nossa chave. Por isso
// a cadeia x5c do JWS não é validada (HISTORY). Ver _shared/appstore.ts.
//
// Resposta: { status, doubloons, finish }. `finish` é o análogo do `consumed` do Android: o app só
// chama transaction.finish() com finish = true. Erro (503/502/500) = não gravou nada, tentar depois.
//
// Segredos (só nomes): APPLE_IAP_KEY_ID, APPLE_IAP_ISSUER_ID, APPLE_IAP_KEY_P8.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { avaliarTransacao, decodeJwsPayload, deveFinalizar, parseTransactionId, PRODUCTS } from "../_shared/appstore.ts";
import { consultarNaApple, criarRegistrador } from "../_shared/appstore_client.ts";

const registrar = criarRegistrador("dobroes_verify_ios_falha");

const sb = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

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
  const dica = decodeJwsPayload(body?.signed_transaction);
  const transactionId = parseTransactionId(dica?.transactionId);
  if (typeof productId !== "string" || !PRODUCTS.has(productId) || !transactionId) {
    return json({ error: "bad_request" }, 400);
  }

  const consulta = await consultarNaApple(transactionId, registrar);
  if (consulta.tipo === "indisponivel") {
    // configuração ou chave fora: não grava, não finaliza; a transação volta no próximo início
    return json({ error: "apple_unavailable" }, 503);
  }
  if (consulta.tipo === "erro") return json({ error: "apple_error", code: consulta.status ?? 0 }, 502);
  if (consulta.tipo === "nao_achou") return json({ status: "not_found", doubloons: 0, finish: false });

  const av = avaliarTransacao(consulta.tx, { userId, transactionId, produto: productId });
  if (!av.ok) {
    // bundle, produto, tipo ou id que não batem: não é nossa compra, nada é gravado nem finalizado
    registrar("transacao_recusada", { motivo: av.motivo });
    return json({ status: "unknown_product", doubloons: 0, finish: false });
  }

  const { data, error } = await sb.rpc("apply_dobroes_purchase_ios", {
    p_user: userId,
    p_product: productId,
    p_transaction_id: transactionId,
    p_environment: consulta.ambiente,
    p_account_ok: av.contaOk,
    p_revoked: av.revogada,
  });
  if (error) return json({ error: "db" }, 500);
  const row = Array.isArray(data) ? data[0] : data;
  const status: string = row?.status ?? "unknown";
  return json({ status, doubloons: row?.doubloons ?? 0, finish: deveFinalizar(status) });
});
