# RokuDev 1.2.4 — Android Studio

Projeto Android Studio do RokuDev, simulador Roku/BrightScript para Android baseado no brs-engine 2.6.1.

## v1.2.4 — SceneGraph

Corrige o caminho duplicado lib/lib da extensão no worker. Detalhes em RELEASE-NOTES-v1.2.4.md.

## v1.2.3 — Inicialização e controles

- Fontes e certificados locais carregados antes de abrir o primeiro pacote.
- Importações e exemplos aguardam o engine; erros de inicialização aparecem na interface.
- Caminho da extensão SceneGraph corrigido e recuperação da tela após falha de download.
- Controle físico navega pelo Android/WebView quando não há app Roku ativo.
- Testes: `node tests/startup.cjs`. Detalhes em `RELEASE-NOTES-v1.2.3.md`.

## v1.2.2 — SVG + Bug Sweep

- Ícones de interface migrados de símbolos Unicode/emoji para 20 SVGs locais.
- Correções de robustez em WebView, importação, ECP, Package Inspector, fullscreen e controles touch.
- Navegação externa é isolada do WebView que possui a ponte Android.
- Importação transacional com limite de 512 MB e limpeza de arquivos parciais/órfãos.
- ECP com limite de clientes, limites de cabeçalhos e suporte correto a `Lit_<caractere>`.
- Android Back corrigido quando nenhum app Roku está em execução.
- Mantidas as abas Simulador, Controle, Console e Dev Tools e os perfis 480p/720p/1080p.

A variante debug utiliza `applicationIdSuffix '.debug'`; a release mantém `br.com.jonathansistemas.brsmobile`.

RokuDev é uma ferramenta independente, não oficial. O brs-engine permanece sob sua licença MIT original.
