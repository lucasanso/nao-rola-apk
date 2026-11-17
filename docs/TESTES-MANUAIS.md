# Roteiro de testes manuais no aparelho

Aparelho principal: Galaxy S26. Anote para cada rodada a versão do Android/One UI, do YouTube, do Instagram e do Não-Rola (*Ajustes › Sobre*).

**Antes de começar:** em *Ajustes › Diagnóstico*, deixe o log limpo. Se algum cenário falhar, faça uma captura da tela problemática (*Capturar próxima tela*) e guarde-a junto com o ID do cenário.

Legenda do resultado: ✅ passou · ❌ falhou · ⚠️ parcial (descreva).

## 1. Instalação e permissões

| ID | Cenário | Passos | Esperado | Resultado |
|---|---|---|---|---|
| P1 | Instalação | `adb install -r release/NaoRola-0.5.0.apk` | App aparece com o ícone do Não-Rola | |
| P1b | Atualização | Com uma versão anterior instalada e usada, instalar a 0.4.0 por cima | Configurações e contagem de hoje mantidas | |
| P2 | Estado inicial | Abrir o app | Painel mostra "Proteção desligada" e o botão Resolver | |
| P3 | Divulgação | Resolver › Abrir configurações de acessibilidade | Aviso "Antes de ativar" aparece antes das configurações do Android | |
| P4 | Ativar serviço | *Acessibilidade › Apps instalados › Não-Rola* › ativar | Volte ao app: "Proteção ativa" | |
| P5 | Configuração restrita | Se P4 for bloqueado | *Apps › Não-Rola › ⋮ › Permitir configurações restritas* resolve | |
| P6 | Dados de uso | Permitir acesso aos dados de uso | Painel passa a mostrar o tempo no app | |
| P7 | Proteção Avançada | Só se usar: ligar a Proteção Avançada | Painel mostra "Bloqueado pela Proteção Avançada" | |

## 2. YouTube

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| Y1 | Tocar na aba Shorts | Tela de bloqueio em até ~1 s | |
| Y2 | Abrir um Short a partir da Início | Bloqueio | |
| Y3 | Abrir um Short por link (WhatsApp) | Bloqueio | |
| Y4 | Vídeo comum (horizontal) | **Nada bloqueado** | |
| Y5 | Vídeo comum vertical (não Short) | **Nada bloqueado** | |
| Y6 | Vídeo comum em tela cheia | **Nada bloqueado** | |
| Y7 | Rolar a Início com a prateleira de Shorts | Sem tela de bloqueio; a prateleira aparece coberta ("Shorts oculto") — veja a seção 9 | |
| Y8 | Mini player aberto e ir para Shorts | Bloqueio | |
| Y9 | Busca, inscrições, biblioteca, comentários | **Nada bloqueado** | |

## 3. Instagram

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| I1 | Aba Reels | Bloqueio | |
| I2 | Reel aberto do feed, do Explorar e de um perfil | Bloqueio | |
| I3 | Stories (próprios e de outros) | **Nunca bloqueado** | |
| I4 | Feed de fotos, perfis, mensagens | **Nada bloqueado** | |
| I5 | Reel recebido por DM, com "Liberar Reel recebido por mensagem" ligado | O Reel aberto toca; ao passar para o próximo, bloqueio | |
| I6 | Mesmo cenário com a opção desligada | Bloqueio imediato | |
| I6b | Reel de DM com conexão lenta (tela de carregamento antes do Reel) | O Reel aberto continua liberado | |
| I7 | Rolar o feed com um Reel visível | Sem tela de bloqueio no feed; a mídia do Reel aparece coberta ("Reels oculto"); tocar nele abre o visualizador e bloqueia | |

## 4. Tela de bloqueio

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| B1 | "Voltar para o YouTube" | Sai do player de Shorts e a tela de bloqueio some | |
| B2 | "Sair do YouTube" | Vai para a tela inicial | |
| B3 | "Desativar por N minutos" | Shorts liberados pelo tempo escolhido; Painel mostra "Pausado até…"; ao fim, bloqueia de novo | |
| B4 | Gesto de voltar do sistema com o bloqueio aberto | Volta e a tela some | |
| B5 | Gesto de início com o bloqueio aberto | A tela de bloqueio some em menos de ~1 s | |
| B6 | Áudio | Anote se o vídeo continua tocando sob o bloqueio | |
| B7 | Rotação e tela dividida | Bloqueio continua utilizável | |

## 5. Configurações e modos

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| C1 | Desligar YouTube Shorts | Shorts liberados; Painel: "Desprotegido · desligado nos ajustes" | |
| C2 | Tolerância de 10 s | Short toca por ~10 s e então bloqueia | |
| C3 | Desligar "Permitir desativar temporariamente" | Botão de pausa some da tela de bloqueio | |
| C4 | Bloqueio total | Ignora as opções; sem pausa; Reel de DM também bloqueado | |
| C5 | Navegador ligado: `m.youtube.com/shorts/…` no Chrome | Bloqueio | |
| C6 | Navegador ligado: Samsung Internet | Bloqueio (id da barra a confirmar) | |
| C7 | Navegador: `youtube.com/watch?v=…` | **Nada bloqueado** | |
| C8 | Apps só de vídeo curto ligado + TikTok instalado | Abrir o TikTok bloqueia; só há o botão "Sair do app" | |

## 6. Limite diário, horários, Disciplina e estatísticas

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| L1 | Tolerância "Sem limite" + limite diário 5 min | Shorts tocam até somar 5 min no dia, mesmo saindo e voltando; depois, bloqueio imediato até a meia-noite | |
| L2 | Tolerância 30 s + limite diário 1 min | Cada entrada dura no máximo 30 s; após 1 min somado, bloqueio imediato | |
| H1 | Horário só seg–sex 08:00–18:00, testar fora dele | Nada bloqueado; Painel: "Desprotegido · fora do horário" | |
| H2 | Exceção semanal agora | Nada bloqueado durante a exceção | |
| H3 | Pausa programada começando em 2 min por 1 h | Bloqueia antes, libera durante, volta a bloquear depois | |
| D1 | Ativar Disciplina (espera 5 min) | Ajustes de Bloqueio e Horários ficam travados; tela de bloqueio sem "Desativar" | |
| D2 | "Quero sair" | Contagem de 5 min; proteção continua; depois "Confirmar saída" libera | |
| D3 | Desligar o serviço nas configurações do Android durante a Disciplina | Ao reativar, a tela da Disciplina mostra quantas vezes foi desligado | |
| D4 | Disciplina com "Forçar Bloqueio total" | Reel de DM também bloqueado; navegador e apps de vídeo curto protegidos | |
| E1 | Estatísticas após alguns dias de uso | Barras por dia batem com a lista "Dia a dia"; dias antigos sem dados aparecem como "sem dados" | |
| E2 | Estimativa de exposição evitada | Só aparece após 10 sessões medidas; mostra a fórmula | |
| F1 | Se algum Short ou Reel **não** for bloqueado na aba Shorts/Reels: ficar ~5 s na aba e voltar ao Painel | Painel mostra "Possível falha de detecção"; *Abrir Diagnóstico › Capturar próxima tela* e voltar à aba gera a captura que corrige a regra | |
| F2 | Depois de F1, quando a detecção voltar a funcionar (ou tocar em Dispensar) | O aviso some | |

## 7. Persistência e robustez

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| R1 | Fechar o app pelos recentes | Proteção continua funcionando | |
| R2 | Reiniciar o celular | Serviço volta sozinho; configurações mantidas | |
| R3 | Desativar o serviço nas configurações do Android | Painel mostra "Proteção desligada"; nada é bloqueado | |
| R4 | Instalar uma versão nova por cima (mesma chave) | Configurações e histórico mantidos | |
| R5 | Uma hora de uso normal com o serviço ligado | Sem travamentos perceptíveis no YouTube/Instagram; anote o consumo de bateria do Não-Rola | |
| R6 | Contadores | "Hoje" soma os bloqueios; virada do dia zera | |

## 8. Aparência

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| A1 | Tema claro e escuro do sistema | App e tela de bloqueio acompanham | |
| A2 | *Ajustes › Cores do sistema* | Usa as cores do papel de parede | |
| A3 | Fonte grande (Acessibilidade › Tamanho da fonte) | Textos legíveis, nada cortado | |

## 9. Capas sobre feeds (0.4.0)

Se algo não for coberto (ou for coberto por engano), faça uma captura de diagnóstico da tela e anote o ID.

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| FC1 | YouTube: Início com a prateleira de Shorts | Prateleira coberta com "Shorts oculto"; vídeos comuns visíveis | |
| FC2 | YouTube: rolar a Início devagar e rápido | A capa acompanha a prateleira; anote se algo aparece por um instante | |
| FC3 | YouTube: aba Shorts na barra inferior | Botão coberto; tocar nele ainda abre Shorts e bloqueia | |
| FC4 | YouTube: Shorts nos resultados de busca e na página de um vídeo | Cobertos | |
| FC5 | Instagram: feed com Reel e carrossel de Reels | Mídia do Reel e carrossel cobertos; fotos visíveis | |
| FC6 | Instagram: aba Reels na barra inferior | Botão coberto | |
| FC7 | Instagram: Reel recebido numa conversa | **Visível** na conversa | |
| FC8 | Com capas na tela: tela inicial, recentes, outro app, cortina de notificações | Capas somem em menos de ~0,5 s | |
| FC9 | Com capas na tela: abrir o teclado (busca) | Nada desenhado sobre o teclado | |
| FC10 | Desligar "Esconder Shorts e Reels nos feeds" | Nenhuma capa de Shorts/Reels; o bloqueio do player continua | |
| FC11 | Pausa temporária ativa | Sem capas de Shorts/Reels (anúncios continuam cobertos) | |
| FC12 | Bloqueio total com a chave de feeds desligada | Capas aparecem mesmo assim | |
| FC14 | Ligar "Esconder a Início do YouTube" e abrir a Início | Feed coberto com "Início oculto"; busca, Inscrições e Você normais | |
| FC15 | Buscar algo a partir da Início | Resultados da busca **visíveis** | |
| FC16 | Pausa ativa com "Seguir horários e pausas" ligado / desligado | Início aparece / continua escondida | |
| FC13 | Uso normal por 30 min com capas | Sem travamentos perceptíveis; anote a bateria | |

## 10. Anúncios (0.4.0)

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| AD1 | YouTube: vídeo com anúncio pulável | Ao aparecer o botão "Pular", o anúncio é pulado sozinho (log: "Anúncio pulado") | |
| AD2 | YouTube: anúncio não pulável | Continua tocando (limitação conhecida) | |
| AD3 | YouTube: card "Patrocinado" na Início | Coberto com "Anúncio oculto" | |
| AD4 | Instagram: post patrocinado no feed | Cabeçalho, mídia e botões cobertos | |
| AD5 | Instagram: story patrocinado | Tela coberta; tocar passa para o próximo story | |
| AD6 | Desligar *Ajustes › Anúncios › Bloquear anúncios* | Nada coberto nem pulado por causa de anúncio | |
| SB1 | Samsung Internet › menu › Bloqueadores de anúncios | Não-Rola aparece na lista e pode ser ativado | |
| SB2 | Com o bloqueador ativo, abrir um site de notícias com anúncios | Menos anúncios; o site continua funcionando | |
| SB3 | Importar um arquivo hosts ou lista AdBlock | Mensagem com o número de regras; o Samsung Internet passa a usar a lista | |
| SB4 | Remover a lista importada | Volta à lista do Não-Rola | |
| SB5 | Tocar em "Configurações" do Não-Rola dentro do Samsung Internet | Abre a tela Anúncios do Não-Rola | |
| SB6 | Desligar o serviço de acessibilidade | O bloqueador do Samsung Internet continua funcionando | |

## 11. Apps de banco

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| N1 | Abrir o Nubank com o serviço ligado | Anote se o Nubank recusa (esperado: recusa) | |
| N2 | Painel › Desligar para usar o banco › abrir o Nubank | Anote se o Nubank abre normalmente | |
| N3 | Bloco "Não-Rola" nas Configurações rápidas | Um toque desliga; outro toque abre a tela para religar | |
| N4 | Painel › Religar proteção | Proteção ativa de novo | |

## 12. Filtro adulto (0.5.0)

| ID | Cenário | Esperado | Resultado |
|---|---|---|---|
| X1 | Ligar o filtro e abrir um site da lista no Chrome | Tela de bloqueio "Este site foi bloqueado pelo filtro adulto", sem botão de pausa | |
| X2 | Mesmo teste no modo anônimo do Chrome | Bloqueio | |
| X3 | Samsung Internet, Firefox, Edge, Brave, Opera, DuckDuckGo (os que tiver) | Bloqueio; se algum não bloquear, captura de diagnóstico dele | |
| X4 | Link de site adulto aberto pelo WhatsApp | Bloqueio ao abrir no navegador | |
| X5 | Buscar "pornô" no Google | Bloqueio da busca | |
| X6 | Buscar "sexo seguro", "pelada de futebol", "Essex" | **Nada bloqueado** | |
| X7 | Desligar "Bloquear buscas explícitas" | X5 não bloqueia; X1 continua bloqueando | |
| X8 | Samsung Internet com o bloqueador do Não-Rola ativo | Site da lista nem carrega | |
| X9 | Rodar o comando adb da tela e tocar em "Usar este DNS" | Configurações › DNS privado mostra o provedor; site adulto não abre nem dentro do Instagram | |
| X10 | "Voltar ao automático" | DNS privado volta a "Automático" | |
| X11 | Painel e Estatísticas após bloqueios do filtro | Painel sem menção; Estatísticas com a linha "Filtro adulto" | |
| X12 | Modo Disciplina ativo | Ajustes do filtro travados | |

