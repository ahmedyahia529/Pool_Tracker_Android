package com.ahmedyahia.ttspooltracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.content.DialogInterface;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String BIO_PREFS = "tts_biometric";
    private static final String BIO_USER = "user";
    private static final String BIO_PASS = "pass";
    private static final String BIO_IV = "iv";
    private static final String BIO_COUNT = "count";
    private static final String KEY_ALIAS = "tts_sentinel_bio_key_v2";

    private static final String DRIVE_FOLDER_ID = "1UEV2NEAw4oilA2gN1oF8ixqnKD4gD8fm";
    private static final String SHEET_ID = "1XpQGUL0DNaK3mgCEecXtfoH2oMZS2qLEx_1rQqrW3QM";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationEngine.initialize(this);
        // WorkManager is initialized by its own AndroidX provider. Keep scheduling
        // defensive so a background-monitor initialization problem can never crash
        // the foreground dashboard.
        try {
            SentinelWorker.schedule(this);
        } catch (Exception ignored) {
            // Dashboard remains usable even if background scheduling is unavailable.
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4101);
        }

        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return handleAppUrl(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handleAppUrl(Uri.parse(url)); }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        loadHome();
    }

    private boolean handleAppUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || !"tts.local".equalsIgnoreCase(uri.getHost())) return false;
        String path = uri.getPath();
        if ("/login".equals(path)) { startNativeLogin(uri.getQueryParameter("username"), uri.getQueryParameter("password"), true); return true; }
        if ("/report".equals(path)) { String date = uri.getQueryParameter("date"); if (date != null) startNativeReportLoad(date); return true; }
        return true;
    }

    private void startNativeLogin(final String username, final String password, final boolean saveForBiometric) {
        final String u = username == null ? "" : username.trim();
        final String p = password == null ? "" : password;
        Toast.makeText(this, "Login request received", Toast.LENGTH_SHORT).show();
        if (u.isEmpty() || p.isEmpty()) { Toast.makeText(this, "Enter username and password.", Toast.LENGTH_LONG).show(); loadHome(); return; }
        Toast.makeText(this, "Connecting to Google…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                authenticateUser(u, p);
                if (saveForBiometric) {
                    try { saveBiometricCredentials(u, p); } catch (Exception ignored) {}
                }
                JSONArray reports = listReports();
                if (reports.length() == 0) throw new Exception("Login successful, but no reports were found in Google Drive.");
                String date = reports.getJSONObject(0).optString("date", "");
                String report = fetchReportByDate(date);
                runOnUiThread(() -> { Toast.makeText(MainActivity.this, "Login successful • " + reports.length() + " report(s)", Toast.LENGTH_SHORT).show(); loadDashboardWithData(reports, date, report); });
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? "Login failed. Check internet access and Google authorization." : e.getMessage();
                runOnUiThread(() -> { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show(); loadHome(); });
            }
        });
    }

    private void startBiometricLogin() {
        if (!hasBiometricCredentials()) {
            Toast.makeText(this, "Please login once with username and password first.", Toast.LENGTH_LONG).show();
            loadHome();
            return;
        }
        int count = getSharedPreferences(BIO_PREFS, MODE_PRIVATE).getInt(BIO_COUNT, 0);
        if (count >= 10) {
            Toast.makeText(this, "For security, confirm your username and password again.", Toast.LENGTH_LONG).show();
            loadHome();
            return;
        }
        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(this, "Biometric unlock requires Android 9 or newer.", Toast.LENGTH_LONG).show();
            return;
        }
        final BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                runOnUiThread(() -> finishBiometricLogin());
            }
            @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Biometric unlock cancelled.", Toast.LENGTH_SHORT).show());
            }
        };
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("TTS Sentinel")
                .setSubtitle("Unlock Pool Tracker")
                .setDescription("Use your fingerprint to securely unlock the dashboard.")
                .setNegativeButton("Use password", getMainExecutor(), (dialog, which) -> loadHome())
                .build();
        prompt.authenticate(new CancellationSignal(), getMainExecutor(), callback);
    }

    private void finishBiometricLogin() {
        try {
            String user = decryptPreference(BIO_USER);
            String pass = decryptPreference(BIO_PASS);
            if (user.isEmpty() || pass.isEmpty()) throw new Exception("Saved credentials are unavailable.");
            authenticateUser(user, pass);
            int next = getSharedPreferences(BIO_PREFS, MODE_PRIVATE).getInt(BIO_COUNT, 0) + 1;
            getSharedPreferences(BIO_PREFS, MODE_PRIVATE).edit().putInt(BIO_COUNT, next).apply();
            JSONArray reports = listReports();
            if (reports.length() == 0) throw new Exception("No reports were found in Google Drive.");
            String date = reports.getJSONObject(0).optString("date", "");
            String report = fetchReportByDate(date);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Fingerprint accepted • " + next + "/10", Toast.LENGTH_SHORT).show();
                loadDashboardWithData(reports, date, report);
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Biometric login failed. Please use your password.", Toast.LENGTH_LONG).show());
            loadHome();
        }
    }

    private boolean hasBiometricCredentials() {
        android.content.SharedPreferences p = getSharedPreferences(BIO_PREFS, MODE_PRIVATE);
        return p.contains(BIO_USER) && p.contains(BIO_PASS) && p.contains(BIO_IV);
    }

    private void saveBiometricCredentials(String user, String pass) throws Exception {
        encryptPreference(BIO_USER, user);
        encryptPreference(BIO_PASS, pass);
        getSharedPreferences(BIO_PREFS, MODE_PRIVATE).edit().putInt(BIO_COUNT, 0).apply();
    }

    private void ensureBioKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT |
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build());
            kg.generateKey();
        }
    }

    private SecretKey getBioKey() throws Exception {
        ensureBioKey();
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    private void encryptPreference(String key, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getBioKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        getSharedPreferences(BIO_PREFS, MODE_PRIVATE).edit()
                .putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(key + "_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(BIO_IV, "1").apply();
    }

    private String decryptPreference(String key) throws Exception {
        android.content.SharedPreferences p = getSharedPreferences(BIO_PREFS, MODE_PRIVATE);
        String enc = p.getString(key, "");
        String iv = p.getString(key + "_iv", "");
        if (enc.isEmpty() || iv.isEmpty()) throw new Exception("No saved credential");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getBioKey(), new GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT)));
        return new String(cipher.doFinal(Base64.decode(enc, Base64.DEFAULT)), StandardCharsets.UTF_8);
    }

    private void startNativeReportLoad(final String date) {
        Toast.makeText(this, "Loading report " + date + "…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try { String report = fetchReportByDate(date); JSONArray reports = listReports(); runOnUiThread(() -> loadDashboardWithData(reports, date, report)); }
            catch (Exception e) { final String msg = e.getMessage() == null ? "Unable to load the selected report." : e.getMessage(); runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show()); }
        });
    }

    private void loadHome() { loadAssetPage("home.html"); }

    private void loadDashboardWithData(JSONArray reports, String selectedDate, String reportJson) {
        try {
            String html = readAsset("dashboard.html");
            String reportsJson = reports == null ? "[]" : reports.toString();
            String safeReport = JSONObject.quote(reportJson == null ? "{}" : reportJson);
            String safeDate = JSONObject.quote(selectedDate == null ? "" : selectedDate);
            String injection = "<script>window.ANDROID_REPORTS=" + reportsJson + ";window.ANDROID_SELECTED_DATE=" + safeDate + ";window.ANDROID_REPORT_DATA=JSON.parse(" + safeReport + ");</script>";
            int headEnd = html.lastIndexOf("</head>");
            if (headEnd >= 0) html = html.substring(0, headEnd) + injection + html.substring(headEnd);
            String mobilePatch = readAsset("mobile_patch.js");
            html = html.replace("</body>", "<script>" + mobilePatch + "</script></body>");
            String baseUrl = "https://appassets.androidplatform.net/assets/dashboard.html";
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", baseUrl);
            try { NotificationEngine.evaluate(this, new JSONObject(reportJson == null ? "{}" : reportJson), true); } catch (Exception ignored) {}
        } catch (Exception e) { Toast.makeText(this, "Unable to load dashboard: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void loadAssetPage(String assetName) {
        try { String html = readAsset(assetName); String baseUrl = "https://appassets.androidplatform.net/assets/" + assetName; webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", baseUrl); }
        catch (Exception e) { Toast.makeText(this, "Unable to load " + assetName + ": " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    @Override public void onBackPressed() { if (webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }

    public class AndroidBridge {
        @JavascriptInterface public void ping() { Toast.makeText(MainActivity.this, "Android bridge connected", Toast.LENGTH_SHORT).show(); }
        @JavascriptInterface public void login(String username, String password) { startNativeLogin(username, password, true); }
        @JavascriptInterface public void biometricLogin() { startBiometricLogin(); }
        @JavascriptInterface public void getLatestReport() {
            executor.execute(() -> { try { JSONArray reports = listReports(); if (reports.length() == 0) throw new Exception("No daily reports found on Google Drive."); String date = reports.getJSONObject(0).getString("date"); String report = fetchReportByDate(date); runOnUiThread(() -> loadDashboardWithData(reports, date, report)); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show()); } });
        }
        @JavascriptInterface public void getReportByDate(String date) { startNativeReportLoad(date); }
        @JavascriptInterface public void backHome() { runOnUiThread(MainActivity.this::loadHome); }
        @JavascriptInterface public void notifyEvent(String title, String body, int level) {
            JSONObject x = new JSONObject();
            try {
                x.put("session_date", "");
                x.put("last_updated", String.valueOf(System.currentTimeMillis()));
                x.put("mobile_event_title", title);
                x.put("mobile_event_body", body);
                NotificationEngine.evaluate(MainActivity.this, x, true);
            } catch (Exception ignored) {}
        }
    }

    private void authenticateUser(String username, String password) throws Exception {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) throw new Exception("Invalid username or password.");
        String token = getAccessToken();
        JSONObject sheet = httpJson("GET", "https://sheets.googleapis.com/v4/spreadsheets/" + SHEET_ID + "/values/A:B", token, null);
        JSONArray rows = sheet.optJSONArray("values");
        if (rows == null) throw new Exception("Authorized Users sheet is unavailable.");
        String wantedUser = normalizeUsername(username);
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i); if (row == null || row.length() == 0) continue;
            String sheetUser = normalizeUsername(row.optString(0, ""));
            String sheetPassword = row.length() > 1 ? row.optString(1, "") : "";
            if (sheetUser.equals(wantedUser)) {
                if (!sheetPassword.equals(password)) throw new Exception("Username found in Authorized Users, but password does not match.");
                return;
            }
        }
        throw new Exception("This user is not authorized. Authorized Users rows read: " + rows.length() + ". Username match: NO.");
    }

    private String normalizeUsername(String value) { if (value == null) return ""; return value.replace("\uFEFF", "").replace("\u200B", "").trim().toLowerCase(Locale.ROOT); }

    private JSONArray listReports() throws Exception {
        String token = getAccessToken();
        String q = "'" + DRIVE_FOLDER_ID + "' in parents and trashed = false";
        String url = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(q, "UTF-8") + "&orderBy=modifiedTime desc&pageSize=100&fields=files(id,name,modifiedTime)";
        JSONObject listed = httpJson("GET", url, token, null); JSONArray source = listed.optJSONArray("files"); JSONArray reports = new JSONArray(); if (source == null) return reports;
        ArrayList<JSONObject> temp = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) { JSONObject f = source.getJSONObject(i); String name = f.optString("name", ""); if (!name.matches("session_\\d{4}-\\d{2}-\\d{2}\\.json")) continue; JSONObject item = new JSONObject(); item.put("date", name.substring(8, 18)); item.put("name", name); item.put("id", f.optString("id", "")); item.put("modifiedTime", f.optString("modifiedTime", "")); temp.add(item); }
        temp.sort((a, b) -> b.optString("modifiedTime", "").compareTo(a.optString("modifiedTime", "")));
        for (JSONObject item : temp) reports.put(item); return reports;
    }

    private String fetchReportByDate(String date) throws Exception {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("Invalid report date.");
        String token = getAccessToken(); String name = "session_" + date + ".json";
        String q = "'" + DRIVE_FOLDER_ID + "' in parents and trashed = false and name = '" + name + "'";
        String listUrl = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(q, "UTF-8") + "&orderBy=modifiedTime desc&pageSize=20&fields=files(id,name,modifiedTime)";
        JSONObject listed = httpJson("GET", listUrl, token, null); JSONArray files = listed.optJSONArray("files"); if (files == null || files.length() == 0) throw new Exception("No report found for " + date + ".");
        String fileId = files.getJSONObject(0).getString("id"); String downloadUrl = "https://www.googleapis.com/drive/v3/files/" + URLEncoder.encode(fileId, "UTF-8") + "?alt=media";
        return httpText("GET", downloadUrl, token, null);
    }

    private String getAccessToken() throws Exception {
        JSONObject c = new JSONObject(readAsset("SOC-Google-API-credentials.json")); String clientEmail = c.getString("client_email"); String privateKeyPem = c.getString("private_key"); String tokenUri = c.optString("token_uri", DEFAULT_TOKEN_URI);
        long now = System.currentTimeMillis() / 1000L; String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)); String scope = DRIVE_SCOPE + " " + SHEETS_SCOPE;
        String payload = "{\"iss\":\"" + jsonEscape(clientEmail) + "\",\"scope\":\"" + jsonEscape(scope) + "\",\"aud\":\"" + jsonEscape(tokenUri) + "\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}";
        String unsigned = header + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8)); PrivateKey key = privateKeyFromPem(privateKeyPem); java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA"); signer.initSign(key); signer.update(unsigned.getBytes(StandardCharsets.UTF_8));
        String jwt = unsigned + "." + base64Url(signer.sign()); String body = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") + "&assertion=" + URLEncoder.encode(jwt, "UTF-8");
        JSONObject token = httpJson("POST", tokenUri, null, body, "application/x-www-form-urlencoded"); String access = token.optString("access_token", ""); if (access.isEmpty()) throw new Exception("Google OAuth token was not returned."); return access;
    }

    private PrivateKey privateKeyFromPem(String pem) throws Exception { String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""); byte[] der = Base64.decode(clean, Base64.DEFAULT); return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der)); }
    private String readAsset(String name) throws Exception { InputStream in = getAssets().open(name); return readAll(in); }
    private String readAll(InputStream in) throws Exception { BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line); r.close(); return b.toString(); }
    private String base64Url(byte[] data) { return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
    private String jsonEscape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private JSONObject httpJson(String method, String url, String token, String body) throws Exception { return httpJson(method, url, token, body, "application/x-www-form-urlencoded"); }
    private JSONObject httpJson(String method, String url, String token, String body, String contentType) throws Exception { return new JSONObject(httpText(method, url, token, body, contentType)); }
    private String httpText(String method, String url, String token, String body) throws Exception { return httpText(method, url, token, body, null); }
    private String httpText(String method, String url, String token, String body, String contentType) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection(); con.setRequestMethod(method); con.setConnectTimeout(20000); con.setReadTimeout(30000); con.setUseCaches(false);
        if (token != null && !token.isEmpty()) con.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) { con.setDoOutput(true); if (contentType != null) con.setRequestProperty("Content-Type", contentType); byte[] bytes = body.getBytes(StandardCharsets.UTF_8); con.setFixedLengthStreamingMode(bytes.length); try (OutputStream out = con.getOutputStream()) { out.write(bytes); } }
        int code = con.getResponseCode(); InputStream in = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream(); String response = readAll(in); if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response); return response;
    }
}
