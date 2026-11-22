#!/usr/bin/env python3
"""Escolhe o tipo de simulador de iPad do ensaio de revisao (#118). A Apple revisou no iPad Air 11" (M3); se a imagem
do runner nao tiver, vale o iPad mais proximo. Le `xcrun simctl list devicetypes -j` da entrada padrao e imprime o nome."""
import json
import sys

nomes = [d["name"] for d in json.load(sys.stdin)["devicetypes"]]
for pref in ("iPad Air 11-inch (M3)", "iPad Air 11-inch (M2)", "iPad Pro 11-inch (M4)", "iPad Pro 11-inch (M5)", "iPad Air (5th generation)"):
    if pref in nomes:
        print(pref)
        break
else:
    print(next(n for n in nomes if n.startswith("iPad")))
