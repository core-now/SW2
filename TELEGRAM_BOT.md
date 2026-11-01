# Telegram Bot Integration & Remote Control

This document describes direct integration with a Telegram Bot for remote logging, notification testing, and exchanging logs or binaries on Windows 11 / Linux.

---

## 1. Bot Configuration

For security reasons, never hardcode bot tokens or chat IDs into code or documentation.

Copy `.env.example` to `.env` and fill in your credentials:

```env
TELEGRAM_BOT_TOKEN=YOUR_TELEGRAM_BOT_TOKEN_HERE
TELEGRAM_CHAT_ID=YOUR_CHAT_ID_HERE
```

Alternatively, set them as environment variables in your shell:
```powershell
$env:TELEGRAM_BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN_HERE"
$env:TELEGRAM_CHAT_ID = "YOUR_CHAT_ID_HERE"
```

---

## 2. Usage on Windows 11

### A) PowerShell (Built-in, No Dependencies)

#### Send a Text Message:
```powershell
$token = $env:TELEGRAM_BOT_TOKEN
$chatId = $env:TELEGRAM_CHAT_ID
$text = "SW2 Test from Windows 11 Notebook!"

$body = @{
    chat_id = $chatId
    text    = $text
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://api.telegram.org/bot$token/sendMessage" -Method Post -Body $body -ContentType "application/json; charset=utf-8"
```

#### Send a File / Logfile / Binary:
```powershell
$token = $env:TELEGRAM_BOT_TOKEN
$chatId = $env:TELEGRAM_CHAT_ID
$filePath = ".\sw2_capture.log"

$form = @{
    chat_id  = $chatId
    caption  = "SW2 Debug Log from Laptop"
    document = Get-Item $filePath
}

Invoke-RestMethod -Uri "https://api.telegram.org/bot$token/sendDocument" -Method Post -Form $form
```

---

### B) curl (CMD, PowerShell, or Git Bash)

#### Send a Text Message:
```bash
curl -s -X POST https://api.telegram.org/botYOUR_TELEGRAM_BOT_TOKEN_HERE/sendMessage ^
     -d chat_id=YOUR_CHAT_ID_HERE ^
     -d text="SW2 Controller started"
```

#### Send a File (Multipart Form-Data):
```bash
curl -s -F chat_id=YOUR_CHAT_ID_HERE ^
        -F document=@sw2_controller.py ^
        -F caption="Current SW2 Controller Script" ^
        https://api.telegram.org/botYOUR_TELEGRAM_BOT_TOKEN_HERE/sendDocument
```

---

### C) Python (Standalone & No External Libraries)

The `telegram_helper.py` script resides directly in the repository and automatically loads configuration from either a `.env` file or environment variables.

#### Import in Python Scripts:
```python
from telegram_helper import send_text, send_document, get_updates

# 1. Send text message
send_text("Watch handshake completed successfully!")

# 2. Send file
send_document("capture.bin", caption="Binary Protocol Capture")

# 3. Poll incoming bot updates / commands
updates = get_updates()
print(updates)
```

#### CLI Usage:
```cmd
:: Send text message:
python telegram_helper.py --text "Test message from Windows Terminal"

:: Send file with a description:
python telegram_helper.py --file sw2_controller.py --caption "Controller Version 1.0"

:: View latest incoming bot updates:
python telegram_helper.py --updates
```
