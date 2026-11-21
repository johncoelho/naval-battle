#!/usr/bin/env python3
"""Grava a sessão da conta de revisão no NSUserDefaults do app no simulador (capturas-iap, #65).

Os campos de texto do Compose não aceitam texto por XCUITest, então o CI faz o login pela API do Supabase
(mesma chamada do app: POST /auth/v1/token?grant_type=password) e grava as chaves que o app lê ao abrir
(`uid`, `email`, `token`, `refresh`: ProfileKeys.kt). Usuário e senha vêm só do ambiente
(IOS_REVIEW_EMAIL / IOS_REVIEW_PASSWORD); os tokens são mascarados no log e nunca impressos.
Uso: semear-sessao.py <caminho do .plist do app>   (com o simulador desligado)."""
import json, os, plistlib, re, sys, urllib.error, urllib.request

src = open("composeApp/src/commonMain/kotlin/br/com/navalbattle/data/Cloud.kt", encoding="utf-8").read()
url = re.search(r'const val URL = "([^"]+)"', src).group(1)
anon_block = re.search(r'const val ANON_KEY = ((?:"[^"]*"\s*\+?\s*)+)', src).group(1)
anon = "".join(re.findall(r'"([^"]*)"', anon_block))

body = json.dumps({"email": os.environ["IOS_REVIEW_EMAIL"], "password": os.environ["IOS_REVIEW_PASSWORD"]}).encode()
req = urllib.request.Request(url + "/auth/v1/token?grant_type=password", data=body,
                             headers={"apikey": anon, "Content-Type": "application/json"})
try:
    r = json.load(urllib.request.urlopen(req, timeout=30))
except urllib.error.HTTPError as e:
    print("login pela API recusado: HTTP", e.code)
    sys.exit(1)

for v in (r["access_token"], r["refresh_token"], r["user"]["id"]):
    print("::add-mask::" + v)

path = sys.argv[1]
os.makedirs(os.path.dirname(path), exist_ok=True)
prefs = {}
if os.path.exists(path):
    with open(path, "rb") as f:
        prefs = plistlib.load(f)
prefs.update({"uid": r["user"]["id"], "email": r["user"]["email"], "token": r["access_token"], "refresh": r["refresh_token"]})
with open(path, "wb") as f:
    plistlib.dump(prefs, f, fmt=plistlib.FMT_BINARY)
print("sessão gravada; chaves no plist:", sorted(prefs.keys()))
