# -*- coding: utf-8 -*-
import sys
import json
import urllib.request

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8080"

def upload_receipt(receipt_text):
    data = json.dumps({"text": receipt_text}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE_URL}/api/upload-receipt",
        data=data,
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def get_inventory():
    with urllib.request.urlopen(f"{BASE_URL}/api/inventory") as resp:
        return json.loads(resp.read().decode("utf-8"))

def main():
    print("1. Inventar vor Upload abfragen...")
    inv_before = get_inventory()
    print(f"   Vorhandene Artikel: {len(inv_before)}")

    sample_receipt = """
        **** Nahkauf Heyer ****
        Leonhard Frank Str. 08
        99425 Weimar
        EUR
        KOCHSCHINKEN O.                  2,48 B
        MIREE DATTEL-CUR                 1,19 B
        MAGGI FIX CARBO                  2,18 B
                     2 Stk x    1,09
        MOENCH.LANDBIER                  1,39 A
        PFAND 0,15 EUR                   0,15 A *
        --------------------------------------
        SUMME                   EUR      7,39
        18.09.2026     14:30     Bon-Nr.:4235
    """

    print("\n2. Kassenbon hochladen (Nahkauf/Rewe)...")
    result = upload_receipt(sample_receipt)
    print(f"   Erkannte Artikel vom Server ({len(result)}):")
    for item in result:
        print(f"   -> {item['name']}: {item['quantity']} {item.get('unit', 'Stk.')} - {item['price']} € ({item.get('storageLocation', 'Kühlschrank')})")

    print("\n3. Inventar nach Upload abfragen...")
    inv_after = get_inventory()
    print(f"   Neue Artikel-Anzahl im Kühlschrank: {len(inv_after)}")

    assert len(result) == 4, f"Erwartet 4 Artikel, erhalten: {len(result)}"
    names = [it['name'] for it in result]
    assert "Kochschinken" in names
    assert "Miree Dattel-curry" in names or "Miree Dattel-Curry" in names
    assert any("Maggi" in n for n in names)
    assert any("Mönchshof" in n for n in names)

    # Prüfen, dass kein Müll im Inventar gelandet ist
    forbidden = ["Nahkauf", "Weimar", "EUR", "PFAND", "SUMME", "Bon-Nr", "Leonhard"]
    for inv_item in inv_after:
        for f in forbidden:
            assert f.lower() not in inv_item['name'].lower(), f"Unzulässiger Text im Artikelnamen: {inv_item['name']}"

    print("\n✓ API Upload-Test erfolgreich bestanden! Keine Kopfzeilen, kein Pfand und keine Summen im Inventar!")

if __name__ == "__main__":
    main()
