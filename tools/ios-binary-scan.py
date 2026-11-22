#!/usr/bin/env python3
"""Varredura do binario iOS contra os termos proibidos (#118, Apple 2.3.10).

A Apple le o binario inteiro, nao so a tela: "Google Play" numa string do app foi a causa da recusa da 0.47.0.
Roda `strings -a` em cada arquivo dado (executavel do .app e Info.plist), confere cada linha contra
`tools/ios-forbidden-terms.txt` (um regex por linha, sem diferenciar maiusculas) e separa o que a allowlist
(`tools/ios-binary-allowlist.txt`, um regex por linha, aprovado pelo Tech Lead) ja aprovou.

A allowlist remove SO o trecho que ela casa: a linha so e aprovada se, depois de tirar os trechos aprovados, o termo
proibido nao casar mais. Assim "androidx ... Google Play" continua achado (o `androidx` some, o `Google Play` fica).
Achados sao deduplicados por (termo, linha) entre os arquivos e agrupados pelo trecho que casou.

Uso: ios-binary-scan.py [--terms ARQ] [--allow ARQ] [--enforce] CAMINHO [CAMINHO...]
Sem --enforce so relata (saida 0). Com --enforce, achado sem allowlist = saida 1.
O relatorio vai para o log e, se existir, para $GITHUB_STEP_SUMMARY. Nada aqui usa segredo nem rede."""
import argparse
import os
import re
import subprocess
import sys

TETO_POR_TERMO = 200


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


def aprovado(linha, termo, allow):
    """True se, tirando so os trechos da allowlist, o termo proibido nao casa mais na linha."""
    resto = linha
    for regra in allow:
        resto = regra.sub(" ", resto)
    return termo.search(resto) is None


def analisar(linhas, termos, allow):
    """linhas: iteravel de strings (de todos os arquivos). Devolve (achados, aprovados, regras):
    achados/aprovados = {termo.pattern: {trecho_casado: set(linhas)}}; regras = {regra.pattern: n de linhas aprovadas que ela tocou}."""
    achados, aprovados, regras = {}, {}, {}
    for linha in set(l.strip() for l in linhas):
        if not linha:
            continue
        for t in termos:
            m = t.search(linha)
            if not m:
                continue
            ok = aprovado(linha, t, allow)
            alvo = aprovados if ok else achados
            alvo.setdefault(t.pattern, {}).setdefault(m.group(0).lower(), set()).add(linha[:200])
            if ok:
                for regra in allow:
                    if regra.search(linha):
                        regras[regra.pattern] = regras.get(regra.pattern, 0) + 1
    return achados, aprovados, regras


def total(grupo):
    return sum(len(v) for v in grupo.values())


def relatorio(termos, achados, aprovados, regras, lidos, n_allow, enforce):
    linhas = [f"## Varredura do binario iOS ({'ENFORCE' if enforce else 'so relatorio'})",
              f"{lidos} arquivo(s) lidos, {len(termos)} termos, {n_allow} regras de allowlist. Linhas distintas por (termo, linha).",
              "",
              "| termo | bruto | aprovado | sem allowlist |", "|---|---|---|---|"]
    for t in termos:
        a, p = total(achados.get(t.pattern, {})), total(aprovados.get(t.pattern, {}))
        if a or p:
            linhas.append(f"| `{t.pattern}` | {a + p} | {p} | {a} |")
    linhas.append("")
    if not achados:
        linhas.append("### Achados sem allowlist (0)")
    for termo, grupos in sorted(achados.items()):
        linhas.append(f"### Achados sem allowlist: `{termo}` ({total(grupos)})")
        n = 0
        for trecho, ls in sorted(grupos.items()):
            for l in sorted(ls):
                if n >= TETO_POR_TERMO:
                    break
                linhas.append(f"- [{trecho}] `{l}`")
                n += 1
        if total(grupos) > n:
            linhas.append(f"- ... e mais {total(grupos) - n}")
    if aprovados:
        linhas.append("### Aprovados pela allowlist (so totais)")
        for termo, grupos in sorted(aprovados.items()):
            linhas.append(f"- `{termo}`: {total(grupos)}")
        for regra, n in sorted(regras.items()):
            linhas.append(f"- regra `{regra}`: tocou {n} linha(s) aprovada(s)")
    return "\n".join(linhas)


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

    lidos, todas = 0, []
    for arq in arquivos(args.caminhos):
        lidos += 1
        todas.extend(strings_de(arq))
    achados, aprovados, regras = analisar(todas, termos, allow)
    texto = relatorio(termos, achados, aprovados, regras, lidos, len(allow), args.enforce)
    print(texto)
    resumo = os.environ.get("GITHUB_STEP_SUMMARY")
    if resumo:
        with open(resumo, "a", encoding="utf-8") as f:
            f.write(texto + "\n")

    n = sum(total(g) for g in achados.values())
    if n and args.enforce:
        print(f"::error::{n} texto(s) proibido(s) no binario iOS (Apple 2.3.10). Tire do commonMain (platformGatedText) ou, se for do runtime, aprove na allowlist.")
        return 1
    if n:
        print(f"::warning::{n} texto(s) proibido(s) no binario iOS (modo relatorio: nao reprova). Feche a allowlist antes de ligar --enforce.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
