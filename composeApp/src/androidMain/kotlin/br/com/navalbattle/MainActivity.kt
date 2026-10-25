package br.com.navalbattle

import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.navalbattle.audio.AppForeground
import br.com.navalbattle.audio.AudioContextHolder
import br.com.navalbattle.data.PushInbox
import br.com.navalbattle.data.PushMessaging

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Celular segue em retrato; tablet, dobrável e Chromebook (>= 600 dp) giram. O manifesto
        // não trava a orientação porque o Android 16 ignora a trava em tela grande (#37)
        if (resources.configuration.smallestScreenWidthDp < LARGE_SCREEN_DP) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        AudioContextHolder.appContext = applicationContext
        ActivityHolder.current = this
        // o canal precisa existir antes do primeiro push chegar com o app fechado
        PushMessaging.createChannel(applicationContext, getString(R.string.push_channel_name))
        // aberto pelo toque num push: o FCM põe o data.kind nos extras (#19)
        if (savedInstanceState == null) openFromPush(intent)
        setContent { App() }
    }

    // singleTask: com o jogo já aberto em segundo plano, o toque chega por aqui
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromPush(intent)
    }

    private fun openFromPush(intent: Intent?) {
        intent?.getStringExtra("kind")?.let { PushInbox.open(it) }
    }

    // dobrar, desdobrar ou dividir a janela chega aqui (configChanges) sem recriar a Activity:
    // a partida em andamento não se perde. O Compose relê o tamanho sozinho
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestedOrientation = if (newConfig.smallestScreenWidthDp < LARGE_SCREEN_DP) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onPause() {
        super.onPause()
        AppForeground.active = false
    }

    override fun onResume() {
        super.onResume()
        AppForeground.active = true
    }

    override fun onDestroy() {
        if (ActivityHolder.current == this) ActivityHolder.current = null
        super.onDestroy()
    }

    private companion object {
        /** Menor lado em dp a partir do qual o aparelho é tela grande (classe de tamanho "medium"). */
        const val LARGE_SCREEN_DP = 600
    }
}
