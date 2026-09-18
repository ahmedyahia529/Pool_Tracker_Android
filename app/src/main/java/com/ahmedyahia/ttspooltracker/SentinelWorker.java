package com.ahmedyahia.ttspooltracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Base64;

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
import java.util.concurrent.TimeUnit;

public class SentinelWorker extends Worker {
    private static final String DRIVE_FOLDER_ID = "1UEV2NEAw4oilA2gN1oF8ixqnKD4gD8fm";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

    public SentinelWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SentinelWorker.class, 15, TimeUnit.MINUTES)
                .setInitialDelay(2, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sentinel_background_monitor",
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String reportText = fetchLatestReport();
            JSONObject report = new JSONObject(reportText);
            NotificationEngine.evaluate(getApplicationContext(), report, true);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private String fetchLatestReport() throws Exception {
        String token = getAccessToken();
        String q = "'" + DRIVE_FOLDER_ID + "' in parents and trashed = false";
        String url = "https://www.googleapis.com/drive/v3/files?q=" +
                URLEncoder.encode(q, "UTF-8") +
                "&orderBy=modifiedTime desc&pageSize=10&fields=files(id,name,modifiedTime)";
        JSONObject listed = httpJson("GET", url, token, null);
        JSONArray files = listed.optJSONArray("files");
        if (files == null) throw new Exception("No files");
        String id = null;
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            if (f.optString("name", "").matches("session_\\d{4}-\\d{2}-\\d{2}\\.json")) {
                id = f.optString("id", "");
                if (!id.isEmpty()) break;
            }
        }
        if (id == null || id.isEmpty()) throw new Exception("No session report");
        return httpText("GET",
                "https://www.googleapis.com/drive/v3/files/" +
                        URLEncoder.encode(id, "UTF-8") + "?alt=media",
                token, null);
    }

    private String getAccessToken() throws Exception {
        JSONObject c = new JSONObject(readAsset("SOC-Google-API-credentials.json"));
        String email = c.getString("client_email");
        String pem = c.getString("private_key");
        String tokenUri = c.optString("token_uri", TOKEN_URI);
        long now = System.currentTimeMillis() / 1000L;

        String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = "{\"iss\":\"" + escape(email) +
                "\",\"scope\":\"" + escape(DRIVE_SCOPE) +
                "\",\"aud\":\"" + escape(tokenUri) +
                "\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}";
        String unsigned = header + "." + b64(payload.getBytes(StandardCharsets.UTF_8));

        PrivateKey key = privateKeyFromPem(pem);
        java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(unsigned.getBytes(StandardCharsets.UTF_8));
        String jwt = unsigned + "." + b64(signer.sign());

        String body = "grant_type=" +
                URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
                "&assertion=" + URLEncoder.encode(jwt, "UTF-8");

        JSONObject token = httpJson("POST", tokenUri, null, body,
                "application/x-www-form-urlencoded");
        String access = token.optString("access_token", "");
        if (access.isEmpty()) throw new Exception("No access token");
        return access;
    }

    private PrivateKey privateKeyFromPem(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.decode(clean, Base64.DEFAULT);
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getApplicationContext().getAssets().open(name);
        return readAll(in);
    }

    private String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line);
        r.close();
        return b.toString();
    }

    private String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JSONObject httpJson(String method, String url, String token, String body) throws Exception {
        return httpJson(method, url, token, body, "application/x-www-form-urlencoded");
    }

    private JSONObject httpJson(String method, String url, String token, String body, String contentType) throws Exception {
        return new JSONObject(httpText(method, url, token, body, contentType));
    }

    private String httpText(String method, String url, String token, String body) throws Exception {
        return httpText(method, url, token, body, null);
    }

    private String httpText(String method, String url, String token, String body, String contentType) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        con.setUseCaches(false);
        if (token != null && !token.isEmpty()) {
            con.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            con.setDoOutput(true);
            if (contentType != null) con.setRequestProperty("Content-Type", contentType);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = con.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = con.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String response = readAll(in);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response);
        return response;
    }
}
