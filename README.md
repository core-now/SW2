<div align="center">
  <img src="splash_image.svg" alt="Sony SmartWatch 2" width="600"/>
</div>

# Sony SmartWatch 2 (SW2) Reverse Engineering & Protocol Lab (Windows 11)

Dieses Verzeichnis (`SWRE`) ermöglicht die direkte Kommunikation, Analyse und das Reverse Engineering des Sony Costanza Protokolls von einem Windows 11 Notebook über Bluetooth – **ohne Umweg über Android-Builds**.

---

## 🚀 Schnelleinstieg unter Windows 11

### 1. SmartWatch 2 koppeln
1. SmartWatch 2 einschalten bzw. zurücksetzen (langer Druck auf die Power-Taste), sodass der Kopplungsbildschirm ("Warten auf Telefon / Smart Connect") erscheint.
2. In Windows 11 öffnen: **Einstellungen -> Bluetooth und Geräte -> Gerät hinzufügen -> Bluetooth**.
3. Die SmartWatch 2 auswählen und koppeln (PIN ist meist `0000` oder wird automatisch bestätigt).

### 2. Verbindungsmodus wählen

Windows 11 unterstützt drei Wege zur Kommunikation mit der SmartWatch 2:

#### Option 1: Nativer Windows 11 RFCOMM Server (Zero-Config, Schnell & Empfohlen) ⭐
Da die Sony SmartWatch 2 als Bluetooth-Client agiert und aktiv nach dem Sony Costanza SDP Service (`8E780522-3C51-11E1-8D8D-001CC4D601D8`) sucht, startet der Controller diesen Server direkt über die native Windows 11 WinRT API:
```cmd
python sw2_controller.py
```
*Keine manuelle COM-Port- oder MAC-Konfiguration erforderlich. Sobald die Uhr in Reichweite ist, verbindet sie sich automatisch in 1-2 Sekunden.*

#### Option 2: Virtueller COM-Port (Klassisch)
Über einen in den Bluetooth-Einstellungen eingerichteten COM-Port:
```cmd
python sw2_controller.py --com COM3
```

#### Option 3: Nativer Windows Bluetooth RFCOMM Client-Socket
Direkte Verbindung zur MAC-Adresse:
```cmd
python sw2_controller.py --mac B4:52:7D:F3:E8:81 --channel 1
```

---

## 📦 Vorbereitung & Abhängigkeiten

Die Skripte laufen mit Standard-Python 3. Für serielle COM-Ports und optionale Bitmap-Generierung:
```cmd
pip install -r requirements.txt
```
*(Hinweis: `sw2_controller.py` und `telegram_helper.py` funktionieren dank Pure-Python Fallbacks auch ohne externe Pakete).*

---

## 🎮 Interaktive Konsole (`sw2_controller.py`)

Nach dem Start führt der Controller automatisch den Sony Initialisierungs-Handshake aus (Version-Check, FOTA-Reset, Zeit-Sync, Locale-Sync, Extension-Registrierung).

Danach erscheint das Menü:
```text
======================================================
  Sony SmartWatch 2 Reverse Engineering Controller
======================================================
  [1] Zeit synchronisieren
  [2] Vibration testen
  [3] Test-Benachrichtigung senden (Titel & Text)
  [4] Notification Extension neu registrieren
  [5] Status abfragen (FOTA & Version)
  [q] Beenden
------------------------------------------------------
```

Alle eingehenden Pakete der Uhr (Acks, Nacks, Touch-Events, Swipe-Events, Tasten und Sensordaten) werden **in Echtzeit im Hintergrund-Thread empfangen, dekodiert und als Klartext + Hex-Dump geloggt**.

---

## 🔬 Protocol Layout & Byte-Offset Referenz (Step 24)

Die Benachrichtigungen (`ResourceNotification`) nutzen das exakte **322-Byte-Layout** aus `libprotocol.so`:

```text
Offset       Länge    Beschreibung
-----------------------------------------------------------------------------------
0x00..0x03    4 B     Gesamtlänge des Pakets (Little-Endian: 322 bzw. 0x0142)
0x04..0x07    4 B     Paket-Typ (TYPE_RESOURCE = 1)
0x08..0x0B    4 B     Message-ID (fortlaufend)
0x0C..0x0F    4 B     Resource CID (z.B. 0x00100064)
0x10..0x13    4 B     Padding (Nullen)
0x14..0x15    2 B     hasCid = 1
0x16..0x17    2 B     Padding
0x18..0x1B    4 B     dataLength (totalSize - 20 = 302 bzw. 0x012E)
0x1C..0x1D    2 B     subType = 2 (Notification)
0x1E..0x1F    2 B     Padding
0x20..0x23    4 B     localization = 0
0x24..0x27    4 B     notificationExtensionCid (CID_NOTIFICATION_EXTENSION)
0x28..0x2F    8 B     timeStamp (Unix Timestamp Millisekunden)
0x30          1 B     personal = 0
0x31          1 B     readStatus = 0
0x32..0x33    2 B     fieldCount (z.B. 7 oder 8)
0x34..0x69   54 B     mOnlineMenu (menuCid = -1, count = 0, 48B Nullen)
0x6A..0x9F   54 B     mOfflineMenu (menuCid = -1, count = 0, 48B Nullen)
0xA0..0x12F 144 B     mFields Tabelle (18 Slots à 8 Bytes: short key, short pad, int value)
0x130..0x131  2 B     subScreenCount (1 falls screenCid vorhanden, sonst 0)
0x132..0x141 16 B     mSubScreens (left=0, top=0, right=219, bottom=175, opaque=2, pad=0, screenCid)
-----------------------------------------------------------------------------------
Gesamtlänge:          322 Bytes (bei subScreenCount = 1)
```

### ResourceExtension Layout (60 Bytes)
Für die Registrierung einer Notification-Extension erwartet die SmartWatch 2 Firmware exakt folgendes 60-Byte-Paket:
```text
Offset       Länge    Beschreibung
-----------------------------------------------------------------------------------
0x00..0x03    4 B     Gesamtlänge des Pakets (60 bzw. 0x003C)
0x04..0x07    4 B     Paket-Typ (TYPE_RESOURCE = 1)
0x08..0x0B    4 B     Message-ID
0x0C..0x0F    4 B     Extension CID (z.B. 0x00100002)
0x10..0x13    4 B     Padding (Nullen)
0x14..0x15    2 B     hasCid = 1
0x16..0x17    2 B     Padding
0x18..0x1B    4 B     dataLength (totalSize - 32 = 28 bzw. 0x001C)
0x1C..0x1D    2 B     subType = 4 (ResourceExtension auf dem Wire!)
0x1E..0x1F    2 B     Padding
0x20..0x23    4 B     nameCid (String Resource CID)
0x24..0x27    4 B     iconCid (48x48 Image CID)
0x28..0x2B    4 B     iconOfflineCid (48x48 Image CID)
0x2C..0x2F    4 B     color (z.B. 0xFF000000 bzw. -16777216)
0x30..0x31    2 B     priority (4 = APPLICATION_ALERT_PRIORITY_NOTIFICATION_MSG)
0x32          1 B     capabilities = 1 (Bit 0: Notification API aktiviert!)
0x33          1 B     supportedDrawingSpecs = 0
0x34..0x37    4 B     showOnHomeScreen (1 = True, 0 = False)
0x38..0x3B    4 B     relativeCid = -1
-----------------------------------------------------------------------------------
Gesamtlänge:          60 Bytes
```

### Bild- und Icon-Kompression (Raw DEFLATE)
> [!IMPORTANT]
> Die SmartWatch 2 Firmware verlangt bei komprimierten Bildern (`subType = 5`, `compressionType = 4`) **reines Raw-DEFLATE** (RFC 1951) **ohne** zlib-Header/Adler32-Trailer (`wbits=-15`).
> Standard `zlib.compress()` fügt den RFC-1950-Header (`0x78 0x9C`) hinzu, was auf der Uhr zu sofortigem **NACK** führt. In Python immer verwenden:
> ```python
> compressor = zlib.compressobj(wbits=-15)
> deflated = compressor.compress(raw_rgb565) + compressor.flush()
> ```

### Große Pakete & Fragmentierung
Pakete > 512 Bytes (z.B. große Bitmaps) werden von `CostanzaCodec.fragment_packet()` automatisch in Fragmente vom **Typ 11 (TYPE_FRAGMENT)** mit max. 492 Bytes Payload aufgeteilt.

---

## 📂 Enthaltene Referenzen (`reference/`)

Zur schnellen Überprüfung von Feldern und Algorithmen liegen im Unterordner `reference/`:
- `GeneralSyncManager.java`: Sonys originale DB- und Notification-Sync Logik
- `CommunicationManager.java`: Originale Handshake-, Socket- und Verbindungsverwaltung
- `ResourceNotification.java`, `ResourceExtension.java`, `ResourceString.java`, `ResourceScreen.java`
- `RequestNotification.java`, `RequestExtension.java`, `Types.java`, `costanza.xml`
- `libprotocol.so`: Die originale ARM-Shared-Library der SmartWatch 2 Firmware
- `CostanzaPacketCodec.kt`, `CostanzaBluetoothManager.kt`, `CostanzaConstants.kt`: Die Kotlin-Quellen der Android Companion App

---

## 🤖 Telegram Bot Helper (`telegram_helper.py`)

Um Logs, Testergebnisse oder Screenshots schnell an das Smartphone zu senden:
- Siehe [TELEGRAM_BOT.md](TELEGRAM_BOT.md)
- Beispiel:
  ```cmd
  python telegram_helper.py --text "Notification Test erfolgreich!"
  ```
