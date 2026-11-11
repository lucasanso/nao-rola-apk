# Não-Rola — Fase 1: Viabilidade técnica

> Análise feita em 10/11/2026, antes de qualquer código. Objetivo: dizer com honestidade o que dá e o que não dá para fazer com `AccessibilityService` num Android moderno (Galaxy S26), quais detecções são confiáveis, quais são aproximações e quais riscos podem inutilizar o app.

---

## 0. Veredito

**É viável, com ressalvas importantes.**

| Pergunta | Resposta curta |
|---|---|
| Dá para detectar o **player de Shorts** (tela cheia, rolagem vertical) e o **visualizador de Reels**? | **Sim**, com boa confiabilidade, *enquanto* os identificadores atuais da interface forem válidos. |
| Dá para fazer isso sem bloquear vídeos normais, Stories, DMs, feed e perfis? | **Sim**, desde que cada app tenha regras próprias e regras *negativas* explícitas (ex.: Stories). |
| Dá para **remover** a prateleira de Shorts do feed ou os Reels sugeridos no feed? | **Não.** Um app não altera a interface de outro. Só daria para *cobrir* pedaços da tela com sobreposições que acompanham a rolagem — frágil e ruim de usar. Recomendo não fazer no MVP. |
| Dá para mostrar uma tela de bloqueio por cima? | **Sim**, com `TYPE_ACCESSIBILITY_OVERLAY`, sem a permissão "sobrepor a outros apps". |
| Dá para **pausar** o vídeo por baixo da tela de bloqueio? | **Só de forma aproximada.** O caminho confiável é tirar o usuário do player (ação "voltar"). |
| Dá para **fechar** o YouTube/Instagram? | **Não.** Nenhum app comum pode encerrar outro. O equivalente honesto é ir para a tela inicial ("Sair do YouTube"). |
| Dá para medir o **tempo em vídeos curtos**? | **Só pelo próprio detector**, enquanto o serviço está ativo. O Android não fornece esse dado. Tempo **total no app** sim, via `UsageStatsManager`. |
| O app vai funcionar para sempre sem manutenção? | **Não.** Atualizações do YouTube/Instagram vão quebrar detectores de tempos em tempos. Isso é estrutural, não um defeito corrigível. |

Há quatro **condições externas** que podem impedir o funcionamento independentemente da qualidade do código (detalhes na seção 8):

1. **Configurações restritas** (Android 13+): APK instalado fora de loja precisa de liberação manual antes de ativar a acessibilidade.
2. **Verificação de desenvolvedor** (no Brasil desde 30/09/2026): instalar APK de desenvolvedor não registrado exige `adb`, o "fluxo avançado" (espera de 24 h) ou uma conta gratuita de distribuição limitada.
3. **Proteção Avançada** (Android 17 / One UI 9): se ativada, só ferramentas de acessibilidade reais podem usar o serviço. O Não-Rola **não é** uma ferramenta de acessibilidade e não vai fingir ser → fica incompatível com esse modo.
4. **`accessibilityDataSensitive`**: YouTube ou Instagram podem, se quiserem, esconder views de serviços que não são ferramentas de acessibilidade. Hoje não há indício de que façam isso nos players, mas é possível.

---

## 1. Dispositivo-alvo

- **Galaxy S26**: lançado com One UI 8.5 (Android 16). A One UI 9 (Android 17) começou a ser distribuída em setembro de 2026 (Coreia em 16/09; Europa, Índia e EUA no fim de setembro). **Confirme no aparelho** em *Configurações › Sobre o telefone › Informações de software*.
- O projeto vai mirar Android 16 e 17, com `minSdk` mais baixo apenas se não custar complexidade (definição na Fase 2).

---

## 2. O que o `AccessibilityService` realmente oferece

Somente APIs que existem de fato:

| Capacidade | API | Uso no Não-Rola |
|---|---|---|
| Receber eventos de outros apps | `onAccessibilityEvent` com `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_SCROLLED`, `TYPE_VIEW_CLICKED`, `TYPE_VIEW_SELECTED`, `TYPE_WINDOWS_CHANGED` | Gatilhos para reavaliar a tela |
| Restringir a apps específicos | `android:packageNames` no XML do serviço | Só YouTube e Instagram (e futuros) — menos eventos, menos bateria, menos dados vistos |
| Ler a árvore da interface | `getRootInActiveWindow()`, `getWindows()` (com `flagRetrieveInteractiveWindows`) | Ler a estrutura da tela atual |
| Ler identificadores de recurso | `AccessibilityNodeInfo.getViewIdResourceName()` (exige `flagReportViewIds`) | Sinal principal de detecção |
| Busca rápida por ID | `findAccessibilityNodeInfosByViewId("pacote:id/nome")` | Caminho rápido antes de percorrer a árvore inteira |
| Estrutura e geometria | `className`, `isScrollable`, `getBoundsInScreen`, `collectionInfo`, `isSelected`, `paneTitle` | Sinais independentes de ID (ex.: "pager vertical em tela cheia") |
| Texto e descrição | `text`, `contentDescription` | Só como sinal **fraco** (são traduzidos e mudam); **nunca armazenados** |
| Ações globais | `performGlobalAction(GLOBAL_ACTION_BACK / GLOBAL_ACTION_HOME)` | Sair do player / ir para a tela inicial |
| Desenhar por cima | `WindowManager` com `TYPE_ACCESSIBILITY_OVERLAY` | Tela de bloqueio, sem `SYSTEM_ALERT_WINDOW` |

O que **não** existe (e portanto não será simulado):

- Nenhuma API do YouTube ou do Instagram diz "isto é um Short/Reel". Tudo é inferido da interface.
- Não há como **remover ou esconder** elementos dentro de outro app.
- Não há como **encerrar** outro app (`killBackgroundProcesses` só afeta processos em segundo plano; o app em primeiro plano não é afetado).
- Não há como **pausar** a mídia de outro app só com acessibilidade. As alternativas (seção 5) são aproximações ou exigem permissões que não se justificam.
- `takeScreenshot()` (API 30+) existe para serviços de acessibilidade, mas classificar vídeo por imagem seria pesado, lento e invasivo. **Descartado.**

---

## 3. Como YouTube e Instagram expõem a interface

### 3.1 YouTube (`com.google.android.youtube`)

- **Uma única Activity** (`WatchWhileActivity`) hospeda home, Shorts, inscrições e o player convencional. O nome da classe em `TYPE_WINDOW_STATE_CHANGED` **não** distingue Shorts de vídeo normal na maior parte dos fluxos.
- Boa parte da interface é desenhada por um framework próprio (Litho/"Elements"): muitos nós são `ViewGroup` genéricos **sem ID**, só com `contentDescription` traduzida. Isso afeta principalmente **feed, busca e prateleiras**.
- O **player de Shorts**, por outro lado, historicamente usa views com IDs de recurso prefixados por `reel_` (nome interno de Shorts). Candidatos conhecidos: `reel_recycler`, `reel_player_page_container`, `reel_watch_player`. **Não verificados na versão atual** — precisam ser confirmados no S26 (seção 6).
- A aba inferior "Shorts" é um nó com descrição "Shorts" (nome de marca, não traduzido) e estado `selected` quando ativa.

### 3.2 Instagram (`com.instagram.android`)

- **Armadilha de nomenclatura:** no Instagram, **Reels = `clips`** e **Stories = `reel`** internamente (o visualizador de Stories usa IDs `reel_viewer_*`). No YouTube, `reel` = **Shorts**. Uma regra genérica "contém `reel`" bloquearia **Stories** no Instagram. Por isso **cada app tem seu próprio detector** e nenhuma regra de texto é compartilhada.
- IDs confirmados em código aberto recente ([DisableDistractions](https://github.com/fischman/DisableDistractions)): `com.instagram.android:id/clips_tab` (aba Reels), `search_tab`, `profile_tab`, `clips_header_title` ("Reels sugeridos" no feed).
- Candidatos para o visualizador de Reels: IDs `clips_viewer_*` / `clips_video_container`. **A confirmar no aparelho.**
- Desenvolvedores de apps semelhantes relatam que o Instagram **muda IDs com frequência**. Projetos que usam limites em pixels fixos (ex.: `bounds.top < 300`, visto no FeedLock) quebram em telas diferentes — anti-padrão a evitar.

---

## 4. Matriz de viabilidade por cenário

Confiabilidade "Alta\*" = alta **enquanto** os identificadores atuais valerem; cai para zero após uma mudança de interface até as regras serem atualizadas.

### YouTube

| Cenário | Detectável? | Confiabilidade | Sinais | Recomendação |
|---|---|---|---|---|
| Player de Shorts (aba Shorts ou vindo de miniatura) | Sim | Alta\* | IDs `reel_*` + pager vertical em tela cheia | **Bloquear (MVP)** |
| Shorts aberto por link externo (WhatsApp etc.) | Sim | Alta\* | Mesmo player | **Bloquear (MVP)** |
| Rolar de um Short para o próximo | Sim | Alta\* | `TYPE_VIEW_SCROLLED` no pager | Contar como tentativa |
| Toque na aba "Shorts" | Sim | Média-alta | `TYPE_VIEW_CLICKED`/`SELECTED` com descrição "Shorts" | Sinal auxiliar, nunca único |
| Prateleira de Shorts no feed/busca | Parcial | Média-baixa | Nós sem ID, texto traduzido | **Não bloquear no MVP** |
| Vídeo convencional (inclusive vertical) | — | Alta | Player convencional presente | **Nunca bloquear** (regra negativa) |
| Shorts no navegador (`youtube.com/shorts/…`) | Sim, pela barra de endereço | Média | Texto da URL no Chrome/Samsung Internet | Opcional, Fase 4 |
| Janela pop-up / tela dividida / PiP da Samsung | Parcial | Média | `getWindows()` em vez da janela ativa | Tratar na Fase 4 |

### Instagram

| Cenário | Detectável? | Confiabilidade | Sinais | Recomendação |
|---|---|---|---|---|
| Aba Reels | Sim | Alta\* | `clips_tab` selecionada + visualizador `clips_*` | **Bloquear (MVP)** |
| Reel aberto do Explorar, perfil ou feed (visualizador em tela cheia) | Sim | Alta\* | Visualizador `clips_*` + pager vertical | **Bloquear (MVP)** |
| Rolar de um Reel para o próximo | Sim | Alta\* | `TYPE_VIEW_SCROLLED` no pager | Contar como tentativa |
| Reel tocando dentro do feed principal | Parcial | Média | Item de feed com vídeo | **Não bloquear** (faz parte do feed — decisão sua, seção 10) |
| Reel recebido por DM | Sim (o visualizador) | Média para saber a origem | Tela anterior = conversa | Configurável na Fase 4: liberar o Reel aberto, bloquear a rolagem |
| Stories | — | Alta | `reel_viewer_*` | **Nunca bloquear** (regra negativa explícita) |
| Feed de fotos, DMs, perfis | — | Alta | Ausência do visualizador | **Nunca bloquear** |

### Outros formatos

| Cenário | Detectável? | Confiabilidade | Recomendação |
|---|---|---|---|
| Apps que são só vídeo curto (TikTok, Kwai…) | Sim, pelo nome do pacote | Muito alta | Bom candidato para "outros formatos": bloqueio do app inteiro, opcional |
| Facebook Reels, YouTube Music "Samples" | Provável | Desconhecida | Investigar depois do MVP |
| Variantes modificadas (ReVanced, Instagram Lite) | Pacotes e estrutura diferentes | Desconhecida | Fora do escopo inicial |

---

## 5. Bloqueio: o que é possível

**Tela de bloqueio.** Uma janela `TYPE_ACCESSIBILITY_OVERLAY` em tela cheia, que consome os toques, é viável e não exige permissão extra. Ela fica acima do app, mas abaixo da barra de status, da gaveta de notificações e dos gestos do sistema: o usuário sempre consegue sair. Isso é desejável.

**O vídeo continua tocando por baixo.** O overlay não pausa nada. Opções:

| Opção | Funciona? | Decisão |
|---|---|---|
| `GLOBAL_ACTION_BACK` para sair do player | Sim. Sair do player interrompe a reprodução. | **Usar** |
| Pedir foco de áudio transitório (`AUDIOFOCUS_GAIN_TRANSIENT`) enquanto o overlay aparece | Depende de como cada app reage à perda de foco — validar no aparelho | Usar como complemento, se funcionar |
| `MediaController` via `NotificationListenerService` | Funcionaria, mas é outra permissão restrita, só para pausar | **Descartado** |

**Botões da tela de bloqueio, de forma honesta:**

| Pedido original | Implementação real |
|---|---|
| "Voltar para o YouTube" | `GLOBAL_ACTION_BACK` até sair do player (o detector confirma que saiu) |
| "Fechar o aplicativo" | `GLOBAL_ACTION_HOME`. O rótulo será **"Sair do YouTube"**, porque o app não é encerrado |
| "Desativar temporariamente" | Pausa de N minutos guardada no DataStore. Oculto no Modo Disciplina |

**Tempo de tolerância:** viável. Um cronômetro conta o tempo contínuo com Shorts/Reels detectado e só bloqueia depois de N segundos.

**Modo Disciplina:** viável dentro do app (sem desbloqueio pela tela de bloqueio; mudanças de configuração exigem sair do modo pelo app, com atrito). **Limites que não vou esconder:** o usuário pode desativar o serviço em *Configurações › Acessibilidade* ou desinstalar o app. O Não-Rola **não** vai bloquear as Configurações do Android nem usar Administrador do Dispositivo para impedir a desinstalação: isso seria contornar mecanismos de segurança. O que dá para fazer é **registrar** que a proteção foi desligada e mostrar isso no app.

---

## 6. Estratégia de detecção recomendada

1. **Um detector por app** (`YouTubeContentDetector`, `InstagramContentDetector`), atrás de uma interface comum. Cada um transforma a árvore de nós num conjunto de **sinais**.
2. **Sinais de tipos diferentes**, para não depender de um só:
   - **Estruturais (IDs):** presença de IDs conhecidos do player/visualizador.
   - **Geométricos:** contêiner rolável vertical ocupando ~100% da tela, com itens de altura igual à da tela. Usar **frações da tela**, nunca pixels fixos.
   - **De navegação:** aba selecionada (Shorts / Reels).
   - **Negativos:** player convencional (YouTube), visualizador de Stories, conversa de DM (Instagram).
   - **Temporais:** a classificação precisa se manter por alguns milissegundos (histerese) para não piscar durante transições.
3. **`ShortVideoClassifier`** combina os sinais com pesos e um limiar. Qualquer sinal negativo forte cancela o bloqueio. **Na dúvida, não bloqueia.**
4. **Regras como dados:** IDs e pesos ficam num arquivo de regras versionado dentro do APK, não espalhados pelo código. Sem servidor, por exigência do projeto. **Consequência:** quando o YouTube ou o Instagram mudarem a interface, a correção exige um APK novo. Como opção futura, dá para importar um arquivo de regras local.
5. **Modo diagnóstico (local, opcional):** exporta **só a estrutura** da tela (IDs, classes, geometria relativa; **sem textos nem descrições**) para um arquivo que você pode me enviar. Não tenho como rodar YouTube e Instagram neste ambiente, então essa é a forma de calibrar os detectores com dados reais do S26.
6. **Autoverificação:** se a aba Shorts/Reels foi tocada e o detector não confirmou o player em seguida, isso fica registrado como "possível detector desatualizado" e o app avisa.
7. **Desempenho:** durante a reprodução, `TYPE_WINDOW_CONTENT_CHANGED` dispara o tempo todo. Mitigações: `packageNames`, `notificationTimeout`, debounce, caminho rápido por ID antes de percorrer a árvore, limite de nós visitados e nenhuma alocação pesada por evento. O consumo de bateria será medido no aparelho.

---

## 7. Tempo de uso e estatísticas: o que é dado real

| Métrica | Fonte | Precisão | Observação |
|---|---|---|---|
| Tempo total no YouTube/Instagram | `UsageStatsManager.queryEvents` (retomada e pausa de Activity) | Boa | Exige "Acesso aos dados de uso". Sem essa permissão, a métrica aparece como **indisponível** |
| Tempo em Shorts/Reels | Próprio detector, enquanto o serviço está ativo | Aproximada | Rotulada como "tempo com vídeo curto detectado". Inclui tolerância e pausas temporárias. Não existe API do Android para isso |
| Shorts/Reels bloqueados, tentativas | Contadores do próprio app | Exata | "Tentativa" = entrada no player. Rolagens são contadas à parte |
| Histórico de 7 dias | Room (agregados diários) | Exata para o que foi medido | O sistema guarda eventos de uso detalhados por pouco tempo, então os agregados diários precisam ser salvos pelo app |
| Tempo de exposição evitado | Sem fonte direta | **Estimativa** | Proposta: mostrar só quando houver uma base medida com seus próprios dados (duração média de uma sessão de vídeo curto) e com a fórmula visível. Sem base, aparece "dados insuficientes". **Fora do MVP** |

Os números podem diferir um pouco dos do Bem-estar digital da Samsung, que usa outra forma de agregação.

---

## 8. Riscos

| # | Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|---|
| R1 | **Mudança de interface** no YouTube/Instagram quebra os detectores | **Alta** (recorrente) | Alto (o bloqueio para sem aviso) | Sinais múltiplos, regras como dados, modo diagnóstico, autoverificação, APK de correção |
| R2 | **Configurações restritas** (Android 13+; ampliadas no 15) impedem ativar a acessibilidade de APK instalado por arquivo | Certa na instalação por arquivo | Bloqueante até ser liberada | Tentar ativar uma vez, depois *Configurações › Apps › Não-Rola › ⋮ › Permitir configurações restritas*. Instruções na tela de permissões |
| R3 | **Verificação de desenvolvedor** (Brasil, desde 30/09/2026) bloqueia instalar APK de desenvolvedor não registrado | Alta | Bloqueante para instalar | (a) `adb install`, que não é afetado; (b) "fluxo avançado" (espera única de 24 h, em liberação gradual); (c) conta gratuita de distribuição limitada (até 20 aparelhos). **Confirmar no seu aparelho** qual opção está disponível |
| R4 | **Proteção Avançada** (Android 17 / One UI 9) bloqueia serviços que não são ferramentas de acessibilidade | Baixa (é opcional) | Total | O Não-Rola declara `isAccessibilityTool="false"`, que é o correto. Declarar o contrário seria falso, violaria a política e você pediu para não contornar a segurança. O app detecta o modo e explica |
| R5 | YouTube/Instagram marcam views como **`accessibilityDataSensitive`** | Baixa-média | Total para o app afetado | Não há contorno legítimo. Seria comunicado como limitação |
| R6 | **Serviço interrompido** por falha, parada forçada, atualização do app ou economia de bateria da Samsung | Média | Proteção desligada | Nenhuma exceção pode escapar de `onAccessibilityEvent`. O app mostra o estado do serviço e orienta a incluir o Não-Rola em "Apps que nunca entram em suspensão". Nunca supor que o serviço está ativo |
| R7 | Avisos periódicos do sistema ("Não-Rola pode ver e controlar sua tela") | Média | Baixo (atrito) | Esperado. Explicado no app |
| R8 | Bateria/desempenho por excesso de eventos | Média | Médio | Seção 6, item 7, e medição no aparelho |
| R9 | Idioma do sistema muda textos e descrições | Média | Baixo | Texto é só sinal fraco |
| R10 | Testes automatizados não reproduzem YouTube/Instagram reais | Certa | Médio | Testes cobrem a lógica com árvores sintéticas e gravadas (modo diagnóstico). Validação final só no S26, com roteiro manual |
| R11 | Samsung **Auto Blocker** bloqueia instalação de APK e comandos via USB (`adb`) | Média | Bloqueante para instalar | Desativar temporariamente durante a instalação |
| R12 | Política do Google Play para a API de acessibilidade | — | Só se um dia for publicado | Fora do escopo (uso pessoal). Exigiria declaração no Console e divulgação em destaque |

---

## 9. Alternativas avaliadas

| Alternativa | Veredito | Motivo |
|---|---|---|
| `UsageStatsManager` / timers do Bem-estar digital | **Não serve para bloquear Shorts** | Só enxerga o app inteiro (e uma única Activity no YouTube). **Será usado** para as estatísticas de tempo |
| VPN local / filtro DNS | **Inviável** | Shorts e vídeos longos vêm dos mesmos servidores (CDN), com TLS. Bloquear um quebraria o outro |
| Captura de tela + classificação de imagem (MediaProjection / `takeScreenshot`) | **Descartado** | Consentimento por sessão, indicador permanente, bateria, latência e privacidade |
| Apps modificados (ex.: ReVanced) | **Fora do escopo** | Modifica apps de terceiros (termos de uso) e não é o Não-Rola |
| Bloquear o app inteiro | **Só para "outros formatos"** | Confiável, mas impede o uso legítimo que é o objetivo do projeto |
| Administrador do Dispositivo / bloquear Configurações | **Rejeitado** | Contorna mecanismos de segurança do Android |
| **Recursos nativos** | **Complemento recomendado** | YouTube: *Configurações › Gerenciamento de tempo › limite do feed do Shorts* (há relatos de opção de 0 minuto; confirmar no seu app). Instagram: não tem opção oficial para esconder Reels, só limite diário do app inteiro |

---

## 10. Decisões propostas para a Fase 2

| # | Decisão |
|---|---|
| D1 | Bloquear apenas os **players em tela cheia** (Shorts e visualizador de Reels). Prateleiras e Reels dentro do feed ficam para depois e só serão feitos se houver um jeito estável |
| D2 | Bloqueio = overlay + `BACK`. "Sair do app" = `HOME`, com rótulo honesto |
| D3 | Detectores por app, sinais múltiplos, regras negativas, regras como dados, preferência por não bloquear na dúvida |
| D4 | **Modo diagnóstico** (só estrutura, sem texto) faz parte do MVP. Ele é o que permite calibrar no S26 |
| D5 | **Sem permissão `INTERNET`**: o app fica tecnicamente incapaz de transmitir dados. Sem `SYSTEM_ALERT_WINDOW`, sem `QUERY_ALL_PACKAGES` (usar `<queries>` só para os pacotes-alvo) |
| D6 | Tempo no app via `UsageStatsManager` (opcional). Tempo em vídeo curto só pelo detector, com rótulo claro |
| D7 | `isAccessibilityTool="false"`. Incompatibilidade com a Proteção Avançada detectada e explicada |
| D8 | Instalação de desenvolvimento via `adb`. APK final pelo fluxo avançado ou por conta de distribuição limitada |
| D9 | Bibliotecas: só as pedidas (Compose/Material 3, DataStore, Room, Coroutines/Flow). Avaliar na Fase 2 se injeção de dependência (Hilt) se paga num app deste tamanho |
| D10 | **Primeiro incremento da Fase 3:** serviço de acessibilidade mínimo + modo diagnóstico, para coletar árvores reais do S26 **antes** de escrever os detectores definitivos |

Permissões previstas (mínimas):

| Permissão | Obrigatória? | Por quê |
|---|---|---|
| Serviço de acessibilidade | Sim | Única forma de saber o que está na tela de outro app e mostrar o bloqueio |
| Acesso aos dados de uso | Não | Só para mostrar tempo total no YouTube/Instagram |
| Notificações | A decidir na Fase 2 | Avisar que a proteção caiu. Avaliar se compensa |

---

## 11. Perguntas em aberto para você

1. **Reels dentro do feed principal** do Instagram (tocando no meio das fotos): bloquear ou não? *Recomendação: não bloquear.*
2. **Reel recebido por DM**: liberar o Reel aberto e bloquear só a rolagem para o próximo? *Recomendação: sim, como opção na Fase 4.*
3. No S26: qual versão de Android/One UI aparece? A **Proteção Avançada** está ativa? Você consegue usar **depuração USB** (`adb`)?
4. Quer bloquear **Shorts no navegador** (Fase 4)? Isso exige ler a barra de endereço do Chrome/Samsung Internet.

**Respostas (10/11/2026):** (1) **bloquear** os Reels no feed principal, contrariando a recomendação. Isso será feito como função experimental, com as limitações descritas na Fase 2, seção 6.2. (2) **Sim**, e a opção deve ser configurável. (3) Aparelho ainda não disponível. (4) **Sim**, bloquear no navegador. Detalhes em [FASE-2-ARQUITETURA.md](FASE-2-ARQUITETURA.md).

---

## Referências

- Verificação de desenvolvedor: [Android Developers Blog (jun/2026)](https://android-developers.googleblog.com/2026/06/android-developer-verification.html), [Guia oficial](https://developer.android.com/developer-verification/guides), [FAQ](https://developer.android.com/developer-verification/guides/faq), [XDA — fluxo avançado](https://www.xda-developers.com/googles-controversial-advanced-flow-sideloading-rolling-out-ahead-of-stricter-developer-verification/), [Android Authority — fluxo avançado](https://www.androidauthority.com/google-android-advanced-flow-sideloading-rollout-begins-3700073/)
- Proteção Avançada e acessibilidade: [The Hacker News (out/2026)](https://thehackernews.com/2026/10/android-17-advanced-protection-locks.html), [Android Authority — Android 17 Beta 2](https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860/), [Samsung Community — One UI 9](https://eu.community.samsung.com/t5/tips/apps-within-accessibility-services-grayed-out/td-p/14883630)
- `accessibilityDataSensitive`: [Android Developers](https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data)
- Configurações restritas: [Android Authority — Android 13](https://www.androidauthority.com/android-13-sideloading-apps-restrictions-3161162), [9to5Google — Android 15](https://9to5google.com/2024/09/12/android-15-sideloaded-apps-restrictions/)
- One UI 9 no S26: [SamMobile](https://www.sammobile.com/news/galaxy-s26-one-ui-9-stable-wider-rollout/), [Android Authority](https://www.androidauthority.com/one-ui-9-stable-rollout-galaxy-s26-3711819/)
- Apps de código aberto semelhantes: [DisableDistractions](https://github.com/fischman/DisableDistractions), [FeedLock](https://github.com/caasion/FeedLock), [AwayDoomscrollin' (F-Droid)](https://f-droid.org/en/packages/com.resolvecommunity.awaydoomscrollin/)
- Recursos nativos: [Android Authority — limite do Shorts](https://androidauthority.com/youtube-shorts-new-timer-feature-3658096/), [Vaizle — limite de 0 minuto](https://insights.vaizle.com/news/youtube-introduces-zero-minute-limit-for-shorts-feed), [Meta — controles do usuário](https://transparency.meta.com/features/user-control-shape-your-experience-on-meta-platforms/)
