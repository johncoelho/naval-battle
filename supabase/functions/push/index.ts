// Envia um aviso da fila push_outbox para os aparelhos do destinatário (Firebase
// Cloud Messaging, API HTTP v1). Chamada pelo próprio banco (supabase/push.sql)
// com { id } — só envia o que já está na fila, uma vez, então pode ficar sem JWT.
// Linha sem user_id é aviso geral (versão nova, novidades): vai para todos os tokens.
//
// Segredo necessário na função: FCM_SERVICE_ACCOUNT = JSON da conta de serviço do
// projeto Firebase "Naval Battle" (Configurações do projeto → Contas de serviço).
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

  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) return finish("no FCM_SERVICE_ACCOUNT");
  const sa = JSON.parse(raw) as ServiceAccount;

  // sem destinatário = aviso geral: vai para todos os aparelhos (com ou sem conta)
  let query = sb.from("push_tokens").select("token").eq("platform", "android");
  if (msg.user_id) query = query.eq("user_id", msg.user_id);
  const { data: tokens } = await query;
  if (!tokens?.length) return finish("no tokens");

  const auth = await accessToken(sa);
  const results: string[] = [];
  for (const { token } of tokens) {
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
    // aparelho desinstalou ou trocou de token: tira da lista
    if (res.status === 404 || res.status === 400) {
      const text = await res.text();
      if (res.status === 404 || text.includes("UNREGISTERED")) {
        await sb.from("push_tokens").delete().eq("token", token);
      }
    }
  }
  return finish(results.join(","));
});
