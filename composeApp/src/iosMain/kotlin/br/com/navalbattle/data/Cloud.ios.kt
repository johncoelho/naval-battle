package br.com.navalbattle.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
// wildcard de propósito: várias propriedades/métodos de NSMutableURLRequest (HTTPMethod,
// HTTPBody, allHTTPHeaderFields) vêm de uma categoria Objective-C e o Kotlin/Native os
// expõe como extensão de nível de pacote — importar a classe sozinha não é suficiente,
// cada extensão precisa aparecer no import (foi isso que quebrou as três rodadas antes).
import platform.Foundation.*
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

    // ------------------------------------------------------------------ modo online

    actual suspend fun createOnlineMatch(
        session: Session,
        mode: String,
        quick: Boolean,
        inviteCode: String?,
        hostName: String
    ): CloudResult<OnlineMatch> = call {
        // sem entrada nenhuma para invite_code quando for nulo: NSDictionary não
        // aceita valor nulo de verdade (só o marcador NSNull), e um mapOf com null
        // aqui quebraria a ponte para Foundation em tempo de execução
        val body = buildMap<String, Any?> {
            put("host_id", session.userId)
            put("host_name", hostName)
            put("mode", mode)
            put("is_quick_match", quick)
            if (inviteCode != null) put("invite_code", inviteCode)
        }
        val json = post(
            "/rest/v1/online_matches",
            body,
            token = session.accessToken,
            prefer = "return=representation"
        )
        val row = (json as? List<*>)?.firstOrNull() as? Map<*, *>
        row?.let { CloudResult.Ok(matchOf(it)) } ?: CloudResult.Fail("Não consegui abrir a sala.")
    }

    actual suspend fun findQuickMatch(session: Session, mode: String): CloudResult<OnlineMatch?> = call {
        val json = get(
            "/rest/v1/online_matches?status=eq.waiting&is_quick_match=is.true" +
                "&mode=eq.$mode&host_id=neq.${session.userId}&order=created_at.asc&limit=1",
            session.accessToken
        )
        val row = (json as? List<*>)?.firstOrNull() as? Map<*, *>
        CloudResult.Ok(row?.let { matchOf(it) })
    }

    actual suspend fun findMatchByCode(session: Session, code: String): CloudResult<OnlineMatch?> = call {
        val json = get(
            "/rest/v1/online_matches?invite_code=eq.$code&status=eq.waiting&limit=1",
            session.accessToken
        )
        val row = (json as? List<*>)?.firstOrNull() as? Map<*, *>
        CloudResult.Ok(row?.let { matchOf(it) })
    }

    actual suspend fun joinOnlineMatch(
        session: Session,
        matchId: String,
        guestName: String
    ): CloudResult<OnlineMatch?> = call {
        // via função (ver online.sql): mantém o mesmo caminho do Android, que não
        // sabe mandar PATCH — a condição "guest_id ainda nulo" dentro da função é
        // a trava contra dois convidados entrando na mesma sala ao mesmo tempo
        val body = mapOf("p_match_id" to matchId, "p_guest_name" to guestName)
        val json = post("/rest/v1/rpc/join_online_match", body, token = session.accessToken)
        val row = (json as? List<*>)?.firstOrNull() as? Map<*, *>
        CloudResult.Ok(row?.let { matchOf(it) })
    }

    actual suspend fun getOnlineMatch(session: Session, matchId: String): CloudResult<OnlineMatch?> = call {
        val json = get("/rest/v1/online_matches?id=eq.$matchId&limit=1", session.accessToken)
        val row = (json as? List<*>)?.firstOrNull() as? Map<*, *>
        CloudResult.Ok(row?.let { matchOf(it) })
    }

    actual suspend fun closeOnlineMatch(session: Session, matchId: String, status: String): CloudResult<Unit> = call {
        val body = mapOf("p_match_id" to matchId, "p_status" to status)
        post("/rest/v1/rpc/close_online_match", body, token = session.accessToken)
        CloudResult.Ok(Unit)
    }

    actual suspend fun sendOnlineMessage(session: Session, matchId: String, body: String): CloudResult<Unit> = call {
        val json = mapOf("match_id" to matchId, "sender_id" to session.userId, "body" to body)
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
        val rows = (json as? List<*>).orEmpty()
        val list = rows.mapNotNull { it as? Map<*, *> }.map { o ->
            OnlineMessage(id = o.longOr("id", 0L), senderId = o.strOr("sender_id", ""), body = o.strOr("body", ""))
        }
        CloudResult.Ok(list)
    }

    // ------------------------------------------------------------------ amigos

    actual suspend fun searchCommander(session: Session, query: String): CloudResult<List<CommanderHit>> = call {
        val json = post("/rest/v1/rpc/search_commander", mapOf("query" to query), token = session.accessToken)
        val rows = (json as? List<*>).orEmpty()
        val list = rows.mapNotNull { it as? Map<*, *> }
            .map { o -> CommanderHit(id = o.strOr("id", ""), username = o.strOr("username", "")) }
        CloudResult.Ok(list)
    }

    actual suspend fun sendFriendRequest(
        session: Session,
        addresseeId: String,
        myUsername: String,
        theirUsername: String
    ): CloudResult<Unit> = call {
        val body = mapOf(
            "requester_id" to session.userId,
            "addressee_id" to addresseeId,
            "requester_username" to myUsername,
            "addressee_username" to theirUsername
        )
        post("/rest/v1/friendships", body, token = session.accessToken, prefer = "return=minimal")
        CloudResult.Ok(Unit)
    }

    actual suspend fun respondFriendRequest(
        session: Session,
        friendshipId: String,
        accept: Boolean
    ): CloudResult<Unit> = call {
        val body = mapOf("p_friendship_id" to friendshipId, "p_accept" to accept)
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
        val rows = (json as? List<*>).orEmpty()
        val list = rows.mapNotNull { it as? Map<*, *> }.map { friendshipOf(it) }
        CloudResult.Ok(list)
    }

    private fun matchOf(o: Map<*, *>): OnlineMatch = OnlineMatch(
        id = o.strOr("id", ""),
        hostId = o.strOr("host_id", ""),
        guestId = o["guest_id"] as? String,
        hostName = o.strOr("host_name", ""),
        guestName = o["guest_name"] as? String,
        mode = o.strOr("mode", "CLASSIC"),
        status = o.strOr("status", "waiting"),
        isQuickMatch = (o["is_quick_match"] as? Boolean) ?: false,
        inviteCode = o["invite_code"] as? String
    )

    private fun friendshipOf(o: Map<*, *>): Friendship = Friendship(
        id = o.strOr("id", ""),
        requesterId = o.strOr("requester_id", ""),
        addresseeId = o.strOr("addressee_id", ""),
        requesterUsername = o.strOr("requester_username", ""),
        addresseeUsername = o.strOr("addressee_username", ""),
        status = o.strOr("status", "pending")
    )

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

    private suspend fun delete(path: String, token: String): Any? =
        request("DELETE", path, null, token, null)

    private suspend fun request(
        method: String,
        path: String,
        body: Map<String, Any?>?,
        token: String?,
        prefer: String?
    ): Any? = suspendCancellableCoroutine { cont ->
        val url = NSURL(string = SupabaseConfig.URL.trimEnd('/') + path)!!
        val req = NSMutableURLRequest(uRL = url)
        // HTTPMethod/HTTPBody só têm getter na classe base NSURLRequest; quem "escreve"
        // é um método de categoria em NSMutableURLRequest (setHTTPMethod:, etc), não uma
        // propriedade — por isso são chamados como função, não como atribuição.
        req.setHTTPMethod(method)
        val headers = mutableMapOf(
            "apikey" to SupabaseConfig.ANON_KEY,
            "Content-Type" to "application/json",
            "Authorization" to ("Bearer " + (token ?: SupabaseConfig.ANON_KEY))
        )
        prefer?.let { headers["Prefer"] = it }
        // NSDictionary não é tipado do lado Objective-C, então o binding pede
        // Map<Any?, *> em vez do Map<String, String> concreto que temos.
        @Suppress("UNCHECKED_CAST")
        req.setAllHTTPHeaderFields(headers as Map<Any?, *>)
        if (body != null) {
            req.setHTTPBody(NSJSONSerialization.dataWithJSONObject(body, 0uL, null))
        }

        // Lambda posicional (sem nomear o parâmetro do completion handler): o binding do
        // Kotlin/Native não preserva "completionHandler" como nome de parâmetro utilizável.
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
        return NSJSONSerialization.JSONObjectWithData(stringToData(text), 0uL, null)
    }

    // Evita o vaivém incerto de NSString.dataUsingEncoding no binding do Kotlin/Native —
    // aqui a conversão passa pelos bytes UTF-8 puros, que não dependem de nenhum
    // método específico de NSString existir com o nome exato esperado.
    private fun stringToData(text: String): NSData {
        val bytes = text.encodeToByteArray()
        if (bytes.isEmpty()) return NSData()
        return bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
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

private fun Map<*, *>.longOr(key: String, default: Long): Long = when (val v = this[key]) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> v.toLong()
    else -> default
}
