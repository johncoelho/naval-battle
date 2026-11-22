#!/usr/bin/env python3
"""Teste curto da varredura do binario (#118): a allowlist remove so o trecho casado, nunca a linha inteira.
Uso: python3 tools/ios-binary-scan-test.py"""
import importlib.util
import os
import re
import sys

aqui = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("scan", os.path.join(aqui, "ios-binary-scan.py"))
scan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scan)

termos = [re.compile(p, re.IGNORECASE) for p in (r"android", r"google play", r"\bbeta\b")]
allow = [re.compile(r"\S*androidx\S*", re.IGNORECASE), re.compile(r"^beta$", re.IGNORECASE)]

linhas = [
    "androidx.compose.ui.scene.ComposeLayersViewController",   # so ruido: aprovada
    "androidx.compose.ui.x chegou a Google Play",              # androidx aprovado, Google Play continua: achado
    "beta",                                                    # aprovada (chave do servidor)
    "Prêmio beta do jogo",                                     # achado
    "ok sem nada",
]
achados, aprovados, regras = scan.analisar(linhas, termos, allow)
ok = True


def confere(cond, msg):
    global ok
    if not cond:
        print("FALHOU:", msg)
        ok = False


todas_achadas = {l for g in achados.values() for ls in g.values() for l in ls}
confere("androidx.compose.ui.x chegou a Google Play" in todas_achadas, "androidx + Google Play tem de continuar achado")
confere("Prêmio beta do jogo" in todas_achadas, "beta fora da regra tem de ser achado")
confere("androidx.compose.ui.scene.ComposeLayersViewController" not in todas_achadas, "ruido androidx puro tem de ser aprovado")
confere("beta" not in todas_achadas, "a regra ^beta$ aprova a linha 'beta'")
confere(scan.total(achados.get(r"android", {})) == 0, "android: tudo aprovado (so androidx)")
confere(scan.total(aprovados.get(r"android", {})) == 2, "android: 2 aprovados")
confere(scan.total(achados.get(r"google play", {})) == 1, "google play: 1 achado")
sys.exit(0 if ok else 1)
