#!/usr/bin/env bash
# Pré-voo da venda de dobrões (#93). SÓ LEITURA: não grava no banco, não muda flag, não faz deploy.
# Uso (de qualquer pasta): bash tools/dobroes-prevoo.sh
# Imprime uma linha por conferência (OK / FALHA / INFO) e sai com código 1 se houver alguma FALHA.
#
# Confere:
#  - banco (tools/dobroes-prevoo.sql): flags (só o valor), contagem do gate da licença, contas sem
#    carteira com credits > 0, divergência credits x razão, os 4 produtos, migrações dobroes_* e a
#    última linha de app_releases;
#  - Edge dobroes-verify publicada igual a supabase/functions/dobroes-verify/index.ts (baixada numa
#    pasta temporária, nunca dentro do repositório);
#  - secret PLAY_BILLING_SERVICE_ACCOUNT: só "presente/ausente" (o valor nunca é lido nem impresso).
#
# Armadilha (#94): `supabase db query --linked` lê o projeto de supabase/.temp/project-ref do
# diretório atual. O repositório não guarda esse arquivo, então o script roda o CLI numa pasta
# temporária com o project-ref gravado. Requer o CLI do Supabase logado (supabase login).
set -u

REF=cwtslesnthbenxswdcbv
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SQL="$ROOT/tools/dobroes-prevoo.sql"
EDGE_REPO="$ROOT/supabase/functions/dobroes-verify/index.ts"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/supabase/.temp"
printf '%s' "$REF" > "$WORK/supabase/.temp/project-ref"

FALHAS=0
linha() {  # linha STATUS "texto"
  printf '%-6s%s\n' "$1" "$2"
  [ "$1" = "FALHA" ] && FALHAS=$((FALHAS + 1))
  return 0
}

echo "Pre-voo da venda de dobroes (#93) - projeto $REF - $(date '+%Y-%m-%d %H:%M')"
echo

# 1. Banco ----------------------------------------------------------------------------------------
OUT="$WORK/banco.csv"; ERR="$WORK/banco.err"
if (cd "$WORK" && supabase db query --linked -o csv -f "$SQL") >"$OUT" 2>"$ERR"; then
  # CSV de uma coluna: pula o cabeçalho, tira as aspas de fora e desfaz "" -> "
  tail -n +2 "$OUT" | tr -d '\r' | sed -e 's/^"//' -e 's/"$//' -e 's/""/"/g' | while IFS= read -r l; do
    [ -n "$l" ] && printf '%s\n' "$l"
  done
  N=$(tail -n +2 "$OUT" | grep -c '^"\?FALHA' || true)
  FALHAS=$((FALHAS + N))
  [ "$(tail -n +2 "$OUT" | grep -c .)" -eq 9 ] || linha FALHA "banco: esperava 9 linhas de conferencia"
else
  linha FALHA "banco: a consulta falhou (CLI logado? rede?)"
  grep -iv 'password\|token\|secret' "$ERR" | tail -n 5 | sed 's/^/      /'
fi

# 2. Edge dobroes-verify igual ao repositório -----------------------------------------------------
EDGE_DIR="$WORK/edge"
mkdir -p "$EDGE_DIR"
if (cd "$EDGE_DIR" && supabase functions download dobroes-verify --project-ref "$REF" --use-api) >"$WORK/edge.log" 2>&1; then
  BAIXADA="$EDGE_DIR/supabase/functions/dobroes-verify/index.ts"
  if [ ! -f "$BAIXADA" ]; then
    linha FALHA "edge dobroes-verify: download sem index.ts"
  elif diff -q --strip-trailing-cr "$BAIXADA" "$EDGE_REPO" >/dev/null; then
    linha OK "edge dobroes-verify: publicada igual a supabase/functions/dobroes-verify/index.ts"
  else
    linha FALHA "edge dobroes-verify: publicada DIFERENTE do repositorio ($(diff --strip-trailing-cr "$BAIXADA" "$EDGE_REPO" | grep -c '^[<>]') linhas)"
  fi
else
  linha FALHA "edge dobroes-verify: download falhou"
fi

# 3. Secret: só presença ---------------------------------------------------------------------------
if (cd "$WORK" && supabase secrets list --project-ref "$REF" 2>/dev/null) \
     | grep -qE '(^|[^A-Z_])PLAY_BILLING_SERVICE_ACCOUNT([^A-Z_]|$)'; then
  linha OK "secret PLAY_BILLING_SERVICE_ACCOUNT: presente (valor nao lido; prova de uso no passo d do roteiro)"
else
  linha FALHA "secret PLAY_BILLING_SERVICE_ACCOUNT: ausente (ou a lista nao abriu)"
fi

echo
if [ "$FALHAS" -gt 0 ]; then
  echo "Resultado: $FALHAS FALHA(S). Ver docs/QA_TEST_PLAN.md, Roteiro da #93, para o que cada uma bloqueia."
  exit 1
fi
echo "Resultado: tudo OK."
