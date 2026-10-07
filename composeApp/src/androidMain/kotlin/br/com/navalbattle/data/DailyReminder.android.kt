package br.com.navalbattle.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.MainActivity
import br.com.navalbattle.R
import br.com.navalbattle.audio.AudioContextHolder
import java.util.Calendar

actual object DailyReminder {
    private const val CHANNEL = "daily_logbook"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    // último lembrete agendado, para refazer depois de reiniciar o aparelho ou atualizar
    // o app (o Android apaga os alarmes nos dois casos)
    private const val PREFS = "daily_reminder"
    private const val ASKED = "permission_asked"

    actual fun requestPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val activity = ActivityHolder.current ?: return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    actual fun schedule(title: String, body: String, skipToday: Boolean) {
        val context = runCatching { AudioContextHolder.appContext }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(EXTRA_TITLE, title).putString(EXTRA_BODY, body).apply()
        scheduleAt(context, nextFire(skipToday), title, body)
    }

    actual fun cancel() {
        val context = runCatching { AudioContextHolder.appContext }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(EXTRA_TITLE).remove(EXTRA_BODY).apply()
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, "", ""))
    }

    actual fun sendTest(title: String, body: String) {
        val context = runCatching { AudioContextHolder.appContext }.getOrNull() ?: return
        requestPermission()
        // alarme inexato poderia atrasar minutos; com o app aberto um Handler basta. Se o
        // diálogo de permissão ainda estiver na tela, espera a resposta (até 30s)
        val handler = Handler(Looper.getMainLooper())
        val manager = context.getSystemService(NotificationManager::class.java)
        var waited = 0
        val attempt = object : Runnable {
            override fun run() {
                if (manager?.areNotificationsEnabled() == false && waited < 30) {
                    waited++
                    handler.postDelayed(this, 1000L)
                } else {
                    show(context, title, body, id = 2)
                }
            }
        }
        handler.postDelayed(attempt, TEST_DELAY_SECONDS * 1000L)
    }

    actual fun checkAllowed(onResult: (Boolean) -> Unit) {
        val context = runCatching { AudioContextHolder.appContext }.getOrNull() ?: return onResult(true)
        val enabled = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: true
        // Android 13+ ainda sem perguntar também dá "desligado" — isso não é bloqueio
        val neverAsked = Build.VERSION.SDK_INT >= 33 &&
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)
        onResult(enabled || neverAsked)
    }

    actual fun openSystemSettings() {
        val context = ActivityHolder.current ?: runCatching { AudioContextHolder.appContext }.getOrNull() ?: return
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Aparelho reiniciou ou o app foi atualizado: refaz o próximo lembrete salvo. */
    internal fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(EXTRA_TITLE, null) ?: return
        val body = prefs.getString(EXTRA_BODY, null) ?: return
        scheduleAt(context, nextFire(skipToday = false), title, body)
    }

    /** Próximas 19h — hoje, se ainda não passou e não é para pular o dia. */
    private fun nextFire(skipToday: Boolean): Long {
        val now = Calendar.getInstance()
        val fire = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (skipToday || !fire.after(now)) fire.add(Calendar.DAY_OF_YEAR, 1)
        return fire.timeInMillis
    }

    private fun pending(context: Context, title: String, body: String): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // alarme inexato de propósito: não exige permissão de alarme exato e alguns
    // minutos de atraso não fazem diferença num lembrete do dia
    private fun scheduleAt(context: Context, at: Long, title: String, body: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, title, body))
    }

    internal fun fire(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        if (title.isBlank()) return
        show(context, title, body, id = 1)
        // o de amanhã já fica agendado, mesmo que o app não seja aberto
        scheduleAt(context, nextFire(skipToday = true), title, body)
    }

    private fun show(context: Context, title: String, body: String, id: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // sem a permissão (Android 13+) o sistema descarta calado; a tela de Ajustes avisa
        runCatching { manager.notify(id, notification) }
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> DailyReminder.restore(context)
            else -> DailyReminder.fire(context, intent)
        }
    }
}
