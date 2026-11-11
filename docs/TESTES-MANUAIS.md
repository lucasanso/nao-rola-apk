# Roteiro de testes manuais no aparelho

Aparelho principal: Galaxy S26. Anote para cada rodada a versão do Android/One UI, do YouTube, do Instagram e do Não-Rola (*Ajustes › Sobre*).

**Antes de começar:** em *Ajustes › Diagnóstico*, deixe o log limpo. Se algum cenário falhar, faça uma captura da tela problemática (*Capturar próxima tela*) e guarde-a junto com o ID do cenário.

Legenda do resultado: ✅ passou · ❌ falhou · ⚠️ parcial (descreva).

## 1. Instalação e permissões

| ID | Cenário | Passos | Esperado | Resultado |
|---|---|---|---|---|
| P1 | Instalação | `adb install -r release/NaoRola-0.3.0.apk` | App aparece com o ícone do Não-Rola | |
| P1b | Atualização | Com uma versão anterior instalada e usada, instalar a 0.3.0 por cima | Configurações e contagem de hoje mantidas | |
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
| Y7 | Rolar a Início com a prateleira de Shorts | **Nada bloqueado** (prateleira fica para a Fase 4) | |
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
| I7 | Rolar o feed com um Reel visível | Nada bloqueado no feed (cobertura é Fase 4); tocar no Reel abre o visualizador e bloqueia | |

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
