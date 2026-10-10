#!/usr/bin/env bash
# Trava de release: todo push que mexe no app publica na Play e no iPhone, então precisa
# subir a versão, alinhar o iOS, escrever as notas da Play e registrar no CHANGELOG.
# Uso: tools/release-check.sh <commit-base> [commit-novo]   (novo = HEAD por padrão)
# Sai com erro e explica em pt-BR o que falta. Commit só de documentação usa [skip ci].
# sem pipefail: grep/head que encerram a leitura cedo não podem derrubar o pipeline (SIGPIPE)
set -eu

BASE="${1:-}"
HEAD_REF="${2:-HEAD}"
GRADLE=composeApp/build.gradle.kts
PLIST=iosApp/iosApp/Info.plist
NOTES=composeApp/src/main/play/release-notes/pt-BR/default.txt

if [ -z "$BASE" ] || [ "$BASE" = "0000000000000000000000000000000000000000" ] || ! git cat-file -e "$BASE^{commit}" 2>/dev/null; then
  echo "release-check: sem commit base para comparar (primeiro push ou execução manual) — pulando."
  exit 0
fi

changed=$(git diff --name-only "$BASE" "$HEAD_REF")
if ! echo "$changed" | grep -E '^(composeApp/|iosApp/|gradle/|build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties)' >/dev/null; then
  echo "release-check: o push não mexe no app — nada a conferir."
  exit 0
fi

fail=0
err() { echo "::error::$1"; echo "ERRO: $1"; fail=1; }

code_of() { git show "$1:$GRADLE" | sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' | head -1; }
name_of() { git show "$1:$GRADLE" | sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' | head -1; }

old_code=$(code_of "$BASE"); new_code=$(code_of "$HEAD_REF")
new_name=$(name_of "$HEAD_REF")
if [ -z "$new_code" ] || [ -z "$old_code" ] || [ "$new_code" -le "$old_code" ]; then
  err "versionCode não subiu ($old_code → $new_code). A Play recusa versão repetida: suba em $GRADLE."
fi

ios_ver=$(git show "$HEAD_REF:$PLIST" | tr -d '\r' | grep -A1 'CFBundleShortVersionString' | sed -n 's/.*<string>\(.*\)<\/string>.*/\1/p' | head -1)
if [ "$ios_ver" != "$new_name" ]; then
  err "Versão do iOS ($ios_ver) diferente do versionName ($new_name): ajuste CFBundleShortVersionString em $PLIST."
fi

if ! echo "$changed" | grep -x "$NOTES" >/dev/null; then
  err "Notas da Play não mudaram: escreva o que há de novo em $NOTES."
fi
notes=$(git show "$HEAD_REF:$NOTES")
len=$(printf '%s' "$notes" | wc -m | tr -d ' ')
if [ "$len" -gt 500 ]; then
  err "Notas da Play com $len caracteres (máximo 500)."
fi
if printf '%s' "$notes" | LC_ALL=C grep '[^ -~]' >/dev/null; then
  err "Notas da Play com acento ou caractere fora do ASCII (regra do projeto: sem acento)."
fi

if ! echo "$changed" | grep -x 'CHANGELOG.md' >/dev/null; then
  err "CHANGELOG.md sem entrada nova para esta versão."
elif ! git show "$HEAD_REF:CHANGELOG.md" | grep "App $new_name" >/dev/null; then
  err "CHANGELOG.md não cita \"App $new_name\" na entrada nova."
fi

if [ "$fail" -ne 0 ]; then
  echo "release-check: release incompleto — veja a skill dev-cycle (passos 4 e 5)."
  exit 1
fi
echo "release-check: ok — versão $new_name ($new_code), iOS alinhado, notas e CHANGELOG em dia."
