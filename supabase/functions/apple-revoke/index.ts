// Revoga o token do "Entrar com a Apple" quando o jogador exclui a conta (#13, App Store 5.1.1).
// O app reautentica na Apple, pega um authorizationCode novo (vale 5 min) e manda para cá com o
// JWT do jogador (verify_jwt ligado). A Edge troca o code por um refresh_token em
// https://appleid.apple.com/auth/token e o revoga em /auth/revoke. Nenhum token da Apple é guardado.
//
// É melhor esforço: a exclusão da conta NUNCA depende desta função. Respostas (sempre 200 quando o
// pedido é válido, para o app seguir com a exclusão):
//   { revoked: true }
//   { revoked: false, reason: "not_configured" }  -> faltam os secrets abaixo (deploy sem eles)
//   { revoked: false, reason: "apple_error" }     -> a Apple recusou ou a rede caiu
//
// Secrets (Supabase > Edge Functions > Secrets): APPLE_TEAM_ID, APPLE_SIWA_KEY_ID e
// APPLE_SIWA_KEY_P8 (conteúdo inteiro do AuthKey_<KEYID>.p8). O client_id (bundle) é constante.
//
// Log: uma linha JSON com evento e etapa (nao_configurado, chave_invalida, token, revoke, rede) e o
// status HTTP da Apple. Nunca code, token, client_secret, chave nem corpo de resposta da Apple.

const CLIENT_ID = "br.com.navalbattle";
const APPLE = "https://appleid.apple.com";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function registrar(etapa: string, extra: Record<string, string | number | undefined> = {}) {
  console.error(JSON.stringify({ evento: "apple_revoke", etapa, ...extra }));
}

// só o nome da classe do erro (ex.: TypeError), nunca a mensagem
const nomeErro = (err: unknown): string => (err instanceof Error ? err.name : "desconhecido");

function b64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// client_secret da Apple: JWT ES256 (WebCrypto devolve a assinatura em r||s, o formato do JWT)
async function clientSecret(teamId: string, keyId: string, p8: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "ES256", kid: keyId, typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: teamId,
    iat: now,
    exp: now + 300,
    aud: APPLE,
    sub: CLIENT_ID,
  }));
  const pem = p8.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"],
  );
  const sig = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  return `${header}.${claims}.${b64url(sig)}`;
}

const form = (fields: Record<string, string>) => ({
  method: "POST",
  headers: { "Content-Type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams(fields).toString(),
});

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);

  const body = await req.json().catch(() => null);
  const code = body?.code;
  if (typeof code !== "string" || code.length < 10 || code.length > 2048) {
    return json({ error: "bad_request" }, 400);
  }

  const teamId = Deno.env.get("APPLE_TEAM_ID");
  const keyId = Deno.env.get("APPLE_SIWA_KEY_ID");
  const p8 = Deno.env.get("APPLE_SIWA_KEY_P8");
  if (!teamId || !keyId || !p8) {
    // deploy sem os secrets: a conta é apagada, mas o token não é revogado na Apple
    registrar("nao_configurado");
    return json({ revoked: false, reason: "not_configured" });
  }

  let secret: string;
  try {
    secret = await clientSecret(teamId, keyId, p8);
  } catch (err) {
    registrar("chave_invalida", { erro: nomeErro(err) });
    return json({ revoked: false, reason: "apple_error" });
  }

  try {
    const t = await fetch(`${APPLE}/auth/token`, form({
      client_id: CLIENT_ID, client_secret: secret, code, grant_type: "authorization_code",
    }));
    if (!t.ok) {
      registrar("token", { status: t.status });
      return json({ revoked: false, reason: "apple_error" });
    }
    const refresh = (await t.json())?.refresh_token;
    if (typeof refresh !== "string" || !refresh) {
      registrar("token", { status: t.status, motivo: "sem_refresh_token" });
      return json({ revoked: false, reason: "apple_error" });
    }
    const r = await fetch(`${APPLE}/auth/revoke`, form({
      client_id: CLIENT_ID, client_secret: secret, token: refresh, token_type_hint: "refresh_token",
    }));
    if (!r.ok) {
      registrar("revoke", { status: r.status });
      return json({ revoked: false, reason: "apple_error" });
    }
    return json({ revoked: true });
  } catch (err) {
    registrar("rede", { erro: nomeErro(err) });
    return json({ revoked: false, reason: "apple_error" });
  }
});
