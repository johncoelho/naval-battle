export interface MatchReconnectSnapshot {
  matchId: string;
  currentTurnPlayerId: string;
  turnDeadlineMs: number;
  firedCoordinates: Array<{ x: number; y: number; hit: boolean }>;
}

export function buildReconnectSnapshot(
  matchId: string,
  currentTurnPlayerId: string,
  turnDeadlineMs: number,
  firedCoordinates: Array<{ x: number; y: number; hit: boolean }>
): MatchReconnectSnapshot {
  return {
    matchId,
    currentTurnPlayerId,
    turnDeadlineMs,
    firedCoordinates: firedCoordinates.map((c) => ({ ...c })),
  };
}
