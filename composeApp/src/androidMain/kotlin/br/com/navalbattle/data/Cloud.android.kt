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

    actual suspend fun signInWithGoogle(idToken: String): CloudResult<Session> = call {
        val body = JSONObject().put("provider", "google").put("id_token", idToken)
        val json = post("/auth/v1/token?grant_type=id_token", body, token = null)
        val session = sessionOf(json, fallbackName = "Comandante")
            ?: return@call CloudResult.Fail("Não consegui abrir a sessão com o Google.")
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

    actual suspend fun updatePassword(session: Session, newPassword: String): CloudResult<Unit> = call {
        val body = JSONObject().put("password", newPassword)
        put("/auth/v1/user", body, session.accessToken)
        CloudResult.Ok(Unit)
    }

    actual suspend fun sendPasswordReset(email: String): CloudResult<Unit> = call {
        val body = JSONObject().put("email", email)
        post("/auth/v1/recover", body, token = null)
        CloudResult.Ok(Unit)
    }

    // ------------------------------------------------------------------ modo online

    actual suspend fun createOnlineMatch(
        session: Session,
        mode: String,
        quick: Boolean,
        inviteCode: String?,
        hostName: String
    ): CloudResult<OnlineMatch> = call {
        val body = JSONObject()
            .put("host_id", session.userId)
            .put("host_name", hostName)
            .put("mode", mode)
            .put("is_quick_match", quick)
            .put("invite_code", inviteCode)
        val json = post(
            "/rest/v1/online_matches",
            body,
            token = session.accessToken,
            prefer = "return=representation"
        )
        val row = JSONArray(json).getJSONObject(0)
        CloudResult.Ok(matchOf(row))
    }

    actual suspend fun findQuickMatch(session: Session, mode: String): CloudResult<OnlineMatch?> = call {
        val json = get(
            "/rest/v1/online_matches?status=eq.waiting&is_quick_match=is.true" +
                "&mode=eq.$mode&host_id=neq.${session.userId}&order=created_at.asc&limit=1",
            session.accessToken
        )
        val arr = JSONArray(json)
        CloudResult.Ok(if (arr.length() == 0) null else matchOf(arr.getJSONObject(0)))
    }

    actual suspend fun findMatchByCode(session: Session, code: String): CloudResult<OnlineMatch?> = call {
        val json = get(
            "/rest/v1/online_matches?invite_code=eq.$code&status=eq.waiting&limit=1",
            session.accessToken
        )
        val arr = JSONArray(json)
        CloudResult.Ok(if (arr.length() == 0) null else matchOf(arr.getJSONObject(0)))
    }

    actual suspend fun joinOnlineMatch(
        session: Session,
        matchId: String,
        guestName: String
    ): CloudResult<OnlineMatch?> = call {
        // via função (ver online.sql): HttpURLConnection não manda PATCH, e a
        // condição "guest_id ainda nulo" dentro da função é a trava contra dois
        // convidados entrando na mesma sala ao mesmo tempo
        val body = JSONObject()
            .put("p_match_id", matchId)
            .put("p_guest_name", guestName)
        val json = post("/rest/v1/rpc/join_online_match", body, token = session.accessToken)
        val arr = JSONArray(json)
        CloudResult.Ok(if (arr.length() == 0) null else matchOf(arr.getJSONObject(0)))
    }

    actual suspend fun getOnlineMatch(session: Session, matchId: String): CloudResult<OnlineMatch?> = call {
        val json = get("/rest/v1/online_matches?id=eq.$matchId&limit=1", session.accessToken)
        val arr = JSONArray(json)
        CloudResult.Ok(if (arr.length() == 0) null else matchOf(arr.getJSONObject(0)))
    }

    actual suspend fun closeOnlineMatch(session: Session, matchId: String, status: String): CloudResult<Unit> = call {
        val body = JSONObject().put("p_match_id", matchId).put("p_status", status)
        post("/rest/v1/rpc/close_online_match", body, token = session.accessToken)
        CloudResult.Ok(Unit)
    }

    actual suspend fun sendOnlineMessage(session: Session, matchId: String, body: String): CloudResult<Unit> = call {
        val json = JSONObject()
            .put("match_id", matchId)
            .put("sender_id", session.userId)
            .put("body", body)
        post("/rest/v1/online_messages", json, token = session.accessToken, prefer = "return=minimal")
        CloudResult.Ok(Unit)
    }

    actual suspend fun pollOnlineMessages(
        session: Session,
        matchId: String,
        afterId: Long
    ): CloudResult<List<OnlineMessage>> = call {
        val json = get(
            "/rest/v1/online_messages?match_id=eq.$matchId&id=gt.$afterId&order=id.asc" +
                "&select=id,sender_id,body",
            session.accessToken
        )
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OnlineMessage(id = o.getLong("id"), senderId = o.getString("sender_id"), body = o.getString("body"))
        }
        CloudResult.Ok(list)
    }

    // ------------------------------------------------------------------ amigos

    actual suspend fun searchCommander(session: Session, query: String): CloudResult<List<CommanderHit>> = call {
        val body = JSONObject().put("query", query)
        val json = post("/rest/v1/rpc/search_commander", body, token = session.accessToken)
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CommanderHit(id = o.getString("id"), username = o.getString("username"))
        }
        CloudResult.Ok(list)
    }

    actual suspend fun sendFriendRequest(
        session: Session,
        addresseeId: String,
        myUsername: String,
        theirUsername: String
    ): CloudResult<Unit> = call {
        val body = JSONObject()
            .put("requester_id", session.userId)
            .put("addressee_id", addresseeId)
            .put("requester_username", myUsername)
            .put("addressee_username", theirUsername)
        post("/rest/v1/friendships", body, token = session.accessToken, prefer = "return=minimal")
        CloudResult.Ok(Unit)
    }

    actual suspend fun respondFriendRequest(
        session: Session,
        friendshipId: String,
        accept: Boolean
    ): CloudResult<Unit> = call {
        val body = JSONObject().put("p_friendship_id", friendshipId).put("p_accept", accept)
        post("/rest/v1/rpc/respond_friend_request", body, token = session.accessToken)
        CloudResult.Ok(Unit)
    }

    actual suspend fun removeFriendship(session: Session, friendshipId: String): CloudResult<Unit> = call {
        delete("/rest/v1/friendships?id=eq.$friendshipId", session.accessToken)
        CloudResult.Ok(Unit)
    }

    actual suspend fun listFriendships(session: Session): CloudResult<List<Friendship>> = call {
        val json = get(
            "/rest/v1/friendships?or=(requester_id.eq.${session.userId},addressee_id.eq.${session.userId})",
            session.accessToken
        )
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { i -> friendshipOf(arr.getJSONObject(i)) }
        CloudResult.Ok(list)
    }

    // optString devolve "" tanto para campo ausente quanto para JSON null — para os
    // campos que fazem diferença (nulo é "ninguém entrou ainda"), checa com isNull
    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun matchOf(o: JSONObject): OnlineMatch = OnlineMatch(
        id = o.getString("id"),
        hostId = o.getString("host_id"),
        guestId = o.stringOrNull("guest_id"),
        hostName = o.optString("host_name"),
        guestName = o.stringOrNull("guest_name"),
        mode = o.optString("mode", "CLASSIC"),
        status = o.optString("status", "waiting"),
        isQuickMatch = o.optBoolean("is_quick_match", false),
        inviteCode = o.stringOrNull("invite_code")
    )

    private fun friendshipOf(o: JSONObject): Friendship = Friendship(
        id = o.getString("id"),
        requesterId = o.getString("requester_id"),
        addresseeId = o.getString("addressee_id"),
        requesterUsername = o.optString("requester_username"),
        addresseeUsername = o.optString("addressee_username"),
        status = o.optString("status", "pending")
    )

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
            "same_password" in raw || "should be different" in raw ->
                "A nova senha precisa ser diferente da atual."
            "rate limit" in raw.lowercase() -> "Muitas tentativas. Espere um pouco e tente de novo."
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

    private fun delete(path: String, token: String): String = read(open(path, "DELETE", token, null))

    private fun put(path: String, body: JSONObject, token: String): String {
        val conn = open(path, "PUT", token, null)
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return read(conn)
    }

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
        // login social (Google) não manda "username" — usa o nome de conta dele
        val name = meta?.optString("username").orEmpty()
            .ifBlank { meta?.optString("full_name").orEmpty() }
            .ifBlank { meta?.optString("name").orEmpty() }
            .ifBlank { fallbackName }
        return Session(
            userId = user.optString("id"),
            email = user.optString("email"),
            username = name,
            accessToken = token,
            refreshToken = o.optString("refresh_token")
        )
    }
}
