# Roteiro de testes completo (SPEC de QA)

Bateria de testes de ponta a ponta do Naval Battle no Android, com o app **instalado pela
Play Store** e duas contas Google reais de teste. É o roteiro usado sempre que for pedida
"uma bateria de testes completa" — rodar inteiro, na ordem, e entregar o relatório no
formato do fim deste documento.

Não substitui teste de unidade: aqui o alvo é o fluxo real que o jogador vê (loja, login,
servidor, push, partida online entre dois aparelhos).

---

## 1. Ambiente

| Item | Valor |
|---|---|
| Aparelho A | emulador **QA01** — conta `aigamesfactory.qa01@gmail.com` |
| Aparelho B | emulador **QA02** — conta `aigamesfactory.qa02@gmail.com` |
| Imagem | Android 36 **Google Play** (`google_apis_playstore`, x86_64) |
| App | `aigamesfactory.navalbattleclassic`, versão da faixa Alpha (teste fechado) |
| Servidor | Supabase de produção (mesmo dos jogadores) |

Regras do ambiente:
- **Contas QA ativas só durante o teste** (decisão do John, 13/10). Fora dos testes elas ficam
  `is_test = true` (fora do ranking, da partida rápida e das recompensas). **Primeiro passo de todo
  teste** (bateria ou issue da esteira):
  `update public.profiles set is_test = false where id in (select id from auth.users where email in ('aigamesfactory.qa01@gmail.com','aigamesfactory.qa02@gmail.com'));`
  **Último passo, sempre** (passou, falhou ou o teste parou no meio):
  `update public.profiles set is_test = true where id in (select id from auth.users where email in ('aigamesfactory.qa01@gmail.com','aigamesfactory.qa02@gmail.com'));`
  Os pontos ganhos no teste ficam no banco, invisíveis enquanto `is_test = true`; não é preciso
  zerar nada. As contas QA nunca são removidas.
- Os dois emuladores ligados juntos: `emulator -avd QA01 -feature -Vulkan` e o mesmo para
  QA02 (Vulkan derruba o emulador). Seriais por ordem de boot — conferir com
  `adb -s <serial> emu avd name`.
- Desligar com `adb emu kill` (salva o estado; matar o processo pode perder o login).
- **Senha nunca é digitada por quem roda o teste.** Login Google do aparelho é feito pelo
  dono das contas; dentro do jogo o login Google só escolhe a conta já presente.
- As contas QA são de teste: não contam como testadores reais da Play e não devem ser
  usadas para inflar ranking — partidas ranqueadas entre QA01 × QA02 só nos casos marcados.
- Tudo que o teste criar no servidor (amizade, salas, feedback) é desfeito na seção 14.
- **Partida rápida pode parear com jogador real** procurando ao mesmo tempo. Para as
  partidas A × B prefira convite de amigo ou sala por código; quando o caso exigir
  partida rápida (ON-01, QO-*), use o modo **Tático** e, se cair com alguém real,
  encerre ainda no posicionamento.
- O turno tem **20s de relógio** (também contra a IA): estourou, o jogo atira sozinho.
  Encadeie habilidade + alvo num comando só e tire o print depois, em vez de analisar
  print entre um toque e outro.
- Sempre print antes de tocar: o menu muda de altura com promoção de patente, dicas e
  faixas, e toque às cegas cai em outra tela.

## 2. Como registrar

Cada caso tem um ID (`AUTH-03`). Para cada um anotar **OK**, **FALHA** (com print e passo
exato) ou **N/A** (com motivo). Print salvo em `scratchpad/qa-<ID>-<A|B>.png`. Falha vira
item no relatório; bug confirmado vira correção pelo fluxo normal de release.

Prioridade: **P0** bloqueia release · **P1** funcionalidade quebrada · **P2** visual/texto.

### Ordem de execução

1. **Primeiro tudo que precisa de dois aparelhos**, com QA01 e QA02 ligados: seções 3–4
   nos dois (instalar e entrar), depois 10 (amigos), 8 (partida rápida), 9 (sala por código),
   11 (partida rápida de qualquer tela), 8b (rede local) e PUSH-01.
2. **Depois desligar o QA02** (`adb -s <serial> emu kill`) e rodar o resto só no QA01:
   5, 6, 7, 12 e 13.
3. Limpeza (14) e relatório (15).

---

## 3. Instalação e primeira abertura (A e B)

| ID | P | Passos | Esperado |
|---|---|---|---|
| INST-01 | P0 | Play Store → página do jogo | Aparece "Naval Battle Classic", ícone e banner atuais, "Você é testador" |
| INST-02 | P0 | Instalar / Atualizar | Instala sem erro; versão igual à última publicada (`dumpsys package … versionName`) |
| INST-03 | P0 | Abrir pela primeira vez | Abertura animada (Splash) e depois boas-vindas, sem travar nem fechar |
| INST-04 | P1 | Permissão de notificação | Pedida ao chegar no menu (Android 13+), uma vez só |
| INST-05 | P1 | Janela de versão nova | Não aparece com a versão mais recente instalada |
| INST-06 | P2 | Girar/voltar no meio da abertura | Nada quebra; voltar no menu fecha o app |
| INST-07 | P1 | Site (`johncoelho.github.io/naval-battle`), seção Baixar e botão do topo | Nenhum link ou instrução de APK; todo caminho de Android leva à página de beta tester / Play Store; seção do iPhone com o `.ipa` e a data da última compilação |

## 4. Conta e login (A e B)

| ID | P | Passos | Esperado |
|---|---|---|---|
| AUTH-01 | P0 | Perfil → Entrar com Google | Lista a conta do aparelho; escolher entra sem pedir senha |
| AUTH-02 | P0 | Depois do login | Nome, avatar e patente no topo do menu; badge "Beta Tester" no Perfil |
| AUTH-03 | P1 | Fechar e reabrir o app | Continua logado (sessão renovada sozinha) |
| AUTH-04 | P1 | Trocar nome de guerra e insígnia no Perfil | Salva, aparece no menu e para o outro aparelho (busca de amigos) |
| AUTH-05 | P1 | Sair da conta e entrar de novo | Volta como convidado ao sair; ao entrar recupera a carreira do servidor |
| AUTH-07 | P1 | Conta só Google (sem senha) → Perfil → Criar senha; nova senha 6+ e confirmação → Salvar | Botão mostra "Criar senha" (não "Trocar senha") e o cartão não pede senha atual; confirma "Senha criada"; o botão vira "Trocar senha"; sair e entrar por e-mail + essa senha cai na mesma carreira (senha digitada só pelo John) |
| AUTH-08 | P1 | Conta criada por e-mail e senha → Perfil | Mostra "Trocar senha" pedindo a senha atual, como antes |

### 4b. Entrar com a Apple (iPhone, #13)

Só no iPhone, pelo **TestFlight** (build assinado com a entitlement; o `.ipa` do Sideloadly dá `AUTH_APPLE_UNAVAILABLE`). O iPhone
não bloqueia o release Android. No Android: botão da Apple ausente, login Google e e-mail iguais, Excluir conta sem reautenticação.

| ID | P | Passos | Esperado |
|---|---|---|---|
| APL-01 | P0 | Sem sessão → Perfil → Entrar com a Apple → COMPARTILHAR MEU E-MAIL | Entra, sem erro 1000; conta nova com o nome da Apple; botão acima do Google e dica abaixo |
| APL-02 | P0 | Tocar no botão e cancelar a janela da Apple | Volta para a tela de entrar, sem erro e sem sessão |
| APL-03 | P1 | Conta criada pela Apple → Perfil | Aparece "Criar senha", não "Trocar senha" |
| APL-04 | P1 | Entrar com a Apple em OCULTAR MEU E-MAIL | Entra em conta nova; na Conta aparece "E-mail oculto pela Apple", e o card de Criar senha mostra o endereço de relay |
| APL-05 | P0 | Conta Google/e-mail com carreira e dobrões → sair → Apple compartilhando o mesmo e-mail | Mesma carreira e dobrões; no banco o `user_id` é o mesmo, com identidade `apple` a mais |
| APL-06 | P1 | Fechar e reabrir o app com a sessão da Apple | Entra direto |
| APL-07 | P0 | Conta Apple → Excluir conta → confirmar → janela da Apple → confirmar | Conta apagada; com os secrets da Edge, `apple-revoke` devolve `revoked:true` e o app some de Ajustes do iPhone > ID Apple > Iniciar sessão com a Apple. Sem os secrets: `reason:"not_configured"` no log e a conta apaga do mesmo jeito |
| APL-08 | P1 | Conta e-mail/senha **não confirmada** com o mesmo e-mail → Apple compartilhando o e-mail | Documentar o resultado (o GoTrue não vincula identidade não verificada); nenhuma conta existente é alterada |
| APL-09 | P0 | Conta Apple → Excluir conta → confirmar → cancelar a janela da Apple | Nada é apagado; aparece `AUTH_DELETE_APPLE_CONFIRM` |
| APL-10 | P1 | Conta Apple de relay que já tem nome → sair e entrar de novo com a Apple | Entra e **não** é renomeada (o nome só vale em carreira nova) |

## 5. Menu e navegação

| ID | P | Passos | Esperado |
|---|---|---|---|
| NAV-01 | P1 | Olhar o menu inteiro | Comandante, cartão do dia, frota animada, Jogar online, vs IA / 2 jogadores / Rede local, atalhos Amigos/Placar/Loja/Feedback — nada sobreposto ou cortado |
| NAV-02 | P1 | Botão voltar em cada tela (Perfil, Ajustes, Loja, Estaleiro, Amigos, Placar, Feedback, Novidades) | Volta para a tela anterior; no menu fecha o app |
| NAV-03 | P1 | Voltar no meio de uma partida | Ignorado (não derruba a batalha) |
| NAV-04 | P2 | Dica de milhas/dobrões | Aparece só na primeira vez |

## 5b. Release minificado (R8) e telas grandes (#37)

R8 só quebra no release: rodar no app da Play (já minificado) nos QA01/QA02, antes de promover para a produção.
Telas grandes: AVD Pixel Tablet e Pixel 9 Pro Fold (API 36), mais o QA01 em retrato; no emulador de celular,
`wm size` (ver docs/BUILD.md).

| ID | P | Passos | Esperado |
|---|---|---|---|
| R8-01 | P0 | App da Play (alpha com R8): abrir, entrar com Google, jogar contra a IA, abrir Loja e Ajustes | Sem crash; logcat sem `FATAL EXCEPTION`, `ClassNotFoundException` nem `NoSuchMethodError` |
| R8-02 | P0 | Loja → Dobrões: lista de pacotes e uma compra de teste (DOB-07) | Produtos carregam e a compra credita (Billing sobrevive ao R8) |
| R8-03 | P1 | Anúncio premiado de teste e consentimento (ADS-01) | Anúncio abre e credita; UMP aparece quando aplicável |
| R8-04 | P1 | Primeiro toque em botão de anúncio no app da Play | A leitura da faixa etária (Age Signals) não derruba o app; o anúncio abre |
| R8-05 | P1 | Push de convite com o app fechado e lembrete diário | Notificação chega e abre a tela certa |
| R8-06 | P2 | Play Console → App bundle explorer → versão → Downloads | Arquivo de desofuscação (mapping) presente |
| BIG-01 | P0 | Pixel Tablet em paisagem: percorrer Menu, Loja, Perfil, Ajustes, Estaleiro, Amigos, Placar, Feedback | Conteúdo centralizado em até 600 dp, fundo até a borda, rolagem onde faltar altura, nada cortado |
| BIG-10 | P0 | Pixel Tablet em paisagem (1280x800 dp): Posicionamento | Tabuleiro menor que a largura, legenda, Aleatório/Voltar e **Confirmar visíveis sem rolar**; dá para confirmar e começar |
| BIG-11 | P1 | Dobrável aberto (~820 dp) e tablet em janela dividida: batalha contra a IA | Alvo com altura limitada; a frota própria (e as habilidades no tático) aparece com pelo menos ~200 dp |
| BIG-02 | P0 | Pixel Tablet em paisagem: partida contra a IA (clássico e tático) | Alvo e frota própria lado a lado (no tático, habilidades à direita); tiro, vez da IA e fim de partida normais |
| BIG-03 | P0 | Dobrável: desdobrar e dobrar no meio de uma partida | A Activity não recria; turno, tiros e relógio continuam; o layout troca entre lado a lado e coluna |
| BIG-04 | P1 | Tablet em retrato e janela dividida | Uma coluna centralizada, sem tabuleiro maior que a tela |
| BIG-05 | P1 | Popups (diário, loja, convite, atualização) em tablet | Painel de até 600 dp centralizado, véu cobrindo a tela inteira |
| BIG-06 | P1 | 2 jogadores no mesmo aparelho em tablet horizontal e em retrato | Horizontal: usa a tela sem girar; retrato: gira como no celular; os dois quadros tocáveis |
| BIG-07 | P1 | Teclado em Nomes e Feedback, no celular (QA01) e no tablet | Igual a antes: o campo fica visível acima do teclado, subindo uma vez só (sem deslocamento dobrado) |
| BIG-08 | P0 | QA01 (celular) em retrato: todas as telas | Igual a antes: conteúdo fora da barra de status, do recorte e dos gestos |
| BIG-09 | P1 | Celular com girar automático ligado | Fica em retrato |
| EDGE-01 | P1 | Android 15+ com barra de gestos e com 3 botões, retrato e paisagem (tablet) | Nenhuma tela com conteúdo sob as barras do sistema ou o recorte |

## 6. Partida contra a IA

| ID | P | Passos | Esperado |
|---|---|---|---|
| IA-01 | P0 | vs IA → Escolha o modo → Clássico | Abre posicionamento; último modo vem marcado da próxima vez |
| IA-02 | P0 | Posicionar (aleatório e manual, girar navio) | Navios não se sobrepõem nem saem da grade; Confirmar libera a batalha |
| IA-03 | P0 | Jogar até o fim | Acertou atira de novo; erro passa a vez; IA persegue contato; fim mostra relatório |
| IA-04 | P1 | Relatório | Vitória/derrota, comparativo, XP, dobrões, condecorações; XP e dobrões somam no menu |
| IA-05 | P1 | vs IA → Tático | Barra de habilidades ao lado da frota com descrição; reconhecimento (avião), barragem dupla, sonar 3×3 (callout com contagem), fumaça (nuvem visível) funcionam e entram em recarga |
| IA-06 | P1 | Cartucho avulso comprado (ver LOJA-03) | Libera um uso mesmo em recarga e some depois |
| IA-07 | P2 | Sons e trilha | Tocam; respeitam os toggles de Ajustes |

### 6a. Aviso de retorno e prêmio de retorno (#42)

Conta QA01 com "Lembrete diário" ligado e notificações permitidas. Ensaio do banco: `supabase/tests/daily_return_ensaio.sql`.

| ID | P | Passos | Esperado |
|---|---|---|---|
| RET-01 | P0 | Ensaio `daily_return_ensaio.sql` | Resultado igual ao esperado no cabeçalho do arquivo (retorno 100 + 3, carência de 30 dias) |
| RET-02 | P0 | QA01: recuar `daily_progress.last_checkin` 5 dias por SQL, abrir o Diário de bordo | Linha "Prêmio de retorno ◆100 + 3 milhas" antes do check-in; após o check-in o saldo e as milhas do servidor sobem |
| RET-03 | P0 | Repetir logo depois (recuar de novo, mesma conta) | Sem prêmio (carência) |
| RET-04 | P1 | Emulador com o relógio adiantado 2 dias sem abrir o app | Às 19h sai o lembrete com o texto "esperando" |
| RET-05 | P1 | Adiantado 5 dias sem abrir; depois mais 2 dias | Um aviso às 12h e nenhum às 19h daquele dia; nenhum aviso de retorno depois |
| RET-06 | P1 | Ajustes → "Lembrete diário" desligado e relógio adiantado | Nenhum aviso |

### 6b. Primeira partida guiada e presente (#43)

Antes de cada rodada, `welcome_gift_reset` da conta QA (administrador) e carreira local zerada.

| ID | P | Passos | Esperado |
|---|---|---|---|
| WEL-01 | P0 | QA01 com reset e 0 partidas locais, abrir o app e chegar ao menu | Aparece "Primeira missão" com Começar e Pular |
| WEL-02 | P0 | Começar e jogar a guiada até o fim | Dicas na ordem: posicionar, tocar numa casa, "Acertou! Quem acerta atira de novo", "Navio afundado"; popup "◆150 dobrões + 5 milhas"; saldo do servidor +150 |
| WEL-03 | P1 | Ajustes → Rever partida guiada, jogar | Dicas aparecem; ao terminar ou pular, nenhum presente |
| WEL-04 | P0 | Reset de novo, Pular no passo 1 | Presente igual ao do WEL-02 |
| WEL-05 | P1 | Convidado faz a guiada e depois entra na conta | Popup pede para entrar; no primeiro login chega o presente |
| WEL-06 | P0 | Conta antiga que não é QA (ensaio `welcome_ensaio.sql`) | `not_eligible`; nada creditado |

### 6c. Convide um amigo (#44)

Ligar `referral_allow_qa = 'true'` só durante o teste e desligar no fim (REF-07). QA02 precisa de conta sem resgate prévio.

| ID | P | Passos | Esperado |
|---|---|---|---|
| REF-01 | P0 | QA01: Amigos → Convidar amigo → Compartilhar | Código de 6 caracteres; o seletor abre com texto, link da Play e código |
| REF-02 | P0 | QA02: Tenho um código de convite → digitar o código do QA01 | Popup "Convite aceito"; os dois aparecem como amigos na hora |
| REF-03 | P0 | QA02 tenta de novo; QA01 digita o próprio código | "Você já usou um código" e "Este é o seu próprio código" |
| REF-04 | P0 | Partida online QA01 x QA02 sem tiro, abandonada | Ninguém recebe |
| REF-05 | P0 | Partida online com combate dos dois lados | QA02 recebe ◆100 no fim; QA01 recebe push "Seu convite rendeu" e o popup ao abrir o menu; saldos do servidor batem |
| REF-06 | P1 | Ensaio `referral_ensaio.sql` | Limites diário (1) e total (10), conta antiga, `is_test`, QA, sem carteira e teto online conferem |
| REF-07 | P0 | Fim do teste | `referral_allow_qa = 'false'` |

### 6d. Desafio da semana (#47)

Em contas de QA o prêmio paga (conta real, não `is_test`). O ensaio WEK-01 vem antes da migração.

| ID | P | Passos | Esperado |
|---|---|---|---|
| WEK-01 | P0 | Ensaio `weekly_ensaio.sql` no SQL Editor | Resultado igual ao cabeçalho do arquivo e nada gravado depois |
| WEK-02 | P0 | Abrir o Diário de bordo (QA01) | Bloco "Desafio da semana" com a missão, barra, prêmio "◆250 + 5 milhas", Resgatar desligado e "Renova na segunda-feira, em ..." |
| WEK-03 | P0 | Missão `ONLINE_PLAY_5`: 5 partidas online QA01 x QA02 com tiros dos dois lados | Barra sobe a cada partida até 5/5; Resgatar liga; ao resgatar, saldo do servidor +250 e +5 milhas |
| WEK-04 | P0 | Tocar em Resgatar de novo (ou abrir o diário em outro aparelho) | Mostra "Prêmio da semana resgatado", nada novo creditado |
| WEK-05 | P1 | Partida abandonada sem tiro | A barra não se move |
| WEK-07 | P1 | Missão `RANKED_WIN_3`: ranqueada vencida sem tiros, e outra com resultado em conflito | Nenhuma move a barra; só ranqueada vencida com combate dos dois lados e sem conflito conta |
| WEK-06 | P1 | Resgatar com 500 dobrões já pagos hoje por partidas online | Paga os ◆250 do mesmo jeito (não entra no teto) |

## 7. Dois jogadores no mesmo aparelho (só A)

| ID | P | Passos | Esperado |
|---|---|---|---|
| LOC-01 | P1 | Nomes → posicionar cada um com tela de passagem | Frota de um não aparece para o outro |
| LOC-02 | P1 | Batalha na horizontal | Só o quadro da vez acende; Inverter troca os lados; fim mostra "Vitória de Fulano" |

## 8. Online — partida rápida (A × B)

| ID | P | Passos | Esperado |
|---|---|---|---|
| ON-01 | P0 | A e B: Jogar online → Partida rápida, Casual, mesmo modo | Radar de espera com Cancelar; os dois se encontram em até ~10s; popup "Adversário encontrado" com selo Casual e modo |
| ON-02 | P0 | Posicionar e jogar a partida inteira | Jogadas chegam no outro aparelho em 1–2s; vez troca certo; alarme ao ser atingido |
| ON-03 | P0 | Fim da partida | Relatório dos dois lados coerente (vencedor de um = derrota do outro), "não conta para o ranking", milha descontada |
| ON-04 | P1 | Modos diferentes (A Clássico, B Tático) | Não pareiam entre si |
| ON-05 | P1 | Cancelar na espera | Sala some; o outro não é pareado com ela |
| ON-06 | P1 | Durante a partida, A vai para segundo plano (Home) | B vê pausa com contagem de 60s; A volta antes → partida segue |
| ON-07 | P1 | A fica fora mais de 60s | B vence por desistência; A vê derrota ao voltar |
| ON-08 | P1 | Cortar rede de A (`adb emu network speed`/modo avião) por 15s, no meio do turno de A | A: "Sem conexão — reconectando" e o relógio do turno para em até ~6s (a jogada não sai sozinha); B: "Adversário sem conexão" com contagem; ao voltar, jogadas guardadas chegam e o relógio retoma (#8) |
| ON-09 | P1 | Provocações (ícone de rádio) | Bolha aparece no outro aparelho |
| ON-10 | P1 | Ranqueada QA01 × QA02 (uma vez por bateria, com as contas ativas) | Exige passe da temporada; a partida conta normalmente: os pontos mudam (soma zero entre as duas) e as duas aparecem no Placar durante o teste |
| ON-11 | P2 | Fórmula do Elo de `record_ranked_result` | Coberta pelo bloco que desfaz no banco (dois usuários fictícios fora de teste), não pelos emuladores: com QA como conta de teste a ranqueada deles não grava pontos (#23) |
| ON-12 | P2 | Cabeçalho da batalha nos dois aparelhos | Cada um vê "VS. <nome do outro>" — inclusive quem entrou na sala |
| ON-13 | P1 | Revanche online | Recomeça; milha e pontos (na ranqueada) contam de novo |
| ON-14 | P1 | B encerra a partida ainda no posicionamento | A vai para o relatório ("O adversário desistiu"), não fica preso no posicionamento |
| ON-15 | P1 | Casual e ranqueada: B sai no posicionamento (ou antes do primeiro tiro de A) | A vê "Vitória sem combate"; XP, dobrões e milhas de A não mudam; desafio do dia não avança; na ranqueada os pontos seguem a regra de abandono; nada muda para B (#6) |
| ON-16 | P1 | A dá pelo menos 1 tiro (ou usa 1 habilidade) e B desiste depois | XP, dobrões, milha e desafio do dia pagos como antes (#6) |
| ON-17 | P1 | Depois de abrir o app logado na versão nova, conferir no banco `device_checks` de A e B (e `profiles.is_test`) | Uma linha por conta e versão; com `allowlist_enforced = 'true'`, QA01/QA02 têm `is_test = true` (conta de teste, `source = 'qa'`), ficam fora do Placar e continuam se encontrando na partida rápida (teste com teste) (#23) |
| ON-18 | P2 | Depois da revisão do Google de cada versão, conferir (só leitura) as contas criadas na janela do upload | Robôs (e-mail `nome.00000@gmail.com` ou `firebase.test.lab`) com `is_test = true`, fora do Placar. Contas comuns, testadores da lista e contas de iPhone com `is_test = false` (0.34.5, #35: quem não é robô vale). Conta comum marcada como teste por engano: conferir o e-mail de login em `auth.users` e a regra `is_robot_account` (#35) |
| ON-19 | P1 | Com `allowlist_enforced = 'true'`, conta nova da loja (e-mail comum, sem robô) | Nasce com `is_test = false`, aparece no Placar e na partida rápida, e ganha pontos normalmente (#35) |

### 8a. Treino contra a IA enquanto procura (#41)

| ID | P | Passos | Esperado |
|---|---|---|---|
| PRT-01 | P1 | QA01 sozinho na partida rápida | O botão "Treinar contra a IA enquanto procuramos" aparece aos ~20 s, não antes; sala de amigo não mostra |
| PRT-02 | P1 | Treinar | Faixa "Treino contra a IA · procurando adversário" fixa em posicionamento, batalha e resultado; sem nome nem avatar de jogador |
| PRT-03 | P1 | QA02 entra na rápida durante o treino | QA01 vê "Adversário encontrado" e cai no posicionamento online em ~3 s; 1 milha gasta só aí; partida online normal; treino sem XP nem estatística |
| PRT-04 | P1 | "Parar de procurar" | QA02 não acha mais a sala; o treino termina com XP e zero dobrões |
| PRT-05 | P2 | Terminar o treino antes de alguém entrar | Resultado contra a IA (XP, desafio do dia, zero dobrões); a busca segue |
| PRT-06 | P2 | Ranqueada: mesmo fluxo | Resultado ranqueado só da online; sair do treino para o menu fecha a sala |

### 8c. Aviso de atualização (#46)

| ID | P | Passos | Esperado |
|---|---|---|---|
| UPD-10 | P1 | App aberto em segundo plano; sai versão nova (`app_releases` e Play); voltar | Popup aparece sem reabrir o app (consulta no máximo a cada 5 min) |
| UPD-11 | P1 | "Depois", sair e voltar | Não reaparece para a mesma versão; com outra versão maior reaparece |
| UPD-12 | P2 | Voltar durante uma partida | Só aparece ao voltar ao menu |

## 8b. Rede local (A × B)

Dois emuladores ficam cada um atrás do próprio NAT (10.0.2.x) e não se enxergam por
mDNS. Rodar estes casos em dois aparelhos reais no mesmo Wi-Fi; nos emuladores, marcar
**N/A (limitação do emulador)** e testar só LAN-01 (a tela abre e procura sem travar).

| ID | P | Passos | Esperado |
|---|---|---|---|
| LAN-01 | P1 | A: Rede local → criar partida com nome → Cancelar → criar de novo com outro nome | Fica anunciando com botão Cancelar; Cancelar volta ao início (campo e Criar partida liberados); o outro aparelho deixa de ver o anúncio antigo (#10) |
| LAN-02 | P1 | B: Rede local → busca acha o nome de A → entrar | Cada um posiciona no seu aparelho; partida no modo de A |
| LAN-03 | P1 | Fim → os dois tocam Revanche | Recomeça na mesma ligação |

## 9. Online — sala de amigo por código (A × B)

| ID | P | Passos | Esperado |
|---|---|---|---|
| ROOM-01 | P1 | A cria sala → código | Código curto visível e compartilhável |
| ROOM-02 | P1 | B entra com o código | Entra no modo da sala de A; partida começa |

## 10. Amigos, presença e convites (A × B)

| ID | P | Passos | Esperado |
|---|---|---|---|
| FR-01 | P0 | A: Amigos → buscar nome de B → Buscar | Mostra quantos achou; B aparece com patente |
| FR-02 | P0 | A envia pedido | B recebe push (app fechado) e selo em Amigos (app aberto); A vê em Pedidos enviados com Cancelar |
| FR-02b | P1 | B com o jogo aberto (ou minimizado) quando o pedido chega | Selo de Amigos no deque acende sem reabrir o app |
| FR-03 | P0 | B aceita | Os dois aparecem em Seus amigos; A recebe push "pedido aceito" |
| FR-03b | P1 | A com a tela de Amigos aberta enquanto B aceita | Em até ~20s o "Pedido enviado" vira amigo, sem sair da tela |
| FR-03c | P1 | Depois do aceite (ou com B já amigo), A busca B de novo ou fica com a busca aberta | Resultado mostra "Amigo" em verde, nunca "Pedido enviado"; se B pediu para A, o resultado mostra Pedido recebido; depois de recusar, nada de botão Aceitar |
| FR-03d | P1 | B pede amizade a A; A busca B | O resultado mostra **Aceitar**; um toque vira "Amigo" (duplo toque não manda dois aceites); recusado ou já aceito não mostra Aceitar (#20) |
| FR-04 | P1 | Presença | Com os dois com jogo aberto: bolinha verde e "Jogar"; B em partida: "Em partida"; B fechado há mais de 2 min: "há X min" |
| FR-05 | P0 | A → Jogar em B → Casual | B vê popup "A te convidou · Casual · <modo>" (push se app fechado); aceitar inicia a partida |
| FR-06 | P1 | Convite recusado | A vê a recusa; nada fica preso na tela |
| FR-07 | P1 | B desliga "Receber convites de amigos" em Ajustes | A não consegue convidar B (sem Jogar); religar volta ao normal |
| FR-08 | P1 | Convite Ranqueada (beta, `friend_ranked_enabled`) | Chip Ranqueada disponível; sem passe pede adesão |
| FR-09 | P2 | Tocar no cartão do amigo | Folha de serviço com Convidar e Remover (com confirmação) |
| FR-10 | P1 | A e B amigos, B com o jogo fechado e a opção "Avisar quando um amigo ficar online" ligada; A abre o jogo logado | B recebe "A está online · Chame para uma batalha." em até 1 min (#19) |
| FR-11 | P1 | B toca no aviso da FR-10 | O jogo abre na tela de Amigos com A online e o Jogar disponível (#19) |
| FR-12 | P1 | A fecha e reabre o jogo três vezes em 1 h | B recebe só 1 aviso (anti-spam de 3 h por amigo) (#19) |
| FR-13 | P1 | B desliga a opção (ou "Receber convites"), ou está no meio de uma partida; A fica online | B não recebe nada (#19) |

## 10b. Segurança entre jogadores: denunciar, bloquear e nome (#58, QA01 × QA02)

O ensaio `supabase/tests/safety_ensaio.sql` roda antes da migração. Casos com `is_test = false` nas duas contas só durante o teste (volta para `true` no fim).

| ID | P | Passos | Esperado |
|---|---|---|---|
| SAF-01 | P0 | Perfil: renomear para um termo ofensivo (com acento e leet) | Recusado com a mensagem de nome não permitido; o nome antigo fica; nada gravado em `profiles` |
| SAF-02 | P0 | Gravar por SQL um nome ofensivo em `profiles` da conta de teste e reabrir o app | O nome vira `Comandante-xxxxxx`, o aviso "Seu nome foi trocado" aparece uma vez e não volta |
| SAF-03 | P0 | QA01 toca numa linha do ranking e denuncia (Nome ofensivo) | Linha em `player_reports` (`context = ranking`); repetir o mesmo motivo no dia mostra "já denunciou" |
| SAF-04 | P0 | Denunciar pela busca de amigos | Grava com `context = search` |
| SAF-05 | P0 | Terminar partida online e denunciar pelo Resultado | Grava com `context = result` e `match_id` da sala |
| SAF-06 | P1 | Com `reports_daily_cap = 1`, denunciar duas vezes por motivos diferentes | A segunda mostra o limite diário (voltar o valor para 10) |
| SAF-07 | P0 | QA01 bloqueia QA02, os dois procurando partida rápida; QA02 busca QA01; QA01 convida QA02; QA02 pede amizade | Não casam; a busca não acha; convite e pedido falham; a amizade, se havia, some |
| SAF-08 | P1 | QA01 desbloqueia QA02 (Ajustes → Jogadores bloqueados) | Tudo volta a funcionar |
| SAF-09 | P1 | Duas denúncias seguidas por contas diferentes dentro de 1 h | O John recebe só um push "Nova denúncia no Naval Battle" |
| SAF-10 | P1 | Ajustes: Política de privacidade, Regras da comunidade e Fale conosco (Android e iPhone) | Abrem o site na língua do jogo; "Fale conosco" abre a política (Contato), sem app de e-mail |
| SAF-11 | P1 | Cliente alterado manda provocação com texto livre | Nada aparece na partida |
| SAF-12 | P0 | App antigo (0.41.0) contra o novo, com bloqueio feito | O bloqueio vale (a partida rápida não casa os dois) |
| SAF-13 | P2 | `select * from public.purge_moderation();` com uma denúncia de 13 meses atrás | A antiga some, as recentes ficam |
| SAF-14 | P1 | Terceiro denunciante distinto contra o mesmo nome em 7 dias | O nome vira `Comandante-xxxxxx` e a vítima vê o aviso uma vez |

## 11. Partida rápida de qualquer tela (A × B)

| ID | P | Passos | Esperado |
|---|---|---|---|
| QO-01 | P1 | B: Ajustes → "Disponível para partida rápida" ligado; A procura partida rápida | B, em qualquer tela fora de partida, vê balão Aceitar / Agora não |
| QO-02 | P1 | B ignora 50s ou toca "Agora não" | Balão some e não volta para aquela sala |
| QO-03 | P1 | B aceita | Entra direto na partida de A |

## 12. Economia, loja e personalização

| ID | P | Passos | Esperado |
|---|---|---|---|
| ECO-01 | P1 | Diário de bordo | Balão abre com prêmio; check-in +25 só uma vez por dia; desafio do dia conta e resgata +50 |
| ECO-02 | P1 | Milhas | 10/dia; cada partida online gasta 1; aviso em vermelho com 2 ou menos |
| LOJA-01 | P1 | Loja → abas cascos, camuflagens, habilidades, dobrões | Itens com preço; sem saldo: apagado, cadeado e "faltam ◆ X" |
| LOJA-02 | P1 | Comprar uma camuflagem | Cartão de confirmação com saldo antes/depois; item vai para o Estaleiro |
| LOJA-03 | P1 | Comprar cartucho de habilidade | Contador aumenta; usado em IA-06 |
| LOJA-04 | P1 | Aba Dobrões, conta de testador de licença, `beta_store_enabled = 'false'` (ver DOB-07) | Aparece só o Prêmio Beta; a vitrine paga não aparece |
| DOB-30 | P0 | Servidor (bloco que desfaz, `supabase/tests/dobroes_saldo_ensaio.sql`, 0.34.9, #34): abertura da carteira com servidor maior, aparelho maior, aparelho acima de 200.000, chamada repetida, compra pendente e compra que o aparelho já somou | Servidor maior vale; aparelho maior vale; acima do teto limita em 200.000; repetir não soma nem reabre; pendente entra uma vez; a que o aparelho já somou só ganha a entrega (sem soma dupla); `seeds=15/4/4` |
| DOB-31 | P0 | Servidor: conta com carteira e o cliente grava `credits`, `owned`, `fleets` (update e upsert, role `authenticated`) | `credits` e itens ficam como estavam (200, sem erro); `xp` e o resto da carreira são aceitos |
| DOB-32 | P0 | Servidor: conta SEM carteira (app antigo) | Cliente grava `credits` e é aceito; `award_online_match` devolve `no_wallet`; anúncio de vitória contra a IA segue igual |
| DOB-33 | P0 | Loja pelo servidor (`dobroes_buy`): pintura da oferta do dia, pintura repetida, cartucho 2× e item inexistente | Oferta custa 70% do preço; repetida devolve `owned` sem cobrar; cartucho cobra a cada compra; pintura de temporada/item incluso/inexistente = `not_found` |
| DOB-34 | P0 | Sem saldo: casco de 1.800, passe da temporada e pacote de milhas | Casco devolve `insufficient` sem mudar nada; passe e milhas falham com `dobroes_insufficient`, sem passe criado, sem milha somada e saldo igual |
| DOB-35 | P0 | Passe de Temporada com saldo (carteira aberta) | Debita 1.200, credita 500 de bônus, põe a pintura da estação nos itens e soma as 20 milhas |
| DOB-36 | P1 | Pacote de milhas com carteira | Debita `miles_pack_price` (100) uma vez |
| DOB-37 | P0 | Check-in e desafio do dia, duas chamadas cada | Um crédito por dia (+25 e +50), uma linha no razão |
| DOB-38 | P0 | Compra real (`apply_dobroes_purchase`) com carteira, duas vezes o mesmo token | Credita uma vez; a 2ª devolve `already` sem mexer; `dobroes_pending` mostra para o aviso "+N" |
| DOB-39 | P0 | Anúncio premiado (`apply_ad_reward`) com carteira, mesmo `transaction_id` duas vezes | Credita uma vez; a 2ª devolve `already` |
| DOB-40 | P0 | `award_online_match`: sem combate do adversário, vitória casual, repetida, ranqueada sem resultado e com derrota registrada, teto diário zerado e parcial, 4ª contra o mesmo adversário | `not_eligible`; `ok/172`; `already/172`; `no_result`; `ok/53` (o `winner_id` do servidor manda, não o relato); `capped/0`; `ok/100` parcial; `limit`. Teto diário real = 500 (`dobroes_online_daily_cap`) |
| DOB-41 | P0 | Anúncio de dobrar vitória com carteira: referência `ai:` e `online:` | `ai:` = `not_eligible` (a IA não paga); `online:` = valor do razão da sala (172, cortado em 150 pelo teto), `p_amount` ignorado |
| DOB-42 | P0 | `dobroes_open_all()` no bloco que desfaz (todas as contas) | Zero contas sem carteira depois; soma antes = soma depois (créditos + pendentes); só `service_role` executa |
| DOB-43 | P0 | Invariante e privilégios | Em toda carteira soma do razão = `credits`; `authenticated` não executa `dobroes_apply`, `dobroes_open_all` nem `dobroes_open_core` |
| DOB-44 | P0 | App 0.34.9, QA01 com saldo conhecido (anotar), primeira abertura com internet | O saldo mostrado é o mesmo (ou o maior entre servidor e aparelho); a loja funciona; conferir no banco `dobroes_wallets` (source `app`) e a linha `opening` do razão |
| DOB-45 | P0 | App 0.34.9: comprar uma pintura, um casco, um cartucho, aderir ao passe e comprar milhas, cada um uma vez | Saldo cai pelo preço (oferta do dia com 30% off); item vai para o Estaleiro; reabrir o app mostra o mesmo saldo; no banco, uma linha por compra no razão |
| DOB-46 | P0 | App 0.34.9 em modo avião: tocar em comprar na loja, passe e milhas | Aviso "Sem conexão: esta compra precisa da internet."; nada muda (nem saldo, nem item) |
| DOB-47 | P0 | Dois aparelhos (QA01 no 0.34.9 e outro no app antigo, se houver): ganho de IA e de partida online no antigo | Nada disso vale (o servidor ignora o saldo gravado); diário, desafio, compra e anúncio valem; o aparelho antigo volta ao saldo do servidor ao reabrir |
| DOB-48 | P0 | Partida contra a IA (vitória e derrota) no 0.34.9 | Resultado com XP e a nota de treino, sem linha de dobrões e sem o botão de dobrar; saldo igual |
| DOB-49 | P0 | Partida online QA01 × QA02 (casual e ranqueada, com tiros dos dois lados) | A linha de dobrões mostra "conferindo dobrões…" e depois "+N" igual ao do servidor; botão de dobrar aparece só na vitória com dobrões; saldo sobe N; sem combate: sem dobrões |
| DOB-50 | P1 | Partida online sem internet no fim (matar o sinal antes do resultado) e reabrir o app com internet | Aviso de que os dobrões serão conferidos depois; na próxima abertura o servidor paga uma vez (e não duplica se abrir de novo) |
| DOB-51 | P1 | Convidado com 500 dobrões que comprou uma pintura, depois cria conta ou entra pela 1ª vez | A pintura e o saldo (o maior com o do servidor) vão para a conta uma vez; sair da conta zera o saldo local; entrar em outra conta não leva dobrões |
| DOB-52 | P0 | Servidor: `dobroes_buy('charge', '2X', '<id>')` com o mesmo id duas vezes (resposta perdida) | Cobra uma vez (uma linha `store_charge` no razão); a repetição devolve `ok` com o saldo atual, sem cobrar; sem id, cada chamada cobra |
| DOB-53 | P0 | Servidor: `claim_feedback` de feedback aprovado (150 ou 300) sem e com carteira, e o 2º resgate | Sem carteira: devolve a linha e o saldo fica igual (o aparelho soma); com carteira: +N uma vez, uma linha `feedback` no razão; o 2º resgate não devolve linha |
| DOB-54 | P1 | App: tocar duas vezes seguidas em comprar (e confirmar) na loja | O segundo toque é ignorado, sem aviso de "sem conexão"; o botão Comprar fica desabilitado enquanto a compra corre |
| DOB-55 | P1 | App: partida online além de 3 contra o mesmo adversário, e depois do teto diário (500) | No `limit`: aviso "já foram 3 partidas recompensadas…" sem "+0"; no teto: aviso de limite diário de dobrões em pt/en/es |
| LOJA-06 | P1 | Aba Dobrões, testador, `beta_store_enabled = 'true'` (ver DOB-16) | Prêmio e, abaixo, "Pacotes pagos" com aviso de cobrança |
| LOJA-05 | P2 | Oferta do dia | Selo "-30%" no atalho; camuflagem no topo com desconto |
| EST-01 | P1 | Estaleiro: equipar casco e camuflagem | Frota do menu e da batalha mudam; prévia desliza pelas 5 classes |
| SEA-01 | P1 | Passe de temporada (gratuito) | Popup da estação pede aceite; aderir libera ranqueada |
| RANK-01 | P1 | Placar | Abas geral, temporada e Troféus; posição própria mesmo fora do topo |

### 12b. Compras de dobrões pela Google Play (#15)

Pré-requisitos (passos do John, no Play Console e no Supabase): os quatro produtos
`dobroes_bolsa`, `dobroes_bau`, `dobroes_cofre`, `dobroes_tesouro` ativos; QA01/QA02 e a lista
de testadores como testadores de licença; secret `PLAY_BILLING_SERVICE_ACCOUNT` na Edge
`dobroes-verify`. Sem eles, só DOB-10 vale. Para ligar a venda ou o Prêmio: os blocos SQL do
PR/commit de #15 (um por bloco). Compra real de teste nunca é cobrada: a Play marca como teste.
Desde 0.34.5 (#35), "testador" nos casos abaixo é conta com brinde: QA01, QA02 ou a conta do John
(`beta_eligible`); testador antigo da lista da Play ou `manual` se comporta como conta comum.

| ID | P | Passos | Esperado |
|---|---|---|---|
| DOB-01 | P0 | Venda real ligada (`real_billing_enabled = 'true'`), conta comum, Dobrões → toca num pacote | Quatro cards com o preço que a Play devolve (R$ 4,90 · 9,90 · 24,90 · 49,90); toque abre a folha da Play |
| DOB-02 | P0 | Concluir a compra de teste na folha da Play (conta de licença) | Saldo sobe exatamente a quantidade do pacote, uma vez; nenhuma cobrança |
| DOB-03 | P0 | Reabrir o app depois da compra (ou repetir a conferência) | Saldo não sobe de novo (um token, um crédito) |
| DOB-04 | P0 | Cancelar na folha da Play | Saldo não muda; nenhum erro aparece |
| DOB-05 | P1 | Fechar o app logo depois do pagamento, antes da conferência; reabrir | A compra é conferida e creditada sem o jogador tocar em nada |
| DOB-06 | P1 | Conta fora da lista de testadores com compra de teste | Nada é creditado (`not_beta`) |
| DOB-07 | P0 | Conta de testador (lista da Play), `premio_beta_enabled = 'true'`, aba Dobrões, toca "Pegar o Prêmio" | Abre sem a folha da Play; credita 6.500 uma vez ("6.500 dobrões entraram no seu cofre") |
| DOB-08 | P1 | Mesmo testador, tocar de novo no mesmo dia (ou repetir a RPC) | Aparece "Você já pegou o Prêmio de hoje" com o contador; não credita |
| DOB-09 | P1 | `premio_beta_enabled = 'false'`, conta de testador | Sem cartão do Prêmio; vitrine só se `beta_store_enabled` ligado; nada é cobrado |
| DOB-10 | P0 | Produto ausente na Play (ou Play fora) | Aba mostra "Dobrões indisponíveis agora"; nenhum cartão e nenhuma cobrança |
| DOB-11 | P2 | iPhone, aba Dobrões (0.33.4). (a) Conta de testador: pegar o Prêmio Beta; (b) conta de não-testador | (a) só o cartão do Prêmio, nenhum pacote; o saldo sobe 6.500 uma vez ao voltar ao app; repetir não soma. (b) "A compra de dobrões chega em breve.", sem pacote. Nada de loja simulada |
| DOB-12 | P0 | Comprar um pacote da vitrine (conta de licença), com a Play em "Processing", fechar o app (`am force-stop`) e desligar Wi-Fi e dados; depois religar e reabrir | Crédito cai uma vez ao reabrir; reabrir de novo não soma |
| DOB-13 | P0 | Servidor (bloco que desfaz): `apply_dobroes_purchase` duas vezes com o mesmo token | 1ª credita; 2ª `already` 0; token novo no mesmo dia `premio_done` 0 |
| DOB-14 | P0 | Servidor: compra de teste de conta fora de `allowed_players`; compra real com `real_billing_enabled = false` | `not_beta` 0; `billing_off` sem gravar |
| DOB-15 | P0 | Servidor: estado pendente (2), cancelado ou nulo; produto desconhecido; conta que não confere; promoção; teste de outro pacote sem origem (legado) | Nenhum credita (`not_purchased`, `unknown_product`, `account_mismatch`, `promo_refused`, `premio_only`) |
| DOB-16 | P0 | Testador com `beta_store_enabled = 'true'`: pacote da vitrine em licença de teste (origem `vitrine`) | Credita o pacote uma vez (`credited`); nenhuma cobrança |
| DOB-17 | P0 | Testador com `beta_store_enabled = 'false'`: pacote da vitrine em licença de teste | `billing_off`, nada gravado; Play devolve em 3 dias |
| DOB-18 | P0 | Conta `manual` em `allowed_players`, fora da licença da Play, com `beta_store_enabled = 'true'`: compra real (John, cobrança de verdade). Depois, com `beta_store_enabled = 'false'` e `real_billing_enabled = 'false'`: compra de novo | Credita o pacote 1×. Com as duas flags `'false'`: `billing_off`, nada gravado |
| DOB-19 | P0 | Servidor (bloco que desfaz): `claim_premio_beta()` com `premio_beta_enabled` `'false'` e `'true'`, duas vezes no mesmo dia | `off`; depois `premio` e `already`; `dobroes_pending` mostra 6.500 premio uma vez |
| DOB-20 | P1 | Servidor (bloco que desfaz): `dobroes_gate_licenca()` | Lista só testadores `play`/`manual` sem `device_checks` 0.33.3 ou mais novo; `app_version_at_least('0.33.10','0.33.3')` = true, `('0.33.2','0.33.3')` = false |
| DOB-24 | P0 | Servidor (bloco que desfaz, `supabase/tests/brindes_ensaio.sql`, 0.34.5, #35): `beta_eligible` e `claim_premio_beta` para QA, a conta do John, testador antigo (`play`/`manual`) e conta nova | QA 2/2 e John 1/1 elegíveis; testadores antigos 0 elegíveis; conta nova não; testador antigo recebe `not_beta` e a aba sem Prêmio nem vitrine; medalha Beta Tester e Prêmios já pagos iguais |
| DOB-21 | P1 | App 0.33.2 ou antes (sem `origem`), com a Edge atual: compra de teste de pacote | Compra de teste de pacote cai no legado (1× por dia pelo Tesouro); nada é cobrado; pacote de outro produto vira `premio_only` |
| DOB-22 | P0 | Licença QA01 (conta de teste da lista "Licenca QA"), app 0.33.3 ou mais novo, `beta_store_enabled = 'true'`: comprar cada um dos 4 pacotes da vitrine (origem `vitrine`), 1 vez cada | Cada pacote credita uma vez (`credited`): o saldo sobe a quantidade do pacote; a Play consome a compra (não fica pendente); nenhuma cobrança |
| DOB-23 | P0 | Reabrir a aba Dobrões com a compra presa da Bolsa de 18/10 (QA01, `dobroes_bolsa`, não consumida na Play). Depois, reabrir de novo | Credita 500 (`kind = test`) uma vez ao reabrir; reabrir de novo não soma. Se não entrar, conferir a Play antes de 3 dias (a Play devolve sozinha depois disso) |

### 12d. Anúncios premiados opcionais (#29)

Pré-requisitos: `ads_enabled = 'true'` só durante o teste (volta a `'false'` no fim, sempre);
URL da Edge `ads-reward` no SSV dos 4 blocos do AdMob; QA01/QA02 em `allowed_players` com
`source = 'qa'` (anúncio de teste, rótulo "Test Ad"). **Nunca tocar em anúncio real** (clique
inválido suspende a conta AdMob). Servidor: `supabase/tests/ads_ensaio.sql` (bloco que desfaz).

| ID | P | Passos | Esperado |
|---|---|---|---|
| ADS-01 | P0 | `ads_enabled = 'false'`: vencer, abrir o Diário, ficar sem milha | Nenhum botão de anúncio aparece |
| ADS-02 | P0 | Ligado, vitória contra a IA que rendeu 130: tocar "DOBRAR DOBRÕES" e assistir até o fim | +130 uma vez ("130 dobrões entraram no seu cofre"); o botão some daquela tela |
| ADS-03 | P0 | Vitória que rendeu 205: dobrar | +150 (`ads_win_double_cap`) |
| ADS-04 | P0 | Duas vitórias já dobradas hoje; vencer a terceira | Sem botão de dobrar |
| ADS-05 | P0 | Derrota, 2 jogadores, rede local, vitória sem combate, vitória por ausência do outro, online acima de 3 por adversário | Sem botão de anúncio |
| ADS-06 | P0 | Check-in feito: "DOBRAR CHECK-IN" e assistir; repetir no 7º dia | +25; no 7º dia continua +25 (o bônus de 300 não dobra) |
| ADS-07 | P0 | Desafio resgatado: dobrar | +50; o botão não volta no mesmo dia |
| ADS-08 | P0 | Saldo de milhas 0: "GANHAR 1 MILHA" e assistir | Saldo 1 (do servidor); entra numa partida online |
| ADS-09 | P1 | Duas milhas por anúncio hoje e saldo 0 de novo; abrir o aviso | "Limite de anúncios de hoje atingido."; sem botão |
| ADS-10 | P0 | Qualquer botão: fechar o anúncio antes do fim | "Anúncio fechado antes do fim: sem prêmio desta vez."; nada credita; o limite não diminui |
| ADS-11 | P1 | Sem rede ou sem anúncio disponível: tocar o botão | "Nenhum anúncio disponível agora…", sem popup de erro; "Continuar" funciona |
| ADS-12 | P0 | Servidor: `apply_ad_reward` sem assinatura (Edge), com vale de outra conta, vencido, usado ou inventado | Nada credita (`403`, `user_mismatch`, `expired`, `used`, `unknown_vale`); log da Edge só com etapa e motivo |
| ADS-13 | P0 | Servidor: o mesmo `transaction_id` duas vezes | Crédito uma vez (`already`); `dobroes_pending` com um token `ad:` |
| ADS-14 | P1 | Máximo do dia (6 anúncios ou 375 dobrões) | Nenhum botão; à meia-noite de Brasília os limites voltam |
| ADS-15 | P0 | QA01/QA02 ou o build `.preview`: abrir qualquer anúncio | Anúncio de teste do Google ("Test Ad") |
| ADS-16 | P2 | Região que exige consentimento (VPN UE): primeiro anúncio; depois Ajustes → "Privacidade dos anúncios" | Formulário do UMP antes do anúncio; o botão dos Ajustes o reabre (no Brasil: "Sem ajuste de privacidade…") |
| ADS-17 | P1 | Anúncio assistido com a confirmação atrasada (> 20 s) | "O prêmio entra assim que o Google confirmar."; o crédito aparece na próxima abertura, sem somar duas vezes |
| ADS-18 | P1 | iPhone: vencer, ficar sem milhas | Sem botão de anúncio |
| ADS-19 | P0 | Menor (build `.preview` com `-PageFake=teen` ou `child`): vencer e tocar no botão de anúncio | UMP sem formulário; anúncio de teste carrega; `npa=1` na requisição (o tratamento TEEN/CHILD é conferido no ADS-26); o prêmio credita normalmente |
| ADS-20 | P1 | Conta adulta (`-PageFake=adult`) e sem sinal (`-PageFake=unknown`, ou app da Play sem compartilhar) | Adulta: anúncio sem `npa` nem marca de menor. Sem sinal: igual a menor (não personalizado). Nenhum texto novo no jogo; o aviso da Play, se vier, só depois do toque no anúncio |
| ADS-21 | P1 | Servidor: `ads_min_app_version = '0.34.7'` e conta com último aparelho `0.34.6` (ensaio `ads_ensaio.sql`) | `ads_status` desligado e `ad_reward_start` devolve `off`; com `0.34.7` libera; chave vazia = sem gate. Ligar só com o OK do John |
| ADS-26 | P0 | Tratamento de idade do AdMob (#53): um build `.preview` por faixa (`-PageFake=child`, `teen`, `adult`, `unknown`) em QA01 e QA02; vencer e tocar no botão de anúncio | Em cada faixa o anúncio de teste carrega, fecha com prêmio e o crédito entra. Mapeamento esperado: adulto UNSPECIFIED, `teen` e `unknown` TEEN, `child` CHILD (provado no teste de unidade `cadaFaixaTemOTratamentoDoAdMob`); sem log da faixa (a idade não vai para log). Adulto depois de menor na mesma sessão não se reproduz com `-PageFake` (fixo no build); garantido pelo `Builder` novo com UNSPECIFIED explícito a cada pedido |
| REV-01 | P1 | Build debug `-PreviewFake=true` (dados limpos), carreira com 2 vitórias: vencer a 3ª partida contra a IA e voltar ao menu (#45) | O fluxo falso de avaliação roda uma vez ao voltar ao menu; nada aparece dentro da partida nem na tela de resultado |
| REV-02 | P1 | Logo depois, vencer outra partida e voltar ao menu (mesmo dia) | Não pede de novo (30 dias); o jogo segue normal |
| REV-03 | P1 | Dados limpos, 3 vitórias já feitas, perder a partida e voltar ao menu | Não pede depois de derrota |
| REV-04 | P0 | APK minificado (`assembleRelease -PqaSign`) com a lib real: vencer a 3ª partida e voltar ao menu | Abre o fluxo ou não mostra nada (cota da Google); o jogo não trava nem fecha, logcat sem `FATAL EXCEPTION` |

### 12c. Conferência de instalação por push (#26)

Conferência da lista da Play, só para o John (indicador; o número oficial é o do Play Console).
Rodar `select public.push_check_run();` (SQL) e depois `select * from public.testers_install_report();`.

| ID | P | Passos | Esperado |
|---|---|---|---|
| CHK-01 | P1 | Rodar a conferência com QA01 instalado | Conferência roda; nenhuma notificação aparece no QA01 |
| CHK-02 | P1 | Desinstalar o app no QA02 e rodar a conferência | Relatório mostra `desinstalado` para QA02 (prova do `validate_only`) |
| CHK-03 | P1 | Reinstalar no QA02, abrir, rodar a conferência | Relatório mostra `instalado` com o token novo; o antigo não conta |
| CHK-04 | P2 | Relatório completo | Uma linha por testador da lista da Play; sem iPhone, `manual` nem `qa` |
| CHK-05 | P1 | **PENDENTE do passo 0 na skill `esteira`** (o John inclui). Depois, rodada num dia com conferência: 1ª rodada (depois das 8 h) dispara; 2ª rodada, ~30 min depois | 1ª devolve "Conferência de instalação disparada"; 2ª devolve o resumo com contagens e usuários, sem e-mail; a 3ª rodada do dia devolve nada |
| CHK-06 | P1 | Token `unregistered` (QA02 desinstalado) e um envio real (ex.: FR-02 ou PUSH-01) | A `push` marca o token (não apaga a linha) e o envio seguinte pula esse token; reinstalar e registrar zera a marca |

### 12e. Conquistas e placar do Google Play Games (#48)

| ID | P | Passos | Esperado |
|---|---|---|---|
| PGS-01 | P0 | v0.40.0 da Play (IDs vazios), abrir e jogar uma partida contra a IA | App abre e joga; nenhuma tela do Play Games; Perfil sem o atalho "Conquistas"; logcat sem `FATAL EXCEPTION` |
| PGS-02 | P1 | **Só na versão de ativação**, QA01 como testador do Play Games: abrir o app | Aparece o "Bem-vindo de volta" do Play Games (conferir o login automático já na primeira abertura, sem botão de entrar) |
| PGS-03 | P1 | Ativação: vencer a 1ª partida contra a IA | Aviso de conquista "Primeira vitória" do Play Games |
| PGS-04 | P1 | Ativação: Perfil → Conquistas | Abre a lista do Play Games com o progresso de navios afundados (até 100) e a recuperação da carreira feita |
| PGS-05 | P1 | Ativação: ranqueada QA01 x QA02 | A pontuação da temporada aparece no placar "Ranqueada: melhor pontuação" |
| PGS-06 | P0 | APK minificado (`assembleRelease -PqaSign`) com a lib real | Nada some pelo R8: login, conquista e placar funcionam |

## 13. Ajustes, notificações, feedback e idioma

| ID | P | Passos | Esperado |
|---|---|---|---|
| SET-01 | P1 | Trilha e efeitos | Toggles separados funcionam na hora |
| SET-02 | P1 | Idioma EN e ES | Todas as telas visitadas trocam na hora, sem texto cortado; voltar para PT |
| SET-03 | P1 | Testar notificação | Notificação chega em ~5s com o app em segundo plano |
| SET-04 | P1 | Bloquear notificações no sistema | Ajustes mostra aviso com atalho para o sistema |
| SET-06 | P1 | Ajustes → Online (conta nova ou existente) | "Avisar quando um amigo ficar online" aparece ligado; com "Receber convites" desligado fica inativo (#19) |
| SET-05 | P2 | Sobre → Novidades / Compartilhar | Lista de versões; compartilhar abre a folha do sistema com o link da loja |
| SET-07 | P1 | Sobre → Novidades, em PT, EN e ES (#31) | A primeira linha é a versão instalada (hoje 0.34.0) e a lista chega até ela (0.26.0 a 0.34.0), em ordem decrescente, textos no idioma escolhido; sem rede a tela abre igual |
| PUSH-01 | P1 | Com o app fechado em B, A faz FR-02/FR-05 | Notificação do sistema aparece em B; tocar abre o jogo |
| PUSH-02 | P2 | Tocar na notificação com o jogo minimizado (não fechado) | Volta para o jogo onde estava, sem passar pela abertura de novo |
| ABL-01 | P1 | Tático: ativar Barragem dupla e acertar o 1º tiro | A barragem continua valendo: um erro depois ainda dá o tiro extra |
| FB-01 | P1 | Feedback (logado) → tocar no **meio** da caixa de texto e digitar | Teclado abre em qualquer ponto da caixa; envio confirma; aparece em `public.feedback` |
| FB-02 | P2 | Feedback deslogado | Botão "Entrar ou criar conta" leva ao Perfil |
| LAUNCH-01 | P1 | Com `launch_notice_enabled = 'false'`, abrir o app (Android) em conta de testador e em conta nova | Nenhum aviso de lançamento (#36) |
| LAUNCH-02 | P1 | Ligar o interruptor (`'true'`), reabrir em conta da lista de testadores | Popup "Naval Battle Classic chegou à Google Play!" com o agradecimento (dobrões e brindes continuam; tudo pago a partir de agora), no deque, depois do popup de atualização e antes de temporada/feedback/Diário |
| LAUNCH-03 | P1 | Tocar em "Entendi" (ou no voltar do sistema), fechar e reabrir; depois abrir em outro aparelho com a mesma conta | Não volta (marca no servidor, `launch_notice_seen` com 1 linha) |
| LAUNCH-04 | P1 | Conta nova (fora da lista, sem aparelho iPhone, sem medalha com corte) | Popup com a boas-vindas, não o agradecimento |
| LAUNCH-05 | P1 | Convidado (sem login) | Boas-vindas; depois de "Entendi" não volta naquele aparelho |
| LAUNCH-06 | P1 | Durante partida e com convite de amigo na tela | O aviso não abre por cima; abre no deque depois |
| LAUNCH-07 | P1 | No iPhone (qualquer conta), interruptor ligado | Nenhum aviso, com nenhum texto; no banco `launch_notice_status('ios')` devolve `enabled = false` |
| LAUNCH-08 | P2 | Aviso e popup de nova temporada juntos | Aviso por cima; ao tocar "Entendi" o convite da temporada aparece embaixo, sem piscar |

## 14. Limpeza (sempre ao fim)

| ID | Passos | Esperado |
|---|---|---|
| CLEAN-01 | Remover amizade A × B (ou manter, se o usuário quiser a dupla pronta) | Lista de amigos limpa |
| CLEAN-02 | Apagar o feedback de teste (FB-01) no Supabase, sem aprovar recompensa | Fila de feedback só com reais |
| CLEAN-03 | Desligar "Disponível para partida rápida" em B | Contas QA não recebem salas de jogadores reais |
| CLEAN-04 | Conferir que nenhuma sala online ficou aberta pelas contas QA | `online_matches` sem sala `waiting` delas |
| CLEAN-05 | Desativar as contas QA: `update public.profiles set is_test = true where id in (select id from auth.users where email in ('aigamesfactory.qa01@gmail.com','aigamesfactory.qa02@gmail.com'));` (ver Regras do ambiente). A passada `refresh_all_is_test` não mexe em conta QA (0.34.5, #35), então este passo é a única forma de desativar | QA01/QA02 `is_test = true`, fora do Placar público |

**Excluir conta (AUTH-06)** é destrutivo e só roda quando pedido de propósito: Perfil →
Excluir conta → confirmar → o jogo volta como convidado e o usuário some do servidor
(`auth.users`); entrar de novo com a mesma conta cria uma conta nova zerada.

---

## 15. Relatório

Ao fim, entregar em pt-BR:

1. **Resumo** — versão testada, data, quantos OK / FALHA / N/A, e se a versão está apta.
2. **Falhas** — tabela por prioridade: ID, o que aconteceu, passo para reproduzir, print.
3. **Observações** — coisas que não são falha mas valem atenção (lentidão, texto estranho).
4. **Limpeza** — o que foi desfeito no servidor.

Falha P0 ou P1 confirmada segue o fluxo normal: corrigir, bumpar versão, publicar e
rerodar só os casos afetados.
