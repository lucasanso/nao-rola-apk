# Não-Rola na Google Play (teste interno)

## Por que isso resolve o Nubank

O Nubank recusa o Não-Rola mesmo com o serviço desligado e no **modo banco**. Isso indica que a checagem dele olha a **origem** do app: apps instalados fora da Play Store que declaram acessibilidade são tratados como suspeitos, porque é assim que agem os golpes da "mão fantasma". Apps instalados pela Play Store passam pela verificação do Google e, na prática, são aceitos pelos bancos.

O Não-Rola **não** finge ter vindo da Play Store. O caminho legítimo é ele vir da Play Store de verdade. A faixa de **teste interno** é privada (só as contas que você convidar veem o app), sai em minutos e não precisa de "lançamento" público.

> Não dá para garantir o resultado antes de testar: cada banco decide o que aceita. Mas este é o único caminho que muda a origem do app sem enganar ninguém.

## Duas edições, mesmo código

| | APK (por adb) | Google Play |
|---|---|---|
| Arquivo | `release/NaoRola-0.6.0.apk` | `release/NaoRola-0.6.0-play.aab` |
| Shorts/Reels, capas, Início do YouTube, filtro adulto, Disciplina, estatísticas | Sim | Sim |
| Bloqueador do Samsung Internet | Sim | Sim |
| Capas sobre anúncios e "Pular anúncio" no YouTube/Instagram | Sim | **Não**: a política da Play proíbe apps que bloqueiam ou interferem nos anúncios de outros apps |
| Nubank | Recusa | Deve aceitar (a confirmar) |

O AAB é gerado com `./gradlew :app:bundleRelease -Pplay` e é assinado com a sua chave (`keystore/naorola-release.jks`), que vira a **chave de upload**.

## O que só você pode fazer

1. **Conta de desenvolvedor:** em <https://play.google.com/console>, crie a conta com a sua conta Google. A taxa é de **US$ 25**, paga uma vez, e há verificação de identidade com documento. Para contas pessoais novas, publicar para todo mundo exige antes um teste fechado com 12 pessoas por 14 dias, mas **o teste interno é liberado na hora**, e é ele que você vai usar.
2. **Política de privacidade numa URL pública:** a Play exige um link. Publique o texto de [`POLITICA-DE-PRIVACIDADE.md`](POLITICA-DE-PRIVACIDADE.md), por exemplo no Google Sites (grátis) ou deixando o repositório público. Antes, troque o campo de contato pelo seu e-mail.
3. **Vídeo da acessibilidade:** a Play pede um vídeo curto (pode ser não listado no YouTube) mostrando:
   1. o aviso "Antes de ativar" do Não-Rola;
   2. a ativação do serviço;
   3. um Short sendo bloqueado.

   Grave com o gravador de tela do celular.

## Passo a passo no Play Console

1. **Criar app:** nome *Não-Rola*, idioma Português (Brasil), tipo *App*, *Gratuito*. Aceite as declarações.
2. **Painel › Configurar o app**, em *Conteúdo do app*:
   - **Política de privacidade:** a URL do passo 2 acima.
   - **Anúncios:** *Não, meu app não contém anúncios*.
   - **Acesso ao app:** *Todas as funcionalidades estão disponíveis sem acesso especial*.
   - **Classificação do conteúdo:** categoria *Todos os outros tipos de app*; responda *Não* a violência, sexo, linguagem, drogas, jogos de azar e interação entre usuários. O filtro adulto só **bloqueia** sites e não mostra conteúdo.
   - **Público-alvo:** *18 anos ou mais*.
   - **Segurança dos dados:** *O app não coleta nem compartilha dados do usuário*. Justificativa: o app não tem permissão de internet, e tudo fica no aparelho.
   - **App governamental / recursos financeiros / saúde:** *Não*.
   - **Permissões e APIs:** quando aparecer a declaração da **API de acessibilidade**, use o texto abaixo e o link do vídeo.
3. **Testes › Teste interno › Testadores:** crie uma lista de e-mails com a sua conta Google.
4. **Teste interno › Criar nova versão:**
   - **Assinatura de apps do Google Play:** aceite *chave gerada pelo Google* (recomendado). O seu `naorola-release.jks` passa a ser a chave de upload.
   - Envie `release/NaoRola-0.6.0-play.aab`, escreva as notas da versão, *Salvar › Revisar › Iniciar lançamento*.
5. Copie o **link de participação** da aba Testadores, abra no celular (com a mesma conta Google), aceite e toque em **Baixar na Google Play**.

### Texto da declaração de acessibilidade

> O Não-Rola é um app de bem-estar digital que ajuda o usuário a não cair na rolagem infinita de vídeos curtos. Ele usa a API de acessibilidade para reconhecer quando o player do YouTube Shorts ou o visualizador do Instagram Reels está na tela e então mostrar uma tela de bloqueio, voltar ou ir para a tela inicial; para cobrir prateleiras de Shorts e Reels nos feeds e, se o usuário ativar, a aba Início do YouTube; e para ler apenas a barra de endereço de navegadores, a fim de bloquear Shorts/Reels na web e, opcionalmente, sites pornográficos. O serviço observa só os apps cujas proteções o usuário ligou, não tem permissão de internet, não coleta nem envia dados, e textos/endereços são interpretados em memória e nunca armazenados. Antes de ativar, o app mostra um aviso explicando exatamente isso e pede consentimento. O serviço não é uma ferramenta de acessibilidade (isAccessibilityTool="false").

## Trocar o APK pela versão da Play

1. No Não-Rola atual, faça o backup em *Ajustes › Backup das configurações › Salvar em arquivo*.
2. **Desinstale** o Não-Rola instalado por APK. Com a chave gerada pelo Google, as assinaturas são diferentes, e o Android não deixa um instalar por cima do outro. O histórico de estatísticas não vai junto.
3. Instale pela Play Store (link de participação).
4. Abra o Não-Rola, ative o serviço de acessibilidade. Apps da Play não passam pela "configuração restrita". Depois, em *Ajustes › Backup das configurações › Restaurar*, escolha o arquivo.
5. Abra o Nubank e me conte o resultado.

## Próximas versões

A cada versão nova eu gero o AAB com `versionCode` maior (`./gradlew :app:bundleRelease -Pplay`), e você envia em *Teste interno › Criar nova versão*. Depois de migrar para a Play, **não instale mais APKs por cima**: a origem voltaria a ser "fora da loja" (e a assinatura nem bate).

## Se a Google recusar

A revisão pode pedir ajustes no uso da acessibilidade (por exemplo, no vídeo ou no aviso). Me mande a mensagem da Play que eu adapto o app ou a declaração. Apps que bloqueiam Shorts e Reels por acessibilidade existem na loja, então o caso é aceitável quando bem declarado.
