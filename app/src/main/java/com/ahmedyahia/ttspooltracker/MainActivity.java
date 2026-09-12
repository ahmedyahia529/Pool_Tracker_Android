package com.ahmedyahia.ttspooltracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import android.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String DRIVE_FOLDER_ID = "1UEV2NEAw4oilA2gN1oF8ixqnKD4gD8fm";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        loadHome();
    }

    private void loadHome() { webView.loadUrl("file:///android_asset/home.html"); }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public class AndroidBridge {
        @JavascriptInterface public void getLatestReport() {
            runOnUiThread(() -> webView.evaluateJavascript("setStatus('Fetching today\\'s report…')", null));
            executor.execute(() -> {
                try {
                    String report = fetchLatestTodayReport();
                    final String quoted = JSONObject.quote(report);
                    runOnUiThread(() -> {
                        webView.loadUrl("file:///android_asset/dashboard.html");
                        webView.postDelayed(() -> webView.evaluateJavascript("onReportLoaded(" + quoted + ")", null), 250);
                    });
                } catch (Exception e) {
                    final String msg = e.getMessage() == null ? "Unknown error" : e.getMessage();
                    runOnUiThread(() -> webView.evaluateJavascript("setStatus(" + JSONObject.quote("Error: " + msg) + ")", null));
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Report unavailable", Toast.LENGTH_SHORT).show());
                }
            });
        }
        @JavascriptInterface public void backHome() { runOnUiThread(() -> loadHome()); }
    }

    private String fetchLatestTodayReport() throws Exception {
        String token = getAccessToken();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String name = "session_" + today + ".json";
        String q = "'" + DRIVE_FOLDER_ID + "' in parents and trashed = false and name = '" + name + "'";
        String listUrl = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(q, "UTF-8")
                + "&orderBy=modifiedTime desc&pageSize=10&fields=files(id,name,modifiedTime)";
        JSONObject listed = httpJson("GET", listUrl, token, null);
        org.json.JSONArray files = listed.optJSONArray("files");
        if (files == null || files.length() == 0) throw new Exception("No report uploaded for today (" + today + ").");
        String fileId = files.getJSONObject(0).getString("id");
        String downloadUrl = "https://www.googleapis.com/drive/v3/files/" + URLEncoder.encode(fileId, "UTF-8") + "?alt=media";
        return httpText("GET", downloadUrl, token, null);
    }

    private String getAccessToken() throws Exception {
        JSONObject c = new JSONObject(readAsset("SOC-Google-API-credentials.json"));
        String clientEmail = c.getString("client_email");
        String privateKeyPem = c.getString("private_key");
        String tokenUri = c.optString("token_uri", DEFAULT_TOKEN_URI);
        long now = System.currentTimeMillis() / 1000L;
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = "{\"iss\":\"" + jsonEscape(clientEmail) + "\",\"scope\":\"" + DRIVE_SCOPE + "\",\"aud\":\"" + jsonEscape(tokenUri) + "\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}";
        String unsigned = header + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        PrivateKey key = privateKeyFromPem(privateKeyPem);
        java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(key); signer.update(unsigned.getBytes(StandardCharsets.UTF_8));
        String jwt = unsigned + "." + base64Url(signer.sign());
        String body = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") + "&assertion=" + URLEncoder.encode(jwt, "UTF-8");
        JSONObject token = httpJson("POST", tokenUri, null, body, "application/x-www-form-urlencoded");
        String access = token.optString("access_token", "");
        if (access.isEmpty()) throw new Exception("Google OAuth token was not returned.");
        return access;
    }

    private PrivateKey privateKeyFromPem(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.decode(clean, Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name); return readAll(in);
    }
    private String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line); r.close(); return b.toString();
    }
    private String base64Url(byte[] data) { return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
    private String jsonEscape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    private JSONObject httpJson(String method, String url, String token, String body) throws Exception { return httpJson(method,url,token,body,"application/x-www-form-urlencoded"); }
    private JSONObject httpJson(String method, String url, String token, String body, String contentType) throws Exception {
        return new JSONObject(httpText(method,url,token,body,contentType));
    }
    private String httpText(String method, String url, String token, String body) throws Exception { return httpText(method,url,token,body,null); }
    private String httpText(String method, String url, String token, String body, String contentType) throws Exception {
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
        con.setRequestMethod(method); con.setConnectTimeout(20000); con.setReadTimeout(30000); con.setUseCaches(false);
        if(token!=null&&!token.isEmpty()) con.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null){con.setDoOutput(true); if(contentType!=null)con.setRequestProperty("Content-Type",contentType); byte[] bytes=body.getBytes(StandardCharsets.UTF_8); con.setFixedLengthStreamingMode(bytes.length); try(OutputStream out=con.getOutputStream()){out.write(bytes);}}
        int code=con.getResponseCode(); InputStream in=code>=200&&code<300?con.getInputStream():con.getErrorStream(); String response=readAll(in); if(code<200||code>=300)throw new Exception("HTTP "+code+": "+response); return response;
    }
}
