const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/web/index.js'), 'utf8');
const script = source.slice(0, source.indexOf('// File selector')) + '\n' +
  source.slice(source.indexOf('async function runFile('), source.indexOf('function reloadLastPackage('));
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup(fetch) {
  const elements = new Map(), events = {}, executions = [], init = [];
  const context = vm.createContext({
    console: {error() {}, warn() {}, log() {}}, fetch, URL,
    localStorage: {getItem() {return null;}},
    document: {baseURI:'https://appassets.androidplatform.net/assets/web/index.html', getElementById(id) {if (!elements.has(id)) elements.set(id, {style: {}, focus() {}}); return elements.get(id);}},
    addEventListener(name, callback) {events[name] = callback;},
    dispatchEvent() {}, CustomEvent: function() {},
    brs: {SupportedExtension: {SceneGraph: 'sg'}, getVersion() {return 'test';}, subscribe() {},
      async initialize(config) {init.push(config);}, execute(...args) {executions.push(args);}},
    appIconsVisibility() {}, fileSelector: {},
  });
  vm.runInContext(script, context);
  return {context, events, elements, executions, init};
}
(async () => {
  let completeAssets;
  const assets = new ArrayBuffer(4);
  const state = setup(() => new Promise(resolve => {completeAssets = resolve;}));
  const opening = vm.runInContext('runFile({name:"test.zip",arrayBuffer:async()=>new ArrayBuffer(8)})', state.context);
  state.events.load(); await tick();
  assert.equal(state.init.length, 0, 'startup must wait for common.zip');
  assert.equal(state.executions.length, 0, 'early package must wait for engine');
  completeAssets({ok:true, arrayBuffer:async()=>assets});
  await opening; await tick();
  assert.equal(state.init[0].assets, assets);
  const extension = state.init[0].extensions.get('sg');
  assert.equal(new URL(extension, 'https://appassets.androidplatform.net/assets/web/lib/brs.worker.js').href,
    'https://appassets.androidplatform.net/assets/web/lib/brs-sg.js');
  assert.equal(state.executions.length, 1);
  assert.ok(fs.existsSync(path.join(__dirname, '../app/src/main/assets/web/lib/brs-sg.js')));

  const failed = setup(async()=>({ok:false,status:404}));
  failed.events.load(); await tick();
  await vm.runInContext('runFile({name:"test.zip"})', failed.context);
  assert.equal(failed.init.length, 0);
  assert.equal(failed.executions.length, 0);
  assert.match(failed.elements.get('appInfo').textContent, /Falha ao iniciar/);
  assert.equal(failed.elements.get('loading').style.visibility, 'hidden');

  state.context.fetch = async()=> {throw new Error('offline');};
  await vm.runInContext('loadZip("home-01")', state.context); await tick();
  assert.equal(state.elements.get('loading').style.visibility, 'hidden');
  assert.equal(state.elements.get('display').style.opacity, 1);
  assert.match(state.elements.get('appInfo').textContent, /Não foi possível/);
  console.log('PASS: startup, early package, missing assets, SceneGraph path and failed download (13 checks)');
})().catch(error => {console.error(error); process.exitCode = 1;});
