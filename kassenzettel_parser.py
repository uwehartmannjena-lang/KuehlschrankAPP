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
    # Hygiene & Drogerie
    "zahnpasta", "zahncreme", "dentagard", "blend-a-med", "colgate", "elmex", "aronal",
    "sensodyne", "oral-b", "zahnbürste", "zahnbuerste", "zahnseide", "mundspülung", "mundspuelung",
    "shampoo", "duschgel", "dusche", "deodorant", "deo", "seife", "handseife",
    "bodylotion", "hautcreme", "gesichtscreme", "haarspray", "haargel", "haarlack",
    "taschentücher", "taschentuecher", "feuchttücher", "feuchttuecher", "kosmetiktücher", "kosmetiktuecher",
    "wattepads", "wattestäbchen", "wattestaebchen", "toilettenpapier", "klopapier", "wc-papier",
    "küchenrolle", "kuechenrolle", "tampons", "binden", "slipeinlagen", "windeln",
    "rasierer", "rasierklingen", "rasierschaum", "aftershave",
    # Reinigung & Haushalt
    "weichspüler", "weichspueler", "kuschelweich", "lenor", "comfort", "waschmittel",
    "persil", "ariel", "spee", "weißer riese", "dash", "spülmittel", "spuelmittel",
    "geschirrspültabs", "geschirrspueltabs", "spültabs", "spueltabs", "somat", "finish",
    "klarspüler", "klarspueler", "spülsalz", "spuelsalz", "fit gsm", "allzweckreiniger", "reiniger",
    "meister proper", "der general", "ajax", "biff", "antikal", "cillit bang", "sagrotan",
    "wc-reiniger", "wc-frisch", "wc-stein", "glasreiniger", "fensterreiniger", "scheibenklar",
    "scheibenreiniger", "entkalker", "müllbeutel", "muellbeutel", "müllsack", "muellsack",
    "gefrierbeutel", "alufolie", "frischhaltefolie", "backpapier", "schwamm", "putzlappen", "spültuch",
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
    lower = text.lower()
    if is_pet_food(lower):
        return False
    return any(k in lower for k in NON_FOOD_KEYWORDS)

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
    return any(k in upper for k in [
        "PFAND", "LEERGUT", "MEHRWEG", "EINWEG", "DPG",
        "TRAEGER", "TRÄGER", "KASTEN", "MW-PFAND", "KASTENPFAND", "PFANDARTIKEL"
    ])

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

def is_header_or_noise_line(line: str, upper: str) -> bool:
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
    if "KAUFLAND" in upper or "K CARD" in upper or "KAUFLAND CARD" in upper:
        return "Kaufland"
    if "REWE" in upper or "NAHKAUF" in upper:
        return "Rewe"
    if any(k in upper for k in ["NETTO-ONLINE", "NETTO APP", "NETTO PLUS", "DEIN EINKAUF VOM"]):
        return "Netto"
    if re.search(r'\bNETTO\b(?!\s*(?:EUR|%|STEUER|BRUTTO))', upper):
        return "Netto"
    if "LIDL" in upper:
        return "Lidl"
    if "ALDI" in upper:
        return "Aldi"
    if "PENNY" in upper:
        return "Penny"
    if "EDEKA" in upper:
        return "Edeka"
    if "DM-DROGERIE" in upper or " DM " in upper:
        return "dm"
    if "ROSSMANN" in upper:
        return "Rossmann"
    if "NORMA" in upper:
        return "Norma"
    if "TEGUT" in upper:
        return "Tegut"
    return "Supermarkt"

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
    # 0. Gelerntes Wörterbuch (receipt_corrections.json) vorab prüfen
    corrections = load_receipt_corrections()
    raw_strip = raw_name.strip()
    if raw_strip in corrections:
        return corrections[raw_strip]
    for orig_k, corr_v in corrections.items():
        if orig_k.lower() == raw_strip.lower():
            return corr_v

    clean = sanitize_ocr_text(raw_name).strip()
    if clean in corrections:
        return corrections[clean]
    for orig_k, corr_v in corrections.items():
        if orig_k.lower() == clean.lower():
            return corr_v

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

    return items

def add_product_to_list(items: list, raw_name: str, price: float, qty: int, default_unit: str, supermarket: str, barcode: str = None):
    """Filtert und formatiert ein Produkt sauber und fügt es der Liste hinzu."""
    if price <= 0.05 or price > 250.0:
        return

    upper_raw = raw_name.upper()
    if is_unwanted_receipt_line(raw_name, upper_raw) or is_header_or_noise_line(raw_name, upper_raw) or is_stop_line(upper_raw):
        return
    if is_non_food(raw_name):
        return

    clean_name = clean_product_name(raw_name)
    if len(clean_name) < 2 or sum(1 for c in clean_name if c.isalpha()) < 2:
        return
    if is_unwanted_receipt_line(clean_name, clean_name.upper()) or is_header_or_noise_line(clean_name, clean_name.upper()):
        return
    if is_non_food(clean_name):
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

def parse_receipt_with_gemini(text: str = "", image_bytes: bytes = None, mime_type: str = "image/png") -> list:
    """Nutzt die Gemini API zur intelligenten Extraktion von Lebensmitteln."""
    api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or "AQ.Ab8RN6JMrG58SZYH26xAGyDq1-F7WnZdvc5JszAuG5d0OxEwVA"
    if not api_key:
        return None

    model = "gemini-2.5-flash"
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"

    prompt = (
        "Du bist ein intelligenter Kassenbon-Assistent für eine Kühlschrank-App. "
        "Analysiere diesen Kassenbon und extrahiere NUR echte Lebensmittel und Vorräte. "
        "Filtere Marktadresse, Kopfzeilen, Kassen-Nr., Datum, Summen, MwSt., Kartenzahlung und Pfandartikel vollständig heraus. "
        "Berechne Mengenrabatte oder Aktionen in den Einzel-/Gesamtpreis ein. "
        "Antworte AUSSCHLIESSLICH mit einem validen JSON-Array ohne Markdown-Codeblöcke im folgenden Format:\n"
        '[{"name": "...", "quantity": 1, "unit": "Stk.", "price": 1.99, "storageLocation": "Kühlschrank"}]\n'
        "Erlaubte Einheiten: 'Stk.', 'kg', 'Becher', 'Dose', 'Glas', 'Kiste', 'Packung', 'Flasche'. "
        "Erlaubte Lagerorte: 'Kühlschrank', 'Gefrierfach', 'Vorratskammer'."
    )

    parts = [{"text": prompt}]
    if image_bytes:
        parts.append({
            "inline_data": {
                "mime_type": mime_type,
                "data": base64.b64encode(image_bytes).decode("utf-8")
            }
        })
    elif text:
        parts.append({"text": f"Kassenbon-Text:\n{text[:4000]}"})
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
        with urllib.request.urlopen(req, timeout=12) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            ans = data["candidates"][0]["content"]["parts"][0]["text"].strip()
            # Falls Markdown-Codeblock vorhanden
            if ans.startswith("```"):
                ans = re.sub(r'^```(?:json)?\s*', '', ans)
                ans = re.sub(r'\s*```$', '', ans)
            raw_items = json.loads(ans)
            if isinstance(raw_items, list) and len(raw_items) > 0:
                validated = []
                for it in raw_items:
                    name = clean_product_name(str(it.get("name", "")))
                    if not name or len(name) < 2:
                        continue
                    price_val = round(abs(float(it.get("price", 1.99))), 2)
                    qty_val = max(1, int(it.get("quantity", 1)))
                    unit_val = str(it.get("unit", "Stk."))
                    storage_val = str(it.get("storageLocation", "Kühlschrank"))
                    validated.append({
                        "name": name,
                        "quantity": qty_val,
                        "unit": unit_val,
                        "price": price_val,
                        "storageLocation": storage_val,
                        "supermarket": "Import (KI)",
                        "expiryDate": int((datetime.now() + timedelta(days=7)).timestamp() * 1000)
                    })
                if validated:
                    return validated
    except Exception:
        pass

    return None

def extract_purchase_date(text: str) -> str:
    """Extrahiert das Kaufdatum aus dem Belegtext im Format DD.MM.YYYY."""
    m = re.search(r'\b(\d{2})[./](\d{2})[./](20\d{2}|\d{2})\b', text)
    if m:
        d, mth, y = m.group(1), m.group(2), m.group(3)
        if len(y) == 2:
            y = "20" + y
        return f"{d}.{mth}.{y}"
    return datetime.now().strftime("%d.%m.%Y")

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
    Metadaten (Supermarkt, Kaufdatum, Artikel-Vorschau und gefilterte Non-Food-Einträge)
    für die interaktive Import-Vorschau in der WebApp.
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
    # 1. Fall: PDF
    if file_bytes and (filename.lower().endswith(".pdf") or file_bytes.startswith(b"%PDF")):
        extracted_text = extract_text_from_pdf(file_bytes)
        if (not extracted_text or len(extracted_text.strip()) < 20) and fitz:
            try:
                doc = fitz.open(stream=file_bytes, filetype="pdf")
                if len(doc) > 0:
                    pix = doc[0].get_pixmap(dpi=150)
                    img_bytes = pix.tobytes("png")
                    ai_items = parse_receipt_with_gemini(image_bytes=img_bytes, mime_type="image/png")
                    if ai_items:
                        return {
                            "supermarket": "Supermarkt (Scan)",
                            "purchaseDate": datetime.now().strftime("%d.%m.%Y"),
                            "receiptTotal": round(sum(it.get("price", 0.0) for it in ai_items), 2),
                            "items": ai_items,
                            "filteredNonFood": [],
                            "rawText": ""
                        }
            except Exception:
                pass
    # 2. Fall: Bilddatei
    elif file_bytes and (any(filename.lower().endswith(ext) for ext in [".png", ".jpg", ".jpeg", ".webp"]) or file_bytes[:4] in [b"\x89PNG", b"\xff\xd8\xff\xe0", b"\xff\xd8\xff\xe1"]):
        mime = "image/jpeg" if any(filename.lower().endswith(x) for x in [".jpg", ".jpeg"]) else "image/png"
        ai_items = parse_receipt_with_gemini(image_bytes=file_bytes, mime_type=mime)
        if ai_items:
            return {
                "supermarket": "Supermarkt (Foto)",
                "purchaseDate": datetime.now().strftime("%d.%m.%Y"),
                "receiptTotal": round(sum(it.get("price", 0.0) for it in ai_items), 2),
                "items": ai_items,
                "filteredNonFood": [],
                "rawText": ""
            }
    # 3. Fall: Text
    else:
        extracted_text = sanitize_ocr_text(raw_text)

    if not extracted_text:
        return {
            "supermarket": "Unbekannt",
            "purchaseDate": datetime.now().strftime("%d.%m.%Y"),
            "receiptTotal": None,
            "items": [],
            "filteredNonFood": [],
            "rawText": ""
        }

    supermarket = detect_supermarket(extracted_text)
    purchase_date = extract_purchase_date(extracted_text)
    receipt_total = extract_receipt_total(extracted_text)

    # Lokales Parsing mit Zählung von Non-Food Artikeln
    all_raw_lines = [l.strip() for l in extracted_text.splitlines() if l.strip()]
    non_food_found = []
    for l in all_raw_lines:
        if is_non_food(l):
            clean_l = re.sub(r'[\d,.]', '', l).strip()
            if len(clean_l) >= 3 and clean_l not in non_food_found:
                non_food_found.append(clean_l)

    items = parse_receipt_text(extracted_text)
    for it in items:
        it["supermarket"] = supermarket
        it["purchaseDate"] = purchase_date

    return {
        "supermarket": supermarket,
        "purchaseDate": purchase_date,
        "receiptTotal": receipt_total,
        "items": items,
        "filteredNonFood": non_food_found[:5],
        "rawText": extracted_text[:500]
    }

def process_uploaded_receipt(params: dict) -> list:
    """Universelle Schnittstelle für Beleg-Upload, liefert direkt die Artikel."""
    analysis = analyze_uploaded_receipt(params)
    return analysis.get("items", [])

