# -*- coding: utf-8 -*-
import sys
import json
import base64
from pathlib import Path
from kassenzettel_parser import (
    sanitize_ocr_text,
    is_non_food,
    clean_product_name,
    parse_receipt_text,
    analyze_uploaded_receipt,
    process_uploaded_receipt
)
from webservice import deduplicate_inventory_items, clean_non_food_from_inventory

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def test_sanitize_and_non_food():
    print("--- 1. Teste Sanitize & Non-Food Filter ---")
    dirty = "Mayb.\x9f Graub.\x1f Tr\x00 3,99\nDentagard\x9f Zahncreme 0,99\nKuschelweich\x0b Weichspüler 1,79\nFit GSM 1,29"
    cleaned = sanitize_ocr_text(dirty)
    assert "\x9f" not in cleaned and "\x1f" not in cleaned and "\x00" not in cleaned
    assert "Mayb." in cleaned
    
    # Non-food checks
    assert is_non_food("Dentagard Zahncreme")
    assert is_non_food("Kuschelweich Weichspüler")
    assert is_non_food("Fit GSM")
    assert is_non_food("Mexx Retroshorts 2er")
    assert is_non_food("Kugelschreiber blau 4er")
    assert not is_non_food("Bio Vollmilch 3.8%")
    assert not is_non_food("Gouda jung in Scheiben")
    assert not is_non_food("Rinderhackfleisch 500g")
    print("✓ Sanitize & Non-Food Filter einwandfrei!")

def test_analyze_receipt_text_with_non_food():
    print("\n--- 2. Teste Receipt Analysis mit gemischtem Kassenbon ---")
    receipt = """
    EDEKA aktiv markt
    Musterstr. 1, 12345 Berlin
    Datum: 04.10.2026  Uhrzeit: 16:45
    
    VOLLMILCH 3.8%            1,49 B
    GOUDA SCHEIBEN            2,29 B
    DENTAGARD ZAHNCREME       0,99 A
    FIT GSM SPÜLMITTEL        1,29 A
    KUSCHELWEICH WEICHSP.     1,79 A
    BANANEN BIO               1,99 B
    MEXX RETROSHORTS 2ER     14,99 A
    
    SUMME EUR                24,84
    """
    res = analyze_uploaded_receipt({"text": receipt})
    assert res["supermarket"].upper() == "EDEKA", f"Erwartet EDEKA, erhalten {res['supermarket']}"
    assert res["purchaseDate"] == "04.10.2026", f"Erwartet 04.10.2026, erhalten {res['purchaseDate']}"
    
    item_names = [it["name"] for it in res["items"]]
    print(f"Erkannte Lebensmittel ({len(item_names)}): {item_names}")
    print(f"Aussortierte Non-Food Artikel: {res['filteredNonFood']}")
    
    # Keine Non-Food Artikel in res["items"]
    for name in item_names:
        assert not is_non_food(name), f"FEHLER: Non-Food '{name}' in Artikelliste gefunden!"
        assert "dentagard" not in name.lower()
        assert "fit gsm" not in name.lower()
        assert "kuschelweich" not in name.lower()
        assert "retroshorts" not in name.lower()
    
    # Lebensmittel müssen vorhanden sein
    assert any("milch" in n.lower() for n in item_names)
    assert any("gouda" in n.lower() for n in item_names)
    assert any("banane" in n.lower() for n in item_names)
    
    # Aussortierte Non-Food Liste muss Einträge enthalten
    assert len(res["filteredNonFood"]) >= 3
    print("✓ Analyse & Filterung von gemischtem Kassenbon erfolgreich!")

def test_kaufland_pdf_analysis():
    print("\n--- 3. Teste Kaufland PDF Analyse ---")
    pdf_path = Path("test_belege/20260726_Kassenzettel Groß Kaufland.pdf")
    if pdf_path.exists():
        file_bytes = pdf_path.read_bytes()
        res = analyze_uploaded_receipt({
            "filename": pdf_path.name,
            "fileType": "application/pdf",
            "fileBase64": base64.b64encode(file_bytes).decode("utf-8")
        })
        print(f"Kaufland PDF Supermarkt: {res['supermarket']}, Kaufdatum: {res['purchaseDate']}")
        print(f"Erkannte Artikel: {len(res['items'])}, Aussortiert Non-Food: {res['filteredNonFood']}")
        assert res["supermarket"] == "Kaufland"
        assert res["purchaseDate"] in ["20.07.2026", "26.07.2026"]
        assert len(res["items"]) > 0
        for it in res["items"]:
            assert "\x9f" not in it["name"], f"Steuerzeichen in {it['name']}"
            assert not is_non_food(it["name"]), f"Non-Food in {it['name']}"
        print("✓ Kaufland PDF fehlerfrei ohne Wirrwar und ohne Non-Food analysiert!")

def test_netto_pdf_analysis():
    print("\n--- 4. Teste Netto PDF Analyse ---")
    pdf_path = Path("test_belege/Netto_Kassenbon_20250816-093409.pdf")
    if pdf_path.exists():
        file_bytes = pdf_path.read_bytes()
        res = analyze_uploaded_receipt({
            "filename": pdf_path.name,
            "fileType": "application/pdf",
            "fileBase64": base64.b64encode(file_bytes).decode("utf-8")
        })
        print(f"Netto PDF Supermarkt: {res['supermarket']}, Kaufdatum: {res['purchaseDate']}")
        print(f"Erkannte Artikel: {len(res['items'])}")
        assert res["supermarket"] == "Netto"
        assert len(res["items"]) > 0
        for it in res["items"]:
            assert not is_non_food(it["name"])
        print("✓ Netto PDF fehlerfrei analysiert!")

def test_deduplication_and_non_food_cleanup():
    print("\n--- 5. Teste Vorrats-Deduplizierung & Cleanup ---")
    mock_inv = [
        {"id": "1", "name": "Bio Vollmilch", "quantity": 1, "storageLocation": "Kühlschrank", "price": 1.49},
        {"id": "2", "name": "bio vollmilch", "quantity": 2, "storageLocation": "Kühlschrank", "price": 1.49},
        {"id": "3", "name": "Bio Vollmilch", "quantity": 1, "storageLocation": "Vorratskammer", "price": 1.49},
        {"id": "4", "name": "Fit GSM Spülmittel", "quantity": 1, "storageLocation": "Kühlschrank", "price": 1.29},
        {"id": "5", "name": "Kuschelweich Weichspüler", "quantity": 1, "storageLocation": "Kühlschrank", "price": 1.79},
        {"id": "6", "name": "Mexx Retroshorts", "quantity": 2, "storageLocation": "Kühlschrank", "price": 14.99}
    ]
    
    # 1. Non-food bereinigen
    cleaned_inv, removed_nf = clean_non_food_from_inventory(mock_inv)
    assert removed_nf == 3, f"Erwartet 3 entfernte Non-Food Artikel, erhalten: {removed_nf}"
    assert len(cleaned_inv) == 3
    
    # 2. Duplikate zusammenfassen
    deduped_inv, merged_cnt = deduplicate_inventory_items(cleaned_inv)
    assert merged_cnt == 1, f"Erwartet 1 zusammengefasstes Duplikat, erhalten: {merged_cnt}"
    assert len(deduped_inv) == 2
    
    # Prüfe Menge von Bio Vollmilch im Kühlschrank (1 + 2 = 3)
    fridge_milk = next(it for it in deduped_inv if it["storageLocation"] == "Kühlschrank")
    assert fridge_milk["quantity"] == 3
    
    # Prüfe Bio Vollmilch in der Vorratskammer (bleibt 1)
    pantry_milk = next(it for it in deduped_inv if it["storageLocation"] == "Vorratskammer")
    assert pantry_milk["quantity"] == 1
    
    print("✓ Deduplizierung und Vorrats-Cleanup funktionieren absolut perfekt!")

if __name__ == "__main__":
    test_sanitize_and_non_food()
    test_analyze_receipt_text_with_non_food()
    test_kaufland_pdf_analysis()
    test_netto_pdf_analysis()
    test_deduplication_and_non_food_cleanup()
    print("\n==============================================")
    print("🎉 ALLE WEBAPP-VERBESSERUNGSTESTS BESTANDEN!")
    print("==============================================")
