#!/usr/bin/env python3
"""
Telegram Bot Helper for SmartWatch 2 RE & Automation
===================================================
Standalone Python utility (Zero external dependencies, uses standard library urllib)
to send logs, notifications, and files directly to Telegram from Windows 11 / Linux.

Configuration:
Tokens and chat IDs are loaded from environment variables or a local .env file:
- TELEGRAM_BOT_TOKEN: Bot API token (e.g. from @BotFather)
- TELEGRAM_CHAT_ID: Default chat ID to send notifications to
"""

import argparse
import json
import mimetypes
import os
import sys
import urllib.parse
import urllib.request
import uuid


def _load_env_file(filepath: str = ".env") -> None:
    """Lightweight pure-python .env loader without third-party dependencies."""
    candidate_paths = [
        filepath,
        os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    ]
    for path in candidate_paths:
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if not line or line.startswith("#") or "=" not in line:
                            continue
                        key, val = line.split("=", 1)
                        key = key.strip()
                        val = val.strip().strip("'\"")
                        if key and key not in os.environ:
                            os.environ[key] = val
                break
            except Exception:
                pass


_load_env_file()

BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "YOUR_TELEGRAM_BOT_TOKEN_HERE")
DEFAULT_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")
BASE_URL = f"https://api.telegram.org/bot{BOT_TOKEN}"


def _check_bot_token() -> None:
    if not BOT_TOKEN or BOT_TOKEN == "YOUR_TELEGRAM_BOT_TOKEN_HERE":
        raise ValueError(
            "TELEGRAM_BOT_TOKEN is not configured! "
            "Please set the TELEGRAM_BOT_TOKEN environment variable or create a .env file from .env.example."
        )


def send_text(text: str, chat_id: str = "") -> dict:
    """Send a text message via Telegram Bot."""
    _check_bot_token()
    target_chat = chat_id or DEFAULT_CHAT_ID
    if not target_chat:
        raise ValueError("Target chat ID must be provided or configured via TELEGRAM_CHAT_ID.")

    url = f"{BASE_URL}/sendMessage"
    payload = json.dumps({
        "chat_id": target_chat,
        "text": text,
        "parse_mode": "Markdown"
    }).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as resp:
        result = json.loads(resp.read().decode("utf-8"))
        return result


def send_document(file_path: str, caption: str = "", chat_id: str = "") -> dict:
    """Send a file/document via Telegram Bot using standard library multipart/form-data."""
    _check_bot_token()
    target_chat = chat_id or DEFAULT_CHAT_ID
    if not target_chat:
        raise ValueError("Target chat ID must be provided or configured via TELEGRAM_CHAT_ID.")

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")

    filename = os.path.basename(file_path)
    content_type = mimetypes.guess_type(file_path)[0] or "application/octet-stream"

    with open(file_path, "rb") as f:
        file_bytes = f.read()

    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    body = bytearray()

    def add_field(name: str, value: str):
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(f"{value}\r\n".encode("utf-8"))

    add_field("chat_id", str(target_chat))
    if caption:
        add_field("caption", caption)

    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(f'Content-Disposition: form-data; name="document"; filename="{filename}"\r\n'.encode("utf-8"))
    body.extend(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
    body.extend(file_bytes)
    body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    url = f"{BASE_URL}/sendDocument"
    req = urllib.request.Request(
        url,
        data=bytes(body),
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(len(body))
        }
    )

    with urllib.request.urlopen(req) as resp:
        result = json.loads(resp.read().decode("utf-8"))
        return result


def get_updates(offset: int = 0) -> dict:
    """Fetch latest messages sent to the bot."""
    _check_bot_token()
    url = f"{BASE_URL}/getUpdates?offset={offset}"
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(description="Telegram Helper for SmartWatch 2 RE")
    parser.add_argument("--text", "-t", help="Text message to send")
    parser.add_argument("--file", "-f", help="Path to file/document to send")
    parser.add_argument("--caption", "-c", default="", help="Caption for file upload")
    parser.add_argument("--chat", default=DEFAULT_CHAT_ID, help=f"Telegram Chat ID (default: {DEFAULT_CHAT_ID or 'None'})")
    parser.add_argument("--updates", "-u", action="store_true", help="Fetch and print recent updates")

    args = parser.parse_args()

    try:
        if args.updates:
            print("[*] Fetching Telegram bot updates...")
            res = get_updates()
            print(json.dumps(res, indent=2))
            return

        if args.text:
            print(f"[*] Sending text to chat {args.chat}...")
            res = send_text(args.text, chat_id=args.chat)
            if res.get("ok"):
                print(f"[+] Message sent! ID: {res['result']['message_id']}")
            else:
                print(f"[!] Error: {res}")

        if args.file:
            print(f"[*] Uploading file '{args.file}' to chat {args.chat}...")
            res = send_document(args.file, caption=args.caption, chat_id=args.chat)
            if res.get("ok"):
                print(f"[+] Document sent! ID: {res['result']['message_id']}")
            else:
                print(f"[!] Error: {res}")

        if not args.text and not args.file and not args.updates:
            parser.print_help()
    except Exception as exc:
        print(f"[!] Error: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
