# -*- coding: utf-8 -*-
import sys
import glob
import json
import base64
import urllib.request

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

from dotenv import load_dotenv
load_dotenv()

API_KEY = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
MODEL = "gemini-2.5-flash"
URL = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={API_KEY}"

def test_vision():
    img_files = glob.glob("test_belege/*.png")
    if not img_files:
        print("Keine PNGs gefunden.")
        return
    
    test_img = img_files[0]
    print(f"Teste Bild: {test_img}")
    with open(test_img, "rb") as f:
        img_b64 = base64.b64encode(f.read()).decode("utf-8")

    prompt = (
        "Analysiere diesen Kassenbon. Extrahiere alle echten Lebensmittel. "
        "Filtere Marktadresse, Kopfzeilen, Kassen-Nr., Datum, Summen, MwSt., Kartenzahlung und Pfand vollständig heraus. "
        "Berechne Rabatte in die Preise ein. "
        "Gib ein JSON-Array zurück mit Feldern: name (sauberer deutscher Produktname ohne Abkürzungen), "
        "quantity (Integer), unit ('Stk.', 'kg', 'Becher', 'Dose', 'Glas', 'Kiste', 'Packung', 'Flasche'), "
        "price (Float, gerundet auf 2 Dezimalstellen), storageLocation ('Kühlschrank', 'Gefrierfach', 'Vorratskammer')."
    )

    payload = {
        "contents": [{
            "parts": [
                {"text": prompt},
                {"inline_data": {"mime_type": "image/png", "data": img_b64}}
            ]
        }],
        "generationConfig": {
            "response_mime_type": "application/json"
        }
    }

    req = urllib.request.Request(
        URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    with urllib.request.urlopen(req, timeout=30) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        ans = res["candidates"][0]["content"]["parts"][0]["text"]
        print("Antwort von Gemini:")
        print(ans)
        items = json.loads(ans)
        print(f"Erfolgreich {len(items)} Artikel extrahiert!")

if __name__ == "__main__":
    test_vision()
