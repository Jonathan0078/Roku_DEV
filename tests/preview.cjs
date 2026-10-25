const http = require('node:http'), fs = require('node:fs'), path = require('node:path');
const root = path.resolve(__dirname, '../app/src/main/assets/web');
const coreflix = path.resolve(__dirname, '../../build/roku/CoreFlix-Roku-0.7.0.zip');
const types = {'.js':'application/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml','.png':'image/png','.zip':'application/zip'};
http.createServer((req,res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    const file = url.pathname === '/test-coreflix.zip' ? coreflix : path.resolve(root, '.' + (url.pathname === '/' ? '/index.html' : url.pathname));
    if (file !== coreflix && !file.startsWith(root + path.sep)) { res.writeHead(403).end(); return; }
    let data = fs.readFileSync(file);
    if (process.argv.includes('--coreflix') && file === path.join(root, 'index.html')) {
      // Exercise the public package loader; do not rewrite runtime paths or assets.
      data = Buffer.from(data.toString().replace('</body>', `<script>window.addEventListener('load', async () => {const response=await fetch('/test-coreflix.zip'); await runFile(new File([await response.arrayBuffer()], 'CoreFlix.zip'));});</script></body>`));
    }
    res.writeHead(200, {'Content-Type':types[path.extname(file)] || 'application/octet-stream','Cross-Origin-Opener-Policy':'same-origin','Cross-Origin-Embedder-Policy':'require-corp','Cache-Control':'no-store'});
    res.end(data);
  } catch {res.writeHead(404).end();}
}).listen(8766, '127.0.0.1', () => console.log('RokuDev preview http://127.0.0.1:8766'));
