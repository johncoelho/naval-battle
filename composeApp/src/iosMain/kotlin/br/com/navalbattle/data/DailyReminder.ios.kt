package br.com.navalbattle.data

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSinceDate
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS não deixa o app reagendar sozinho em segundo plano, então a semana inteira
 * fica agendada de uma vez (um pedido por dia, ids fixos) e é refeita a cada abertura.
 */
actual object DailyReminder {
    private const val DAYS = 7
    private val ids = (0 until DAYS).map { "daily_logbook_$it" }

    private val center: UNUserNotificationCenter
        get() = UNUserNotificationCenter.currentNotificationCenter()

    actual fun requestPermission() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ -> }
    }

    actual fun schedule(title: String, body: String, skipToday: Boolean) {
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        val calendar = NSCalendar.currentCalendar
        val now = NSDate()
        var slot = 0
        var offset = if (skipToday) 1L else 0L
        while (slot < DAYS && offset <= DAYS) {
            val day = calendar.dateByAddingUnit(NSCalendarUnitDay, offset, now, 0uL)
            val fire = day?.let { calendar.dateBySettingHour(REMINDER_HOUR.toLong(), 0, 0, it, 0uL) }
            offset++
            if (fire == null || fire.timeIntervalSinceDate(now) <= 0.0) continue
            val parts = calendar.components(
                NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute,
                fire
            )
            val content = UNMutableNotificationContent()
            content.setTitle(title)
            content.setBody(body)
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(parts, repeats = false)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(ids[slot], content, trigger),
                withCompletionHandler = null
            )
            slot++
        }
    }

    actual fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(ids)
    }

    // o iPhone não mostra notificação com o app aberto na frente: o texto do botão
    // pede para minimizar o jogo durante os 5 segundos
    actual fun sendTest(title: String, body: String) {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions
            val content = UNMutableNotificationContent()
            content.setTitle(title)
            content.setBody(body)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(TEST_DELAY_SECONDS.toDouble(), repeats = false)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier("daily_logbook_test", content, trigger),
                withCompletionHandler = null
            )
        }
    }

    actual fun checkAllowed(onResult: (Boolean) -> Unit) {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            // "ainda não perguntado" não é bloqueio: o pedido sai no check-in ou no teste
            val allowed = settings?.authorizationStatus != UNAuthorizationStatusDenied
            dispatch_async(dispatch_get_main_queue()) { onResult(allowed) }
        }
    }

    actual fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
