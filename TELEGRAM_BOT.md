# Telegram Bot Integration & Remote Control

Dieses Dokument beschreibt die direkte Anbindung an einen Telegram Bot für Remote-Logging, Benachrichtigungstests und den Austausch von Logs/APKs unter Windows 11 / Linux.

---

## 1. Bot-Konfiguration

Aus Sicherheitsgründen dürfen Tokens und Chat-IDs niemals fest im Code oder in Dokumentationen hinterlegt werden.

Kopiere die Datei `.env.example` nach `.env` und trage dort deine Zugangsdaten ein:

```env
TELEGRAM_BOT_TOKEN=YOUR_TELEGRAM_BOT_TOKEN_HERE
TELEGRAM_CHAT_ID=YOUR_CHAT_ID_HERE
```

Alternativ können diese als Umgebungsvariablen in der Shell gesetzt werden:
```powershell
$env:TELEGRAM_BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN_HERE"
$env:TELEGRAM_CHAT_ID = "YOUR_CHAT_ID_HERE"
```

---

## 2. Verwendung unter Windows 11

### A) PowerShell (Bordmittel ohne Installation)

#### Textnachricht senden:
```powershell
$token = $env:TELEGRAM_BOT_TOKEN
$chatId = $env:TELEGRAM_CHAT_ID
$text = "SW2 Test vom Windows 11 Notebook!"

$body = @{
    chat_id = $chatId
    text    = $text
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://api.telegram.org/bot$token/sendMessage" -Method Post -Body $body -ContentType "application/json; charset=utf-8"
```

#### Datei / Logfile / APK senden:
```powershell
$token = $env:TELEGRAM_BOT_TOKEN
$chatId = $env:TELEGRAM_CHAT_ID
$filePath = ".\sw2_capture.log"

$form = @{
    chat_id  = $chatId
    caption  = "SW2 Debug Log vom Laptop"
    document = Get-Item $filePath
}

Invoke-RestMethod -Uri "https://api.telegram.org/bot$token/sendDocument" -Method Post -Form $form
```

---

### B) curl (CMD, PowerShell oder Git Bash)

#### Textnachricht senden:
```bash
curl -s -X POST https://api.telegram.org/botYOUR_TELEGRAM_BOT_TOKEN_HERE/sendMessage ^
     -d chat_id=YOUR_CHAT_ID_HERE ^
     -d text="SW2 Controller gestartet"
```

#### Datei senden (Multipart Form-Data):
```bash
curl -s -F chat_id=YOUR_CHAT_ID_HERE ^
        -F document=@sw2_controller.py ^
        -F caption="Aktuelles SW2 Controller Skript" ^
        https://api.telegram.org/botYOUR_TELEGRAM_BOT_TOKEN_HERE/sendDocument
```

---

### C) Python (Standalone & ohne externe Bibliotheken)

Das Skript `telegram_helper.py` liegt direkt im selben Ordner und lädt die Konfiguration automatisch aus der `.env`-Datei oder aus den Umgebungsvariablen.

#### In eigenen Python-Skripten importieren:
```python
from telegram_helper import send_text, send_document, get_updates

# 1. Textnachricht senden
send_text("Watch Handshake erfolgreich abgeschlossen!")

# 2. Datei senden
send_document("capture.bin", caption="Binary Protocol Capture")

# 3. Eingehende Telegram-Befehle prüfen
updates = get_updates()
print(updates)
```

#### Aufruf direkt über die Kommandozeile:
```cmd
:: Textnachricht senden:
python telegram_helper.py --text "Testnachricht aus Windows Terminal"

:: Datei mit Beschreibung senden:
python telegram_helper.py --file sw2_controller.py --caption "Controller Version 1.0"

:: Neueste Bot-Nachrichten anzeigen:
python telegram_helper.py --updates
```
