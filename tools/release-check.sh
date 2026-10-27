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

# Nome de constante pública do commonMain que colide com macro C/Darwin: o Kotlin/Native exporta `const val`/`val`
# como @property no ComposeApp.h e o pré-processador troca o nome pela macro (0.34.8 CHILD_MAX, 0.34.10
# OPEN_MAX). A compilação de metadados abaixo NÃO pega isso; só o ios.yml, depois do push. Regra: declaração
# `val`/`var`/`const val` em CAIXA_ALTA (com ou sem anotação), sem private/internal/protected, em commonMain ou
# iosMain, cujo nome esteja em tools/c-macros.txt, seja MAX/MIN ou termine em _MAX/_MIN (a família de limits.h;
# pega os que a lista não trouxer). Sem a lista a trava falha fechada.
if echo "$changed" | grep -E '^(composeApp/src/(commonMain|iosMain)/|tools/c-macros\.txt)' >/dev/null; then
  macros=tools/c-macros.txt
  [ -f "$macros" ] || err "$macros não existe: a trava de nomes que colidem com macros C/Darwin não tem lista."
  decl='^[[:space:]]*(@[A-Za-z.]+(\([^)]*\))?[[:space:]]+)*([a-z]+[[:space:]]+)*(const[[:space:]]+)?(val|var)[[:space:]]+[A-Z][A-Z0-9_]*[[:space:]]*[:=]'
  hits=$(grep -rnE "$decl" composeApp/src/commonMain composeApp/src/iosMain --include=*.kt 2>/dev/null || true)
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    content=${line#*:}; content=${content#*:}   # tira "arquivo:linha:"
    # modificadores e anotações que vêm antes de val/var: private/internal/protected deixam de ser públicos
    before=$(printf '%s' "$content" | sed -E 's/(val|var)[[:space:]]+[A-Z][A-Z0-9_]*.*$//')
    if printf '%s' "$before" | grep -qE '(^|[[:space:]])(private|internal|protected)[[:space:]]'; then continue; fi
    nm=$(printf '%s' "$content" | sed -E 's/^.*(val|var)[[:space:]]+([A-Z][A-Z0-9_]*).*$/\2/')
    if { [ -f "$macros" ] && tr -d '\r' < "$macros" | grep -qxF "$nm"; } || printf '%s' "$nm" | grep -qE '(^|_)(MAX|MIN)$'; then
      err "constante pública '$nm' colide com macro C/Darwin no ComposeApp.h do iOS (${line%%:*}): torne private/internal ou renomeie (lista: $macros)."
    fi
  done <<EOF_HITS
$hits
EOF_HITS
fi

# API só da JVM no commonMain (toSortedSet, String.format, java.*...) só quebrava no CI do iOS, depois
# do push (0.34.9). A compilação de metadados do commonMain não precisa de Mac e pega esse erro.
# Roda só quando o push mexe em código comum ou na configuração do build. Compila a árvore de trabalho
# (no CI ela é o próprio commit). RELEASE_CHECK_SKIP_METADATA=1 pula.
if [ "${RELEASE_CHECK_SKIP_METADATA:-0}" != "1" ]    && echo "$changed" | grep -E '^(composeApp/src/commonMain/|composeApp/src/iosMain/|gradle/|build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties|composeApp/build\.gradle\.kts)' >/dev/null; then
  if [ "${OS:-}" = "Windows_NT" ]; then
    mkdir -p /d/tmp 2>/dev/null || true
    export TMP='D:\tmp' TEMP='D:\tmp'   # contorno do erro de loopback do Gradle (docs/BUILD.md)
  fi
  echo "release-check: compilando os metadados do commonMain (pega API só da JVM que quebraria o iOS)..."
  meta_log=$(mktemp)
  if ! bash ./gradlew :composeApp:compileCommonMainKotlinMetadata --console=plain > "$meta_log" 2>&1; then
    grep -E '^e: ' "$meta_log" | head -20 || true
    err "commonMain não compila como código comum (API só da JVM?). Veja os 'e:' acima; o iOS quebraria no CI."
  fi
  rm -f "$meta_log"
  # o Kotlin/Native do iOS também compila aqui (as klibs de plataforma vêm no ~/.konan): pega nome de cinterop que não existe
  # (0.42.0: ASAuthorizationAppleIDButtonTypeSignIn) antes de o CI do Mac quebrar. Só o link e os testes ficam para o Mac.
  echo "release-check: compilando o Kotlin/Native do iOS (simulador)..."
  ios_log=$(mktemp)
  if ! bash ./gradlew :composeApp:compileKotlinIosSimulatorArm64 --console=plain > "$ios_log" 2>&1; then
    grep -E '^e: ' "$ios_log" | head -20 || true
    err "o iosMain não compila no Kotlin/Native (nome de API Apple/UIKit que não existe como escrito?). Veja os 'e:' acima."
  fi
  rm -f "$ios_log"
fi

if [ "$fail" -ne 0 ]; then
  echo "release-check: release incompleto — veja a skill dev-cycle (passos 4 e 5)."
  exit 1
fi
echo "release-check: ok — versão $new_name ($new_code), iOS alinhado, notas e CHANGELOG em dia."
