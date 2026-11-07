# Plano de testes do iPhone (TestFlight)

Para o John seguir de pé, com o iPhone na mão. Cada caso tem: o que preparar, passos curtos, o que
deve acontecer e uma coluna **Resultado**. Os IDs (APL-xx, PSH-IOS-xx...) são os mesmos de
[QA_TEST_PLAN.md](QA_TEST_PLAN.md), onde estão os detalhes de servidor. Dono do plano: **QA**.

Elaborado em 04/11/2026 pelo PO (issues #13, #14, #40, #43, #46, #56, #59, #61, #62, #63, #65) e revisado
pelo QA no mesmo dia contra o código (`AppleAuth.ios.kt`, `PushMessaging.ios.kt`, `RewardedAds.ios.kt`,
`UpdateChecker.ios.kt`, `iOSApp.swift`), o banco e as Edges.

---

## 0. Antes de começar

### Qual build instalar

- Link público do TestFlight: <https://testflight.apple.com/join/PvGjjszH> (abra no iPhone; precisa do app
  TestFlight instalado).
- **Teste na 0.43.4 ou superior.** Até esta revisão o TestFlight só tinha a **0.42.2 (build 8)**; a 0.43.4
  está no envio do Release (rodada 15 do workflow "iPhone na App Store"; depois do envio a Apple leva
  de 10 a 30 min para processar). Na 0.42.2 vários casos **falham de propósito** (aba Dobrões em branco, sem
  push, sem Vincular Apple ID, sem aviso de atualização). Não teste esses casos nela.
- **O número da build não é fixo:** é o número da rodada do workflow (a 0.43.4 deve sair como **(15)**, ou
  maior se o Release rodar de novo). O que vale é a versão **0.43.4 ou maior**.
- **Como conferir a versão no app:** **Ajustes**, rótulo em amarelo perto do fim da tela (`0.43.4 (15)`).
  No menu principal aparece também, discreto e em maiúsculas, `V0.43.4 (15)`. Se mostrar `0.42.2 (8)`, abra o
  TestFlight e toque **Atualizar**.
- O caso **INST-IOS-02** registra a versão testada. Anote-a no topo de todo relato.

### Como devolver os resultados (o jeito mais curto)

Responda ao PM no chat com uma lista, **uma linha por caso**, e os prints (nome do arquivo = ID, por
exemplo `APL-12.png`; botões laterais: lateral + aumentar volume):

```
Versão testada: 0.43.4 (15)
INST-IOS-01: Passou
APL-01: Falhou — apareceu "erro 1000" depois de confirmar
```

APL-12: N/A — não deu para testar (motivo)
```

Regras: use só **Passou**, **Falhou** (com uma frase do que viu) ou **N/A** (com o motivo). Se um caso
falhar e travar os seguintes, escreva **Bloqueou** neles e siga para o próximo grupo. Se o app fechar sozinho,
anote o horário aproximado (o QA acha o erro no servidor). Pode também devolver este arquivo preenchido: tanto faz.

### O que o QA faz entre os casos (você não faz)

O QA acompanha a rodada em tempo real e, **depois de cada caso com "QA confere"**, olha o banco
(`profiles`, `auth.identities`, `welcome_gifts`, `push_tokens`, `push_outbox`, `device_checks`) e as Edges,
e responde "confere" ou "não confere". Quem decide Passou/Falhou final é o QA. Entre as etapas do grupo D o QA
também **zera o estado de teste por SQL** (apaga a conta de teste ou o vínculo Apple) e avisa quando pode seguir.
Por isso o grupo D é feito **com o QA conectado**: ele diz "pode seguir" a cada etapa.

### O que já está pronto no servidor (QA conferiu em 04/11)

| Item | Estado |
|---|---|
| Edge `push` com ramo iPhone (APNs) implantada | Sim (versão 12), com a **chave APNs atual** (decisão do John, 04/11: não será criada chave nova). A Apple já a aceitou: a sonda do QA com token falso devolveu `ios:400:BadDeviceToken` (se um dia vier `ios:403`, o grupo E para e o QA avisa). O grupo E **não depende mais de chave nova** |
| `ios_live` da 0.43.4 em `app_releases` | **Não**: está `false` (certo: o Release só liga quando a App Store já oferece a versão, conferido pelo `lookup`; o TestFlight não conta). Só o grupo H usa |
| `launch_notice_enabled` | `false`. **Não será ligado** (ligaria o aviso para todos os jogadores do Android). O LAUNCH-07 é conferido só no servidor (bloco que desfaz), sem ligar nada |
| Anúncios (`ads_enabled`, `ads_min_app_version`) | Não mexidos por este plano |
| Edge `apple-revoke` | **Autorizada pelo John a implantar (04/11); o Release implanta.** Até o QA confirmar a implantação (`supabase functions list`), o **APL-07** apaga a conta mas o app continua na lista "Entrar com a Apple" do iPhone (não é falha). Com a Edge implantada, o app deve sumir da lista |
| Auth "Allow manual linking" | **O QA não consegue ler esse interruptor** (só pelo painel do Supabase). O APL-12 prova: se aparecer "Vincular logins não está disponível agora", está desligado (é do Release ou do John ligar) |
| Tokens iPhone em `push_tokens` | Nenhum hoje (0 linhas `ios`) |

### Contas e o que você precisa ter em mãos

- O iPhone, com TestFlight e o build instalados, Wi-Fi ou 4G ligado.
- **Seu Apple ID** (o do iPhone). **Só este**: não use segundo Apple ID. Para repetir um caso do zero, use
  o caminho: **Ajustes do iPhone → seu nome (ID Apple) → Iniciar sessão com a Apple → Naval Battle → Parar de
  usar o ID Apple**. Depois disso a janela da Apple volta a perguntar tudo como na primeira vez. O QA avisa quando
  fazer.
- **Conta Google "principal"** (a sua, com carreira). Casos que só *entram* nela: AUTH-IOS, NOM-02. Não é
  usada para excluir conta.
- **Conta Google QA03: `aigamesfactory.qa03@gmail.com`** (já existe; criada em 01/11 para a gravação). O QA conferiu em 04/11: **não há mais** `auth.users`, `profiles` nem identidade dela no jogo (conta do jogo apagada), então o primeiro login gera conta virgem. Ela já está em `allowed_players` com `source = 'qa'` (conta de teste, anúncio de teste, presente de boas-vindas mesmo com a regra de data). Serve para: vincular, boas-vindas, nome repetido e **excluir conta** (APL-07 e APL-09). **Você** entra nela no iPhone (digita a senha do Google); o QA nunca entra. **Nunca** exclua conta real.
- Senha: você digita a sua, nas janelas do Google e da Apple. Ninguém mais digita senha.
- Um segundo aparelho para push e convite: o QA usa o emulador QA01 (Android). Para o QA mandar pedidos para a sua
  conta, ele precisa ser seu amigo no jogo: o primeiro caso do grupo E faz isso (o QA apaga a amizade no fim).

### O que só o iPhone real permite

Entrar com a Apple (janela da Apple), push pelo APNs (produção, só TestFlight/App Store), permissão de
notificação, volta do segundo plano, teclado e área segura do iPhone. Nada disso vale em simulador.

### Regras da QA03 para o QA (não do John)

1. Já feito em 04/11: linha em `allowed_players` (`aigamesfactory.qa03@gmail.com`, `source = 'qa'`, nota "QA03 iPhone"). Vale pelo e-mail, então continua valendo se a conta do jogo for apagada e recriada.
2. A conta nasce com `is_test = false` (o gatilho de criação não marca teste e a passada `refresh_all_is_test` não mexe em conta QA). O QA confere isso logo depois do primeiro login e **antes** do WEL-05: conta `is_test = true` não recebe o presente de boas-vindas.
3. A cada "zerar a QA03", o QA apaga a conta (`delete from auth.users` da QA03; o `profiles` e o resto saem em cascata) e confere `auth.users`, `profiles`, `welcome_gifts` e `auth.identities` vazios. A próxima entrada pelo Google cria conta virgem.
4. **Limpeza ao fim:** `is_test = true` na conta da QA03 (se ainda existir), sobras apagadas (amizade, `push_tokens`, `device_checks`), e `allowed_players` da QA03 **fica** (como QA01/QA02). Nenhum ajuste em `ads_enabled` nem `ads_min_app_version`.

---

## A. Instalação (TestFlight) · issue #56

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| INST-IOS-01 | iPhone com TestFlight | 1. Abra o link <https://testflight.apple.com/join/PvGjjszH>. 2. Toque **Aceitar / Participar**. 3. Toque **Instalar** (ou **Atualizar** se já tiver a 0.42.2). | O app "Naval Battle Classic" instala pelo TestFlight, sem aviso de "desenvolvedor não confiável" e sem computador | |
| INST-IOS-02 | Build instalado | 1. Abra o jogo. 2. **Ajustes** → leia a versão. 3. Anote. | **0.43.4 ou maior**. Se for 0.42.2 (8), pare e atualize no TestFlight | |
| INST-IOS-03 | Idem | 1. Feche o jogo (arraste para cima) e abra de novo. 2. Deixe 30 s no menu. 3. Comece uma partida contra a IA e dê o 1º tiro. | Abre sem travar, ícone correto; menu e partida contra a IA funcionam | |

## B. Entrar sem conta anterior (convidado) · issue #13

Preparar: **Perfil sem sessão** (convidado). Se estiver logado: Perfil → Sair da conta.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| AUTH-IOS-01 | Convidado | 1. Perfil → **Entrar com o Google**. 2. Escolha a conta **principal** e confirme. | Entra; nome, avatar e patente no topo do menu | |
| AUTH-IOS-02 | Logado pelo Google | Feche o app e abra de novo | Continua logado | |

(Depois: Perfil → Sair da conta, para o grupo D começar como convidado.)

## C. Nome de guerra ao entrar · issue #61

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| NOM-02 | Convidado **sem nome de guerra** (campo vazio) | 1. Perfil. **Sem sair da tela**, toque **Entrar com o Google** e escolha a conta **principal**. 2. Olhe o campo "Nome de guerra". 3. Apague e digite "Capita" no campo e espere ~10 s sem salvar. | O campo mostra o nome da conta **assim que o login termina**, sem reabrir a tela. O "Capita" digitado **não** é trocado sozinho | |

(O NOM-01, nome repetido na fusão, é o último caso do grupo D porque usa a QA03.)

## D. Apple ID, vínculos, boas-vindas · issues #13, #63, #43, #59

**Faça em ordem, com o QA conectado.** Cada etapa diz o que **você** faz e o que o **QA** confere. "Zerar a Apple" = o QA
apaga a identidade ou a conta Apple de teste por SQL **e** você faz *Parar de usar o ID Apple* (caminho no começo do
documento). Os casos APL-05, APL-14 e APL-16 usam esse caminho, sem segundo Apple ID.

### D1. Primeira entrada pela Apple (compartilhando o e-mail)

Preparar: convidado; **zerar a Apple** (na primeira vez, só o *Parar de usar o ID Apple*, se o Naval Battle aparecer na lista).

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| APL-02 | Perfil → **Entrar com a Apple** (fica acima do Google). Na janela da Apple, **feche/cancele** | Volta para a tela de entrar, sem erro e sem sessão | nenhuma linha nova em `auth.users` | |
| APL-01 | Perfil → **Entrar com a Apple** → **Compartilhar meu e-mail** → confirme | Entra **sem erro** (nada de "erro 1000" nem "Entrar com a Apple não está disponível"). Se o seu Apple ID tiver o mesmo e-mail de uma conta do jogo, cai **nela**; senão cria conta nova com o nome da Apple | qual dos dois ocorreu (identidade `apple` em conta antiga ou conta nova) | |
| APL-05 | Mesmo passo do APL-01 (não é um passo a mais) | **Só vale** se o e-mail do seu Apple ID for o mesmo e-mail Google de uma conta com carreira: cai na **mesma** carreira e nos mesmos dobrões, sem conta nova. Se o e-mail for outro, o QA marca **N/A** | mesmo `user_id`; `auth.identities` com `apple` a mais | |
| APL-03 | Se o APL-01 criou conta nova: Perfil → cartão de senha | Aparece **"Criar senha"**, não "Trocar senha" | | |
| APL-06 | Feche o app (arraste para cima) e abra de novo | Continua logado, entra direto | | |

Fim da D1: Perfil → Sair da conta. **QA e você zeram a Apple.**

### D2. Apple escondendo o e-mail (relay) e a pergunta do Google

Preparar: convidado, Apple zerada (*Parar de usar o ID Apple* feito e a conta ou identidade Apple apagada pelo QA).

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| APL-14 (cancelar) | **Entrar com a Apple** → **Ocultar meu e-mail** → confirme. Na pergunta **"Já joga Naval Battle com Google?"**, toque **Cancelar** (ou feche) | Nada acontece e **nenhuma conta** é criada | `auth.users` sem linha nova | |
| APL-15 | De novo: **Entrar com a Apple** → **Ocultar meu e-mail** → na pergunta, toque **Não, criar conta nova** | Segue o login da Apple de sempre: entra em conta nova | conta nova com e-mail `@privaterelay.appleid.com` | |
| APL-04 | Perfil → Conta (logo depois) | Aparece **"E-mail oculto pela Apple"**; o cartão de Criar senha mostra o endereço de relay | | |

Agora esta conta (Apple por relay, só Apple) fica como está: é a "conta com Apple" do APL-16 e do APL-13.
**Não zere a Apple aqui.** Perfil → Sair da conta.

### D3. Apple que já tem conta, vinculada em outra (APL-16)

Preparar: o QA confirma a QA03 sem conta (já está assim em 04/11) e o app como convidado.

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| APL-16 | 1. **Entrar com o Google** na **QA03**. 2. Perfil → **Vincular Apple ID** → confirme na Apple | Aparece a orientação "Este login já tem outra conta no jogo..."; nada é alterado nas duas contas | as duas contas iguais; Apple continua só na de relay | |

### D4. Vincular o Google à conta aberta pela Apple (APL-13) e a pergunta com "Sim" (APL-14)

Preparar: o QA apaga a QA03 de novo, se ela já tiver sido criada (para o Google dela ficar livre). Perfil → Sair da conta.

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| APL-13 | 1. **Entrar com a Apple** (cai na conta de relay do D2). 2. Perfil → **Vincular Google** → escolha a **QA03**. 3. Sair da conta. 4. **Entrar com o Google** na QA03 | Linha **"Apple · Google"** (em maiúsculas); entrar pelo Google abre a **mesma** carreira | um `user_id` com `apple` e `google` | |

Antes do APL-14 (sim): **zerar a Apple** de novo (o QA apaga a conta de relay e você faz *Parar de usar o ID Apple*) e apagar a QA03.

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| APL-14 (sim) | Convidado: **Entrar com a Apple** → **Ocultar meu e-mail** → na pergunta, toque **Sim, entrar com o Google** e entre com a **QA03** (nova) | Entra com o Google e a Apple fica vinculada à **mesma** carreira; nenhuma conta nova vazia | identidade `apple` na QA03, sem conta nova vazia | |

Agora a QA03 tem Google e Apple: é a conta dos próximos dois casos (excluir). **Não zere nada ainda.**

### D5. Excluir conta (APL-09 e APL-07), só na QA03

Preparar: a QA03 do passo anterior (Google e Apple). **Nunca** em conta real.

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| APL-09 | Perfil → **Excluir conta** → confirmar → na janela da Apple, **cancele** | Nada é apagado; aparece o aviso de que é preciso confirmar com a Apple | conta ainda existe | |
| APL-07 | Perfil → **Excluir conta** → confirmar → janela da Apple → **confirme** | Conta apagada. **Enquanto a Edge `apple-revoke` não estiver implantada**, o app continua na lista de Ajustes → ID Apple → Entrar com a Apple (esperado hoje, **não é falha**). Com a Edge implantada, ele some da lista | `auth.users` sem a QA03; log da Edge, se implantada | |

### D6. Vincular a Apple a uma conta Google (APL-12)

Preparar: **zerar a Apple** (você faz *Parar de usar o ID Apple*). Convidado.

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| APL-12 | 1. **Entrar com o Google** na **QA03** (conta nova). 2. Perfil: "Logins da conta: GOOGLE". 3. **Vincular Apple ID** → confirme na Apple | Mensagem de vínculo feito; a linha passa a **"GOOGLE · APPLE"**; o botão **Vincular Apple ID** some. Se aparecer "Vincular logins não está disponível agora", o Allow manual linking está desligado (avise o QA: bloqueia APL-12, 13, 14 sim e 16) | mesmo `user_id` com identidade `apple` a mais | |

### D7. Boas-vindas e nome repetido na QA03 virgem (WEL-05, NOM-01)

Preparar: o QA apaga a QA03 e confirma que, no login, `is_test = false`. O QA passa **um nome de guerra que já existe** (para o
NOM-01). Aparelho **como convidado limpo**: apague o app e reinstale pelo TestFlight (o iPhone não tem "limpar dados"), para a
guiada voltar a aparecer.

| ID | Passos (você) | Esperado | QA confere | Resultado |
|---|---|---|---|---|
| WEL-05 | 1. Menu → **Primeira missão** → **Pular**. 2. Leia o popup. 3. **Entrar com o Google** na **QA03 nova**. 4. Se o presente não chegar, feche e abra o app | O popup do convidado diz "Crie sua conta para receber ◆150 + 5 milhas (conta nova)" (sem `%s` nem `%1`). Ao entrar, o presente de **150 dobrões + 5 milhas** chega (pode chegar só ao reabrir o app: foi o que se viu no Android, **bug P2 #72**; anote qual dos dois ocorreu) | uma linha em `welcome_gifts`; saldo +150; milhas +5 | |
| NOM-01 | Perfil de convidado: escolha o **nome repetido** do QA, jogue 2 partidas contra a IA, **depois** entre com a QA03 (o QA zera a QA03 entre o WEL-05 e este) e jogue mais 1 partida; espere 1 min | Aparece "carreira enviada" (ou equivalente); o nome vira `nome-xxxx` ou o da conta; o jogo **não** mostra erro | `profiles.username` único; `xp` e `matches` subiram | |

## E. Push no iPhone (APNs) · issue #14

Só vale pelo TestFlight, com a **chave APNs atual** (a Apple já a aceitou; ver tabela da seção 0). **O token do iPhone é registrado com ou sem permissão de aviso** (a permissão só decide se o aviso aparece). A permissão é pedida **ao chegar ao menu**, a mesma do "Lembrete diário".

Preparar: iPhone logado na conta **principal**. O QA usa o emulador QA01 (o primeiro caso, 02a, já deixa vocês amigos).

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| PSH-IOS-01 | Conta principal logada, notificações ainda não decididas | 1. Abra o app e chegue ao menu. 2. Quando pedir permissão de aviso, toque **Permitir**. 3. Avise o QA. | A permissão é pedida **uma vez**; o QA vê uma linha em `push_tokens` com plataforma `ios` (token de 64 ou mais caracteres hexadecimais) | |
| PSH-IOS-02a | Permissão concedida; app **fechado** (arraste para cima) | O QA manda, pelo QA01, um **pedido de amizade** para você; espere até 1 min | Chega o aviso "Pedido de amizade" (texto igual ao do Android). Depois aceite o pedido (assim vocês ficam amigos para os próximos) | |
| PSH-IOS-02b | Idem, app fechado | O QA manda um **convite de partida** pelo QA01; espere até 1 min | Chega o aviso de convite; o resultado em `push_outbox` traz `ios:200` | |
| PSH-IOS-04 | Amigos (QA01) | O QA dispara "amigo online"; toque no aviso com o app fechado, e de novo com o app em segundo plano | Abre na tela de **Amigos** (os outros avisos só abrem o jogo) | |
| PSH-IOS-03 | Ajustes → **Receber convites de amigos** desligado | O QA manda outro convite | **Nenhum** aviso no iPhone | |
| PSH-IOS-05 | App fechado, iPhone registrado | O QA envia o aviso `daily_invite` **só para a sua conta** (envio individual; nunca o broadcast das 12h, que vai para todos) | O iPhone recebe; tocar no aviso só abre o jogo | |
| PSH-IOS-06 | Permissão **negada** em Ajustes do iPhone → Naval Battle → Notificações | 1. Negue o aviso. 2. Feche e abra o jogo 3 vezes. | Não pede de novo; o jogo segue sem erro; o token continua registrado (só o aviso não aparece) | |
| PSH-IOS-07 | Só servidor | O QA insere um token iOS falso e envia | O token falso fica marcado como não registrado; o aparelho real recebe; `new_version` nunca vai ao iPhone | QA |
| LAUNCH-07 | Só servidor | O QA roda `launch_notice_status` para ios com o interruptor ligado **dentro de um bloco que desfaz** | `enabled = false` para iPhone, com qualquer texto. **Nada é ligado de verdade e você não testa nada no iPhone** | QA |

Fim do grupo E: o QA apaga a amizade com o QA01 e os tokens de teste.

## F. Loja de dobrões · issue #62 (e #65)

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| DOBIOS-01 | Conta que **não** é testador: a **QA03** (o QA confere `beta_eligible` dela) | 1. Loja → aba **Dobrões**. 2. Espere 10 s. | Mostra **"A compra de dobrões chega em breve."**. **Nunca** em branco nem preso em "Abrindo o cofre…" | |
| DOBIOS-02 | Conta **principal** (testador) com o Prêmio Beta ligado (`beta_store_enabled` já está `true`; o QA confere `beta_eligible` da conta) | 1. Aba Dobrões. 2. Toque para pegar o **Prêmio Beta**. 3. Toque de novo no mesmo dia. | Só o cartão do Prêmio (nenhum pacote pago); ao pegar, aviso **"+N"** e o saldo sobe; de novo no mesmo dia mostra o retorno à meia-noite | |
| DOBIOS-03 | Qualquer conta | 1. **Modo avião**. 2. Aba Dobrões. 3. Desligue o modo avião. 4. **Tentar de novo**. | "Não consegui abrir o cofre…" com **Tentar de novo**; com a rede, carrega | |

**#65 (StoreKit, compras reais) não tem caso aqui:** continua na Fila e **não há compra no iPhone nesta versão**.
Pré-requisitos antes de testar: aprovar a stack (StoreKit 2 + App Store Server API); Paid Apps Agreement, impostos e
conta bancária no App Store Connect; chave In-App Purchase (.p8) no Supabase; 4 consumíveis iguais aos da Play
(Bolsa, Baú, Cofre, Tesouro); sandbox tester. Quando houver build, este arquivo ganha um grupo novo.

## G. Anúncios · issue #40

No iPhone **não há anúncio premiado** (`RewardedAds.ios.kt` devolve "sem anúncio"; o servidor também não libera
para iOS). O caso é só garantir que **nada aparece**.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| ADS-18 | Conta principal. O QA zera as suas milhas no servidor antes (você **não** precisa jogar online até 0) | 1. Vença uma partida contra a IA e olhe os botões da tela de resultado. 2. Abra Jogar online e o aviso de milhas (com 0). 3. Olhe **Ajustes**. | **Nenhum** "DOBRAR DOBRÕES" nem "GANHAR 1 MILHA"; nada de anúncio; em Ajustes **não** existe "Privacidade dos anúncios". O QA devolve as milhas depois | |

O restante da #40 (ADS-02 a ADS-26) é só Android e fica com o QA nos emuladores.

## H. Aviso de atualização · issue #46

O iPhone não consulta loja (a função devolve nulo): o aviso vem só de `app_releases` com `ios_live = true`, versão
**maior** que a instalada. `ios_live` só é marcado quando a **App Store** já oferece a versão (não o TestFlight). Só há como
testar quando o Release publicar uma versão **depois da instalada** e ela estiver na App Store. **Não** dá para forçar com linha falsa (todos os testadores do iPhone veriam o popup). Esta rodada:
instale a 0.43.4; o grupo H fica para a **próxima** versão.

| ID | Preparar | Passos | Esperado | Resultado |
|---|---|---|---|---|
| UPD-IOS-00 | Só se ainda estiver na **0.42.2 (8)**, antes de atualizar | Abra o app no menu | Informativo: a 0.42.2 não tem o recurso da #46; o popup pode ou não aparecer. **Não vale como UPD-10 a 12** | |
| UPD-10 | 0.43.4 ou maior instalada. O Release publica a versão seguinte e marca `ios_live` | 1. Deixe o jogo aberto no menu e vá à tela inicial do iPhone. 2. Espere o aviso do QA de que a nova versão saiu. 3. Volte ao jogo. | O popup de atualização aparece **sem reabrir o app** (consulta no máximo a cada 5 min; espere se não aparecer) | |
| UPD-11 | Depois do UPD-10 | 1. Toque **Depois**. 2. Vá à tela inicial e volte. | Não reaparece para a mesma versão; só com outra versão maior | |
| UPD-12 | Versão nova disponível | 1. Comece uma partida contra a IA. 2. Vá à tela inicial e volte. | O aviso **não** abre durante a partida; só ao voltar ao menu | |
| UPD-IOS-13 | Popup aberto | Leia a dica e toque o botão principal ("Atualizar na loja") | A dica diz **"Atualize pela App Store."** (sem ".ipa", TestFlight nem site). No iPhone real o botão abre a página do jogo na App Store; no simulador (sem loja) abre `https://apps.apple.com/app/id6827258132` no Safari. Anote para onde levou | |

---

## Ordem e tempo

1. Seção 0 e A: 10 min. 2. B e C: 15 min. 3. **D: 45 a 60 min com o QA conectado** (a parte mais longa).
4. E (push): 20 min. 5. F e G: 15 min. 6. H: fica para a próxima versão.

## Quando tudo passar, estes cartões mudam

| Cartão | Casos que precisam passar | Estado do cartão hoje | Depois de passar |
|---|---|---|---|
| #56 iPhone: TestFlight | INST-IOS-01 a 03 | Liberado para testes | **Em produção** |
| #13 Entrar com a Apple | APL-01 a 04, 06, 14, 15 (APL-05 se o e-mail bater; APL-07 e 09 na QA03) | Em produção | Fica; o QA registra "validado no iPhone" no cartão |
| #63 Vincular Apple ID e Google | APL-12, 13, 16 | Liberado para testes | **Em produção** |
| #61 Nome de guerra após login Google | NOM-02 (e AUTH-IOS-01 e 02) | Liberado para testes | **Em produção** |
| #59 Nome repetido na fusão | NOM-01 | Liberado para testes | **Em produção** |
| #43 Boas-vindas | WEL-05 | Em produção | Fica; "validado no iPhone" |
| #14 Push no iPhone | PSH-IOS-01 a 06 (07 e LAUNCH-07: QA) | Em produção | Fica; "validado no iPhone" |
| #62 Aba Dobrões no iPhone | DOBIOS-01 a 03 | Liberado para testes | **Em produção** |
| #40 Anúncios para todos | ADS-18 (iPhone); o resto no Android | Liberado para testes | Só o lado do iPhone fecha aqui; o cartão espera o QA do Android |
| #46 Atualização ao voltar | UPD-10 a 12 (Android pela Play e iPhone) | Liberado para testes | **Em produção** (o Android pode ir antes: a 0.43.4 já está na Play) |
| #65 StoreKit | nenhum caso | Fila | Não entra: sem build |

Regra: o cartão só vai para Em produção se **todos** os casos dele estiverem "Passou" ou com falha já explicada como bug
conhecido (#72, #70 e #71) que o PO aceitou. Falha nova vira bug na Fila. Caso só de iPhone que o John não conseguir
rodar é registrado como "não testado" e **não segura** o cartão.
