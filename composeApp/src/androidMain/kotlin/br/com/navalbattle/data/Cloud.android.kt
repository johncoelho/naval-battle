package br.com.navalbattle.data

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente HTTP direto contra a API REST do Supabase. Sem biblioteca de rede:
 * são cinco chamadas simples e o APK continua leve.
 */
actual class CloudApi actual constructor() {

    actual suspend fun signUp(
        email: String,
        password: String,
        username: String
    ): CloudResult<Session> = call {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", JSONObject().put("username", username))
        val json = post("/auth/v1/signup", body, token = null)
        val session = sessionOf(json, fallbackName = username)
            ?: return@call CloudResult.Fail(
                "Conta criada. Confirme o e-mail e entre para sincronizar."
            )
        // primeira gravação: a linha da carreira nasce junto com a conta
        CloudResult.Ok(session)
    }

    actual suspend fun signIn(email: String, password: String): CloudResult<Session> = call {
        val body = JSONObject().put("email", email).put("password", password)
        val json = post("/auth/v1/token?grant_type=password", body, token = null)
        val session = sessionOf(json, fallbackName = email.substringBefore("@"))
            ?: return@call CloudResult.Fail("Não consegui abrir a sessão.")
        CloudResult.Ok(session)
    }

    actual suspend fun refresh(refreshToken: String): CloudResult<Session> = call {
        val body = JSONObject().put("refresh_token", refreshToken)
        val json = post("/auth/v1/token?grant_type=refresh_token", body, token = null)
        val session = sessionOf(json, fallbackName = "Comandante")
            ?: return@call CloudResult.Fail("Sessão expirada.")
        CloudResult.Ok(session)
    }

    actual suspend fun loadProfile(session: Session): CloudResult<CloudProfile?> = call {
        val json = get("/rest/v1/profiles?id=eq.${session.userId}&select=*", session.accessToken)
        val arr = JSONArray(json)
        if (arr.length() == 0) return@call CloudResult.Ok(null)
        val o = arr.getJSONObject(0)
        CloudResult.Ok(
            CloudProfile(
                username = o.optString("username", session.username),
                insignia = o.optString("insignia", "anc"),
                xp = o.optInt("xp"),
                credits = o.optInt("credits", 500),
                matches = o.optInt("matches"),
                wins = o.optInt("wins"),
                shots = o.optInt("shots"),
                hits = o.optInt("hits"),
                sunk = o.optInt("sunk"),
                streak = o.optInt("streak"),
                bestStreak = o.optInt("best_streak"),
                owned = o.optString("owned", "std,br"),
                equipped = o.optString("equipped", "br"),
                fleets = o.optString("fleets", "std"),
                fleet = o.optString("fleet", "std")
            )
        )
    }

    actual suspend fun saveProfile(session: Session, profile: CloudProfile): CloudResult<Unit> =
        call {
            val body = JSONObject()
                .put("id", session.userId)
                .put("email", session.email)
                .put("username", profile.username)
                .put("insignia", profile.insignia)
                .put("xp", profile.xp)
                .put("credits", profile.credits)
                .put("matches", profile.matches)
                .put("wins", profile.wins)
                .put("shots", profile.shots)
                .put("hits", profile.hits)
                .put("sunk", profile.sunk)
                .put("streak", profile.streak)
                .put("best_streak", profile.bestStreak)
                .put("owned", profile.owned)
                .put("equipped", profile.equipped)
                .put("fleets", profile.fleets)
                .put("fleet", profile.fleet)
            // upsert: cria a linha na primeira vez, atualiza nas seguintes
            post(
                path = "/rest/v1/profiles?on_conflict=id",
                body = body,
                token = session.accessToken,
                prefer = "resolution=merge-duplicates,return=minimal"
            )
            CloudResult.Ok(Unit)
        }

    // ------------------------------------------------------------------ HTTP

    /** Sessão vencida: quem chamou renova o token e repete. */
    private class SessionExpired : Exception("Sessão expirada.")

    private suspend fun <T> call(block: suspend () -> CloudResult<T>): CloudResult<T> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext CloudResult.Fail("Servidor do jogo ainda não configurado.")
            }
            runCatching { block() }.getOrElse { e ->
                if (e is SessionExpired) {
                    CloudResult.Fail(e.message.orEmpty(), expired = true)
                } else {
                    CloudResult.Fail(readable(e))
                }
            }
        }

    private fun readable(e: Throwable): String {
        val raw = e.message ?: "Falha de conexão."
        return when {
            "Invalid login credentials" in raw -> "E-mail ou senha incorretos."
            "User already registered" in raw -> "Esse e-mail já tem conta. Faça login."
            "Password should be" in raw -> "A senha precisa de pelo menos 6 caracteres."
            "duplicate key" in raw && "username" in raw -> "Esse nome de usuário já está em uso."
            "Unable to validate email" in raw || "invalid format" in raw -> "E-mail inválido."
            raw.length > 120 -> "Não consegui falar com o servidor do jogo."
            else -> raw
        }
    }

    private fun post(
        path: String,
        body: JSONObject,
        token: String?,
        prefer: String? = null
    ): String {
        val conn = open(path, "POST", token, prefer)
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return read(conn)
    }

    private fun get(path: String, token: String): String = read(open(path, "GET", token, null))

    private fun open(
        path: String,
        method: String,
        token: String?,
        prefer: String?
    ): HttpURLConnection {
        val conn = URL(SupabaseConfig.URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${token ?: SupabaseConfig.ANON_KEY}")
        prefer?.let { conn.setRequestProperty("Prefer", it) }
        return conn
    }

    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        // o token do Supabase vive uma hora; 401 aqui quer dizer "renove e tente de novo"
        if (code == 401 && "Invalid login credentials" !in text) throw SessionExpired()
        if (code !in 200..299) {
            val message = runCatching {
                val o = JSONObject(text)
                o.optString("msg").ifBlank {
                    o.optString("error_description").ifBlank {
                        o.optString("message").ifBlank { o.optString("error") }
                    }
                }
            }.getOrNull().orEmpty()
            throw IllegalStateException(message.ifBlank { "HTTP $code" })
        }
        return text
    }

    private fun sessionOf(raw: String, fallbackName: String): Session? {
        val o = JSONObject(raw)
        val token = o.optString("access_token")
        if (token.isBlank()) return null
        val user = o.optJSONObject("user") ?: return null
        val meta = user.optJSONObject("user_metadata")
        return Session(
            userId = user.optString("id"),
            email = user.optString("email"),
            username = meta?.optString("username").orEmpty().ifBlank { fallbackName },
            accessToken = token,
            refreshToken = o.optString("refresh_token")
        )
    }
}
