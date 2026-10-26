#!/usr/bin/env bash
# Promoção da produção na Play com liberação gradual (issue #39).
# Usa só openssl, curl e jq, a Play Developer API v3 (edits) e a conta de serviço play-publisher-ci.
#
# Uso: tools/play-rollout.sh <ação> [versionCode] [fração]
#   promote  <vc> [0.2]   leva o versionCode da Alpha para a produção em 20%
#   advance  <vc> <0.5|1> sobe a fração de um release em andamento (1 = conclui, 100%)
#   halt     [vc]         pausa o release em andamento (sem vc: o único em andamento)
#   resume   <vc>         retoma um release pausado na mesma fração
#   status                só leitura: mostra Alpha e produção
#   vitals   <vc>         só leitura: falha e ANR percebidos do versionCode (Play Developer Reporting API,
#                         janela de 3 dias) e veredito ALARME, OK, POUCOS_USUARIOS ou SEM_DADOS (issue #51).
#                         Sai com 0 se leu (qualquer veredito) e com 1 só se a leitura falhou. Com
#                         DRY_RUN=true não chama a API: roda o autoteste do veredito com amostras fixas.
#
# Ambiente:
#   PLAY_SERVICE_ACCOUNT_JSON   conteúdo da chave (secret do GitHub); ou PLAY_SERVICE_ACCOUNT_FILE
#   PLAY_PRODUCTION_LIVE        precisa ser "true" para um promote de verdade (trava 1)
#   DRY_RUN=true                valida a edição na Play (edits.validate) e descarta, sem commit
#   PLAY_PACKAGE                padrão aigamesfactory.navalbattleclassic
#   PLAY_RETRY_WAIT             segundos entre tentativas (padrão 60)
#   VITALS_CRASH_MAX            falha percebida máxima, em fração (padrão 0.0109 = 1,09%)
#   VITALS_ANR_MAX              ANR percebido máximo, em fração (padrão 0.0047 = 0,47%)
#   VITALS_MIN_USERS            piso de usuários distintos no dia para o dia valer (padrão 100)
# Nunca imprime a chave nem o token. Erros em pt-BR; saída diferente de zero = nada foi publicado.
set -eu

PACKAGE="${PLAY_PACKAGE:-aigamesfactory.navalbattleclassic}"
API="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PACKAGE"
API_BASE="$API"
REPORTING_BASE="https://playdeveloperreporting.googleapis.com/v1beta1/apps/$PACKAGE"
SCOPE="https://www.googleapis.com/auth/androidpublisher"
VITALS_WINDOW_DAYS=3
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
  HTTP_CODE=$(curl "${args[@]}" "$API_BASE$path") || { HTTP_CODE=000; RESP=""; return 1; }
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

# ---- vitals (issue #51): só leitura na Play Developer Reporting API, sem edição ----

# Definições jq do vitals. Os limites chegam por --argjson (cmax, amax, min). Dia = {date, users, crash,
# anr, crash_all, anr_all}, taxas em fração. Comparação estrita (> limite) e piso inclusivo (>= usuários):
# só dia com usuários >= piso conta para ALARME ou OK.
VITALS_DEFS='
def pad2: tostring | if length < 2 then "0" + . else . end;
def day_of: "\(.startTime.year)-\(.startTime.month | pad2)-\(.startTime.day | pad2)";
def metric($n): ((.metrics // []) | map(select(.metric == $n)) | .[0].decimalValue.value) as $v
  | if $v == null then null else ($v | tonumber) end;
def vitals_days($c; $a):
  ((($c.rows // []) | map({date: day_of, k: "c", r: .}))
   + (($a.rows // []) | map({date: day_of, k: "a", r: .}))) as $all
  | $all | group_by(.date)
  | map(. as $g | $g[0].date as $dt
      | ([$g[] | select(.k == "c") | .r] | .[0]) as $cr
      | ([$g[] | select(.k == "a") | .r] | .[0]) as $ar
      | {date: $dt,
         users: ([($cr // {} | metric("distinctUsers")), ($ar // {} | metric("distinctUsers"))] | map(select(. != null)) | max // 0),
         crash: ($cr // {} | metric("userPerceivedCrashRate")),
         anr: ($ar // {} | metric("userPerceivedAnrRate")),
         crash_all: ($cr // {} | metric("crashRate")),
         anr_all: ($ar // {} | metric("anrRate"))})
  | sort_by(.date);
def vitals_verdict:
  if length == 0 then "SEM_DADOS"
  elif any(.[]; (.users // 0) >= $min and (((.crash // 0) > $cmax) or ((.anr // 0) > $amax))) then "ALARME"
  elif any(.[]; (.users // 0) >= $min) then "OK"
  else "POUCOS_USUARIOS" end;
'

VITALS_CMAX="${VITALS_CRASH_MAX:-0.0109}"
VITALS_AMAX="${VITALS_ANR_MAX:-0.0047}"
VITALS_MIN="${VITALS_MIN_USERS:-100}"

# vitals_jq FILTRO [opções do jq...] : roda o filtro com as definições e os limites
vitals_jq() {
  local filter="$1"; shift
  jq "$@" --argjson cmax "$VITALS_CMAX" --argjson amax "$VITALS_AMAX" --argjson min "$VITALS_MIN" "$VITALS_DEFS $filter"
}

vitals_check_limits() {
  case "$VITALS_CMAX" in ''|*[!0-9.]*|.*|*.|*.*.*) die "VITALS_CRASH_MAX inválido (use fração, ex.: 0.0109)." ;; esac
  case "$VITALS_AMAX" in ''|*[!0-9.]*|.*|*.|*.*.*) die "VITALS_ANR_MAX inválido (use fração, ex.: 0.0047)." ;; esac
  case "$VITALS_MIN" in ''|*[!0-9]*) die "VITALS_MIN_USERS inválido (só números, ex.: 100)." ;; esac
}

# Autoteste (dry_run): não chama a API nem precisa de credencial. Usa limites fixos, nunca o ambiente.
vitals_selftest() {
  local fails=0 n=0 got
  VITALS_CMAX=0.0109; VITALS_AMAX=0.0047; VITALS_MIN=100
  check() { # nome esperado entrada
    n=$((n + 1))
    got=$(printf '%s' "$3" | vitals_jq 'vitals_verdict' -r 2>&1) || got="erro do jq: $got"
    if [ "$got" = "$2" ]; then
      note "  ok    $1 -> $got"
    else
      note "  FALHA $1: esperado $2, veio $got"; fails=$((fails + 1))
    fi
  }
  note "Autoteste do veredito do vitals (limites fixos: falha > 0.0109, ANR > 0.0047, piso de 100 usuários):"
  check "dentro dos limites" OK '[{"users":150,"crash":0.005,"anr":0.001}]'
  check "falha acima do limite" ALARME '[{"users":200,"crash":0.011,"anr":0.001}]'
  check "ANR acima do limite" ALARME '[{"users":200,"crash":0.001,"anr":0.0048}]'
  check "limite exato não alarma (estrito)" OK '[{"users":100,"crash":0.0109,"anr":0.0047}]'
  check "todos os dias abaixo do piso" POUCOS_USUARIOS '[{"users":50,"crash":0.001,"anr":0.0}]'
  check "99 usuários acima do limite" POUCOS_USUARIOS '[{"users":99,"crash":0.05,"anr":0.05}]'
  check "99 usuários acima do limite e outro dia ok" OK '[{"users":99,"crash":0.05,"anr":0.05},{"users":120,"crash":0.001,"anr":0.0}]'
  check "alarme em só um dos dias" ALARME '[{"users":120,"crash":0.001,"anr":0.0},{"users":130,"crash":0.02,"anr":0.0}]'
  check "taxa ausente não alarma" OK '[{"users":150,"crash":null,"anr":null}]'
  check "sem linhas" SEM_DADOS '[]'
  # junção das duas consultas, no formato da Reporting API
  local crash anr merged
  crash='{"rows":[{"aggregationPeriod":"DAILY","startTime":{"year":2026,"month":10,"day":22},"metrics":[{"metric":"userPerceivedCrashRate","decimalValue":{"value":"0.0123"}},{"metric":"crashRate","decimalValue":{"value":"0.02"}},{"metric":"distinctUsers","decimalValue":{"value":"140"}}]},{"aggregationPeriod":"DAILY","startTime":{"year":2026,"month":10,"day":23},"metrics":[{"metric":"userPerceivedCrashRate","decimalValue":{"value":"0.001"}},{"metric":"distinctUsers","decimalValue":{"value":"90"}}]}]}'
  anr='{"rows":[{"aggregationPeriod":"DAILY","startTime":{"year":2026,"month":10,"day":22},"metrics":[{"metric":"userPerceivedAnrRate","decimalValue":{"value":"0.001"}},{"metric":"anrRate","decimalValue":{"value":"0.002"}},{"metric":"distinctUsers","decimalValue":{"value":"140"}}]},{"aggregationPeriod":"DAILY","startTime":{"year":2026,"month":10,"day":23},"metrics":[{"metric":"userPerceivedAnrRate","decimalValue":{"value":"0.0"}},{"metric":"distinctUsers","decimalValue":{"value":"90"}}]}]}'
  merged=$(vitals_jq 'vitals_days($c; $a)' -c -n --argjson c "$crash" --argjson a "$anr" 2>&1) || merged="erro do jq: $merged"
  check "API crua: junção e alarme de falha" ALARME "$merged"
  n=$((n + 1))
  if [ "$(printf '%s' "$merged" | jq -r 'length, .[0].date, .[0].users, .[1].users, .[1].anr' 2>&1 | tr '\n' ' ')" = "2 2026-10-22 140 90 0 " ]; then
    note "  ok    API crua: dois dias, usuários e ANR lidos"
  else
    note "  FALHA API crua: junção diferente da esperada ($merged)"; fails=$((fails + 1))
  fi
  n=$((n + 1))
  if [ "$(vitals_jq 'vitals_days($c; $a)' -c -n --argjson c '{}' --argjson a '{}' 2>&1)" = "[]" ]; then
    note "  ok    API crua sem linhas -> lista vazia"
  else
    note "  FALHA API crua sem linhas não deu lista vazia"; fails=$((fails + 1))
  fi
  if [ "$fails" -ne 0 ]; then die "Autoteste do vitals: $fails de $n verificações falharam."; fi
  note "Autoteste do vitals ok ($n verificações). Nenhuma chamada à API."
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "**Autoteste do vitals ok ($n verificações)**" >> "$GITHUB_STEP_SUMMARY"; fi
}

vitals_fail() { # $1 = o que estava fazendo
  if [ "$HTTP_CODE" = "403" ]; then
    api_fail "$1: a conta não pode ler relatórios ou a Reporting API está desligada"
  fi
  api_fail "$1"
}

do_vitals() {
  local fresh end start body crash anr days verdict until_day tz="America/Los_Angeles"  # fuso que a API exige no DAILY

  api GET "/crashRateMetricSet" || vitals_fail "Não consegui ler a atualidade dos dados de falha"
  printf '%s' "$RESP" | jq -e 'type == "object"' >/dev/null 2>&1 || die "A Reporting API devolveu uma resposta inesperada para crashRateMetricSet."
  fresh=$(printf '%s' "$RESP" | jq -r '
    def p2: tostring | ("0" + .)[-2:];
    [(.freshnessInfo.freshnesses // [])[] | select(.aggregationPeriod == "DAILY") | .latestEndTime][0]
    | if . == null then empty else "\(.year)-\(.month | p2)-\(.day | p2)" end') || die "Não consegui interpretar a atualidade dos dados."

  if [ -z "$fresh" ]; then
    # a API ainda não tem dado diário do app: não é falha de leitura
    days='[]'; until_day="n/d"
  else
    case "$fresh" in [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;; *) die "Data de atualidade inválida vinda da API (\"$fresh\")." ;; esac
    end=$(date -u -d "$fresh" +%F) || die "Não consegui interpretar a data $fresh."
    start=$(date -u -d "$end - $VITALS_WINDOW_DAYS days" +%F) || die "Não consegui calcular a janela de $VITALS_WINDOW_DAYS dias."
    # latestEndTime é exclusivo: o último dia coberto é o anterior
    until_day=$(date -u -d "$end - 1 day" +%F) || die "Não consegui calcular o último dia da janela."

    body=$(jq -nc --arg s "$start" --arg e "$end" --arg tz "$tz" --arg vc "$VERSION_CODE" '
      def dt($d): ($d | split("-") | map(tonumber)) as $p
        | {year: $p[0], month: $p[1], day: $p[2], timeZone: {id: $tz}};
      {timelineSpec: {aggregationPeriod: "DAILY", startTime: dt($s), endTime: dt($e)},
       dimensions: ["versionCode"], filter: ("versionCode = " + $vc), pageSize: 100}')

    crash=$(jq -c '. + {metrics: ["userPerceivedCrashRate", "crashRate", "distinctUsers"]}' <<<"$body")
    api POST "/crashRateMetricSet:query" "$crash" || vitals_fail "Não consegui consultar a taxa de falha"
    crash="$RESP"
    anr=$(jq -c '. + {metrics: ["userPerceivedAnrRate", "anrRate", "distinctUsers"]}' <<<"$body")
    api POST "/anrRateMetricSet:query" "$anr" || vitals_fail "Não consegui consultar a taxa de ANR"
    anr="$RESP"
    printf '%s' "$crash" | jq -e 'type == "object"' >/dev/null 2>&1 || die "Resposta inesperada da consulta de falha."
    printf '%s' "$anr" | jq -e 'type == "object"' >/dev/null 2>&1 || die "Resposta inesperada da consulta de ANR."
    days=$(vitals_jq 'vitals_days($c; $a)' -c -n --argjson c "$crash" --argjson a "$anr") || die "Não consegui juntar as consultas de falha e ANR."
  fi

  verdict=$(printf '%s' "$days" | vitals_jq 'vitals_verdict' -r) || die "Não consegui calcular o veredito."
  case "$verdict" in ALARME|OK|POUCOS_USUARIOS|SEM_DADOS) ;; *) die "Veredito inesperado (\"$verdict\")." ;; esac

  note "vitals de $PACKAGE, versionCode $VERSION_CODE, janela de $VITALS_WINDOW_DAYS dias (dia | usuários | falha % | ANR %):"
  printf '%s' "$days" | jq -r '
    def pct: if . == null then "-" else ((. * 10000 | round) / 100 | tostring) end;
    .[] | "  \(.date) | \(.users) | \(.crash | pct) | \(.anr | pct)"'
  note "vitals: $verdict (versionCode $VERSION_CODE, até $until_day)"
  jq -nc --arg vc "$VERSION_CODE" --arg v "$verdict" --arg upto "$until_day" --argjson days "$days" \
    --argjson cmax "$VITALS_CMAX" --argjson amax "$VITALS_AMAX" --argjson min "$VITALS_MIN" --argjson w "$VITALS_WINDOW_DAYS" \
    '{versionCode: ($vc | tonumber), verdict: $v, until: $upto, window_days: $w,
      limits: {crash_max: $cmax, anr_max: $amax, min_users: $min}, days: $days}' | sed 's/^/vitals_json: /'

  if [ "$verdict" = "ALARME" ] && [ -n "${GITHUB_ACTIONS:-}" ]; then
    echo "::warning::Android vitals em ALARME para o versionCode $VERSION_CODE (falha percebida > $VITALS_CMAX ou ANR percebido > $VITALS_AMAX com pelo menos $VITALS_MIN usuários no dia)."
  fi
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Android vitals: versionCode $VERSION_CODE → $verdict"
      echo "Janela de $VITALS_WINDOW_DAYS dias, até $until_day. Limites: falha percebida > $VITALS_CMAX, ANR percebido > $VITALS_AMAX, piso de $VITALS_MIN usuários no dia."
      echo
      echo "| Dia | Usuários | Falha % | ANR % |"
      echo "|---|---|---|---|"
      printf '%s' "$days" | jq -r '
        def pct: if . == null then "-" else ((. * 10000 | round) / 100 | tostring) end;
        .[] | "| \(.date) | \(.users) | \(.crash | pct) | \(.anr | pct) |"'
    } >> "$GITHUB_STEP_SUMMARY"
  fi
}

# ---- ações que mudam a produção: calculam o corpo novo da faixa em $NEW_TRACK ----

plan_promote() {
  local alpha prod rel max inprog big name notes newrel
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
  case "$max" in ''|*[!0-9]*) die "Trava 3: não consegui ler o maior versionCode da produção (veio \"$max\"). Nada foi enviado." ;; esac
  if [ "$VERSION_CODE" -le "$max" ]; then
    die "Trava 3: o versionCode $VERSION_CODE não é maior que o maior da produção ($max)."
  fi

  # release em andamento na produção: a Play não aceita dois
  inprog=$(printf '%s' "$prod" | jq -c '[(.releases // [])[] | select(.status == "inProgress" or .status == "halted")] | .[0] // empty')
  if [ -n "$inprog" ]; then
    big=$(printf '%s' "$inprog" | jq -r '(.status == "inProgress") and ((.userFraction // 0) >= 0.5)') || die "Não consegui avaliar o release em andamento da produção."
    case "$big" in
      false) ;;
      true) die "A produção tem um release em andamento em 50% ou mais ($(printf '%s' "$inprog" | jq -r '.versionCodes | join(",")')). Conclua o antigo (advance, fraction=1) e promova a nova depois." ;;
      *) die "Não consegui avaliar a fração do release em andamento da produção (resposta \"$big\"). Nada foi enviado." ;;
    esac
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
    if $f == 1 then
      # concluir: a Play quer só o release que está sendo concluído
      {track: "production", releases: [(.releases // [])[]
        | select(.status == "inProgress" and ((.versionCodes // []) | index($v)))
        | (.status = "completed" | del(.userFraction))]}
    else
      {track: "production", releases: [(.releases // [])[]
        | if .status == "inProgress" and ((.versionCodes // []) | index($v)) then (.userFraction = $f) else . end]}
    end')
  if [ "$FRACTION" = "1" ]; then MSG="Produção: 100% (versionCode $VERSION_CODE)"; else MSG="Produção: 50% (versionCode $VERSION_CODE)"; fi
}

plan_halt() {
  local rel n
  track_get production; PROD="$RESP"
  if [ -z "$VERSION_CODE" ]; then
    # sem versionCode: pausa o único release em andamento
    n=$(printf '%s' "$PROD" | jq -r '[(.releases // [])[] | select(.status == "inProgress")] | length') || die "Não consegui ler a produção."
    case "$n" in
      1) VERSION_CODE=$(printf '%s' "$PROD" | jq -r '[(.releases // [])[] | select(.status == "inProgress")] | .[0].versionCodes[0]') ;;
      0) die "Não há release em andamento na produção para pausar." ;;
      *) die "Há $n releases em andamento na produção; informe o versionCode para pausar." ;;
    esac
  fi
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

# uma tentativa completa, numa edição nova (o vitals não abre edição)
attempt() {
  if [ "$ACTION" = "vitals" ]; then
    do_vitals
    return 0
  fi
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
    promote|advance|halt|resume|status|vitals) ;;
    *) echo "Uso: tools/play-rollout.sh <promote|advance|halt|resume|status|vitals> [versionCode] [fração]" >&2; exit 2 ;;
  esac
  if [ "$ACTION" = "vitals" ] && [ "$DRY_RUN" = "true" ]; then
    # autoteste: sem credencial, sem rede, sem versionCode
    command -v jq >/dev/null 2>&1 || { err "Falta a ferramenta jq no ambiente."; exit 1; }
    vitals_selftest
    exit 0
  fi
  if [ "$ACTION" != "status" ] && ! { [ "$ACTION" = "halt" ] && [ -z "$VERSION_CODE" ]; }; then
    case "$VERSION_CODE" in
      ''|*[!0-9]*) die "Informe o versionCode (só números) para a ação $ACTION." ;;
    esac
  fi
  for tool in openssl curl jq; do
    command -v "$tool" >/dev/null 2>&1 || { err "Falta a ferramenta $tool no ambiente."; exit 1; }
  done

  if [ "$ACTION" = "vitals" ]; then
    vitals_check_limits
    API_BASE="$REPORTING_BASE"
    SCOPE="https://www.googleapis.com/auth/playdeveloperreporting"
  fi

  setup_tmp
  get_token

  local tries=0 rc
  while :; do
    tries=$((tries + 1))
    set +e
    ( set -e; attempt )
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
