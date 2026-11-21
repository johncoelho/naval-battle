#!/usr/bin/env python3
"""Trava do PM (docs/ESTEIRA.md > Quem faz o quê). PreToolUse.

Nega, só no agente PRINCIPAL, as ferramentas de execução reservadas aos agentes por perfil.
Subagente (payload com agent_id ou agent_type) passa livre. Falha aberta: qualquer erro, campo
estranho ou dúvida na LEITURA do payload => permite (exit 0 sem saída). Já na análise de um
`supabase db query` ou de aspas mal fechadas, dúvida => nega.
Desligar: NB_PM_GUARD=off no ambiente, ou remover o bloco "hooks" de .claude/settings.json.
"""
import json, os, re, sys

MSG = ("PM não executa: delegue ao agente do perfil "
       "(ver docs/ESTEIRA.md > Quem faz o quê)")

PREFIX = (r"^(?:(?:then|do|else|elif|\{)\s+)*(?:(?:env|command|time|exec|nohup|xargs)\s+)*"
          r"(?:\w+=\S*\s+)*")
# supabase [flags globais, com valor opcional] <subcomando>
SB = (r"supabase(?:\s+--?[A-Za-z][\w-]*(?:=\S*)?"
      r"(?:\s+(?!db\b|functions\b|secrets\b)[^-\s]\S*)?)*\s+")
WRAP_RE = re.compile(r"^(?:eval|(?:bash|sh|zsh|pwsh|powershell)(?:\.exe)?(?:\s+-[A-Za-z]+)*)\s+(.+)$", re.I)
CMD_RE = re.compile(
    PREFIX + r"(?:"
    r"gh\s+workflow\s+run\b"
    r"|gh\s+run\s+download\b"
    r"|git(?:\s+-{1,2}[\w-]+(?:=\S*)?(?:\s+[^-\s]\S*)?)*\s+push\b"
    r"|" + SB + r"(?:functions\s+deploy|secrets|db\s+push|db\s+reset)\b"
    r"|(?:(?:bash|sh|cmd\s+/c)\s+)?(?:\.{0,2}[/\\])?gradlew(?:\.bat)?\b"
    r")", re.I)
DB_RE = re.compile(PREFIX + SB + r"db\s+query\b", re.I)
PATH_DIRS = ("composeApp", "iosApp", "supabase", "site", ".github", "tools")

# SQL de leitura: começa com select/with, sem palavra de escrita, sem função fora da lista de leitura.
# Em dúvida, nega (o hook é um filtro por texto, não um parser de SQL).
SQL_WRITE_RE = re.compile(
    r"\b(insert|update|delete|drop|alter|create|grant|revoke|truncate|perform|call|do|copy|merge|"
    r"vacuum|set|execute|lock|comment|reindex|refresh|cluster|reset|notify|listen|into|"
    r"nextval|setval|set_config|dblink|net|http\w*)\b|\$|\bpg_\w+|\blo_\w+", re.I)
SQL_KEYWORDS = set("""as in values exists over from join on and or not any all where select with by
having union intersect except case when then else end filter within group distinct lateral using limit
offset order like ilike between is null left right inner outer cross full natural array row""".split())
SQL_SAFE_FUNCS = set("""count sum min max avg coalesce nullif now lower upper length char_length to_char
date_trunc extract round floor ceil ceiling abs greatest least cast array_agg string_agg row_number rank
dense_rank date to_timestamp to_date substring substr split_part btrim trim ltrim rtrim replace concat
position age generate_series percentile_cont percentile_disc stddev variance lag lead first_value
last_value ntile unnest bool_and bool_or every md5 initcap""".split())
SQL_CALL_RE = re.compile(r"([A-Za-z_][\w.]*)\s*\(")


def split_segments(cmd):
    """Divide em ; & && | || quebra de linha, crase e parênteses, respeitando aspas e barra de escape.
    Devolve (segmentos, duvida): duvida=True se sobrou aspa aberta ou há aspa escapada."""
    segs, cur, q, i = [], [], None, 0
    doubt = False
    while i < len(cmd):
        c = cmd[i]
        if c == "\\" and q != "'" and i + 1 < len(cmd):
            if cmd[i + 1] in "'\"":
                doubt = True
            cur.append(c + cmd[i + 1])
            i += 2
            continue
        if q:
            cur.append(c)
            if c == q:
                q = None
        elif c in "'\"":
            q = c
            cur.append(c)
        elif c in ";|&\n`(){}":
            if c in "{}":
                cur.append(c)  # só separa pelo texto; `{ git push; }` fica coberto pelo PREFIX
            else:
                segs.append("".join(cur))
                cur = []
        else:
            cur.append(c)
        i += 1
    segs.append("".join(cur))
    return segs, doubt or q is not None


def sql_is_read_only(sql):
    s = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    s = re.sub(r"--[^\n]*", " ", s).strip()
    s = re.sub(r";\s*$", "", s)
    if ";" in s or not re.match(r"^(select|with)\b", s, re.I):
        return False
    if s[:4].lower() == "with" and not re.search(r"\bselect\b", s, re.I):
        return False
    if SQL_WRITE_RE.search(s):
        return False
    for name in SQL_CALL_RE.findall(s):
        n = name.lower()
        if n in SQL_KEYWORDS or n in SQL_SAFE_FUNCS or re.match(r"^jsonb?_\w+$", n):
            continue
        return False
    return True


VALUE_FLAGS = ("-o", "--output", "--db-url", "--workdir", "--project-ref")


def db_query_denied(seg, bases):
    try:
        import shlex
        toks = shlex.split(seg)
        toks = toks[[t.lower() for t in toks].index("query") + 1:]
        files, pos, i = [], [], 0
        while i < len(toks):
            t = toks[i]
            if t in ("-f", "--file"):
                files.append(toks[i + 1])
                i += 1
            elif t.startswith("--file="):
                files.append(t.split("=", 1)[1])
            elif t in VALUE_FLAGS:
                i += 1
            elif not t.startswith("-"):
                pos.append(t)
            i += 1
        if len(pos) + len(files) != 1:
            return True  # nada, ou mais de um: dúvida
        if pos:
            return not sql_is_read_only(pos[0])
        for b in bases:
            fp = files[0] if os.path.isabs(files[0]) else os.path.join(b, files[0])
            if os.path.isfile(fp) and os.path.getsize(fp) < 200000:
                with open(fp, encoding="utf-8") as fh:
                    return not sql_is_read_only(fh.read())
        return True  # arquivo ilegível
    except Exception:
        return True


def denied_command(cmd, bases=()):
    segs, doubt = split_segments(cmd)
    if doubt:  # aspa aberta ou escapada: confere também o texto cru, sem respeitar aspas
        segs = segs + re.split(r"&&|\|\||;|&|\||\n|\(|\)|`|\{|\}", cmd)
    for seg in segs:
        seg = seg.strip()
        w = WRAP_RE.match(seg)  # bash -c "...", powershell -Command "...", eval "..."
        if w and denied_command(w.group(1).strip().strip("'\""), bases):
            return True
        if CMD_RE.match(seg):
            return True
        if DB_RE.match(seg) and (doubt or db_query_denied(seg, bases)):
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
        bad = denied_command(ti.get("command", ""), [b for b in bases if b])
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
