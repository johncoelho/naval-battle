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
import br.com.navalbattle.game.ReminderKind
import br.com.navalbattle.game.ReminderSlot
import java.util.Calendar

actual object DailyReminder {
    private const val CHANNEL = "daily_logbook"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_KIND = "kind"
    // plano agendado ("instante,tipo;instante,tipo") e os textos de cada tipo, para refazer depois
    // de reiniciar o aparelho ou atualizar o app (o Android apaga os alarmes nos dois casos)
    private const val PREFS = "daily_reminder"
    private const val PLAN = "plan"
    private const val ASKED = "permission_asked"

    actual fun requestPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val activity = ActivityHolder.current ?: return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    actual fun schedule(slots: List<ReminderSlot>, texts: ReminderTexts) {
        val context = runCatching { AudioContextHolder.appContext }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val plan = slots.map { fireAt(it) to it.kind }.filter { it.first > now }.sortedBy { it.first }
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ReminderKind.values().forEach { kind ->
            edit.putString(titleKey(kind), texts.of(kind).title).putString(bodyKey(kind), texts.of(kind).body)
        }
        edit.putString(PLAN, encode(plan)).remove(EXTRA_TITLE).remove(EXTRA_BODY).apply()
        scheduleNext(context)
    }

    actual fun cancel() {
        val context = runCatching { AudioContextHolder.appContext }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(PLAN).remove(EXTRA_TITLE).remove(EXTRA_BODY).apply()
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, ""))
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

    /** Aparelho reiniciou ou o app foi atualizado: refaz o próximo aviso do plano salvo. */
    internal fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(PLAN, null) == null) {
            // versão antiga do app: só havia um lembrete diário às 19h
            val title = prefs.getString(EXTRA_TITLE, null) ?: return
            val body = prefs.getString(EXTRA_BODY, null) ?: return
            val daily = fireAt(ReminderSlot(0, REMINDER_HOUR, ReminderKind.DAILY))
            val at = if (daily > System.currentTimeMillis()) daily else daily + DAY_MS
            prefs.edit().putString(titleKey(ReminderKind.DAILY), title).putString(bodyKey(ReminderKind.DAILY), body)
                .putString(PLAN, encode(listOf(at to ReminderKind.DAILY))).apply()
        }
        scheduleNext(context)
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun titleKey(kind: ReminderKind) = "title_" + kind.name
    private fun bodyKey(kind: ReminderKind) = "body_" + kind.name

    private fun encode(plan: List<Pair<Long, ReminderKind>>) = plan.joinToString(";") { "${it.first},${it.second.name}" }

    private fun decode(raw: String?): List<Pair<Long, ReminderKind>> =
        raw.orEmpty().split(";").mapNotNull { part ->
            val bits = part.split(",")
            val at = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val kind = runCatching { ReminderKind.valueOf(bits.getOrNull(1).orEmpty()) }.getOrNull() ?: return@mapNotNull null
            at to kind
        }

    /** Instante do aviso: hoje + [ReminderSlot.dayOffset] dias, na hora do relógio do aparelho. */
    private fun fireAt(slot: ReminderSlot): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, slot.dayOffset)
        set(Calendar.HOUR_OF_DAY, slot.hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun pending(context: Context, kind: String): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).putExtra(EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // alarme inexato de propósito: não exige permissão de alarme exato e alguns
    // minutos de atraso não fazem diferença num lembrete do dia (o de retorno, às 12h,
    // continua dentro da janela de 11h às 15h na maioria dos aparelhos)
    private fun scheduleNext(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val next = decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PLAN, null))
            .firstOrNull { it.first > now }
        if (next == null) {
            alarms.cancel(pending(context, ""))
            return
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.first, pending(context, next.second.name))
    }

    internal fun fire(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kind = runCatching { ReminderKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrNull() ?: return
        val title = prefs.getString(titleKey(kind), null).orEmpty()
        val body = prefs.getString(bodyKey(kind), null).orEmpty()
        if (title.isBlank()) return
        show(context, title, body, id = 1)
        // o próximo do plano já fica agendado, mesmo que o app não seja aberto
        val now = System.currentTimeMillis()
        val rest = decode(prefs.getString(PLAN, null)).filter { it.first > now }
        prefs.edit().putString(PLAN, encode(rest)).apply()
        scheduleNext(context)
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
