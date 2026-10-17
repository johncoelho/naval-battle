// Conferência de instalação por push de validação (#26). Para cada aparelho Android
// registrado, pede ao FCM uma validação (validate_only = true): o Google confere o token
// sem entregar nada a aparelho nenhum. 404/UNREGISTERED = app desinstalado; 200 = instalado.
// Só grava checked_at e check_status. Não apaga token (isso é da função `push`).
//
// Chamada pelo banco (supabase/push_check.sql, função push_check_run) com o cabeçalho
// x-push-check. Sem o valor certo de app_config.push_check_key: 401, nada feito.
//
// Segredo usado: FCM_SERVICE_ACCOUNT (o mesmo da função push). Nunca escreva o valor em
// arquivo, log ou resposta.
import { createClient } from "jsr:@supabase/supabase-js@2";

const sb = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

type ServiceAccount = { project_id: string; client_email: string; private_key: string };
type Status = "ok" | "unregistered" | "erro";

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

// valida um token no FCM sem enviar nada
async function check(sa: ServiceAccount, auth: string, token: string): Promise<Status> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      validate_only: true,
      message: { token, notification: { title: "Conferência", body: "Conferência de instalação" } },
    }),
  });
  if (res.status === 200) return "ok";
  if (res.status === 404) return "unregistered";
  const text = await res.text();
  return text.includes("UNREGISTERED") ? "unregistered" : "erro";
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);

  // a chave fica só no banco: sem ela, ninguém marca token como desinstalado
  const { data: keyRow } = await sb.from("app_config").select("value").eq("key", "push_check_key").maybeSingle();
  const sent = req.headers.get("x-push-check") ?? "";
  if (!keyRow?.value || sent !== keyRow.value) return json({ error: "unauthorized" }, 401);

  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) return json({ error: "config" }, 503);
  const sa = JSON.parse(raw) as ServiceAccount;

  const { data: tokens, error } = await sb.from("push_tokens")
    .select("token")
    .eq("platform", "android");
  if (error) return json({ error: "db" }, 500);

  let auth: string;
  try {
    auth = await accessToken(sa);
  } catch {
    return json({ error: "fcm_auth" }, 503);
  }

  // poucas centenas por dia, em sequência: sem risco de cota
  const counts: Record<Status, number> = { ok: 0, unregistered: 0, erro: 0 };
  for (const { token } of tokens ?? []) {
    const status = await check(sa, auth, token);
    counts[status] += 1;
    await sb.from("push_tokens")
      .update({ check_status: status, checked_at: new Date().toISOString() })
      .eq("token", token);
  }
  return json({ checked: counts.ok + counts.unregistered + counts.erro, ...counts });
});
