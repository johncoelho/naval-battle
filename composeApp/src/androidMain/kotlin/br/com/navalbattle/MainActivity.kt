package br.com.navalbattle

import android.content.Intent
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
}
