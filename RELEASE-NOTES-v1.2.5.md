# RokuDev 1.2.5 — atualização do CoreFlix pelo site

Na aba Simulador, use **Verificar atualização** e depois **Baixar e abrir**. A consulta usa https://jonathan-sistemas.com.br/coreflix/roku-update.json. O download ocorre no Android, sem depender de CORS do WebView. Esta função atualiza o pacote CoreFlix dentro do emulador, não o APK RokuDev nem canais de aparelhos Roku físicos.

A consulta compara a versão publicada com a do CoreFlix ativo e a última atualização salva cujo arquivo ainda existe. Um pacote importado manualmente precisa ser aberto para sua versão entrar nessa comparação. Uma versão já salva pode ser aberta em Pacotes recentes.

O download valida HTTPS/domínio/pasta, tamanho máximo de 64 MB, SHA-256, manifest na raiz, nome CoreFlix, versão e arquivo de entrada. Redirects continuam limitados ao mesmo domínio/pasta. A importação só acontece depois da validação; falhas não encerram o app ativo. Ao escolher Baixar e abrir, o pacote validado é salvo em recentes e o app em execução é encerrado para abri-lo. O resultado da abertura continua dependente do runtime.

## Publicar no site

1. Na raiz do workspace, execute `powershell -File tools/roku/prepare-update.ps1 -Package build/roku/CoreFlix-Roku-0.7.0.zip` (ajuste o nome para a nova versão).
2. Envie primeiro o ZIP de `build/roku/site-update` para a pasta pública `/coreflix/` do site.
3. Envie `roku-update.json` para a mesma pasta, por último. Use substituição atômica no servidor quando disponível.
4. Garanta HTTPS válido, acesso direto aos arquivos sem página de login e cache curto ou revalidação para o JSON. Não substitua o ZIP de uma versão já publicada: aumente a versão no manifest, compile e gere novamente o JSON.
5. No RokuDev Android, consulte e baixe a atualização. Se o JSON não estiver publicado, o app informa HTTP 404.

Não é necessário alterar o arquivo APK Android, a configuração Firebase Android ou as regras Firestore. O hash protege a integridade do download; o controle de publicação continua sendo a segurança do seu site HTTPS.

Não foi realizada publicação no servidor: não há acesso de hospedagem conectado nesta tarefa. Foram preparados somente arquivos locais. Instalação/download completo no Android precisam ser confirmados no aparelho após publicação.

## Testes

- `node tests/startup.cjs`: 13 verificações de inicialização.
- `tests/CoreFlixPackageTest.java`: validação JVM do ZIP real, versões, endereços e pacotes inválidos.
- `assembleDebug` e `lintDebug`: verificação Android; consultar o relatório em app/build/reports.
