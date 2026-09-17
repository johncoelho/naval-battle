package br.com.navalbattle.data

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import br.com.navalbattle.ActivityHolder
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

actual class GoogleAuth actual constructor() {

    actual suspend fun signIn(): GoogleAuthResult {
        val activity = ActivityHolder.current
            ?: return GoogleAuthResult.Fail("Não consegui abrir a tela do Google agora.")
        if (!GoogleAuthConfig.isConfigured) {
            return GoogleAuthResult.Fail("Login do Google ainda não configurado neste aparelho.")
        }

        val option = GetSignInWithGoogleOption.Builder(GoogleAuthConfig.WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleAuthResult.Fail("A conta escolhida não devolveu uma credencial do Google.")
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleAuthResult.Ok(token.idToken)
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (e: GoogleIdTokenParsingException) {
            GoogleAuthResult.Fail("Não consegui ler a credencial do Google.")
        } catch (e: GetCredentialException) {
            // e.message às vezes vem "" (vazio, não nulo) em vez de null — o "?:"
            // não pega esse caso, e a tela trata mensagem vazia como cancelamento
            // silencioso (ver AccountSection em ProfileScreen), escondendo o erro de verdade
            val detail = e.message?.takeIf { it.isNotBlank() }
            GoogleAuthResult.Fail(
                if (detail != null) "Não consegui entrar com o Google: $detail"
                else "Não consegui entrar com o Google (${e::class.simpleName})."
            )
        }
    }
}
