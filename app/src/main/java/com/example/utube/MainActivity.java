package com.example.utube;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.MobileAds;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String PROXY_HOST = "31.77.248.230";
    private static final int PROXY_PORT = 3128;
    private static final String UPDATE_URL = "http://31.77.248.230/version.json";
    private static final String WHITELIST_URL = "http://31.77.248.230:8081/allow-UtubeSecret2026xK9pL";
    private static final String YANDEX_AD_UNIT_ID = "R-M-20213816-1";

    private WebView wv;
    private FrameLayout rootLayout;
    private FrameLayout bannerContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private BannerAdView bannerView;

    private static final String[] AD_DOMAINS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "ads.youtube.com", "pagead2.googlesyndication.com",
            "tpc.googlesyndication.com", "analytics.google.com", "googletagmanager.com",
            "googletagservices.com", "google-analytics.com", "youtube.com/ptracking",
            "youtube.com/pagead", "youtube.com/get_midroll_info",
            "youtube.com/api/stats/ads", "youtube.com/api/stats/qoe"
    };

    private String pureMime(String ct) {
        if (ct == null) return "text/html";
        int i = ct.indexOf(';');
        if (i > 0) ct = ct.substring(0, i);
        return ct.trim();
    }

    private boolean isAd(String url) {
        for (String d : AD_DOMAINS) {
            if (url.contains(d)) return true;
        }
        return false;
    }

    private boolean shouldProxy(String url) {
        return url.startsWith("https://m.youtube.com")
                || url.startsWith("https://www.youtube.com")
                || url.startsWith("https://youtube.com")
                || url.startsWith("https://youtu.be")
                || url.startsWith("https://i.ytimg.com")
                || url.startsWith("https://yt3.ggpht.com")
                || url.startsWith("https://rr")
                || url.startsWith("https://youtubei.googleapis.com")
                || url.startsWith("https://fonts.gstatic.com")
                || url.startsWith("https://fonts.googleapis.com");
    }

    private void hideFullscreen() {
        if (fullscreenView == null) return;
        rootLayout.removeView(fullscreenView);
        fullscreenView = null;
        wv.setVisibility(View.VISIBLE);
        if (bannerContainer.getChildCount() > 0) {
            bannerContainer.setVisibility(View.VISIBLE);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
    }

    private void registerIp() {
        new Thread(() -> {
            try {
                URL u = new URL(WHITELIST_URL);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.getResponseCode();
            } catch (Exception e) {}
        }).start();
    }

    private void initYandexAds() {
        MobileAds.initialize(this, this::loadYandexBanner);
    }

    private void loadYandexBanner() {
        bannerView = new BannerAdView(this);
        bannerView.setAdUnitId(YANDEX_AD_UNIT_ID);
        bannerView.setAdSize(BannerAdSize.stickySize(this, 320));

        bannerView.setBannerAdEventListener(new BannerAdEventListener() {
            @Override
            public void onAdLoaded() {
                bannerContainer.removeAllViews();
                bannerContainer.addView(bannerView);
                bannerContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAdFailedToLoad(AdRequestError adRequestError) {
                bannerContainer.setVisibility(View.GONE);
            }

            @Override public void onAdClicked() {}
            @Override public void onLeftApplication() {}
            @Override public void onReturnedToApplication() {}
            @Override public void onImpression(ImpressionData impressionData) {}
        });

        AdRequest adRequest = new AdRequest.Builder().build();
        bannerView.loadAd(adRequest);
    }

    private void checkUpdate() {
        final String localKey = BuildConfig.BUILD_KEY;
        new Thread(() -> {
            try {
                URL u = new URL(UPDATE_URL);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                InputStream is = c.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n));
                is.close();

                JSONObject obj = new JSONObject(sb.toString());
                String serverKey = obj.getString("key");
                String apkUrl = obj.getString("url");

                if (!serverKey.equals(localKey)) {
                    new Handler(Looper.getMainLooper()).post(() -> showUpdateDialog(apkUrl));
                }
            } catch (Exception e) {}
        }).start();
    }

    private void showUpdateDialog(String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Доступно обновление")
                .setMessage("Вышла новая версия приложения. Обновить?")
                .setCancelable(false)
                .setPositiveButton("Обновить", (d, w) -> downloadAndInstall(apkUrl))
                .setNegativeButton("Позже", (d, w) -> d.dismiss())
                .show();
    }

    private void downloadAndInstall(String apkUrl) {
        Toast.makeText(this, "Скачиваю обновление...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL u = new URL(apkUrl);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                InputStream is = c.getInputStream();

                File apkFile = new File(getCacheDir(), "update.apk");
                FileOutputStream fos = new FileOutputStream(apkFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                is.close();

                new Handler(Looper.getMainLooper()).post(() -> installApk(apkFile));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void installApk(File apkFile) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка установки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void injectScripts(WebView view) {
        String script =
                "(function(){" +
                        "  if (window.__myTubeInstalled) return;" +
                        "  window.__myTubeInstalled = true;" +
                        "  var FIELDS = ['adPlacements','playerAds','adSlots','adBreakHeartbeatParams','adBreakServiceParams','adParams','adThumbnail','adVideoId','adQueryParams'];" +
                        "  function replaceField(t, name){" +
                        "    var key = '\"' + name + '\":';" +
                        "    var i = t.indexOf(key);" +
                        "    if (i === -1) return t;" +
                        "    var start = i + key.length;" +
                        "    var ch = t.charAt(start);" +
                        "    if (ch === '[') {" +
                        "      var depth = 0, j = start, inStr = false, esc = false;" +
                        "      while (j < t.length) {" +
                        "        var c = t.charAt(j);" +
                        "        if (esc) { esc = false; j++; continue; }" +
                        "        if (c === '\\\\') { esc = true; j++; continue; }" +
                        "        if (c === '\"') { inStr = !inStr; j++; continue; }" +
                        "        if (!inStr) {" +
                        "          if (c === '[') depth++;" +
                        "          else if (c === ']') { depth--; if (depth === 0) { j++; break; } }" +
                        "        }" +
                        "        j++;" +
                        "      }" +
                        "      return t.substring(0, i) + key + '[]' + t.substring(j);" +
                        "    }" +
                        "    if (ch === '{') {" +
                        "      var depth2 = 0, k = start, inStr2 = false, esc2 = false;" +
                        "      while (k < t.length) {" +
                        "        var c2 = t.charAt(k);" +
                        "        if (esc2) { esc2 = false; k++; continue; }" +
                        "        if (c2 === '\\\\') { esc2 = true; k++; continue; }" +
                        "        if (c2 === '\"') { inStr2 = !inStr2; k++; continue; }" +
                        "        if (!inStr2) {" +
                        "          if (c2 === '{') depth2++;" +
                        "          else if (c2 === '}') { depth2--; if (depth2 === 0) { k++; break; } }" +
                        "        }" +
                        "        k++;" +
                        "      }" +
                        "      return t.substring(0, i) + key + '{}' + t.substring(k);" +
                        "    }" +
                        "    return t;" +
                        "  }" +
                        "  function clean(t){" +
                        "    if (!t || typeof t !== 'string') return t;" +
                        "    for (var x = 0; x < FIELDS.length; x++) t = replaceField(t, FIELDS[x]);" +
                        "    return t;" +
                        "  }" +
                        "  function isPlayer(u){" +
                        "    return u && (u.indexOf('/youtubei/v1/player') !== -1 || u.indexOf('/youtubei/v1/next') !== -1 || u.indexOf('/youtubei/v1/get_watch') !== -1);" +
                        "  }" +
                        "  var of = window.fetch;" +
                        "  window.fetch = function(input, init){" +
                        "    var u = (typeof input === 'string') ? input : (input && input.url ? input.url : '');" +
                        "    var p = of.apply(this, arguments);" +
                        "    if (isPlayer(u)) {" +
                        "      return p.then(function(r){" +
                        "        return r.text().then(function(t){" +
                        "          var c = clean(t);" +
                        "          return new Response(c, {status: r.status, statusText: r.statusText, headers: r.headers});" +
                        "        });" +
                        "      });" +
                        "    }" +
                        "    return p;" +
                        "  };" +
                        "  var oo = XMLHttpRequest.prototype.open;" +
                        "  var os = XMLHttpRequest.prototype.send;" +
                        "  XMLHttpRequest.prototype.open = function(m, u){" +
                        "    this.__isPlayer = isPlayer(u);" +
                        "    return oo.apply(this, arguments);" +
                        "  };" +
                        "  XMLHttpRequest.prototype.send = function(){" +
                        "    if (this.__isPlayer) {" +
                        "      var xhr = this;" +
                        "      var rsc = xhr.onreadystatechange;" +
                        "      xhr.onreadystatechange = function(){" +
                        "        if (xhr.readyState === 4 && xhr.status === 200) {" +
                        "          try {" +
                        "            var c = clean(xhr.responseText);" +
                        "            Object.defineProperty(xhr, 'responseText', {value: c, writable: false, configurable: true});" +
                        "          } catch(e){}" +
                        "        }" +
                        "        if (rsc) rsc.apply(xhr, arguments);" +
                        "      };" +
                        "    }" +
                        "    return os.apply(this, arguments);" +
                        "  };" +
                        "  setInterval(function(){" +
                        "    try{" +
                        "      var skip = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');" +
                        "      for (var i=0; i<skip.length; i++) skip[i].click();" +
                        "      var banner = document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-image-overlay, .ytp-ad-text-overlay');" +
                        "      for (var j=0; j<banner.length; j++) banner[j].style.display='none';" +
                        "    }catch(e){}" +
                        "  }, 1000);" +
                        "})();";
        view.evaluateJavascript(script, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Locale.setDefault(new Locale("ru", "RU"));
        android.content.res.Configuration cfg = getResources().getConfiguration();
        cfg.setLocale(new Locale("ru", "RU"));
        getResources().updateConfiguration(cfg, getResources().getDisplayMetrics());

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);

        registerIp();
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() { registerIp(); }
        }, 3 * 60 * 1000, 3 * 60 * 1000);

        checkUpdate();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyConfig config = new ProxyConfig.Builder()
                        .addProxyRule(PROXY_HOST + ":" + PROXY_PORT)
                        .build();
                ProxyController.getInstance().setProxyOverride(
                        config,
                        Executors.newSingleThreadExecutor(),
                        () -> {}
                );
            } catch (Exception e) {}
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        wv = new WebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(wv);

        bannerContainer = new FrameLayout(this);
        bannerContainer.setVisibility(View.GONE);
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerParams.gravity = Gravity.BOTTOM;
        bannerContainer.setLayoutParams(bannerParams);
        rootLayout.addView(bannerContainer);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                rootLayout.addView(view);
                wv.setVisibility(View.GONE);
                bannerContainer.setVisibility(View.GONE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }
        });

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectScripts(view);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String method = request.getMethod();
                if (!url.startsWith("http")) return null;

                if (isAd(url)) {
                    Map<String, String> h = new HashMap<>();
                    return new WebResourceResponse("text/plain", "utf-8", 204, "No Content",
                            h, new ByteArrayInputStream(new byte[0]));
                }

                if (!shouldProxy(url)) return null;
                if (!"GET".equalsIgnoreCase(method)) return null;

                try {
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection(proxy);
                    conn.setRequestMethod("GET");
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);

                    for (Map.Entry<String, String> h : request.getRequestHeaders().entrySet()) {
                        String key = h.getKey();
                        if (key.equalsIgnoreCase("Accept-Encoding")) continue;
                        if (key.equalsIgnoreCase("Host")) continue;
                        if (key.equalsIgnoreCase("Cookie")) continue;
                        if (key.equalsIgnoreCase("Content-Length")) continue;
                        if (key.equalsIgnoreCase("Accept-Language")) continue;
                        conn.setRequestProperty(key, h.getValue());
                    }
                    conn.setRequestProperty("Accept-Encoding", "identity");
                    conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5");

                    String cookie = cm.getCookie(url);
                    if (cookie != null) conn.setRequestProperty("Cookie", cookie);

                    int code = conn.getResponseCode();
                    String ct = conn.getContentType();
                    if (ct == null) ct = "text/html";
                    String mime = pureMime(ct);

                    String msg = conn.getResponseMessage();
                    if (msg == null || msg.isEmpty()) msg = "OK";

                    Map<String, String> headers = new HashMap<>();
                    for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                        if (e.getKey() == null || e.getValue().isEmpty()) continue;
                        String k = e.getKey();
                        if (k.equalsIgnoreCase("Content-Encoding")) continue;
                        if (k.equalsIgnoreCase("Content-Length")) continue;
                        if (k.equalsIgnoreCase("Transfer-Encoding")) continue;
                        if (k.equalsIgnoreCase("Content-Type")) continue;
                        if (k.equalsIgnoreCase("Set-Cookie")) {
                            for (String v : e.getValue()) cm.setCookie(url, v);
                            continue;
                        }
                        String v = e.getValue().get(0);
                        if (v != null && !v.isEmpty()) headers.put(k, v);
                    }

                    InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    if (is == null) is = new ByteArrayInputStream(new byte[0]);

                    String enc = "utf-8";
                    if (mime.startsWith("image") || mime.startsWith("video") || mime.startsWith("audio")) enc = null;

                    return new WebResourceResponse(mime, enc, code, msg, headers, is);
                } catch (Exception e) {
                    return null;
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenView != null) {
                    hideFullscreen();
                } else if (wv.canGoBack()) {
                    wv.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        initYandexAds();

        cm.setCookie("https://m.youtube.com", "PREF=hl=ru&gl=RU; path=/; domain=.youtube.com");
        cm.setCookie("https://www.youtube.com", "PREF=hl=ru&gl=RU; path=/; domain=.youtube.com");
        cm.setCookie("https://youtube.com", "PREF=hl=ru&gl=RU; path=/; domain=.youtube.com");

        wv.loadUrl("https://m.youtube.com/?hl=ru&gl=RU");
    }

    @Override
    protected void onDestroy() {
        if (bannerView != null) {
            bannerView.destroy();
            bannerView = null;
        }
        super.onDestroy();
    }
}