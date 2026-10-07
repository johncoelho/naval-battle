package br.com.navalbattle.data

/**
 * Push de verdade: convites de amigo, pedidos de amizade e adversário encontrado
 * chegam com o app fechado. Android via Firebase Cloud Messaging; o envio sai do
 * Supabase (supabase/push.sql + Edge Function `push`). No iPhone ainda não existe
 * (precisa de conta paga da Apple para o APNs) e [token] devolve nulo.
 */
expect object PushMessaging {
    /** "android" ou "ios" — vai junto com o token para o servidor. */
    val platform: String

    /** Token deste aparelho, ou nulo quando o push não está disponível. */
    suspend fun token(): String?
}
