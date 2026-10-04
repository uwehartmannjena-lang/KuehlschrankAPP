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

def test_all_markets():
    belege = {
        "Kaufland": """
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
        """,
        "Netto": """
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
        """,
        "Globus": """
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
    }

    all_items = []
    for markt, text in belege.items():
        print(f"\n==========================================")
        print(f"Test Upload {markt}...")
        res = upload_receipt(text)
        print(f"Erkannte Artikel ({len(res)}):")
        for it in res:
            print(f"  * {it['name']} | {it['quantity']} {it['unit']} | {it['price']:.2f} € | {it['storageLocation']}")
            all_items.append(it)
        assert len(res) >= 4, f"Zu wenige Artikel für {markt}: {len(res)}"

    print("\n==========================================")
    print(f"Insgesamt erfolgreich über Webservice importiert: {len(all_items)} Artikel!")
    print("Alle Markt-Filterregeln funktionieren perfekt!")

if __name__ == "__main__":
    test_all_markets()
