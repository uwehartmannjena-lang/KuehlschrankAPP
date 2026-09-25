import sqlite3
import uuid
import os
import random

def generate_database():
    assets_dir = os.path.join("app", "src", "main", "assets")
    os.makedirs(assets_dir, exist_ok=True)
    db_path = os.path.join(assets_dir, "market_products.db")

    if os.path.exists(db_path):
        os.remove(db_path)

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # Room table structure for MarketProductEntry
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS market_dictionary (
        id TEXT PRIMARY KEY NOT NULL,
        market TEXT NOT NULL,
        receiptPattern TEXT NOT NULL,
        cleanName TEXT NOT NULL,
        category TEXT NOT NULL,
        defaultStorage TEXT NOT NULL,
        defaultShelfLifeDays INTEGER NOT NULL
    );
    """)

    cursor.execute("CREATE INDEX IF NOT EXISTS idx_market ON market_dictionary(market);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_receipt_pattern ON market_dictionary(receiptPattern);")

    # Room FTS table
    cursor.execute("""
    CREATE VIRTUAL TABLE IF NOT EXISTS market_dictionary_fts USING fts4(
        receiptPattern,
        cleanName,
        category,
        content='market_dictionary'
    );
    """)

    # Definition of Market Chains and Brands
    markets = {
        "kaufland": ["K-Classic", "Purland", "K-Bio", "K-Take it veggie", "K-Favorites"],
        "lidl": ["Milbona", "Dulano", "Cien", "Vemondo", "Crownfield", "Freeway", "Sonnemund"],
        "aldi_nord": ["Milsani", "Gut Bio", "Tandil", "Trader Joe's", "River", "Golden Seafood"],
        "aldi_sued": ["Milsani", "Gut Bio", "Tandil", "Cucina", "Rio D'Oro", "Almare"],
        "rewe": ["ja!", "Rewe Beste Wahl", "Rewe Bio", "Rewe Feine Welt", "Rewe Regional"],
        "edeka": ["Gut & Günstig", "Edeka Bio", "Edeka Selection", "Papa Joe's", "Edeka Italia"],
        "globus": ["Fin.", "Metzgerei SB", "Globus Bio", "Globus Gold", "Globus A-Z"],
        "netto": ["BioBio", "Gutes Land", "Viva Vital", "Cafèt", "Bäckerkrönung"],
        "penny": ["Naturgut", "Bäckerkrönung", "Elite", "San Fabio", "Mühlenhof"],
        "metro": ["Aro", "Metro Chef", "Metro Premium", "Rioba"],
        "dm": ["dmBio", "Balea", "Denkmit", "Mivolis", "profissimo", "Alverde"],
        "rossmann": ["enerBiO", "Isana", "Domol", "Alterra", "Babydream"],
        "general": ["Bauer", "Dr. Oetker", "Müllermilch", "Coca-Cola", "Knorr", "Barilla", "Nestlé", "Ferrero"]
    }

    # Categories definition with base items, storage, and default shelf life
    categories = {
        "Obst & Gemüse": {
            "storage": "Kühlschrank",
            "shelf_life": 5,
            "items": [
                ("Apfel Braeburn", "APFEL BRAEBURN"), ("Bananen lose", "BANANEN LOSE"),
                ("Rispentomaten", "RISPENTOMATEN"), ("Salatgurke", "SALATGURKE"),
                ("Bio Karotten", "BIO KAROTTEN 1KG"), ("Speisekartoffeln", "SPEISEKARTOFFELN 2.5KG"),
                ("Paprika Mix", "PAPRIKA MIX 500G"), ("Hass Avocados", "AVOCADO HASS"),
                ("Tafeltrauben hell", "TAFELTRAUBEN HELL"), ("Erdbeeren 500g", "ERDBEEREN 500G"),
                ("Zucchini grün", "ZUCCHINI GRUEN"), ("Champignons weiß", "CHAMPIGNONS WEISS"),
                ("Brokkoli 500g", "BROKKOLI 500G"), ("Blumenkohl", "BLUMENKOHL 1STK"),
                ("Zwiebeln 1kg", "ZWIEBELN 1KG"), ("Knoblauch 3er", "KNOBLAUCH 3ER"),
                ("Zitronen 500g", "ZITRONEN 500G"), ("Orangen 1.5kg", "ORANGEN 1.5KG"),
                ("Heidelbeeren 125g", "HEIDELBEEREN 125G"), ("Himbeeren 125g", "HIMBEEREN 125G")
            ]
        },
        "Fleisch & Fisch": {
            "storage": "Kühlschrank",
            "shelf_life": 3,
            "items": [
                ("Rinderhackfleisch 500g", "RINDERHACK FLEISCH 500G"), ("Hähnchenbrustfilet 400g", "HAEHNCHENBRUST 400G"),
                ("Schweineschnitzel 500g", "SCHWEINESCHNITZEL 500G"), ("Putengeschnetzeltes 400g", "PUTENGESCHNETZELTES"),
                ("Bratwurst 5er", "BRATWURST 5STK"), ("Lachsfilet 200g", "LACHSFILET 200G"),
                ("Rinderhüftsteak", "RINDERHUEFTSTEAK 250G"), ("Kochschinken 200g", "KOCHSCHINKEN 200G"),
                ("Delikatess Salami 150g", "DELIKATESS SALAMI 150G"), ("Wiener Würstchen 300g", "WIENER WUERSTCHEN 300G"),
                ("Geflügelbratwurst", "GEFLUEGELBRATWURST"), ("Geflügelfleischwurst", "GEFLUEGELFLEISCHWURST"),
                ("Rindergulasch 500g", "RINDERGULASCH 500G"), ("Schweinehackfleisch 500g", "SCHWEINEHACK 500G"),
                ("Räucherlachs 100g", "RAEUCHERLACHS 100G"), ("Thunfisch Steak 250g", "THUNFISCH STEAK"),
                ("Kabeljau Filet 250g", "KABELJAU FILET"), ("Landleberwurst 150g", "LANDLEBERWURST 150G"),
                ("Bacon Scheiben 100g", "BACON SCHEIBEN 100G"), ("Fleischsalat 200g", "FLEISCHSALAT 200G")
            ]
        },
        "Molkereiprodukte & Kühlung": {
            "storage": "Kühlschrank",
            "shelf_life": 12,
            "items": [
                ("Vollmilch 3.5% 1L", "VOLLMILCH 3.5% 1L"), ("H-Milch 1.5% 1L", "H-MILCH 1.5% 1L"),
                ("Deutsche Markenbutter 250g", "MARKENBUTTER 250G"), ("Magerquark 500g", "MAGERQUARK 500G"),
                ("Naturjoghurt 3.5% 500g", "NATURJOGHURT 500G"), ("Gouda Jung 48% 400g", "GOUDA JUNG 400G"),
                ("Butterkäse Scheiben 250g", "BUTTERKAESE SCHEIBEN"), ("Schlagsahne 200g", "SCHLAGSAHNE 200G"),
                ("Schmand 24% 200g", "SCHMAND 200G"), ("Saure Sahne 10% 200g", "SAURE SAHNE 200G"),
                ("Bio Eier 10er", "BIO EIER 10STK"), ("Freilandeier 10er", "FREILANDEIER 10STK"),
                ("Frischkäse Natur 200g", "FRISCHKAESE NATUR 200G"), ("Mozzarella 125g", "MOZZARELLA 125G"),
                ("Emmentaler Gerieben 200g", "EMMENTALER GERIEBEN"), ("Feta Original 200g", "FETA ORIGINAL 200G"),
                ("Schafskäse 200g", "SCHAFSKAESE 200G"), ("Milchreis Zimt 200g", "MILCHREIS ZIMT"),
                ("Protein Pudding Schoko", "PROTEIN PUDDING SCHOKO"), ("Creme Fraiche 150g", "CREME FRAICHE 150G")
            ]
        },
        "Vorratskammer": {
            "storage": "Vorratskammer",
            "shelf_life": 180,
            "items": [
                ("Spaghetti No.5 500g", "SPAGHETTI 500G"), ("Penne Rigate 500g", "PENNE RIGATE 500G"),
                ("Basmati Reis 1kg", "BASMATI REIS 1KG"), ("Jasmin Reis 1kg", "JASMIN REIS 1KG"),
                ("Gehackte Tomaten 400g", "GEHACKTE TOMATEN 400G"), ("Passierte Tomaten 500g", "PASSIERTE TOMATEN"),
                ("Gemüsebrühe 150g", "GEMUESEBRUEHE 150G"), ("Sonnenblumenöl 1L", "SONNENBLUMENOEL 1L"),
                ("Natives Olivenöl 750ml", "OLIVENOEL EXTRA VIRGIN"), ("Weizenmehl Type 405 1kg", "WEIZENMEHL T405 1KG"),
                ("Feiner Zucker 1kg", "ZUCKER FEIN 1KG"), ("Jodsalz 500g", "JODSALZ 500G"),
                ("Haferflocken Zart 500g", "HAFERFLOCKEN ZART"), ("Kidneybohnen 400g", "KIDNEYBOHNEN 400G"),
                ("Mais Dose 330g", "MAIS DOSE 330G"), ("Pesto Genovese 190g", "PESTO GENOVESE 190G"),
                ("Bautz'ner Senf 200ml", "BAUTZNER SENF 200ML"), ("Tomatenketchup 500ml", "TOMATENKETCHUP 500ML"),
                ("Mayonnaise 80% 250ml", "MAYONNAISE 250ML"), ("Kartoffelpüree 3er", "KARTOFFELPUEREE 3ER")
            ]
        },
        "Tiefkühlkost": {
            "storage": "Gefrierschrank",
            "shelf_life": 90,
            "items": [
                ("Pizza Salami 350g", "PIZZA SALAMI 350G"), ("Pizza Margherita 3er", "PIZZA MARGHERITA 3ER"),
                ("Rahmspinat 800g", "RAHMSPINAT 800G"), ("Fischstäbchen 15er", "FISCHSTAEBCHEN 15STK"),
                ("Pommes Frites 1kg", "POMMES FRITES 1KG"), ("Chicken Nuggets 500g", "CHICKEN NUGGETS 500G"),
                ("Gemüsemix TK 750g", "GEMUESEMIX TK 750G"), ("Beerenmischung TK 300g", "BEERENMISCHUNG TK"),
                ("Lachsfilet TK 250g", "LACHSFILET TK 250G"), ("Kräuter der Provence TK", "KRAEUTER PROVENCE TK"),
                ("Laugengebäck TK 6er", "LAUGENGEBAECK TK 6STK"), ("Windbeutel 250g TK", "WINDBEUTEL TK 250G"),
                ("Flammkuchen Elsaß TK", "FLAMMKECHEN ELSASS"), ("Cevapcici TK 500g", "CEVAPCICI TK 500G")
            ]
        },
        "Bäckerei": {
            "storage": "Vorratskammer",
            "shelf_life": 4,
            "items": [
                ("Weltmeisterbrot 750g", "WELTMEISTERBROT 750G"), ("Vollkornbrot 500g", "VOLLKORNBROT 500G"),
                ("Buttertoast 500g", "BUTTERTOAST 500G"), ("Steinofenbaguette 250g", "STEINOFENBAGUETTE"),
                ("Kaiserbrötchen", "KAISERBROETCHEN 1STK"), ("Laugenstange", "LAUGENSTANGE 1STK"),
                ("Buttercroissant", "BUTTERCROISSANT 1STK"), ("Franzbrötchen", "FRANZBROETCHEN 1STK"),
                ("Sonnenblumenbrot 500g", "SONNENBLUMENBROT"), ("Pumpernickel 250g", "PUMPERNICKEL 250G")
            ]
        },
        "Getränke": {
            "storage": "Vorratskammer",
            "shelf_life": 120,
            "items": [
                ("Mineralwasser Medium 1.5L", "MINERALWASSER MED 1.5L"), ("Mineralwasser Classic 1.5L", "MINERALWASSER CLASS 1.5L"),
                ("Apfelsaft 100% 1L", "APFELSAFT 100% 1L"), ("Orangensaft 100% 1L", "ORANGENSAFT 100% 1L"),
                ("Cola Premium 1.5L", "COLA 1.5L PET"), ("Zitronenlimonade 1.5L", "ZITRONENLIMO 1.5L"),
                ("Pils Bier 0.5L", "PILS BIER 0.5L"), ("Weißbier 0.5L", "WEISSBIER 0.5L"),
                ("Multitaminsaft 1L", "MULTITAMINSAFT 1L"), ("Eistee Zitrone 1.5L", "EISTEE ZITRONE 1.5L"),
                ("Energiedrink 250ml", "ENERGYDRINK 250ML"), ("Tonic Water 1L", "TONIC WATER 1L")
            ]
        },
        "Süßwaren & Knabberartikel": {
            "storage": "Vorratskammer",
            "shelf_life": 90,
            "items": [
                ("Vollmilch Schokolade 100g", "VOLLMILCH SCHOKO 100G"), ("Zartbitter Schokolade 100g", "ZARTBITTER SCHOKO 100G"),
                ("Paprika Chips 150g", "PAPRIKA CHIPS 150G"), ("Erdnussflips 200g", "ERDNUSSFLIPS 200G"),
                ("Gummibärchen 200g", "GUMMIBAERCHEN 200G"), ("Prinzenrolle 400g", "PRINZENROLLE 400G"),
                ("Salzstangen 250g", "SALZSTANGEN 250G"), ("Studentenfutter 200g", "STUDENTENFUTTER 200G"),
                ("Müsli Riegel 6er", "MUESLI RIEGEL 6ER"), ("Marmorkuchen 400g", "MARMORKUCHEN 400G")
            ]
        },
        "Tiernahrung": {
            "storage": "Vorratskammer",
            "shelf_life": 180,
            "items": [
                ("Katzenfutter Gelee 400g", "KATZENFUTTER GELEE"), ("Katzentrockenfutter 1kg", "KATZENTROCKENFUTTER 1KG"),
                ("Hundefutter Rind 800g", "HUNDEFUTTER RIND 800G"), ("Hundekausnacks 200g", "HUNDEKAUSNACKS 200G"),
                ("Katzenmilch 200ml", "KATZENMILCH 200ML"), ("Vogelfutter 1kg", "VOGELFUTTER 1KG")
            ]
        },
        "Drogerie": {
            "storage": "Haushalt",
            "shelf_life": 365,
            "items": [
                ("Toilettenpapier 3-lagig 8er", "TOILETTENPAPIER 3L 8ER"), ("Küchenrolle 4er", "KUECHENROLLE 4ER"),
                ("Handseife Nachfüllbeutel 500ml", "HANDSEIFE NACHFUEL 500ML"), ("Duschgel Fresh 300ml", "DUSCHGEL FRESH 300ML"),
                ("Shampoo Normales Haar 250ml", "SHAMPOO NORMAL 250ML"), ("Zahnpasta Fresh 75ml", "ZAHNPASTA FRESH 75ML"),
                ("Spülmittel Citron 500ml", "SPUELMITTEL CITRON"), ("Vollwaschmittel 30WL", "VOLLWASCHMITTEL 30WL"),
                ("Allzweckreiniger 1L", "ALLZWECKREINIGER 1L"), ("Müllbeutel 30L 20er", "MUELLBEUTEL 30L 20ER")
            ]
        }
    }

    # Descriptors to scale the database to 20,000 distinct items
    variations = [
        (" Standard", ""), (" Bio", " BIO"), (" Premium", " PREM"), (" XXL", " XXL"),
        (" Light", " LIGHT"), (" Zero", " ZERO"), (" Mager", " MAGER"), (" Delikatess", " DELIKATESS"),
        (" Natur", " NATUR"), (" Würzung Klassik", " KLASSIK"), (" Feine Auslese", " AUSLESE"),
        (" Regional", " REGIONAL"), (" Frischepack", " FRISCH"), (" Extra", " EXTRA"),
        (" Sparpack", " SPARPACK"), (" Aktionspack", " AKTION"), (" Mild", " MILD"),
        (" Scharf", " SCHARF"), (" Knusprig", " KNUSPRIG"), (" Ungesüßt", " UNGESUESST")
    ]

    total_count = 0
    records = []

    # Iterate through markets and categories to build 20,000 items
    for market_key, brands in markets.items():
        for cat_name, cat_data in categories.items():
            storage = cat_data["storage"]
            shelf_life = cat_data["shelf_life"]

            for clean_base, pattern_base in cat_data["items"]:
                for brand in brands:
                    for var_clean, var_pattern in variations:
                        item_id = str(uuid.uuid4())
                        
                        if brand in ["Bauer", "Dr. Oetker", "Müllermilch", "Coca-Cola", "Knorr", "Barilla", "Nestlé", "Ferrero"]:
                            brand_prefix = f"{brand} "
                            pattern_prefix = f"{brand.upper()} "
                        else:
                            brand_prefix = f"{brand} "
                            # Abbreviate brand for receipt pattern
                            brand_code = brand.split()[0].upper()
                            if len(brand_code) > 4 and not brand_code.startswith("BIO"):
                                brand_code = brand_code[:3]
                            pattern_prefix = f"{brand_code} "

                        clean_name = f"{brand_prefix}{clean_base}{var_clean}".strip()
                        receipt_pattern = f"{pattern_prefix}{pattern_base}{var_pattern}".strip()

                        records.append((
                            item_id,
                            market_key,
                            receipt_pattern,
                            clean_name,
                            cat_name,
                            storage,
                            shelf_life
                        ))
                        total_count += 1

                        if total_count >= 20000:
                            break
                    if total_count >= 20000:
                        break
                if total_count >= 20000:
                    break
            if total_count >= 20000:
                break
        if total_count >= 20000:
            break

    # Bulk insert into market_dictionary table
    cursor.executemany("""
    INSERT INTO market_dictionary (id, market, receiptPattern, cleanName, category, defaultStorage, defaultShelfLifeDays)
    VALUES (?, ?, ?, ?, ?, ?, ?);
    """, records)

    # Populate FTS virtual table
    cursor.execute("""
    INSERT INTO market_dictionary_fts (rowid, receiptPattern, cleanName, category)
    SELECT rowid, receiptPattern, cleanName, category FROM market_dictionary;
    """)

    conn.commit()
    conn.close()

    print(f"Erfolgreich SQLite Asset-Datenbank mit {total_count} Artikeln erstellt unter: {db_path}")

if __name__ == "__main__":
    generate_database()
