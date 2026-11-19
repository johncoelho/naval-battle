// Notificações do servidor da App Store (V2) para compras de dobrões do iPhone (#65): REFUND e
// REVOKE debitam o crédito (RPC apply_dobroes_refund_ios, supabase/dobroes_ios.sql).
// Quem chama é a Apple (POST { signedPayload }), não o app: verify_jwt DESLIGADO de propósito, ao
// estilo da `ads-reward`. As URLs (Production e Sandbox) são cadastradas no App Store Connect.
//
// Qualquer um pode dar POST nesta URL, então o signedPayload NÃO é crido: serve de dica. A Edge
// extrai o transactionId e reconsulta a App Store Server API; só age se a API confirmar
// `revocationDate`. Por isso a cadeia x5c também fica dispensada aqui (HISTORY).
//
// Resposta: 200 quando tratado ou ignorado de vez (TEST, outros tipos, id que a Apple não conhece,
// estorno não confirmado); 400 corpo ilegível; 5xx em falha passageira (Apple/rede/banco/secret),
// para a Apple tentar de novo.
//
// Segredos (só nomes): APPLE_IAP_KEY_ID, APPLE_IAP_ISSUER_ID, APPLE_IAP_KEY_P8.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { acaoDaNotificacao, avaliarTransacao, lerNotificacao } from "../_shared/appstore.ts";
import { consultarNaApple, criarRegistrador } from "../_shared/appstore_client.ts";

const registrar = criarRegistrador("apple_iap_notificacao_falha");

const sb = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);

  const body = await req.json().catch(() => null);
  const nota = lerNotificacao(body?.signedPayload);
  if (!nota) return json({ error: "bad_request" }, 400);

  const acao = acaoDaNotificacao(nota.tipo);
  if (acao !== "estorno" || !nota.transactionId) return json({ ok: true, acao: acao === "teste" ? "teste" : "ignorada" });

  const consulta = await consultarNaApple(nota.transactionId, registrar);
  if (consulta.tipo === "indisponivel") return json({ error: "apple_unavailable" }, 503);
  if (consulta.tipo === "erro") return json({ error: "apple_error", code: consulta.status ?? 0 }, 502);
  if (consulta.tipo === "nao_achou") return json({ ok: true, acao: "desconhecida" });

  // quem comprou não importa aqui (a RPC acha a compra pelo token); a conta do JWT não existe
  const av = avaliarTransacao(consulta.tx, { userId: "", transactionId: nota.transactionId, produto: null });
  if (!av.ok) return json({ ok: true, acao: "recusada" });
  if (!av.revogada) return json({ ok: true, acao: "nao_confirmada" });

  const { data, error } = await sb.rpc("apply_dobroes_refund_ios", {
    p_transaction_id: nota.transactionId,
    p_environment: consulta.ambiente,
  });
  if (error) return json({ error: "db" }, 500);
  const row = Array.isArray(data) ? data[0] : data;
  return json({ ok: true, acao: "estorno", status: row?.status ?? "unknown" });
});
