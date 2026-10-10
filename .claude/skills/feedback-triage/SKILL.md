---
name: feedback-triage
description: Triagem dos feedbacks (bugs e melhorias) enviados pelos jogadores do Naval Battle. Use quando o John perguntar "temos bugs?", "algum feedback?", ou antes de fechar a SPEC de uma melhoria.
---

# Triagem de feedback

Feedback vem da tela "Enviar feedback" para `public.feedback`. Aprovar paga recompensa ao jogador,
por isso **nenhuma aprovação ou recusa sem o OK do John**.

## 1. Listar

```sql
select id, username, kind, app_version, platform, lang, created_at, message
from public.feedback where status = 'pending' order by created_at;
```

Traga **todos** para o John, cada um com: quem, tipo, versão, plataforma, data, texto e uma
**gravidade sugerida** com o motivo:
- bug: `minor` / `medium` / `major` = 150 / 300 / 600 dobrões;
- melhoria: `small` / `large` = 1 / 3 cargas da habilidade sugerida.
Diga também se já reproduziu ou se o bug já foi corrigido em versão recente (procure no CHANGELOG).
Feedback de teste das contas QA: proponha apagar, sem recompensa.

## 2. Esperar a decisão

Só depois do OK de cada um:
```sql
select public.review_feedback('<id>', true|false, '<severity>', '<ability|null>', '<nota curta>');
```
O jogador recebe o aviso automaticamente na próxima abertura do app.

## 3. Transformar em trabalho

- Bug aprovado e não corrigido → entra no próximo ciclo (`dev-cycle`), referenciando o feedback no
  CHANGELOG.
- Melhoria aprovada → backlog no `MEMORY.md` ou SPEC, conforme o tamanho.
- Sessão do Supabase expirada no navegador: peça para o John entrar; não logue por ele.
