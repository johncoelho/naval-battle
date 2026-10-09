package br.com.navalbattle.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import br.com.navalbattle.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

actual object PushMessaging {
    actual val platform: String = "android"

    /** Canal dos avisos de convite e amizade (o servidor manda channel_id = "social"). */
    const val CHANNEL = "social"

    actual suspend fun token(): String? {
        // sem google-services.json no build o Firebase não inicializa: push desligado
        if (runCatching { FirebaseApp.getInstance() }.isFailure) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    fun createChannel(context: Context, name: String) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_HIGH)
        )
    }
}

/**
 * Com o app fechado/em segundo plano o próprio Android mostra a notificação (payload
 * "notification"); com ele aberto, não se mostra nada — só avisa o jogo pelo
 * [PushInbox] para reler o que mudou (amizades, por exemplo). Token novo é
 * registrado na próxima vez que o app abre.
 */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        PushInbox.post(message.data["kind"].orEmpty())
    }

    override fun onNewToken(token: String) {
        // só no build de depuração: permite testar o envio pelo banco sem entrar na conta
        if (BuildConfig.DEBUG) Log.i("NavalPush", "token=$token")
    }
}
