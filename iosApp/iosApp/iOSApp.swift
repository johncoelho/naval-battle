import SwiftUI
import UIKit
import UserNotifications
import ComposeApp

/// Push no iPhone (#14), sem SDK: o sistema entrega o token do APNs e o toque na notificação aqui.
/// O Kotlin pede o registro (`PushMessaging.ios.kt`) e recebe o resultado pelo `PushBridge`.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // antes de a abertura terminar: senão o toque que abriu o app fechado se perde
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        PushBridge.shared.onToken(hex: hex)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        PushBridge.shared.onError(message: error.localizedDescription)
    }

    /// App aberto: não mostra o aviso (o jogo já tem os balões), só manda reler o servidor, como no Android.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if let kind = notification.request.content.userInfo["kind"] as? String {
            PushInbox.shared.post(kind: kind)
        }
        completionHandler([])
    }

    /// Toque no aviso: o Kotlin leva à mesma tela que no Android (`screenForPushKind`).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let kind = response.notification.request.content.userInfo["kind"] as? String {
            PushInbox.shared.open(kind: kind)
        }
        completionHandler()
    }
}

/// Ponte para o `UIViewController` que o Kotlin cria em
/// `composeApp/src/iosMain/kotlin/br/com/navalbattle/MainViewController.kt`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
