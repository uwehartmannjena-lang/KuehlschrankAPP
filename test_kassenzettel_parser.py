# -*- coding: utf-8 -*-
import sys
import json
from pathlib import Path

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

from kassenzettel_parser import parse_receipt_text


def test_kaufland():
    text = """
        Belegkopie
        Kaufland - Humboldtstraße 90
        99425 Weimar
        Preis EUR
        Harzbube Edelschi.
        1,99 B
        Rinderhackfleisch
        4,99 B
        Lübzer Pils     2 * 1,09
        2,18 A
        Pfandartikel     2 * 0,08
        0,16 A
        Bens Expressreis
           2 * 1,69
        3,38 B
        Mengenrabatt
        -0,71
        Summe
        11,83
        Kartenzahlung 11,83
        Datum 20.07.26 08:16 Uhr
    """
    items = parse_receipt_text(text)
    print(f"\n--- Kaufland Test ({len(items)} Artikel) ---")
    for it in items:
        print(f"  * {it['name']}: {it['quantity']}x {it['unit']} für {it['price']} € ({it['storageLocation']})")
    
    assert len(items) == 4, f"Erwartet 4 Artikel, erhalten: {len(items)}"
    names = [it['name'] for it in items]
    assert any("Harzbube" in n or "Edelschimmelkäse" in n for n in names)
    assert any("Rinderhackfleisch" in n for n in names)
    
    luebzer = next(it for it in items if "Lübzer" in it['name'])
    assert luebzer['quantity'] == 2
    assert abs(luebzer['price'] - 2.18) < 0.05
    
    reis = next(it for it in items if "Expressreis" in it['name'])
    assert reis['quantity'] == 2
    # 3.38 - 0.71 = 2.67
    assert abs(reis['price'] - 2.67) < 0.05
    print("✓ Kaufland Test erfolgreich!")

def test_rewe_nahkauf():
    text = """
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
    items = parse_receipt_text(text)
    print(f"\n--- Rewe/Nahkauf Test ({len(items)} Artikel) ---")
    for it in items:
        print(f"  * {it['name']}: {it['quantity']}x {it['unit']} für {it['price']} € ({it['storageLocation']})")
        
    assert len(items) == 4, f"Erwartet 4 Artikel, erhalten: {len(items)}"
    maggi = next(it for it in items if "Maggi" in it['name'] or "Carbonara" in it['name'])
    assert maggi['quantity'] == 2
    assert abs(maggi['price'] - 2.18) < 0.05
    
    bier = next(it for it in items if "Mönchshof" in it['name'])
    assert bier['quantity'] == 1
    assert abs(bier['price'] - 1.39) < 0.05
    print("✓ Rewe/Nahkauf Test erfolgreich!")

def test_netto():
    text = """
        Dein Einkauf vom 16.08.2025 09:34
        Filiale 0008696 / Pos 001 / Bon-Nr. 0412
        EUR
        BO Schnittbroetchen 1ST 2,28
          12 x 0,19 EUR 
        TWQ Medium MW12x0,75L 4,44
        MW-Pfand 3,30 EUR 3,30*
        GL Creme Fraiche Natur 200g 0,89
        0.10€ Rabatt -0,10
        Kiwi Gold ST 1,38
          2 x 0,69 EUR 
        Bio Bananen Lose 0,71
          0,40 g x 1,79 EUR/g 
        Summe 12,62
        Netto App 12,62
    """
    items = parse_receipt_text(text)
    print(f"\n--- Netto Test ({len(items)} Artikel) ---")
    for it in items:
        print(f"  * {it['name']}: {it['quantity']}x {it['unit']} für {it['price']} € ({it['storageLocation']})")
        
    assert len(items) == 5, f"Erwartet 5 Artikel, erhalten: {len(items)}"
    broetchen = next(it for it in items if "Schnittbrötchen" in it['name'])
    assert broetchen['quantity'] == 12
    assert abs(broetchen['price'] - 2.28) < 0.05
    
    creme = next(it for it in items if "Crème Fraîche" in it['name'] or "Creme Fraiche" in it['name'])
    # 0.89 - 0.10 = 0.79
    assert abs(creme['price'] - 0.79) < 0.05
    print("✓ Netto Test erfolgreich!")

def test_netto_ebon():
    text = """
        *** eBon ***
        NETTO
        Filiale 1778
        Erfurter Str. 84
        99427 Weimar
        EUR
        2 x 0,49
        BO-Bio Weizenbroetchen 1ST 0,98 B
        Preisaenderung -0,50
        BO-Bio Dinkelrusti 1ST 0,55 B
        Preisaenderung -0,28
        Satori Jasminreis 1kg 1,99 B
        SUMME 2,74€
        02.10.26 18:20
    """
    items = parse_receipt_text(text)
    print(f"\n--- Netto eBon Test ({len(items)} Artikel) ---")
    for it in items:
        print(f"  * {it['name']}: {it['quantity']}x {it['unit']} für {it['price']} € ({it['storageLocation']})")
        
    assert len(items) == 3, f"Erwartet 3 Artikel, erhalten: {len(items)}"
    weizen = next(it for it in items if "Weizenbrötchen" in it['name'])
    # 0.98 - 0.50 = 0.48
    assert abs(weizen['price'] - 0.48) < 0.05
    
    dinkel = next(it for it in items if "Dinkelrusti" in it['name'])
    # 0.55 - 0.28 = 0.27
    assert abs(dinkel['price'] - 0.27) < 0.05
    print("✓ Netto eBon Test erfolgreich!")

def test_globus():
    text = """
        GLOBUS Handelshof GmbH & Co.KG
        Markthalle Jena-Isserstedt
        Weimarische Str. 3 - 07751 Jena
        EUR
        #2070000602755
        Kart.vfk 2,5kg                  2,49 2
        #4000582188093
        Fin.Sweet Chili 100g            1,39 2
        #4009268333093
        KLOSSTEIG 750 G
                 3    x    1,99         5,97 2
        Paprika gelb lose
             0,224 kg x  4,99 €/kg      1,12 2
        ANZ. ARTIKEL   6
        Summe           10,97
        Datum 14.04.26                   18:10 Uhr
    """
    items = parse_receipt_text(text)
    print(f"\n--- Globus Test ({len(items)} Artikel) ---")
    for it in items:
        print(f"  * {it['name']}: {it['quantity']}x {it['unit']} für {it['price']} € (Barcode: {it.get('barcode')})")
        
    assert len(items) == 4, f"Erwartet 4 Artikel, erhalten: {len(items)}"
    kart = next(it for it in items if "Kartoffeln" in it['name'])
    assert kart['barcode'] == "2070000602755"
    assert abs(kart['price'] - 2.49) < 0.05
    
    chili = next(it for it in items if "Finesse Sweet Chili" in it['name'])
    assert chili['barcode'] == "4000582188093"
    assert abs(chili['price'] - 1.39) < 0.05
    
    kloss = next(it for it in items if "Kloßteig" in it['name'])
    assert kloss['barcode'] == "4009268333093"
    assert kloss['quantity'] == 3
    assert abs(kloss['price'] - 5.97) < 0.05
    print("✓ Globus Test erfolgreich!")


def test_json_files():
    json_path = Path("Kassenzettel json")
    if json_path.exists():
        for p in json_path.glob("*.json"):
            content = p.read_text(encoding="utf-8")
            items = parse_receipt_text(content)
            print(f"\n--- JSON Test {p.name} ({len(items)} Artikel) ---")
            for it in items:
                print(f"  * {it['name']}: {it['quantity']}x {it['unit']} für {it['price']} €")
            assert len(items) > 0, f"Keine Artikel in {p.name} erkannt"

if __name__ == "__main__":
    test_kaufland()
    test_rewe_nahkauf()
    test_netto()
    test_netto_ebon()
    test_globus()
    test_json_files()
    print("\n==========================================")
    print("🎉 ALLE AUTOMATISIERTEN TESTS BESTANDEN!")
    print("==========================================")
