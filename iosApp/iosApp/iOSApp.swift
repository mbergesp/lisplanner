import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Antes de mostrar la interfaz, para recibir los toques en las notificaciones.
        MainViewControllerKt.setupPlatform()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                // XML de MS Project abierto desde Archivos, Mail, AirDrop…
                .onOpenURL { url in MainViewControllerKt.openFile(url: url) }
        }
    }
}

/** Toda la interfaz es la de Compose Multiplatform, compartida con Android. */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
