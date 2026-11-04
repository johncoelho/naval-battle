# Plano de testes do iPhone (TestFlight)

Para o John seguir de pé, com o iPhone na mão. Cada caso tem: o que preparar, passos curtos, o que
deve acontecer e uma coluna **Resultado** para você preencher (**Passou** ou **Falhou** + uma frase).
Quando terminar, devolva este arquivo preenchido (ou só a coluna Resultado) para o PM. Os IDs
(APL-xx, PSH-IOS-xx...) são os mesmos de [QA_TEST_PLAN.md](QA_TEST_PLAN.md), onde estão os detalhes de
servidor.

Elaborado em 04/11/2026 a partir das issues #13, #14, #40, #43, #46, #56, #59, #61, #62, #63 e #65.

---

## 0. Antes de começar

### Qual build instalar

- Link público do TestFlight: <https://testflight.apple.com/join/PvGjjszH> (abra no iPhone; precisa do app
  TestFlight instalado).
- **Teste na 0.43.4 ou superior.** Hoje o TestFlight só tem a **0.42.2 (build 8)**; o Release está enviando
  a 0.43.4. Na 0.42.2 vários casos **vão falhar de propósito** (aba Dobrões em branco, sem push, sem
  Vincular Apple ID). Não teste esses casos nela: espere a 0.43.4 aparecer no TestFlight e atualize.
- **Como conferir a versão no app:** abra o jogo, vá em **Ajustes** e olhe o rótulo de versão perto do
  fim da tela (formato `0.43.4 (9)`, versão e build). No menu principal também aparece `v0.43.4 (9)`
  discretamente. Se aparecer `0.42.2 (8)`, ainda não atualizou: no TestFlight toque em **Atualizar**.
- O caso **INST-IOS-02** registra a versão testada. Anote-a em todo relato.

### Como reportar uma falha

1. Tire um **print** do que aparece (botões laterais: lateral + aumentar volume).
2. Escreva na coluna Resultado: **Falhou**, o **ID do caso** e uma frase do que viu.
3. Se o app fechar sozinho, anote o horário aproximado (dá para achar o erro no servidor).
4. Mande ao PM os prints com o ID no nome (por exemplo `APL-12.png`).

Regra: **um caso por vez, na ordem**. Se um caso falhar e impedir os seguintes, anote "bloqueou" nos
seguintes e siga para o próximo grupo.

### O que o QA/Release preparam no servidor ANTES (você não faz)

| Item | Quem | Para quê |
|---|---|---|
| Build 0.43.4+ no TestFlight, processada e liberada ao grupo de testadores | Release | Todos os casos |
| Edge `push` publicada **com a chave APNs** (`APNS_KEY_ID`/`APNS_KEY_P8`; a chave atual nasceu só com Sign in with Apple e dá `ios:403`) | John + Release | Grupo E (push). Sem isso PSH-IOS-02/05 falham por causa do servidor, não do app |
| Auth do Supabase com **"Allow manual linking"** ligado e decisão sobre "Confirm email" | John + Release | Grupo D (APL-12 a APL-17) |
| Interruptor `launch_notice_enabled` conferido (o aviso nunca deve ir ao iPhone) | QA | LAUNCH-07 |
| `app_releases`: a versão nova com `ios_live = true` só quando o build estiver no TestFlight | Release | Grupo H (aviso de atualização) |
| Conta de teste `is_test` conferida: a conta que você usar para o Prêmio Beta (testador/John elegível em `beta_eligible`) e uma conta que **não** seja testador | QA | DOBIOS-01/02 |
| Contas "virgens" (Google/e-mail sem nenhuma partida) liberadas para você, o PM combina com você | John | NOM-01, WEL-05 |
| Depois de cada caso do servidor (PSH-IOS-01/02/07, APL-05/12/13/14, NOM-01): QA lê `push_tokens`, `push_outbox`, `auth.identities`, `profiles` | QA | Confirmar o que o app não mostra |

### O que só o iPhone real permite

Entrar com a Apple (janela da Apple), push pelo APNs (produção, só TestFlight/App Store), TestFlight,
permissão de notificação, comportamento ao voltar do segundo plano, teclado e área segura do iPhone.
Nada disso vale em simulador nem em emulador Android.

### O que você precisa ter em mãos

- O iPhone, com o TestFlight e o build instalados, Wi-Fi ou 4G ligado.
- Seu Apple ID (o que está no iPhone). Sem digitar senha por ninguém: a janela da Apple é sua.
- A conta Google que você já usa no jogo (a que tem carreira) e, para o grupo D, uma segunda conta
  Google que **nunca** entrou no jogo (o PM combina com você).
- Um segundo aparelho (Android com QA01 ou outro) para os casos de push e convite. Quem prepara é o QA.

---

## A. Instalação (TestFlight) · issue #56

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| INST-IOS-01 | iPhone com TestFlight | 1. Abra o link <https://testflight.apple.com/join/PvGjjszH>. 2. Toque **Aceitar / Participar**. 3. Toque **Instalar** (ou **Atualizar** se já tiver a 0.42.2). | O app "Naval Battle Classic" instala pelo TestFlight, sem aviso de "desenvolvedor não confiável" e sem precisar de computador | |
| INST-IOS-02 | Build instalado | 1. Abra o jogo. 2. Vá em **Ajustes** e leia a versão. 3. Anote. | Versão **0.43.4 (build) ou maior**. Se for 0.42.2 (8), pare e atualize no TestFlight | |
| INST-IOS-03 | Idem | 1. Feche o jogo e abra de novo. 2. Deixe 30 segundos no menu. 3. Toque em Jogar contra a IA e comece uma partida até o 1º tiro. | Abre sem travar, ícone correto, menu e partida contra a IA funcionam | |

## B. Login (sem conta anterior no aparelho) · issue #13

Preparar: **Perfil sem sessão** (convidado). Se já estiver logado, Perfil → Sair da conta antes.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| APL-01 | Convidado, Perfil | 1. Toque **Entrar com a Apple** (fica acima do Google). 2. Na janela da Apple, escolha **Compartilhar meu e-mail**. 3. Confirme. | Entra sem erro (nada de "erro 1000" nem `AUTH_APPLE_UNAVAILABLE`); a conta nova mostra o nome da Apple | |
| APL-02 | Convidado, Perfil | 1. Toque **Entrar com a Apple**. 2. Feche/cancele a janela da Apple. | Volta para a tela de entrar, sem erro e sem sessão | |
| APL-03 | Logado pela Apple (APL-01) | Perfil → olhe o cartão de senha | Aparece **"Criar senha"**, não "Trocar senha" | |
| APL-06 | Logado pela Apple | 1. Feche o app (arraste para cima). 2. Abra de novo. | Continua logado, entra direto | |
| AUTH-IOS-01 | Convidado | 1. Perfil → **Entrar com Google**. 2. Escolha a conta e confirme. | Entra; nome, avatar e patente aparecem no topo do menu | |
| AUTH-IOS-02 | Logado pelo Google | Feche o app e abra de novo | Continua logado | |

## C. Nome de guerra ao entrar · issues #61 e #59

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| NOM-02 | Convidado **sem nome de guerra** (campo vazio), iPhone com conta Google que já tem nome | 1. Perfil. **Sem sair da tela**, toque **Entrar com Google**. 2. Escolha a conta. 3. Olhe o campo "Nome de guerra". 4. Digite "Capita" no campo e espere uns 10 s sem salvar. | O campo mostra o nome da conta **assim que o login termina**, sem reabrir a tela. O "Capita" que você digitou **não** é trocado sozinho | |
| NOM-01 | Conta Google/e-mail **virgem** (sem partidas) liberada pelo QA, e um **nome de guerra repetido**: o QA informa um nome que já existe | 1. Perfil de convidado. 2. Escolha esse nome repetido e jogue 2 partidas contra a IA. 3. Entre com a conta virgem. 4. Jogue mais 1 partida e espere 1 min. | Aparece "carreira enviada" (ou equivalente); o nome vira `nome-xxxx` ou o da conta; o jogo **não** mostra erro; o QA vê no servidor XP e partidas subindo | |

## D. O que depende de conta (vincular, relay, boas-vindas) · issues #13, #63, #43

Preparar: conta Google **com carreira** (a sua de sempre) e, onde pedido, uma conta Google virgem.
Os casos APL-12 a APL-17 só existem na 0.43.x.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| APL-12 | Logado com Google (carreira). Servidor: "Allow manual linking" ligado | 1. Perfil → **Logins da conta: Google**. 2. Toque **Vincular Apple ID**. 3. Confirme na janela da Apple. | Mensagem de vínculo feito; a linha passa a **"Google · Apple"**; o botão **Vincular Apple ID** some. QA confirma: mesmo `user_id`, identidade `apple` a mais | |
| APL-05 | Conta Google com carreira e dobrões; o Apple ID usa o **mesmo e-mail** do Google. Antes, **desvincule** no QA ou use outro Apple ID | 1. Perfil → Sair. 2. **Entrar com a Apple** → Compartilhar meu e-mail. | Cai na **mesma** carreira e nos mesmos dobrões, sem conta nova | |
| APL-13 | Logado pela Apple (conta aberta pela Apple) | 1. Perfil → **Vincular Google**. 2. Escolha a conta Google. 3. Perfil → Sair. 4. **Entrar com Google** nessa conta. | Linha **"Apple · Google"**; entrar pelo Google abre a mesma carreira | |
| APL-14 | iPhone sem conta, Apple ID que nunca autorizou o app (ou depois de "Parar de usar o ID Apple" em Ajustes do iPhone), **Ocultar meu e-mail** | 1. Perfil → **Entrar com a Apple** → Ocultar meu e-mail. 2. Leia a pergunta. 3. Toque **Sim** e entre com o Google da sua carreira. | Antes de entrar aparece **"Já joga Naval Battle com Google?"**. Sim: entra com o Google e a Apple fica vinculada (mesma carreira). Nenhuma conta nova vazia | |
| APL-15 | Mesmo ponto do APL-14, outra tentativa | 1. Na pergunta, toque **Não, criar conta nova**. 2. Em outra tentativa, toque **Cancelar**. | Não: segue o login da Apple de sempre (conta nova). Cancelar: nada acontece e nenhuma conta é criada | |
| APL-04 | Logo depois do APL-15 (conta nova por relay) | Perfil → Conta; olhe o cartão de senha | Aparece **"E-mail oculto pela Apple"** e o cartão de Criar senha mostra o endereço de relay | |
| APL-16 | Apple ID que já tem conta no jogo (a do APL-12) e **outra** conta Google (QA03 ou virgem) | 1. Entre na outra conta. 2. Perfil → **Vincular Apple ID**. | Aparece a orientação de que o Apple ID já está em outra conta; nada é alterado nas duas contas | |
| WEL-05 | **Convidado** (dados do app limpos ou conta virgem), iPhone | 1. Menu → **Primeira missão** → **Pular** (passo 1). 2. Leia o popup (deve pedir para entrar). 3. Entre com Google numa conta **nova** (virgem). 4. Se não chegar, feche e abra o app. | Popup diz "Crie sua conta para receber ◆150 + 5 milhas" (sem `%s`/`%1`). Ao entrar, o presente de **150 dobrões + 5 milhas** chega (pode chegar só ao reabrir o app: é o bug conhecido #72, anote qual foi o caso) | |

Opcionais destrutivos (só com conta de teste, **nunca na conta principal**):

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| APL-09 | Conta de teste aberta pela Apple | Perfil → Excluir conta → confirmar → na janela da Apple **cancele** | Nada é apagado; aparece aviso de confirmação da Apple | |
| APL-07 | Mesma conta de teste | Perfil → Excluir conta → confirmar → janela da Apple → **confirme**. Depois: Ajustes do iPhone → seu nome → Senha e Segurança → Entrar com a Apple | Conta apagada; o app **some** da lista de apps que usam Entrar com a Apple | |

## E. Push no iPhone (APNs) · issue #14

Pré-requisito do servidor: Edge `push` com chave APNs (seção 0). **Só vale pelo TestFlight.** Preparar um
segundo aparelho (QA/Android) com conta amiga sua para mandar pedido de amizade ou convite.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| PSH-IOS-01 | iPhone logado, notificações ainda não decididas | 1. Abra o app. 2. Quando pedir permissão de aviso, toque **Permitir**. 3. Avise o PM: o QA confere. | Pediu a permissão uma vez; o servidor ganha uma linha em `push_tokens` com `platform = 'ios'` (QA confirma) | |
| PSH-IOS-02 | Permissão concedida; app **fechado** (arraste para cima) | 1. O QA/outra conta manda a você **pedido de amizade** ou **convite de partida**. 2. Espere até 1 min. | Chega o aviso com o mesmo texto do Android; `push_outbox.result` traz `ios:200` (QA confirma). Se vier `ios:403`, é a chave APNs (servidor) | |
| PSH-IOS-04 | Mesma preparação, receba um aviso "amigo online" (QA dispara) | Toque no aviso com o app fechado; repita com o app em segundo plano | Abre na tela de **Amigos**; outros avisos só abrem o jogo | |
| PSH-IOS-03 | Ajustes → **Receber convites** desligado | O QA manda outro convite | **Nenhum** aviso no iPhone | |
| PSH-IOS-05 | App fechado, tarde entre 11h e 15h (Brasília), iPhone registrado | O QA dispara o `daily_invite` | O iPhone recebe, uma vez no dia | |
| PSH-IOS-06 | Instalação limpa ou permissão **negada** em Ajustes do iPhone | 1. Negue o aviso. 2. Feche e abra o jogo 3 vezes. | Não pede de novo; o jogo segue sem erro | |
| PSH-IOS-07 | Só servidor | O QA insere token iOS falso (`BadDeviceToken`) e envia | O token falso fica `unregistered`; o aparelho real recebe; `new_version` nunca vai ao iPhone | QA |
| LAUNCH-07 | iPhone, qualquer conta, interruptor de lançamento ligado pelo QA | Abra o app e espere 1 min | **Nenhum** aviso de lançamento ("chegou à Google Play") | |

## F. Loja de dobrões · issues #62 e #65

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| DOBIOS-01 | Conta que **não** é testador (a que o QA indicar) | 1. Menu → Loja → aba **Dobrões**. 2. Espere 10 s. | Mostra **"A compra de dobrões chega em breve."**. **Nunca** fica em branco nem preso em "Abrindo o cofre…" | |
| DOBIOS-02 | Conta de testador/John com Prêmio Beta ligado pelo QA | 1. Aba Dobrões. 2. Toque para pegar o **Prêmio Beta**. 3. Volte a tocar no mesmo dia. | Só o cartão do Prêmio (nenhum pacote pago); ao pegar, aviso **"+N"** e o saldo sobe; de novo no mesmo dia mostra "volta à meia-noite" | |
| DOBIOS-03 | Qualquer conta | 1. Ative o **modo avião**. 2. Abra a aba Dobrões. 3. Desative o modo avião. 4. Toque **Tentar de novo**. | Aparece "Não consegui abrir o cofre…" com **Tentar de novo**; com a rede, carrega | |

**#65 (StoreKit, compras reais) não tem caso aqui de propósito:** continua na Fila e **não há compra no
iPhone nesta versão**. Pré-requisitos antes de testar um dia (do John e do Tech Lead): aprovar a stack
(StoreKit 2 + App Store Server API); Paid Apps Agreement, impostos e conta bancária no App Store Connect;
chave In-App Purchase (.p8) como segredo no Supabase; 4 consumíveis cadastrados com os mesmos IDs da Play
(Bolsa, Baú, Cofre, Tesouro); sandbox tester da Apple. Quando houver build, o QA_IPHONE ganha um grupo novo.

## G. Anúncios · issue #40

No iPhone **não há anúncio premiado** (o código do iOS devolve "sem anúncio": `RewardedAds.ios.kt`, e o
servidor também não libera para `ios`). O caso é só garantir que nada aparece.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| ADS-18 | iPhone, conta com milhas | 1. Vença uma partida contra a IA. 2. Na tela de resultado, olhe os botões. 3. Esvazie as milhas (jogue online até 0) ou abra o aviso de "0 milhas". | **Nenhum** botão "DOBRAR DOBRÕES" nem "GANHAR 1 MILHA"; nada de anúncio. Nos Ajustes não há "Privacidade dos anúncios" funcionando (ou a opção não existe) | |

O restante da #40 (ADS-02 a ADS-26) é só Android e fica com o QA nos emuladores.

## H. Aviso de atualização · issue #46

O iPhone não consulta loja: o aviso vem da tabela `app_releases` com `ios_live = true`. Só dá para testar
quando existir uma versão **maior** que a instalada, liberada no TestFlight. Nesta rodada: instale a
0.43.4, depois o Release libera a próxima.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| UPD-IOS-00 | Hoje, no iPhone ainda com a **0.42.2 (8)** e a 0.43.4 já marcada `ios_live` | 1. Abra o app no menu. | Pode aparecer o popup "Versão 0.43.4 disponível". Anote o texto do botão e do dica (hoje ainda diz ".ipa" no aviso do iPhone; isso é a #70/#71, **não é falha deste caso**) | |
| UPD-10 | 0.43.4 instalada. O QA/Release publicam a versão seguinte e marcam `ios_live` | 1. Deixe o jogo aberto no menu e vá para a tela inicial do iPhone. 2. Espere o aviso do QA de que a nova versão saiu. 3. Volte ao jogo. | O popup de atualização aparece **sem reabrir o app** (consulta no máximo a cada 5 min; espere se ainda não apareceu) | |
| UPD-11 | Depois do UPD-10 | 1. Toque **Depois**. 2. Vá para a tela inicial e volte. | Não reaparece para a mesma versão; só com outra versão maior | |
| UPD-12 | Versão nova disponível | 1. Comece uma partida contra a IA. 2. Vá à tela inicial e volte. | O aviso **não** abre durante a partida; só ao voltar ao menu | |
| UPD-IOS-13 | Popup aberto | Toque no botão principal do popup | Abre a página do jogo/TestFlight para atualizar. Anote para onde levou | |

---

## Resumo do tempo e da ordem

1. Seção 0 e A (instalação): 10 min.
2. B (login) e C (nome): 20 min.
3. D (contas): 30 a 40 min. É a parte mais longa e depende das contas virgens.
4. E (push): 20 min com o QA do outro lado.
5. F e G (loja e anúncios): 10 min.
6. H (atualização): depende de nova versão; pode ficar para depois.

## Quando tudo passar, estes cartões vão para Em produção (ou ficam como estão)

| Cartão | Casos que precisam passar | Estado do cartão hoje | Depois de passar |
|---|---|---|---|
| #56 iPhone: TestFlight | INST-IOS-01 a 03 | Liberado para testes | **Em produção** |
| #13 Entrar com a Apple | APL-01 a 03, 06, 04, 05, 14, 15 (e APL-07/09 se rodados) | Em produção, iPhone nunca testado | Fica; registra validado no iPhone |
| #63 Vincular Apple ID e Google | APL-12, 13, 16 (e APL-05, 14) | Liberado para testes | **Em produção** |
| #61 Nome de guerra após login Google | NOM-02 (e AUTH-IOS-01/02) | Liberado para testes | **Em produção** |
| #59 Nome repetido no merge | NOM-01 | Liberado para testes | **Em produção** |
| #43 Boas-vindas | WEL-05 | Em produção (Android ok) | Fica; registra validado no iPhone |
| #14 Push no iPhone | PSH-IOS-01 a 06 (07 e LAUNCH-07: QA) | Em produção, iPhone nunca testado | Fica; registra validado no iPhone |
| #62 Aba Dobrões no iPhone | DOBIOS-01 a 03 | Liberado para testes | **Em produção** |
| #40 Anúncios para todos | ADS-18 (iPhone); o resto no Android | Liberado para testes | Só o lado do iPhone fecha aqui; o cartão espera o QA do Android |
| #46 Atualização ao voltar | UPD-10 a 12 (e UPD-IOS-00/13) | Liberado para testes | **Em produção** |
| #65 StoreKit | nenhum caso | Fila | Não entra: sem build |

Regra: o cartão só vai para Em produção se **todos** os casos dele estiverem "Passou" ou com falha já
explicada como bug conhecido (#72, #70/#71) que o PO aceitou. Falha nova vira bug na Fila.
