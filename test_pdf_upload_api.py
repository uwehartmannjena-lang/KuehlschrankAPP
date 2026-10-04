# -*- coding: utf-8 -*-
import sys
import json
import base64
import urllib.request
from pathlib import Path

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8080"

def upload_pdf_file(pdf_path):
    p = Path(pdf_path)
    print(f"\n--- Teste Upload von {p.name} ({p.stat().st_size} Bytes) ---")
    file_bytes = p.read_bytes()
    b64_str = base64.b64encode(file_bytes).decode('utf-8')

    payload = {
        "filename": p.name,
        "fileType": "application/pdf",
        "fileBase64": b64_str
    }

    req = urllib.request.Request(
        f"{BASE_URL}/api/upload-receipt",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    with urllib.request.urlopen(req, timeout=30) as resp:
        items = json.loads(resp.read().decode("utf-8"))
        print(f"Erkannte Artikel vom Webservice ({len(items)}):")
        for it in items:
            print(f"  * {it['name']}: {it['quantity']} {it.get('unit', 'Stk.')} für {it['price']:.2f} € ({it.get('storageLocation', 'Kühlschrank')})")
        
        assert len(items) > 0, "Es wurden keine Artikel erkannt!"
        # Verifizieren, dass kein PDF-Rohcode im Namen ist
        for it in items:
            assert "%pdf" not in it['name'].lower(), f"FEHLER: PDF Rohcode im Artikelnamen: {it['name']}"
            assert "kaufland" not in it['name'].lower() and "weimar" not in it['name'].lower(), f"FEHLER: Header im Artikel: {it['name']}"
        print(f"✓ Upload von {p.name} erfolgreich validiert!")
        return items

if __name__ == "__main__":
    upload_pdf_file("test_belege/20260726_Kassenzettel Groß Kaufland.pdf")
    upload_pdf_file("test_belege/Netto_Kassenbon_20250816-093409.pdf")
    print("\n==========================================")
    print("🎉 ALLE PDF-UPLOAD TESTS BESTANDEN!")
    print("==========================================")
