# TTS Pool Tracker Android

Mobile viewer built from the final TTS Pool Tracker v6 dashboard.

## Behavior
- Home screen has **GET LAST UPDATE REPORT**.
- Reads the Google service-account credentials injected by GitHub Actions at build time.
- Finds today's `session_YYYY-MM-DD.json` in the existing shared Drive folder and downloads the latest modified copy.
- Opens the same dashboard renderer used by the desktop system, adapted for mobile.
- No changes are required to the existing EXE/Drive Sync system.

Drive folder: `1UEV2NEAw4oilA2gN1oF8ixqnKD4gD8fm`

## Build without Android Studio
This repository contains a GitHub Actions workflow that builds the APK in the cloud. You do **not** need Android Studio, Gradle, or an Android SDK on your PC.

### One-time secret
The Google service-account JSON is intentionally **not committed** because this repository is public. Create a repository secret named `GOOGLE_CREDENTIALS_B64`.

On Windows PowerShell, run:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("SOC-Google-API-credentials.json"))
```

Copy the output, then GitHub → repository **Settings → Secrets and variables → Actions → New repository secret** and set:
- Name: `GOOGLE_CREDENTIALS_B64`
- Value: the Base64 output

### Build the APK
1. Open the **Actions** tab.
2. Select **Build TTS Pool Tracker APK**.
3. Click **Run workflow**.
4. When it finishes, open the workflow run.
5. Under **Artifacts**, download **TTS-Pool-Tracker**.
6. Inside the artifact is `TTS-Pool-Tracker.apk`.

### Security note
The final APK contains the service-account key because the app needs direct Drive access and this was explicitly requested. Anyone who can extract the APK can potentially recover that key. Keep the repository private if possible, or use a dedicated least-privilege service account and rotate/revoke the key if the APK is distributed outside the intended team.
