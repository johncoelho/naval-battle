import XCTest

/// Capturas de revisão da compra (Apple, #65): roda só no CI (`ios-qa.yml`, modo `capturas-iap`), com o plano
/// `StoreShots.xctestplan` que carrega `tools/ios-qa/NavalIAP.storekit` como StoreKit Configuration. O app é o
/// da loja já compilado para o simulador, lançado por bundle id; nada aqui entra no binário.
///
/// Credenciais: vêm do ambiente (`TEST_RUNNER_IOS_REVIEW_EMAIL` / `..._PASSWORD`, secrets do GitHub). Nenhuma
/// captura é feita antes de o login terminar e nada é anexado ao xcresult; o workflow apaga o xcresult depois.
final class StoreShotsUITests: XCTestCase {
    private let appId = "br.com.navalbattle"
    private var app: XCUIApplication!
    private var outDir = ""
    private var loggedIn = false

    override func setUpWithError() throws {
        continueAfterFailure = false
        let env = ProcessInfo.processInfo.environment
        outDir = env["SHOTS_DIR"] ?? NSTemporaryDirectory()
        try? FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)
    }

    func testCapturasDosQuatroProdutos() throws {
        let env = ProcessInfo.processInfo.environment
        guard let email = env["IOS_REVIEW_EMAIL"], !email.isEmpty,
              let password = env["IOS_REVIEW_PASSWORD"], !password.isEmpty else {
            XCTFail("Faltam IOS_REVIEW_EMAIL e IOS_REVIEW_PASSWORD no ambiente do teste")
            return
        }

        // notificação do sistema depois do login
        addUIInterruptionMonitor(withDescription: "notificacoes") { alert in
            for rotulo in ["Allow", "Permitir", "Don’t Allow", "Don't Allow", "Não permitir"] {
                let b = alert.buttons[rotulo]
                if b.exists { b.tap(); return true }
            }
            return false
        }

        app = XCUIApplication(bundleIdentifier: appId)
        app.launch()

        // --- login (nenhuma captura de tela nesta fase) ---
        try tap("Entrar ou criar conta|Sign in or create account|Entrar o crear cuenta", timeout: 60)
        try tap("Entrar(?! com| ou)|Sign in(?! with| or)", timeout: 15, index: 0)
        sleep(2)
        shot("diagnostico-login-antes-de-digitar") // nada digitado ainda: sem credenciais na tela
        // diagnóstico ANTES de digitar (sem valores): tipo, rótulo e posição dos elementos da tela de login
        let todos = app.descendants(matching: .any).allElementsBoundByIndex.prefix(80)
        for e in todos { print("LOGIN-UI tipo=\(e.elementType.rawValue) rotulo=\(e.label) y=\(Int(e.frame.minY)) h=\(Int(e.frame.height))") }
        let campos = app.textFields
        if campos.firstMatch.exists {
            campos.firstMatch.tap()
            app.typeText(email)
            let senha = app.secureTextFields.firstMatch
            if senha.exists { senha.tap() } else { campos.element(boundBy: 1).tap() }
            app.typeText(password)
        } else {
            // Compose não expõe o campo como TextField: toca logo abaixo do rótulo e digita no foco
            try tapBelow("E-?mail|Correo")
            app.typeText(email)
            try tapBelow("Senha|Password|Contraseña")
            app.typeText(password)
        }
        // o 2º "Entrar" é o botão de enviar (o 1º é a aba)
        dismissKeyboard()
        try tap("Entrar(?! com| ou)|Sign in(?! with| or)", timeout: 10, index: 1)
        guard element("Loja|Store|Tienda").waitForExistence(timeout: 45) else {
            return fail("Loja não apareceu depois do login (conta sem acesso ou login recusado)")
        }
        loggedIn = true

        // --- Loja > Dobrões ---
        let pular = element("Pular|Skip|Saltar")
        if pular.waitForExistence(timeout: 5) { pular.tap() }
        try tap("Loja|Store|Tienda", timeout: 15)
        try tap("Dobrões|Doubloons|Doblones", timeout: 20)
        guard element("Bolsa|Pouch").waitForExistence(timeout: 25) else {
            return fail("Aba Dobrões sem pacotes (vitrine fechada para a conta ou StoreKit sem produtos)")
        }
        XCTAssertTrue(element("R\\$.?4,90").exists, "preço de R$ 4,90 ausente: StoreKit Configuration não entrou")
        XCTAssertTrue(element("R\\$.?49,90").exists, "preço de R$ 49,90 ausente")
        XCTAssertFalse(element("beta|android|google play").exists, "texto proibido na vitrine")
        shot("iap-00-vitrine")

        let produtos: [(String, String)] = [
            ("Bolsa|Pouch", "iap-01-dobroes_bolsa"),
            ("Baú|Chest|Baúl", "iap-02-dobroes_bau"),
            ("Cofre|Strongbox|Caja fuerte", "iap-03-dobroes_cofre"),
            ("Tesouro do Almirante|Admiral.s Treasure|Tesoro del Almirante", "iap-04-dobroes_tesouro"),
        ]
        for (rotulo, nome) in produtos {
            try tap(rotulo, timeout: 15)
            // a folha de compra do StoreKit sobe sobre o app
            sleep(5)
            shot(nome)
            cancelarFolha()
            sleep(2)
        }
    }

    // MARK: - ajudas

    /// Primeiro elemento cujo rótulo casa (regex, sem diferenciar maiúsculas), preferindo botão e texto.
    private func element(_ regex: String) -> XCUIElement {
        let p = NSPredicate(format: "label MATCHES[c] %@", "(?s).*(\(regex)).*")
        for q in [app.buttons, app.staticTexts, app.otherElements] as [XCUIElementQuery] {
            let e = q.matching(p).firstMatch
            if e.exists { return e }
        }
        return app.descendants(matching: .any).matching(p).firstMatch
    }

    private func tap(_ regex: String, timeout: TimeInterval, index: Int = 0) throws {
        let p = NSPredicate(format: "label MATCHES[c] %@", "(?s).*(\(regex)).*")
        let todos = app.descendants(matching: .any).matching(p)
        let fim = Date().addingTimeInterval(timeout)
        while Date() < fim {
            if todos.count > index {
                // ordena por posição na tela para o índice valer como no roteiro antigo (de cima para baixo)
                let ordenados = (0..<todos.count).map { todos.element(boundBy: $0) }
                    .filter { $0.exists && $0.isHittable }
                    .sorted { $0.frame.minY < $1.frame.minY }
                var vistos = Set<String>()
                let unicos = ordenados.filter { vistos.insert("\(Int($0.frame.minY / 8))-\(Int($0.frame.minX / 8))").inserted }
                if unicos.count > index { unicos[index].tap(); return }
            }
            usleep(500_000)
        }
        fail("Não achei na tela: \(regex) (índice \(index))")
        throw NSError(domain: "StoreShots", code: 1)
    }

    /// Toca logo abaixo do rótulo (campos do Compose não aparecem como TextField).
    private func tapBelow(_ regex: String) throws {
        let p = NSPredicate(format: "label MATCHES[c] %@", "(?s).*(\(regex)).*")
        let r = app.descendants(matching: .any).matching(p).firstMatch
        guard r.waitForExistence(timeout: 10) else { fail("Rótulo não achado: \(regex)"); throw NSError(domain: "StoreShots", code: 2) }
        let f = r.frame
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: app.frame.midX, dy: f.maxY + f.height * 1.6)).tap()
        sleep(1)
    }

    private func dismissKeyboard() {
        if app.keyboards.count > 0 {
            for rotulo in ["Return", "return", "Done", "OK", "ok"] where app.keyboards.buttons[rotulo].exists {
                app.keyboards.buttons[rotulo].tap(); return
            }
        }
    }

    /// Cancela a folha de compra sem comprar nada (o botão vive no SpringBoard ou no próprio app).
    private func cancelarFolha() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for host in [springboard, app!] {
            for rotulo in ["Cancel", "Cancelar", "Fechar", "Close"] {
                let b = host.buttons[rotulo]
                if b.exists && b.isHittable { b.tap(); return }
            }
        }
    }

    /// PNG da tela inteira (inclui a folha do StoreKit, que é de outro processo). 1290x2796 no iPhone 16 Plus.
    private func shot(_ nome: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        let url = URL(fileURLWithPath: outDir).appendingPathComponent("\(nome).png")
        do { try png.write(to: url) } catch { XCTFail("Não gravou \(url.path): \(error)") }
    }

    /// Falha com diagnóstico: só depois do login a tela é capturada (antes dele há o e-mail digitado).
    private func fail(_ msg: String) {
        if loggedIn { shot("diagnostico-falha") }
        else {
            let textos = app.staticTexts.allElementsBoundByIndex.prefix(120).map { $0.label }
            print("botoes na tela: (app.buttons.allElementsBoundByIndex.prefix(60).map { $0.label })")
            print("textos na tela (sem campos): \(textos)")
        }
        XCTFail(msg)
    }
}
