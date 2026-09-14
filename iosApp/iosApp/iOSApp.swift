import SwiftUI
import ComposeApp

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
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
