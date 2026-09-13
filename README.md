# TTS Pool Tracker Android

Secure Android viewer for the TTS Pool Tracker daily reports.

## What this version does

1. Login is required before reports can be viewed.
2. Username + password are checked against the `Pool Tracker Authorized_Users` Google Sheet:
   - Column A = username
   - Column B = password
3. After login, the app loads all daily `session_YYYY-MM-DD.json` reports from the configured Google Drive folder.
4. `REPORT DAY` is a dropdown containing every available report.
5. `OPEN SELECTED REPORT` opens the chosen day.
6. `GET LAST REPORT UPDATE` opens the most recently modified daily report.
7. The dashboard is optimized for Android screens, with horizontal scrolling for wide data tables.
8. Google credentials are injected during the GitHub Actions build and are ignored by Git.

## Google access required

The same Google service account used by the dashboard must have:
- Viewer/appropriate access to the Drive folder containing the session JSON reports.
- Viewer access to the `Pool Tracker Authorized_Users` spreadsheet.

Spreadsheet:
`1XpQGUL0DNaK3mgCEecXtfoH2oMZS2qLEx_1rQqrW3QM`

Drive folder:
`1UEV2NEAw4oilA2gN1oF8ixqnKD4gD8fm`

## GitHub Actions

Create a repository secret named:

`GOOGLE_CREDENTIALS_B64`

On Windows PowerShell, generate it with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("SOC-Google-API-credentials.json"))
```

Paste the output into:

GitHub → Repository → Settings → Secrets and variables → Actions → New repository secret

Then run:

Actions → Build TTS Pool Tracker APK → Run workflow

The APK artifact will be named:

`TTS-Pool-Tracker`

## Security note

The final APK contains the service-account credential because the app needs direct access to Drive/Sheets. Anyone who obtains and reverse-engineers the APK may be able to recover that credential. Keep the GitHub repository private and rotate/revoke the service-account key if the APK is distributed outside your trusted users.
