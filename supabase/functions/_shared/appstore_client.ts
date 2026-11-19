// Cola das Edges do iPhone (#65) com o ambiente: lê os secrets da App Store Server API, monta o
// JWT (com cache) e consulta a transação. A lógica pura está em appstore.ts (coberta por deno test).
//
// Secrets (só os nomes; os valores ficam nos secrets do Supabase, nunca no repositório):
//   APPLE_IAP_KEY_ID     Key ID da chave In-App Purchase (App Store Connect > Users and Access > Integrations)
//   APPLE_IAP_ISSUER_ID  Issuer ID da mesma tela
//   APPLE_IAP_KEY_P8     conteúdo inteiro do AuthKey_<KEYID>.p8
// Não colidem com os do apple-revoke (APPLE_TEAM_ID, APPLE_SIWA_KEY_ID, APPLE_SIWA_KEY_P8).
//
// Falha registrada (padrão do #27): uma linha JSON com evento e etapa (secret_ausente, chave_privada,
// rede, apple_api, resposta, ambiente_divergente) e só o status HTTP. Nunca segredo, JWS, token,
// corpo da Apple nem mensagem de erro.
import { buildApiJwt, consultarTransacao, type Consulta, importP8 } from "./appstore.ts";

export function criarRegistrador(evento: string) {
  return (etapa: string, extra: Record<string, string | number | undefined> = {}) =>
    console.error(JSON.stringify({ evento, etapa, ...extra }));
}

const nomeErro = (err: unknown): string => (err instanceof Error ? err.name : "desconhecido");

let chave: CryptoKey | null = null;
let cache: { jwt: string; exp: number } | null = null;

// null = sem configuração ou chave ruim (o motivo já foi registrado)
async function jwtDaApi(registrar: ReturnType<typeof criarRegistrador>): Promise<string | null> {
  const keyId = Deno.env.get("APPLE_IAP_KEY_ID");
  const issuerId = Deno.env.get("APPLE_IAP_ISSUER_ID");
  const p8 = Deno.env.get("APPLE_IAP_KEY_P8");
  if (!keyId || !issuerId || !p8) {
    registrar("secret_ausente");
    return null;
  }
  const now = Math.floor(Date.now() / 1000);
  if (cache && cache.exp - 60 > now) return cache.jwt;
  try {
    chave ??= await importP8(p8);
    const jwt = await buildApiJwt(chave, { keyId, issuerId, now });
    cache = { jwt, exp: now + 20 * 60 };
    return jwt;
  } catch (err) {
    registrar("chave_privada", { erro: nomeErro(err) });
    return null;
  }
}

export type Resultado = Consulta | { tipo: "indisponivel" };

// Consulta a transação na Apple. `indisponivel` = secret ausente ou chave inválida (503, nada gravado).
export async function consultarNaApple(
  transactionId: string,
  registrar: ReturnType<typeof criarRegistrador>,
): Promise<Resultado> {
  const jwt = await jwtDaApi(registrar);
  if (!jwt) return { tipo: "indisponivel" };
  const r = await consultarTransacao(transactionId, jwt, fetch);
  if (r.tipo === "erro") registrar(r.etapa, { status: r.status });
  return r;
}
