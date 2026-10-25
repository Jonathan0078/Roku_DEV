# RokuDev 1.2.3 — revisão de estabilidade

Melhorias aplicadas:
- Carregamento explícito do common.zip antes da inicialização: fontes e certificados já estão disponíveis quando o primeiro pacote começa.
- Pacotes importados e exemplos aguardam a inicialização do engine. Falhas de inicialização são exibidas na interface e no Console.
- Caminho SceneGraph corrigido para lib/brs-sg.js, onde a extensão está empacotada.
- Download de exemplo com falha encerra o indicador de carregamento e recupera a tela.
- Sem app Roku ativo, teclas físicas voltam ao WebView/Android para navegar pela interface do emulador.

Validação: node tests/startup.cjs (13 verificações) e node --check app/src/main/assets/web/index.js. O exemplo Rect Bounce foi aberto na interface web local sem substituições no código servido.

Limites: o runtime continua brs-engine 2.6.1 / SceneGraph 0.6.1. Estas mudanças não garantem compatibilidade completa com Roku OS, codecs, DRM ou serviços externos. Reprodução real e controle físico precisam ser verificados no Android; nenhum dispositivo estava conectado ao ADB durante esta revisão.

Para verificar a interface localmente: node tests/preview.cjs e abrir http://127.0.0.1:8766. Esse teste não substitui instalar o APK no Android.

Verificação final: assembleDebug e lintDebug concluídos com sucesso. Tema Android 8.0 corrigido com variante values-v27; verificações de recursos WebView reconhecidas pelo Lint. Permanecem avisos não bloqueantes no relatório app/build/reports/lint-results-debug.html, incluindo recuperação após falha do processo WebView, atualização de SDK e recursos visuais. APK: app/build/outputs/apk/debug/app-debug.apk (1.2.3-debug, versionCode 8).
