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

# --- casos com a allowlist e a lista de termos REAIS (tools/) ---
reais_termos = scan.regexes(os.path.join(aqui, "ios-forbidden-terms.txt"))
reais_allow = scan.regexes(os.path.join(aqui, "ios-binary-allowlist.txt"))


def estado(linha):
    a, p, _ = scan.analisar([linha], reais_termos, reais_allow)
    return "achado" if a else ("aprovado" if p else "limpo")


confere(estado("__ZN10SkPixelRef18android_only_resetEiim") == "aprovado", "android_only_reset do Skia aprovado")
confere(estado("L?NSt3__110__function6__funcIZN14SkAndroidCodec16getAndroidPixelsERK11SkImageInfoPvmPKNS2_14AndroidOptionsEE3$_0NS_9allocatorISA_EEFN7SkCodec6ResultES5_S6_mRKNSD_7OptionsEiEEE") == "aprovado", "SkAndroidCodec/getAndroidPixels aprovado")
confere(estado("_kfun:kotlinx.coroutines.flow#onSubscription__at__kotlinx.coroutines.flow.SharedFlow<0:0>(kotlin.coroutines.SuspendFunction1<kotlinx.coroutines.flow.FlowCollector<0:0>,kotlin.Unit>){0") == "aprovado", "onSubscription__at__ aprovado")
confere(estado("__ZNK16SkNoPixelsDevice23android_utils_clipAsRgnEP8SkRegion") == "aprovado", "android_utils_clipAsRgn aprovado")
confere(estado("__ZNK8SkCanvas25androidFramework_isClipAAEv") == "aprovado", "androidFramework_ aprovado")
confere(estado("Google Play abre 23android_utils_clipAsRgn") == "achado", "Google Play junto de android_utils_ continua achado")
confere(estado("android") == "aprovado", "linha pura android (ICU) aprovada")
confere(estado("Entrar com Google Play e android") == "achado", "texto nosso com android/Google Play continua achado")
sys.exit(0 if ok else 1)
