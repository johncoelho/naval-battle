import XCTest
import UIKit

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

    /// Qualquer falha depois do login (inclusive as do XCTest, como "not hittable") deixa PNG e árvore na saída.
    override func tearDownWithError() throws {
        if loggedIn, let r = testRun, r.totalFailureCount > 0 {
            shot("diagnostico-falha")
            print("ARVORE: \(String(app.debugDescription.prefix(8000)))")
        }
    }

    func testCapturasDosQuatroProdutos() throws {
        let env = ProcessInfo.processInfo.environment
        guard let email = env["IOS_REVIEW_EMAIL"]?.trimmingCharacters(in: .whitespacesAndNewlines), !email.isEmpty,
              let password = env["IOS_REVIEW_PASSWORD"]?.trimmingCharacters(in: .whitespacesAndNewlines), !password.isEmpty else {
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
        if env["SESSAO_SEMEADA"] == "1" {
            // o workflow gravou a sessão no armazenamento do app (os campos do Compose não aceitam texto por XCUITest)
            guard element("Loja|Store|Tienda").waitForExistence(timeout: 90) else {
                let textos = app.staticTexts.allElementsBoundByIndex.prefix(60).map { $0.label }
                print("sessao semeada, sem Loja; textos: \(textos)")
                return fail("Loja não apareceu com a sessão semeada")
            }
        } else {
        try tap("Entrar ou criar conta|Sign in or create account|Entrar o crear cuenta", timeout: 60)
        try tap("Entrar(?! com| ou)|Sign in(?! with| or)", timeout: 15, index: 0)
        sleep(2)
        shot("diagnostico-login-antes-de-digitar") // nada digitado ainda: sem credenciais na tela
        // diagnóstico ANTES de digitar (sem valores): tipo, rótulo e posição dos elementos da tela de login
        let todos = app.descendants(matching: .any).allElementsBoundByIndex.prefix(80)
        for e in todos { print("LOGIN-UI tipo=\(e.elementType.rawValue) rotulo=\(e.label) y=\(Int(e.frame.minY)) h=\(Int(e.frame.height))") }
        var pe = CGPoint.zero, ps = CGPoint.zero
        let campos = app.textFields
        if campos.firstMatch.exists {
            campos.firstMatch.tap()
            app.typeText(email)
            let senha = app.secureTextFields.firstMatch
            if senha.exists { senha.tap() } else { campos.element(boundBy: 1).tap() }
            app.typeText(password)
        } else {
            // Compose não expõe o campo como TextField: toca logo abaixo do rótulo e digita no foco, caractere a caractere
            pe = try pontoAbaixo("E-?mail|Correo")
            ps = try pontoAbaixo("Senha|Password|Contraseña")
            preencher(pe, email, nome: "email")
            preencher(ps, password, nome: "senha")
            relatar("email", pe, email)
            relatar("senha", ps, password)
        }
        // só botões com rótulo exatamente "Entrar": a aba é a de cima, o envio é o de baixo
        let enviar = app.buttons.matching(NSPredicate(format: "label ==[c] 'Entrar' OR label ==[c] 'Sign in'"))
        let envios = (0..<enviar.count).map { enviar.element(boundBy: $0) }.sorted { $0.frame.minY < $1.frame.minY }
        print("botoes Entrar: \(envios.map { Int($0.frame.minY) }) teclados=\(app.keyboards.count)")
        guard let envio = envios.last, envios.count >= 2 else { return fail("Botão de envio do login não achado") }
        envio.tap()
        // espera até 8 s por mensagem do app ou pela Loja
        _ = element("Loja|Store|Tienda").waitForExistence(timeout: 8)
        if !element("Loja|Store|Tienda").exists {
            let msgs = app.staticTexts.allElementsBoundByIndex.map { $0.label }.filter { !$0.isEmpty }
            print("APOS-ENVIO textos: \(msgs)")
            // limpa os dois campos (apagando) para a tela de diagnóstico não mostrar e-mail nem senha
            for p in [pe, ps] where p != .zero { limpar(p) }
            shot("diagnostico-apos-envio-campos-limpos")
            // 2ª tentativa: cola pela área de transferência (long press + Colar), caso a digitação não chegue ao campo
            if pe != .zero {
                print("TENTATIVA 2: colar")
                colar(pe, email); colar(ps, password)
                relatar("email2", pe, email)
                let envios2 = (0..<enviar.count).map { enviar.element(boundBy: $0) }.sorted { $0.frame.minY < $1.frame.minY }
                if let e2 = envios2.last { e2.tap() }
                _ = element("Loja|Store|Tienda").waitForExistence(timeout: 10)
                if !element("Loja|Store|Tienda").exists {
                    print("APOS-ENVIO 2 textos: \(app.staticTexts.allElementsBoundByIndex.map { $0.label }.filter { !$0.isEmpty })")
                    for p in [pe, ps] { limpar(p) }
                    shot("diagnostico-apos-envio2-campos-limpos")
                }
            }
        }
        guard element("Loja|Store|Tienda").waitForExistence(timeout: 40) else {
            return fail("Loja não apareceu depois do login (conta sem acesso ou login recusado)")
        }
        }
        loggedIn = true

        // --- Loja > Dobrões ---
        // conta nova: avisos cobrem a tela (alerta de notificações do sistema, presente de boas-vindas, temporada,
        // Diário de bordo, "Entendi" da rosa dos ventos), em ordem que varia. Laço único, só botões, por prioridade.
        // O Diário fecha em FECHAR; só faz o check-in se não houver FECHAR.
        let prioridade = [
            "FECHAR|Close|Cerrar",
            "VALEU|Thanks|Gracias",
            "AGORA NÃO|Not now|Ahora no",
            "ENTENDI|Got it|Entendido",
            "PULAR|Skip|Saltar",
            "FAZER CHECK-IN.*|Check in.*|Hacer check-in.*",
        ]
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        var vazios = 0
        var ciclos = 0
        while ciclos < 14 && vazios < 2 {
            ciclos += 1
            var feito = false
            for r in ["Allow", "Permitir"] where springboard.buttons[r].exists {
                springboard.buttons[r].tap(); feito = true; break
            }
            if !feito {
                for regex in prioridade {
                    let p = NSPredicate(format: "label MATCHES[c] %@", "(?s)(\(regex))[ !.]*(\n.*)?")
                    let b = app.buttons.matching(p).firstMatch
                    if b.exists { print("aviso: tocando \(regex.prefix(12))"); tocarSeguro(b); feito = true; break }
                }
            }
            if feito { vazios = 0; sleep(2) } else { vazios += 1; sleep(3) }
        }
        print("avisos fechados em \(ciclos) ciclos; botoes visiveis: \(app.buttons.allElementsBoundByIndex.prefix(40).map { $0.label })")
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

    /// Espera o elemento ficar tocável (até 10 s); se não ficar, toca no centro do quadro por coordenada.
    private func tocarSeguro(_ e: XCUIElement) {
        let fim = Date().addingTimeInterval(10)
        while Date() < fim && !e.isHittable { usleep(500_000) }
        if e.isHittable { e.tap(); return }
        let f = e.frame
        print("elemento não tocável; toque por coordenada em (\(Int(f.midX)), \(Int(f.midY)))")
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: f.midX, dy: f.midY)).tap()
    }

    private func pontoAbaixo(_ regex: String) throws -> CGPoint {
        let p = NSPredicate(format: "label MATCHES[c] %@", "(?s).*(\(regex)).*")
        let r = app.descendants(matching: .any).matching(p).firstMatch
        guard r.waitForExistence(timeout: 10) else { fail("Rótulo não achado: \(regex)"); throw NSError(domain: "StoreShots", code: 3) }
        let f = r.frame
        return CGPoint(x: app.frame.midX, y: f.maxY + f.height * 1.6)
    }

    private func tocar(_ pt: CGPoint) {
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: pt.x, dy: pt.y)).tap()
    }

    /// Toca o campo, confere o teclado e digita caractere a caractere (o Compose perdia o texto digitado de uma vez).
    private func preencher(_ pt: CGPoint, _ texto: String, nome: String) {
        tocar(pt)
        sleep(1)
        print("CAMPO \(nome): teclados=\(app.keyboards.count) tamanho do texto=\(texto.count)")
        for c in texto { app.typeText(String(c)); usleep(80_000) }
    }

    /// Estado do campo sem mostrar valores: tipo, tamanho do valor e se é igual ao esperado.
    private func relatar(_ nome: String, _ pt: CGPoint, _ esperado: String) {
        for e in app.descendants(matching: .any).allElementsBoundByIndex where e.frame.contains(pt) && e.frame.height < 140 {
            let v = (e.value as? String) ?? ""
            print("CAMPO \(nome): tipo=\(e.elementType.rawValue) rotulo.count=\(e.label.count) valor.count=\(v.count) igual=\(v == esperado) foco=\(e.hasFocus)")
        }
    }

    private func colar(_ pt: CGPoint, _ texto: String) {
        UIPasteboard.general.string = texto
        tocar(pt)
        sleep(1)
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: pt.x, dy: pt.y)).press(forDuration: 1.5)
        sleep(1)
        let sb = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for host in [app!, sb] {
            for r in ["Paste", "Colar", "Pegar"] {
                if host.menuItems[r].exists { host.menuItems[r].tap(); break }
                if host.buttons[r].exists { host.buttons[r].tap(); break }
            }
            sleep(1)
            for r in ["Allow Paste", "Permitir colar", "Permitir Colar", "Permitir pegar"] where host.buttons[r].exists { host.buttons[r].tap() }
        }
        sleep(1)
    }

    private func limpar(_ pt: CGPoint) {
        tocar(pt)
        sleep(1)
        app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 80))
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
        print("botoes visiveis na falha: \(app.buttons.allElementsBoundByIndex.prefix(60).map { $0.label })")
        if loggedIn { shot("diagnostico-falha") }
        else {
            let textos = app.staticTexts.allElementsBoundByIndex.prefix(120).map { $0.label }
            print("botoes na tela: \(app.buttons.allElementsBoundByIndex.prefix(60).map { $0.label })")
            print("textos na tela (sem campos): \(textos)")
        }
        XCTFail(msg)
    }
}
