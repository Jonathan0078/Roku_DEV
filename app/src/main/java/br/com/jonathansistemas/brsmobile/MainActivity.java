package br.com.jonathansistemas.brsmobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.Profile;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * RokuDev 1.2.5
 *
 * Android host for the MIT-licensed brs-engine browser runtime.
 * Adds developer-oriented features around the engine: recent packages,
 * package inspection, hardware remote keys and a lightweight ECP server.
 */
public class MainActivity extends Activity {
    private static final String TAG = "RokuDev";
    private static final int FILE_CHOOSER_REQUEST = 4102;
    private static final String LOCAL_ORIGIN = "https://appassets.androidplatform.net";
    private static final String START_URL = LOCAL_ORIGIN + "/assets/web/index.html";
    private static final String PREFS_NAME = "brs_mobile_prefs";
    private static final String PREF_RECENTS = "recent_packages";
    private static final int MAX_RECENTS = 8;
    private static final int ECP_PORT = 8060;
    private static final long MAX_PACKAGE_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_INSPECT_ENTRIES = 5000;
    private static final int MAX_INSPECT_LIST_ITEMS = 500;

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private WebViewAssetLoader assetLoader;
    private boolean isolationAllowlistEnabled = false;
    private File packagesDir;
    private volatile String lastImportedPackageId = "";
    private volatile String pendingPackageId = "";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RokuDev-IO");
        t.setDaemon(true);
        return t;
    });
    private EcpServer ecpServer;
    private volatile String activeAppTitle = "RokuDev";
    private volatile String activeAppVersion = "1.2.5";
    private volatile boolean activeAppRunning = false;
    private final AtomicBoolean coreFlixUpdateBusy = new AtomicBoolean(false);
    private volatile String coreFlixUpdateStatus = "{}";
    private JSONObject coreFlixAvailableUpdate;
    private String currentDisplayMode = "720p";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        packagesDir = new File(getFilesDir(), "packages");
        if (!packagesDir.exists() && !packagesDir.mkdirs()) {
            Log.w(TAG, "Não foi possível criar o diretório de pacotes recentes.");
        }
        cleanupPackageStorage();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(11, 13, 17));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/packages/", new InternalPackagePathHandler())
                .build();

        configureWebView();
        configureCrossOriginIsolation();
        startEcpServer();
        processIncomingIntent(getIntent());

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebView.setWebContentsDebuggingEnabled(true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setUserAgentString(s.getUserAgentString() + " RokuDev/1.2.5");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.rgb(11, 13, 17));
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new LocalAssetClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                openPackagePicker();
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d(TAG, message.message() + " @" + message.sourceId() + ":" + message.lineNumber());
                return true;
            }
        });
    }

    private void configureCrossOriginIsolation() {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
                    && WebViewFeature.isFeatureSupported(WebViewFeature.CROSS_ORIGIN_ISOLATED_ALLOWLIST)) {
                Profile profile = WebViewCompat.getProfile(webView);
                profile.setCrossOriginIsolatedAllowlist(Collections.singleton(LOCAL_ORIGIN));
                isolationAllowlistEnabled = true;
                Log.i(TAG, "Cross-origin isolated APIs liberadas para " + LOCAL_ORIGIN);
            } else {
                Log.w(TAG, "WebView sem CROSS_ORIGIN_ISOLATED_ALLOWLIST. Atualize Android System WebView/Chrome.");
            }
        } catch (Throwable t) {
            isolationAllowlistEnabled = false;
            Log.e(TAG, "Falha ao habilitar cross-origin isolation", t);
        }
    }

    private void openPackagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/octet-stream",
                "text/plain"
        });
        try {
            startActivityForResult(intent, FILE_CHOOSER_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o seletor de arquivos.", Toast.LENGTH_LONG).show();
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = null;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            Uri selected = data.getData();
            if (selected == null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                selected = data.getClipData().getItemAt(0).getUri();
            }
            if (selected != null) {
                String name = getDisplayName(selected);
                long knownSize = getContentSize(selected);
                if (!isSupportedPackageName(name)) {
                    Toast.makeText(this, "Formato não suportado. Use ZIP, BPK ou BRS.", Toast.LENGTH_LONG).show();
                } else if (knownSize > MAX_PACKAGE_BYTES) {
                    Toast.makeText(this, "Pacote excede o limite de 512 MB.", Toast.LENGTH_LONG).show();
                } else {
                    result = new Uri[]{selected};
                    final Uri importUri = selected;
                    ioExecutor.execute(() -> {
                        try {
                            String id = importPackage(importUri);
                            if (!id.isEmpty()) {
                                lastImportedPackageId = id;
                                notifyRecentPackagesChanged(id, false);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Falha ao salvar pacote nos recentes", e);
                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                    "Falha ao salvar em recentes: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }
            }
        }
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIncomingIntent(intent);
    }

    private void processIncomingIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= 33) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                //noinspection deprecation
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                uri = intent.getClipData().getItemAt(0).getUri();
            }
        }
        if (uri == null) return;
        String name = getDisplayName(uri);
        long knownSize = getContentSize(uri);
        if (!isSupportedPackageName(name)) {
            Toast.makeText(this, "Formato não suportado. Use ZIP, BPK ou BRS.", Toast.LENGTH_LONG).show();
            return;
        }
        if (knownSize > MAX_PACKAGE_BYTES) {
            Toast.makeText(this, "Pacote excede o limite de 512 MB.", Toast.LENGTH_LONG).show();
            return;
        }
        final Uri importUri = uri;
        ioExecutor.execute(() -> {
            try {
                String id = importPackage(importUri);
                if (id.isEmpty()) return;
                pendingPackageId = id;
                lastImportedPackageId = id;
                notifyRecentPackagesChanged(id, true);
            } catch (Exception e) {
                Log.e(TAG, "Falha ao importar pacote recebido", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Não foi possível importar o pacote recebido: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event);
        }
        if (!activeAppRunning) {
            return super.dispatchKeyEvent(event);
        }
        String rokuKey = mapHardwareKey(event.getKeyCode());
        if (rokuKey != null && webView != null) {
            String action = event.getAction() == KeyEvent.ACTION_DOWN ? "down" : "up";
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) return true;
            dispatchRokuKey(rokuKey, action);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private String mapHardwareKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return "select";
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE: return "back";
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "play";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "fwd";
            case KeyEvent.KEYCODE_MEDIA_REWIND: return "rev";
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_STAR: return "info";
            default: return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.mobileBackFromAndroid && window.mobileBackFromAndroid();", null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (ecpServer != null) ecpServer.stop();
        ioExecutor.shutdownNow();
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void startEcpServer() {
        ecpServer = new EcpServer(ECP_PORT);
        ecpServer.start();
    }

    private void dispatchRokuKey(String key, String action) {
        if (webView == null) return;
        final String safeKey = JSONObject.quote(key == null ? "" : key);
        final String safeAction = JSONObject.quote(action == null ? "press" : action);
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript("window.nativeRokuKey && window.nativeRokuKey(" + safeKey + "," + safeAction + ");", null);
            }
        });
    }

    private String importPackage(Uri uri) throws IOException {
        String originalName = getDisplayName(uri);
        if (originalName == null || originalName.trim().isEmpty()) originalName = "package.zip";
        String safeName = sanitizeFileName(originalName);
        String lower = safeName.toLowerCase(Locale.US);
        if (!(lower.endsWith(".zip") || lower.endsWith(".bpk") || lower.endsWith(".brs"))) {
            return "";
        }

        String id = UUID.randomUUID().toString() + "_" + safeName;
        File outFile = new File(packagesDir, id);
        File tempFile = new File(packagesDir, id + ".part");
        long total = 0;
        try {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) throw new IOException("Não foi possível ler o arquivo selecionado.");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_PACKAGE_BYTES) {
                        throw new IOException("Pacote excede o limite de 512 MB.");
                    }
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            if (total <= 0) throw new IOException("O pacote está vazio.");
            if (!tempFile.renameTo(outFile)) {
                throw new IOException("Não foi possível concluir a importação do pacote.");
            }
            addRecentPackage(id, originalName, outFile.length());
            return id;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            throw e;
        }
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) return null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) return cursor.getString(idx);
                }
            } catch (Exception ignored) {}
        }
        String path = uri.getLastPathSegment();
        return path == null ? null : path;
    }

    private boolean isSupportedPackageName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".zip") || lower.endsWith(".bpk") || lower.endsWith(".brs");
    }

    private long getContentSize(Uri uri) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) return -1L;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private String sanitizeFileName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        while (safe.contains("..")) safe = safe.replace("..", ".");
        if (safe.length() > 100) safe = safe.substring(safe.length() - 100);
        return safe.isEmpty() ? "package.zip" : safe;
    }

    private void addRecentPackage(String id, String name, long size) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        JSONArray oldArray;
        try {
            oldArray = new JSONArray(prefs.getString(PREF_RECENTS, "[]"));
        } catch (Exception e) {
            oldArray = new JSONArray();
        }
        JSONArray updated = new JSONArray();
        JSONObject fresh = new JSONObject();
        try {
            fresh.put("id", id);
            fresh.put("name", name);
            fresh.put("size", size);
            fresh.put("time", System.currentTimeMillis());
            updated.put(fresh);

            int kept = 1;
            for (int i = 0; i < oldArray.length(); i++) {
                JSONObject item = oldArray.optJSONObject(i);
                if (item == null) continue;
                String oldId = item.optString("id", "");
                String oldName = item.optString("name", "");
                long oldSize = item.optLong("size", -1);
                if (oldName.equals(name) && oldSize == size) {
                    if (!oldId.isEmpty()) new File(packagesDir, oldId).delete();
                    continue;
                }
                if (kept < MAX_RECENTS) {
                    updated.put(item);
                    kept++;
                } else if (!oldId.isEmpty()) {
                    new File(packagesDir, oldId).delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Falha ao atualizar recentes", e);
        }
        prefs.edit().putString(PREF_RECENTS, updated.toString()).apply();
    }

    private void removeRecentPackageInternal(String id) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        JSONArray oldArray;
        try {
            oldArray = new JSONArray(prefs.getString(PREF_RECENTS, "[]"));
        } catch (Exception e) {
            oldArray = new JSONArray();
        }
        JSONArray updated = new JSONArray();
        for (int i = 0; i < oldArray.length(); i++) {
            JSONObject item = oldArray.optJSONObject(i);
            if (item == null) continue;
            String oldId = item.optString("id", "");
            if (oldId.equals(id)) {
                new File(packagesDir, oldId).delete();
            } else {
                updated.put(item);
            }
        }
        prefs.edit().putString(PREF_RECENTS, updated.toString()).apply();
    }


    private void cleanupPackageStorage() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        JSONArray source;
        try {
            source = new JSONArray(prefs.getString(PREF_RECENTS, "[]"));
        } catch (Exception e) {
            source = new JSONArray();
        }

        JSONArray repaired = new JSONArray();
        Set<String> referenced = new HashSet<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            if (id.isEmpty()) continue;
            try {
                File file = safePackageFile(id);
                if (file != null && file.isFile()) {
                    repaired.put(item);
                    referenced.add(id);
                }
            } catch (Exception ignored) {}
        }
        if (!repaired.toString().equals(source.toString())) {
            prefs.edit().putString(PREF_RECENTS, repaired.toString()).apply();
        }

        File[] files = packagesDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (name.endsWith(".part") || !referenced.contains(name)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private String getRecentPackagesJsonInternal() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_RECENTS, "[]");
    }

    private String inspectPackage(String id) {
        JSONObject result = new JSONObject();
        try {
            File file = safePackageFile(id);
            result.put("id", id == null ? "" : id);
            if (file == null || !file.exists()) {
                result.put("error", "Pacote recente não encontrado.");
                return result.toString();
            }
            result.put("name", recentNameForId(id));
            result.put("size", file.length());
            String lower = file.getName().toLowerCase(Locale.US);
            result.put("type", lower.endsWith(".bpk") ? "bpk" : lower.endsWith(".brs") ? "brs" : "zip");
            if (lower.endsWith(".bpk")) {
                result.put("protected", true);
                result.put("message", "BPK protegido: conteúdo interno não é inspecionado sem descriptografia.");
                return result.toString();
            }
            if (lower.endsWith(".brs")) {
                result.put("scripts", new JSONArray().put(recentNameForId(id)));
                return result.toString();
            }

            JSONObject manifest = new JSONObject();
            JSONArray files = new JSONArray();
            JSONArray components = new JSONArray();
            JSONArray scripts = new JSONArray();
            int totalEntries = 0;
            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    totalEntries++;
                    if (totalEntries > MAX_INSPECT_ENTRIES) {
                        result.put("truncated", true);
                        break;
                    }
                    String entryName = entry.getName();
                    if (files.length() < MAX_INSPECT_LIST_ITEMS) {
                        JSONObject f = new JSONObject();
                        f.put("name", entryName);
                        f.put("size", entry.getSize());
                        files.put(f);
                    }
                    String eLower = entryName.toLowerCase(Locale.US);
                    if (eLower.startsWith("components/") && eLower.endsWith(".xml") && components.length() < MAX_INSPECT_LIST_ITEMS) components.put(entryName);
                    if (eLower.endsWith(".brs") && scripts.length() < MAX_INSPECT_LIST_ITEMS) scripts.put(entryName);
                    if ("manifest".equalsIgnoreCase(entryName)) {
                        String text = readText(zip.getInputStream(entry), 256 * 1024);
                        String[] lines = text.split("\\r?\\n");
                        for (String line : lines) {
                            String clean = line.trim();
                            if (clean.isEmpty() || clean.startsWith("#")) continue;
                            int eq = clean.indexOf('=');
                            if (eq <= 0) continue;
                            manifest.put(clean.substring(0, eq).trim(), clean.substring(eq + 1).trim());
                        }
                    }
                }
            }
            result.put("manifest", manifest);
            result.put("files", files);
            result.put("components", components);
            result.put("scripts", scripts);
            result.put("entryCount", totalEntries);
        } catch (Exception e) {
            try { result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage()); } catch (Exception ignored) {}
        }
        return result.toString();
    }

    private String recentNameForId(String id) {
        try {
            JSONArray arr = new JSONArray(getRecentPackagesJsonInternal());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) return o.optString("name", id);
            }
        } catch (Exception ignored) {}
        return id == null ? "" : id;
    }

    private File safePackageFile(String id) throws IOException {
        if (id == null || id.isEmpty()) return null;
        File file = new File(packagesDir, id);
        String root = packagesDir.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(root)) return null;
        return file;
    }

    private String readText(InputStream in, int maxBytes) throws IOException {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) != -1 && total < maxBytes) {
                int count = Math.min(read, maxBytes - total);
                out.write(buffer, 0, count);
                total += count;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void notifyRecentPackagesChanged(String id, boolean autoRun) {
        if (webView == null) return;
        runOnUiThread(() -> {
            if (webView == null) return;
            String js = "window.__brsNativeLastPackageId=" + JSONObject.quote(id == null ? "" : id) + ";" +
                    "window.dispatchEvent(new CustomEvent('brs-recents-changed',{detail:{id:" + JSONObject.quote(id == null ? "" : id) + ",autoRun:" + autoRun + "}}));";
            webView.evaluateJavascript(js, null);
        });
    }

    private String getLocalIpv4() {
        String lanFallback = "";
        String anyFallback = "";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String interfaceName = ni.getName() == null ? "" : ni.getName().toLowerCase(Locale.US);
                boolean preferredLan = interfaceName.startsWith("wlan") || interfaceName.startsWith("wifi") ||
                        interfaceName.startsWith("eth") || interfaceName.startsWith("en");
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null || host.isEmpty()) continue;
                    if (addr.isSiteLocalAddress()) {
                        if (preferredLan) return host;
                        if (lanFallback.isEmpty()) lanFallback = host;
                    } else if (anyFallback.isEmpty()) {
                        anyFallback = host;
                    }
                }
            }
        } catch (Exception ignored) {}
        if (!lanFallback.isEmpty()) return lanFallback;
        return anyFallback.isEmpty() ? "127.0.0.1" : anyFallback;
    }

    private String runtimeJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("android", Build.VERSION.RELEASE);
            obj.put("sdk", Build.VERSION.SDK_INT);
            obj.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            obj.put("isolationAllowlist", isolationAllowlistEnabled);
            obj.put("ecpPort", ECP_PORT);
            obj.put("ecpRunning", ecpServer != null && ecpServer.isRunning());
            obj.put("ip", getLocalIpv4());
            PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
            if (webViewPackage != null) {
                obj.put("webViewPackage", webViewPackage.packageName);
                obj.put("webViewVersion", webViewPackage.versionName);
            }
        } catch (Exception ignored) {}
        return obj.toString();
    }

    private void saveLogToDownloads(String text) {
        String name = "RokuDev-log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RokuDev");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Falha ao criar arquivo em Downloads.");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Falha ao abrir arquivo de log.");
                    out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Log salvo em Downloads/RokuDev.", Toast.LENGTH_LONG).show();
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = getFilesDir();
                File file = new File(dir, name);
                try (OutputStream out = new FileOutputStream(file)) {
                    out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Log salvo em " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Falha ao salvar log", e);
            Toast.makeText(this, "Falha ao salvar log: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setSystemBarsHidden(boolean hidden) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (hidden) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            if (hidden) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    private void reportCoreFlixUpdate(String state, String message, String version) {
        try {
            JSONObject data = new JSONObject();
            data.put("state", state); data.put("message", message); data.put("version", version);
            coreFlixUpdateStatus = data.toString();
            final String json = coreFlixUpdateStatus;
            runOnUiThread(() -> {
                if (webView != null) webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('coreflix-update',{detail:" + json + "}));", null);
            });
        } catch (Exception ignored) { }
    }

    private void fetchCoreFlixFile(String address, OutputStream out, long limit) throws IOException {
        URL url = CoreFlixPackage.trustedUrl(address);
        for (int redirect = 0; redirect < 4; redirect++) {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            try {
                connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("User-Agent", "RokuDev-CoreFlix-Updater/1.0");
                int status = connection.getResponseCode();
                if (status >= 300 && status <= 399) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirecionamento sem destino.");
                    url = CoreFlixPackage.trustedUrl(new URL(url, location).toString());
                    continue;
                }
                if (status == 404) throw new IOException("Arquivo ainda não publicado no site (HTTP 404).");
                if (status != 200) throw new IOException("Servidor respondeu HTTP " + status + ".");
                if (connection.getContentLengthLong() > limit) throw new IOException("Download excede o limite permitido.");
                long total = 0; long deadline = System.currentTimeMillis() + 180000;
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[32768]; int n;
                    while ((n = input.read(buffer)) != -1) {
                        total += n;
                        if (total > limit) throw new IOException("Download excede o limite permitido.");
                        if (System.currentTimeMillis() > deadline) throw new IOException("Tempo de download excedido.");
                        out.write(buffer, 0, n);
                    }
                }
                if (total == 0) throw new IOException("Arquivo vazio no servidor.");
                return;
            } finally {connection.disconnect();}
        }
        throw new IOException("Muitos redirecionamentos no servidor.");
    }

    private long installedCoreFlixVersion() {
        long code = 0;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            File remembered = safePackageFile(prefs.getString("coreflix_update_package", ""));
            if (remembered != null && remembered.isFile()) code = prefs.getLong("coreflix_update_version", 0);
            if (activeAppRunning && "CoreFlix".equalsIgnoreCase(activeAppTitle))
                code = Math.max(code, CoreFlixPackage.versionCode(activeAppVersion));
        } catch (Exception ignored) { }
        return code;
    }

    private void runCoreFlixUpdate(boolean install) {
        if (!coreFlixUpdateBusy.compareAndSet(false, true)) return;
        reportCoreFlixUpdate(install ? "downloading" : "checking", install ? "Baixando e verificando o pacote…" : "Consultando atualizações…", "");
        try {
            ioExecutor.execute(() -> {
                File temporary = null;
                try {
                    if (!install) {
                        coreFlixAvailableUpdate = null;
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        fetchCoreFlixFile(CoreFlixPackage.FEED, bytes, 65536);
                        JSONObject feed = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                        if (feed.getInt("schemaVersion") != 1 || !"coreflix-roku".equals(feed.getString("appId")))
                            throw new IOException("Arquivo de atualização incompatível.");
                        String version = feed.getString("version");
                        long code = CoreFlixPackage.versionCode(version);
                        CoreFlixPackage.trustedUrl(feed.getString("packageUrl"));
                        if (!feed.getString("sha256").matches("[a-fA-F0-9]{64}")) throw new IOException("SHA-256 ausente ou inválido.");
                        if (code <= installedCoreFlixVersion()) {
                            reportCoreFlixUpdate("current", "A versão disponível já está carregada ou salva neste aparelho.", version);
                        } else {
                            coreFlixAvailableUpdate = feed;
                            reportCoreFlixUpdate("available", "CoreFlix " + version + " disponível. Baixar e abrir encerra o app em execução.", version);
                        }
                    } else {
                        if (coreFlixAvailableUpdate == null) throw new IOException("Verifique as atualizações primeiro.");
                        JSONObject feed = coreFlixAvailableUpdate;
                        String version = feed.getString("version");
                        if (CoreFlixPackage.versionCode(version) <= installedCoreFlixVersion()) throw new IOException("Esta versão já está disponível no aparelho. Abra-a em Pacotes recentes.");
                        temporary = new File(getCacheDir(), "CoreFlix-Roku-" + version + ".zip");
                        try (OutputStream output = new FileOutputStream(temporary)) {fetchCoreFlixFile(feed.getString("packageUrl"), output, CoreFlixPackage.MAX_BYTES);}
                        CoreFlixPackage.validate(temporary, feed.getString("sha256"), version);
                        String id = importPackage(Uri.fromFile(temporary));
                        if (id.isEmpty()) throw new IOException("Não foi possível importar a atualização.");
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString("coreflix_update_package", id)
                                .putLong("coreflix_update_version", CoreFlixPackage.versionCode(version)).apply();
                        coreFlixAvailableUpdate = null;
                        reportCoreFlixUpdate("installed", "Pacote verificado e salvo em recentes. Abrindo CoreFlix…", version);
                        notifyRecentPackagesChanged(id, true);
                    }
                } catch (Exception error) {
                    coreFlixAvailableUpdate = null;
                    reportCoreFlixUpdate("error", "Atualização não concluída: " + (error.getMessage() == null ? "falha de conexão ou arquivo inválido." : error.getMessage()), "");
                } finally {
                    if (temporary != null) temporary.delete();
                    coreFlixUpdateBusy.set(false);
                }
            });
        } catch (RejectedExecutionException error) {
            coreFlixUpdateBusy.set(false);
            reportCoreFlixUpdate("error", "O emulador está encerrando. Abra novamente para atualizar.", "");
        }
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public void checkCoreFlixUpdate() {runCoreFlixUpdate(false);}
        @JavascriptInterface
        public void installCoreFlixUpdate() {runCoreFlixUpdate(true);}
        @JavascriptInterface
        public String getCoreFlixUpdateStatus() {return coreFlixUpdateStatus;}
        @JavascriptInterface
        public void haptic() {
            runOnUiThread(() -> {
                if (webView != null) webView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            });
        }

        @JavascriptInterface
        public void toggleOrientation() {
            runOnUiThread(() -> {
                int current = getResources().getConfiguration().orientation;
                if (current == Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
            });
        }

        @JavascriptInterface
        public void setImmersive(boolean hidden) {
            runOnUiThread(() -> setSystemBarsHidden(hidden));
        }

        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("RokuDev", text == null ? "" : text));
                Toast.makeText(MainActivity.this, "Copiado.", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void shareText(String text) {
            runOnUiThread(() -> {
                try {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                    startActivity(Intent.createChooser(share, "Compartilhar log"));
                } catch (Exception e) {
                    Log.w(TAG, "Falha ao compartilhar log", e);
                    Toast.makeText(MainActivity.this, "Não foi possível compartilhar o log.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void saveLog(String text) {
            runOnUiThread(() -> saveLogToDownloads(text));
        }

        @JavascriptInterface
        public boolean isolationAllowlistEnabled() {
            return isolationAllowlistEnabled;
        }

        @JavascriptInterface
        public String getRecentPackagesJson() {
            return getRecentPackagesJsonInternal();
        }

        @JavascriptInterface
        public String getLastImportedPackageId() {
            return lastImportedPackageId == null ? "" : lastImportedPackageId;
        }

        @JavascriptInterface
        public String getPackageInspectionJson(String id) {
            return inspectPackage(id);
        }

        @JavascriptInterface
        public void removeRecentPackage(String id) {
            removeRecentPackageInternal(id);
            runOnUiThread(() -> notifyRecentPackagesChanged("", false));
        }

        @JavascriptInterface
        public String getRuntimeJson() {
            return runtimeJson();
        }

        @JavascriptInterface
        public void setDisplayMode(String mode) {
            if ("480p".equals(mode) || "720p".equals(mode) || "1080p".equals(mode)) currentDisplayMode = mode;
        }

        @JavascriptInterface
        public void setActiveApp(String title, String version, boolean running) {
            activeAppTitle = title == null || title.isEmpty() ? "RokuDev" : title;
            activeAppVersion = version == null ? "" : version;
            activeAppRunning = running;
        }

        @JavascriptInterface
        public void openExternalUrl(String value) {
            if (value == null) return;
            runOnUiThread(() -> {
                try {
                    Uri uri = Uri.parse(value);
                    String scheme = uri.getScheme();
                    if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return;
                    Intent external = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(external);
                } catch (Exception e) {
                    Log.w(TAG, "Falha ao abrir URL externa", e);
                    Toast.makeText(MainActivity.this, "Não foi possível abrir a URL externa.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(MainActivity.this::finish);
        }
    }

    private final class InternalPackagePathHandler implements WebViewAssetLoader.PathHandler {
        @Override
        public WebResourceResponse handle(String path) {
            try {
                String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
                File file = safePackageFile(decoded);
                if (file == null || !file.exists() || !file.isFile()) return null;
                String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime == null) mime = "application/octet-stream";
                return new WebResourceResponse(mime, null, new FileInputStream(file));
            } catch (Exception e) {
                Log.e(TAG, "Falha ao servir pacote recente", e);
                return null;
            }
        }
    }

    private final class LocalAssetClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null || !request.isForMainFrame()) return false;
            Uri uri = request.getUrl();
            if (uri == null) return true;
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(scheme) && "appassets.androidplatform.net".equalsIgnoreCase(host)) {
                return false;
            }
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                new AndroidBridge().openExternalUrl(uri.toString());
            } else {
                Log.w(TAG, "Navegação externa bloqueada: " + uri);
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            WebResourceResponse response = assetLoader.shouldInterceptRequest(request.getUrl());
            if (response == null) return super.shouldInterceptRequest(view, request);

            Map<String, String> headers = new HashMap<>();
            if (response.getResponseHeaders() != null) headers.putAll(response.getResponseHeaders());
            headers.put("Document-Isolation-Policy", "isolate-and-credentialless");
            headers.put("Cross-Origin-Resource-Policy", "same-origin");
            headers.put("Cache-Control", "no-cache");
            response.setResponseHeaders(headers);
            return response;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String pending = pendingPackageId == null ? "" : pendingPackageId;
            String js = "window.__brsNativeIsolationAllowlisted=" + (isolationAllowlistEnabled ? "true" : "false") + ";" +
                    "window.__brsNativeLastPackageId=" + JSONObject.quote(lastImportedPackageId == null ? "" : lastImportedPackageId) + ";" +
                    "window.__brsPendingPackageId=" + JSONObject.quote(pending) + ";" +
                    "window.dispatchEvent(new Event('brs-native-ready'));";
            view.evaluateJavascript(js, null);
            pendingPackageId = "";
        }
    }

    private final class EcpServer {
        private final int port;
        private final ExecutorService clients = new ThreadPoolExecutor(
                2, 4, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16),
                new ThreadPoolExecutor.AbortPolicy());
        private volatile boolean running = false;
        private ServerSocket serverSocket;
        private Thread acceptThread;

        EcpServer(int port) {
            this.port = port;
        }

        void start() {
            acceptThread = new Thread(() -> {
                try {
                    serverSocket = new ServerSocket();
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
                    running = true;
                    Log.i(TAG, "ECP ativo em " + getLocalIpv4() + ":" + port);
                    while (running) {
                        Socket socket = serverSocket.accept();
                        try {
                            clients.execute(() -> handleClient(socket));
                        } catch (RejectedExecutionException rejected) {
                            try { socket.close(); } catch (Exception ignored) {}
                            Log.w(TAG, "ECP recusou conexão: limite de clientes atingido.");
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "ECP indisponível na porta " + port, e);
                } finally {
                    running = false;
                }
            }, "RokuDev-ECP-Accept");
            acceptThread.start();
        }

        boolean isRunning() {
            return running;
        }

        void stop() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
            clients.shutdownNow();
        }

        private void handleClient(Socket socket) {
            try (Socket s = socket;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
                s.setSoTimeout(3000);
                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isEmpty() || requestLine.length() > 2048) return;
                String line;
                int headerCount = 0;
                int headerChars = 0;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    headerCount++;
                    headerChars += line.length();
                    if (headerCount > 64 || headerChars > 16384) return;
                }

                String[] parts = requestLine.split(" ");
                String method = parts.length > 0 ? parts[0] : "GET";
                String rawPath = parts.length > 1 ? parts[1] : "/";
                int query = rawPath.indexOf('?');
                if (query >= 0) rawPath = rawPath.substring(0, query);
                String path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name());

                int status = 200;
                String contentType = "text/plain; charset=utf-8";
                String body = "";

                if ("OPTIONS".equalsIgnoreCase(method)) {
                    body = "";
                } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/keypress/")) {
                    dispatchRokuKey(normalizeEcpKey(path.substring("/keypress/".length())), "press");
                } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/keydown/")) {
                    dispatchRokuKey(normalizeEcpKey(path.substring("/keydown/".length())), "down");
                } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/keyup/")) {
                    dispatchRokuKey(normalizeEcpKey(path.substring("/keyup/".length())), "up");
                } else if ("GET".equalsIgnoreCase(method) && "/query/device-info".equals(path)) {
                    contentType = "application/xml; charset=utf-8";
                    body = deviceInfoXml();
                } else if ("GET".equalsIgnoreCase(method) && "/query/active-app".equals(path)) {
                    contentType = "application/xml; charset=utf-8";
                    body = activeAppXml();
                } else if ("GET".equalsIgnoreCase(method) && "/query/apps".equals(path)) {
                    contentType = "application/xml; charset=utf-8";
                    body = appsXml();
                } else if ("GET".equalsIgnoreCase(method) && "/".equals(path)) {
                    body = "RokuDev ECP 1.2.5";
                } else {
                    status = 404;
                    body = "Not Found";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                writer.write("HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\n");
                writer.write("Content-Type: " + contentType + "\r\n");
                writer.write("Content-Length: " + bytes.length + "\r\n");
                writer.write("Access-Control-Allow-Origin: *\r\n");
                writer.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
                writer.write("Access-Control-Allow-Headers: Content-Type\r\n");
                writer.write("Connection: close\r\n\r\n");
                writer.write(body);
                writer.flush();
            } catch (Exception e) {
                Log.d(TAG, "Cliente ECP desconectado: " + e.getMessage());
            }
        }

        private String normalizeEcpKey(String key) {
            if (key == null) return "";
            // Roku ECP uses Lit_<character> for keyboard input. Do not trim or
            // lowercase the literal character (Lit_%20 must remain a space).
            if (key.regionMatches(true, 0, "Lit_", 0, 4) && key.length() == 5) {
                return "Lit_" + key.substring(4);
            }
            String k = key.trim().toLowerCase(Locale.US);
            switch (k) {
                case "home": return "home";
                case "rev": return "rev";
                case "fwd": return "fwd";
                case "play": return "play";
                case "select": return "select";
                case "left": return "left";
                case "right": return "right";
                case "down": return "down";
                case "up": return "up";
                case "back": return "back";
                case "instantreplay": return "instantreplay";
                case "info": return "info";
                case "backspace": return "backspace";
                case "enter": return "enter";
                default: return k;
            }
        }
    }

    private String deviceInfoXml() {
        String ip = getLocalIpv4();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<device-info>" +
                "<udn>uuid:rokudev-simulator</udn>" +
                "<serial-number>ROKUDEV120</serial-number>" +
                "<device-id>ROKUDEV120</device-id>" +
                "<vendor-name>RokuDev</vendor-name>" +
                "<model-name>RokuDev</model-name>" +
                "<model-number>ROKUDEV-ANDROID</model-number>" +
                "<network-type>wifi</network-type>" +
                "<friendly-device-name>RokuDev</friendly-device-name>" +
                "<ui-resolution>" + xmlEscape(currentDisplayMode) + "</ui-resolution>" +
                "<developer-enabled>true</developer-enabled>" +
                "<power-mode>PowerOn</power-mode>" +
                "<ip-address>" + xmlEscape(ip) + "</ip-address>" +
                "</device-info>";
    }

    private String activeAppXml() {
        if (!activeAppRunning) return "<?xml version=\"1.0\"?><active-app><app>RokuDev</app></active-app>";
        return "<?xml version=\"1.0\"?><active-app><app id=\"dev\" version=\"" + xmlEscape(activeAppVersion) + "\">" + xmlEscape(activeAppTitle) + "</app></active-app>";
    }

    private String appsXml() {
        String title = activeAppRunning ? activeAppTitle : "RokuDev";
        String version = activeAppRunning ? activeAppVersion : "1.2.5";
        return "<?xml version=\"1.0\"?><apps><app id=\"dev\" type=\"dev\" version=\"" + xmlEscape(version) + "\">" + xmlEscape(title) + "</app></apps>";
    }

    private String xmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
