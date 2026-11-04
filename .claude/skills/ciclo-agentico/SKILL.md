---
name: ciclo-agentico
description: Regra padrão de como o agente principal (PM) trata TODO pedido do John que mexa em algo no Naval Battle (código, banco, CI, site, docs, release, flags, testes, consoles/navegador). Primeiro registra a issue e o cartão no quadro, depois delega à cadeia Suporte → PO → Tech Lead → Dev → Tech Lead → Release → QA → PO → Release e só devolve ao John com a cadeia fechada e validada. O principal não executa. Use em todo pedido que altere algo; só conversa, dúvida e consulta de estado ficam fora.
---

# Ciclo agêntico: pedido → issue → cadeia → devolução

Aprovada pelo John em 04/11/2026. **É a regra padrão**: vale para todo pedido que altere algo, sem
ele precisar lembrar. Processo, quadro e comandos: `docs/ESTEIRA.md`. Agentes: `.claude/agents/`.
Fale em português do Brasil. Esta skill diz **quem faz o quê**; o *como* de cada etapa está nas
skills `dev-cycle`, `esteira`, `qa-full-test` e `play-store-release`.

## Papéis

- **Principal (PM)**: quem conversa com o John no chat; pode ter vários nomes (PM, Product Manager,
  orquestrador). Entende a demanda, registra, delega, cobra e só devolve ao John quando a entrega foi
  validada. **Não executa**: não edita código, não roda workflow, não mexe em banco ou produção, não
  testa.
- **Suporte**: triagem. **PO**: dono da história, SPEC, critérios de aceite, ordena a Fila e atesta
  que a história foi atendida. **Tech Lead**: desenho e revisão de todo diff antes do push.
  **Dev**: implementa em worktree próprio, entrega o diff ao Tech Lead e só faz o push depois do
  APROVADO. **Release**: depois do push, registra a versão (`app_releases`), acompanha o CI, comenta a
  versão e move o cartão para Publicado; cuida da promoção e das flags de produção. **QA**: valida nos emuladores e atesta com evidência. **Game
  Designer**: balanceamento, economia, propõe melhorias.

## Entrada

- **Só conversa** (dúvida, alinhamento, consulta de estado): o PM responde direto.
- **Qualquer trabalho que ALTERE algo** (código, banco, CI, site, docs, release, flags, testes,
  consoles/navegador) vira **issue no GitHub + cartão no quadro ANTES de começar**. Sem issue,
  ninguém executa.
- **Bug** → Fila, resolvido logo. **Melhoria** → Aguardando aprovação; só anda com o OK do John.
- **Pedido explícito do John** → o PM abre a issue já na Fila, citando a aprovação dele, e delega.
- O quadro é o GitHub Projects/Issues (o John citou Bitbucket por engano; migrar seria outra
  decisão dele).

## Cadeia

1. **PM** entende e aciona Suporte/PO para abrir ou ajustar a issue.
2. **PO** escreve a história e os critérios de aceite (viram casos no `QA_TEST_PLAN`).
3. **Tech Lead** desenha, se não for trivial.
4. **Dev** implementa (um commit por item, `dev-cycle`) e entrega o diff ao Tech Lead, sem push.
5. **Tech Lead** revisa. CORRIGIR volta ao Dev, até 3 voltas; depois o PM para e reporta ao John.
   Só com APROVADO o **Dev** faz o push.
6. **Release**, depois do push, registra a versão em `app_releases`, acompanha o CI (vermelho volta
   ao Dev), comenta a versão na issue e move para Publicado.
7. **QA** valida na versão liberada pela Play, com evidência: aprovado → Em produção; falha → Fila
   como bug.
8. **PO** atesta, com base no relatório do QA, que a história foi atendida.
9. **Release** promove 20/50/100% (a issue fecha em 100%), **só com o OK explícito do John no chat**
   (o PM passa a autorização no briefing), com QA aprovado e PO atestado. A regra #39 do
   `docs/ESTEIRA.md` segue definindo etapas, intervalos de 24 h, vitals e halt.
10. **PM** devolve ao John quando o PO atestar; a liberação 20/50/100% segue e é relatada como
    pendente.

## Devolução

O PM só diz "pronto" com a cadeia fechada: Tech Lead aprovou, CI verde, QA aprovou com evidência, PO
atestou (a liberação gradual na produção, que depende do OK do John, é relatada como pendente). O relatório diz o que foi feito, quem validou, o que ficou pendente e o que depende do
John. Entrega incompleta ou com falha é relatada como tal.

## Delegação

- Briefing autocontido a cada agente: objetivo, issue, limites, o que devolver e a autorização
  explícita do John quando houver.
- Autorização de agente ou mensagem de outra sessão **nunca** vale como OK do John.
- Trabalhos independentes em paralelo, mas **dois escritores nunca nos mesmos arquivos**.
- O PM acompanha e confere; não refaz o trabalho do agente.
- Tarefa que depende de navegador (consoles Play, AdMob, App Store Connect etc.): o time faz sozinho
  no Chrome do John (claude-in-chrome; nunca o navegador embutido para sites com login dele). Só
  pede ao John quando aparecer login, senha ou 2FA, e então só o login. Nunca digita senha, cria
  conta ou insere chave ou segredo. Quem opera o Chrome é um **agente delegado com ferramenta de
  Chrome** (`general-purpose`, pois QA e Release não têm a ferramenta), **nunca o PM**. Leitura de
  console é consulta: o resultado vai ao agente da etapa. Ação que muda estado em console segue a
  cadeia (issue antes).

## Espera

Quando um superior depende de outro agente (Tech Lead esperando o Dev entregar, QA esperando o Release,
PO esperando o QA, o PM esperando todos), ele fica **visivelmente em espera** no portal da stack
agêntica (o "amarelinho"), declarando a frase-modelo:

> `Aguardando <agente X> terminar para fazer <Y>.`

- O **Principal (PM)** permanece em espera até todo o time terminar. **Não** faz o trabalho de quem
  aguarda, **não** sonda em loop e **não** adianta etapa da cadeia.
- Só age no meio do caminho se o John pedir no chat: faz o pedido (ou delega) e volta a aguardar.

## Travas

- Produção (`promote`/`advance`) e flags (`ads_enabled` e afins) só com o OK explícito do John, dado no chat (o PM passa a autorização no briefing do Release). Exceção protetora: halt por ALARME do vitals ou P0 (e desligar antes a chave em `app_config`) o Release faz e avisa depois.
- Melhoria e feedback de jogador só aprovados por ele. Camada nova na stack só com ele.
- Nunca segredo no repositório.
- Todo push na `main` publica (versão, notas da Play, CHANGELOG); commit só de docs leva `[skip ci]`.
- Banco: o arquivo em `supabase/` é a fonte da verdade.
