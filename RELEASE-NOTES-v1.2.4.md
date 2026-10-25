# RokuDev 1.2.4 — correção SceneGraph no worker

Corrige regressão da versão 1.2.3: o worker resolve caminhos relativos a partir de lib/brs.worker.js; ./lib/brs-sg.js virava lib/lib/brs-sg.js. Agora a URL da extensão é construída de forma absoluta a partir de document.baseURI, funcionando também sob /assets/web/ no Android.

O teste tests/startup.cjs reproduz a resolução da URL pelo worker. A prévia tests/preview.cjs --coreflix abre o pacote CoreFlix pelo carregador público runFile, sem reescrever caminhos ou recursos do engine. A tela de login SceneGraph foi renderizada com sucesso na prévia web.

O teste anterior com Rect Bounce exercitava desenho 2D e não validava SceneGraph. A instalação do APK e a execução no Android continuam pendentes de confirmação no aparelho.

Pacotes sem manifest na raiz não são canais Roku válidos. O aviso de detecção DRM indica ausência dos sistemas de chaves consultados e não explica a falha de carregamento SceneGraph.
