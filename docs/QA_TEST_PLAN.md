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

## 5. Menu e navegação

| ID | P | Passos | Esperado |
|---|---|---|---|
| NAV-01 | P1 | Olhar o menu inteiro | Comandante, cartão do dia, frota animada, Jogar online, vs IA / 2 jogadores / Rede local, atalhos Amigos/Placar/Loja/Feedback — nada sobreposto ou cortado |
| NAV-02 | P1 | Botão voltar em cada tela (Perfil, Ajustes, Loja, Estaleiro, Amigos, Placar, Feedback, Novidades) | Volta para a tela anterior; no menu fecha o app |
| NAV-03 | P1 | Voltar no meio de uma partida | Ignorado (não derruba a batalha) |
| NAV-04 | P2 | Dica de milhas/dobrões | Aparece só na primeira vez |

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
| ON-10 | P1 | Ranqueada (uma vez por bateria) | Exige passe da temporada; relatório com bloco de ranking; pontos aparecem no Placar |
| ON-11 | P1 | Conferir a pontuação da ON-10 no banco (`ranked_season_stats`) contra a fórmula de `record_ranked_result` | Vencedor e perdedor batem com o Elo calculado a partir dos pontos de ANTES da partida, sem depender de quem relatou primeiro |
| ON-12 | P2 | Cabeçalho da batalha nos dois aparelhos | Cada um vê "VS. <nome do outro>" — inclusive quem entrou na sala |
| ON-13 | P1 | Revanche online | Recomeça; milha e pontos (na ranqueada) contam de novo |
| ON-14 | P1 | B encerra a partida ainda no posicionamento | A vai para o relatório ("O adversário desistiu"), não fica preso no posicionamento |
| ON-15 | P1 | Casual e ranqueada: B sai no posicionamento (ou antes do primeiro tiro de A) | A vê "Vitória sem combate"; XP, dobrões e milhas de A não mudam; desafio do dia não avança; na ranqueada os pontos seguem a regra de abandono; nada muda para B (#6) |
| ON-16 | P1 | A dá pelo menos 1 tiro (ou usa 1 habilidade) e B desiste depois | XP, dobrões, milha e desafio do dia pagos como antes (#6) |

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
| LOJA-04 | P1 | Aba Dobrões (compra simulada) | Credita sem cobrança; respeita limite de R$ 50/dia |
| LOJA-05 | P2 | Oferta do dia | Selo "-30%" no atalho; camuflagem no topo com desconto |
| EST-01 | P1 | Estaleiro: equipar casco e camuflagem | Frota do menu e da batalha mudam; prévia desliza pelas 5 classes |
| SEA-01 | P1 | Passe de temporada (gratuito) | Popup da estação pede aceite; aderir libera ranqueada |
| RANK-01 | P1 | Placar | Abas geral, temporada e Troféus; posição própria mesmo fora do topo |

## 13. Ajustes, notificações, feedback e idioma

| ID | P | Passos | Esperado |
|---|---|---|---|
| SET-01 | P1 | Trilha e efeitos | Toggles separados funcionam na hora |
| SET-02 | P1 | Idioma EN e ES | Todas as telas visitadas trocam na hora, sem texto cortado; voltar para PT |
| SET-03 | P1 | Testar notificação | Notificação chega em ~5s com o app em segundo plano |
| SET-04 | P1 | Bloquear notificações no sistema | Ajustes mostra aviso com atalho para o sistema |
| SET-06 | P1 | Ajustes → Online (conta nova ou existente) | "Avisar quando um amigo ficar online" aparece ligado; com "Receber convites" desligado fica inativo (#19) |
| SET-05 | P2 | Sobre → Novidades / Compartilhar | Lista de versões; compartilhar abre a folha do sistema com o link da loja |
| PUSH-01 | P1 | Com o app fechado em B, A faz FR-02/FR-05 | Notificação do sistema aparece em B; tocar abre o jogo |
| PUSH-02 | P2 | Tocar na notificação com o jogo minimizado (não fechado) | Volta para o jogo onde estava, sem passar pela abertura de novo |
| ABL-01 | P1 | Tático: ativar Barragem dupla e acertar o 1º tiro | A barragem continua valendo: um erro depois ainda dá o tiro extra |
| FB-01 | P1 | Feedback (logado) → tocar no **meio** da caixa de texto e digitar | Teclado abre em qualquer ponto da caixa; envio confirma; aparece em `public.feedback` |
| FB-02 | P2 | Feedback deslogado | Botão "Entrar ou criar conta" leva ao Perfil |

## 14. Limpeza (sempre ao fim)

| ID | Passos | Esperado |
|---|---|---|
| CLEAN-01 | Remover amizade A × B (ou manter, se o usuário quiser a dupla pronta) | Lista de amigos limpa |
| CLEAN-02 | Apagar o feedback de teste (FB-01) no Supabase, sem aprovar recompensa | Fila de feedback só com reais |
| CLEAN-03 | Desligar "Disponível para partida rápida" em B | Contas QA não recebem salas de jogadores reais |
| CLEAN-04 | Conferir que nenhuma sala online ficou aberta pelas contas QA | `online_matches` sem sala `waiting` delas |
| CLEAN-05 | Zerar a ranqueada das contas QA: apagar as linhas delas em `ranked_season_stats` e voltar `profiles.ranked_rating/matches/wins` para 1000/0/0 | Contas QA fora do placar público |

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
