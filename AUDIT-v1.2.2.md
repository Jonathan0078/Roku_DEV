# RokuDev 1.2.2 — Relatório de varredura

## Escopo

Varredura estática e revisão manual de `MainActivity.java`, `index.html`, `index.js`, `mobile.js`, `mobile.css`, Manifest, recursos XML, fluxo ECP, importação/armazenamento de pacotes e integração WebView.

## Bugs/fragilidades corrigidos

1. **ANR ao importar pacotes** — a cópia para recentes ocorria na UI thread. Agora usa executor de I/O dedicado.
2. **Arquivo parcial após falha** — importação passou a usar `.part` + rename atômico no mesmo filesystem e limpeza em erro.
3. **Crescimento de armazenamento** — limpeza de `.part`, órfãos e recents inválidos no startup.
4. **Pacotes gigantes** — limite de 512 MB e validação de tamanho conhecida antes da importação.
5. **ZIPs patológicos no Inspector** — limites de entradas e de listas exibidas.
6. **Exposição da ponte Android em navegação externa** — WebView principal fica preso ao host local; http/https abre externamente.
7. **ECP com threads ilimitadas** — executor e fila agora são limitados.
8. **ECP `Lit_` corrompido** — normalização não remove espaço nem converte o caractere literal para minúsculo.
9. **IP ECP errado em múltiplas redes** — priorização de wlan/wifi/ethernet e IPv4 privado.
10. **Back do Android preso no engine sem app** — volta ao sistema quando não há app Roku ativo.
11. **Duplo carregamento de exemplos** — removido onclick duplicado no `<img>` interno.
12. **Fullscreen inconsistente** — duplo toque e botão agora usam `screenFrame`, mantendo o remote overlay.
13. **Tecla touch presa** — pointer capture + lostpointercapture no D-pad.
14. **Erro sobre erro em file://** — removida referência a variável inexistente `data.url`.
15. **Base64 de imagem grande** — conversão em chunks para não estourar call stack.
16. **Cancelamento do seletor** — `fileSelector.onchange` agora aceita ausência de arquivo.
17. **console.debug invisível** — incluído na captura do console interno.
18. **ACTION_SEND incompleto** — fallback para ClipData.
19. **Falha de share sem tratamento** — exceções tratadas e informadas ao usuário.
20. **Backup Android contendo dados de dev** — `allowBackup=false`.
21. **Colisão de ID de pacote** — IDs internos migrados para UUID.

## SVG

Foram criados 20 assets SVG locais em `app/src/main/assets/web/images/ui/`: `back`, `close`, `console`, `down`, `fast-forward`, `fullscreen`, `home`, `left`, `options`, `play-pause`, `reload`, `remote`, `replay`, `rewind`, `right`, `rotate`, `select`, `settings`, `simulator`, `up`.

## Verificações automatizadas

- JavaScript: sintaxe válida em Node.
- HTML: sem IDs duplicados e sem referências estáticas de ID ausentes.
- Tabs: 4 botões e 4 views com nomes correspondentes.
- SVG: 20/20 parseados como XML.
- XML Android: Manifest, strings e tema parseados com sucesso.
- Assets: todos os `src` locais do HTML existem.
- UI própria: sem caracteres das faixas Unicode de emoji/dingbats usadas como ícones.
- Código Java: sem erros sintáticos detectáveis pelo `javac`; os erros restantes no container são somente classes Android/AndroidX ausentes.

## Limitação mantida

`roku_browser` para conteúdo local `file:///pkg_...` depende de BrowserFS. O web-app upstream carrega BrowserFS via CDN. Como o RokuDev foi estruturado para funcionar offline e com isolamento de origem, essa dependência remota não foi adicionada nesta revisão.
