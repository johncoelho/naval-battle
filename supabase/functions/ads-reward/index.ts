// Callback de verificação no servidor (SSV) do anúncio premiado do AdMob (#29).
// Quem chama é o Google, não o app: GET com a query
//   ad_network, ad_unit, custom_data (= vale de ad_reward_start), key_id, reward_amount,
//   reward_item, signature, timestamp, transaction_id, user_id (= uid do jogador)
// Por isso o verify_jwt fica DESLIGADO nesta função: a segurança é a assinatura ECDSA do
// Google, conferida aqui com as chaves públicas de verifier-keys.json (WebCrypto, sem
// biblioteca). Assinada é a query crua até "&signature=" (sem decodificar nem reencodar).
//
// Com a assinatura válida, chama apply_ad_reward (supabase/ads.sql) com service role. O valor
// do prêmio sai do vale guardado no banco, nunca do callback.
//
// Resposta: 200 quando o callback foi tratado (creditado, repetido ou recusado de vez: o Google
// não precisa repetir); 500 só em falha passageira do banco (o Google tenta de novo); 403 com
// assinatura inválida.
//
// Falha registrada (padrão do #27): uma linha JSON em Logs da Edge com evento, etapa e um
// motivo fixo. Nunca assinatura, query, transaction_id, vale, user_id nem mensagem de erro.
import { createClient } from "jsr:@supabase/supabase-js@2";

const KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const KEYS_TTL_MS = 24 * 60 * 60 * 1000;
const REFETCH_MIN_MS = 60 * 1000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const sb = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

function registrar(etapa: string, extra: Record<string, string | number | undefined> = {}) {
  console.error(JSON.stringify({ evento: "ads_reward_falha", etapa, ...extra }));
}

const nomeErro = (err: unknown): string => (err instanceof Error ? err.name : "desconhecido");

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

// ------------------------------------------------------------------ chaves do Google (cache)

let keys = new Map<string, CryptoKey>();
let keysAt = 0;
let lastFetch = 0;

function b64ToBytes(s: string): Uint8Array {
  let b = s.replace(/-/g, "+").replace(/_/g, "/").replace(/\s+/g, "");
  while (b.length % 4) b += "=";
  return Uint8Array.from(atob(b), (c) => c.charCodeAt(0));
}

async function loadKeys(): Promise<void> {
  lastFetch = Date.now();
  const res = await fetch(KEYS_URL);
  if (!res.ok) {
    registrar("chaves", { status: res.status });
    return;
  }
  const body = await res.json();
  const next = new Map<string, CryptoKey>();
  for (const k of body?.keys ?? []) {
    const id = String(k?.keyId ?? "");
    const b64 = typeof k?.base64 === "string"
      ? k.base64
      : String(k?.pem ?? "").replace(/-----[^-]+-----/g, "");
    if (!id || !b64) continue;
    try {
      const key = await crypto.subtle.importKey(
        "spki", b64ToBytes(b64), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"],
      );
      next.set(id, key);
    } catch (err) {
      registrar("chave_invalida", { erro: nomeErro(err) });
    }
  }
  if (next.size > 0) {
    keys = next;
    keysAt = Date.now();
  }
}

async function keyFor(id: string): Promise<CryptoKey | undefined> {
  const stale = Date.now() - keysAt > KEYS_TTL_MS;
  // key_id novo: o Google trocou as chaves; busca de novo, no máximo uma vez por minuto
  if (stale || (!keys.has(id) && Date.now() - lastFetch > REFETCH_MIN_MS)) {
    try {
      await loadKeys();
    } catch (err) {
      registrar("chaves", { erro: nomeErro(err) });
    }
  }
  return keys.get(id);
}

// ------------------------------------------------------------------ assinatura

// ECDSA em DER (SEQUENCE { INTEGER r, INTEGER s }) → r‖s de 32 bytes cada, que o WebCrypto quer
function derToRaw(der: Uint8Array): Uint8Array | null {
  let i = 0;
  if (der[i++] !== 0x30) return null;
  let len = der[i++];
  if (len & 0x80) {
    const n = len & 0x7f;
    len = 0;
    for (let k = 0; k < n; k++) len = (len << 8) | der[i++];
  }
  if (i + len !== der.length) return null;
  const out = new Uint8Array(64);
  for (let part = 0; part < 2; part++) {
    if (der[i++] !== 0x02) return null;
    const n = der[i++];
    let v = der.slice(i, i + n);
    i += n;
    while (v.length > 32 && v[0] === 0) v = v.slice(1);
    if (v.length > 32) return null;
    out.set(v, part * 32 + (32 - v.length));
  }
  return i === der.length ? out : null;
}

async function verify(rawQuery: string): Promise<"ok" | "sem_assinatura" | "chave_desconhecida" | "invalida"> {
  const cut = rawQuery.indexOf("&signature=");
  if (cut <= 0) return "sem_assinatura";
  const message = rawQuery.slice(0, cut);
  const params = new URLSearchParams(rawQuery.slice(cut + 1));
  const sig = params.get("signature");
  const keyId = params.get("key_id");
  if (!sig || !keyId) return "sem_assinatura";
  const key = await keyFor(keyId);
  if (!key) return "chave_desconhecida";
  let raw: Uint8Array | null;
  try {
    raw = derToRaw(b64ToBytes(sig));
  } catch {
    raw = null;
  }
  if (!raw) return "invalida";
  const ok = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" }, key, raw, new TextEncoder().encode(message),
  );
  return ok ? "ok" : "invalida";
}

// ------------------------------------------------------------------ callback

Deno.serve(async (req) => {
  if (req.method !== "GET") return json({ error: "method" }, 405);
  const q = req.url.indexOf("?");
  const rawQuery = q >= 0 ? req.url.slice(q + 1) : "";

  let check: Awaited<ReturnType<typeof verify>>;
  try {
    check = await verify(rawQuery);
  } catch (err) {
    registrar("assinatura", { erro: nomeErro(err) });
    return json({ error: "verify" }, 500);
  }
  if (check !== "ok") {
    registrar("assinatura", { motivo: check });
    return json({ error: "signature" }, 403);
  }

  const params = new URLSearchParams(rawQuery);
  const vale = params.get("custom_data") ?? "";
  const user = params.get("user_id") ?? "";
  const tx = params.get("transaction_id") ?? "";
  // o teste de URL do AdMob vem assinado, mas sem vale nem usuário: responde 200 sem gravar nada
  if (!vale && !user) return json({ status: "verificado" });
  if (!UUID.test(vale) || !UUID.test(user) || !tx || tx.length > 200) {
    registrar("parametros", { motivo: "formato" });
    return json({ status: "invalido" });
  }

  const { data, error } = await sb.rpc("apply_ad_reward", { p_vale: vale, p_transaction: tx, p_user: user });
  if (error) {
    registrar("banco", { motivo: typeof error.code === "string" ? error.code.slice(0, 10) : "erro" });
    return json({ error: "db" }, 500);
  }
  const row = Array.isArray(data) ? data[0] : data;
  const status: string = row?.status ?? "unknown";
  if (status !== "applied" && status !== "already") registrar("recusado", { motivo: status });
  return json({ status });
});
