# Capturas reais de tela

Arquivos gerados pelo **modo diagnóstico** do Não-Rola (*Ajustes › Diagnóstico*). Cada arquivo guarda **só a estrutura** de uma tela: tipos de elemento, identificadores e posições relativas. Não há textos, nomes, mensagens nem endereços.

## Como adicionar

1. No app, toque em **Capturar próxima tela** e abra a tela do YouTube, do Instagram ou do navegador.
2. Na lista de capturas, marque **Deveria bloquear** ou **Não deveria bloquear**. O rótulo fica gravado no arquivo (`# esperado=bloquear` ou `# esperado=liberar`).
3. Toque em **Compartilhar** e coloque o arquivo nesta pasta (de preferência numa branch ou pull request).

## O que acontece depois

O teste `CapturedScreensTest`, do módulo `:engine`, lê **todas** as capturas rotuladas desta pasta. Ele confere se o detector chega à mesma conclusão que o rótulo.

- Rodando localmente: `./gradlew :engine:test`.
- No GitHub, o CI roda o mesmo teste em cada pull request.

Uma captura que falha mostra quais sinais o detector viu. É o ponto de partida para ajustar as regras em `engine/src/main/kotlin/com/lucasanso/naorola/detection/rules/`. Cada correção vem acompanhada da captura que a motivou, então a mesma tela não volta a falhar numa versão futura.

Capturas sem rótulo são ignoradas pelo teste e só aparecem na contagem.
