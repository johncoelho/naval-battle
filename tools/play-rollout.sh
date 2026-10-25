#!/usr/bin/env bash
# Promoção da produção na Play com liberação gradual (issue #39).
# Usa só openssl, curl e jq, a Play Developer API v3 (edits) e a conta de serviço play-publisher-ci.
#
# Uso: tools/play-rollout.sh <ação> [versionCode] [fração]
#   promote  <vc> [0.2]   leva o versionCode da Alpha para a produção em 20%
#   advance  <vc> <0.5|1> sobe a fração de um release em andamento (1 = conclui, 100%)
#   halt     <vc>         pausa o release em andamento (ninguém mais recebe a versão)
#   resume   <vc>         retoma um release pausado na mesma fração
#   status                só leitura: mostra Alpha e produção
#
# Ambiente:
#   PLAY_SERVICE_ACCOUNT_JSON   conteúdo da chave (secret do GitHub); ou PLAY_SERVICE_ACCOUNT_FILE
#   PLAY_PRODUCTION_LIVE        precisa ser "true" para um promote de verdade (trava 1)
#   DRY_RUN=true                valida a edição na Play (edits.validate) e descarta, sem commit
#   PLAY_PACKAGE                padrão aigamesfactory.navalbattleclassic
#   PLAY_RETRY_WAIT             segundos entre tentativas (padrão 60)
# Nunca imprime a chave nem o token. Erros em pt-BR; saída diferente de zero = nada foi publicado.
set -eu

PACKAGE="${PLAY_PACKAGE:-aigamesfactory.navalbattleclassic}"
API="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PACKAGE"
SCOPE="https://www.googleapis.com/auth/androidpublisher"
TMPDIR_RO=""
TOKEN=""
EDIT_ID=""
HTTP_CODE=""
RESP=""

err() {
  echo "ERRO: $1" >&2
  if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error::$1"; fi
}
die() { err "$1"; cleanup_edit; exit 1; }
note() { echo "$*"; }

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# JWT RS256 da conta de serviço. $1 = e-mail, $2 = arquivo da chave PEM, $3 = token_uri
make_jwt() {
  local now header claims unsigned sig
  now=$(date +%s)
  header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  claims=$(printf '{"iss":"%s","scope":"%s","aud":"%s","iat":%s,"exp":%s}' \
    "$1" "$SCOPE" "$3" "$now" "$((now + 3000))" | b64url)
  unsigned="$header.$claims"
  sig=$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign "$2" -binary | b64url)
  printf '%s.%s' "$unsigned" "$sig"
}

setup_tmp() {
  TMPDIR_RO=$(mktemp -d)
  trap 'rm -rf "$TMPDIR_RO"' EXIT
}

get_token() {
  local json email uri
  if [ -n "${PLAY_SERVICE_ACCOUNT_JSON:-}" ]; then
    json="$PLAY_SERVICE_ACCOUNT_JSON"
  elif [ -n "${PLAY_SERVICE_ACCOUNT_FILE:-}" ] && [ -f "$PLAY_SERVICE_ACCOUNT_FILE" ]; then
    json=$(cat "$PLAY_SERVICE_ACCOUNT_FILE")
  else
    die "Sem credencial: defina PLAY_SERVICE_ACCOUNT_JSON (secret do GitHub) ou PLAY_SERVICE_ACCOUNT_FILE."
  fi
  email=$(printf '%s' "$json" | jq -r '.client_email // empty') || die "A chave da conta de serviço não é um JSON válido."
  uri=$(printf '%s' "$json" | jq -r '.token_uri // "https://oauth2.googleapis.com/token"')
  printf '%s' "$json" | jq -r '.private_key // empty' > "$TMPDIR_RO/key.pem"
  if [ -z "$email" ] || [ ! -s "$TMPDIR_RO/key.pem" ]; then
    die "A chave da conta de serviço não tem client_email ou private_key."
  fi
  local jwt
  jwt=$(make_jwt "$email" "$TMPDIR_RO/key.pem" "$uri") || die "Não consegui assinar o JWT (openssl)."
  local out code
  out="$TMPDIR_RO/token.json"
  code=$(curl -sS --max-time 60 -o "$out" -w '%{http_code}' -X POST "$uri" \
    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
    --data-urlencode "assertion=$jwt") || die "Falha de rede ao pedir o token do Google."
  if [ "$code" != "200" ]; then
    die "O Google recusou o token (HTTP $code): $(jq -r '.error_description // .error // "sem detalhe"' "$out" 2>/dev/null)"
  fi
  TOKEN=$(jq -r '.access_token // empty' "$out")
  [ -n "$TOKEN" ] || die "Resposta do token sem access_token."
  rm -f "$TMPDIR_RO/key.pem"
  printf 'Authorization: Bearer %s\n' "$TOKEN" > "$TMPDIR_RO/auth.hdr"
}

# api MÉTODO CAMINHO [CORPO]  -> corpo em $RESP, código em $HTTP_CODE; devolve 1 se não for 2xx
api() {
  local method="$1" path="$2" body="${3:-}" out="$TMPDIR_RO/resp.json"
  local args=(-sS --max-time 90 -o "$out" -w '%{http_code}' -X "$method" -H "@$TMPDIR_RO/auth.hdr")
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' --data-binary "$body"); fi
  HTTP_CODE=$(curl "${args[@]}" "$API$path") || { HTTP_CODE=000; RESP=""; return 1; }
  RESP=$(cat "$out")
  case "$HTTP_CODE" in 2??) return 0 ;; *) return 1 ;; esac
}

api_message() { printf '%s' "$RESP" | jq -r '.error.message // empty' 2>/dev/null || true; }

# Falha de API: 409, 429, 5xx e erro de rede podem ser repetidos (exit 75); o resto é definitivo.
api_fail() {
  local msg
  msg="$(api_message)"
  err "$1 (HTTP $HTTP_CODE${msg:+: $msg})"
  case "$HTTP_CODE" in
    000|409|429|5??) cleanup_edit; exit 75 ;;
    *) cleanup_edit; exit 1 ;;
  esac
}

cleanup_edit() {
  if [ -n "$EDIT_ID" ] && [ -n "$TOKEN" ]; then
    api DELETE "/edits/$EDIT_ID" >/dev/null 2>&1 || true
    EDIT_ID=""
  fi
}

open_edit() {
  api POST "/edits" '{}' || api_fail "Não consegui abrir a edição na Play (a conta de serviço tem acesso ao app?)"
  EDIT_ID=$(printf '%s' "$RESP" | jq -r '.id // empty')
  [ -n "$EDIT_ID" ] || die "A Play não devolveu o id da edição."
}

# track_get FAIXA -> JSON em $RESP (faixa vazia vira {"releases":[]})
track_get() {
  if api GET "/edits/$EDIT_ID/tracks/$1"; then
    return 0
  elif [ "$HTTP_CODE" = "404" ]; then
    RESP='{"releases":[]}'
    return 0
  fi
  api_fail "Não consegui ler a faixa $1"
}

summarize() { # $1 = JSON da faixa
  printf '%s' "$1" | jq -r '
    (.releases // [])[]
    | "  - " + (.name // "sem nome") + " | versionCodes " + ((.versionCodes // []) | join(","))
      + " | " + .status + (if .userFraction then " | " + ((.userFraction * 100 | round) | tostring) + "%" else "" end)'
}

do_status() {
  local alpha prod
  track_get alpha; alpha="$RESP"
  track_get production; prod="$RESP"
  note "PLAY_PRODUCTION_LIVE=${PLAY_PRODUCTION_LIVE:-<não definida>}"
  note "Faixa alpha:"; summarize "$alpha"
  note "Faixa production:"; summarize "$prod"
  jq -nc --argjson a "$alpha" --argjson p "$prod" \
    '{alpha: ($a.releases // []), production: ($p.releases // [])}' | sed 's/^/status_json: /'
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Play: alpha e production"
      echo '```'; summarize "$alpha" | sed 's/^/alpha /'; summarize "$prod" | sed 's/^/production /'; echo '```'
    } >> "$GITHUB_STEP_SUMMARY"
  fi
}

# ---- ações que mudam a produção: calculam o corpo novo da faixa em $NEW_TRACK ----

plan_promote() {
  local alpha prod rel max inprog name notes newrel
  [ -n "$FRACTION" ] || FRACTION=0.2
  [ "$FRACTION" = "0.2" ] || die "O promote sempre começa em 20% (fraction=0.2). Use advance para 50% e 100%."

  # trava 1: a produção só abre depois do Published da versão 91
  if [ "${PLAY_PRODUCTION_LIVE:-false}" != "true" ]; then
    if [ "$DRY_RUN" = "true" ]; then
      note "AVISO: PLAY_PRODUCTION_LIVE não é true; um promote de verdade seria barrado. Seguindo só porque é dry_run."
    else
      die "Trava 1: a variável PLAY_PRODUCTION_LIVE não é true (a produção 91 ainda não foi publicada). Nada foi enviado."
    fi
  fi

  track_get alpha; alpha="$RESP"
  track_get production; prod="$RESP"

  # trava 2: o versionCode precisa estar num release concluído da Alpha
  rel=$(printf '%s' "$alpha" | jq -c --arg v "$VERSION_CODE" \
    '[(.releases // [])[] | select(.status == "completed" and ((.versionCodes // []) | index($v)))] | .[0] // empty')
  [ -n "$rel" ] || die "Trava 2: o versionCode $VERSION_CODE não está em um release concluído da faixa alpha."

  # trava 3: maior que tudo o que já passou pela produção
  max=$(printf '%s' "$prod" | jq -r '[(.releases // [])[] | (.versionCodes // [])[] | tonumber] | max // 0')
  if [ "$VERSION_CODE" -le "$max" ]; then
    die "Trava 3: o versionCode $VERSION_CODE não é maior que o maior da produção ($max)."
  fi

  # release em andamento na produção: a Play não aceita dois
  inprog=$(printf '%s' "$prod" | jq -c '[(.releases // [])[] | select(.status == "inProgress" or .status == "halted")] | .[0] // empty')
  if [ -n "$inprog" ]; then
    if printf '%s' "$inprog" | jq -e '.status == "inProgress" and ((.userFraction // 0) >= 0.5)' >/dev/null; then
      die "A produção tem um release em andamento em 50% ou mais ($(printf '%s' "$inprog" | jq -r '.versionCodes | join(",")')). Conclua o antigo (advance, fraction=1) e promova a nova depois."
    fi
    note "AVISO: o release $(printf '%s' "$inprog" | jq -r '(.versionCodes // []) | join(",")') ($(printf '%s' "$inprog" | jq -r '.status')) será substituído pelo $VERSION_CODE, que recomeça em 20%."
  fi

  # trava 4: notas e nome copiados do release da Alpha
  name=$(printf '%s' "$rel" | jq -r '.name // empty')
  [ -n "$name" ] || name="$VERSION_CODE"
  notes=$(printf '%s' "$rel" | jq -c '.releaseNotes // []')
  if [ "$notes" = "[]" ]; then
    die "Trava 4: o release da alpha não tem notas da versão para copiar."
  fi
  newrel=$(jq -nc --arg name "$name" --arg v "$VERSION_CODE" --argjson f "$FRACTION" --argjson notes "$notes" \
    '{name: $name, versionCodes: [$v], status: "inProgress", userFraction: $f, releaseNotes: $notes}')

  # mantém o último release concluído e acrescenta o novo
  NEW_TRACK=$(printf '%s' "$prod" | jq -c --argjson new "$newrel" '
    ([(.releases // [])[] | select(.status == "completed")]
      | sort_by([.versionCodes[] | tonumber] | max) | last) as $done
    | {track: "production", releases: (([$done] | map(select(. != null))) + [$new])}')
  MSG="Produção: 20% (versionCode $VERSION_CODE, $name)"
}

find_release() { # $1 = status esperado; prod JSON em $PROD
  printf '%s' "$PROD" | jq -c --arg v "$VERSION_CODE" --arg s "$1" \
    '[(.releases // [])[] | select(.status == $s and ((.versionCodes // []) | index($v)))] | .[0] // empty'
}

plan_advance() {
  local rel cur
  case "$FRACTION" in 0.5|1) ;; *) die "O advance aceita fraction=0.5 ou fraction=1." ;; esac
  track_get production; PROD="$RESP"
  rel=$(find_release inProgress)
  [ -n "$rel" ] || die "Não há release em andamento (inProgress) com o versionCode $VERSION_CODE na produção. Se estiver pausado, use resume."
  cur=$(printf '%s' "$rel" | jq -r '.userFraction // 0')
  if ! jq -en --argjson c "$cur" --argjson f "$FRACTION" '$f > $c' >/dev/null; then
    die "O versionCode $VERSION_CODE já está em $(jq -n --argjson c "$cur" '$c * 100 | round')%; a fração nova ($FRACTION) precisa ser maior."
  fi
  NEW_TRACK=$(printf '%s' "$PROD" | jq -c --arg v "$VERSION_CODE" --argjson f "$FRACTION" '
    {track: "production", releases: [(.releases // [])[]
      | if .status == "inProgress" and ((.versionCodes // []) | index($v))
        then (if $f == 1 then (.status = "completed" | del(.userFraction)) else (.userFraction = $f) end)
        else . end]}')
  if [ "$FRACTION" = "1" ]; then MSG="Produção: 100% (versionCode $VERSION_CODE)"; else MSG="Produção: 50% (versionCode $VERSION_CODE)"; fi
}

plan_halt() {
  local rel
  track_get production; PROD="$RESP"
  rel=$(find_release inProgress)
  [ -n "$rel" ] || die "Não há release em andamento (inProgress) com o versionCode $VERSION_CODE na produção."
  NEW_TRACK=$(printf '%s' "$PROD" | jq -c --arg v "$VERSION_CODE" '
    {track: "production", releases: [(.releases // [])[]
      | if .status == "inProgress" and ((.versionCodes // []) | index($v)) then .status = "halted" else . end]}')
  MSG="Produção: PAUSADA (versionCode $VERSION_CODE); quem já recebeu continua com a versão"
}

plan_resume() {
  local rel
  track_get production; PROD="$RESP"
  rel=$(find_release halted)
  [ -n "$rel" ] || die "Não há release pausado (halted) com o versionCode $VERSION_CODE na produção."
  NEW_TRACK=$(printf '%s' "$PROD" | jq -c --arg v "$VERSION_CODE" '
    {track: "production", releases: [(.releases // [])[]
      | if .status == "halted" and ((.versionCodes // []) | index($v)) then .status = "inProgress" else . end]}')
  MSG="Produção: retomada (versionCode $VERSION_CODE) na mesma fração"
}

# uma tentativa completa, numa edição nova
attempt() {
  open_edit
  if [ "$ACTION" = "status" ]; then
    do_status
    cleanup_edit
    return 0
  fi
  case "$ACTION" in
    promote) plan_promote ;;
    advance) plan_advance ;;
    halt) plan_halt ;;
    resume) plan_resume ;;
  esac
  api PUT "/edits/$EDIT_ID/tracks/production" "$NEW_TRACK" || api_fail "A Play recusou a mudança na faixa production"
  if [ "$DRY_RUN" = "true" ]; then
    api POST "/edits/$EDIT_ID:validate" || api_fail "A validação da edição falhou"
    cleanup_edit
    note "dry_run ok: a Play validou e a edição foi descartada, sem commit. Teria feito: $MSG"
  else
    api POST "/edits/$EDIT_ID:commit" || api_fail "O commit da edição falhou"
    EDIT_ID=""
    note "$MSG"
    note "Atenção: o release novo passa pela revisão do Google; a fração só vale depois de Published."
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "**$MSG**" >> "$GITHUB_STEP_SUMMARY"; fi
  fi
}

main() {
  ACTION="${1:-}"
  VERSION_CODE="${2:-}"
  FRACTION="${3:-}"
  DRY_RUN="${DRY_RUN:-false}"

  case "$ACTION" in
    promote|advance|halt|resume|status) ;;
    *) echo "Uso: tools/play-rollout.sh <promote|advance|halt|resume|status> [versionCode] [fração]" >&2; exit 2 ;;
  esac
  if [ "$ACTION" != "status" ]; then
    case "$VERSION_CODE" in
      ''|*[!0-9]*) die "Informe o versionCode (só números) para a ação $ACTION." ;;
    esac
  fi
  for tool in openssl curl jq; do
    command -v "$tool" >/dev/null 2>&1 || { err "Falta a ferramenta $tool no ambiente."; exit 1; }
  done

  setup_tmp
  get_token

  local tries=0 rc
  while :; do
    tries=$((tries + 1))
    set +e
    ( attempt )
    rc=$?
    set -e
    [ "$rc" -ne 75 ] && break
    if [ "$tries" -ge 3 ]; then err "A Play seguiu ocupada ou instável após 3 tentativas."; exit 1; fi
    note "Edição em conflito ou Play instável; nova tentativa em ${PLAY_RETRY_WAIT:-60} s ($tries/3)."
    sleep "${PLAY_RETRY_WAIT:-60}"
  done
  exit "$rc"
}

# permite carregar as funções em teste (source) sem executar
if [ "${BASH_SOURCE[0]}" = "$0" ]; then main "$@"; fi
