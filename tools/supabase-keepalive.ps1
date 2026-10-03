# Mantém o projeto Supabase acordado: o plano gratuito pausa o projeto depois de ~7 dias
# sem requisições. Uma leitura simples na API REST a cada 2 dias conta como atividade.
#
# Registrado no Agendador de Tarefas do Windows como "NavalBattle Supabase keepalive"
# (ver docs/BUILD.md). URL e chave anônima saem de Cloud.kt — a mesma chave pública
# que já vai dentro do app, então nada secreto fica neste arquivo.

$ErrorActionPreference = "Stop"
$cloud = Join-Path $PSScriptRoot "..\composeApp\src\commonMain\kotlin\br\com\navalbattle\data\Cloud.kt"
$src = Get-Content $cloud -Raw -Encoding UTF8

$url = [regex]::Match($src, 'const val URL = "([^"]+)"').Groups[1].Value
$keyBlock = [regex]::Match($src, '(?s)const val ANON_KEY = (.+?)\r?\n\s*\r?\n').Groups[1].Value
$key = ([regex]::Matches($keyBlock, '"([^"]*)"') | ForEach-Object { $_.Groups[1].Value }) -join ""

$log = Join-Path $env:LOCALAPPDATA "naval-battle-keepalive.log"
try {
    $r = Invoke-WebRequest -UseBasicParsing -Uri "$url/rest/v1/app_config?select=key&limit=1" `
        -Headers @{ apikey = $key; Authorization = "Bearer $key" } -TimeoutSec 30
    "$(Get-Date -Format s) ok $($r.StatusCode)" | Add-Content $log
} catch {
    "$(Get-Date -Format s) erro $($_.Exception.Message)" | Add-Content $log
    exit 1
}
