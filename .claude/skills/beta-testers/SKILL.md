---
name: beta-testers
description: Gestão dos testadores do teste fechado do Naval Battle — fila do site, lista "Testadores fechados" da Play, contagem para produção. Use quando o John perguntar por testadores novos, pedir para liberar alguém, ou quiser saber quantos estão inscritos.
---

# Testadores do teste fechado

Fluxo: o jogador se cadastra em `site/beta.html` → entra em `public.beta_testers` (`pending`) →
**eu** coloco o e-mail na lista da Play → marco `invited` → a página dele acende o botão de
instalar. A liberação é manual de propósito, e o aviso ao testador é só pela página.

## Novos na fila

```sql
select email, platform, status, created_at from public.beta_testers
where status = 'pending' order by created_at;
```

## Liberar na Play (Chrome do John, já logado)

1. Play Console → Naval Battle Classic → Testing → Closed testing → **Alpha** → aba **Testers**
   (`…/tracks/4699826548171185940?tab=testers`).
2. Seta da lista **"Testadores fechados"** → campo "Add email addresses": clicar pelo `ref` do campo
   (`find`), digitar o e-mail, Enter, conferir que entrou na lista, **Save changes** → confirmar Save.
3. Conferir na página que salvou ("Your changes have been saved") e fechar a aba.
4. `update public.beta_testers set status = 'invited' where email = '<email>';`

Remover alguém: mesma lista, lixeira da linha, salvar; status de volta para `pending` ou apagar a
linha, conforme o John disser.

## Contagem para produção

- Dashboard do app no Play Console → card "Apply for access to production": os três requisitos
  (versão no teste fechado, **12 inscritos**, 14 dias seguidos). Ler o texto da página.
- Para saber **quem** ainda não entrou: cruzar a lista da Play com `auth.users` (quem entrou e
  abriu o jogo tem conta) e com `beta_testers`.
- As contas QA (`aigamesfactory.qa01/qa02`) estão na lista para testes, mas **não contam** como
  testadores reais.

## Responder ao John

Quem entrou desde a última vez, quem está pendente, o total inscrito e o que falta para a produção.
