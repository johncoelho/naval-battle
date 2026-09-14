package br.com.navalbattle.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Mesmo cliente REST direto contra o Supabase que o Android tem, só que com
 * `NSURLSession` no lugar de `HttpURLConnection`. A lógica (que endpoint chamar, como
 * ler o erro) é a mesma dos dois lados de propósito — só a chamada HTTP muda.
 */
@OptIn(ExperimentalForeignApi::class)
actual class CloudApi actual constructor() {

    actual suspend fun signUp(email: String, password: String, username: String): CloudResult<Session> = call {
        val body = mapOf("email" to email, "password" to password, "data" to mapOf("username" to username))
        val json = post("/auth/v1/signup", body, token = null)
        sessionOf(json, fallbackName = username)?.let { CloudResult.Ok(it) }
            ?: CloudResult.Fail("Conta criada. Confirme o e-mail e entre para sincronizar.")
    }

    actual suspend fun signIn(email: String, password: String): CloudResult<Session> = call {
        val body = mapOf("email" to email, "password" to password)
        val json = post("/auth/v1/token?grant_type=password", body, token = null)
        sessionOf(json, fallbackName = email.substringBefore("@"))?.let { CloudResult.Ok(it) }
            ?: CloudResult.Fail("Não consegui abrir a sessão.")
    }

    actual suspend fun signInWithGoogle(idToken: String): CloudResult<Session> = call {
        val body = mapOf("provider" to "google", "id_token" to idToken)
        val json = post("/auth/v1/token?grant_type=id_token", body, token = null)
        sessionOf(json, fallbackName = "Comandante")?.let { CloudResult.Ok(it) }
            ?: CloudResult.Fail("Não consegui abrir a sessão com o Google.")
    }

    actual suspend fun refresh(refreshToken: String): CloudResult<Session> = call {
        val body = mapOf("refresh_token" to refreshToken)
        val json = post("/auth/v1/token?grant_type=refresh_token", body, token = null)
        sessionOf(json, fallbackName = "Comandante")?.let { CloudResult.Ok(it) }
            ?: CloudResult.Fail("Sessão expirada.")
    }

    actual suspend fun loadProfile(session: Session): CloudResult<CloudProfile?> = call {
        val json = get("/rest/v1/profiles?id=eq.${session.userId}&select=*", session.accessToken)
        val row = (json as? List<*>)?.firstOrNull() as? Map<*, *>
        if (row == null) {
            CloudResult.Ok(null)
        } else {
            CloudResult.Ok(
                CloudProfile(
                    username = row.strOr("username", session.username),
                    insignia = row.strOr("insignia", "anc"),
                    xp = row.intOr("xp", 0),
                    credits = row.intOr("credits", 500),
                    matches = row.intOr("matches", 0),
                    wins = row.intOr("wins", 0),
                    shots = row.intOr("shots", 0),
                    hits = row.intOr("hits", 0),
                    sunk = row.intOr("sunk", 0),
                    streak = row.intOr("streak", 0),
                    bestStreak = row.intOr("best_streak", 0),
                    owned = row.strOr("owned", "std,br"),
                    equipped = row.strOr("equipped", "br"),
                    fleets = row.strOr("fleets", "std"),
                    fleet = row.strOr("fleet", "std")
                )
            )
        }
    }

    actual suspend fun saveProfile(session: Session, profile: CloudProfile): CloudResult<Unit> = call {
        val body = mapOf(
            "id" to session.userId,
            "email" to session.email,
            "username" to profile.username,
            "insignia" to profile.insignia,
            "xp" to profile.xp,
            "credits" to profile.credits,
            "matches" to profile.matches,
            "wins" to profile.wins,
            "shots" to profile.shots,
            "hits" to profile.hits,
            "sunk" to profile.sunk,
            "streak" to profile.streak,
            "best_streak" to profile.bestStreak,
            "owned" to profile.owned,
            "equipped" to profile.equipped,
            "fleets" to profile.fleets,
            "fleet" to profile.fleet
        )
        post(
            path = "/rest/v1/profiles?on_conflict=id",
            body = body,
            token = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
        CloudResult.Ok(Unit)
    }

    actual suspend fun updatePassword(session: Session, newPassword: String): CloudResult<Unit> = call {
        put("/auth/v1/user", mapOf("password" to newPassword), session.accessToken)
        CloudResult.Ok(Unit)
    }

    actual suspend fun sendPasswordReset(email: String): CloudResult<Unit> = call {
        post("/auth/v1/recover", mapOf("email" to email), token = null)
        CloudResult.Ok(Unit)
    }

    // ------------------------------------------------------------------ HTTP

    private class SessionExpired : Exception("Sessão expirada.")

    private suspend fun <T> call(block: suspend () -> CloudResult<T>): CloudResult<T> {
        if (!SupabaseConfig.isConfigured) {
            return CloudResult.Fail("Servidor do jogo ainda não configurado.")
        }
        return try {
            block()
        } catch (e: SessionExpired) {
            CloudResult.Fail(e.message.orEmpty(), expired = true)
        } catch (e: Exception) {
            CloudResult.Fail(readable(e))
        }
    }

    private fun readable(e: Throwable): String {
        val raw = e.message ?: "Falha de conexão."
        return when {
            "Invalid login credentials" in raw -> "E-mail ou senha incorretos."
            "User already registered" in raw -> "Esse e-mail já tem conta. Faça login."
            "Password should be" in raw -> "A senha precisa de pelo menos 6 caracteres."
            "same_password" in raw || "should be different" in raw ->
                "A nova senha precisa ser diferente da atual."
            "rate limit" in raw.lowercase() -> "Muitas tentativas. Espere um pouco e tente de novo."
            "duplicate key" in raw && "username" in raw -> "Esse nome de usuário já está em uso."
            "Unable to validate email" in raw || "invalid format" in raw -> "E-mail inválido."
            raw.length > 120 -> "Não consegui falar com o servidor do jogo."
            else -> raw
        }
    }

    private suspend fun post(path: String, body: Map<String, Any?>, token: String?, prefer: String? = null): Any? =
        request("POST", path, body, token, prefer)

    private suspend fun put(path: String, body: Map<String, Any?>, token: String): Any? =
        request("PUT", path, body, token, null)

    private suspend fun get(path: String, token: String): Any? =
        request("GET", path, null, token, null)

    private suspend fun request(
        method: String,
        path: String,
        body: Map<String, Any?>?,
        token: String?,
        prefer: String?
    ): Any? = suspendCancellableCoroutine { cont ->
        val url = NSURL(string = SupabaseConfig.URL.trimEnd('/') + path)
        val req = NSMutableURLRequest(uRL = url)
        req.HTTPMethod = method
        req.setValue(SupabaseConfig.ANON_KEY, forHTTPHeaderField = "apikey")
        req.setValue("application/json", forHTTPHeaderField = "Content-Type")
        req.setValue("Bearer " + (token ?: SupabaseConfig.ANON_KEY), forHTTPHeaderField = "Authorization")
        prefer?.let { req.setValue(it, forHTTPHeaderField = "Prefer") }
        if (body != null) {
            req.HTTPBody = NSJSONSerialization.dataWithJSONObject(body, 0uL, null)
        }

        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, error ->
            if (error != null) {
                cont.resumeWithException(Exception(error.localizedDescription))
                return@dataTaskWithRequest
            }
            val http = response as? NSHTTPURLResponse
            val code = http?.statusCode?.toInt() ?: 0
            val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding) as String? }.orEmpty()

            if (code == 401 && "Invalid login credentials" !in text) {
                cont.resumeWithException(SessionExpired())
                return@dataTaskWithRequest
            }
            if (code !in 200..299) {
                val message = runCatching {
                    val parsed = parseJson(text) as? Map<*, *>
                    (parsed?.get("msg") ?: parsed?.get("error_description")
                        ?: parsed?.get("message") ?: parsed?.get("error")) as? String
                }.getOrNull().orEmpty()
                cont.resumeWithException(Exception(message.ifBlank { "HTTP $code" }))
                return@dataTaskWithRequest
            }
            cont.resume(if (text.isBlank()) null else parseJson(text))
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    private fun parseJson(text: String): Any? {
        val data = (text as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return null
        return NSJSONSerialization.JSONObjectWithData(data, 0uL, null)
    }

    private fun sessionOf(raw: Any?, fallbackName: String): Session? {
        val o = raw as? Map<*, *> ?: return null
        val token = o["access_token"] as? String
        if (token.isNullOrBlank()) return null
        val user = o["user"] as? Map<*, *> ?: return null
        val meta = user["user_metadata"] as? Map<*, *>
        val name = (meta?.get("username") as? String).orEmpty()
            .ifBlank { (meta?.get("full_name") as? String).orEmpty() }
            .ifBlank { (meta?.get("name") as? String).orEmpty() }
            .ifBlank { fallbackName }
        return Session(
            userId = (user["id"] as? String).orEmpty(),
            email = (user["email"] as? String).orEmpty(),
            username = name,
            accessToken = token,
            refreshToken = (o["refresh_token"] as? String).orEmpty()
        )
    }
}

private fun Map<*, *>.strOr(key: String, default: String): String = (this[key] as? String) ?: default

private fun Map<*, *>.intOr(key: String, default: Int): Int = when (val v = this[key]) {
    is Int -> v
    is Long -> v.toInt()
    is Double -> v.toInt()
    else -> default
}
