#!/usr/bin/env python3
"""Varredura do binario iOS contra os termos proibidos (#118, Apple 2.3.10).

A Apple le o binario inteiro, nao so a tela: "Google Play" numa string do app foi a causa da recusa da 0.47.0.
Roda `strings -a` em cada arquivo dado (executavel do .app, Info.plist, .storekit...), confere cada linha contra
`tools/ios-forbidden-terms.txt` (um regex por linha, sem diferenciar maiusculas) e separa o que a allowlist
(`tools/ios-binary-allowlist.txt`, um regex por linha, aprovado pelo Tech Lead) ja aprovou.

Uso: ios-binary-scan.py [--terms ARQ] [--allow ARQ] [--enforce] CAMINHO [CAMINHO...]
  CAMINHO pode ser arquivo ou pasta (varre todos os arquivos de dentro; passe o executavel e o Info.plist, nao a pasta do .app inteira).
Sem --enforce so relata (saida 0): primeira rodada, para fechar a allowlist. Com --enforce, achado sem allowlist = saida 1.
O relatorio vai para o log e, se existir, para $GITHUB_STEP_SUMMARY. Nada aqui usa segredo nem rede."""
import argparse
import os
import re
import subprocess
import sys


def regexes(path):
    out = []
    if not path or not os.path.exists(path):
        return out
    with open(path, encoding="utf-8") as f:
        for linha in f:
            linha = linha.strip()
            if linha and not linha.startswith("#"):
                out.append(re.compile(linha, re.IGNORECASE))
    return out


def strings_de(path):
    try:
        r = subprocess.run(["strings", "-a", path], capture_output=True, text=True, errors="replace", timeout=300)
        return r.stdout.splitlines()
    except (OSError, subprocess.SubprocessError):
        with open(path, "rb") as f:  # sem `strings` (ex.: fora do macOS): extrai ASCII imprimivel na mao
            dados = f.read()
        return [m.decode("latin-1") for m in re.findall(rb"[\x20-\x7e]{4,}", dados)]


def arquivos(caminhos):
    for c in caminhos:
        if os.path.isdir(c):
            for raiz, _, nomes in os.walk(c):
                for n in nomes:
                    yield os.path.join(raiz, n)
        elif os.path.exists(c):
            yield c
        else:
            print(f"::warning::caminho nao encontrado: {c}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--terms", default="tools/ios-forbidden-terms.txt")
    ap.add_argument("--allow", default="tools/ios-binary-allowlist.txt")
    ap.add_argument("--enforce", action="store_true")
    ap.add_argument("caminhos", nargs="+")
    args = ap.parse_args()

    termos = regexes(args.terms)
    allow = regexes(args.allow)
    if not termos:
        print("::error::lista de termos vazia ou ausente:", args.terms)
        return 1

    achados, aprovados, lidos = [], [], 0
    for arq in arquivos(args.caminhos):
        lidos += 1
        vistos = set()
        for linha in strings_de(arq):
            for t in termos:
                if t.search(linha):
                    chave = (arq, t.pattern, linha)
                    if chave in vistos:
                        continue
                    vistos.add(chave)
                    ok = any(a.search(linha) for a in allow)
                    (aprovados if ok else achados).append((arq, t.pattern, linha.strip()[:200]))

    def bloco(titulo, itens):
        out = [f"### {titulo} ({len(itens)})"]
        por_termo = {}
        for arq, termo, linha in itens:
            por_termo.setdefault(termo, []).append((arq, linha))
        for termo, ocorr in sorted(por_termo.items()):
            out.append(f"- `{termo}`: {len(ocorr)} ocorrencia(s)")
            for arq, linha in ocorr[:15]:
                out.append(f"    - {os.path.basename(arq)}: `{linha}`")
            if len(ocorr) > 15:
                out.append(f"    - ... e mais {len(ocorr) - 15}")
        return out

    linhas = [f"## Varredura do binario iOS ({'ENFORCE' if args.enforce else 'so relatorio'})",
              f"{lidos} arquivo(s) lidos, {len(termos)} termos, {len(allow)} regras de allowlist."]
    linhas += bloco("Achados sem allowlist", achados) if achados else ["### Achados sem allowlist (0)"]
    if aprovados:
        linhas += bloco("Aprovados pela allowlist", aprovados)
    texto = "\n".join(linhas)
    print(texto)
    resumo = os.environ.get("GITHUB_STEP_SUMMARY")
    if resumo:
        with open(resumo, "a", encoding="utf-8") as f:
            f.write(texto + "\n")

    if achados and args.enforce:
        print(f"::error::{len(achados)} texto(s) proibido(s) no binario iOS (Apple 2.3.10). Tire do commonMain (androidOnlyText) ou, se for do runtime, aprove na allowlist.")
        return 1
    if achados:
        print(f"::warning::{len(achados)} texto(s) proibido(s) no binario iOS (modo relatorio: nao reprova). Feche a allowlist antes de ligar --enforce.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
