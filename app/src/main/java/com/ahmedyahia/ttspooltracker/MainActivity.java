package com.ahmedyahia.ttspooltracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
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
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private volatile String pendingReportJson = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String DRIVE_FOLDER_ID = "1UEV2NEAw4oilA2gN1oF8ixqnKD4gD8fm";
    private static final String SHEET_ID = "1XpQGUL0DNaK3mgCEecXtfoH2oMZS2qLEx_1rQqrW3QM";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleAppUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleAppUrl(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                deliverPendingReport();
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        loadHome();
    }

    private boolean handleAppUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || !"tts.local".equalsIgnoreCase(uri.getHost())) return false;
        String path = uri.getPath();
        if ("/login".equals(path)) {
            String username = uri.getQueryParameter("username");
            String password = uri.getQueryParameter("password");
            startNativeLogin(username, password);
            return true;
        }
        if ("/report".equals(path)) {
            String date = uri.getQueryParameter("date");
            if (date != null) startNativeReportLoad(date);
            return true;
        }
        return true;
    }

    private void startNativeLogin(final String username, final String password) {
        final String u = username == null ? "" : username.trim();
        final String p = password == null ? "" : password;
        Toast.makeText(this, "Login request received", Toast.LENGTH_SHORT).show();
        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "Enter username and password.", Toast.LENGTH_LONG).show();
            loadHome();
            return;
        }
        Toast.makeText(this, "Connecting to Google…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                authenticateUser(u, p);
                JSONArray reports = listReports();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Login successful • " + reports.length() + " report(s)", Toast.LENGTH_LONG).show();
                    showReportsPage(u, reports);
                });
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? "Login failed. Check internet access and Google authorization." : e.getMessage();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    loadHome();
                });
            }
        });
    }

    private void showReportsPage(String username, JSONArray reports) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta charset=\"utf-8\"><title>TTS Pool Tracker</title>");
        html.append("<style>body{margin:0;padding:22px;background:#081322;color:#e8eef8;font:14px Arial,sans-serif}.card{max-width:520px;margin:30px auto;background:#111b2e;border:1px solid #24344f;border-radius:22px;padding:24px}.title{font-size:24px;font-weight:900;text-align:center}.sub{text-align:center;color:#8ea0b8;margin:8px 0 24px}.item{display:block;text-decoration:none;color:#8fd7ff;background:#0a1424;border:1px solid #2a3c59;border-radius:12px;padding:15px;margin:10px 0;font-weight:800}.latest{border-color:#36d399;color:#74e3b4}.logout{display:block;text-align:center;margin-top:22px;color:#8ea0b8;text-decoration:none}</style></head><body><div class=\"card\"><div class=\"title\">◈ TTS POOL TRACKER</div><div class=\"sub\">Authorized user: " + escapeHtml(username) + "</div>");
        html.append("<div style=\"color:#8ea0b8;margin-bottom:10px\">REPORT DAYS</div>");
        if (reports.length() == 0) {
            html.append("<div style=\"color:#ff8b99\">No daily reports found on Google Drive.</div>");
        } else {
            for (int i = 0; i < reports.length(); i++) {
                JSONObject r = reports.optJSONObject(i);
                if (r == null) continue;
                String date = r.optString("date", "");
                html.append("<a class=\"item " + (i == 0 ? "latest" : "") + "\" href=\"https://tts.local/report?date=" + Uri.encode(date) + "\">" + escapeHtml(date) + (i == 0 ? " · LATEST UPDATE" : "") + "</a>");
            }
        }
        html.append("<a class=\"logout\" href=\"https://tts.local/login?username=&password=\">LOGOUT</a></div></body></html>");
        webView.loadDataWithBaseURL("https://tts.local/reports", html.toString(), "text/html", "UTF-8", "https://tts.local/reports");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void startNativeReportLoad(final String date) {
        Toast.makeText(this, "Loading report " + date + "…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                String report = fetchReportByDate(date);
                pendingReportJson = JSONObject.quote(report);
                runOnUiThread(this::loadDashboard);
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? "Unable to load the selected report." : e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void loadHome() { loadAssetPage("home.html"); }
    private void loadDashboard() { loadAssetPage("dashboard.html"); }

    private void loadAssetPage(String assetName) {
        try {
            String html = readAsset(assetName);
            String baseUrl = "https://appassets.androidplatform.net/assets/" + assetName;
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", baseUrl);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to load " + assetName + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public class AndroidBridge {
        @JavascriptInterface public void ping() { Toast.makeText(MainActivity.this, "Android bridge connected", Toast.LENGTH_SHORT).show(); }
        @JavascriptInterface public void login(final String username, final String password) { startNativeLogin(username, password); }
        @JavascriptInterface public void getLatestReport() { executor.execute(() -> { try { JSONArray reports=listReports(); if(reports.length()==0) throw new Exception("No daily reports found on Google Drive."); String date=reports.getJSONObject(0).getString("date"); pendingReportJson=JSONObject.quote(fetchReportByDate(date)); runOnUiThread(MainActivity.this::loadDashboard); } catch(Exception e){ runOnUiThread(()->Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_LONG).show()); }}); }
        @JavascriptInterface public void getReportByDate(final String date) { startNativeReportLoad(date); }
        @JavascriptInterface public void backHome() { runOnUiThread(MainActivity.this::loadHome); }
    }

    private void deliverPendingReport() {
        String quoted = pendingReportJson;
        if (quoted != null) {
            pendingReportJson = null;
            webView.evaluateJavascript("if(window.onReportLoaded) onReportLoaded(" + quoted + ")", null);
        }
    }

    private void authenticateUser(String username, String password) throws Exception {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) throw new Exception("Invalid username or password.");
        String token = getAccessToken();
        JSONObject sheet = httpJson("GET", "https://sheets.googleapis.com/v4/spreadsheets/" + SHEET_ID + "/values/A:B", token, null);
        JSONArray rows = sheet.optJSONArray("values");
        if (rows == null) throw new Exception("Authorized Users sheet is unavailable.");
        String wantedUser = username.trim(); boolean found = false;
        for (int i=0;i<rows.length();i++) { JSONArray row=rows.optJSONArray(i); if(row==null||row.length()==0) continue; String sheetUser=row.optString(0,"").trim(); String sheetPassword=row.length()>1?row.optString(1,""):""; if(sheetUser.equals(wantedUser)){found=true;if(!sheetPassword.equals(password))throw new Exception("Invalid username or password.");break;} }
        if(!found) throw new Exception("This user is not authorized.");
    }

    private JSONArray listReports() throws Exception {
        String token=getAccessToken(); String q="'"+DRIVE_FOLDER_ID+"' in parents and trashed = false"; String url="https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&orderBy=modifiedTime desc&pageSize=100&fields=files(id,name,modifiedTime)"; JSONObject listed=httpJson("GET",url,token,null); JSONArray source=listed.optJSONArray("files"); JSONArray reports=new JSONArray(); if(source==null)return reports; ArrayList<JSONObject> temp=new ArrayList<>(); for(int i=0;i<source.length();i++){JSONObject f=source.getJSONObject(i);String name=f.optString("name","");if(!name.matches("session_\\d{4}-\\d{2}-\\d{2}\\.json"))continue;JSONObject item=new JSONObject();item.put("date",name.substring(8,18));item.put("name",name);item.put("id",f.optString("id",""));item.put("modifiedTime",f.optString("modifiedTime",""));temp.add(item);} temp.sort((a,b)->b.optString("modifiedTime","").compareTo(a.optString("modifiedTime","")));for(JSONObject item:temp)reports.put(item);return reports;
    }

    private String fetchReportByDate(String date) throws Exception {
        if(date==null||!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new Exception("Invalid report date."); String token=getAccessToken();String name="session_"+date+".json";String q="'"+DRIVE_FOLDER_ID+"' in parents and trashed = false and name = '"+name+"'";String listUrl="https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&orderBy=modifiedTime desc&pageSize=20&fields=files(id,name,modifiedTime)";JSONObject listed=httpJson("GET",listUrl,token,null);JSONArray files=listed.optJSONArray("files");if(files==null||files.length()==0)throw new Exception("No report found for "+date+".");String fileId=files.getJSONObject(0).getString("id");String downloadUrl="https://www.googleapis.com/drive/v3/files/"+URLEncoder.encode(fileId,"UTF-8")+"?alt=media";return httpText("GET",downloadUrl,token,null);
    }

    private String getAccessToken() throws Exception {
        JSONObject c=new JSONObject(readAsset("SOC-Google-API-credentials.json"));String clientEmail=c.getString("client_email");String privateKeyPem=c.getString("private_key");String tokenUri=c.optString("token_uri",DEFAULT_TOKEN_URI);long now=System.currentTimeMillis()/1000L;String header=base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));String scope=DRIVE_SCOPE+" "+SHEETS_SCOPE;String payload="{\"iss\":\""+jsonEscape(clientEmail)+"\",\"scope\":\""+jsonEscape(scope)+"\",\"aud\":\""+jsonEscape(tokenUri)+"\",\"iat\":"+now+",\"exp\":"+(now+3600)+"}";String unsigned=header+"."+base64Url(payload.getBytes(StandardCharsets.UTF_8));PrivateKey key=privateKeyFromPem(privateKeyPem);java.security.Signature signer=java.security.Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update(unsigned.getBytes(StandardCharsets.UTF_8));String jwt=unsigned+"."+base64Url(signer.sign());String body="grant_type="+URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer","UTF-8")+"&assertion="+URLEncoder.encode(jwt,"UTF-8");JSONObject token=httpJson("POST",tokenUri,null,body,"application/x-www-form-urlencoded");String access=token.optString("access_token","");if(access.isEmpty())throw new Exception("Google OAuth token was not returned.");return access;
    }

    private PrivateKey privateKeyFromPem(String pem)throws Exception{String clean=pem.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");byte[] der=Base64.decode(clean,Base64.DEFAULT);return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));}
    private String readAsset(String name)throws Exception{InputStream in=getAssets().open(name);return readAll(in);}
    private String readAll(InputStream in)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();return b.toString();}
    private String base64Url(byte[] data){return Base64.encodeToString(data,Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);}
    private String jsonEscape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    private JSONObject httpJson(String method,String url,String token,String body)throws Exception{return httpJson(method,url,token,body,"application/x-www-form-urlencoded");}
    private JSONObject httpJson(String method,String url,String token,String body,String contentType)throws Exception{return new JSONObject(httpText(method,url,token,body,contentType));}
    private String httpText(String method,String url,String token,String body)throws Exception{return httpText(method,url,token,body,null);}
    private String httpText(String method,String url,String token,String body,String contentType)throws Exception{HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();con.setRequestMethod(method);con.setConnectTimeout(20000);con.setReadTimeout(30000);con.setUseCaches(false);if(token!=null&&!token.isEmpty())con.setRequestProperty("Authorization","Bearer "+token);if(body!=null){con.setDoOutput(true);if(contentType!=null)con.setRequestProperty("Content-Type",contentType);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);con.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=con.getOutputStream()){out.write(bytes);}}int code=con.getResponseCode();InputStream in=code>=200&&code<300?con.getInputStream():con.getErrorStream();String response=readAll(in);if(code<200||code>=300)throw new Exception("HTTP "+code+": "+response);return response;}
}
