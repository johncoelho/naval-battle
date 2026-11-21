#!/usr/bin/env python3
"""Trava do PM (docs/ESTEIRA.md > Quem faz o quê). PreToolUse.

Nega, só no agente PRINCIPAL, as ferramentas de execução reservadas aos agentes por perfil.
Subagente (payload com agent_id ou agent_type) passa livre. Falha aberta: qualquer erro, campo
estranho ou dúvida => permite (exit 0 sem saída).
Desligar: NB_PM_GUARD=off no ambiente, ou remover o bloco "hooks" de .claude/settings.json.
"""
import json, os, re, sys

MSG = ("PM não executa: delegue ao agente do perfil "
       "(ver docs/ESTEIRA.md > Quem faz o quê)")

CMD_RE = re.compile(
    r"^(?:\w+=\S*\s+)*(?:"
    r"gh\s+workflow\s+run\b"
    r"|gh\s+run\s+download\b"
    r"|git\s+(?:-C\s+\S+\s+)?push\b"
    r"|supabase\s+(?:db\s+query|functions\s+deploy|secrets)\b"
    r"|(?:(?:bash|sh|cmd\s+/c)\s+)?(?:\.{0,2}[/\\])?gradlew(?:\.bat)?\b"
    r")", re.I)
PATH_DIRS = ("composeApp", "iosApp", "supabase", "site", ".github", "tools")


def denied_command(cmd):
    for seg in re.split(r"&&|\|\||;|\||\n|\(|\)|`", cmd):
        if CMD_RE.match(seg.strip()):
            return True
    return False


def denied_path(path, base):
    m = re.match(r"^/([a-zA-Z])/(.*)", base)  # caminho estilo Git Bash -> Windows
    if m:
        base = m.group(1).upper() + ":/" + m.group(2)
    if not path:
        return False
    p = os.path.normcase(os.path.abspath(path if os.path.isabs(path) else os.path.join(base, path)))
    b = os.path.normcase(os.path.abspath(base))
    rel = os.path.relpath(p, b) if p.startswith(b) else ""
    if not rel or rel.startswith(".."):
        return False
    first = rel.replace("\\", "/").split("/")[0]
    return first.lower() in [d.lower() for d in PATH_DIRS]


def main():
    if os.environ.get("NB_PM_GUARD", "").lower() == "off":
        return
    d = json.load(sys.stdin)
    if d.get("agent_id") or d.get("agent_type"):
        return  # subagente: livre
    tool, ti = d.get("tool_name", ""), d.get("tool_input") or {}
    bases = [os.environ.get("CLAUDE_PROJECT_DIR"), d.get("cwd"), os.getcwd()]
    bad = False
    if tool in ("Bash", "PowerShell"):
        bad = denied_command(ti.get("command", ""))
    elif tool in ("Edit", "Write", "NotebookEdit"):
        f = ti.get("file_path") or ti.get("notebook_path")
        bad = any(denied_path(f, b) for b in bases if b)
    if bad:
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse",
              "permissionDecision": "deny", "permissionDecisionReason": MSG}}))


try:
    main()
except Exception:
    pass
sys.exit(0)
