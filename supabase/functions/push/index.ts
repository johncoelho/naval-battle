// Envia um aviso da fila push_outbox para os aparelhos do destinatário: Android pelo Firebase
// Cloud Messaging (API HTTP v1) e iPhone direto pelo APNs (#14, HTTP/2 com JWT ES256). Chamada pelo próprio banco (supabase/push.sql)
// com { id } — só envia o que já está na fila, uma vez, então pode ficar sem JWT.
// Linha sem user_id é aviso geral (versão nova, novidades): vai para todos os tokens.
//
// Segredo necessário na função: FCM_SERVICE_ACCOUNT = JSON da conta de serviço do
// projeto Firebase "Naval Battle" (Configurações do projeto → Contas de serviço).
// Para o iPhone: APPLE_TEAM_ID (o mesmo do apple-revoke) e uma chave .p8 com o serviço APNs ligado:
// APNS_KEY_ID e APNS_KEY_P8 (se faltarem, cai em APPLE_SIWA_KEY_ID/APPLE_SIWA_KEY_P8, que só servem
// se essa chave tiver APNs). Só APNs de produção: TestFlight e App Store usam produção.
// Sem esses segredos o iPhone fica de fora e o Android segue igual.
import { createClient } from "jsr:@supabase/supabase-js@2";

const sb = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

type ServiceAccount = { project_id: string; client_email: string; private_key: string };

let cached: { token: string; exp: number } | null = null;

function b64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${b64url(signature)}`,
    }),
  });
  const json = await res.json();
  if (!json.access_token) throw new Error(`oauth ${res.status}`);
  cached = { token: json.access_token, exp: now + (json.expires_in ?? 3600) };
  return cached.token;
}

// ---------------------------------------------------------------- APNs (iPhone, #14)

const APNS_HOST = "https://api.push.apple.com";
const APNS_TOPIC = "br.com.navalbattle"; // bundle id do app

// JWT do provedor (ES256) guardado no módulo. O APNs devolve 429 TooManyProviderTokenUpdates se o
// token mudar com frequência, então o iat é arredondado para janelas de 30 min: isolates frios na
// mesma janela geram o mesmo token. (Copiado da apple-revoke, sem pasta _shared, para os deploys
// não ficarem acoplados.)
let apnsJwt: { token: string; window: number } | null = null;

async function apnsToken(teamId: string, keyId: string, p8: string): Promise<string> {
  const window = Math.floor(Date.now() / 1000 / 1800);
  if (apnsJwt && apnsJwt.window === window) return apnsJwt.token;
  const header = b64url(JSON.stringify({ alg: "ES256", kid: keyId, typ: "JWT" }));
  const claims = b64url(JSON.stringify({ iss: teamId, iat: window * 1800 }));
  const pem = p8.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"],
  );
  // WebCrypto já devolve a assinatura em r||s, o formato do JWT
  const sig = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  apnsJwt = { token: `${header}.${claims}.${b64url(sig)}`, window };
  return apnsJwt.token;
}

// Avisos que não vão para o iPhone: a versão da Play não é a da App Store (fica para outra issue).
const KINDS_SEM_IOS = new Set(["new_version"]);

// Marca o token de um aparelho que o servidor de push disse que não existe mais.
async function marcarDesregistrado(token: string) {
  await sb.from("push_tokens")
    .update({ check_status: "unregistered", checked_at: new Date().toISOString() })
    .eq("token", token);
}

Deno.serve(async (req) => {
  const { id } = await req.json().catch(() => ({ id: null }));
  if (!id) return new Response("missing id", { status: 400 });

  // reserva a linha: só segue quem conseguiu marcar sent_at (nunca envia duas vezes)
  const { data: msg } = await sb.from("push_outbox")
    .update({ sent_at: new Date().toISOString() })
    .eq("id", id).is("sent_at", null)
    .select("*").maybeSingle();
  if (!msg) return new Response("skip");

  const finish = async (result: string) => {
    await sb.from("push_outbox").update({ result }).eq("id", id);
    return new Response(result);
  };

  // sem destinatário = aviso geral: vai para todos os aparelhos (com ou sem conta)
  // tokens marcados 'unregistered' (desinstalado, #26) ficam fora dos envios; NULL precisa do "is.null"
  let query = sb.from("push_tokens").select("token, platform").in("platform", ["android", "ios"])
    .or("check_status.is.null,check_status.neq.unregistered");
  if (msg.user_id) query = query.eq("user_id", msg.user_id);
  const { data: all } = await query;
  const android = (all ?? []).filter((t) => t.platform === "android");
  const ios = KINDS_SEM_IOS.has(msg.kind) ? [] : (all ?? []).filter((t) => t.platform === "ios");
  if (!android.length && !ios.length) return finish("no tokens");

  const results: string[] = [];

  // ---- Android (FCM): caminho igual ao de sempre
  if (android.length) {
    const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
    if (!raw) {
      results.push("no FCM_SERVICE_ACCOUNT");
    } else {
      const sa = JSON.parse(raw) as ServiceAccount;
      const auth = await accessToken(sa);
      for (const { token } of android) {
        const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
          method: "POST",
          headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            message: {
              token,
              notification: { title: msg.title, body: msg.body },
              data: { kind: msg.kind },
              android: {
                priority: "high",
                notification: { channel_id: "social", icon: "ic_notification", color: "#FFC95C" },
              },
            },
          }),
        });
        results.push(String(res.status));
        // aparelho desinstalou ou trocou de token: MARCA (não apaga), para a conferência e o relatório
        // verem o histórico; o envio seguinte pula o token marcado. Só 404/UNREGISTERED marcam.
        if (res.status === 404 || res.status === 400) {
          const text = await res.text();
          if (res.status === 404 || text.includes("UNREGISTERED")) await marcarDesregistrado(token);
        }
      }
    }
  }

  // ---- iPhone (APNs, #14)
  if (ios.length) {
    const teamId = Deno.env.get("APPLE_TEAM_ID");
    const keyId = Deno.env.get("APNS_KEY_ID") ?? Deno.env.get("APPLE_SIWA_KEY_ID");
    const p8 = Deno.env.get("APNS_KEY_P8") ?? Deno.env.get("APPLE_SIWA_KEY_P8");
    if (!teamId || !keyId || !p8) {
      results.push("ios:no APNs secrets");
    } else {
      try {
        const jwt = await apnsToken(teamId, keyId, p8);
        for (const { token } of ios) {
          const res = await fetch(`${APNS_HOST}/3/device/${token}`, {
            method: "POST",
            headers: {
              authorization: `bearer ${jwt}`,
              "apns-topic": APNS_TOPIC,
              "apns-push-type": "alert",
              "apns-priority": "10",
              "content-type": "application/json",
            },
            body: JSON.stringify({
              aps: { alert: { title: msg.title, body: msg.body }, sound: "default" },
              kind: msg.kind,
            }),
          });
          if (res.status === 200) {
            results.push("ios:200");
            continue;
          }
          const reason = (await res.json().catch(() => null))?.reason ?? "";
          results.push(`ios:${res.status}:${reason}`);
          // 410 Unregistered e 400 BadDeviceToken: o aparelho não existe mais. MARCA (não apaga), como o
          // Android. 403, 429 e DeviceTokenNotForTopic são erro de configuração: não marcam token.
          if (res.status === 410 || (res.status === 400 && reason === "BadDeviceToken")) {
            await marcarDesregistrado(token);
          }
        }
      } catch (_e) {
        // chave inválida ou rede: o Android já foi enviado, só o iPhone fica de fora
        results.push("ios:error");
      }
    }
  }
  return finish(results.join(","));
});
