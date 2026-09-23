import urllib.request
import urllib.error
import json
import subprocess
import os
import re
import sys
import time

try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

# ==========================================
# 1. EINSTELLUNGEN & API-KEY
# ==========================================
API_KEY = "AQ.Ab8RN6JMrG58SZYH26xAGyDq1-F7WnZdvc5JszAuG5d0OxEwVA"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
def projekt_pfad(*teile):
    return os.path.join(BASE_DIR, *teile)

# NEU: Das Skript kontrolliert jetzt den ganzen Ordner!
APP_DIR = projekt_pfad("app", "src", "main", "java", "com", "example", "myapplication")

MAX_RETRIES = 5
MAX_ZEIT = 180
AKTULLES_MODELL = "gemini-2.5-flash"

# WICHTIG: KassenzettelParser.kt wurde aus der Lösch-Liste entfernt!
ALTE_ZUSATZDATEIEN = [
    "FridgeItem.kt", "FridgeViewModel.kt", "FridgeDao.kt", "FridgeDatabase.kt",
    "FoodFactsService.kt", "FridgeWidget.kt", "PredictiveStockWorker.kt",
    "ScannerActivity.kt", "ExpiryNotificationWorker.kt", "FridgeWidgetReceiver.kt"
]

# ==========================================
# 2. DATEI-REINIGUNG & GIT BACKUP
# ==========================================
def bereinige_geister_dateien():
    for stoerenfried in ALTE_ZUSATZDATEIEN:
        vollstaendiger_pfad = os.path.join(APP_DIR, stoerenfried)
        if os.path.exists(vollstaendiger_pfad):
            try:
                os.remove(vollstaendiger_pfad)
                print(f"-> 🧹 Geister-Datei gelöscht: {stoerenfried}")
            except Exception:
                pass

def sichere_mit_git():
    if not os.path.exists(projekt_pfad(".git")):
        fuehre_befehl_aus(["git", "init"])
    # Sichert jetzt den gesamten Ordner mit beiden Dateien
    fuehre_befehl_aus(["git", "add", APP_DIR])
    fuehre_befehl_aus(["git", "commit", "-m", "Auto-Backup", "--no-verify"])

def stelle_git_wieder_her():
    print("-> ⏪ Setze Projekt auf letzten funktionierenden Stand zurück...")
    fuehre_befehl_aus(["git", "checkout", "HEAD", "--", APP_DIR])

# ==========================================
# 3. BEFEHLSAUSFÜHRUNG & LOG-ANALYSE
# ==========================================
def fuehre_befehl_aus(befehl, timeout=MAX_ZEIT):
    cmd = list(befehl)
    if os.name == 'nt' and cmd[0] in ["./gradlew", "gradlew"]:
        cmd[0] = projekt_pfad("gradlew.bat")

    try:
        ergebnis = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, shell=(os.name == 'nt'))
        kompletter_log = (ergebnis.stdout + "\n" + ergebnis.stderr).strip()
        return ergebnis.returncode == 0, kompletter_log
    except Exception as e:
        return False, str(e)

def extrahiere_wichtige_fehler(log):
    fehler_zeilen = re.findall(r"(e: file://.*)", log)
    if fehler_zeilen:
        return "\n".join(fehler_zeilen)
    if "Composable invocations can only happen" in log:
        return "FEHLER: @Composable UI-Funktion innerhalb onClick aufgerufen! Nutze State."
    if "Redeclaration" in log:
        return "FEHLER: Redeclaration! Du hast Dinge doppelt definiert. Prüfe MainActivity und KassenzettelParser."
    return log[-1500:]

# ==========================================
# 4. KI-KOMMUNIKATION (MULTI-FILE)
# ==========================================
def rufe_ki_auf(prompt):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{AKTULLES_MODELL}:generateContent?key={API_KEY}"
    headers = {'Content-Type': 'application/json'}
    data = {"contents": [{"parts": [{"text": prompt}]}]}

    req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req) as response:
            res = json.loads(response.read().decode('utf-8'))
            text = res['candidates'][0]['content']['parts'][0]['text']
            return text.strip()
    except urllib.error.HTTPError as e:
        print(f"-> 🌐 API Fehler ({e.code}): Google Limit erreicht. Warte 65 Sekunden...")
        time.sleep(65)
        return ""
    except Exception as e:
        print(f"-> 🌐 Netzwerkfehler: {e}. Warte 65 Sekunden...")
        time.sleep(65)
        return ""

def lese_code():
    code_sammlung = ""
    # Liest nun BEIDE Dateien für die KI ein!
    for datei in ["MainActivity.kt", "KassenzettelParser.kt"]:
        pfad = os.path.join(APP_DIR, datei)
        if os.path.exists(pfad):
            with open(pfad, "r", encoding='utf-8') as f:
                code_sammlung += f"\n\n### DATEI: {datei} ###\n{f.read()}"
    return code_sammlung

def schreibe_code(neuer_code):
    # Trennt die Antwort der KI und speichert sie in die richtige Datei
    dateien = re.split(r'### DATEI: (.*?) ###', neuer_code)

    if len(dateien) < 3:
        # Fallback, falls die KI die Markierungen vergisst
        pfad = os.path.join(APP_DIR, "MainActivity.kt")
        bereinigter_code = re.sub(r'```(?:kotlin)?\n?(.*?)```', r'\1', neuer_code, flags=re.DOTALL).strip()
        with open(pfad, "w", encoding='utf-8') as f:
            f.write(bereinigter_code)
        return

    for i in range(1, len(dateien), 2):
        dateiname = dateien[i].strip()
        code_inhalt = dateien[i+1].strip()
        code_inhalt = re.sub(r'```(?:kotlin)?\n?(.*?)```', r'\1', code_inhalt, flags=re.DOTALL).strip()

        if dateiname in ["MainActivity.kt", "KassenzettelParser.kt"]:
            pfad = os.path.join(APP_DIR, dateiname)
            os.makedirs(os.path.dirname(pfad), exist_ok=True)
            with open(pfad, "w", encoding='utf-8') as f:
                f.write(code_inhalt)

# ==========================================
# 5. DER INTELLIGENTE LOOP
# ==========================================
def starte_loop(aufgabe_text):
    bereinige_geister_dateien()
    sichere_mit_git()
    fehler_verlauf = []

    for versuch in range(1, MAX_RETRIES + 1):
        print(f"\n-> 🚀 Versuch {versuch} von {MAX_RETRIES}...")

        system_prompt = (
            f"Du bist ein Senior Android Entwickler. Programmiere die Kühlschrank-App.\n"
            f"WICHTIG: Das Projekt besteht nun aus ZWEI Dateien (MainActivity.kt und KassenzettelParser.kt)!\n"
            f"REGELN:\n"
            f"1. Baue diese Aufgabe ein: {aufgabe_text}\n"
            f"2. Behalte ALLES bestehende (ViewModel, UI) in beiden Dateien zwingend bei.\n"
            f"3. Verhindere 'Redeclaration'-Fehler: Deklariere nichts doppelt, was in der anderen Datei steht.\n"
            f"4. Du MUSST in deiner Antwort dieses Format nutzen, um den Code zu trennen:\n"
            f"### DATEI: MainActivity.kt ###\n"
            f"[Code hier]\n"
            f"### DATEI: KassenzettelParser.kt ###\n"
            f"[Code hier]\n"
        )

        prompt = f"{system_prompt}\nAktueller Code beider Dateien:\n{lese_code()}"

        if fehler_verlauf:
            prompt += f"\n\nACHTUNG: Letzter Code ist abgestürzt! Behebe diese Fehler:\n" + "\n".join(fehler_verlauf)

        neuer_code = rufe_ki_auf(prompt)

        if not neuer_code or len(neuer_code) < 50:
            print("-> ❌ Leere Antwort der KI. Wiederhole...")
            continue

        schreibe_code(neuer_code)
        print("-> 🏗️ Code getrennt gespeichert. Baue App zusammen (assembleDebug)...")

        test_ok, log = fuehre_befehl_aus(["gradlew", "assembleDebug"])

        if test_ok:
            print("-> 🎉 ERFOLG! Beide Dateien sind fehlerfrei kompiliert!")
            sichere_mit_git()
            return True
        else:
            wichtiger_fehler = extrahiere_wichtige_fehler(log)
            print(f"-> ❌ Build fehlgeschlagen! Fehler: {wichtiger_fehler[:200]}...")
            fehler_verlauf.append(wichtiger_fehler)
            stelle_git_wieder_her()

            if versuch == MAX_RETRIES:
                print("-> 🛑 Maximale Versuche erreicht. Gradle Clean...")
                fuehre_befehl_aus(["gradlew", "clean"])

    return False

# ==========================================
# 6. INTERAKTIVES MENÜ
# ==========================================
def interaktives_menue():
    while True:
        print("\n" + "="*55)
        print(" 🤖 MULTI-FILE AUTO-CODER PRO")
        print("="*55)
        print("[1] ✍️  Eigenen Wunsch eingeben (Frei Schnauze)")
        print("[2] ❌ Beenden")

        try:
            wahl = input("\nDeine Wahl (1/2): ").strip()
        except EOFError:
            break

        if wahl == "1":
            try:
                wunsch = input("Was soll die KI einbauen?: ").strip()
                if wunsch:
                    starte_loop(wunsch)
            except EOFError:
                break
        elif wahl == "2":
            sys.exit(0)

if __name__ == "__main__":
    interaktives_menue()