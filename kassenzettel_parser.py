# -*- coding: utf-8 -*-
"""
Kassenzettel-Parser für den Webservice von 'Kühlschrank Profi 🍏'.
Portierung und Erweiterung der robusten Filter- und Extraktionsregeln aus KassenzettelParser.kt.
"""

import re
import json
import uuid
import base64
import io
import os
import urllib.request
from datetime import datetime, timedelta

try:
    import pypdf
except Exception:
    pypdf = None

try:
    import fitz
except Exception:
    fitz = None

try:
    import pytesseract
    from PIL import Image
except Exception:
    pytesseract = None


def sanitize_ocr_text(text: str) -> str:
    """Entfernt unlesbare C0/C1-Steuerzeichen (z. B. \\x9f aus PDF-Extraktionen) und normalisiert Leerzeichen."""
    if not text:
        return ""
    clean = re.sub(r'[\x00-\x08\x0B\x0C\x0E-\x1F\x7F-\x9F]', ' ', text)
    clean = clean.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2007', ' ').replace('\uFEFF', ' ').replace('\u200B', ' ')
    return clean

NON_FOOD_KEYWORDS = {
    # Hygiene, Kosmetik & Drogerie
    "zahnpasta", "zahncreme", "dentagard", "blend-a-med", "colgate", "elmex", "aronal",
    "sensodyne", "oral-b", "zahnbürste", "zahnbuerste", "zahnseide", "mundspülung", "mundspuelung",
    "shampoo", "duschgel", "dusche", "deodorant", "deo", "seife", "handseife",
    "bodylotion", "hautcreme", "gesichtscreme", "haarspray", "haargel", "haarlack",
    "taschentücher", "taschentuecher", "feuchttücher", "feuchttuecher", "kosmetiktücher", "kosmetiktuecher",
    "wattepads", "wattestäbchen", "wattestaebchen", "toilettenpapier", "klopapier", "wc-papier",
    "küchenrolle", "kuechenrolle", "tampons", "binden", "slipeinlagen", "windeln",
    "rasierer", "rasierklingen", "rasierschaum", "aftershave", "handcreme", "lotion", "parfüm", "parfuem",
    "nagellack", "lippenstift", "sonnencreme", "sonnenmilch", "pflaster", "verband",
    # Reinigung & Haushalt
    "weichspüler", "weichspueler", "kuschelweich", "lenor", "comfort", "waschmittel",
    "persil", "ariel", "spee", "weißer riese", "dash", "spülmittel", "spuelmittel",
    "geschirrspültabs", "geschirrspueltabs", "spültabs", "spueltabs", "somat", "finish",
    "klarspüler", "klarspueler", "spülsalz", "spuelsalz", "fit gsm", "allzweckreiniger", "reiniger",
    "meister proper", "der general", "ajax", "biff", "antikal", "cillit bang", "sagrotan",
    "wc-reiniger", "wc-frisch", "wc-stein", "glasreiniger", "fensterreiniger", "scheibenklar",
    "scheibenreiniger", "entkalker", "müllbeutel", "muellbeutel", "müllsack", "muellsack",
    "gefrierbeutel", "alufolie", "frischhaltefolie", "backpapier", "schwamm", "putzlappen", "spültuch",
    # Küchenartikel & Haushaltswaren
    "bratpfanne", "pfanne", "kochtopf", "topf", "küchenmesser", "kuechenmesser", "kochmesser",
    "kellnermesser", "schneidebrett", "brettchen", "besteck", "gabel", "löffel", "loeffel",
    "tasse", "kaffeebecher", "teller", "schüssel", "schuessel", "dosenöffner", "dosenoeffner",
    "geschirr", "küchenwaage", "kuechenwaage", "sieb", "backform", "reibe", "vorratsdose",
    "thermoskanne", "trinkflasche", "backblech", "trinkglas", "weinglas", "bierglas", "gläserset",
    # Elektronik & Garantie-Artikel
    "toaster", "wasserkocher", "mixer", "standmixer", "handmixer", "blender", "pürierstab", "puerierstab",
    "kopfhörer", "kopfhoerer", "earbuds", "ladekabel", "kabel", "adapter", "usb-c", "usb",
    "lightning", "akku", "lampe", "leuchte", "led-stripe", "lautsprecher", "steckdosenleiste",
    "powerbank", "rasierapparat", "fön", "foen", "haartrockner", "glätteisen", "glaetteisen",
    "elektronik", "garantie", "ventilator", "wecker", "smartwatch",
    # Kleidung & Textilien
    "retroshorts", "shorts", "boxershorts", "unterhose", "unterwäsche", "unterwaesche",
    "slip", "socken", "strümpfe", "struempfe", "strumpfhose", "bluse", "hemd",
    "t-shirt", "shirt", "hose", "pullover", "jacke", "schuhe", "bettwäsche", "handtuch",
    # Büro, Schreibwaren & Medien
    "kugelschreiber", "karteikarten", "stift", "bleistift", "marker", "textmarker",
    "notizblock", "heft", "ordner", "kopierpapier", "tesafilm", "klebestift", "kleber",
    "zeitung", "zeitschrift", "magazin", "rätselheft", "raetselheft", "buch",
    # Sonstiges Non-Food
    "batterie", "batterien", "glühbirne", "gluehbirne", "led-lampe", "leuchtmittel", "feuerzeug",
    "kerze", "kerzen", "teelichter", "zigaretten", "tabak", "zigarren", "katzenstreu", "vogelsand",
    "blumenerde", "blumenstrauss", "schnittblumen", "topfpflanze", "orchidee", "gutschein",
    "geschenkkarte", "guthabenkarte", "itunes", "google play", "amazon karte"
}

CORRECTIONS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipt_corrections.json")

def load_receipt_corrections() -> dict:
    if os.path.exists(CORRECTIONS_FILE):
        try:
            with open(CORRECTIONS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_receipt_corrections(corrections: dict):
    try:
        with open(CORRECTIONS_FILE, "w", encoding="utf-8") as f:
            json.dump(corrections, f, ensure_ascii=False, indent=2)
    except Exception:
        pass

def add_receipt_correction(original_name: str, corrected_name: str):
    if not original_name or not corrected_name:
        return
    orig = original_name.strip()
    corr = corrected_name.strip()
    if not orig or not corr or orig.lower() == corr.lower():
        return
    corrections = load_receipt_corrections()
    corrections[orig] = corr
    save_receipt_corrections(corrections)

LEARNED_PRODUCTS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "learned_products.json")

def load_learned_products() -> list:
    if os.path.exists(LEARNED_PRODUCTS_FILE):
        try:
            with open(LEARNED_PRODUCTS_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, list):
                    return data
                elif isinstance(data, dict):
                    return list(data.values())
        except Exception:
            return []
    return []

def save_learned_products(items: list):
    try:
        with open(LEARNED_PRODUCTS_FILE, "w", encoding="utf-8") as f:
            json.dump(items, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"Fehler beim Speichern der gelernten Produkte: {e}")

def normalize_text_for_matching(text: str) -> str:
    if not text:
        return ""
    t = sanitize_ocr_text(text).lower()
    t = re.sub(r'[\d,.]+(?:\s*€|\s*eur)?', ' ', t)
    t = re.sub(r'[^a-z0-9äöüß]', ' ', t)
    return " ".join(t.split())

def learn_product(raw_name: str, clean_name: str, unit: str = "Stk.", category: str = "Vorrat", storage_location: str = "Kühlschrank", supermarket: str = ""):
    raw = (raw_name or "").strip()
    clean = (clean_name or "").strip()
    if not clean or len(clean) < 2:
        return
    if not raw:
        raw = clean

    market = (supermarket or "").strip()
    products = load_learned_products()

    norm_raw = normalize_text_for_matching(raw)
    updated = False

    for item in products:
        item_norm = normalize_text_for_matching(item.get("rawName", ""))
        item_market = (item.get("supermarket") or "").strip().lower()
        if item_norm == norm_raw and (not market or not item_market or item_market == market.lower() or item_market == "alle"):
            item["name"] = clean
            item["unit"] = unit or item.get("unit", "Stk.")
            item["category"] = category or item.get("category", "Vorrat")
            item["storageLocation"] = storage_location or item.get("storageLocation", "Kühlschrank")
            if market:
                item["supermarket"] = market
            item["updatedAt"] = datetime.now().isoformat()
            item["count"] = int(item.get("count", 1)) + 1
            updated = True
            break

    if not updated:
        new_entry = {
            "id": str(uuid.uuid4()),
            "rawName": raw,
            "name": clean,
            "unit": unit or "Stk.",
            "category": category or "Vorrat",
            "storageLocation": storage_location or "Kühlschrank",
            "supermarket": market or "Alle",
            "createdAt": datetime.now().isoformat(),
            "updatedAt": datetime.now().isoformat(),
            "count": 1
        }
        products.append(new_entry)

    save_learned_products(products)
    add_receipt_correction(raw, clean)

def find_learned_product(line_text: str, supermarket: str = "") -> dict:
    """
    Sucht einen gelernten Artikel anhand von Exakt- oder Ähnlichkeitsabgleich.
    Gibt das gelernte Produkt-Dictionary zurück oder None.
    """
    if not line_text:
        return None
    norm_line = normalize_text_for_matching(line_text)
    if not norm_line or len(norm_line) < 2:
        return None

    products = load_learned_products()
    if not products:
        return None

    market_lower = (supermarket or "").lower()

    # 1. Exakter Match
    best_exact = None
    for p in products:
        p_raw = normalize_text_for_matching(p.get("rawName", ""))
        p_name = normalize_text_for_matching(p.get("name", ""))
        p_market = (p.get("supermarket") or "").lower()

        if norm_line == p_raw or norm_line == p_name or (len(p_raw) >= 4 and p_raw in norm_line):
            if market_lower and p_market in [market_lower, "alle", ""]:
                return p
            if not best_exact:
                best_exact = p

    if best_exact:
        return best_exact

    # 2. Ähnlichkeitssuche (Fuzzy-Match)
    import difflib
    best_match = None
    best_ratio = 0.0

    for p in products:
        p_raw = normalize_text_for_matching(p.get("rawName", ""))
        if not p_raw or len(p_raw) < 3:
            continue
        p_market = (p.get("supermarket") or "").lower()

        ratio = difflib.SequenceMatcher(None, norm_line, p_raw).ratio()
        if market_lower and p_market in [market_lower, "alle"]:
            ratio += 0.05

        if ratio > best_ratio and ratio >= 0.82:
            best_ratio = ratio
            best_match = p

    return best_match

PET_FOOD_KEYWORDS = {
    "perle", "gourmet", "felix", "whiskas", "sheba", "purina", "chappi", "pedigree",
    "cesar", "vitakraft", "katzenfutter", "hundefutter", "tiernahrung", "tierfutter",
    "nassfutter", "trockenfutter", "katzensnack", "hundesnack", "dreamies", "kitekat",
    "frolic", "royal canin", "animonda", "rinti", "miamor", "catessy", "katzen", "hunde",
    "gourmet perle", "felix soup", "sheba sauce"
}

def is_pet_food(text: str) -> bool:
    if not text:
        return False
    lower = text.lower()
    return any(k in lower for k in PET_FOOD_KEYWORDS)

def is_non_food(text: str) -> bool:
    if not text:
        return False
    lower = text.lower()
    if is_pet_food(lower):
        return False
    if any(k in lower for k in ["creme fraiche", "crème fraîche", "eiscreme", "schokocreme", "kaffeecreme"]):
        return False
    return any(k in lower for k in NON_FOOD_KEYWORDS)

RECEIPT_ARCHIVE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipt_archive.json")

def load_receipt_archive() -> list:
    if os.path.exists(RECEIPT_ARCHIVE_FILE):
        try:
            with open(RECEIPT_ARCHIVE_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                return data if isinstance(data, list) else []
        except Exception:
            return []
    return []

def save_receipt_archive(items: list):
    try:
        with open(RECEIPT_ARCHIVE_FILE, "w", encoding="utf-8") as f:
            json.dump(items, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"Fehler beim Speichern der Beleg-Ablage: {e}")

def classify_non_food(text: str) -> str:
    lower = text.lower()
    if any(k in lower for k in [
        "toaster", "wasserkocher", "mixer", "blender", "pürierstab", "puerierstab",
        "kopfhörer", "kopfhoerer", "earbuds", "ladekabel", "kabel", "adapter", "usb",
        "lightning", "akku", "lampe", "leuchte", "led", "lautsprecher", "steckdosenleiste",
        "powerbank", "rasierapparat", "fön", "foen", "haartrockner", "glätteisen",
        "elektronik", "garantie", "ventilator", "wecker", "smartwatch", "batterie"
    ]):
        return "Elektronik & Garantie"
    if any(k in lower for k in [
        "pfanne", "bratpfanne", "topf", "kochtopf", "besteck", "küchenmesser", "kuechenmesser",
        "kochmesser", "messer", "kellnermesser", "schneidebrett", "brettchen", "gabel", "löffel",
        "loeffel", "tasse", "teller", "schüssel", "schuessel", "dosenöffner", "dosenoeffner",
        "geschirr", "küchenwaage", "kuechenwaage", "sieb", "backform", "reibe", "vorratsdose",
        "thermoskanne", "trinkflasche", "backblech", "trinkglas", "weinglas"
    ]):
        return "Küchenartikel"
    if any(k in lower for k in [
        "zahnpasta", "zahncreme", "shampoo", "duschgel", "deo", "seife", "creme",
        "lotion", "taschentücher", "feuchttücher", "klopapier", "toilettenpapier",
        "weichspüler", "waschmittel", "spülmittel", "reiniger", "windeln", "parfüm", "parfuem"
    ]):
        return "Drogerie & Hygiene"
    return "Non-Food"

def archive_non_food_item(name: str, price: float = 0.0, quantity: int = 1, purchase_date: str = "", supermarket: str = "", raw_line: str = "") -> dict:
    if not name or not is_non_food(name):
        return None
    archive = load_receipt_archive()
    category = classify_non_food(name)
    clean_n = name.strip()
    clean_n = re.sub(r'[\d,.]+(?:\s*€|\s*eur)?', '', clean_n).strip()
    clean_n = re.sub(r'^[AB12*#€-]\s+', '', clean_n).strip()
    clean_n = re.sub(r'\s+[*#€AB12-]+\s*$', '', clean_n).strip()
    if not clean_n:
        clean_n = name.strip()

    price_val = round(abs(float(price)), 2) if price else 0.0
    date_val = purchase_date or datetime.now().strftime("%d.%m.%Y")
    market_val = supermarket or "Supermarkt"

    entry = {
        "id": str(uuid.uuid4()),
        "name": clean_n,
        "rawName": raw_line or name,
        "price": price_val,
        "quantity": max(1, quantity),
        "purchaseDate": date_val,
        "supermarket": market_val,
        "category": category,
        "archivedAt": datetime.now().isoformat()
    }

    for existing in archive:
        if (existing.get("name", "").lower() == clean_n.lower() and
            existing.get("purchaseDate") == date_val and
            abs(float(existing.get("price", 0.0)) - price_val) < 0.01 and
            existing.get("supermarket", "").lower() == market_val.lower()):
            return existing

    archive.append(entry)
    save_receipt_archive(archive)
    return entry

def classify_deposit(text: str, amount: float = 0.0) -> str:
    upper = text.upper()
    if amount < 0 or any(k in upper for k in ["LEERGUT", "RÜCKGABE", "RUECKGABE", "EINLÖSUNG", "EINLOESUNG", "PFANDBON", "PFANDGUTSCHRIFT"]):
        return "Leergut"
    if any(k in upper for k in ["KASTEN", "TRAEGER", "TRÄGER", "KASTENPFAND"]):
        return "Kasten"
    if any(k in upper for k in ["MEHRWEG", "MW-PFAND", "MW PFAND", "MW_PFAND", "GLASPFAND", "JOGHURTPFAND"]):
        return "Mehrweg"
    if any(k in upper for k in ["EINWEG", "DPG", "EW-PFAND", "EW PFAND"]):
        return "Einweg"
    if abs(abs(amount) - 0.25) < 0.01:
        return "Einweg"
    if abs(abs(amount) - 0.08) < 0.01 or abs(abs(amount) - 0.15) < 0.01:
        return "Mehrweg"
    if abs(abs(amount) - 1.50) < 0.01 or abs(abs(amount) - 3.10) < 0.01 or abs(abs(amount) - 3.30) < 0.01:
        return "Kasten"
    return "Pfand"

def extract_deposit_lines(text: str) -> list:
    if not text:
        return []
    lines = [l.strip() for l in sanitize_ocr_text(text).splitlines() if l.strip()]
    deposits = []
    pending_dep_name = None
    pending_dep_qty = 1

    for line in lines:
        upper = line.upper()
        if is_stop_line(upper):
            break

        is_dep = is_deposit_line(upper)

        m_mult = MULTIPLIER_REGEX.search(line)
        if m_mult and is_dep:
            try:
                pending_dep_qty = int(m_mult.group(1).replace(',', '.').split('.')[0])
            except Exception:
                pending_dep_qty = 1

        price_matches = list(PRICE_REGEX.finditer(line))
        if is_dep and price_matches:
            p_val = float(price_matches[-1].group(1).replace(',', '.'))
            if "-" in line or "LEERGUT" in upper or "RÜCKGABE" in upper or "RUECKGABE" in upper:
                p_val = -abs(p_val)
            dep_type = classify_deposit(line, p_val)
            deposits.append({
                "description": line,
                "type": dep_type,
                "amount": round(p_val, 2),
                "quantity": pending_dep_qty
            })
            pending_dep_name = None
            pending_dep_qty = 1
        elif is_dep and not price_matches:
            pending_dep_name = line
        elif pending_dep_name and price_matches:
            p_val = float(price_matches[-1].group(1).replace(',', '.'))
            if "-" in line or "LEERGUT" in pending_dep_name.upper():
                p_val = -abs(p_val)
            dep_type = classify_deposit(pending_dep_name, p_val)
            deposits.append({
                "description": f"{pending_dep_name} {line}",
                "type": dep_type,
                "amount": round(p_val, 2),
                "quantity": pending_dep_qty
            })
            pending_dep_name = None
            pending_dep_qty = 1

    return deposits

PRICE_HISTORY_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "price_history.json")

def load_price_history() -> dict:
    if os.path.exists(PRICE_HISTORY_FILE):
        try:
            with open(PRICE_HISTORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_price_history(data: dict):
    try:
        with open(PRICE_HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception:
        pass

def check_price_radar(item_name: str, current_price: float, quantity: int = 1, supermarket: str = "") -> dict:
    if not item_name or current_price <= 0:
        return {"priceDiff": "", "previousPrice": None, "priceDiffVal": 0.0}

    norm = normalize_text_for_matching(item_name)
    if not norm:
        return {"priceDiff": "", "previousPrice": None, "priceDiffVal": 0.0}

    current_unit_price = round(current_price / max(1, quantity), 2)
    history = load_price_history()
    prev_price = None

    if norm in history and "lastPrice" in history[norm]:
        prev_price = float(history[norm]["lastPrice"])
    else:
        for k, v in history.items():
            if len(k) >= 3 and (k in norm or norm in k):
                prev_price = float(v.get("lastPrice", 0))
                break

    if prev_price is not None and prev_price > 0:
        diff_val = round(current_unit_price - prev_price, 2)
        if abs(diff_val) >= 0.01:
            diff_str = f"+{diff_val:.2f} €".replace(".", ",") if diff_val > 0 else f"{diff_val:.2f} €".replace(".", ",")
            return {
                "priceDiff": diff_str,
                "previousPrice": prev_price,
                "priceDiffVal": diff_val,
                "currentUnitPrice": current_unit_price
            }

    return {"priceDiff": "", "previousPrice": prev_price, "priceDiffVal": 0.0, "currentUnitPrice": current_unit_price}

def record_price_radar_purchase(item_name: str, price: float, quantity: int = 1, supermarket: str = "", purchase_date: str = ""):
    if not item_name or price <= 0:
        return
    norm = normalize_text_for_matching(item_name)
    if not norm:
        return
    unit_price = round(price / max(1, quantity), 2)
    history = load_price_history()
    date_str = purchase_date or datetime.now().strftime("%d.%m.%Y")

    if norm in history:
        old_price = history[norm].get("lastPrice", unit_price)
        diff_val = round(unit_price - old_price, 2)
        diff_str = f"+{diff_val:.2f} €".replace(".", ",") if diff_val > 0 else f"{diff_val:.2f} €".replace(".", ",")
        history[norm]["lastPrice"] = unit_price
        history[norm]["lastSupermarket"] = supermarket or history[norm].get("lastSupermarket", "")
        history[norm]["lastDate"] = date_str
        if abs(diff_val) >= 0.01:
            changes = history[norm].get("changes", [])
            changes.append({
                "date": date_str,
                "supermarket": supermarket,
                "oldPrice": old_price,
                "newPrice": unit_price,
                "diff": diff_str,
                "diffVal": diff_val,
                "timestamp": datetime.now().isoformat()
            })
            history[norm]["changes"] = changes
    else:
        history[norm] = {
            "name": item_name,
            "lastPrice": unit_price,
            "lastSupermarket": supermarket,
            "lastDate": date_str,
            "changes": []
        }
    save_price_history(history)

def generate_instant_recipe_ideas(items: list) -> list:
    if not items:
        return []

    names = [it.get("name", "").strip() for it in items if it.get("name") and not it.get("isPetFood")]
    if not names:
        names = [it.get("name", "").strip() for it in items if it.get("name")]
    if not names:
        return []

    recipes = []
    meats = [n for n in names if any(k in n.lower() for k in ["hack", "hähnchen", "haehnchen", "pute", "schnitzel", "gulasch", "steak", "lachs", "wurst", "fleisch"])]
    veggies = [n for n in names if any(k in n.lower() for k in ["tomate", "paprika", "zwiebel", "möhre", "moehre", "karotte", "kartoffel", "spinat", "erbsen", "brokkoli", "salat", "pilz", "champignon"])]
    dairy = [n for n in names if any(k in n.lower() for k in ["käse", "kaese", "gouda", "mozzarella", "sahne", "schmand", "creme fraiche", "joghurt", "quark", "butter", "milch"])]
    carbs = [n for n in names if any(k in n.lower() for k in ["nudel", "spaghetti", "reis", "kloßteig", "klossteig", "brot", "brötchen", "kartoffel"])]

    if meats and (veggies or carbs):
        m = meats[0]
        side = (veggies + carbs)[0]
        recipes.append({
            "title": f"Herzhafte Schlemmerpfanne mit {m} & {side}",
            "ingredients": f"{m}, {side}" + (f", {dairy[0]}" if dairy else ", Olivenöl, Salz & Pfeffer"),
            "instructions": f"1. {m} in etwas Öl anbraten und würzen.\n2. {side} kleinschneiden und ca. 10 Min. mitgaren.\n3. Heiß servieren" + (f" und mit {dairy[0]} abrunden!" if dairy else "!"),
            "time": "ca. 20 Min."
        })
    elif veggies and (dairy or carbs):
        v = veggies[0]
        partner = (dairy + carbs)[0]
        recipes.append({
            "title": f"Frischer Auflauf mit {v} & {partner}",
            "ingredients": f"{v}, {partner}" + (f", {dairy[0]}" if dairy and dairy[0] != partner else ", Kräuter & Gewürze"),
            "instructions": f"1. Ofen auf 190°C vorheizen.\n2. {v} und {partner} in eine Auflaufform geben.\n3. Würzen" + (f" und mit {dairy[0]} überbacken." if dairy else " und 20 Min. backen."),
            "time": "ca. 25 Min."
        })
    elif len(names) >= 2:
        recipes.append({
            "title": f"Schnelle Pfannengericht-Kreation mit {names[0]} & {names[1]}",
            "ingredients": f"{names[0]}, {names[1]}, Gewürze nach Wahl",
            "instructions": f"1. {names[0]} vorbereiten und anbraten.\n2. {names[1]} hinzufügen und 8-10 Min. dünsten.\n3. Heiß genießen!",
            "time": "ca. 15 Min."
        })
    else:
        recipes.append({
            "title": f"Frische Zubereitung mit {names[0]}",
            "ingredients": f"{names[0]}, Gewürze nach Wahl",
            "instructions": f"1. {names[0]} schonend zubereiten.\n2. Nach persönlichem Geschmack verfeinern und servieren.",
            "time": "ca. 10 Min."
        })

    if len(recipes) < 2 and len(names) >= 2:
        remaining = [n for n in names if n not in recipes[0]["title"]]
        second_a = remaining[0] if remaining else names[-1]
        second_b = remaining[1] if len(remaining) > 1 else (dairy[0] if dairy else "Kräutern")
        recipes.append({
            "title": f"Schneller Snack: {second_a} à la Chef",
            "ingredients": f"{second_a}, {second_b}, Prise Salz & Pfeffer",
            "instructions": f"1. {second_a} vorbereiten und mit {second_b} anrichten.\n2. Nach Geschmack würzen und sofort genießen!",
            "time": "ca. 10 Min."
        })

    return recipes[:2]

EXPANSIONS = [
    # Tiernahrung & Haustierbedarf
    (r'\bGOURM\.?\s*PERLE\b', 'Gourmet Perle'),
    (r'\bGOURM\.?\b', 'Gourmet'),
    (r'\bFELIX\b', 'Felix'),
    (r'\bWHISKAS\b', 'Whiskas'),
    (r'\bSHEBA\b', 'Sheba'),
    (r'\bPURINA\b', 'Purina'),
    (r'\bKATZENF\.?\b', 'Katzenfutter'),
    (r'\bHUNDEF\.?\b', 'Hundefutter'),
    # Marken & Weine & Getränke
    (r'\bMAYB\.?\s*GRAUB\.?\b', 'Maybach Grauburgunder'),
    (r'\bMAYB\.?\s*WEISSB\.?\b', 'Maybach Weißburgunder'),
    (r'\bMAYB\.?\b', 'Maybach'),
    (r'\bGRAUB\.?\b', 'Grauburgunder'),
    (r'\bWEISSB\.?\b', 'Weißburgunder'),
    (r'\bTR\.?\b', 'trocken'),
    (r'\bLEFILOU\b', 'Le Filou'),
    (r'\bROSE\b', 'Rosé'),
    (r'\bELCAR\s+AIREN\b', 'El Coto Airen'),
    (r'\bREB\.?\s*PORTUG\.?\s*WEISSH\.?\b', 'Rebenschoppen Portugieser Weißherbst'),
    (r'\bREB\.?\s*MÜLLER[- ]?THURGAU\b', 'Rebenschoppen Müller-Thurgau'),
    (r'\bREB\.?\b', 'Rebenschoppen'),
    (r'\bPORTUG\.?\b', 'Portugieser'),
    (r'\bWEISSH\.?\b', 'Weißherbst'),
    (r'\bADELH\.?\s*MIWA\b', 'Adelholzener Mineralwasser'),
    (r'\bADELH\.?\b', 'Adelholzener'),
    (r'\bMIWA\b', 'Mineralwasser'),
    (r'\bTH\.?\s*WQ\.?\b', 'Thüringer Waldquell'),
    (r'\bTWQ\b', 'Thüringer Waldquell'),
    (r'\bSELECT\.D\.J\.?\b', 'Selektion des Jahres'),
    (r'\bZÖPF\.?\s*KÄSE\+SPECK\b', 'Käse-Speck-Zöpfchen'),
    (r'\bHÄH\.?\s*KNUSPERSCHNITZEL\b', 'Hähnchen-Knusperschnitzel'),
    (r'\bHÄH\.?\b', 'Hähnchen'),
    (r'\bHAEH\.?\b', 'Hähnchen'),
    (r'\bHÄHNCHENBR\.?\b', 'Hähnchenbrust'),
    (r'\bJT\.?\b', 'Jeden Tag'),
    (r'\bMÜ\.?\s*GRIESSPUDDING\b', 'Müller Grießpudding'),
    (r'\bMÜ\.?\b', 'Müller'),
    (r'\bCAF\.?\b', 'Coppenrath & Wiese'),
    (r'\bKN\.?\s*FIXE?\b', 'Knorr Fix'),
    (r'\bKPur\.\b', 'K-Purland'),
    (r'\bKlc\.\b', 'K-Classic'),
    (r'\bKfav\.\b', 'K-Favourites'),
    (r'\bK\.SCHLAGSAHNE\b', 'K-Classic Schlagsahne'),
    (r'\bK\.RAHMSPINAT\b', 'K-Classic Rahmspinat'),
    (r'\bK\.BLATTSPINAT\b', 'K-Classic Blattspinat'),
    (r'\bK\.NATURJOGHURT\b', 'K-Classic Naturjoghurt'),
    (r'\bK\.SCHWW\.SCHINKEN\b', 'K-Classic Schwarzwälder Schinken'),
    (r'\bK\.(?=[A-Za-z])', 'K-Classic '),
    (r'\bMETZGEREI\s+(?:BEDIENUNG|SB)\b', 'Metzgerei Frischetheke'),
    (r'\bMIREE\s+DATTEL-CUR\b', 'Miree Dattel-Curry'),
    (r'\bMAGGI\s+FIX\s+CARBO\b', 'Maggi Fix Carbonara'),
    (r'\bKOCHSCHINKEN\s+O\.?\b', 'Kochschinken'),
    (r'\bEDELSCHI\.?\b', 'Edelschimmelkäse'),
    (r'\bHARZBUBE\s+EDELSCHI\.?\b', 'Harzbube Edelschimmelkäse'),
    (r'\bBENS\s+EXPRESSREIS\b', "Ben's Original Expressreis"),
    (r'\bDELV\.SPAGHETTI\b', 'Delverde Spaghetti'),
    (r'\bÜLTJE\b', 'Ültje'),
    (r'\bVITA\s+COLA\s+PUR\s+L\b', 'Vita Cola Pur'),
    (r'\bVITA\s+COLA\b', 'Vita Cola'),
    (r'\bM\.?\s*FRZ\.?\s*CAM\.?\b', 'mit franz. Camembert'),
    (r'\bPACKUN\b', 'Packung'),
]

def extrahiere_mengenangaben(aktuelle_zeile, vorherige_zeile):
    daten = {
        "menge": 1.0,
        "einheit": "Stück",
        "gehoert_zu_artikel": vorherige_zeile
    }
    
    # Sucht nach Mustern wie "3 x 1,50" oder "0,500 kg x 2,99"
    mengen_muster = r"(\d+([.,]\d+)?)\s*(x|kg)\s*(\d+([.,]\d+)?)"
    treffer = re.search(mengen_muster, aktuelle_zeile.lower())
    
    if treffer:
        # Extrahiert die Zahl vor dem 'x' oder 'kg'
        daten["menge"] = float(treffer.group(1).replace(',', '.'))
        
        if "kg" in treffer.group(3):
            daten["einheit"] = "kg"
            
        return daten
        
    return None

PRICE_REGEX = re.compile(r'(-?\d+[,.]\d{2})')
TAX_SUFFIX_REGEX = re.compile(r'\s+(?:[AB12*#€]\s*|\d\s*)*$')
MULTIPLIER_REGEX = re.compile(r'^\s*(\d+(?:[,.]\d+)?)\s*(?:kg|g|l|ml|stk|stück|stueck|packun|packung|pack|btl|becher|dose|glas|fl)?\s*[*xX]\s*(\d+[,.]\d{2})', re.IGNORECASE)
WEIGHING_REGEX = re.compile(r'^(\d+[,.]\d+)\s*(kg|g)\s*[*xX]\s*(\d+[,.]\d{2})\s*(?:€/kg|EUR/kg|€|EUR)?(?:\s+(-?\d+[,.]\d{2}))?', re.IGNORECASE)

ALDI_MULT_REGEX = re.compile(
    r'^(\d+)\s*(?:Stk\.?\s*)?(?:[xX*]|Stk\.?\s*[aáà])\s*(\d+[,.]\d{2})\s*€?\s+(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?[AB12]?$',
    re.IGNORECASE
)

LIDL_MULT_REGEX = re.compile(
    r'^(.*?)\s+(\d+[,.]\d{2})\s*(?:[xX*]|Stk\.?\s*[aáà])\s*(\d+)\s+(-?\d+[,.]\d{2})\s*[AB12]?$',
    re.IGNORECASE
)

STANDARD_LINE_REGEX = re.compile(
    r'^(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?(?:[AB12*#€]\s*|\d\s*)*$'
)

KAUFLAND_INLINE_MULT = re.compile(
    r'^(.*?)\s+(\d+(?:[,.]\d+)?)\s*[*xX]\s*(\d+[,.]\d{2})\s*(?:€\s*)?$'
)

FOOTER_STOP_WORDS = {
    "SUMME", "GESAMT", "ZU ZAHLEN", "TOTAL", "KARTENZAHLUNG",
    "BARGELD", "GEG. BAR", "RÜCKGELD", "RUECKGELD", "ZAHLBETRAG", "GUTHABEN",
    "STEUER %", "KUNDENBELEG", "TERMINAL", "VISA", "GIROCARD",
    "MASTERCARD", "TSE-SIGNATUR", "PAYBACK", "LIDL PAY", "EC-CASH",
    "EMV-AID", "TERMINAL-ID", "TRACE", "BELEG-NR", "KARTEN-NR",
    "BAR", "KREDITKARTE", "EC-KARTE", "MWST", "MEHRWERTSTEUER",
    "UST", "STEUER", "ENDSUMME", "ZWISCHENSUMME", "GESAMTBETRAG",
    "UNBAR", "GUTSCHEIN", "COUPON", "APPLE PAY", "GOOGLE PAY",
    "ANZ. ARTIKEL", "ANZ.ARTIKEL", "NETTO APP", "NETTO PLUS APP", "BARAUSZAHLUNG"
}

IGNORE_KEYWORDS = {
    "PFANDWERT", "PFAND", "LEERGUT", "MEHRWEG", "EINWEG", "DPG",
    "PAPIERTRAGETASCHE", "KNOTENBEUTEL", "TRAGETASCHE", "PAPIERTASCHE", "EINKAUFSTÜTE", "EINKAUFSTUETE", "PERMANENTTASCHE",
    "BELEGKOPIE", "BONKOPIE", "HERZLICH WILLKOMMEN", "VIELEN DANK", "AUF WIEDERSEHEN", "IHR EINKAUF",
    "KAUFLAND", "LIDL", "REWE", "NAHKAUF", "ALDI", "DM-DROGERIE", "GLOBUS", "NETTO", "EDEKA", "PENNY", "ROSSMANN", "NORMA", "TEGUT",
    "STRASSE", "STR.", "WEIMAR", "JENA", "ERFURT", "ISSERSTEDT", "GMBH", "UST-ID", "DE1", "DE2",
    "EUR", "PREIS EUR", "PREIS", "SUMME", "GESAMT", "RABATT", "SOFORT-RABATT", "AKTION", "PFANDRÜCKGABE", "PFANDRUECKGABE",
    "MWST", "MEHRWERTSTEUER", "UST", "STEUER", "KARTENZAHLUNG", "EC-KARTE", "GIROCARD", "VISA", "MASTERCARD",
    "DATUM", "DATE", "UHRZEIT", "ZEIT", "TERMINAL", "KUNDE", "KASSIERER", "BEDIENER"
}

BRAND_DATABASE = {
    "SCHWW SCHINKEN": "Schwarzwälder Schinken",
    "KNORR FIX CHILI C CAR": "Knorr Fix Chili con Carne",
    "CASH CRANB MIX": "Cashew-Cranberry-Mix",
    "ERDN GERÖS GES": "Erdnüsse geröstet gesalzen",
    "H BRUSTFILET": "Hähnchenbrustfilet",
    "KPUR.H.BRUSTFILET": "K-Purland Hähnchenbrustfilet",
    "WAGNER ST PIZZA": "Wagner Steinofen Pizza",
    "OUÄSE": "Quäse",
    "EXSREIS": "Expressreis",
    "AROTTENKRÜSTCHEN": "Karottenkrüstchen",
    "CAROTTENKRÜSTCHEN": "Karottenkrüstchen",
    "KART.VFK 2,5KG": "Kartoffeln",
    "KART.VFK": "Kartoffeln",
    "FIN. HÄHNCHENBRUST C": "Hähnchenbrust",
    "FIN. HÄHNCHENBRUST": "Hähnchenbrust",
    "FIN. HÄHNCHENBR": "Hähnchenbrust",
    "KLOSSTEIG 750 G": "Kloßteig",
    "KLOSSTEIG": "Kloßteig",
    "KLOßTEIG": "Kloßteig",
    "K.BLATTSPINAT": "K-Classic Blattspinat",
    "TH.WQ. MEDIUM": "Thüringer Waldquell Medium",
    "TH.WQ. CLASSIC": "Thüringer Waldquell Classic",
    "TH.WQ.": "Thüringer Waldquell",
    "TWQ MEDIUM": "Thüringer Waldquell Medium",
    "TWQ KLASSIK": "Thüringer Waldquell Classic",
    "SÖHNLEIN WHITE ICE": "Söhnlein White Ice",
    "APEROL APERITIVO": "Aperol Aperitivo",
    "PROTEIN KÄSE MILD": "Protein Käse mild",
    "BAUTZ.SENF MS": "Bautz'ner Senf mittelscharf",
    "PRES. LA BRIQUE": "Président La Brique Weichkäse",
    "HÄ-GESCHNETZELTES": "Hähnchen-Geschnetzeltes",
    "GEFL.FLEISCHWURST": "Geflügelfleischwurst",
    "EDELSCHI.": "Edelschimmelkäse",
    "HARZBUBE EDELSCHI.": "Harzbube Edelschimmelkäse",
    "BENS EXPRESSREIS": "Ben's Original Expressreis",
    "SENS EXPRESSREIS": "Ben's Original Expressreis",
    "DELV.SPAGHETTI": "Delverde Spaghetti",
    "KLC GEH. TOM.": "K-Classic Gehackte Tomaten",
    "KLC.AMER.COOKIES": "K-Classic American Cookies",
    "ROTW. WEISSB.": "Rotwein Weißburgunder",
    "KN.FIX": "Knorr Fix",
    "FOL EPI LEGERE": "Fol Epi Légère",
    "CHURRO BITES": "Churro Bites",
    "K.NATURJOGHURT": "K-Classic Naturjoghurt",
    "ÜLTJE ERDNÜSSE": "Ültje Erdnüsse",
    "WAGNER RUSTIPANI": "Wagner Rustipani",
    "MOPRO JOGHURT": "Molkerei Joghurt",
    "MOPRO": "Molkerei",
    "MÖNCHOF": "Mönchshof",
    "MÖNCH.": "Mönchshof",
    "MOENCH.LANDBIER": "Mönchshof Landbier",
    "MOENCHSHOF ZWICK": "Mönchshof Zwickl",
    "JA! MILCH 1,5%": "Ja Milch",
    "JA! MILCH": "Ja Milch",
    "JA!": "Ja",
    "DBIO": "dmBio",
    "DABIO": "dmBio",
    "DEBIO": "dmBio",
    "HACKF. GEM.": "Hackfleisch gemischt",
    "LANDLEBERWURST": "Landleberwurst",
    "ASTRA URTYP": "Astra Urtyp",
    "MORTADELLA PAPRI": "Mortadella Paprika",
    "GEWUERZSPEKULAT.": "Gewürzspekulatius",
    "HIO EIER OKT": "Bio Eier 10er",
    "KÄSESCHEIBENBUTTERK.": "Butterkäse Scheiben",
    "LIGHT ROHSCHINKEN": "Rohschinken Light",
    "CLC": "K-Classic",
    "MEG. FEINESÜSSRAHM": "Meggler Feine Süßrahmbutter",
    "K-CLASSIC": "K-Classic",
    "GUT&G": "Gut & Günstig",
    "G&G": "Gut & Günstig",
    "CORNEDBEEF": "Corned Beef",
    "CORNED BEEF": "Corned Beef",
    "STREICHMETTW.": "Streichmettwurst",
    "TEEWURST": "Teewurst",
    "WIENER": "Wiener Würstchen",
    "KLC KIDNEYBOHNEN": "K-Classic Kidneybohnen",
    "KLC.GEMÜSEMAIS": "K-Classic Gemüsemais",
    "KLC.CASH.CRANB.MIX": "K-Classic Cashew-Cranberry-Mix",
    "KLC.ERDN.GERÖS/GES": "K-Classic Erdnüsse geröstet gesalzen",
    "K.SCHWW.SCHINKEN": "K-Classic Schwarzwälder Schinken",
    "KLC.WALNUSSKERNE": "K-Classic Walnusskerne",
    "KLC CANTUCCINI": "K-Classic Cantuccini",
    "BO SCHNITTBROETCHEN": "Schnittbrötchen",
    "BO-BIO WEIZENBROETCHEN": "Bio Weizenbrötchen",
    "BO-BIO DINKELRUSTI": "Bio Dinkelrusti Brötchen",
    "BO-BIO BB VITALRUSTI": "Bio Vitalrusti Brötchen",
    "GL CREME FRAICHE": "Crème Fraîche",
    "GL JOGH.": "Joghurt",
    "BIO BB EIER": "Bio Eier",
    "BIO BB ZITRONENSSAFT": "Bio Zitronensaft",
    "BIO BB KRAEUT.": "Bio Kräuter",
    "MINIPFLAUMEN TOMATEN": "Mini-Pflaumentomaten",
    "MILR.MUERITZER": "Milram Müritzer Käse",
    "MILRAM RAHMKAESE": "Milram Rahmkäse",
    "GW GARTENSALAT": "Gartensalat",
    "MAGGI FIX CARBO": "Maggi Fix Carbonara",
    "MIREE DATTEL-CUR": "Miree Dattel-Curry",
    "KOCHSCHINKEN O.": "Kochschinken",
    "CHERRYRISPENTOM.": "Cherry-Rispentomaten",
    "FIN.SWEET CHILI": "Finesse Sweet Chili",
    "FINESSE HÄHNCHEN": "Finesse Hähnchen",
    "FINESSE HAEHNCHEN": "Finesse Hähnchen",
    "BROTAUFSTR.THUNFISCH": "Brotaufstrich Thunfisch",
    "BROT TOMA/MOZARELLA": "Brotaufstrich Tomate-Mozzarella"
}

def is_deposit_line(upper: str) -> bool:
    if any(k in upper for k in [
        "PFAND", "LEERGUT", "MEHRWEG", "EINWEG", "DPG",
        "MW-PFAND", "MW PFAND", "MW_PFAND", "KASTENPFAND", "PFANDARTIKEL",
        "EW-PFAND", "EW PFAND", "GLASPFAND", "PFANDRÜCKGABE", "PFANDRUECKGABE",
        "PFANDBON", "PFANDGUTSCHRIFT", "FLASCHENPFAND"
    ]):
        return True
    if re.search(r'\b(?:KASTENPFAND|TRÄGERPFAND|TRAEGERPFAND)\b', upper):
        return True
    if re.match(r'^(?:\d+\s*[xX*]\s*)?(?:KASTEN|TRÄGER|TRAEGER)\b', upper.strip()) and not any(b in upper for b in ["PILS", "BIER", "COLA", "WASSER", "LIMO", "SAFT", "WEIZEN", "EXPORT", "HELLES"]):
        return True
    return False

def is_tax_line(upper: str) -> bool:
    if any(k in upper for k in ["MWST", "MEHRWERTSTEUER", "STEUERSATZ", "STEUERBETRAG", "STEUER %", "STEUER-BETRAG"]):
        return True
    if re.search(r'\bUST\b', upper):
        return True
    if re.search(r'\b(?:A|B|1|2)\s*=\s*\d+([.,]\d+)?\s*%', upper):
        return True
    if re.search(r'\b\d+([.,]\d+)?\s*%\s*(?:MWST|UST|STEUER)\b', upper):
        return True
    if "STEUER" in upper and "STEUERMARKE" not in upper:
        return True
    return False

def is_payment_line(upper: str) -> bool:
    if any(k in upper for k in [
        "KARTENZAHLUNG", "EC-KARTE", "EC-CASH", "GIROCARD", "VISA", "MASTERCARD",
        "MAESTRO", "AMEX", "V-PAY", "KREDITKARTE", "TERMINAL", "KUNDENBELEG",
        "HÄNDLERBELEG", "HAENDLERBELEG", "PAYBACK", "LIDL PAY", "APPLE PAY", "GOOGLE PAY",
        "BARGELD", "GEG. BAR", "GEGEBEN", "RÜCKGELD", "RUECKGELD", "GUTSCHEIN",
        "GUTSCHRIFT", "COUPON", "BARAUSZAHLUNG", "NETTO APP", "NETTO PLUS APP"
    ]):
        return True
    if re.search(r'\bBAR\b', upper) or re.search(r'\bUNBAR\b', upper):
        return True
    return False

def is_total_or_sum_line(upper: str) -> bool:
    return any(k in upper for k in [
        "SUMME", "GESAMT", "ZU ZAHLEN", "TOTAL", "ZAHLBETRAG",
        "GESAMTBETRAG", "ENDSUMME", "ZWISCHENSUMME", "RECHNUNGSBETRAG", "SALDO",
        "ANZ. ARTIKEL", "ANZ.ARTIKEL"
    ])

def is_date_or_time_line(line: str, upper: str) -> bool:
    if any(k in upper for k in ["DATUM", "UHRZEIT", "STAND:", "DATE"]):
        return True
    if "ZEIT" in upper and "ZEITSCHRIFT" not in upper and "MAHLZEIT" not in upper:
        return True
    date_match = re.search(r'\b\d{2}[./]\d{2}[./]\d{2,4}\b', line)
    if date_match:
        without_date = line.replace(date_match.group(0), "").strip()
        if not without_date or re.fullmatch(r'[\d:/\s.-]+', without_date):
            return True
    time_match = re.search(r'\b\d{2}:\d{2}(:\d{2})?\b', line)
    if time_match:
        without_time = line.replace(time_match.group(0), "").strip()
        if not without_time or re.fullmatch(r'[\d:/\s.-]+', without_time):
            return True
    return False

def is_non_food_or_garbage(upper: str) -> bool:
    if is_non_food(upper):
        return True
    if any(k in upper for k in [
        "PAPIERTRAGETASCHE", "TRAGETASCHE", "KNOTENBEUTEL", "PAPIERTASCHE",
        "PERMANENTTASCHE", "EINKAUFSTASCHE", "EINKAUFSTÜTE", "EINKAUFSTUETE"
    ]):
        return True
    if any(k in upper for k in [
        "TSE-SIGNATUR", "TSE-SERIENNUMMER", "TRANSAKTION", "BELEG-NR",
        "BON-NR", "BONNR", "TRACE-NR", "EMV-AID", "TERMINAL-ID", "KASSEN-ID",
        "KASSIERER", "BEDIENER", "BELEGKOPIE", "BONKOPIE",
        "HERZLICH WILLKOMMEN", "VIELEN DANK", "AUF WIEDERSEHEN", "IHR EINKAUF"
    ]):
        return True
    if any(k in upper for k in ["WWW.", "HTTP:", "HTTPS:", ".DE/", ".COM/", "HOTLINE", "KUNDENSERVICE"]):
        return True
    return False

def is_unwanted_receipt_line(line: str, upper: str) -> bool:
    return (is_total_or_sum_line(upper) or is_tax_line(upper) or
            is_payment_line(upper) or is_deposit_line(upper) or
            is_date_or_time_line(line, upper) or is_non_food_or_garbage(upper))

def is_code_or_receipt_number(line: str) -> bool:
    if not line:
        return True
    s = line.strip()
    if not s:
        return True
    # Preiszeilen oder Rabatte (z. B. '1,99 B', '-0,71', '2,18 A', '3,38 B') dürfen NIE als Code gefiltert werden
    if re.fullmatch(r'^[+-]?\s*\d+[,.]\d{2}(?:\s*€|\s*EUR)?(?:\s+[A-Za-z12*#€])?$', s):
        return False
    # Mengen-/Multiplikationszeilen (z. B. '2 * 1,09', '2 x 1,69') dürfen NIE als Code gefiltert werden
    if re.search(r'\b\d+\s*[*xX]\s*\d+[,.]\d{2}', s):
        return False
    # Reine Nummern und Ziffernfolgen mit Trennzeichen (z. B. '4234662 0 98', '2261', '098')
    if re.fullmatch(r'[\d\s./*#:-]+', s):
        if re.search(r'\d+[,.]\d{2}\b', s):
            return False
        return True
    # Kassen- / Terminal- / Artikel-Codes (z. B. 'T20002117 2 Mg', 'A12345678', 'POS12345')
    if re.match(r'^[A-Za-z]?\d{4,}(?:\s+[A-Za-z0-9]+)*$', s):
        return True
    # Überwiegend Ziffern mit minimalen Buchstaben (z. B. Prüfziffern wie '4234662 0 98', 'T20002117 2 Mg')
    letters = sum(1 for c in s if c.isalpha())
    digits = sum(1 for c in s if c.isdigit())
    if digits >= 3 and letters <= 2:
        return True
    return False

def is_header_or_noise_line(line: str, upper: str) -> bool:
    if is_code_or_receipt_number(line):
        return True
    if upper in ["EUR", "PREIS EUR", "PREIS", "WÄHRUNG", "WAEHRUNG"]:
        return True
    if upper.startswith("DEIN EINKAUF VOM") or upper.startswith("FILIALE "):
        return True
    if upper.startswith("*** EBON ***"):
        return True
    if is_unwanted_receipt_line(line, upper):
        return True
    if re.search(r'\b\d{5}\b', line):
        return True
    if any(k in upper for k in ["STRASSE", "STRAßE", "STR."]) or re.search(r'\bSTR\b', upper):
        return True
    if re.search(r'\b(?:WEG|GASSE|ALLEE|PLATZ|HAUSNR|PLZ|ISSERSTEDT|JENA|WEIMAR|ERFURT)\b', upper):
        return True
    if any(k in upper for k in ["GMBH", "CO. KG", "CO.KG", " CO KG"]) or re.search(r'\b(?:E\.K\.|E\.V\.)\b', upper):
        return True
    if re.search(r'\b(?:TEL|TELEFON|FON|FAX|UST-ID|ST-NR|STNR|ST\.-NR|STEUER)\b', upper):
        return True
    if re.search(r'\bDE\d+', upper):
        return True
    if re.search(r'(?:\+49|0\d{2,4})[\s/-]?\d{5,}', line):
        return True
    if re.search(r'\b\d{3,5}[/-]\d{3,8}\b', line):
        return True
    if upper.startswith("#") or re.match(r'^#\d+.*', upper):
        return True
    if any(upper == k or upper.startswith(k + " ") or (" " + k + " ") in upper for k in IGNORE_KEYWORDS):
        return True
    return False

def is_stop_line(upper: str) -> bool:
    if is_total_or_sum_line(upper):
        return True
    if is_payment_line(upper):
        return True
    for word in FOOTER_STOP_WORDS:
        if len(word) <= 4:
            if upper == word or upper.startswith(word + " ") or (" " + word + " ") in upper or upper.endswith(" " + word):
                return True
        else:
            if word in upper:
                return True
    return False

def detect_supermarket(text: str) -> str:
    upper = text.upper()
    if "GLOBUS" in upper:
        return "Globus"
    if any(k in upper for k in ["KAUFLAND", "K CARD", "KAUFLAND CARD", "K-CLASSIC", "K CLASSIC", "K-PUR", "K-FAVOURITES", "PREIS EUR"]):
        return "Kaufland"
    if any(k in upper for k in ["REWE", "NAHKAUF", "JA!", "JA MILCH"]):
        return "Rewe"
    if any(k in upper for k in ["NETTO-ONLINE", "NETTO APP", "NETTO PLUS", "DEIN EINKAUF VOM", "MARKEN-DISCOUNT"]):
        return "Netto"
    if re.search(r'\bNETTO\b(?!\s*(?:EUR|%|STEUER|BRUTTO))', upper):
        return "Netto"
    if any(k in upper for k in ["LIDL", "MILBONA", "CIEN", "LIDL PLUS", "LIDL PAY"]):
        return "Lidl"
    if any(k in upper for k in ["ALDI", "MILSANI", "GUT BIO", "ALDI NORD", "ALDI SÜD"]):
        return "Aldi"
    if any(k in upper for k in ["PENNY", "BEST MOMENTS"]):
        return "Penny"
    if any(k in upper for k in ["EDEKA", "GUT&GÜNSTIG", "GUT & GÜNSTIG", "G&G", "E AKTIV", "E CENTER"]):
        return "Edeka"
    if any(k in upper for k in ["DM-DROGERIE", " DM ", "DMBIO"]):
        return "dm"
    if "ROSSMANN" in upper:
        return "Rossmann"
    if "NORMA" in upper:
        return "Norma"
    if "TEGUT" in upper:
        return "Tegut"
    return "Kaufland"

def determine_smart_unit_and_storage(name: str, price: float, raw_text: str = ""):
    combined = f"{name.lower()} {raw_text.lower()}"
    
    # 1. Einheit bestimmen & bereinigen
    if any(k in combined for k in ["kiste", "kasten", "20x", "24x", "12x", "träger", "traeger"]) or (
        any(k in combined for k in ["wasser", "bier", "cola", "limo", "waldquell"]) and price >= 3.50
    ):
        unit = "Kiste"
    elif any(k in combined for k in ["brötchen", "broetchen", "rusti", "krüstchen", "semmel"]):
        unit = "Stk."
    elif any(k in combined for k in [
        "becher", "sahnebecher", "sahne", "schlagsahne", "saure sahne",
        "sauerrahm", "schmand", "creme fraiche", "crème fraîche", "joghurt", "quark",
        "pudding", "milchreis", "margarine", "rama", "lätta", "tzatziki", "aioli",
        "fleischsalat", "krautsalat", "feinkostsalat", "mascarpone", "ricotta",
        "hüttenkäse", "miree", "frischkäse", "aufstrich", "dip"
    ]):
        unit = "Becher"
    elif any(k in combined for k in [
        "dose", "red bull", "monster energy", "energy", "thunfisch", "tuna",
        "konserve", "eintopf", "gestückelte tomaten", "schältomaten", "mais", "sardinen", "kidneybohnen"
    ]):
        unit = "Dose"
    elif any(k in combined for k in [
        "glas", "marmelade", "konfitüre", "honig", "senf", "nutella",
        "apfelmus", "gewürzgurken", "gurken", "rotkohl", "sauerkraut", "sauerkirschen", "pesto", "oliven"
    ]):
        unit = "Glas"
    elif any(k in combined for k in ["saft", "apfelsaft", "orangensaft", "öl", "essig", "wein", "bier", "cola", "wasser", "limonade", "sirup"]):
        unit = "Flasche"
    elif any(k in combined for k in ["kg", "lose"]) or (any(k in combined for k in ["bananen", "äpfel", "kartoffeln", "möhren", "zwiebeln"]) and not any(k in combined for k in ["saft", "apfelmus", "kuchen", "tee", "2,5kg"])):
        unit = "kg"
    elif any(k in combined for k in ["packun", "packung", "pkg", "pck", "reis", "nudeln", "spaghetti", "kloßteig", "cookies", "spekulatius", "cantuccini"]):
        unit = "Packung"
    else:
        unit = "Stk."

    # Abkürzungen ausschreiben ('Packun' -> 'Packung')
    if unit.lower() in ["packun", "pack", "pck"]:
        unit = "Packung"

    # 2. Automatische Lagerort-Zuordnung & MHD-Datumsvorschlag nach Kategorie
    # Tiefkühl -> Gefrierfach, +180 Tage
    if any(k in combined for k in ["pizza", "tiefkühl", "tk ", "spinat", "eiscreme", "rustipani", "pommes", "erbsen sehr fein"]):
        storage = "Gefrierfach"
        days = 180
    # Geflügel / Hack -> Kühlschrank, +2 Tage
    elif any(k in combined for k in [
        "hackfleisch", "hackf", "rinderhack", "schweinehack", "geflügel", "gefluegel",
        "hähnchen", "haehnchen", "pute", "h-brustfilet", "hähnchenbrust", "knusperschnitzel"
    ]):
        storage = "Kühlschrank"
        days = 2
    # Molkerei / Käse -> Kühlschrank, +14 Tage
    elif any(k in combined for k in [
        "milch", "käse", "kaese", "gouda", "butter", "joghurt", "quark", "sahne",
        "schmand", "creme fraiche", "crème fraîche", "camembert", "brique", "edelschi",
        "edelschimmel", "parmesan", "mozzarella", "frischkäse", "miree", "ricotta",
        "mascarpone", "magerquark", "buttermilch", "kefir", "pudding"
    ]):
        storage = "Kühlschrank"
        days = 14
    # Frische Wurst, Fleisch & Fischwaren -> Kühlschrank, +5 Tage
    elif any(k in combined for k in [
        "wurst", "salami", "schinken", "fleischwurst", "leberwurst", "wiener",
        "mortadella", "fleisch", "steak", "schnitzel", "gulasch", "lachs", "forelle",
        "eier", "salat", "gartensalat"
    ]):
        storage = "Kühlschrank"
        days = 5
    # Tiernahrung -> Vorratskammer, +365 Tage
    elif is_pet_food(combined) or any(k in combined for k in [
        "perle", "gourmet", "felix", "whiskas", "sheba", "chappi", "pedigree",
        "katzenfutter", "hundefutter", "tiernahrung", "tierfutter", "nassfutter", "trockenfutter"
    ]):
        storage = "Vorratskammer"
        days = 365
    # Snacks -> Vorratskammer, +180 Tage
    elif any(k in combined for k in [
        "cookies", "keks", "kekse", "chips", "erdnüsse", "erdnuss", "cashew", "walnuss",
        "nüsse", "schokolade", "riegel", "churro", "waffeln", "spekulatius", "cantuccini"
    ]):
        storage = "Vorratskammer"
        days = 180
    # Trockenware / Konserven (Reis, Nudeln, Konserven) -> Vorratskammer, +365 Tage
    elif any(k in combined for k in [
        "reis", "nudeln", "spaghetti", "expressreis", "mehl", "zucker", "grieß", "haferflocken",
        "müsli", "cornflakes", "dose", "glas", "konserve", "eintopf", "tomatenmark", "tomaten",
        "gehackte tomaten", "bohnen", "erbsen", "kidneybohnen", "mais", "öl", "essig", "pesto",
        "senf", "ketchup", "sauerkraut", "rotkohl", "gurken", "marmelade", "honig", "nutella",
        "fix", "maggi", "knorr", "tee", "kaffee"
    ]):
        storage = "Vorratskammer"
        days = 365
    else:
        # Fallback nach Warengruppe
        if any(k in combined for k in ["brot", "brötchen", "bananen", "äpfel", "kartoffeln", "zwiebeln"]):
            storage = "Vorratskammer"
            days = 5
        elif any(k in combined for k in ["wasser", "cola", "limo", "bier", "wein", "saft"]):
            storage = "Vorratskammer"
            days = 180
        else:
            storage = "Kühlschrank"
            days = 7

    return unit, storage, days

def clean_product_name(raw_name: str) -> str:
    learned = find_learned_product(raw_name)
    if learned and learned.get("name"):
        return learned["name"]

    # 0. Gelerntes Wörterbuch (receipt_corrections.json) vorab prüfen
    corrections = load_receipt_corrections()
    raw_strip = raw_name.strip()
    if raw_strip in corrections:
        return corrections[raw_strip]
    for orig_k, corr_v in corrections.items():
        if orig_k.lower() == raw_strip.lower():
            return corr_v

    if is_code_or_receipt_number(raw_name):
        return ""

    clean = sanitize_ocr_text(raw_name).strip()
    if is_code_or_receipt_number(clean):
        return ""

    if clean in corrections:
        return corrections[clean]
    for orig_k, corr_v in corrections.items():
        if orig_k.lower() == clean.lower():
            return corr_v

    # 0b. Gezielte OCR- und Kassenkürzel-Korrekturen
    clean_upper = clean.upper()
    if re.search(r'\bALNI[G]?HURT\b', clean_upper) or (re.search(r'\bALMJOGHURT\b', clean_upper) and "5000" in clean_upper):
        return "Almjoghurt 500g Glas"

    if re.search(r'\bTSCHINKEN\b', clean_upper):
        return "Saftschinken 100g"

    if re.search(r'\bMEGGL\s*E\s+SCHEIBEN\b', clean_upper) or re.search(r'\bMEGGLE\s+SCHEIBEN\b', clean_upper) or re.search(r'\bMEGGL\s+E\b', clean_upper):
        return "Meggle Schmelzkäsescheiben"

    if any(re.search(p, clean_upper) for p in [
        r'\bGP\s+POCH\s+KAL[O0]\b',
        r'\bGO[LR]{1,2}\s*NET\s+A\s+L[8A]\s*CARTE\b',
        r'\bGOR\s*NET\s+A\s+LA\s*CARTE\b',
        r'\bGP\s+POCH\b'
    ]):
        return "Gourmet Perle Katzenfutter"

    # Entferne führende Multiplikatoren (z.B. "3 x Kaisersemmel", "12 x 0,19 EUR", etc.)
    clean = re.sub(r'^\d+\s*(?:5tk|stk|st|stück|stueck|x)\s*', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'^[AB12*#€-]\s+', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\b\d+([.,]\d+)?%', '', clean)
    clean = re.sub(r'\s+\d+[,.]\d{2}\s*€?$', '', clean)
    clean = re.sub(r'\s+\d{1,2}\s*$', '', clean)
    clean = re.sub(r'[,.]\d{2}\s*$', '', clean)
    clean = re.sub(r'\s+-QS\b', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\b(?:MW)?\d+\s*x\s*\d+([.,]\d+)?\s*(?:kg|g|l|ml)?\b', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\b\d+([.,]\d+)?\s*(?:kg|g|l|ml)\b', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\b\d+\s*(?:stk|st|stück|stueck)\b', '', clean, flags=re.IGNORECASE)

    # 1. Spezifische Expansions anwenden
    for pattern, rep in EXPANSIONS:
        clean = re.sub(pattern, rep, clean, flags=re.IGNORECASE)

    # 2. BRAND_DATABASE Mappings
    for short_brand, full_brand in BRAND_DATABASE.items():
        base = re.escape(short_brand.rstrip('.'))
        pattern = r'\b' + base + r'(?:\.|\b|[^\w\s]|$)'
        clean = re.sub(pattern, full_brand, clean, flags=re.IGNORECASE)

    # 3. Trailing Noise & Füllwörter entfernen
    clean = re.sub(r'\b(?:sort\.ab\d+ml|sort\.ab\d+g|sort\.ab|sort)\b', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\b\d+(?:er|pack)\b', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\b(?:stk|st|fl|btl|ausl|scheib|herzh|dkl)\b', '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\b\d{3,}\b', '', clean)
    clean = re.sub(r'[-*#\s.+:,/]+$', '', clean)
    clean = re.sub(r'^[-*#\s.+:,/]+', '', clean)
    clean = re.sub(r'\s+', ' ', clean).strip()

    # 4. Titel-Schreibweise (Groß-/Kleinschreibung)
    words = clean.split()
    capitalized_words = []
    for w in words:
        wl = w.lower()
        if wl in ["und", "mit", "vom", "der", "die", "das", "des", "im", "in", "von", "franz.", "franz"]:
            capitalized_words.append(wl)
        elif wl == "dmbio":
            capitalized_words.append("dmBio")
        elif wl == "h-milch":
            capitalized_words.append("H-Milch")
        elif wl == "k-classic":
            capitalized_words.append("K-Classic")
        elif wl == "k-purland":
            capitalized_words.append("K-Purland")
        elif wl == "k-favourites":
            capitalized_words.append("K-Favourites")
        elif wl == "le":
            capitalized_words.append("Le")
        elif wl == "filou":
            capitalized_words.append("Filou")
        elif wl == "rosé":
            capitalized_words.append("Rosé")
        else:
            capitalized_words.append(w.capitalize())

    clean = " ".join(capitalized_words)
    if clean in corrections:
        return corrections[clean]
    for orig_k, corr_v in corrections.items():
        if orig_k.lower() == clean.lower():
            return corr_v
        if len(orig_k) >= 3 and orig_k.lower() in clean.lower():
            clean = re.sub(r'\b' + re.escape(orig_k) + r'\b', corr_v, clean, flags=re.IGNORECASE)
    return clean

def parse_receipt_json(data: dict) -> list:
    """Extrahiert Produkte aus strukturiertem JSON wie in 'Kassenzettel json/'."""
    fokus = data
    for wrapper in ["receipt", "kassenbon", "bon", "data", "result", "beleg"]:
        if wrapper in data and isinstance(data[wrapper], dict):
            fokus = data[wrapper]
            break

    supermarket = fokus.get("markt") or fokus.get("supermarket") or fokus.get("vendor") or "Supermarkt"
    purchase_date = fokus.get("datum") or fokus.get("date") or fokus.get("purchaseDate") or datetime.now().strftime("%d.%m.%Y")
    purchase_time = str(fokus.get("uhrzeit") or fokus.get("time") or fokus.get("purchaseTime") or "").strip()
    items_raw = fokus.get("artikel") or fokus.get("items") or fokus.get("produkte") or fokus.get("products") or []
    
    products = []
    last_item = None

    for it in items_raw:
        raw_name = it.get("name") or it.get("title") or it.get("artikel") or ""
        price_val = float(it.get("preis") or it.get("price") or 0.0)
        
        upper = raw_name.upper()
        if is_deposit_line(upper):
            continue

        # Rabatt auf letztes Produkt anrechnen
        if price_val < 0 or any(k in upper for k in ["RABATT", "AKTION", "PREISVORTEIL", "GUTSCHEIN"]):
            if last_item:
                last_item["price"] = round(max(0.01, last_item["price"] + price_val), 2)
            continue

        qty = int(it.get("menge") or it.get("quantity") or 1)
        # Parse Menge aus Name z.B. "3 x Kaisersemmel"
        m_mult = re.match(r'^(\d+)\s*x\s*(.*)', raw_name, re.IGNORECASE)
        if m_mult:
            qty = int(m_mult.group(1))
            raw_name = m_mult.group(2)

        name = clean_product_name(raw_name)
        if not name or len(name) < 2 or is_unwanted_receipt_line(name, name.upper()):
            continue

        unit, storage, days = determine_smart_unit_and_storage(name, price_val, raw_name)
        pet = is_pet_food(name) or is_pet_food(raw_name)
        if pet:
            storage = "Vorratskammer"
        exp_date = datetime.now() + timedelta(days=days)
        item = {
            "name": name,
            "rawName": raw_name,
            "quantity": qty,
            "unit": unit,
            "storageLocation": storage,
            "price": round(price_val, 2),
            "supermarket": supermarket,
            "purchaseDate": purchase_date,
            "purchaseTime": purchase_time,
            "expiryDate": int(exp_date.timestamp() * 1000),
            "expiryDateStr": exp_date.strftime("%Y-%m-%d"),
            "isPetFood": pet,
            "category": "Tiernahrung" if pet else "Lebensmittel"
        }
        products.append(item)
        last_item = item

    return products

def parse_receipt_text(text: str) -> list:
    """
    Hauptfunktion: Analysiert und filtert Kassenbon-Text und liefert saubere Lebensmittel zurück.
    """
    # 1. Prüfe ob der Text im JSON-Format vorliegt
    clean_text = text.strip()
    if (clean_text.startswith("{") and clean_text.endswith("}")) or (clean_text.startswith("[") and clean_text.endswith("]")):
        try:
            parsed_json = json.loads(clean_text)
            if isinstance(parsed_json, dict):
                embedded_text = parsed_json.get("text") or parsed_json.get("ocr_text") or parsed_json.get("raw_text")
                if embedded_text and isinstance(embedded_text, str):
                    return parse_receipt_text(embedded_text)
                json_items = parse_receipt_json(parsed_json)
                if json_items:
                    return json_items
        except Exception:
            pass


    # 2. Text normalisieren (Steuerzeichen und schmale Leerzeichen entfernen)
    norm_text = sanitize_ocr_text(text)
    raw_lines = [line.strip() for line in norm_text.splitlines() if line.strip()]
    if not raw_lines:
        return []

    supermarket = detect_supermarket(norm_text)
    purchase_date, purchase_time = extract_purchase_datetime(norm_text)
    is_kaufland = (supermarket == "Kaufland" or "PREIS EUR" in norm_text.upper())

    items = []
    pending_name = None
    pending_qty = 1
    pending_unit_price = None
    pending_deposit = False
    pending_barcode = None

    for raw_line in raw_lines:
        line = raw_line.strip()
        if not line:
            continue
        upper = line.upper()

        # Sobald Artikel gefunden wurden und wir auf eine Stop-Zeile (Summe, Kartenzahlung, etc.) stoßen -> Ende der Artikel
        if len(items) > 0 and is_stop_line(upper):
            break

        # Pfand / Leergut ignorieren
        if is_deposit_line(upper):
            pending_deposit = True
            pending_name = None
            continue

        # Datum, Zeit, Steuern, Kartenzahlung übergehen
        if is_tax_line(upper) or is_payment_line(upper) or is_date_or_time_line(line, upper):
            pending_name = None
            continue

        # Rabatte & Preiskorrekturen (z.B. "Mengenrabatt -0,71" oder "Preisaenderung -0,50")
        if line.startswith("-") or any(k in upper for k in ["RABATT", "PREISVORTEIL", "AKTION", "SOFORT-RABATT", "PREISAENDERUNG", "PREISÄNDERUNG", "MENGENRABATT"]):
            discount_match = PRICE_REGEX.search(line)
            if discount_match:
                discount_val = abs(float(discount_match.group(1).replace(',', '.')))
                if items:
                    last = items[-1]
                    last["price"] = round(max(0.01, last["price"] - discount_val), 2)
            pending_name = None
            pending_qty = 1
            pending_unit_price = None
            continue

        # Barcode Zeile (z.B. "#4009268333093" bei Globus)
        ean_match = re.match(r'^#\s*(\d{8,14})', line)
        if ean_match:
            pending_barcode = ean_match.group(1)
            continue

        # Header / Noise Zeilen (Adressen, PLZ, Telefon, etc.)
        if is_header_or_noise_line(line, upper) and not ALDI_MULT_REGEX.match(line) and not LIDL_MULT_REGEX.match(line) and not MULTIPLIER_REGEX.search(line) and not WEIGHING_REGEX.search(line) and not KAUFLAND_INLINE_MULT.match(line):
            pending_name = None
            continue

        # Kaufland Inline Multiplier: "Lübzer Pils 2 * 1,09"
        kaufland_mult = KAUFLAND_INLINE_MULT.match(line)
        if kaufland_mult:
            article_part = kaufland_mult.group(1).strip()
            qty_val = int(kaufland_mult.group(2).replace(',', '.').split('.')[0])
            unit_price = float(kaufland_mult.group(3).replace(',', '.'))
            if is_deposit_line(article_part.upper()):
                pending_deposit = True
                pending_name = None
            else:
                pending_name = article_part
                pending_qty = qty_val
                pending_unit_price = unit_price
            continue

        # Wiegeartikel: "0,224 kg x 4,99 €/kg 1,12 2" oder "0,40 g x 1,79 EUR/g"
        weigh_match = WEIGHING_REGEX.search(line)
        if weigh_match:
            weight_val = float(weigh_match.group(1).replace(',', '.'))
            unit_price = float(weigh_match.group(3).replace(',', '.'))
            g4 = weigh_match.group(4)
            line_total = abs(float(g4.replace(',', '.'))) if g4 else round(weight_val * unit_price, 2)
            
            target_name = pending_name if pending_name else (items[-1]["name"] if items else None)
            if pending_name:
                add_product_to_list(items, pending_name, line_total, 1, "kg", supermarket, pending_barcode)
                pending_name = None
                pending_barcode = None
                pending_qty = 1
            elif items:
                items[-1]["price"] = line_total
                items[-1]["unit"] = "kg"
            continue

        # Aldi Format: "8 x 0,89 € ASTRA URTYP DOSE 7,12 € 2"
        aldi_match = ALDI_MULT_REGEX.match(line)
        if aldi_match:
            qty = int(aldi_match.group(1))
            inline_name = aldi_match.group(3).strip()
            price = float(aldi_match.group(4).replace(',', '.'))
            raw_target = inline_name if sum(1 for c in inline_name if c.isalpha()) >= 2 else pending_name
            if raw_target:
                add_product_to_list(items, raw_target, price, qty, "Stk.", supermarket, pending_barcode)
            pending_barcode = None
            pending_name = None
            pending_qty = 1
            continue

        # Lidl Format: "Catsan Katzenstreu 8,99 X 2 17,98 B"
        lidl_match = LIDL_MULT_REGEX.match(line)
        if lidl_match:
            raw_target = lidl_match.group(1).strip()
            qty = int(lidl_match.group(3))
            price = float(lidl_match.group(4).replace(',', '.'))
            add_product_to_list(items, raw_target, price, qty, "Stk.", supermarket, pending_barcode)
            pending_barcode = None
            pending_name = None
            pending_qty = 1
            continue

        # Multiplikator Zeile: "2 Stk x 1,09" oder "12 x 0,19 EUR" oder "2 x 0,49"
        mult_match = MULTIPLIER_REGEX.search(line)
        if mult_match:
            qty_str = mult_match.group(1).replace(',', '.')
            parsed_qty = int(qty_str.split('.')[0]) if qty_str.split('.')[0].isdigit() else 1
            unit_price = float(mult_match.group(2).replace(',', '.'))

            line_no_mult = line[mult_match.end():].strip()
            price_matches = list(PRICE_REGEX.finditer(line_no_mult))
            line_price = abs(float(price_matches[-1].group(1).replace(',', '.'))) if price_matches else 0.0

            if pending_name:
                total_to_use = line_price if line_price > 0.05 else round(parsed_qty * unit_price, 2)
                add_product_to_list(items, pending_name, total_to_use, parsed_qty, "Stk.", supermarket, pending_barcode)
                pending_barcode = None
                pending_name = None
                pending_qty = 1
            elif items:
                last = items[-1]
                expected_total = round(parsed_qty * unit_price, 2)
                if abs(last["price"] - expected_total) < 0.1 or abs(last["price"] - unit_price) < 0.1:
                    last["quantity"] = parsed_qty
                    if last["price"] < expected_total - 0.05:
                        last["price"] = expected_total
                    pending_qty = 1
                else:
                    pending_qty = parsed_qty
                    pending_unit_price = unit_price
            else:
                pending_qty = parsed_qty
                pending_unit_price = unit_price
            continue

        # Verschachtelte Mengen- und Gewichtsangaben via extrahiere_mengenangaben
        prev_target = pending_name if pending_name else (items[-1]["name"] if items else "")
        mengen_treffer = extrahiere_mengenangaben(line, prev_target)
        if mengen_treffer:
            m_qty = int(mengen_treffer["menge"]) if mengen_treffer["einheit"] != "kg" else 1
            m_unit = mengen_treffer["einheit"]
            if pending_name:
                pending_qty = m_qty
            elif items:
                items[-1]["quantity"] = m_qty
                if m_unit == "kg":
                    items[-1]["unit"] = "kg"
            else:
                pending_qty = m_qty

        # Standard Zeile: "KOCHSCHINKEN O. 2,48 B" oder "MIREE DATTEL-CUR 1,19 B"
        std_match = STANDARD_LINE_REGEX.match(line)
        if std_match and not MULTIPLIER_REGEX.search(line):
            raw_target = std_match.group(1).strip()
            price = float(std_match.group(2).replace(',', '.'))
            if sum(1 for c in raw_target if c.isalpha()) >= 2:
                total_to_use = (pending_qty * pending_unit_price) if (pending_qty > 1 and pending_unit_price and abs(price - pending_unit_price) < 0.05) else price
                add_product_to_list(items, raw_target, total_to_use, pending_qty, "Stk.", supermarket, pending_barcode)
                pending_barcode = None
                pending_name = None
                pending_qty = 1
                pending_unit_price = None
                continue

        # Freistehende Preiszeile oder Name vor Preiszeile
        price_match = PRICE_REGEX.search(line)
        if price_match:
            price_val = abs(float(price_match.group(1).replace(',', '.')))
            if pending_deposit:
                pending_deposit = False
                pending_name = None
                continue
            
            line_without_price = re.sub(r'(-?\d+[,.]\d{2})', '', line)
            line_without_price = re.sub(r'^[*#\s.-]+', '', line_without_price)
            line_without_price = re.sub(r'[AB12*#€0-9\s]+$', '', line_without_price).strip()

            if pending_name and not line_without_price and price_val > 0.05:
                total_to_use = (pending_qty * pending_unit_price) if (pending_qty > 1 and pending_unit_price and abs(price_val - pending_unit_price) < 0.05) else price_val
                add_product_to_list(items, pending_name, total_to_use, pending_qty, "Stk.", supermarket, pending_barcode)
                pending_barcode = None
                pending_name = None
                pending_qty = 1
                pending_unit_price = None
            elif len(line_without_price) >= 3 and not is_header_or_noise_line(line_without_price, line_without_price.upper()):
                total_to_use = (pending_qty * pending_unit_price) if (pending_qty > 1 and pending_unit_price and abs(price_val - pending_unit_price) < 0.05) else price_val
                add_product_to_list(items, line_without_price, total_to_use, pending_qty, "Stk.", supermarket, pending_barcode)
                pending_barcode = None
                pending_name = None
                pending_qty = 1
                pending_unit_price = None
            continue

        # Textzeile ohne Preis (Artikelname steht vor Preiszeile)
        if len(line) >= 2 and not is_header_or_noise_line(line, upper) and not is_stop_line(upper):
            pending_name = line

    for it in items:
        if not it.get("purchaseDate"):
            it["purchaseDate"] = purchase_date
        if not it.get("purchaseTime"):
            it["purchaseTime"] = purchase_time

    return items

def add_product_to_list(items: list, raw_name: str, price: float, qty: int, default_unit: str, supermarket: str, barcode: str = None):
    """Filtert und formatiert ein Produkt sauber und fügt es der Liste hinzu."""
    if price <= 0.05 or price > 250.0:
        return

    upper_raw = raw_name.upper()
    if is_unwanted_receipt_line(raw_name, upper_raw) or is_header_or_noise_line(raw_name, upper_raw) or is_stop_line(upper_raw):
        return
    if is_non_food(raw_name):
        archive_non_food_item(raw_name, price=price, quantity=qty, supermarket=supermarket, raw_line=raw_name)
        return

    clean_name = clean_product_name(raw_name)
    if len(clean_name) < 2 or sum(1 for c in clean_name if c.isalpha()) < 2:
        return
    if is_unwanted_receipt_line(clean_name, clean_name.upper()) or is_header_or_noise_line(clean_name, clean_name.upper()):
        return
    if is_non_food(clean_name):
        archive_non_food_item(clean_name, price=price, quantity=qty, supermarket=supermarket, raw_line=raw_name)
        return

    unit, storage, days = determine_smart_unit_and_storage(clean_name, price, raw_name)
    if default_unit == "kg":
        unit = "kg"

    final_qty = max(1, qty)
    final_price = round(price, 2)

    # Zusammenfassung identischer Produkte
    for existing in items:
        if existing["name"].lower() == clean_name.lower() and existing["unit"] == unit:
            existing["quantity"] += final_qty
            existing["price"] = round(existing["price"] + final_price, 2)
            return

    pet = is_pet_food(clean_name) or is_pet_food(raw_name)
    if pet:
        storage = "Vorratskammer"

    exp_date = datetime.now() + timedelta(days=days)
    item = {
        "name": clean_name,
        "rawName": raw_name,
        "quantity": final_qty,
        "unit": unit,
        "storageLocation": storage,
        "price": final_price,
        "supermarket": supermarket,
        "barcode": barcode,
        "expiryDate": int(exp_date.timestamp() * 1000),
        "expiryDateStr": exp_date.strftime("%Y-%m-%d"),
        "isPetFood": pet,
        "category": "Tiernahrung" if pet else "Lebensmittel"
    }
    items.append(item)

def two_stage_receipt_parse(text: str, supermarket: str = "") -> list:
    """
    Zweistufige Erkennung beim Kassenbon-Import:
    1. Vorab-Abgleich: Jede Zeile des Belegs mit der Lerndatenbank abgleichen (Exakt- und Ähnlichkeitssuche).
       Bekannte Artikel sofort 1:1 fehlerfrei und ohne KI-Abfrage zuordnen.
    2. KI-Erkennung für neue Artikel: Verbleibende unbekannte Zeilen an Gemini übergeben,
       wobei bereits gelernte Artikel als Referenzbeispiele im Prompt mitgesendet werden.
    """
    norm_text = sanitize_ocr_text(text)
    raw_lines = [line.strip() for line in norm_text.splitlines() if line.strip()]
    if not raw_lines:
        return []

    if not supermarket:
        supermarket = detect_supermarket(norm_text)

    known_items = []
    unresolved_lines = []
    pending_name = None
    pending_qty = 1

    for raw_line in raw_lines:
        line = raw_line.strip()
        upper = line.upper()

        if is_unwanted_receipt_line(line, upper) or is_header_or_noise_line(line, upper):
            continue
        if is_stop_line(upper):
            break

        # Multiplikator erfassen
        m_mult = MULTIPLIER_REGEX.search(line)
        if m_mult:
            qty_str = m_mult.group(1).replace(',', '.')
            try:
                pending_qty = int(qty_str.split('.')[0]) if qty_str.split('.')[0].isdigit() else 1
            except Exception:
                pending_qty = 1

        price_matches = list(PRICE_REGEX.finditer(line))
        if not price_matches:
            if len(line) >= 2 and not is_header_or_noise_line(line, upper):
                if pending_name:
                    unresolved_lines.append(pending_name)
                pending_name = line
            continue

        last_match = price_matches[-1]
        try:
            price_val = abs(float(last_match.group(1).replace(',', '.')))
        except ValueError:
            price_val = 0.0

        if price_val <= 0.05 or price_val > 500.0:
            if pending_name:
                unresolved_lines.append(pending_name)
                pending_name = None
            continue

        line_before_price = line[:last_match.start()].strip()
        line_clean = re.sub(r'^[AB12*#€-]\s+', '', line_before_price)
        line_clean = re.sub(r'[\d,.]+(?:\s*€|\s*eur)?', '', line_clean)
        line_clean = re.sub(r'[*#€AB12-]+\s*$', '', line_clean).strip()

        cand_name = line_clean if len(line_clean) >= 2 else (pending_name or "")
        learned = find_learned_product(cand_name, supermarket) if cand_name else None

        if learned:
            clean_name = learned.get("name") or clean_product_name(cand_name)
            unit = learned.get("unit") or "Stk."
            cat = learned.get("category") or "Vorrat"
            storage = learned.get("storageLocation") or "Kühlschrank"
            qty = max(1, pending_qty)

            _, _, days = determine_smart_unit_and_storage(clean_name, price_val, cand_name)
            exp_date = datetime.now() + timedelta(days=days)

            total_val = round(price_val * qty, 2) if (qty > 1 and price_val < 10.0) else price_val
            item = {
                "name": clean_name,
                "rawName": cand_name,
                "quantity": qty,
                "unit": unit,
                "storageLocation": storage,
                "price": price_val,
                "totalPrice": total_val,
                "supermarket": supermarket,
                "expiryDate": int(exp_date.timestamp() * 1000),
                "expiryDateStr": exp_date.strftime("%Y-%m-%d"),
                "category": cat,
                "isPetFood": is_pet_food(clean_name) or is_pet_food(cand_name),
                "isLearned": True
            }
            known_items.append(item)
            pending_name = None
            pending_qty = 1
        else:
            if pending_name and pending_name != cand_name:
                unresolved_lines.append(pending_name)
            unresolved_lines.append(raw_line)
            pending_name = None
            pending_qty = 1

    if pending_name:
        unresolved_lines.append(pending_name)

    new_items = []
    if unresolved_lines:
        unresolved_text = "\n".join(unresolved_lines)
        ai_res = parse_receipt_with_gemini(text=unresolved_text, supermarket=supermarket)
        if ai_res and ai_res.get("items"):
            new_items = ai_res.get("items", [])
        else:
            new_items = parse_receipt_text(unresolved_text)

    all_items = known_items + new_items
    return all_items

def extract_text_from_pdf(pdf_bytes: bytes) -> str:
    """Extrahiert Text aus PDF-Bytes mittels pypdf oder fitz."""
    extracted = ""
    if pypdf:
        try:
            reader = pypdf.PdfReader(io.BytesIO(pdf_bytes))
            for page in reader.pages:
                extracted += (page.extract_text() or "") + "\n"
        except Exception:
            pass

    if len(extracted.strip()) < 20 and fitz:
        try:
            doc = fitz.open(stream=pdf_bytes, filetype="pdf")
            for page in doc:
                extracted += page.get_text() + "\n"
        except Exception:
            pass

    return sanitize_ocr_text(extracted).strip()

def render_pdf_to_images(pdf_bytes: bytes, max_pages: int = 4, target_width: int = 1200) -> list:
    """
    Rendert Seiten eines E-Bon-PDFs mit PyMuPDF (fitz) direkt als hochauflösende JPEG-Bilder
    für die visuelle Analyse (Vision).
    """
    if not fitz:
        return []
    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
        images = []
        for i, page in enumerate(doc):
            if i >= max_pages:
                break
            rect = page.rect
            scale = 1.0
            if rect.width > 0:
                scale = target_width / rect.width
                scale = max(1.0, min(scale, 2.5))
            matrix = fitz.Matrix(scale, scale)
            pix = page.get_pixmap(matrix=matrix)
            jpg_bytes = pix.tobytes("jpeg", jpg_quality=90)
            if jpg_bytes:
                images.append(jpg_bytes)
        return images
    except Exception as e:
        print(f"Fehler beim Rendern von PDF-Seiten mit PyMuPDF: {e}")
        return []

def parse_receipt_with_gemini(
    text: str = "",
    image_bytes: bytes = None,
    images_list: list = None,
    mime_type: str = "image/jpeg",
    supermarket: str = None
) -> dict:
    """
    Nutzt die Gemini 2.5 Flash API zur hochpräzisen visuellen oder textbasierten
    Extraktion von Lebensmittel- und Haushaltsartikeln aus deutschen Kassenbons und E-Bons.
    """
    try:
        from dotenv import load_dotenv
        load_dotenv()
    except ImportError:
        pass
    api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
    if not api_key:
        return None

    model = "gemini-2.5-flash"
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"

    learned_items = load_learned_products()
    ref_examples_text = ""
    if learned_items:
        market_lower = (supermarket or "").lower() if supermarket else ""
        sorted_learned = sorted(
            learned_items,
            key=lambda x: (
                0 if market_lower and (x.get("supermarket") or "").lower() == market_lower else 1,
                -int(x.get("count", 1))
            )
        )[:20]
        ref_lines = []
        for l in sorted_learned:
            r = l.get("rawName") or l.get("name")
            n = l.get("name")
            u = l.get("unit", "Stk.")
            c = l.get("category", "Vorrat")
            s = l.get("storageLocation", "Kühlschrank")
            ref_lines.append(f"- Bon-Text: '{r}' -> Name: '{n}', Einheit: '{u}', Kategorie: '{c}', Lagerort: '{s}'")
        if ref_lines:
            ref_examples_text = (
                "\n\nBereits gelernte Artikel & Referenzbeispiele aus früheren Einkäufen:\n"
                + "\n".join(ref_lines)
                + "\nNutze diese Bezeichnungen und Zuordnungen exakt, wenn gleiche oder ähnliche Artikel auf dem Beleg vorkommen.\n"
            )

    prompt = (
        "Du bist ein spezialisierter KI-Experte für die Analyse von deutschsprachigen Kassenbons und digitalen E-Bons "
        "(u.a. Lidl Plus, Kaufland Card, Netto App, REWE eBon, Edeka App, Aldi).\n\n"
        "Deine Aufgabe:\n"
        "Extrahiere alle gekauften Artikel (insbesondere Lebensmittel, Getränke, Vorräte und Haushaltswaren/Tiernahrung) "
        "mit höchster Genauigkeit aus dem Beleg.\n\n"
        "Regeln für Layout & Spalten:\n"
        "1. Spaltenlayout exakt erfassen: Beachte Spalten wie Menge, Einheit, Einzelpreis und Gesamtpreis sowie Mehrfachkäufe "
        "(z.B. '2 x 0,49 €' oder '2 Stk * 0,39').\n"
        "2. Rabatte, Coupons & Spar-Vorteile verrechnen:\n"
        "   - Zeilen wie 'Rabatt', 'Kaufland Card Rabatt', 'Lidl Plus', 'Aktionsrabatt', 'Preisaenderung', 'Coupons', 'App-Rabatt' "
        "gehören zum unmittelbar darüberstehenden Artikel.\n"
        "   - Berechne den tatsächlichen Zahlpreis (rabattierter Gesamtpreis) bzw. passe Einzelpreis und Gesamtpreis an: "
        "Gesamtpreis = Normalpreis - Rabatt. Falls ein Einzelpreis angegeben ist, berechne: Einzelpreis = Gesamtpreis / Menge.\n"
        "3. Nicht-Artikel strikt ignorieren:\n"
        "   - Kopfzeilen (Marktname, Adresse, Filiale, Steuernummer).\n"
        "   - Pfand und Leergut ('Pfand', 'Einwegpfand', 'Leergutrückgabe').\n"
        "   - Summenzeilen ('SUMME', 'Gesamtbetrag', 'Zu zahlen', 'Bar', 'Gegeben', 'Rückgeld', 'Kartenzahlung', 'EC-Karte').\n"
        "   - Steuerblöcke (MwSt A/B, Netto, Brutto).\n"
        "   - TSE-Blöcke (Transaktionsnummer, Seriennummer, Signatur, Prüfwert, QR-Code).\n"
        "   - Treuepunkte (Payback, Lidl Plus Punkte, Ersparnis-Zusammenfassungen).\n"
        "4. Strukturierung der Artikel:\n"
        "   - name: Vollständiger, lesbarer deutscher Produktname (Abkürzungen sinnvoll auflösen, z.B. 'BO-Bio Weizenbroetchen' -> 'Bio Weizenbrötchen', 'KLC.' -> 'K-Classic', 'Kn.Fix' -> 'Knorr Fix').\n"
        "   - quantity: Menge als Zahl (z.B. 1, 2, 0.5).\n"
        "   - unit: Einheit ('Stk.', 'kg', 'g', 'l', 'Packung', 'Dose', 'Flasche', 'Becher', 'Glas', 'Bund').\n"
        "   - price: Einzelpreis in Euro (Float, 2 Dezimalstellen).\n"
        "   - totalPrice: Tatsächlich bezahlter Gesamtpreis für diese Position nach Abzug aller Rabatte (Float, 2 Dezimalstellen).\n"
        "   - category: Eine der Kategorien ('Obst & Gemüse', 'Milchprodukte', 'Fleisch & Fisch', 'Käse', 'Wurst', 'Brot & Backwaren', 'Tiefkühl', 'Getränke', 'Vorrat', 'Süßwaren & Snacks', 'Tiernahrung', 'Drogerie & Haushalt', 'Sonstiges').\n"
        "   - storageLocation: Sinnvoller Lagerort ('Kühlschrank', 'Gefrierfach', 'Vorratskammer').\n"
        "   - mhdDays: Geschätzte Haltbarkeit ab Kauf in Tagen (z.B. Hackfleisch: 2, Milch: 5, Joghurt: 14, TK: 180, Konserven/Nudeln: 365, Brot: 4, Eier: 14).\n\n"
        f"{ref_examples_text}"
        "Metadaten des Belegs:\n"
        "- supermarket: Name des Marktes (z.B. 'Netto', 'Kaufland', 'Lidl', 'REWE', 'Edeka', 'Aldi').\n"
        "- purchaseDate: Kaufdatum im Format 'DD.MM.YYYY' (oder null, falls nicht auffindbar).\n"
        "- purchaseTime: Uhrzeit des Einkaufs im Format 'HH:MM' (oder null, falls nicht auffindbar).\n"
        "- receiptTotal: Die auf dem Beleg ausgewiesene Endsumme (Float).\n\n"
        "Antworte AUSSCHLIESSLICH als valides JSON-Objekt (ohne Markdown ```json-Tags) im Format:\n"
        "{\n"
        '  "supermarket": "Netto",\n'
        '  "purchaseDate": "02.10.2026",\n'
        '  "purchaseTime": "18:20",\n'
        '  "receiptTotal": 14.17,\n'
        '  "items": [\n'
        '    {\n'
        '      "name": "Bio Weizenbrötchen",\n'
        '      "quantity": 2,\n'
        '      "unit": "Stk.",\n'
        '      "price": 0.24,\n'
        '      "totalPrice": 0.48,\n'
        '      "category": "Brot & Backwaren",\n'
        '      "storageLocation": "Vorratskammer",\n'
        '      "mhdDays": 3\n'
        '    }\n'
        '  ]\n'
        "}"
    )

    parts = [{"text": prompt}]

    if images_list:
        for img in images_list:
            if img:
                parts.append({
                    "inline_data": {
                        "mime_type": mime_type,
                        "data": base64.b64encode(img).decode("utf-8")
                    }
                })
    elif image_bytes:
        parts.append({
            "inline_data": {
                "mime_type": mime_type,
                "data": base64.b64encode(image_bytes).decode("utf-8")
            }
        })
    elif text:
        parts.append({"text": f"Kassenbon-Text:\n{text[:6000]}"})
    else:
        return None

    payload = {
        "contents": [{"parts": parts}],
        "generationConfig": {"response_mime_type": "application/json"}
    }

    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=40) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            ans = data["candidates"][0]["content"]["parts"][0]["text"].strip()
            if ans.startswith("```"):
                ans = re.sub(r'^```(?:json)?\s*', '', ans)
                ans = re.sub(r'\s*```$', '', ans)
            parsed = json.loads(ans)

            raw_items = parsed.get("items", []) if isinstance(parsed, dict) else (parsed if isinstance(parsed, list) else [])
            supermarket = parsed.get("supermarket") if isinstance(parsed, dict) else None
            purchase_date = parsed.get("purchaseDate") if isinstance(parsed, dict) else None
            purchase_time = str(parsed.get("purchaseTime") or "").strip() if isinstance(parsed, dict) else ""
            receipt_total = parsed.get("receiptTotal") if isinstance(parsed, dict) else None

            if not purchase_date:
                purchase_date = datetime.now().strftime("%d.%m.%Y")
            try:
                purchase_dt = datetime.strptime(purchase_date, "%d.%m.%Y")
            except Exception:
                purchase_dt = datetime.now()

            validated = []
            for it in raw_items:
                raw_name = str(it.get("name", "")).strip()
                name = clean_product_name(raw_name)
                if not name or len(name) < 2:
                    continue
                qty_val = float(it.get("quantity", 1))
                if qty_val <= 0:
                    qty_val = 1.0
                if qty_val.is_integer():
                    qty_val = int(qty_val)

                unit_val = str(it.get("unit", "Stk."))
                total_val = round(abs(float(it.get("totalPrice", it.get("price", 1.99)))), 2)
                price_val = round(abs(float(it.get("price", total_val / qty_val if qty_val > 0 else total_val))), 2)
                cat_val = str(it.get("category", "Vorrat"))
                storage_val = str(it.get("storageLocation", "Kühlschrank"))
                learned = find_learned_product(raw_name, supermarket) or find_learned_product(name, supermarket)
                if learned:
                    name = learned.get("name") or name
                    unit_val = learned.get("unit") or unit_val
                    cat_val = learned.get("category") or cat_val
                    storage_val = learned.get("storageLocation") or storage_val

                mhd_days = int(it.get("mhdDays", 7))
                exp_ts = int((purchase_dt + timedelta(days=mhd_days)).timestamp() * 1000)

                validated.append({
                    "name": name,
                    "rawName": raw_name,
                    "quantity": qty_val,
                    "unit": unit_val,
                    "price": price_val,
                    "totalPrice": total_val,
                    "category": cat_val,
                    "storageLocation": storage_val,
                    "mhdDays": mhd_days,
                    "supermarket": supermarket or "Import (KI)",
                    "purchaseDate": purchase_date,
                    "purchaseTime": purchase_time,
                    "expiryDate": exp_ts,
                    "isLearned": bool(learned)
                })

            if validated:
                return {
                    "supermarket": supermarket or "Supermarkt (KI)",
                    "purchaseDate": purchase_date,
                    "purchaseTime": purchase_time,
                    "receiptTotal": receipt_total or round(sum(it["totalPrice"] for it in validated), 2),
                    "items": validated
                }
    except Exception as e:
        print(f"Gemini API Fehler: {e}")

    return None

RECEIPT_HISTORY_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipt_history.json")

def load_receipt_history() -> dict:
    if os.path.exists(RECEIPT_HISTORY_FILE):
        try:
            with open(RECEIPT_HISTORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_receipt_history(history: dict):
    try:
        with open(RECEIPT_HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"Fehler beim Speichern der Beleg-Historie: {e}")

def extract_purchase_datetime(text: str) -> tuple:
    """
    Extrahiert Kaufdatum (DD.MM.YYYY) und Kaufzeit (HH:MM) aus dem Belegtext.
    Prüft Datumsangaben, Zeitstempel und TSE-Zeilen.
    """
    if not text:
        return datetime.now().strftime("%d.%m.%Y"), ""

    # 1. TSE-Block Timestamp: z.B. TSE-Start:2026-10-02T16:21:09.000Z
    tse_match = re.search(r'TSE-(?:Start|Stop)\s*:\s*(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})', text, re.IGNORECASE)
    if tse_match:
        y, m, d, hh, mm = tse_match.groups()
        return f"{d}.{m}.{y}", f"{hh}:{mm}"

    # 2. Datum mit Uhrzeit: DD.MM.YYYY HH:MM oder DD.MM.YY HH:MM
    dt_match = re.search(r'\b(\d{2})[./](\d{2})[./](20\d{2}|\d{2})\s+(\d{1,2}:\d{2})\b', text)
    if dt_match:
        d, m, y, t = dt_match.groups()
        if len(y) == 2:
            y = "20" + y
        return f"{d}.{m}.{y}", t.zfill(5)

    # 3. Nur Datum
    date_str = None
    d_match = re.search(r'\b(\d{2})[./](\d{2})[./](20\d{2}|\d{2})\b', text)
    if d_match:
        d, m, y = d_match.groups()
        if len(y) == 2:
            y = "20" + y
        date_str = f"{d}.{m}.{y}"

    # 4. Suche nach Uhrzeit
    time_str = ""
    t_match = re.search(r'\b(?:Uhrzeit|Zeit|Uhr)[\s:]*(\d{1,2}:\d{2})\b', text, re.IGNORECASE) or \
              re.search(r'\b(\d{2}:\d{2}(?::\d{2})?)\s*(?:Uhr)?\b', text)
    if t_match:
        time_str = t_match.group(1)[:5]

    return date_str or datetime.now().strftime("%d.%m.%Y"), time_str

def generate_receipt_key(supermarket: str, purchase_date: str, purchase_time: str, receipt_total: float = None, file_bytes: bytes = None) -> str:
    """
    Erzeugt einen eindeutigen Beleg-Schlüssel zur Duplikaterkennung.
    Basiert auf Markt, Kaufdatum, Uhrzeit und Gesamtsumme, mit Fallback auf Datei-Hash.
    """
    import hashlib
    market_norm = re.sub(r'[^a-z0-9]', '', (supermarket or "markt").lower())
    date_norm = (purchase_date or "").strip()
    time_norm = (purchase_time or "").strip()
    total_norm = f"{float(receipt_total):.2f}" if receipt_total is not None and float(receipt_total) > 0 else ""

    parts = [p for p in [market_norm, date_norm, time_norm, total_norm] if p]
    if len(parts) >= 2:
        return "_".join(parts)
    elif file_bytes:
        return "hash_" + hashlib.sha256(file_bytes).hexdigest()[:16]
    else:
        raw = f"{market_norm}_{date_norm}_{time_norm}_{total_norm}"
        return "key_" + hashlib.sha256(raw.encode('utf-8')).hexdigest()[:16]

def calculate_recognition_quality(items: list, receipt_total: float = None) -> float:
    """
    Berechnet die quantitative Erkennungsgüte eines Kassenbons basierend auf:
    - Anzahl erkannter Artikel
    - Vollständigkeit der Felder (Name, Menge, Preis, Lagerort, Kategorie)
    - Plausibilität gegenüber der Beleg-Gesamtsumme
    """
    if not items:
        return 0.0

    score = 0.0
    item_sum = 0.0

    for it in items:
        name = str(it.get("name", "")).strip()
        price = float(it.get("price", 0.0) or 0.0)
        total_p = float(it.get("totalPrice", price) or price)
        qty = float(it.get("quantity", 1.0) or 1.0)
        unit = str(it.get("unit", "")).strip()
        loc = str(it.get("storageLocation", "")).strip()
        cat = str(it.get("category", "")).strip()

        # 10 Basis-Punkte pro gültigem Artikel
        if len(name) >= 2:
            score += 10.0
        if len(name) >= 5:
            score += 3.0
        # Kein Barcode/Zahlensalat im Namen
        if not re.search(r'\d{6,}', name):
            score += 2.0
        # Gültiger Preis
        if price > 0.0:
            score += 5.0
        if total_p > 0.0:
            score += 2.0
        # Gültige Menge & Einheit
        if qty > 0.0:
            score += 2.0
        if unit and unit != "Stk.":
            score += 1.0
        # Plausibler Lagerort & Kategorie
        if loc in ["Kühlschrank", "Gefrierfach", "Vorratskammer"]:
            score += 2.0
        if cat and cat not in ["Sonstiges", "Vorrat", "Import"]:
            score += 2.0

        item_sum += total_p

    # Bonus für Übereinstimmung mit ausgewiesener Gesamtsumme
    if receipt_total and receipt_total > 0 and item_sum > 0:
        diff = abs(receipt_total - item_sum)
        if diff <= 0.05:
            score += 25.0
        elif diff <= 0.50:
            score += 15.0
        elif diff <= 2.00:
            score += 8.0

    return round(score, 2)

def extract_purchase_date(text: str) -> str:
    """Extrahiert das Kaufdatum aus dem Belegtext im Format DD.MM.YYYY."""
    d, _ = extract_purchase_datetime(text)
    return d

def extract_receipt_total(text: str):
    """Extrahiert die Gesamtsumme/Endsumme des Kassenbons aus dem Belegtext."""
    if not text:
        return None
    patterns = [
        r'(?:SUMME|GESAMTBETRAG|ENDSUMME|GESAMT|ZU\s+ZAHLEN|TOTAL|ZAHLBETRAG|RECHNUNGSBETRAG)[^\d\n]*?(\d{1,4}[,.]\d{2})',
        r'(\d{1,4}[,.]\d{2})\s*(?:EUR|€)?\s*[\n\r]+.*?(?:GEGEBEN|BAR|KARTENZAHLUNG|EC)',
        r'(?:BAR|KARTENZAHLUNG|EC-KARTE|VISA|MASTERCARD)[^\d\n]*?(\d{1,4}[,.]\d{2})'
    ]
    for p in patterns:
        matches = list(re.finditer(p, text, re.IGNORECASE))
        if matches:
            val_str = matches[-1].group(1).replace(',', '.')
            try:
                val = float(val_str)
                if 0.05 <= val <= 9999.0:
                    return val
            except ValueError:
                pass
    return None

def analyze_uploaded_receipt(params: dict) -> dict:
    """
    Analysiert einen Beleg (PDF Base64, Bild oder Text) und liefert strukturierte
    Metadaten (Supermarkt, Kaufdatum, Kaufzeit, Gesamtsumme, Beleg-Schlüssel,
    Erkennungsgüte-Score und Duplikat-Status) für die interaktive Import-Vorschau.
    Erkennt doppelte Belege und unterstützt bedingtes Ersetzen bei besserer Erkennungsqualität.
    """
    file_b64 = params.get("fileBase64") or params.get("file_base64")
    filename = params.get("filename") or ""
    raw_text = params.get("text") or ""

    file_bytes = None
    if file_b64:
        try:
            file_bytes = base64.b64decode(file_b64)
        except Exception:
            pass

    if not file_bytes and raw_text.startswith("%PDF"):
        file_bytes = raw_text.encode("latin-1", errors="ignore")
        filename = filename or "kassenbon.pdf"

    extracted_text = ""
    supermarket = "Unbekannt"
    purchase_date = datetime.now().strftime("%d.%m.%Y")
    purchase_time = ""
    receipt_total = None
    items = []
    filtered_non_food = []

    # 1. Fall: PDF (E-Bons)
    if file_bytes and (filename.lower().endswith(".pdf") or file_bytes.startswith(b"%PDF")):
        extracted_text = extract_text_from_pdf(file_bytes)
        if extracted_text and len(extracted_text.strip()) > 30:
            supermarket = detect_supermarket(extracted_text)
            purchase_date, purchase_time = extract_purchase_datetime(extracted_text)
            receipt_total = extract_receipt_total(extracted_text)
            items = two_stage_receipt_parse(extracted_text, supermarket=supermarket)

        # Falls lokale Extraktion keine Artikel fand oder PDF ein Scan ohne Text ist:
        if not items and fitz:
            rendered_images = render_pdf_to_images(file_bytes, max_pages=4, target_width=1200)
            if rendered_images:
                ai_res = parse_receipt_with_gemini(images_list=rendered_images, mime_type="image/jpeg", supermarket=supermarket)
                if ai_res and ai_res.get("items"):
                    supermarket = ai_res.get("supermarket", supermarket or "Supermarkt (E-Bon)")
                    purchase_date = ai_res.get("purchaseDate", datetime.now().strftime("%d.%m.%Y"))
                    purchase_time = ai_res.get("purchaseTime", "")
                    receipt_total = ai_res.get("receiptTotal")
                    items = ai_res.get("items", [])

    # 2. Fall: Bilddatei
    elif file_bytes and (any(filename.lower().endswith(ext) for ext in [".png", ".jpg", ".jpeg", ".webp"]) or file_bytes[:4] in [b"\x89PNG", b"\xff\xd8\xff\xe0", b"\xff\xd8\xff\xe1"]):
        mime = "image/jpeg" if any(filename.lower().endswith(x) for x in [".jpg", ".jpeg"]) else "image/png"
        ai_res = parse_receipt_with_gemini(image_bytes=file_bytes, mime_type=mime, supermarket=supermarket)
        if ai_res and ai_res.get("items"):
            supermarket = ai_res.get("supermarket", "Supermarkt (Foto)")
            purchase_date = ai_res.get("purchaseDate", datetime.now().strftime("%d.%m.%Y"))
            purchase_time = ai_res.get("purchaseTime", "")
            receipt_total = ai_res.get("receiptTotal")
            items = ai_res.get("items", [])

    # 3. Fall: Text
    else:
        extracted_text = sanitize_ocr_text(raw_text)
        if extracted_text:
            supermarket = detect_supermarket(extracted_text)
            purchase_date, purchase_time = extract_purchase_datetime(extracted_text)
            receipt_total = extract_receipt_total(extracted_text)
            items = two_stage_receipt_parse(extracted_text, supermarket=supermarket)

    # Falls noch keine Artikel gefunden wurden, lokales Text-Parsing als Fallback
    if not items and extracted_text:
        supermarket = detect_supermarket(extracted_text)
        purchase_date, purchase_time = extract_purchase_datetime(extracted_text)
        receipt_total = extract_receipt_total(extracted_text)
        items = parse_receipt_text(extracted_text)

    # 1. Non-Food & Garantie-Filter: Extraktion & Archivierung in 'receipt_archive.json'
    full_text_to_scan = extracted_text or raw_text or ""
    all_raw_lines = [l.strip() for l in full_text_to_scan.splitlines() if l.strip()]
    archived_non_food = []

    for l in all_raw_lines:
        if is_non_food(l):
            clean_l = re.sub(r'[\d,.]+(?:\s*€|\s*eur)?', '', l).strip()
            clean_l = re.sub(r'^[AB12*#€-]\s+', '', clean_l).strip()
            clean_l = re.sub(r'\s+[*#€AB12-]+\s*$', '', clean_l).strip()
            p_match = PRICE_REGEX.search(l)
            l_price = abs(float(p_match.group(1).replace(',', '.'))) if p_match else 0.0
            
            m_mult = MULTIPLIER_REGEX.search(l)
            l_qty = 1
            if m_mult:
                try:
                    l_qty = int(m_mult.group(1).replace(',', '.').split('.')[0])
                except Exception:
                    l_qty = 1

            if len(clean_l) >= 2 and clean_l not in filtered_non_food:
                filtered_non_food.append(clean_l)
            
            arch = archive_non_food_item(clean_l or l, price=l_price, quantity=l_qty, purchase_date=purchase_date, supermarket=supermarket, raw_line=l)
            if arch and arch not in archived_non_food:
                archived_non_food.append(arch)

    # Sicherstellen, dass keine Non-Food Artikel in der Vorratsliste verbleiben:
    clean_food_items = []
    for it in items:
        name_cand = it.get("name", "")
        raw_cand = it.get("rawName", "")
        if is_non_food(name_cand) or is_non_food(raw_cand):
            target_n = clean_product_name(name_cand) if name_cand else raw_cand
            if target_n and target_n not in filtered_non_food:
                filtered_non_food.append(target_n)
            arch = archive_non_food_item(target_n or name_cand, price=it.get("price", 0.0), quantity=it.get("quantity", 1), purchase_date=purchase_date, supermarket=supermarket, raw_line=raw_cand)
            if arch and arch not in archived_non_food:
                archived_non_food.append(arch)
        else:
            clean_food_items.append(it)
    items = clean_food_items

    # 2. Pfand-Verwaltung: Pfandzeilen (Einweg/Mehrweg/Kästen/Leergut) erkennen
    deposit_items = extract_deposit_lines(full_text_to_scan)
    deposit_total = round(sum(d.get("amount", 0.0) for d in deposit_items), 2)
    deposit_summary = {
        "total": deposit_total,
        "items": deposit_items
    }

    # Wenn Kaufzeit noch leer ist, aber extracted_text oder ein Belegtext vorhanden ist
    if not purchase_time and extracted_text:
        _, purchase_time = extract_purchase_datetime(extracted_text)

    # Kaufdatum, Kaufzeit, gelernte Eigenschaften und Preis-Radar an jedes Item anhängen
    for it in items:
        it["supermarket"] = supermarket
        it["purchaseDate"] = purchase_date
        it["purchaseTime"] = purchase_time
        if "totalPrice" not in it:
            it["totalPrice"] = round(it.get("price", 0.0) * it.get("quantity", 1), 2)

        learned = find_learned_product(it.get("rawName") or it.get("name"), supermarket)
        if learned:
            it["name"] = learned.get("name") or it["name"]
            if learned.get("unit"):
                it["unit"] = learned["unit"]
            if learned.get("category"):
                it["category"] = learned["category"]
            if learned.get("storageLocation"):
                it["storageLocation"] = learned["storageLocation"]
            it["isLearned"] = True

        # 3. Preis-Radar: Einkaufspreis mit vorherigen Einkäufen abgleichen
        radar = check_price_radar(it.get("name", ""), it.get("price", 0.0), it.get("quantity", 1), supermarket=supermarket)
        it["priceDiff"] = radar["priceDiff"]
        it["previousPrice"] = radar["previousPrice"]
        it["priceDiffVal"] = radar["priceDiffVal"]

    # Duplikaterkennung & Qualitätsbewertung
    receipt_key = generate_receipt_key(supermarket, purchase_date, purchase_time, receipt_total, file_bytes)
    quality_score = calculate_recognition_quality(items, receipt_total)

    history = load_receipt_history()
    duplicate_status = "new"
    duplicate_message = ""
    can_replace = True

    if receipt_key in history:
        prev = history[receipt_key]
        prev_score = float(prev.get("qualityScore", 0.0))
        prev_count = int(prev.get("itemCount", 0))

        # Prüfe, ob der neue Durchlauf mehr oder vollständigere Artikel liefert
        if quality_score > prev_score or len(items) > prev_count:
            duplicate_status = "duplicate_better"
            duplicate_message = (
                f"Bereits importierter Beleg erkannt, aber mit besserer Erkennungsqualität "
                f"({len(items)} Artikel / Güte {quality_score:.1f} vs. vorher {prev_count} Artikel / Güte {prev_score:.1f}). "
                f"Frühere Artikel dieses Einkaufs werden beim Speichern aktualisiert."
            )
            can_replace = True
        else:
            duplicate_status = "duplicate_rejected"
            duplicate_message = (
                f"Beleg bereits mit gleichem/besserem Ergebnis vorhanden "
                f"(Markt: {supermarket}, {purchase_date} {purchase_time}, vorher {prev_count} Artikel / Güte {prev_score:.1f}). "
                f"Import abgewiesen."
            )
            can_replace = False

    return {
        "supermarket": supermarket,
        "purchaseDate": purchase_date,
        "purchaseTime": purchase_time,
        "receiptTotal": receipt_total,
        "receiptKey": receipt_key,
        "qualityScore": quality_score,
        "duplicateStatus": duplicate_status,
        "duplicateMessage": duplicate_message,
        "canReplace": can_replace,
        "items": items,
        "filteredNonFood": filtered_non_food,
        "archivedNonFood": archived_non_food,
        "depositSummary": deposit_summary,
        "rawText": extracted_text[:500] if extracted_text else ""
    }

def process_uploaded_receipt(params: dict) -> list:
    """Universelle Schnittstelle für Beleg-Upload, liefert direkt die Artikel."""
    analysis = analyze_uploaded_receipt(params)
    if analysis.get("duplicateStatus") == "duplicate_rejected":
        return []
    return analysis.get("items", [])



