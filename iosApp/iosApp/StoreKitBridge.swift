import Foundation
import StoreKit
import ComposeApp

/// Compra de dobrões no iPhone (#65) com StoreKit 2. Só ponte: lista produtos, abre a folha de compra,
/// devolve a transação assinada (JWS) e finaliza quando o Kotlin mandar. Nenhuma regra de negócio aqui:
/// quem confere e credita é o servidor (`dobroes-verify-ios`), e `finish()` só roda depois disso.
/// O Kotlin pede por `StoreKitNative` e recebe a resposta por `StoreKitBridge.shared` (item a item e `onDone`),
/// sempre na thread principal. Registrada no `AppDelegate` (iOSApp.swift).
final class StoreKitDriver: NSObject, StoreKitNative {
    /// Transações já vistas e ainda sem `finish()`, por id: o Kotlin finaliza pelo id.
    private var open: [String: Transaction] = [:]
    private var updatesTask: Task<Void, Never>?

    override init() {
        super.init()
        // Transaction.updates precisa estar escutando desde a abertura (compra aprovada depois, Ask to Buy,
        // queda no meio): guarda a transação; quem credita e finaliza é o fluxo do Kotlin (unfinished).
        updatesTask = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let tx) = result {
                    await MainActor.run { self?.open[String(tx.id)] = tx }
                }
            }
        }
    }

    deinit { updatesTask?.cancel() }

    func requestProducts(requestId: String, ids: [String]) {
        Task {
            do {
                let products = try await Product.products(for: ids)
                await MainActor.run {
                    for p in products {
                        StoreKitBridge.shared.onItem(
                            requestId: requestId, state: "product", productId: p.id,
                            transactionId: "", signed: "", detail: p.displayPrice)
                    }
                    StoreKitBridge.shared.onDone(requestId: requestId, ok: true)
                }
            } catch {
                await MainActor.run { StoreKitBridge.shared.onDone(requestId: requestId, ok: false) }
            }
        }
    }

    func requestPurchase(requestId: String, productId: String, accountToken: String) {
        Task {
            func reply(_ state: String, _ tx: String = "", _ signed: String = "", _ detail: String = "") async {
                await MainActor.run {
                    StoreKitBridge.shared.onItem(
                        requestId: requestId, state: state, productId: productId,
                        transactionId: tx, signed: signed, detail: detail)
                    StoreKitBridge.shared.onDone(requestId: requestId, ok: true)
                }
            }
            guard let uuid = UUID(uuidString: accountToken) else { return await reply("failed", "", "", "conta invalida") }
            do {
                guard let product = try await Product.products(for: [productId]).first else {
                    return await reply("failed", "", "", "produto indisponivel")
                }
                switch try await product.purchase(options: [.appAccountToken(uuid)]) {
                case .success(let verification):
                    switch verification {
                    case .verified(let tx):
                        await MainActor.run { self.open[String(tx.id)] = tx }
                        await reply("purchased", String(tx.id), verification.jwsRepresentation)
                    case .unverified:
                        await reply("failed", "", "", "transacao nao verificada")
                    }
                case .userCancelled:
                    await reply("cancelled")
                case .pending:
                    await reply("pending")
                @unknown default:
                    await reply("failed", "", "", "resultado desconhecido")
                }
            } catch {
                await reply("failed", "", "", error.localizedDescription)
            }
        }
    }

    func requestUnfinished(requestId: String) {
        Task {
            var found: [(Transaction, String)] = []
            for await result in Transaction.unfinished {
                if case .verified(let tx) = result { found.append((tx, result.jwsRepresentation)) }
            }
            let snapshot = found
            await MainActor.run {
                for (tx, jws) in snapshot {
                    self.open[String(tx.id)] = tx
                    StoreKitBridge.shared.onItem(
                        requestId: requestId, state: "purchased", productId: tx.productID,
                        transactionId: String(tx.id), signed: jws, detail: "")
                }
                StoreKitBridge.shared.onDone(requestId: requestId, ok: true)
            }
        }
    }

    func requestFinish(requestId: String, transactionId: String) {
        Task {
            let tx = await MainActor.run { self.open[transactionId] }
            if let tx = tx { await tx.finish() }
            await MainActor.run {
                self.open[transactionId] = nil
                // sem a transação (já finalizada em outra sessão): nada a fazer, conta como feito
                StoreKitBridge.shared.onDone(requestId: requestId, ok: true)
            }
        }
    }
}
