#!/usr/bin/env bash
# Sobe um print de teste para o branch qa-evidencias e imprime o Markdown da imagem, pronto para o
# comentário da issue. Uso: bash tools/qa-evidencia.sh <issue> <arquivo.png> [nome.png]
# Ex.: bash tools/qa-evidencia.sh 7 /tmp/qa01-busca.png 0.30.3-qa01-busca-amigo.png
set -eu

N="$1"; FILE="$2"; NAME="${3:-$(basename "$FILE")}"
REPO=johncoelho/naval-battle
BRANCH=qa-evidencias
DEST="issue-$N/$NAME"

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
# arquivo já existe no branch: a API exige o sha para substituir
SHA=$(gh api "repos/$REPO/contents/$DEST?ref=$BRANCH" --jq .sha 2>/dev/null || true)
case "$SHA" in *[!0-9a-f]*|"") SHA="" ;; esac  # 404 devolve JSON de erro, não um sha
{
  printf '{"message":"QA #%s: %s","branch":"%s",' "$N" "$NAME" "$BRANCH"
  [ -n "$SHA" ] && printf '"sha":"%s",' "$SHA"
  printf '"content":"'
  base64 -w0 "$FILE"
  printf '"}'
} > "$TMP"
gh api -X PUT "repos/$REPO/contents/$DEST" --input "$TMP" --jq .content.path >/dev/null

printf '![%s](https://raw.githubusercontent.com/%s/%s/%s)\n' "$NAME" "$REPO" "$BRANCH" "$DEST"
