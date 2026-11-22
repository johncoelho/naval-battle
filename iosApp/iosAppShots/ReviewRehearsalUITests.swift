import XCTest
import UIKit

/// Ensaio de revisão da Apple (#118): faz o que o revisor faz, antes de enviar. Roda só no CI (`ios-qa.yml`, modo
/// `ensaio-revisao`), num simulador de iPad (o revisor usou iPad Air 11" M3), com a sessão da conta de demonstração
/// semeada pelo workflow. Toca os 4 pacotes de Dobrões, o Passe de Temporada, as milhas e os botões de compra da Loja;
/// cada toque precisa de resposta visível em até 10 s (folha da Apple, popup, mensagem ou tela diferente). Varre também
/// o texto de todas as telas visitadas contra `tools/ios-forbidden-terms.txt` (Android, Google Play, beta, assinatura).
///
/// Regras de segurança (inegociáveis):
/// - nenhum `typeText`, nenhuma senha: a sessão vem semeada e este teste nunca digita nada;
/// - a folha de login do Apple ID nunca é fotografada nem tem o conteúdo registrado: se ela aparece, o teste só
///   registra "folha de login do sistema" e a cancela;
/// - nada é anexado ao xcresult (o workflow o apaga); só PNGs e `ensaio-revisao.md` saem do runner.
///
/// Ensaio vermelho = não enviar à Apple. O que o relatório afirma é só o que foi provado aqui: as notas de revisão
/// (skill `apple-review`) saem dele.
final class ReviewRehearsalUITests: XCTestCase {
    private var app: XCUIApplication!
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    private var outDir = ""
    private var terms: [NSRegularExpression] = []
    private var linhas: [String] = []
    private var falhas: [String] = []
    private var avisos: [String] = []
    private var violacoes: [String] = []
    private var telasVarridas: [String] = []
    private var passo = 0
    /// Diagnóstico para o relatório: limites da tela, e a árvore de acessibilidade resumida quando algo não é achado.
    private var diag: [String] = []
    private var arvores: [String] = []
    private let limite: TimeInterval = 10
    /// Estado da conta de demonstração nesta rodada (`sem-saldo` | `com-saldo`), preparado antes por tools/ios-qa/demo-estado.sql.
    private var estado = ""
    /// Menos rótulos que isto numa tela varrida = árvore vazia (a varredura "passaria" sem ver nada): falha.
    private var minRotulos = 8

    override func setUpWithError() throws {
        let env = ProcessInfo.processInfo.environment
        try XCTSkipUnless(env["ENSAIO"] == "1", "só roda no modo ensaio-revisao do ios-qa.yml")
        continueAfterFailure = true
        estado = env["ESTADO"] ?? ""
        if let n = Int(env["MIN_ROTULOS"] ?? ""), n > 0 { minRotulos = n }
        outDir = env["SHOTS_DIR"] ?? NSTemporaryDirectory()
        try? FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)
        if let caminho = env["TERMS_FILE"], let texto = try? String(contentsOfFile: caminho, encoding: .utf8) {
            for linha in texto.components(separatedBy: .newlines) {
                let l = linha.trimmingCharacters(in: .whitespaces)
                if l.isEmpty || l.hasPrefix("#") { continue }
                if let rx = try? NSRegularExpression(pattern: l, options: [.caseInsensitive]) { terms.append(rx) }
            }
        }
    }

    override func tearDownWithError() throws {
        guard ProcessInfo.processInfo.environment["ENSAIO"] == "1" else { return }
        escreverRelatorio()
    }

    // MARK: - roteiro

    func testEnsaioDeRevisao() throws {
        XCTAssertFalse(terms.isEmpty, "lista de termos proibidos não carregada (TERMS_FILE)")
        XCTAssertTrue(["sem-saldo", "com-saldo"].contains(estado), "ESTADO deve ser sem-saldo ou com-saldo (recebido: \(estado))")
        app = XCUIApplication()
        app.launch()
        shotSeguro("00-abertura")

        // sessão semeada pelo workflow: sem login, sem digitação
        guard element("Loja|Store|Tienda|Estaleiro|Shipyard").waitForExistence(timeout: 90) else {
            falhar("Abertura", "o app não chegou ao menu com a sessão semeada (90 s)")
            return
        }
        // o app é só de iPhone (TARGETED_DEVICE_FAMILY = 1): no iPad roda em modo de compatibilidade; os limites ajudam a ler os frames
        diag.append("app.frame = \(app.frame); janela = \(app.windows.firstMatch.frame); tela = \(XCUIScreen.main.screenshot().image.size)")
        shotSeguro("01-menu-inicial")
        varrer("menu (primeira tela)")
        // avisos que cobrem a tela da conta (notificação, Valeu, Diário de bordo...) ficam para depois: o convite do Passe tem de aparecer
        shotSeguro("02-antes-do-passe")

        ensaioPasse()
        ensaioPacotes()
        ensaioMilhas()
        ensaioCompraDaLoja()
        varreduraDasTelas()

        if !falhas.isEmpty { XCTFail("Ensaio vermelho: \(falhas.count) falha(s). Não enviar à Apple.") }
    }

    // MARK: - Passe de Temporada

    private let ctaPasse = "Entrar com [0-9]|Join with [0-9]|Entrar con [0-9]|Fazer upgrade com|Upgrade with|Mejorar con"
    private let popupsDoPasse = "Dobrões insuficientes|Not enough doubloons|Doblones insuficientes|Tudo certo|All set|Todo listo|Sem conexão|No connection|Sin conexión|Não deu certo|That didn.t work|No funcionó"

    /// "Primeira missão" (COMEÇAR / PULAR): sempre PULAR, nunca COMEÇAR (iniciaria o tutorial). Com o app 0.47.1 ela cede a vez ao Passe;
    /// aqui fecha se aparecer, para não cobrir o que o ensaio toca.
    private func pularPrimeiraMissao() {
        for _ in 0..<3 where existe("Primeira missão|First mission|Primera misión") {
            let p = NSPredicate(format: "label MATCHES[c] %@", "(?s)(PULAR|Skip|Saltar)[ !.]*")
            let b = app.buttons.matching(p).firstMatch
            guard b.exists else { break }
            shotSeguro("passe-primeira-missao")
            b.tap()
            sleep(2)
        }
    }

    private func ensaioPasse() {
        let semSaldo = estado == "sem-saldo"
        pularPrimeiraMissao()
        guard abrirPasse() else {
            falhar("Passe", "o popup do Passe não abriu (conta sem temporada?)")
            return
        }
        varrer("Passe de Temporada")
        guard ctaPorId() || existe(ctaPasse) else {
            // demo já com o Passe, ou estado errado: sem o CTA o ensaio não prova nada do Passe
            falhar("Passe", "CTA \"Entrar com N dobrões\" ausente (conta demo já com passe? prepare o estado \(estado) com tools/ios-qa/demo-estado.sql)")
            fecharPasse()
            return
        }
        // a linha que diz que o passe é pago com a moeda do jogo precisa estar na tela
        if existe("Pago com os dobrões|Paid with in-game doubloons|Se paga con los doblones") {
            registrar("Passe", "texto \"pago com os dobrões do jogo\"", "visível na tela", 0, nil)
        } else {
            falhar("Passe", "a linha \"pago com os dobrões do jogo\" não aparece junto do preço")
        }
        if existe("Comprar passe|Buy pass|Comprar pase") { falhar("Passe", "rótulo antigo \"Comprar passe\" ainda aparece") }

        let insuficiente = "Dobrões insuficientes|Not enough doubloons|Doblones insuficientes"
        let sucesso = "Tudo certo|All set|Todo listo"
        pularPrimeiraMissao()
        let r = tocarEEsperar("Passe: CTA de entrada (\(estado))", ctaPasse, esperado: semSaldo ? insuficiente : sucesso, estrito: true)
        if r == nil { fecharAvisosDoPasse(); return }
        if semSaldo {
            guard existe(insuficiente) else {
                falhar("Passe sem saldo", "esperava o popup \"Dobrões insuficientes\"; a conta demo tem saldo? (estado \(estado))")
                fecharAvisosDoPasse()
                return
            }
            registrar("Passe sem saldo", "popup \"Dobrões insuficientes\"", "visível sem rolar", 0, shotSeguro("passe-sem-saldo"))
            if tocarEEsperar("Passe: atalho para a loja de Dobrões", "Abrir a loja de Dobr|Open the Doubloon store|Abrir la tienda de Doblones",
                             esperado: "Bolsa|Pouch", estrito: true) != nil {
                registrar("Passe sem saldo", "atalho \"Abrir a loja de Dobrões\"", "abriu a aba Dobrões", 0, shotSeguro("passe-atalho-loja"))
                varrer("Loja > Dobrões (pelo atalho do Passe)")
                voltarAoMenu()
            }
        } else {
            guard existe(sucesso) else {
                falhar("Passe com saldo", "esperava o popup \"Tudo certo\"; a conta demo tem saldo para o passe? (estado \(estado))")
                fecharAvisosDoPasse()
                return
            }
            registrar("Passe com saldo", "popup de confirmação \"Tudo certo\"", "visível", 0, shotSeguro("passe-com-saldo"))
            _ = tocarEEsperar("Passe: OK da confirmação", "^OK$", esperado: nil, estrito: false)
        }
        fecharAvisosDoPasse()
    }

    private func abrirPasse() -> Bool {
        for ciclo in 0..<12 {
            if ciclo == 3 || ciclo == 8 { shotSeguro("passe-procura-\(ciclo)"); registrarArvore("Passe: ciclo \(ciclo) sem o CTA") }
            if ctaPorId() || existe(ctaPasse) || existe("Passe de Temporada|Season Pass|Pase de Temporada") && existe("Agora não|Not now|Ahora no|Fechar|Close|Cerrar") { return true }
            // o convite abre sozinho em conta sem passe; senão, o chip "Temporada de..." do menu
            if existe("Temporada de|Season$|Season of|Temporada$") {
                if tocar("Temporada de|Season of|Temporada", espera: 3) { sleep(2); continue }
            }
            fecharAvisos(excluindo: "AGORA NÃO|Not now|Ahora no")
            sleep(2)
        }
        return existe(ctaPasse) || existe("Passe de Temporada|Season Pass|Pase de Temporada")
    }

    /// O CTA do Passe tem `testTag("season_join_cta")` no Compose (identificador estável, não depende do idioma).
    private func ctaPorId() -> Bool { app.descendants(matching: .any)["season_join_cta"].exists }

    private func fecharPasse() {
        _ = tocar("Fechar|Close|Cerrar|Agora não|Not now|Ahora no", espera: 3)
        sleep(1)
    }

    private func fecharAvisosDoPasse() {
        for _ in 0..<3 {
            if existe("^OK$") { _ = tocar("^OK$", espera: 2); sleep(1) }
            if existe("Agora não|Not now|Ahora no|Fechar|Close|Cerrar") { _ = tocar("Fechar|Close|Cerrar|Agora não|Not now|Ahora no", espera: 2); sleep(1) }
        }
    }

    // MARK: - Dobrões: os 4 pacotes

    private func irParaDobroes() -> Bool {
        limparAvisos()
        voltarAoMenu()
        shotSeguro("10-antes-da-loja")
        guard tocar("Loja|Store|Tienda", espera: 15) else { falhar("Loja", "botão da Loja não encontrado"); return false }
        sleep(2)
        guard tocar("Dobrões|Doubloons|Doblones", espera: 20) else { falhar("Loja", "aba Dobrões não aparece (venda fechada para a conta?)"); return false }
        return element("Bolsa|Pouch").waitForExistence(timeout: 25)
    }

    private func ensaioPacotes() {
        guard irParaDobroes() else {
            falhar("Dobrões", "a vitrine não mostrou os pacotes (Bolsa, Baú, Cofre, Tesouro)")
            return
        }
        varrer("Loja > Dobrões")
        shotSeguro("20-dobroes-vitrine")
        let pacotes: [(String, String, String)] = [
            ("Bolsa", "Bolsa|Pouch", "pacote-1"),
            ("Baú", "Baú|Chest|Baúl", "pacote-2"),
            ("Cofre", "Cofre|Strongbox|Caja fuerte", "pacote-3"),
            ("Tesouro do Almirante", "Tesouro do Almirante|Admiral.s Treasure|Tesoro del Almirante", "pacote-4"),
        ]
        let mensagem = "indisponíve|unavailable|no disponible|Não foi possível|Couldn.t|No se pudo|pendente|pending|pendiente"
        for (nome, regex, png) in pacotes {
            let r = tocarEEsperar("Pacote \(nome)", regex, esperado: mensagem, estrito: false, png: png)
            if r != nil { fecharFolhaDeCompra() }
            sleep(2)
        }
    }

    // MARK: - Milhas

    private func ensaioMilhas() {
        // milhas gastam dobrões da demo: só na rodada sem-saldo (a com-saldo precisa do saldo intacto para o Passe)
        guard estado == "sem-saldo" else {
            registrar("Milhas", "não exercitado nesta rodada", "estado \(estado): milhas só no sem-saldo", 0, nil)
            return
        }
        guard existe("Milhas náuticas|Nautical miles|Millas náuticas") || irParaDobroes() else { return }
        guard tocar("Milhas náuticas|Nautical miles|Millas náuticas", espera: 10) else {
            avisar("Milhas", "linha de milhas não encontrada na aba Dobrões: NÃO exercitada")
            return
        }
        sleep(2)
        varrer("Milhas náuticas")
        shotSeguro("30-milhas")
        _ = tocarEEsperar("Milhas: comprar pacote", "Comprar [0-9]+ milha|Buy [0-9]+ mile|Comprar [0-9]+ milla",
                          esperado: "Faltam|short|Faltan|comprou|bought|compraste|Limite|limit|Sem conexão|No connection|Sin conexión|Não foi possível|Couldn.t|No se pudo|Você tem|You have|Tienes",
                          estrito: true)
        _ = tocar("Fechar|Close|Cerrar", espera: 3)
        sleep(1)
    }

    // MARK: - Botões de compra da Loja (cascos, camuflagens, habilidades)

    private func ensaioCompraDaLoja() {
        voltarAoMenu()
        guard tocar("Loja|Store|Tienda", espera: 15) else { falhar("Loja", "botão da Loja não encontrado"); return }
        sleep(2)
        for (aba, regex) in [("Cascos", "Cascos|Hulls|Cascos"), ("Camuflagens", "Camuflagens|Camos|Camuflajes"), ("Habilidades", "Habilidades|Abilities|Habilidades")] {
            guard tocar(regex, espera: 8) else {
                avisar("Loja", "aba \(aba) não encontrada: NÃO exercitada")
                registrarArvore("Loja: aba \(aba) não encontrada")
                continue
            }
            sleep(2)
            varrer("Loja > \(aba)")
            shotSeguro("40-loja-" + aba.lowercased())
            // botão de preço de um item que ainda não é da conta: abre o cartão de confirmação ou explica o que falta
            guard let botao = primeiroPreco() else { avisar("Loja", "nenhum botão de preço visível em \(aba): NÃO exercitado"); continue }
            let antes = assinatura()
            botao.tap()
            if esperarMudar(antes) != nil || confirmacaoAberta() {
                registrar("Loja \(aba)", "botão de preço do primeiro item à venda", confirmacaoAberta() ? "cartão de confirmação" : "tela mudou", 0, nil)
                if confirmacaoAberta() { _ = tocar("Cancelar|Cancel", espera: 5) }
            } else {
                falhar("Loja \(aba)", "toque no botão de preço sem resposta em \(Int(limite)) s")
            }
        }
    }

    private func primeiroPreco() -> XCUIElement? {
        let p = NSPredicate(format: "label MATCHES %@", "(?s)^[0-9][0-9.,]*$")
        let candidatos = app.descendants(matching: .any).matching(p).allElementsBoundByIndex
            .filter { $0.exists && $0.isHittable && $0.frame.minY > app.frame.height * 0.25 }
            .sorted { $0.frame.minY < $1.frame.minY }
        return candidatos.first
    }

    private func confirmacaoAberta() -> Bool { existe("Cancelar|Cancel") && existe("Comprar|Buy|Confirm") }

    // MARK: - varredura de texto das telas

    private func varreduraDasTelas() {
        voltarAoMenu()
        let telas: [(String, String)] = [
            ("Perfil", "Perfil|Profile"),
            ("Configurações", "Configurações|Settings|Configuración"),
            ("Amigos", "Amigos|Friends"),
            ("Ranking", "Placar|Ranking|Clasificación"),
            ("Estaleiro", "Estaleiro|Shipyard|Astillero"),
        ]
        for (nome, regex) in telas {
            guard tocar(regex, espera: 6) else {
                avisar("Telas", "\(nome): entrada não encontrada, varredura de texto desta tela NÃO feita")
                registrarArvore("Telas: entrada \(nome) não encontrada")
                continue
            }
            sleep(3)
            shotSeguro("50-tela-" + nome.lowercased())
            varrer(nome)
            if nome == "Configurações", tocar("Novidades|What.s new|Novedades", espera: 4) {
                sleep(2)
                varrer("Novidades")
            }
            voltarAoMenu()
        }
        // sem o menu inicial, a varredura teria falhado antes; as telas do Passe, Loja e Milhas foram varridas no caminho
        registrar("Varredura de texto", "\(telasVarridas.count) telas", violacoes.isEmpty ? "nenhum termo proibido" : "\(violacoes.count) ocorrência(s)", 0, nil)
        if !violacoes.isEmpty { falhar("Varredura de texto", "termo proibido na tela: \(violacoes.prefix(8).joined(separator: " | "))") }
    }

    /// Lê os rótulos da árvore de acessibilidade do app (uma só chamada) e confere contra a lista de termos.
    private func varrer(_ tela: String) {
        telasVarridas.append(tela)
        let rotulos = rotulosVisiveis()
        if rotulos.count < minRotulos {
            falhar("Varredura de texto", "a tela \"\(tela)\" devolveu só \(rotulos.count) rótulo(s) (mínimo \(minRotulos)): árvore vazia, a varredura não viu nada")
        }
        for rotulo in rotulos {
            let r = NSRange(rotulo.startIndex..., in: rotulo)
            for rx in terms where rx.firstMatch(in: rotulo, range: r) != nil {
                violacoes.append("[\(tela)] \"\(rotulo.prefix(80))\" casa /\(rx.pattern)/")
            }
        }
    }

    private func rotulosVisiveis() -> [String] {
        let dump = app.debugDescription
        var achados: [String] = []
        for linha in dump.components(separatedBy: "\n") {
            for chave in ["label: '", "value: '", "title: '"] {
                guard let ini = linha.range(of: chave) else { continue }
                let resto = linha[ini.upperBound...]
                if let fim = resto.range(of: "'", options: .backwards) { achados.append(String(resto[..<fim.lowerBound])) }
            }
        }
        return achados
    }

    // MARK: - toques com resposta obrigatória

    /// Toca o elemento e exige resposta em até 10 s. `estrito`: só vale `esperado` (ou folha da Apple); senão vale também
    /// qualquer mudança na árvore. Devolve a descrição da resposta, ou nil (e registra a falha) se ficou mudo.
    @discardableResult
    private func tocarEEsperar(_ nome: String, _ regex: String, esperado: String?, estrito: Bool, png: String? = nil) -> String? {
        guard let alvo = achar(regex, espera: 10) else {
            falhar(nome, "não encontrei na tela: \(regex)")
            return nil
        }
        let antes = assinatura()
        let inicio = Date()
        alvo.tap()
        var resposta: String?
        while Date().timeIntervalSince(inicio) < limite {
            if let esperado = esperado, existe(esperado) { resposta = "mensagem/popup esperado"; break }
            switch folhaDoSistema() {
            case .compra: resposta = "folha de compra da Apple"
            case .login: resposta = "folha de login do sistema (não registrada)"
            case .nenhuma: break
            }
            if resposta != nil { break }
            if !estrito, assinatura() != antes { resposta = "tela mudou"; break }
            usleep(500_000)
        }
        let seg = Date().timeIntervalSince(inicio)
        guard let r = resposta else {
            falhar(nome, "toque sem resposta visível em \(Int(limite)) s")
            _ = shotSeguro("falha-" + String(passo + 1))
            return nil
        }
        let arquivo: String? = (r == "folha de login do sistema (não registrada)") ? nil : shotSeguro(png ?? "resp-\(passo + 1)")
        registrar(nome, "toque", r, seg, arquivo)
        return r
    }

    private func esperarMudar(_ antes: String) -> String? {
        let fim = Date().addingTimeInterval(limite)
        while Date() < fim {
            if assinatura() != antes { return "tela mudou" }
            usleep(500_000)
        }
        return nil
    }

    /// Só os rótulos (sem posição): animações não contam como resposta.
    private func assinatura() -> String { rotulosVisiveis().joined(separator: "¦") }

    // MARK: - folhas do sistema (compra e login do Apple ID)

    private enum Folha { case nenhuma, compra, login }

    private func hostsDoSistema() -> [XCUIApplication] {
        [springboard, XCUIApplication(bundleIdentifier: "com.apple.StoreKitUIService"), XCUIApplication(bundleIdentifier: "com.apple.AuthKitUIService")]
    }

    /// Só os processos do sistema contam (o `Cancelar` do próprio app, do cartão de confirmação da Loja, não é folha).
    /// Com botão de cancelar visível: tem campo de texto ou rótulo de login = folha de login do Apple ID; senão, compra.
    private func folhaDoSistema() -> Folha {
        for host in hostsDoSistema() {
            let temCancelar = host.buttons["Cancel"].exists || host.buttons["Cancelar"].exists
            if !temCancelar { continue }
            if host.secureTextFields.count > 0 || host.textFields.count > 0 { return .login }
            let rotuloLogin = NSPredicate(format: "label MATCHES[c] %@", "(?s).*(Sign In|Entrar|Iniciar sess|Password|Senha|Contraseña).*")
            if host.descendants(matching: .any).matching(rotuloLogin).firstMatch.exists { return .login }
            return .compra
        }
        return .nenhuma
    }

    /// Cancela a folha sem comprar nada e sem digitar nada.
    private func fecharFolhaDeCompra() {
        for host in hostsDoSistema() + [app!] {
            for rotulo in ["Cancel", "Cancelar", "Fechar", "Close", "Not Now", "Agora não"] {
                let b = host.buttons[rotulo]
                if b.exists && b.isHittable { b.tap(); sleep(1); return }
            }
        }
    }

    // MARK: - navegação e ajudas

    private func voltarAoMenu() {
        for _ in 0..<8 {
            if element("Estaleiro|Shipyard|Astillero").exists && element("Amigos|Friends").exists && element("Placar|Ranking|Clasificación").exists { return }
            if folhaDoSistema() != .nenhuma { fecharFolhaDeCompra() }
            fecharAvisos(excluindo: nil)
            if existe("^(Voltar|Back|Volver)$") { _ = tocar("^(Voltar|Back|Volver)$", espera: 2) }
            sleep(1)
        }
    }

    /// Mesmo laço do StoreShotsUITests (que acha a vitrine com esta conta): fecha notificação, Valeu, Agora não, Entendi,
    /// Fechar do Diário de bordo, Pular e o check-in, até duas voltas seguidas sem nada para fechar.
    private func limparAvisos() {
        let prioridade = [
            "FECHAR|Close|Cerrar", "VALEU|Thanks|Gracias", "AGORA NÃO|Not now|Ahora no", "ENTENDI|Got it|Entendido", "PULAR|Skip|Saltar",
            "FAZER CHECK-IN.*|Check in.*|Hacer check-in.*",
        ]
        var vazios = 0
        var ciclos = 0
        while ciclos < 8 && vazios < 2 {
            ciclos += 1
            var feito = false
            for r in ["Allow", "Permitir"] where springboard.buttons[r].exists {
                springboard.buttons[r].tap(); feito = true; break
            }
            if !feito {
                for regex in prioridade {
                    let p = NSPredicate(format: "label MATCHES[c] %@", "(?s)(\(regex))[ !.]*(\n.*)?")
                    let b = app.buttons.matching(p).firstMatch
                    if b.exists && b.isHittable { b.tap(); feito = true; break }
                }
            }
            if feito { vazios = 0; sleep(2) } else { vazios += 1; sleep(3) }
        }
        diag.append("limparAvisos: \(ciclos) ciclos")
    }

    /// Fecha os avisos que cobrem a tela (presente, Diário de bordo, rosa dos ventos), em ordem de prioridade.
    private func fecharAvisos(excluindo: String?) {
        let prioridade = [
            "FECHAR|Close|Cerrar", "VALEU|Thanks|Gracias", "AGORA NÃO|Not now|Ahora no", "ENTENDI|Got it|Entendido", "PULAR|Skip|Saltar",
        ]
        for r in ["Allow", "Permitir"] where springboard.buttons[r].exists { springboard.buttons[r].tap(); return }
        for regex in prioridade where regex != excluindo {
            let p = NSPredicate(format: "label MATCHES[c] %@", "(?s)(\(regex))[ !.]*(\n.*)?")
            let b = app.buttons.matching(p).firstMatch
            if b.exists && b.isHittable { b.tap(); sleep(1); return }
        }
    }

    private func lbl(_ regex: String) -> NSPredicate { NSPredicate(format: "label MATCHES[c] %@", "(?s).*(\(regex)).*") }

    private func element(_ regex: String) -> XCUIElement {
        for q in [app.buttons, app.staticTexts, app.otherElements] as [XCUIElementQuery] {
            let e = q.matching(lbl(regex)).firstMatch
            if e.exists { return e }
        }
        return app.descendants(matching: .any).matching(lbl(regex)).firstMatch
    }

    /// Só conta elemento visível: o frame inteiro dentro da tela do app (texto fora da dobra ou de outra camada não vale).
    private func existe(_ regex: String) -> Bool {
        // limites: a janela do app (no iPad em modo de compatibilidade o app.frame pode não ser a área desenhada), com 2 pt de folga
        let janela = app.windows.firstMatch
        let limites = (janela.exists && !janela.frame.isEmpty ? janela.frame : app.frame).insetBy(dx: -2, dy: -2)
        let q = app.descendants(matching: .any).matching(lbl(regex))
        let n = min(q.count, 20)
        var achouFora = false
        for i in 0..<n {
            let e = q.element(boundBy: i)
            guard e.exists, !e.frame.isEmpty else { continue }
            if limites.contains(e.frame) { return true }
            achouFora = true
        }
        if achouFora { diag.append("existe(\(regex.prefix(40))): achado, mas com o frame fora de \(limites) (não conta)") }
        return false
    }

    /// Resume a árvore de acessibilidade do app (rótulos, identificadores e frames) para o relatório; só do app, nunca de folhas do sistema.
    private func registrarArvore(_ titulo: String) {
        var out: [String] = []
        for linha in app.debugDescription.components(separatedBy: "\n") where linha.contains("label: '") || linha.contains("identifier: '") {
            out.append(String(linha.trimmingCharacters(in: .whitespaces).prefix(170)))
            if out.count >= 90 { break }
        }
        arvores.append("### \(titulo)\n" + out.map { "- `\($0)`" }.joined(separator: "\n"))
        if arvores.count > 12 { arvores.removeFirst() }
    }

    /// Primeiro elemento tocável (de cima para baixo) cujo rótulo casa.
    private func achar(_ regex: String, espera: TimeInterval) -> XCUIElement? {
        let todos = app.descendants(matching: .any).matching(lbl(regex))
        let fim = Date().addingTimeInterval(espera)
        while Date() < fim {
            let ordenados = (0..<todos.count).map { todos.element(boundBy: $0) }
                .filter { $0.exists && $0.isHittable }
                .sorted { $0.frame.minY < $1.frame.minY }
            if let e = ordenados.first { return e }
            usleep(500_000)
        }
        return nil
    }

    @discardableResult
    private func tocar(_ regex: String, espera: TimeInterval) -> Bool {
        guard let e = achar(regex, espera: espera) else { return false }
        e.tap()
        return true
    }

    // MARK: - relatório e capturas

    /// PNG da tela inteira, exceto se a folha de login do Apple ID está aberta (nunca fotografada).
    @discardableResult
    private func shotSeguro(_ nome: String) -> String? {
        if folhaDoSistema() == .login { return nil }
        let png = XCUIScreen.main.screenshot().pngRepresentation
        // nome do arquivo sempre ASCII (sem acento): o artefato precisa abrir em qualquer sistema
        let limpo = String(nome.folding(options: .diacriticInsensitive, locale: nil).filter { $0.isASCII })
        let arquivo = "\(limpo).png"
        do { try png.write(to: URL(fileURLWithPath: outDir).appendingPathComponent(arquivo)) } catch { return nil }
        return arquivo
    }

    private func registrar(_ nome: String, _ toque: String, _ resposta: String, _ seg: TimeInterval, _ png: String?) {
        passo += 1
        linhas.append("| \(passo) | \(nome) | \(toque) | \(resposta) | \(String(format: "%.1f", seg)) s | \(png ?? "-") |")
    }

    private func falhar(_ nome: String, _ motivo: String) {
        passo += 1
        falhas.append("\(nome): \(motivo)")
        let png = shotSeguro("falha-\(passo)-" + nome.lowercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) })
        registrarArvore("\(nome): \(motivo)")
        linhas.append("| \(passo) | \(nome) | - | **FALHA**: \(motivo) | - | \(png ?? "-") |")
        XCTFail("\(nome): \(motivo)")
    }

    private func avisar(_ nome: String, _ texto: String) {
        avisos.append("\(nome): \(texto)")
    }

    private func escreverRelatorio() {
        let verde = falhas.isEmpty
        var md = "# Ensaio de revisão (#118)\n\n"
        md += "RESULTADO: \(verde ? "VERDE" : "VERMELHO")\n\n"
        md += "Simulador: \(UIDevice.current.model), iOS \(UIDevice.current.systemVersion). Regra: cada toque exige resposta em até \(Int(limite)) s.\n"
        md += "Nada foi digitado; a folha de login do Apple ID nunca é fotografada.\n\n"
        md += "| # | Passo | Ação | Resposta | Tempo | PNG |\n|---|---|---|---|---|---|\n"
        md += linhas.joined(separator: "\n") + "\n\n"
        if !diag.isEmpty { md += "## Diagnóstico\n" + diag.prefix(40).map { "- \($0)" }.joined(separator: "\n") + "\n\n" }
        if !arvores.isEmpty { md += "## Árvore de acessibilidade quando algo não foi achado\n" + arvores.joined(separator: "\n\n") + "\n\n" }
        md += "## Telas varridas contra tools/ios-forbidden-terms.txt (\(telasVarridas.count))\n"
        md += telasVarridas.map { "- \($0)" }.joined(separator: "\n") + "\n\n"
        md += violacoes.isEmpty ? "Nenhum termo proibido nas telas varridas.\n" : "Ocorrências:\n" + violacoes.map { "- \($0)" }.joined(separator: "\n") + "\n"
        if !avisos.isEmpty { md += "\n## Não exercitado ou só parcial (as notas de revisão NÃO podem afirmar isto)\n" + avisos.map { "- \($0)" }.joined(separator: "\n") + "\n" }
        if !falhas.isEmpty { md += "\n## Falhas (ensaio vermelho, não enviar)\n" + falhas.map { "- \($0)" }.joined(separator: "\n") + "\n" }
        try? md.write(toFile: (outDir as NSString).appendingPathComponent("ensaio-revisao.md"), atomically: true, encoding: .utf8)
    }
}
