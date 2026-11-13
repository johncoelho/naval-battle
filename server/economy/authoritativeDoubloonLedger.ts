export interface DoubloonTransaction {
  txId: string;
  playerId: string;
  delta: number;
  reason: string;
  timestampMs: number;
}

export class AuthoritativeDoubloonLedger {
  private readonly balances = new Map<string, number>();
  private readonly processedTxIds = new Set<string>();

  getBalance(playerId: string): number {
    return this.balances.get(playerId) ?? 0;
  }

  applyTransaction(tx: DoubloonTransaction): { ok: boolean; balance: number; error?: string } {
    if (!tx.txId || !tx.playerId) {
      return { ok: false, balance: this.getBalance(tx.playerId), error: "invalid_tx" };
    }
    if (this.processedTxIds.has(tx.txId)) {
      return { ok: true, balance: this.getBalance(tx.playerId) };
    }
    const current = this.getBalance(tx.playerId);
    const next = current + Math.trunc(tx.delta);
    if (next < 0) {
      return { ok: false, balance: current, error: "insufficient_doubloons" };
    }
    this.balances.set(tx.playerId, next);
    this.processedTxIds.add(tx.txId);
    return { ok: true, balance: next };
  }
}
