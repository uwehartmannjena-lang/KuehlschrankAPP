# -*- coding: utf-8 -*-
import sys
import json
import time
import urllib.request
import pypdf

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

API_KEY = "AQ.Ab8RN6JMrG58SZYH26xAGyDq1-F7WnZdvc5JszAuG5d0OxEwVA"
MODEL = "gemini-2.5-flash"
URL = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={API_KEY}"

def test_pdf_gemini():
    pdf_path = "test_belege/20260726_Kassenzettel Groß Kaufland.pdf"
    reader = pypdf.PdfReader(pdf_path)
    text = ""
    for page in reader.pages:
        text += page.extract_text() or ""

    print(f"Extrahierter PDF-Text ({len(text)} Zeichen):")
    print(text[:300])

    prompt = (
        "Analysiere diesen Kassenbon. Extrahiere alle echten Lebensmittel. "
        "Filtere Marktadresse, Kopfzeilen, Kassen-Nr., Datum, Summen, MwSt., Kartenzahlung und Pfand vollständig heraus. "
        "Berechne Rabatte in die Preise ein. "
        "Gib ein JSON-Array zurück mit Feldern: name (sauberer deutscher Produktname), "
        "quantity (Integer), unit ('Stk.', 'kg', 'Becher', 'Dose', 'Glas', 'Kiste', 'Packung', 'Flasche'), "
        "price (Float, gerundet auf 2 Dezimalstellen), storageLocation ('Kühlschrank', 'Gefrierfach', 'Vorratskammer').\n\n"
        f"Kassenbon-Text:\n{text[:3000]}"
    )

    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"response_mime_type": "application/json"}
    }

    t0 = time.time()
    req = urllib.request.Request(
        URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    with urllib.request.urlopen(req, timeout=20) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        ans = res["candidates"][0]["content"]["parts"][0]["text"]
        t1 = time.time()
        print(f"\nGemini Antwort ({t1 - t0:.2f} s):")
        print(ans)
        items = json.loads(ans)
        print(f"\n✓ Erfolgreich {len(items)} Artikel extrahiert!")

if __name__ == "__main__":
    test_pdf_gemini()
