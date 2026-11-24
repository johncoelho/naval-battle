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
    /// Passo em que o teste está (vai para o relatório se ele abortar) e passos obrigatórios que já tiveram resultado.
    private var obrigatoriosFaltando: [String] = []
    private var atalhoPendente = false
    private let insuficienteRx = "Dobrões insuficientes|Not enough doubloons|Doblones insuficientes"
    private let atalhoRx = "Abrir a loja de Dobr|Open the Doubloon store|Abrir la tienda de Doblones"
    private var ultimoPasso = "abertura"
    private var concluidos = Set<String>()
    private var obrigatorios: Set<String> {
        var s: Set<String> = ["passe-cta", "passe-popup", "pacote-Bolsa", "pacote-Baú", "pacote-Cofre", "pacote-Tesouro do Almirante"]
        if estado == "sem-saldo" { s.insert("passe-atalho") }
        return s
    }
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
        // o XCUITest pode abortar o teste (ex.: "Failed to not hittable") sem passar por falhar(): isso nunca pode virar VERDE
        if let r = testRun, r.totalFailureCount > 0, falhas.isEmpty {
            falhas.append("o teste terminou por falha do XCUITest (\(r.totalFailureCount)) no passo \"\(ultimoPasso)\"")
        }
        // passo obrigatório sem resultado é falha (CTA do Passe, popup, atalho da loja, os 4 pacotes)
        let faltam = obrigatorios.subtracting(concluidos).sorted()
        for f in faltam { falhas.append("passo obrigatório sem resultado: \(f) (parou em \"\(ultimoPasso)\")") }
        obrigatoriosFaltando = faltam
        escreverRelatorio()
    }

    // MARK: - roteiro

    func testEnsaioDeRevisao() throws {
        XCTAssertFalse(terms.isEmpty, "lista de termos proibidos não carregada (TERMS_FILE)")
        XCTAssertTrue(["sem-saldo", "com-saldo"].contains(estado), "ESTADO deve ser sem-saldo ou com-saldo (recebido: \(estado))")
        // alerta do sistema de notificações: é do SpringBoard (não está na árvore do app) e engole os toques; sempre "Não Permitir"
        addUIInterruptionMonitor(withDescription: "notificacoes") { [weak self] alerta in
            let p = NSPredicate(format: "label MATCHES[c] %@", "(?s).*(Não permitir|Don.t Allow|No permitir).*")
            let b = alerta.buttons.matching(p).firstMatch
            if b.exists { b.tap(); self?.diag.append("alerta de notificações negado pelo monitor"); return true }
            return false
        }
        app = XCUIApplication()
        app.launch()
        shotSeguro("00-abertura")
        for _ in 0..<6 {
            if negarNotificacao() { shotSeguro("03-apos-alerta-notificacoes"); break }
            sleep(2)
        }

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
            tocarElemento(b)
            sleep(2)
        }
    }

    private func ensaioPasse() {
        let semSaldo = estado == "sem-saldo"
        ultimoPasso = "Passe"
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
        let r = tocarEEsperar("Passe: CTA de entrada (\(estado))", ctaPasse, esperado: semSaldo ? insuficiente : sucesso, estrito: true, id: "season_join_cta")
        if r == nil { fecharAvisosDoPasse(); return }
        concluidos.insert("passe-cta")
        if semSaldo {
            guard existe(insuficiente) else {
                falhar("Passe sem saldo", "esperava o popup \"Dobrões insuficientes\"; a conta demo tem saldo? (estado \(estado))")
                fecharAvisosDoPasse()
                return
            }
            concluidos.insert("passe-popup")
            registrar("Passe sem saldo", "popup \"Dobrões insuficientes\"", "visível sem rolar", 0, shotSeguro("passe-sem-saldo"))
            if achar(atalhoRx, espera: 10) != nil {
                if tocarEEsperar("Passe: atalho para a loja de Dobrões", atalhoRx, esperado: nil, estrito: true, pronto: { self.lojaDobroesAberta() }, limiteSeg: 25) != nil {
                    concluidos.insert("passe-atalho")
                    registrar("Passe sem saldo", "atalho \"Abrir a loja de Dobrões\"", "abriu a aba Dobrões", 0, shotSeguro("passe-atalho-loja"))
                    varrer("Loja > Dobrões (pelo atalho do Passe)")
                    // a loja já está aberta na aba Dobrões: prova os 4 pacotes aqui, sem navegação extra
                    provarPacotes()
                    voltarAoMenu()
                }
            } else {
                // o atalho só aparece com a venda do iPhone aberta (iosSaleOpen); o app agora a consulta ao abrir este popup, mas se ele
                // não veio, o ensaio prova a vitrine primeiro e REPETE o toque (2ª tentativa): ausente com a vitrine presente é defeito do app
                atalhoPendente = true
                diag.append("atalho da loja ausente na 1ª tentativa; será exigido de novo depois de a vitrine ser provada")
                shotSeguro("passe-sem-atalho-1a-tentativa")
                registrarArvore("Passe: atalho ausente na 1ª tentativa")
            }
        } else {
            guard existe(sucesso) else {
                falhar("Passe com saldo", "esperava o popup \"Tudo certo\"; a conta demo tem saldo para o passe? (estado \(estado))")
                fecharAvisosDoPasse()
                return
            }
            concluidos.insert("passe-popup")
            registrar("Passe com saldo", "popup de confirmação \"Tudo certo\"", "visível", 0, shotSeguro("passe-com-saldo"))
            _ = tocarEEsperar("Passe: OK da confirmação", "^OK$", esperado: nil, estrito: false)
        }
        fecharAvisosDoPasse()
        fecharPasseForcado()
    }

    /// 2ª tentativa do atalho, depois de a vitrine ter sido provada: reabre o Passe, toca o CTA e exige o atalho.
    private func retentarAtalho() {
        ultimoPasso = "Passe (2ª tentativa do atalho)"
        fecharPasseForcado()
        voltarAoMenu()
        guard abrirPasse() else { falhar("Passe: atalho", "o Passe não reabriu para a 2ª tentativa"); return }
        guard tocarEEsperar("Passe: CTA (2ª tentativa)", ctaPasse, esperado: insuficienteRx, estrito: true, id: "season_join_cta") != nil else {
            fecharPasseForcado()
            return
        }
        guard achar(atalhoRx, espera: 10) != nil else {
            shotSeguro("passe-sem-atalho-2a-tentativa")
            falhar("Passe: atalho", "atalho ausente MESMO com a vitrine presente: defeito do app, abrir bug na #118 com a foto")
            fecharAvisosDoPasse()
            fecharPasseForcado()
            return
        }
        if tocarEEsperar("Passe: atalho para a loja de Dobrões (2ª tentativa)", atalhoRx, esperado: nil, estrito: true, pronto: { self.lojaDobroesAberta() }) != nil {
            concluidos.insert("passe-atalho")
            registrar("Passe sem saldo", "atalho \"Abrir a loja de Dobrões\" (2ª tentativa)", "abriu a aba Dobrões", 0, shotSeguro("passe-atalho-loja"))
            voltarAoMenu()
        }
        fecharPasseForcado()
    }

    /// Garante que o Passe está fechado: botões (OK, Fechar, Agora não) e, se o botão estiver abaixo da dobra do cartão rolável,
    /// um toque no véu (fora do cartão, na borda esquerda da janela), que fecha o popup.
    private func fecharPasseForcado() {
        for _ in 0..<3 {
            if !(ctaPorId() || existe("NOVA TEMPORADA|NEW SEASON|NUEVA TEMPORADA|Passe de Temporada|Season Pass|Pase de Temporada")) { return }
            fecharAvisosDoPasse()
            if !(ctaPorId() || existe("NOVA TEMPORADA|NEW SEASON|NUEVA TEMPORADA")) { return }
            app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.5)).tap()
            diag.append("Passe fechado por toque no véu (botão Agora não abaixo da dobra)")
            sleep(1)
        }
    }

    private func abrirPasse() -> Bool {
        for ciclo in 0..<12 {
            negarNotificacao()
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

    /// Alerta de notificações do sistema (SpringBoard): toca em "Não Permitir" / "Don't Allow". Devolve se havia o alerta.
    @discardableResult
    private func negarNotificacao() -> Bool {
        let p = NSPredicate(format: "label MATCHES[c] %@", "(?s).*(Não permitir|Don.t Allow|No permitir).*")
        for host in [springboard, app!] {
            let b = host.alerts.buttons.matching(p).firstMatch
            if b.exists { b.tap(); sleep(1); return true }
            let solto = host.buttons.matching(p).firstMatch
            if tocavel(solto) { tocarElemento(solto); sleep(1); return true }
        }
        return false
    }

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
        fecharPasseForcado()
        pularPrimeiraMissao()
        limparAvisos()
        voltarAoMenu()
        shotSeguro("10-antes-da-loja")
        guard tocar("Loja|Store|Tienda", espera: 15) else { falhar("Loja", "botão da Loja não encontrado"); return false }
        sleep(2)
        guard !existe("Diário de bordo|Captain.s logbook|Diario de a bordo"), tocar("Dobrões|Doubloons|Doblones", espera: 20) else { falhar("Loja", "aba Dobrões não aparece (venda fechada para a conta?)"); return false }
        let fim = Date().addingTimeInterval(25)
        while Date() < fim { if lojaDobroesAberta() { return true }; sleep(1) }
        return false
    }

    /// A aba Dobrões está aberta quando os 4 pacotes existem na árvore, pelo identificador (testTag do Compose): não depende do
    /// idioma, do rótulo (que leva bônus e preço da App Store) nem do frame, que no iPad em compatibilidade sai das coordenadas da janela.
    private let idsPacotes = ["pack_dobroes_bolsa", "pack_dobroes_bau", "pack_dobroes_cofre", "pack_dobroes_tesouro"]
    private func lojaDobroesAberta() -> Bool {
        idsPacotes.allSatisfy { app.descendants(matching: .any)[$0].exists }
    }

    private var pacotesProvados = false

    private func ensaioPacotes() {
        // já provados com a loja aberta pelo atalho do Passe (rodada sem-saldo): não repete a navegação
        if !pacotesProvados {
            guard irParaDobroes() else {
                falhar("Dobrões", "a vitrine não mostrou os pacotes (Bolsa, Baú, Cofre, Tesouro)")
                return
            }
            varrer("Loja > Dobrões")
            shotSeguro("20-dobroes-vitrine")
            provarPacotes()
        }
        if atalhoPendente && ["Bolsa", "Baú", "Cofre", "Tesouro do Almirante"].allSatisfy({ concluidos.contains("pacote-\($0)") }) { retentarAtalho() }
    }

    /// Com a aba Dobrões aberta: toca os 4 pacotes (pelo identificador) e exige resposta visível em cada um.
    private func provarPacotes() {
        pacotesProvados = true
        let pacotes: [(String, String, String)] = [
            ("Bolsa", "pack_dobroes_bolsa", "pacote-1"),
            ("Baú", "pack_dobroes_bau", "pacote-2"),
            ("Cofre", "pack_dobroes_cofre", "pacote-3"),
            ("Tesouro do Almirante", "pack_dobroes_tesouro", "pacote-4"),
        ]
        let mensagem = "indisponíve|unavailable|no disponible|Não foi possível|Couldn.t|No se pudo|pendente|pending|pendiente"
        for (nome, id, png) in pacotes {
            ultimoPasso = "Pacote \(nome)"
            // o 1º toque acorda o StoreKitUIService (partida a frio do simulador, passou de 10 s): só ele ganha 30 s; os demais seguem em 10 s
            let r = tocarEEsperar("Pacote \(nome)", id, esperado: mensagem, estrito: false, png: png, id: id, limiteSeg: nome == "Bolsa" ? 30 : nil)
            if r != nil { concluidos.insert("pacote-\(nome)"); fecharFolhaDeCompra() }
            sleep(2)
        }
    }

    // MARK: - Milhas

    private func ensaioMilhas() {
        ultimoPasso = "Milhas"
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
        ultimoPasso = "Loja (botões de preço)"
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
            tocarElemento(botao)
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
            .filter { tocavel($0) && $0.frame.minY > app.windows.firstMatch.frame.height * 0.25 }
            .sorted { $0.frame.minY < $1.frame.minY }
        return candidatos.first
    }

    private func confirmacaoAberta() -> Bool { existe("Cancelar|Cancel") && existe("Comprar|Buy|Confirm") }

    // MARK: - varredura de texto das telas

    private func varreduraDasTelas() {
        ultimoPasso = "Varredura das telas"
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
    private func tocarEEsperar(_ nome: String, _ regex: String, esperado: String?, estrito: Bool, png: String? = nil, id: String? = nil, pronto: (() -> Bool)? = nil, limiteSeg: TimeInterval? = nil) -> String? {
        let limite = limiteSeg ?? self.limite
        negarNotificacao()
        let alvoOpt: XCUIElement?
        if let id = id {
            // pelo identificador (testTag do Compose): não depende do idioma nem do formato do rótulo ("ENTRAR COM 1.200 DOBRÕES")
            let e = app.descendants(matching: .any)[id]
            alvoOpt = e.waitForExistence(timeout: 10) ? e : achar(regex, espera: 5)
        } else {
            alvoOpt = achar(regex, espera: 10)
        }
        guard let alvo = alvoOpt else {
            falhar(nome, "não encontrei na tela: \(id ?? regex)")
            return nil
        }
        let antes = assinatura()
        let inicio = Date()
        if id != nil {
            // #167: o CTA do Passe existe e está visível, mas o XCUITest o dá como "Not hittable" (fora do app.frame no iPad).
            // Toca pelas coordenadas da JANELA (centro do frame do alvo), sem depender de isHittable.
            let janela = app.windows.firstMatch
            let f = alvo.frame, jf = janela.frame, af = app.frame
            diag.append("toque \(id ?? ""): isHittable=\(alvo.isHittable); alvo.frame=\(f); app.frame=\(af); janela.frame=\(jf)")
            let origem = janela.coordinate(withNormalizedOffset: .zero)
            origem.withOffset(CGVector(dx: f.midX - jf.minX, dy: f.midY - jf.minY)).tap()
        } else {
            tocarElemento(alvo)
        }
        var resposta: String?
        while Date().timeIntervalSince(inicio) < limite {
            if let esperado = esperado, existe(esperado) { resposta = "mensagem/popup esperado"; break }
            if let pronto = pronto, pronto() { resposta = "tela esperada"; break }
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
                if tocavel(b) { tocarElemento(b); sleep(1); return }
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
            "PULAR|Skip|Saltar", "FECHAR|Close|Cerrar", "VALEU|Thanks|Gracias", "AGORA NÃO|Not now|Ahora no", "ENTENDI|Got it|Entendido",
        ]
        let checkIn = "FAZER CHECK-IN.*|Check in.*|Hacer check-in.*"
        var vazios = 0
        var ciclos = 0
        // fila de avisos de conta nova após o Passe: Primeira Missão, presente, Diário de bordo, rosa dos ventos... (até 14 voltas)
        while ciclos < 14 && vazios < 2 {
            ciclos += 1
            var feito = false
            if negarNotificacao() { feito = true }
            if !feito {
                for regex in prioridade {
                    let p = NSPredicate(format: "label MATCHES[c] %@", "(?s)(\(regex))[ !.]*(\n.*)?")
                    let b = app.buttons.matching(p).firstMatch
                    if tocavel(b) { tocarElemento(b); feito = true; break }
                }
            }
            if !feito && fecharDiarioPeloVeu() { feito = true }
            if !feito {
                // último recurso: o check-in também fecha o Diário (não é tocado enquanto houver Fechar ou véu para tocar)
                let p = NSPredicate(format: "label MATCHES[c] %@", "(?s)(\(checkIn))[ !.]*(\n.*)?")
                let b = app.buttons.matching(p).firstMatch
                if tocavel(b) { tocarElemento(b); feito = true }
            }
            if feito { vazios = 0; sleep(2) } else { vazios += 1; sleep(3) }
        }
        diag.append("limparAvisos: \(ciclos) ciclos")
    }

    /// Fecha os avisos que cobrem a tela (presente, Diário de bordo, rosa dos ventos), em ordem de prioridade.
    private func fecharAvisos(excluindo: String?) {
        let prioridade = [
            "PULAR|Skip|Saltar", "FECHAR|Close|Cerrar", "VALEU|Thanks|Gracias", "AGORA NÃO|Not now|Ahora no", "ENTENDI|Got it|Entendido",
        ]
        if negarNotificacao() { return }
        for regex in prioridade where regex != excluindo {
            let p = NSPredicate(format: "label MATCHES[c] %@", "(?s)(\(regex))[ !.]*(\n.*)?")
            let b = app.buttons.matching(p).firstMatch
            if tocavel(b) { tocarElemento(b); sleep(1); return }
        }
        _ = fecharDiarioPeloVeu()
    }

    /// Diário de bordo aberto sem botão Fechar visível (fica abaixo da dobra do cartão rolável): o toque no véu, fora do cartão
    /// (borda esquerda da janela), fecha o popup (`DailyPopup`: clique no fundo = closeDailyPopup). Sem isso o Diário ficava aberto
    /// por cima do menu e o ensaio "tocava" na aba Dobrões pelo subtítulo dele ("ganhe dobrões").
    private func fecharDiarioPeloVeu() -> Bool {
        guard existe("Diário de bordo|Captain.s logbook|Diario de a bordo") else { return false }
        app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.5)).tap()
        diag.append("Diário de bordo fechado por toque no véu (Fechar abaixo da dobra)")
        sleep(1)
        return true
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
                .filter { tocavel($0) }
                .sorted { $0.frame.minY < $1.frame.minY }
            if let e = ordenados.first { return e }
            usleep(500_000)
        }
        return nil
    }

    @discardableResult
    private func tocar(_ regex: String, espera: TimeInterval) -> Bool {
        guard let e = achar(regex, espera: espera) else { return false }
        tocarElemento(e)
        return true
    }

    /// App só de iPhone no iPad (modo de compatibilidade): o app.frame é 375x667 e a janela 663x1180 em tela 820x1180; os frames dos
    /// elementos vêm em coordenadas de tela, então o XCUITest acha "não tocável" um botão que está visível na janela. Para esse caso
    /// toca por coordenada normalizada na JANELA (nunca pelo app.frame) e anota no diagnóstico.
    private func tocarElemento(_ e: XCUIElement) {
        if e.isHittable { e.tap(); return }
        let jan = app.windows.firstMatch
        let wf = jan.frame
        let f = e.frame
        guard wf.width > 0, wf.height > 0, !f.isEmpty else { e.tap(); return }
        let dx = (f.midX - wf.minX) / wf.width
        let dy = (f.midY - wf.minY) / wf.height
        diag.append("toque por coordenada da janela (\(String(format: "%.3f", dx)), \(String(format: "%.3f", dy))): \(e.identifier.isEmpty ? String(e.label.prefix(30)) : e.identifier) não é tocável pela API (frame \(f), janela \(wf))")
        jan.coordinate(withNormalizedOffset: CGVector(dx: dx, dy: dy)).tap()
    }

    /// Tocável de fato: a API diz que sim, ou o frame está dentro da janela do app (a API erra no modo de compatibilidade do iPad).
    private func tocavel(_ e: XCUIElement) -> Bool {
        guard e.exists, !e.frame.isEmpty else { return false }
        if e.isHittable { return true }
        let jan = app.windows.firstMatch
        return jan.exists && jan.frame.insetBy(dx: -2, dy: -2).contains(e.frame)
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
        md += "RESULTADO: \(verde ? "VERDE" : "VERMELHO")\n"
        md += obrigatoriosFaltando.isEmpty ? "OBRIGATORIOS: OK (\(obrigatorios.count) passos)\n\n" : "OBRIGATORIOS: FALTAM \(obrigatoriosFaltando.joined(separator: ", "))\n\n"
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
