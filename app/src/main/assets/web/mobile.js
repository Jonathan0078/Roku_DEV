(() => {
  const $ = id => document.getElementById(id);
  const consoleOutput = $('consoleOutput');
  const isolationBadge = $('isolationBadge');
  const closeAppButton = $('closeAppButton');
  const reloadButton = $('reloadButton');
  const fileButton = $('fileButton');
  const appInfo = $('appInfo');
  const display = $('display');
  const screenFrame = $('screenFrame');
  const recentPackages = $('recentPackages');
  const deviceProfile = $('deviceProfile');
  const profileLabel = $('profileLabel');
  const ecpBadge = $('ecpBadge');
  const ecpAddress = $('ecpAddress');
  const runtimeInfo = $('runtimeInfo');
  const packageSummary = $('packageSummary');
  const packageType = $('packageType');
  const manifestOutput = $('manifestOutput');
  const componentsOutput = $('componentsOutput');
  const filesOutput = $('filesOutput');
  const lastErrorCard = $('lastErrorCard');
  const lastErrorText = $('lastErrorText');

  const logs = [{ level:'info', text:'RokuDev iniciado.' }];
  let activeFilter = 'all';
  let lastPackageId = '';
  let pendingAutoRunId = globalThis.__brsPendingPackageId || '';
  let lastNativeAppState = '';

  function renderCoreFlixUpdate(data = {}) {
    const busy = ['checking', 'downloading'].includes(data.state);
    $('coreflixCheckUpdate').disabled = busy;
    $('coreflixInstallUpdate').hidden = data.state !== 'available';
    $('coreflixInstallUpdate').disabled = busy;
    if (data.message) $('coreflixUpdateMessage').textContent = data.message;
  }
  $('coreflixCheckUpdate').addEventListener('click', () => {
    if (!window.Android?.checkCoreFlixUpdate) {
      renderCoreFlixUpdate({state:'error', message:'Atualizações estão disponíveis no aplicativo RokuDev para Android.'});
      return;
    }
    renderCoreFlixUpdate({state:'checking', message:'Consultando atualizações…'});
    Android.checkCoreFlixUpdate();
  });
  $('coreflixInstallUpdate').addEventListener('click', () => {
    if (!window.Android?.installCoreFlixUpdate) return;
    renderCoreFlixUpdate({state:'downloading', message:'Baixando e verificando o pacote…'});
    Android.installCoreFlixUpdate();
  });
  window.addEventListener('coreflix-update', event => renderCoreFlixUpdate(event.detail || {}));
  window.addEventListener('brs-native-ready', () => renderCoreFlixUpdate(parseBridgeJson('getCoreFlixUpdateStatus', {})));

  // Navegação principal do RokuDev
  const tabButtons = Array.from(document.querySelectorAll('.tab-button'));
  const tabViews = Array.from(document.querySelectorAll('.tab-view'));
  function activateTab(name) {
    const target = ['simulator','control','console','devtools'].includes(name) ? name : 'simulator';
    tabButtons.forEach(b => b.classList.toggle('active', b.dataset.tab === target));
    tabViews.forEach(v => v.classList.toggle('active', v.dataset.tabView === target));
    try { localStorage.setItem('rokuDevActiveTab', target); } catch (_) {}
    if (target === 'console') setTimeout(renderLogs, 0);
    if (target === 'devtools') setTimeout(refreshRuntime, 0);
  }
  tabButtons.forEach(button => button.addEventListener('click', () => activateTab(button.dataset.tab)));
  activateTab((()=>{ try { return localStorage.getItem('rokuDevActiveTab') || 'simulator'; } catch (_) { return 'simulator'; } })());

  function stringify(value) {
    if (typeof value === 'string') return value;
    if (value instanceof Error) return `${value.name}: ${value.message}`;
    try { return JSON.stringify(value); } catch (_) { return String(value); }
  }

  function renderLogs() {
    if (!consoleOutput) return;
    const visible = logs.filter(item => activeFilter === 'all' || item.level === activeFilter);
    consoleOutput.textContent = visible.map(item => item.text).join('\n') || '(sem registros neste filtro)';
    consoleOutput.scrollTop = consoleOutput.scrollHeight;
  }

  function appendLog(level, args) {
    const normalized = (level === 'log' || level === 'debug') ? 'info' : level;
    const values = Array.from(args);
    if (typeof values[0] === 'string' && values[0].startsWith('%c')) {
      values[0] = values[0].slice(2);
      if (typeof values[1] === 'string' && /(?:color|font|background)\s*:/.test(values[1])) values.splice(1, 1);
    }
    const line = `[${new Date().toLocaleTimeString()}] ${normalized.toUpperCase()}: ${values.map(stringify).join(' ')}`;
    logs.push({ level: normalized, text: line });
    if (logs.length > 700) logs.splice(0, logs.length - 550);
    if (normalized === 'error') showLastError(line);
    renderLogs();
  }

  ['log','info','debug','warn','error'].forEach(level => {
    const original = console[level].bind(console);
    console[level] = (...args) => { original(...args); appendLog(level, args); };
  });

  window.addEventListener('error', e => console.error('JS', e.message || 'erro desconhecido', e.filename ? `${e.filename}:${e.lineno}` : ''));
  window.addEventListener('unhandledrejection', e => console.error('Promise', e.reason || 'rejeitada'));

  function showLastError(text) {
    if (!lastErrorCard || !lastErrorText) return;
    lastErrorText.textContent = text;
    lastErrorCard.hidden = false;
  }
  $('dismissErrorButton')?.addEventListener('click', () => { lastErrorCard.hidden = true; });

  function engine() {
    return globalThis.brs && typeof globalThis.brs.sendKeyPress === 'function' ? globalThis.brs : null;
  }

  function currentAppState() {
    try { return globalThis.brsMobileGetCurrentApp?.() || { id:'', running:false }; }
    catch (_) { return { id:'', running:false }; }
  }

  function haptic() {
    try {
      if (window.Android?.haptic) Android.haptic();
      else if (navigator.vibrate) navigator.vibrate(12);
    } catch (_) {}
  }

  function engineUnavailable(action) { console.error(`${action}: engine BRS ainda não está disponível`); }
  function down(key) { const b=engine(); if(!b)return engineUnavailable(`keyDown ${key}`); try{b.sendKeyDown(key);haptic();}catch(e){console.error('Falha keyDown',key,e);} }
  function up(key) { const b=engine(); if(!b)return engineUnavailable(`keyUp ${key}`); try{b.sendKeyUp(key);}catch(e){console.error('Falha keyUp',key,e);} }
  function press(key) { const b=engine(); if(!b)return engineUnavailable(`keyPress ${key}`); try{b.sendKeyPress(key,90);haptic();}catch(e){console.error('Falha keyPress',key,e);} }

  globalThis.nativeRokuKey = function(key, action='press') {
    if (action === 'down') down(key);
    else if (action === 'up') up(key);
    else press(key);
  };

  document.querySelectorAll('.remote-key').forEach(button => {
    const key = button.dataset.key;
    if (!key) return;
    if (button.classList.contains('holdable')) {
      const start = ev => {
        ev.preventDefault();
        try { button.setPointerCapture?.(ev.pointerId); } catch (_) {}
        button.classList.add('pressed');
        down(key);
      };
      const end = ev => {
        if (!button.classList.contains('pressed')) return;
        ev?.preventDefault?.();
        button.classList.remove('pressed');
        up(key);
        try { if (ev?.pointerId != null && button.hasPointerCapture?.(ev.pointerId)) button.releasePointerCapture(ev.pointerId); } catch (_) {}
      };
      button.addEventListener('pointerdown', start);
      button.addEventListener('pointerup', end);
      button.addEventListener('pointercancel', end);
      button.addEventListener('lostpointercapture', end);
    } else button.addEventListener('click', ev => { ev.preventDefault(); press(key); });
  });

  document.querySelectorAll('.example-card').forEach((card, idx) => {
    card.addEventListener('click', ev => {
      ev.preventDefault();
      if (!engine()) return engineUnavailable('Abrir exemplo');
      try { lastPackageId=''; clearInspector(); if (typeof appList !== 'undefined' && appList[idx]?.id) loadZip(appList[idx].id); }
      catch (e) { console.error('Falha ao abrir exemplo', e); }
    });
  });

  $('fullscreenButton')?.addEventListener('click', async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await screenFrame.requestFullscreen();
    } catch (e) { console.warn('Tela cheia indisponível', e?.message || e); }
  });

  document.addEventListener('fullscreenchange', () => {
    try { if (window.Android?.setImmersive) Android.setImmersive(Boolean(document.fullscreenElement)); } catch (_) {}
  });

  $('orientationButton')?.addEventListener('click', () => {
    try { if (window.Android?.toggleOrientation) Android.toggleOrientation(); }
    catch (e) { console.warn('Não foi possível alternar orientação', e); }
  });

  closeAppButton?.addEventListener('click', () => {
    try {
      const b = engine(); const state = currentAppState();
      if (state.running && b) b.terminate('EXIT_USER_NAV');
      else if (typeof globalThis.closeApp === 'function') globalThis.closeApp();
    } catch (e) { console.error('Falha ao fechar app', e); }
  });

  reloadButton?.addEventListener('click', () => {
    try {
      if (!globalThis.brsMobileReload?.()) console.warn('Nenhum pacote disponível para recarregar.');
    } catch (e) { console.error('Falha ao recarregar pacote', e); }
  });

  document.querySelectorAll('.console-filter').forEach(button => {
    button.addEventListener('click', () => {
      activeFilter = button.dataset.filter || 'all';
      document.querySelectorAll('.console-filter').forEach(b => b.classList.toggle('active', b === button));
      renderLogs();
    });
  });

  $('clearConsoleButton')?.addEventListener('click', () => { logs.length=0; logs.push({level:'info',text:'Console limpo.'}); renderLogs(); });
  $('copyConsoleButton')?.addEventListener('click', async () => {
    const text = logs.map(x=>x.text).join('\n');
    try { await navigator.clipboard.writeText(text); haptic(); }
    catch (_) { try { if (window.Android?.copyText) Android.copyText(text); } catch (e) {} }
  });
  $('saveConsoleButton')?.addEventListener('click', () => { try { window.Android?.saveLog?.(logs.map(x=>x.text).join('\n')); } catch (e) { console.warn('Salvar log indisponível',e); } });
  $('shareConsoleButton')?.addEventListener('click', () => { try { window.Android?.shareText?.(logs.map(x=>x.text).join('\n')); } catch (e) { console.warn('Compartilhar log indisponível',e); } });

  function parseBridgeJson(method, fallback) {
    try {
      if (!window.Android || typeof Android[method] !== 'function') return fallback;
      return JSON.parse(Android[method]());
    } catch (_) { return fallback; }
  }

  function formatBytes(bytes) {
    if (!Number.isFinite(Number(bytes))) return '—';
    const n=Number(bytes); if(n<1024)return `${n} B`; if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`; return `${(n/1024/1024).toFixed(1)} MB`;
  }

  function formatDate(ms) {
    try { return new Date(Number(ms)).toLocaleString(); } catch (_) { return ''; }
  }

  function refreshRecents() {
    const items = parseBridgeJson('getRecentPackagesJson', []);
    if (!recentPackages) return;
    recentPackages.innerHTML='';
    if (!Array.isArray(items) || !items.length) {
      recentPackages.innerHTML='<span class="muted">Nenhum pacote recente.</span>';
      return;
    }
    items.forEach(item => {
      const row=document.createElement('div'); row.className='recent-item';
      const open=document.createElement('button'); open.className='recent-open'; open.type='button';
      const strong=document.createElement('strong'); strong.textContent=item.name || item.id;
      const small=document.createElement('small'); small.textContent=`${formatBytes(item.size)} · ${formatDate(item.time)}`;
      open.append(strong,small);
      open.addEventListener('click',()=>loadRecentPackage(item.id,item.name));
      const del=document.createElement('button'); del.className='recent-delete'; del.type='button'; del.title='Remover dos recentes'; del.setAttribute('aria-label','Remover dos recentes');
      const delIcon=document.createElement('img'); delIcon.src='images/ui/close.svg'; delIcon.alt=''; del.append(delIcon);
      del.addEventListener('click',ev=>{ev.stopPropagation();try{window.Android?.removeRecentPackage?.(item.id);refreshRecents();if(lastPackageId===item.id)clearInspector();}catch(e){console.error('Falha ao remover recente',e);}});
      row.append(open,del); recentPackages.append(row);
    });
  }

  async function loadRecentPackage(id,name) {
    if (!engine()) return engineUnavailable('Abrir recente');
    const ext=(name||id).split('.').pop()?.toLowerCase();
    if (ext==='bpk') { console.warn('BPK recente protegido: abra pelo seletor para informar a senha.'); return; }
    try {
      const response=await fetch(`/packages/${encodeURIComponent(id)}`,{cache:'no-store'});
      if(!response.ok)throw new Error(`HTTP ${response.status}`);
      const blob=await response.blob();
      const file=new File([blob],name||id,{type:blob.type||'application/octet-stream'});
      lastPackageId=id; globalThis.__brsNativeLastPackageId=id;
      inspectPackage(id);
      const launch = () => runFile(file);
      if (currentAppState().running) {
        try { engine()?.terminate('EXIT_USER_NAV'); } catch (_) {}
        setTimeout(launch, 350);
      } else launch();
    } catch(e) { console.error('Falha ao abrir pacote recente',e); }
  }

  function clearInspector() {
    packageSummary.textContent='Abra um pacote para inspecionar manifest, componentes e scripts.';
    packageType.textContent='—'; manifestOutput.textContent='—'; componentsOutput.textContent='—'; filesOutput.textContent='—';
  }

  function inspectPackage(id) {
    if (!id) return clearInspector();
    try {
      if (!window.Android?.getPackageInspectionJson) return;
      const data=JSON.parse(Android.getPackageInspectionJson(id));
      if(data.error){packageSummary.textContent=data.error;return;}
      lastPackageId=id;
      packageType.textContent=(data.type||'arquivo').toUpperCase();
      const manifest=data.manifest||{};
      const title=manifest.title || data.name || id;
      packageSummary.textContent=`${title} · ${formatBytes(data.size)}${data.entryCount?` · ${data.entryCount} arquivos`:''}`;
      if(data.protected){ manifestOutput.textContent=data.message||'Pacote protegido.'; componentsOutput.textContent='—'; filesOutput.textContent='—'; return; }
      const manifestLines=Object.keys(manifest).sort().map(k=>`${k}=${manifest[k]}`);
      manifestOutput.textContent=manifestLines.join('\n')||'(manifest não encontrado)';
      const comps=Array.isArray(data.components)?data.components:[]; const scripts=Array.isArray(data.scripts)?data.scripts:[];
      componentsOutput.textContent=[`Componentes XML (${comps.length})`,...comps,'',`Scripts BRS (${scripts.length})`,...scripts].join('\n');
      const files=Array.isArray(data.files)?data.files:[];
      filesOutput.textContent=files.map(f=>`${f.name}  ${f.size>=0?formatBytes(f.size):''}`).join('\n') || '—';
    } catch(e){ console.error('Falha no Package Inspector',e); }
  }

  function refreshRuntime() {
    const native=parseBridgeJson('getRuntimeJson',{});
    const status=updateRuntimeStatus();
    let profile='720p'; try { profile=localStorage.getItem('brsDisplayMode')||'720p'; } catch (_) {}
    const pairs={
      'Engine':status.b?`v${status.b.getVersion?.()||'?'}`:'indisponível',
      'SceneGraph':'brs-scenegraph 0.6.1',
      'SharedArrayBuffer':status.sab?'sim':'não',
      'Cross-origin':status.isolated?'isolado':'não isolado',
      'Perfil':profile,
      'Android':native.android?`${native.android} (SDK ${native.sdk})`:'—',
      'Dispositivo':native.device||'—',
      'WebView':native.webViewVersion||'—'
    };
    runtimeInfo.innerHTML='';
    Object.entries(pairs).forEach(([k,v])=>{const dt=document.createElement('dt');dt.textContent=k;const dd=document.createElement('dd');dd.textContent=v;runtimeInfo.append(dt,dd);});
    const ecpOk=Boolean(native.ecpRunning); ecpBadge.textContent=ecpOk?'ativo':'indisponível'; ecpBadge.className=`badge ${ecpOk?'ok':'bad'}`;
    ecpAddress.textContent=native.ip?`http://${native.ip}:${native.ecpPort||8060}`:'—';
  }

  $('refreshRuntimeButton')?.addEventListener('click',refreshRuntime);
  $('copyEcpButton')?.addEventListener('click',async()=>{const text=ecpAddress.textContent;try{await navigator.clipboard.writeText(text);}catch(_){try{window.Android?.copyText?.(text);}catch(e){}}});

  function configureProfileUi() {
    let storedProfile='720p'; try { storedProfile=localStorage.getItem('brsDisplayMode')||'720p'; } catch (_) {}
    const profile=['480p','720p','1080p'].includes(storedProfile)?storedProfile:'720p';
    deviceProfile.value=profile; profileLabel.textContent=profile;
    try { window.Android?.setDisplayMode?.(profile); } catch (_) {}
    screenFrame.classList.toggle('mode-480p',profile==='480p');
    deviceProfile.addEventListener('change',()=>{
      try { localStorage.setItem('brsDisplayMode',deviceProfile.value); } catch (_) {}
      console.info(`Perfil alterado para ${deviceProfile.value}; reiniciando runtime.`);
      setTimeout(()=>location.reload(),80);
    });
  }

  function updateRuntimeStatus() {
    let nativeAllowlist=Boolean(globalThis.__brsNativeIsolationAllowlisted);
    try { if(window.Android?.isolationAllowlistEnabled) nativeAllowlist=Android.isolationAllowlistEnabled(); } catch(_) {}
    const sab=typeof SharedArrayBuffer!=='undefined'; const isolated=Boolean(window.crossOriginIsolated); const b=engine();
    if(isolationBadge){
      if(b&&sab){isolationBadge.textContent='engine OK';isolationBadge.className='badge ok';}
      else if(!nativeAllowlist){isolationBadge.textContent='WebView antigo';isolationBadge.className='badge bad';}
      else if(!sab){isolationBadge.textContent='sem SharedArrayBuffer';isolationBadge.className='badge bad';}
      else{isolationBadge.textContent='carregando…';isolationBadge.className='badge';}
    }
    if(fileButton)fileButton.disabled=!b;
    document.querySelectorAll('.remote-key,.example-card').forEach(el=>el.disabled=!b);
    if(reloadButton)reloadButton.disabled=!b||!Boolean(globalThis.brsMobileHasReload?.());
    return {b,sab,isolated,nativeAllowlist};
  }

  function reportStartup() {
    const status=updateRuntimeStatus();
    console.info(`Runtime: brs=${Boolean(status.b)} SharedArrayBuffer=${status.sab} crossOriginIsolated=${status.isolated} allowlist=${status.nativeAllowlist}`);
    if(!status.b){
      if(!status.sab){const msg='SharedArrayBuffer indisponível. Atualize Android System WebView/Google Chrome.';console.error(msg);if(appInfo)appInfo.textContent=msg;}
      else console.error('SharedArrayBuffer disponível, mas lib/brs.api.js não inicializou.');
    } else { try{const v=status.b.getVersion?.();console.info(`BRS engine carregado${v?`: v${v}`:''}.`);}catch(_){} }
    refreshRuntime();
    try{const nativeId=window.Android?.getLastImportedPackageId?.();if(nativeId){lastPackageId=nativeId;inspectPackage(nativeId);}}catch(_){}
    if(!pendingAutoRunId && globalThis.__brsPendingPackageId) pendingAutoRunId=globalThis.__brsPendingPackageId;
    if(pendingAutoRunId){const id=pendingAutoRunId;pendingAutoRunId='';globalThis.__brsPendingPackageId='';setTimeout(()=>{const rec=parseBridgeJson('getRecentPackagesJson',[]).find(x=>x.id===id);if(rec)loadRecentPackage(id,rec.name);},250);}
  }

  window.addEventListener('brs-native-ready',()=>{refreshRecents();setTimeout(reportStartup,150);});
  window.addEventListener('brs-recents-changed',e=>{
    refreshRecents(); const id=e.detail?.id||globalThis.__brsNativeLastPackageId||'';
    if(id){lastPackageId=id;inspectPackage(id);} if(e.detail?.autoRun&&id){pendingAutoRunId=id;setTimeout(reportStartup,200);}
  });
  window.addEventListener('brs-app-state',e=>{
    const app=e.detail?.app||{}; const event=e.detail?.event||'';
    const running=Boolean(app.running)||event==='started';
    const stateKey=`${app.title||''}|${app.version||''}|${running}`;
    if(stateKey!==lastNativeAppState){lastNativeAppState=stateKey;try{window.Android?.setActiveApp?.(app.title||'RokuDev',app.version||'',running);}catch(_){} }
    updateRuntimeStatus();
    if(lastPackageId)inspectPackage(lastPackageId);
  });

  window.addEventListener('load',()=>{configureProfileUi();refreshRecents();renderLogs();setTimeout(reportStartup,250);setTimeout(reportStartup,1200);});

  const stateTimer=setInterval(()=>{
    try{
      const state=currentAppState();
      if(closeAppButton)closeAppButton.style.display=state.running?'inline-block':'none';
      if(state.running&&display)display.style.opacity='1';
      updateRuntimeStatus();
    }catch(_){}
  },600);

  const browserKeyMap={ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',Enter:'select',Escape:'back',Backspace:'back',' ':'play',MediaPlayPause:'play',MediaTrackNext:'fwd',MediaTrackPrevious:'rev'};
  window.addEventListener('keydown',e=>{if(['INPUT','SELECT','TEXTAREA'].includes(e.target?.tagName))return;const k=browserKeyMap[e.key];if(k&&!e.repeat){e.preventDefault();down(k);}});
  window.addEventListener('keyup',e=>{if(['INPUT','SELECT','TEXTAREA'].includes(e.target?.tagName))return;const k=browserKeyMap[e.key];if(k){e.preventDefault();up(k);}});

  window.mobileBackFromAndroid=function(){try{const b=engine();const state=currentAppState();if(b&&state.running)b.sendKeyPress('back',90);else if(window.Android?.exitApp)window.Android.exitApp();}catch(_){try{window.Android?.exitApp?.();}catch(e){}}};
  window.addEventListener('beforeunload',()=>clearInterval(stateTimer));
})();
