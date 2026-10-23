import http.server
import socketserver
import json
import os
import sys
import uuid
import socket
from datetime import datetime, timedelta

try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

from kassenzettel_parser import (
    parse_receipt_text,
    process_uploaded_receipt,
    analyze_uploaded_receipt,
    is_non_food,
    clean_product_name,
    sanitize_ocr_text,
    add_receipt_correction,
    load_receipt_corrections,
    is_pet_food,
    load_receipt_history,
    save_receipt_history,
    generate_receipt_key,
    calculate_recognition_quality,
    load_learned_products,
    save_learned_products,
    learn_product,
    find_learned_product,
    load_receipt_archive,
    save_receipt_archive,
    archive_non_food_item,
    classify_deposit,
    extract_deposit_lines,
    load_price_history,
    save_price_history,
    check_price_radar,
    record_price_radar_purchase,
    generate_instant_recipe_ideas
)


HOST = "0.0.0.0"
PORT = 8080
DATA_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fridge_web_data.json")

def get_wlan_ip():
    """Ermittelt die lokale WLAN/LAN IP-Adresse des Rechners."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "192.168.0.223"

# Initialer Datenbestand
def get_initial_data():
    now = datetime.now()
    return {
        "pin": "1234",
        "persistent_token": str(uuid.uuid4()),
        "inventory": [
            {
                "id": str(uuid.uuid4()),
                "name": "Bio Vollmilch 3.8%",
                "quantity": 2,
                "unit": "l",
                "storageLocation": "Kühlschrank",
                "expiryDate": int((now + timedelta(days=5)).timestamp() * 1000),
                "supermarket": "REWE",
                "price": 1.49,
                "category": "Milchprodukte"
            },
            {
                "id": str(uuid.uuid4()),
                "name": "Gouda jung in Scheiben",
                "quantity": 1,
                "unit": "Packung",
                "storageLocation": "Kühlschrank",
                "expiryDate": int((now + timedelta(days=9)).timestamp() * 1000),
                "supermarket": "ALDI",
                "price": 2.29,
                "category": "Käse"
            },
            {
                "id": str(uuid.uuid4()),
                "name": "Frische Eier Freiland 10er",
                "quantity": 1,
                "unit": "Packung",
                "storageLocation": "Kühlschrank",
                "expiryDate": int((now + timedelta(days=2)).timestamp() * 1000),
                "supermarket": "LIDL",
                "price": 2.19,
                "category": "Eier"
            },
            {
                "id": str(uuid.uuid4()),
                "name": "Rinderhackfleisch 500g",
                "quantity": 1,
                "unit": "Packung",
                "storageLocation": "Kühlschrank",
                "expiryDate": int((now + timedelta(days=1)).timestamp() * 1000),
                "supermarket": "EDEKA",
                "price": 4.99,
                "category": "Fleisch"
            },
            {
                "id": str(uuid.uuid4()),
                "name": "TK Erbsen sehr fein",
                "quantity": 1,
                "unit": "Btl.",
                "storageLocation": "Gefrierfach",
                "expiryDate": int((now + timedelta(days=180)).timestamp() * 1000),
                "supermarket": "Kaufland",
                "price": 1.79,
                "category": "Tiefkühl"
            },
            {
                "id": str(uuid.uuid4()),
                "name": "Speisekartoffeln vorw. festkochend",
                "quantity": 2.5,
                "unit": "kg",
                "storageLocation": "Vorratskammer",
                "expiryDate": int((now + timedelta(days=21)).timestamp() * 1000),
                "supermarket": "Netto",
                "price": 2.49,
                "category": "Gemüse"
            }
        ],
        "shopping": [
            {
                "id": str(uuid.uuid4()),
                "name": "Mineralwasser Classic",
                "quantity": 1,
                "unit": "Kiste",
                "isChecked": False
            },
            {
                "id": str(uuid.uuid4()),
                "name": "Bananen",
                "quantity": 1,
                "unit": "Bund",
                "isChecked": True
            }
        ],
        "history": [
            {
                "id": str(uuid.uuid4()),
                "itemName": "Bio Vollmilch 3.8%",
                "quantity": 2,
                "unit": "l",
                "price": 2.98,
                "date": int((now - timedelta(days=2)).timestamp() * 1000)
            },
            {
                "id": str(uuid.uuid4()),
                "itemName": "Rinderhackfleisch 500g",
                "quantity": 1,
                "unit": "Packung",
                "price": 4.99,
                "date": int((now - timedelta(days=1)).timestamp() * 1000)
            }
        ],
        "depositAccount": {
            "balance": 0.0,
            "transactions": []
        }
    }

def load_data():
    if os.path.exists(DATA_FILE):
        try:
            with open(DATA_FILE, "r", encoding="utf-8") as f:
                d = json.load(f)
                if "depositAccount" not in d:
                    d["depositAccount"] = {"balance": 0.0, "transactions": []}
                return d
        except Exception:
            pass
    data = get_initial_data()
    save_data(data)
    return data

def save_data(data):
    try:
        with open(DATA_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"Fehler beim Speichern: {e}", file=sys.stderr)

db = load_data()

def deduplicate_inventory_items(inventory: list) -> tuple:
    """Fasst Artikel mit identischem Namen und Lagerort zusammen."""
    merged = {}
    dupes = 0
    for item in inventory:
        name_key = item.get("name", "").strip().lower()
        loc_key = item.get("storageLocation", "Kühlschrank").strip().lower()
        key = (name_key, loc_key)
        if key in merged:
            dupes += 1
            try:
                merged[key]["quantity"] = float(merged[key].get("quantity", 1)) + float(item.get("quantity", 1))
                if merged[key]["quantity"].is_integer():
                    merged[key]["quantity"] = int(merged[key]["quantity"])
            except Exception:
                pass
            if item.get("price") and not merged[key].get("price"):
                merged[key]["price"] = item["price"]
            if item.get("supermarket") and not merged[key].get("supermarket"):
                merged[key]["supermarket"] = item["supermarket"]
        else:
            merged[key] = dict(item)
    return list(merged.values()), dupes

def clean_non_food_from_inventory(inventory: list) -> tuple:
    """Entfernt versehentlich gespeicherte Drogerie-, Kosmetik- und Reinigungsartikel."""
    cleaned = []
    removed = 0
    for item in inventory:
        if is_non_food(item.get("name", "")):
            removed += 1
        else:
            cleaned.append(item)
    return cleaned, removed

HTML_CONTENT = """<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="0">
    <link rel="manifest" href="/manifest.json">
    <title>Kühlschrank Profi 🍏 PC-Begleiter</title>
    <style>
        :root { --primary: #2e7d32; --bg: #f5f5f5; --card: #ffffff; --text: #212121; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: var(--bg); color: var(--text); }
        header { background-color: var(--primary); color: white; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 4px rgba(0,0,0,0.1); flex-wrap: wrap; gap: 0.5rem; }
        h1 { margin: 0; font-size: 1.5rem; }
        nav { display: flex; gap: 0.25rem; flex-wrap: wrap; }
        nav button { background: none; border: none; color: white; padding: 0.5rem 1rem; cursor: pointer; font-size: 0.95rem; border-radius: 4px; transition: background-color 0.2s; }
        nav button.active { background-color: rgba(255,255,255,0.25); font-weight: bold; }
        nav button:hover { background-color: rgba(255,255,255,0.15); }
        main { max-width: 1000px; margin: 1.5rem auto; padding: 0 1rem; }
        .card { background: var(--card); border-radius: 8px; padding: 1.5rem; margin-bottom: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
        th, td { text-align: left; padding: 0.75rem; border-bottom: 1px solid #ddd; }
        th { background-color: #f8f9fa; }
        .btn { background-color: var(--primary); color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; font-weight: 500; }
        .btn-danger { background-color: #d32f2f; }
        .btn-sm { padding: 0.25rem 0.5rem; font-size: 0.875rem; }
        .badge { padding: 0.25rem 0.5rem; border-radius: 12px; font-size: 0.75rem; font-weight: bold; color: white; display: inline-block; }
        .badge-green { background-color: #2e7d32; }
        .badge-yellow { background-color: #f57c00; }
        .badge-red { background-color: #d32f2f; }
        .badge-gray { background-color: #757575; }
        .drop-zone { border: 2px dashed #2e7d32; border-radius: 8px; padding: 1.5rem; text-align: center; color: #2e7d32; background: #e8f5e9; cursor: pointer; transition: background 0.2s, border-color 0.2s; }
        .drop-zone.dragover { background: #c8e6c9; border-color: #1b5e20; }

        .filter-bar { display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
        .filter-bar input { flex: 1; min-width: 200px; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; }
        #auth-modal { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 1000; }
        .modal-content { background: white; padding: 2rem; border-radius: 8px; width: 320px; text-align: center; box-shadow: 0 4px 12px rgba(0,0,0,0.2); }
        .form-group { margin-bottom: 1rem; text-align: left; }
        .form-group label { display: block; font-size: 0.85rem; font-weight: bold; margin-bottom: 0.25rem; color: #555; }
        .preview-card { background: #ffffff; border: 2px solid #2e7d32; border-radius: 8px; padding: 1.25rem; margin-top: 1.5rem; box-shadow: 0 4px 12px rgba(46,125,50,0.12); }
        .preview-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 2px solid #e8f5e9; }
        .preview-meta { display: flex; gap: 0.75rem; flex-wrap: wrap; font-size: 0.95rem; }
        .preview-table { width: 100%; border-collapse: collapse; margin-bottom: 1rem; }
        .preview-table th { background-color: #e8f5e9; color: #1b5e20; font-size: 0.85rem; padding: 0.6rem 0.5rem; }
        .preview-table td { padding: 0.4rem; border-bottom: 1px solid #e0e0e0; }
        .preview-input { width: 100%; box-sizing: border-box; padding: 0.4rem; border: 1px solid #ccc; border-radius: 4px; font-family: inherit; font-size: 0.9rem; }
        .preview-actions { 
            position: sticky; 
            bottom: 0; 
            background: #ffffff; 
            z-index: 100; 
            display: flex; 
            justify-content: space-between; 
            align-items: center; 
            gap: 1rem; 
            flex-wrap: wrap; 
            padding: 0.85rem 1.25rem; 
            border-top: 2px solid #2e7d32; 
            box-shadow: 0 -4px 16px rgba(0,0,0,0.1); 
            margin-left: -1.25rem; 
            margin-right: -1.25rem; 
            margin-bottom: -1.25rem; 
            border-radius: 0 0 8px 8px; 
        }
        .preview-undo-bar {
            background-color: #323232;
            color: #ffffff;
            padding: 0.65rem 1rem;
            border-radius: 6px;
            margin-bottom: 0.75rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 3px 8px rgba(0,0,0,0.25);
        }
        .preview-undo-bar button {
            background-color: #ffb74d;
            color: #212121;
            border: none;
            padding: 0.35rem 0.85rem;
            border-radius: 4px;
            font-weight: bold;
            cursor: pointer;
            font-size: 0.85rem;
            transition: background 0.15s;
        }
        .preview-undo-bar button:hover {
            background-color: #ffa726;
        }
        .storage-badge {
            font-weight: 600 !important;
            border-radius: 6px !important;
            padding: 0.35rem 0.45rem !important;
            transition: all 0.2s ease;
        }
        .storage-badge-kuehlschrank {
            background-color: #e3f2fd !important;
            color: #0d47a1 !important;
            border: 1.5px solid #1976d2 !important;
        }
        .storage-badge-vorratskammer {
            background-color: #fff3e0 !important;
            color: #e65100 !important;
            border: 1.5px solid #f57c00 !important;
        }
        .storage-badge-gefrierfach {
            background-color: #e0f7fa !important;
            color: #006064 !important;
            border: 1.5px solid #00acc1 !important;
        }
        .preview-split-container {
            display: flex;
            gap: 1.25rem;
            margin-top: 1.5rem;
            align-items: flex-start;
        }
        .preview-doc-panel {
            flex: 1;
            min-width: 320px;
            max-width: 45%;
            background: #ffffff;
            border: 2px solid #2e7d32;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(46,125,50,0.12);
            position: sticky;
            top: 1rem;
            max-height: calc(100vh - 2rem);
            display: flex;
            flex-direction: column;
            overflow: hidden;
            transition: all 0.25s ease;
        }
        .preview-doc-panel.hidden {
            display: none;
        }
        .preview-table-panel {
            flex: 1.4;
            min-width: 0;
            transition: all 0.25s ease;
        }
        .preview-doc-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.65rem 1rem;
            background: #e8f5e9;
            border-bottom: 1.5px solid #c8e6c9;
        }
        .preview-doc-body {
            flex: 1;
            overflow: auto;
            padding: 0.6rem;
            background: #4a4d50;
            display: flex;
            justify-content: center;
            align-items: flex-start;
            min-height: 480px;
        }
        @media (max-width: 1024px) {
            .preview-split-container {
                flex-direction: column;
            }
            .preview-doc-panel {
                max-width: 100%;
                width: 100%;
                position: static;
                max-height: 480px;
            }
        }
        .non-food-box { background: #fff3e0; border-left: 4px solid #f57c00; padding: 0.75rem 1rem; border-radius: 4px; margin-bottom: 1rem; font-size: 0.85rem; color: #e65100; }
        .toast { position: fixed; bottom: 20px; right: 20px; background: #323232; color: white; padding: 12px 24px; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.3); z-index: 2000; font-size: 0.95rem; display: none; }
        .btn-secondary { background-color: #757575; color: white; }
        .btn-secondary:hover { background-color: #616161; }
        .btn-warning { background-color: #f57c00; color: white; }
        .btn-warning:hover { background-color: #ef6c00; }
        .spinner { display: inline-block; width: 18px; height: 18px; border: 3px solid rgba(46,125,50,.3); border-radius: 50%; border-top-color: #2e7d32; animation: spin 1s ease-in-out infinite; vertical-align: middle; margin-right: 8px; }
        @keyframes spin { to { transform: rotate(360deg); } }
    </style>
</head>
<body>
    <header>
        <div>
            <h1>🍏 Kühlschrank Profi PC-Begleiter</h1>
            <div style="display: flex; gap: 0.5rem; align-items: center; margin-top: 4px; flex-wrap: wrap;">
                <span id="connection-status" style="font-size: 0.8rem; background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 10px;">🟡 Verbindung wird hergestellt...</span>
                <span id="pfand-badge" style="font-size: 0.8rem; background: rgba(255,255,255,0.25); padding: 2px 8px; border-radius: 10px; font-weight: bold;" title="Aktuelles Pfandkonto">🪙 Pfandkonto: <span id="pfand-balance-display">0,00 €</span></span>
            </div>
        </div>
        <nav>
            <button id="tab-inv" class="active" onclick="showTab('inv')">📦 Vorrat</button>
            <button id="tab-receipt" onclick="showTab('receipt')">🧾 Kassenbon-Import</button>
            <button id="tab-learned" onclick="showTab('learned')">🧠 Lerndatenbank</button>
            <button id="tab-shop" onclick="showTab('shop')">🛒 Einkaufsliste</button>
            <button id="tab-recipes" onclick="showTab('recipes')">🍳 Rezepte</button>
            <button id="tab-hist" onclick="showTab('hist')">📈 Historie</button>
        </nav>
    </header>

    <div id="auth-modal">
        <div class="modal-content">
            <h2>Verbindung freigeben</h2>
            <p>Gib die 4-stellige PIN ein (Standard: <strong>1234</strong>):</p>
            <div class="form-group">
                <input type="password" id="pin-input" maxlength="4" placeholder="0000" style="text-align: center; font-size: 1.5rem; letter-spacing: 0.5rem;" value="1234">
            </div>
            <button class="btn" style="width: 100%;" onclick="authenticate()">Koppeln</button>
            <p id="pin-error" style="color: red; display: none; margin-top: 1rem;">Falsche PIN!</p>
        </div>
    </div>

    <main id="app-content" style="display: none;">
        <section id="section-inv">
            <div class="card">
                <h2>Filter & Suche</h2>
                <div class="filter-bar">
                    <input type="text" id="search-input" placeholder="🔍 Artikel suchen..." oninput="filterInventory()">
                    <button class="btn btn-sm" onclick="filterLocation('')">Alle</button>
                    <button class="btn btn-sm" onclick="filterLocation('Kühlschrank')">Kühlschrank</button>
                    <button class="btn btn-sm" onclick="filterLocation('Gefrierfach')">Gefrierfach</button>
                    <button class="btn btn-sm" onclick="filterLocation('Vorratskammer')">Vorratskammer</button>
                </div>
                <div style="display: flex; gap: 0.5rem; margin-top: 0.5rem; flex-wrap: wrap;">
                    <button class="btn btn-sm btn-secondary" onclick="deduplicateInventory()" title="Artikel mit gleichem Namen und Lagerort zusammenfassen">🧹 Duplikate zusammenfassen</button>
                    <button class="btn btn-sm btn-warning" onclick="cleanNonFoodInventory()" title="Drogerie-, Reinigungs- und Textilartikel aussortieren">🛡️ Non-Food aussortieren</button>
                    <button class="btn btn-sm btn-danger" onclick="clearAllInventory()" title="Gesamten Vorrat leeren">🗑️ Vorrat leeren</button>
                </div>
            </div>

            <div class="card">
                <h2>Artikel hinzufügen</h2>
                <div style="display: grid; grid-template-columns: 2fr 1fr 1fr 1fr auto; gap: 0.5rem; align-items: end;">
                    <div class="form-group" style="margin: 0;">
                        <label>Name</label>
                        <input type="text" id="add-name" placeholder="z. B. Milch">
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Menge</label>
                        <input type="number" id="add-qty" value="1">
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Einheit</label>
                        <input type="text" id="add-unit" value="Stk.">
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Lagerort</label>
                        <select id="add-loc">
                            <option value="Kühlschrank">Kühlschrank</option>
                            <option value="Gefrierfach">Gefrierfach</option>
                            <option value="Vorratskammer">Vorratskammer</option>
                        </select>
                    </div>
                    <button class="btn" onclick="addItem()">Hinzufügen</button>
                </div>
            </div>

            <div class="card">
                <h2>Aktueller Vorrat (MHD-Ampel)</h2>
                <div style="overflow-x: auto;">
                    <table>
                        <thead>
                            <tr>
                                <th>Status</th>
                                <th>Artikel</th>
                                <th>Menge</th>
                                <th>Lagerort</th>
                                <th>Kaufdatum</th>
                                <th>Ablaufdatum (MHD)</th>
                                <th>Aktionen</th>
                            </tr>
                        </thead>
                        <tbody id="inventory-table"></tbody>
                    </table>
                </div>
            </div>
        </section>

        <section id="section-receipt" style="display: none;">
            <div class="card">
                <h2>🧾 Kassenbon Import (PDF, Foto oder Text)</h2>
                <div id="drop-zone" class="drop-zone" 
                     ondragenter="handleDragOver(event)"
                     ondragover="handleDragOver(event)" 
                     ondragleave="handleDragLeave(event)" 
                     ondrop="handleFileDrop(event)">
                    <p style="font-size: 1.1rem; margin-top: 0; font-weight: bold;">📄 Ziehe eine Kassenbon-PDF oder ein Foto hierher</p>
                    <p style="color: #555; font-size: 0.9rem; margin-bottom: 0.8rem;">oder wähle eine Datei direkt von deinem Gerät aus:</p>
                    <input type="file" id="receipt-file-input" accept=".pdf,image/*,.txt,.json" style="display: none;" onchange="handleFileSelect(event)">
                    <button type="button" class="btn" onclick="document.getElementById('receipt-file-input').click()">📂 Datei auswählen (PDF / Bild)</button>
                    
                    <div style="margin: 1.5rem 0 0.5rem 0; text-align: left;">
                        <label style="font-size: 0.85rem; font-weight: bold; color: #555;">Oder Bon-Text direkt eingeben / hineinkopieren (oder Datei auch hier direkt ablegen):</label>
                        <textarea id="receipt-text" rows="4" 
                                  ondragenter="handleDragOver(event)"
                                  ondragover="handleDragOver(event)" 
                                  ondragleave="handleDragLeave(event)" 
                                  ondrop="handleFileDrop(event)"
                                  placeholder="Kassenbon-Text hier hineinkopieren (oder PDF/Bild direkt hier ablegen)..." 
                                  style="width: 100%; box-sizing: border-box; margin-top: 0.25rem; font-family: inherit; padding: 0.5rem; border-radius: 4px; border: 1px solid #ccc;"></textarea>
                    </div>
                    <button class="btn" style="width: 100%;" onclick="uploadReceiptManualText()">🔍 Kassenbon einlesen & analysieren</button>
                </div>
                <div id="receipt-results" style="margin-top: 1rem;"></div>
                <div id="receipt-preview-container"></div>
            </div>
        </section>

        <section id="section-learned" style="display: none;">
            <div class="card">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem;">
                    <div>
                        <h2>🧠 Selbstlernende Lebensmittel-Datenbank</h2>
                        <p style="color: #666; margin: 0; font-size: 0.95rem;">
                            Artikel werden beim Kassenbon-Import automatisch gelernt und zukünftig 1:1 ohne KI-Kosten fehlerfrei erkannt.
                        </p>
                    </div>
                    <span id="learned-count-badge" class="badge badge-green" style="font-size: 0.9rem; padding: 0.4rem 0.8rem;">0 Einträge</span>
                </div>
                <div style="margin-top: 1rem; display: flex; gap: 0.5rem; align-items: center;">
                    <input type="text" id="learned-search-input" placeholder="🔍 Bon-Rohbezeichnung, Klarname oder Markt filtern..." class="preview-input" oninput="filterLearnedTable()">
                    <button class="btn btn-secondary btn-sm" onclick="document.getElementById('learned-search-input').value=''; filterLearnedTable();">✕</button>
                </div>
            </div>

            <div class="card">
                <h3>➕ Neuen Artikel anlernen / bearbeiten</h3>
                <div style="display: grid; grid-template-columns: 2fr 2fr 1fr 1.2fr 1.2fr 1.2fr auto; gap: 0.5rem; align-items: end;">
                    <div class="form-group" style="margin: 0;">
                        <label>Bon-Rohbezeichnung</label>
                        <input type="text" id="learn-raw" placeholder="z. B. H BRUSTFILET">
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Klarname (Ziel)</label>
                        <input type="text" id="learn-name" placeholder="z. B. Hähnchenbrustfilet">
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Einheit</label>
                        <select id="learn-unit">
                            <option value="Stk.">Stk.</option>
                            <option value="Packung">Packung</option>
                            <option value="Becher">Becher</option>
                            <option value="Flasche">Flasche</option>
                            <option value="Dose">Dose</option>
                            <option value="Glas">Glas</option>
                            <option value="kg">kg</option>
                            <option value="g">g</option>
                            <option value="l">l</option>
                        </select>
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Kategorie</label>
                        <select id="learn-cat">
                            <option value="Milchprodukte">Milchprodukte</option>
                            <option value="Fleisch & Fisch">Fleisch & Fisch</option>
                            <option value="Käse">Käse</option>
                            <option value="Wurst">Wurst</option>
                            <option value="Obst & Gemüse">Obst & Gemüse</option>
                            <option value="Brot & Backwaren">Brot & Backwaren</option>
                            <option value="Tiefkühl">Tiefkühl</option>
                            <option value="Getränke">Getränke</option>
                            <option value="Vorrat">Vorrat</option>
                            <option value="Süßwaren & Snacks">Süßwaren & Snacks</option>
                            <option value="Tiernahrung">Tiernahrung</option>
                        </select>
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Lagerort</label>
                        <select id="learn-loc">
                            <option value="Kühlschrank">❄️ Kühlschrank</option>
                            <option value="Gefrierfach">🧊 Gefrierfach</option>
                            <option value="Vorratskammer">🥫 Vorratskammer</option>
                        </select>
                    </div>
                    <div class="form-group" style="margin: 0;">
                        <label>Markt</label>
                        <input type="text" id="learn-market" placeholder="z. B. Kaufland / Alle">
                    </div>
                    <button class="btn" onclick="saveLearnedProductForm()">Speichern</button>
                </div>
            </div>

            <div class="card">
                <h3>📋 Gelernte Beleg-Zuordnungen</h3>
                <div style="overflow-x: auto;">
                    <table>
                        <thead>
                            <tr>
                                <th>Bon-Rohbezeichnung</th>
                                <th>Bereinigter Klarname</th>
                                <th>Einheit</th>
                                <th>Kategorie</th>
                                <th>Lagerort</th>
                                <th>Markt</th>
                                <th>Häufigkeit</th>
                                <th>Aktionen</th>
                            </tr>
                        </thead>
                        <tbody id="learned-table"></tbody>
                    </table>
                </div>
            </div>
        </section>


        <section id="section-shop" style="display: none;">
            <div class="card">
                <h2>Einkaufsartikel hinzufügen</h2>
                <div style="display: flex; gap: 0.5rem;">
                    <input type="text" id="shop-name" placeholder="Artikelname" style="flex: 1;">
                    <button class="btn" onclick="addShopItem()">Auf Liste setzen</button>
                </div>
            </div>
            <div class="card">
                <h2>Einkaufsliste</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Status</th>
                            <th>Artikel</th>
                            <th>Menge</th>
                            <th>Aktion</th>
                        </tr>
                    </thead>
                    <tbody id="shopping-table"></tbody>
                </table>
            </div>
        </section>

        <section id="section-recipes" style="display: none;">
            <div class="card">
                <h2>🍳 Resteverwerter Rezeptideen</h2>
                <button class="btn" onclick="loadRecipes()">Automatische Rezeptideen abrufen</button>
                <div id="recipes-list" style="margin-top: 1rem;"></div>
            </div>
        </section>

        <section id="section-hist" style="display: none;">
            <div class="card">
                <h2>Preishistorie</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Datum</th>
                            <th>Artikel</th>
                            <th>Menge</th>
                            <th>Preis</th>
                        </tr>
                    </thead>
                    <tbody id="history-table"></tbody>
                </table>
            </div>
        </section>
        <div id="toast-msg" class="toast"></div>
    </main>

    <script>
        let rawInventory = [];
        let currentLocationFilter = '';
        let authToken = localStorage.getItem('companion_token') || '';
        let currentPreviewItems = [];
        let currentReceiptMetadata = { supermarket: 'Import', purchaseDate: '' };
        let previewFilterQuery = '';
        let lastDeletedPreviewItem = null;
        let previewUndoTimer = null;
        let previewSortColumn = '';
        let previewSortAsc = true;
        let currentReceiptMedia = null;
        let splitScreenVisible = true;

        function showToast(msg) {
            const toast = document.getElementById('toast-msg');
            if (toast) {
                toast.innerHTML = msg;
                toast.style.display = 'block';
                setTimeout(() => { toast.style.display = 'none'; }, 3500);
            }
        }

        const originalFetch = window.fetch;
        window.fetch = async function(url, options = {}) {
            options.cache = 'no-store';
            if (url.startsWith('/api/') && url !== '/api/auth' && url !== '/api/ping' && url !== '/api/verify-session') {
                options.headers = options.headers || {};
                options.headers['Authorization'] = 'Bearer ' + authToken;
            }
            const res = await originalFetch(url, options);
            if (res.status === 401 && url !== '/api/auth' && url !== '/api/verify-session') {
                updateStatusBanner(false, '🔴 Sitzung abgelaufen. Bitte neu anmelden.');
                localStorage.removeItem('companion_token');
                authToken = '';
                document.getElementById('auth-modal').style.display = 'flex';
                document.getElementById('app-content').style.display = 'none';
            }
            return res;
        };

        document.addEventListener('DOMContentLoaded', () => {
            // Alte Service Worker deregistrieren um Browser-Cache komplett zu leeren
            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.getRegistrations().then(registrations => {
                    for (let reg of registrations) { reg.unregister(); }
                });
            }

            setupDragAndDrop();

            const urlParams = new URLSearchParams(window.location.search);
            const tokenFromUrl = urlParams.get('token');
            if (tokenFromUrl) {
                authToken = tokenFromUrl;
                localStorage.setItem('companion_token', authToken);
                window.history.replaceState({}, document.title, window.location.pathname);
            }

            if (authToken) {
                verifySession();
            } else {
                authenticate(); // Auto-login mit Default-PIN falls vorhanden
            }
            setInterval(checkHeartbeat, 3000);
        });

        async function verifySession() {
            try {
                const res = await originalFetch('/api/verify-session', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ token: authToken })
                });
                if (res.ok) {
                    document.getElementById('auth-modal').style.display = 'none';
                    document.getElementById('app-content').style.display = 'block';
                    updateStatusBanner(true, '🟢 Verbunden (Sitzung aktiv)');
                    loadInventory();
                } else {
                    updateStatusBanner(false, '🔴 Sitzung ungültig.');
                    localStorage.removeItem('companion_token');
                    authToken = '';
                }
            } catch (e) {
                updateStatusBanner(false, '🔴 Server nicht erreichbar.');
            }
        }

        async function checkHeartbeat() {
            try {
                const res = await originalFetch('/api/ping');
                if (res.ok) {
                    if (!authToken && document.getElementById('auth-modal').style.display !== 'none') {
                        updateStatusBanner(true, '🟡 Verbunden (Warte auf PIN-Eingabe)');
                    } else if (authToken && document.getElementById('app-content').style.display === 'none') {
                        verifySession();
                    } else if (authToken) {
                        updateStatusBanner(true, '🟢 Verbunden (Sitzung aktiv)');
                    }
                } else {
                    updateStatusBanner(false, '🔴 Verbindungsverlust. Bitte Server prüfen.');
                }
            } catch (e) {
                updateStatusBanner(false, '🔴 Server offline oder Verbindungsverlust.');
            }
        }

        function updateStatusBanner(online, msg) {
            const status = document.getElementById('connection-status');
            if (status) {
                status.innerText = msg;
                status.style.background = online ? (msg.includes('Warte') ? '#f57c00' : 'rgba(255,255,255,0.3)') : '#d32f2f';
                status.style.color = 'white';
            }
        }

        async function authenticate() {
            const pin = document.getElementById('pin-input').value;
            const res = await originalFetch('/api/auth', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pin: pin })
            });
            if (res.ok) {
                const data = await res.json();
                if (data.token) {
                    authToken = data.token;
                    localStorage.setItem('companion_token', authToken);
                }
                document.getElementById('auth-modal').style.display = 'none';
                document.getElementById('app-content').style.display = 'block';
                updateStatusBanner(true, '🟢 Verbunden (Sitzung aktiv)');
                loadInventory();
            } else {
                document.getElementById('pin-error').style.display = 'block';
            }
        }

        function showTab(tab) {
            ['inv', 'receipt', 'learned', 'shop', 'recipes', 'hist'].forEach(t => {
                const el = document.getElementById('section-' + t);
                if (el) el.style.display = t === tab ? 'block' : 'none';
                const tabBtn = document.getElementById('tab-' + t);
                if (tabBtn) tabBtn.classList.toggle('active', t === tab);
            });
            if (tab === 'inv') loadInventory();
            if (tab === 'learned') loadLearnedProducts();
            if (tab === 'shop') loadShopping();
            if (tab === 'recipes') loadRecipes();
            if (tab === 'hist') loadHistory();
        }

        let rawLearnedProducts = [];

        async function loadLearnedProducts() {
            try {
                const res = await fetch('/api/learned-products');
                const data = await res.json();
                rawLearnedProducts = data.items || [];
                const badge = document.getElementById('learned-count-badge');
                if (badge) badge.innerText = `${rawLearnedProducts.length} Einträge`;
                filterLearnedTable();
            } catch (e) {
                console.error("Fehler beim Laden der Lerndatenbank:", e);
            }
        }

        function filterLearnedTable() {
            const query = (document.getElementById('learned-search-input')?.value || '').toLowerCase().trim();
            const filtered = rawLearnedProducts.filter(p => {
                const raw = (p.rawName || '').toLowerCase();
                const name = (p.name || '').toLowerCase();
                const market = (p.supermarket || '').toLowerCase();
                const cat = (p.category || '').toLowerCase();
                return !query || raw.includes(query) || name.includes(query) || market.includes(query) || cat.includes(query);
            });
            renderLearnedProductsTable(filtered);
        }

        function renderLearnedProductsTable(items) {
            const tbody = document.getElementById('learned-table');
            if (!tbody) return;
            if (items.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #757575; padding: 1.5rem;">Keine Einträge in der Lerndatenbank gefunden.</td></tr>';
                return;
            }
            tbody.innerHTML = items.map(p => `
                <tr>
                    <td><code>${escapeHtml(p.rawName || '-')}</code></td>
                    <td><strong>${escapeHtml(p.name)}</strong></td>
                    <td>${escapeHtml(p.unit || 'Stk.')}</td>
                    <td><span class="badge badge-gray">${escapeHtml(p.category || 'Vorrat')}</span></td>
                    <td>${escapeHtml(p.storageLocation || 'Kühlschrank')}</td>
                    <td>${escapeHtml(p.supermarket || 'Alle')}</td>
                    <td style="text-align: center;">${p.count || 1}×</td>
                    <td style="white-space: nowrap;">
                        <button class="btn btn-sm btn-secondary" onclick="fillLearnedForm('${escapeHtml(p.rawName || '')}', '${escapeHtml(p.name)}', '${escapeHtml(p.unit || 'Stk.')}', '${escapeHtml(p.category || 'Vorrat')}', '${escapeHtml(p.storageLocation || 'Kühlschrank')}', '${escapeHtml(p.supermarket || 'Alle')}')">✏️</button>
                        <button class="btn btn-danger btn-sm" onclick="deleteLearnedProduct('${p.id || ''}', '${escapeHtml(p.rawName || '')}')">🗑️</button>
                    </td>
                </tr>
            `).join('');
        }

        function fillLearnedForm(raw, name, unit, cat, loc, market) {
            document.getElementById('learn-raw').value = raw;
            document.getElementById('learn-name').value = name;
            document.getElementById('learn-unit').value = unit;
            document.getElementById('learn-cat').value = cat;
            document.getElementById('learn-loc').value = loc;
            document.getElementById('learn-market').value = market;
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }

        async function saveLearnedProductForm() {
            const raw = document.getElementById('learn-raw').value.trim();
            const name = document.getElementById('learn-name').value.trim();
            if (!name) {
                alert('Bitte gib mindestens einen Klarnamen ein.');
                return;
            }
            const unit = document.getElementById('learn-unit').value;
            const cat = document.getElementById('learn-cat').value;
            const loc = document.getElementById('learn-loc').value;
            const market = document.getElementById('learn-market').value.trim() || 'Alle';

            await fetch('/api/learned-products/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    rawName: raw || name,
                    name: name,
                    unit: unit,
                    category: cat,
                    storageLocation: loc,
                    supermarket: market
                })
            });

            document.getElementById('learn-raw').value = '';
            document.getElementById('learn-name').value = '';
            showToast('✅ Artikel erfolgreich in Lerndatenbank gespeichert!');
            loadLearnedProducts();
        }

        async function deleteLearnedProduct(id, rawName) {
            if (!confirm(`Diesen gelernten Eintrag wirklich löschen?`)) return;
            await fetch('/api/learned-products/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id, rawName: rawName })
            });
            showToast('🗑️ Eintrag gelöscht.');
            loadLearnedProducts();
        }

        async function loadInventory() {
            const res = await fetch('/api/inventory');
            rawInventory = await res.json();
            renderInventory(rawInventory);
            loadDepositAccount();
        }

        function filterLocation(loc) {
            currentLocationFilter = loc;
            filterInventory();
        }

        function filterInventory() {
            const query = document.getElementById('search-input').value.toLowerCase();
            const filtered = rawInventory.filter(item => {
                const matchesName = (item.name || '').toLowerCase().includes(query);
                const matchesLoc = !currentLocationFilter || item.storageLocation === currentLocationFilter;
                return matchesName && matchesLoc;
            });
            renderInventory(filtered);
        }

        function renderInventory(data) {
            const tbody = document.getElementById('inventory-table');
            const now = new Date().getTime();

            tbody.innerHTML = data.map(item => {
                let badgeClass = 'badge-gray';
                let badgeText = '⚪ Keine Angabe';
                let dateVal = '';

                if (item.expiryDate) {
                    const exp = new Date(item.expiryDate);
                    dateVal = exp.toISOString().split('T')[0];
                    const diffDays = Math.ceil((item.expiryDate - now) / (1000 * 60 * 60 * 24));
                    if (diffDays < 0) {
                        badgeClass = 'badge-red';
                        badgeText = '🔴 Abgelaufen (' + Math.abs(diffDays) + ' T.)';
                    } else if (diffDays <= 3) {
                        badgeClass = 'badge-yellow';
                        badgeText = '🟡 Bald fällig (' + diffDays + ' T.)';
                    } else {
                        badgeClass = 'badge-green';
                        badgeText = '🟢 Haltbar (' + diffDays + ' T.)';
                    }
                }

                const purchaseDisplay = item.purchaseDate ? `${escapeHtml(item.purchaseDate)}${item.purchaseTime ? ' ' + escapeHtml(item.purchaseTime) : ''}` : '-';

                return `
                    <tr>
                        <td><span class="badge ${badgeClass}">${badgeText}</span></td>
                        <td><strong>${escapeHtml(item.name)}</strong></td>
                        <td>${item.quantity} ${item.unit || 'Stk.'}</td>
                        <td>${item.storageLocation || 'Kühlschrank'}</td>
                        <td style="font-size: 0.9rem; color: #555;">${purchaseDisplay}</td>
                        <td>
                            <input type="date" value="${dateVal}" onchange="updateMhd('${item.id}', this.value)">
                        </td>
                        <td>
                            <button class="btn btn-sm" onclick="consumeItem('${item.id}')">Verbrauchen</button>
                            <button class="btn btn-danger btn-sm" onclick="deleteItem('${item.id}')">Löschen</button>
                        </td>
                    </tr>
                `;
            }).join('');
        }

        async function updateMhd(id, dateStr) {
            await fetch('/api/inventory/update-mhd', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id, expiryDate: dateStr })
            });
            loadInventory();
        }

        async function addItem() {
            const name = document.getElementById('add-name').value;
            const qty = parseFloat(document.getElementById('add-qty').value) || 1;
            const unit = document.getElementById('add-unit').value || 'Stk.';
            const loc = document.getElementById('add-loc').value;
            if (!name) return;

            await fetch('/api/inventory/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name, quantity: qty, unit: unit, storageLocation: loc })
            });
            document.getElementById('add-name').value = '';
            loadInventory();
        }

        async function consumeItem(id) {
            await fetch('/api/inventory/consume', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            });
            loadInventory();
        }

        async function deleteItem(id) {
            await fetch('/api/inventory/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            });
            loadInventory();
        }

        function preventDefaults(e) {
            if (e) {
                e.preventDefault();
                e.stopPropagation();
            }
        }

        function handleDragOver(e) {
            preventDefaults(e);
            if (e.dataTransfer) {
                e.dataTransfer.dropEffect = 'copy';
            }
            const zone = document.getElementById('drop-zone');
            if (zone) zone.classList.add('dragover');
        }

        function handleDragLeave(e) {
            preventDefaults(e);
            const zone = document.getElementById('drop-zone');
            if (zone && (!e.relatedTarget || !zone.contains(e.relatedTarget))) {
                zone.classList.remove('dragover');
            }
        }

        async function handleFileDrop(e) {
            preventDefaults(e);
            const zone = document.getElementById('drop-zone');
            if (zone) zone.classList.remove('dragover');
            
            const dt = e.dataTransfer;
            const files = dt ? dt.files : null;
            if (files && files.length > 0) {
                await processSelectedFile(files[0]);
            } else if (dt) {
                const text = dt.getData('text');
                if (text) {
                    if (text.startsWith('%PDF')) {
                        alert('Bitte PDF-Dateien als Datei ablegen, nicht als Rohtext!');
                        return;
                    }
                    document.getElementById('receipt-text').value = text;
                }
            }
        }

        function setupDragAndDrop() {
            // Verhindert zuverlässig das Standard-Verhalten von Chrome für das gesamte Fenster
            // (Chrome würde sonst die PDF im Browserfenster öffnen)
            ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
                window.addEventListener(eventName, preventDefaults, false);
                document.addEventListener(eventName, preventDefaults, false);
            });

            const dropZone = document.getElementById('drop-zone');
            const receiptText = document.getElementById('receipt-text');

            // Drag & Drop sowohl auf der grünen Box als auch auf der Textarea identisch aktivieren
            [dropZone, receiptText].forEach(el => {
                if (!el) return;
                el.addEventListener('dragenter', handleDragOver, false);
                el.addEventListener('dragover', handleDragOver, false);
                el.addEventListener('dragleave', handleDragLeave, false);
                el.addEventListener('drop', handleFileDrop, false);
            });
        }

        async function handleFileSelect(e) {
            const files = e.target.files;
            if (files && files.length > 0) {
                await processSelectedFile(files[0]);
            }
        }

        function escapeHtml(str) {
            if (!str) return '';
            return String(str).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]);
        }

        async function processSelectedFile(file) {
            if (!file) return;
            const textarea = document.getElementById('receipt-text');
            const div = document.getElementById('receipt-results');
            document.getElementById('receipt-preview-container').innerHTML = '';
            textarea.value = '📄 Kassenbon-Datei geladen: ' + file.name + ' (' + Math.round(file.size / 1024) + ' KB)';
            div.innerHTML = '<p style="color: #2e7d32; font-weight: bold;"><span class="spinner"></span> Datei <strong>' + escapeHtml(file.name) + '</strong> (' + Math.round(file.size / 1024) + ' KB) wird analysiert...</p>';

            const isTextOrJson = file.name.endsWith('.txt') || file.name.endsWith('.json') || (file.type && file.type.startsWith('text/'));
            
            if (isTextOrJson) {
                const text = await file.text();
                currentReceiptMedia = { type: 'text/plain', text: text, name: file.name };
                textarea.value = text;
                await analyzeReceiptPayload({ text: text, filename: file.name });
            } else {
                // PDF oder Bild: Als Base64 übertragen
                const reader = new FileReader();
                reader.onload = async function() {
                    const base64Data = reader.result.split(',')[1];
                    currentReceiptMedia = {
                        type: file.type || (file.name.toLowerCase().endsWith('.pdf') ? 'application/pdf' : 'image/jpeg'),
                        dataUrl: reader.result,
                        name: file.name
                    };
                    await analyzeReceiptPayload({
                        filename: file.name,
                        fileType: file.type || 'application/pdf',
                        fileBase64: base64Data
                    });
                };
                reader.onerror = function() {
                    div.innerHTML = '<p style="color: red; font-weight: bold;">❌ Fehler beim Lesen der Datei.</p>';
                };
                reader.readAsDataURL(file);
            }
        }

        async function uploadReceiptManualText() {
            const text = document.getElementById('receipt-text').value;
            if (!text || text.startsWith('📄 Kassenbon-Datei')) {
                alert('Bitte füge zuerst Kassenbon-Text ein oder wähle eine Datei aus.');
                return;
            }
            currentReceiptMedia = { type: 'text/plain', text: text, name: 'Kassenbon-Text' };
            const div = document.getElementById('receipt-results');
            document.getElementById('receipt-preview-container').innerHTML = '';
            div.innerHTML = '<p style="color: #2e7d32; font-weight: bold;"><span class="spinner"></span> Kassenbon-Text wird analysiert...</p>';
            await analyzeReceiptPayload({ text: text });
        }

        async function analyzeReceiptPayload(payload) {
            const div = document.getElementById('receipt-results');
            const previewContainer = document.getElementById('receipt-preview-container');
            try {
                const res = await fetch('/api/parse-receipt', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();
                div.innerHTML = '';
                
                if (!data || !data.items || data.items.length === 0) {
                    div.innerHTML = '<p style="color: #f57c00; font-weight: bold;">⚠️ Keine Lebensmittel im Kassenbon gefunden. Bitte überprüfe den Beleg oder die Lesequalität.</p>';
                    previewContainer.innerHTML = '';
                    return;
                }
                
                renderReceiptPreview(data);
            } catch (err) {
                div.innerHTML = '<p style="color: red; font-weight: bold;">❌ Fehler bei der Beleg-Analyse: ' + escapeHtml(err.message) + '</p>';
                previewContainer.innerHTML = '';
            }
        }

        function getCategoryIcon(name) {
            const n = (name || '').toLowerCase();
            if (/pizza|tiefkühl|tk\b|eiscreme|rustipani|pommes/i.test(n)) return '🧊';
            if (/perle|gourmet|felix|whiskas|sheba|chappi|pedigree|katzen|hunde|tiernahr|tierfutter/i.test(n)) return '🐱';
            if (/fleisch|wurst|schinken|salami|hack|hähnchen|haehnchen|geflügel|steak|schnitzel|wiener|bratwurst|speck|leberwurst|mortadella/i.test(n)) return '🥩';
            if (/lachs|forelle|thunfisch|fisch|garnelen|sardinen/i.test(n)) return '🐟';
            if (/milch|käse|kaese|gouda|butter|joghurt|quark|sahne|schmand|mozzarella|camembert|frischkäse|pudding|creme fraiche|crème fraîche|mascarpone|ricotta/i.test(n)) return '🧀';
            if (/cookies|keks|chips|erdnüss|erdnuss|cashew|walnuss|nüss|schokolade|riegel|churro|waffel|spekulatius|cantuccini/i.test(n)) return '🍪';
            if (/brot|brötchen|broetchen|toast|baguette|rusti|krüstchen|semmel/i.test(n)) return '🍞';
            if (/banane|apfel|äpfel|kartoffel|möhre|zwiebel|salat|avocado|zitrone|paprika|tomate/i.test(n)) return '🥦';
            if (/reis|nudel|spaghetti|expressreis|kloßteig/i.test(n)) return '🍝';
            if (/kaffee|espresso|tee\b|cappuccino/i.test(n)) return '☕';
            if (/wasser|bier|wein|cola|limo|saft|waldquell/i.test(n)) return '🍷';
            if (/dose|glas|konserve|eintopf|bohnen|erbsen|mais|sauerkraut|rotkohl|gurken|senf|ketchup|pesto|marmelade|honig|nutella|fix\b|maggi|knorr/i.test(n)) return '🥫';
            return '🍏';
        }

        function getStorageBadgeClass(location) {
            const loc = (location || '').toLowerCase();
            if (loc.includes('gefrier') || loc.includes('tiefkühl') || loc.includes('tk')) {
                return 'storage-badge-gefrierfach';
            }
            if (loc.includes('vorrat')) {
                return 'storage-badge-vorratskammer';
            }
            return 'storage-badge-kuehlschrank';
        }

        function updatePreviewStorage(idx, selectEl) {
            const val = selectEl.value;
            updatePreviewField(idx, 'storageLocation', val);
            selectEl.className = 'preview-input storage-badge ' + getStorageBadgeClass(val);
        }

        function batchSetStorage(targetLocation) {
            let count = 0;
            currentPreviewItems.forEach(it => {
                if (it.selected) {
                    it.storageLocation = targetLocation;
                    count++;
                }
            });
            if (count === 0) {
                showToast('⚠️ Bitte zuerst mindestens einen Artikel auswählen.');
                return;
            }
            const tbody = document.getElementById('preview-tbody');
            if (tbody) tbody.innerHTML = renderPreviewRows();
            showToast(`📦 ${count} Artikel nach „${targetLocation}“ verschoben.`);
        }

        function checkInventoryDuplicate(itemName) {
            if (!itemName || !rawInventory || rawInventory.length === 0) return null;
            const clean = itemName.toLowerCase().replace(/[^a-z0-9äöüß]/gi, '');
            if (clean.length < 3) return null;
            return rawInventory.find(inv => {
                const invClean = (inv.name || '').toLowerCase().replace(/[^a-z0-9äöüß]/gi, '');
                return invClean === clean || (invClean.length >= 4 && (invClean.includes(clean) || clean.includes(invClean)));
            }) || null;
        }

        function getSortIndicator(col) {
            if (previewSortColumn !== col) return '⇅';
            return previewSortAsc ? '▲' : '▼';
        }

        function sortPreviewTable(col) {
            if (previewSortColumn === col) {
                previewSortAsc = !previewSortAsc;
            } else {
                previewSortColumn = col;
                previewSortAsc = true;
            }
            currentPreviewItems.sort((a, b) => {
                let vA = a[col];
                let vB = b[col];
                if (col === 'price') {
                    vA = parseFloat(vA) || 0;
                    vB = parseFloat(vB) || 0;
                    return previewSortAsc ? vA - vB : vB - vA;
                }
                vA = (vA || '').toString().toLowerCase();
                vB = (vB || '').toString().toLowerCase();
                const cmp = vA.localeCompare(vB, 'de');
                return previewSortAsc ? cmp : -cmp;
            });
            currentPreviewItems.forEach((it, idx) => it.id = idx);
            const nameInd = document.getElementById('sort-indicator-name');
            if (nameInd) nameInd.innerText = getSortIndicator('name');
            const priceInd = document.getElementById('sort-indicator-price');
            if (priceInd) priceInd.innerText = getSortIndicator('price');
            const storageInd = document.getElementById('sort-indicator-storageLocation');
            if (storageInd) storageInd.innerText = getSortIndicator('storageLocation');
            const tbody = document.getElementById('preview-tbody');
            if (tbody) tbody.innerHTML = renderPreviewRows();
        }

        function toggleSplitScreen() {
            splitScreenVisible = !splitScreenVisible;
            const panel = document.getElementById('preview-doc-panel');
            const btn = document.getElementById('toggle-splitscreen-btn');
            if (panel) {
                panel.classList.toggle('hidden', !splitScreenVisible);
            }
            if (btn) {
                btn.innerHTML = `📄 ${splitScreenVisible ? 'Beleg verbergen' : 'Beleg anzeigen (Split-Screen)'}`;
            }
        }

        function renderReceiptPreview(data) {
            previewFilterQuery = '';
            lastDeletedPreviewItem = null;
            previewSortColumn = '';
            previewSortAsc = true;
            if (previewUndoTimer) {
                clearTimeout(previewUndoTimer);
                previewUndoTimer = null;
            }

            if (rawInventory.length === 0) {
                loadInventory();
            }

            const allowedUnits = ['Stk.', 'Packung', 'Becher', 'Flasche', 'Dose', 'g', 'kg', 'l'];
            currentPreviewItems = (data.items || []).map((it, idx) => {
                let unit = it.unit || 'Stk.';
                if (['packun', 'pack', 'pck'].includes(unit.toLowerCase())) unit = 'Packung';
                if (!allowedUnits.includes(unit)) {
                    unit = allowedUnits.find(u => u.toLowerCase() === unit.toLowerCase()) || 'Stk.';
                }
                const dup = checkInventoryDuplicate(it.name);
                const isPet = !!it.isPetFood || /perle|gourmet|felix|whiskas|sheba|chappi|pedigree|katzen|hunde|tiernahr|tierfutter|dreamies|kitekat|purina/i.test(it.name || '');
                return {
                    id: idx,
                    name: it.name || '',
                    originalName: (it.rawName || it.name || '').trim(),
                    rawName: (it.rawName || it.name || '').trim(),
                    quantity: typeof it.quantity === 'number' ? it.quantity : 1,
                    unit: unit,
                    price: typeof it.price === 'number' ? it.price : 0.0,
                    storageLocation: isPet ? 'Vorratskammer' : (it.storageLocation || 'Kühlschrank'),
                    supermarket: data.supermarket || it.supermarket || 'Import',
                    expiryDate: it.expiryDate,
                    expiryDateStr: it.expiryDateStr || (it.expiryDate ? new Date(it.expiryDate).toISOString().split('T')[0] : new Date().toISOString().split('T')[0]),
                    purchaseDate: it.purchaseDate || data.purchaseDate || '',
                    purchaseTime: it.purchaseTime || data.purchaseTime || '',
                    selected: true,
                    isPetFood: isPet,
                    duplicateOf: dup ? { id: dup.id, name: dup.name, quantity: dup.quantity, unit: dup.unit } : null,
                    restockExisting: dup ? true : false,
                    category: it.category || 'Vorrat',
                    isLearned: !!it.isLearned
                };
            });
            currentReceiptMetadata = {
                supermarket: data.supermarket || 'Import',
                purchaseDate: data.purchaseDate || '',
                purchaseTime: data.purchaseTime || '',
                receiptTotal: typeof data.receiptTotal === 'number' ? data.receiptTotal : null,
                receiptKey: data.receiptKey || '',
                qualityScore: typeof data.qualityScore === 'number' ? data.qualityScore : 0,
                duplicateStatus: data.duplicateStatus || 'new',
                duplicateMessage: data.duplicateMessage || '',
                canReplace: data.canReplace !== false
            };

            const container = document.getElementById('receipt-preview-container');
            let nonFoodHtml = '';
            if (data.filteredNonFood && data.filteredNonFood.length > 0) {
                nonFoodHtml = `
                    <div class="non-food-box">
                        <strong>🛡️ Automatisch aussortierte Drogerie- & Non-Food Artikel (${data.filteredNonFood.length}):</strong><br>
                        ${escapeHtml(data.filteredNonFood.join(', '))}
                    </div>
                `;
            }

            let duplicateAlertHtml = '';
            if (data.duplicateStatus === 'duplicate_rejected') {
                duplicateAlertHtml = `
                    <div style="background: #ffebee; border: 2px solid #d32f2f; color: #b71c1c; border-radius: 6px; padding: 0.85rem 1.15rem; margin-bottom: 1rem; display: flex; align-items: center; gap: 0.85rem;">
                        <span style="font-size: 1.6rem;">⚠️</span>
                        <div>
                            <strong>Beleg bereits vorhanden (Import abgewiesen):</strong><br>
                            <span>${escapeHtml(data.duplicateMessage || 'Dieser Beleg wurde bereits mit gleichem oder besserem Ergebnis importiert.')}</span>
                        </div>
                    </div>
                `;
            } else if (data.duplicateStatus === 'duplicate_better') {
                duplicateAlertHtml = `
                    <div style="background: #e3f2fd; border: 2px solid #1976d2; color: #0d47a1; border-radius: 6px; padding: 0.85rem 1.15rem; margin-bottom: 1rem; display: flex; align-items: center; gap: 0.85rem;">
                        <span style="font-size: 1.6rem;">🔄</span>
                        <div>
                            <strong>Bessere Erkennungsqualität gefunden:</strong><br>
                            <span>${escapeHtml(data.duplicateMessage || 'Neue Erkennung liefert vollständigere Artikel. Bisherige Einträge werden aktualisiert.')}</span>
                        </div>
                    </div>
                `;
            }

            let docViewerHtml = '';
            if (currentReceiptMedia) {
                if (currentReceiptMedia.dataUrl && (currentReceiptMedia.type === 'application/pdf' || (currentReceiptMedia.name && currentReceiptMedia.name.toLowerCase().endsWith('.pdf')))) {
                    docViewerHtml = `<iframe src="${currentReceiptMedia.dataUrl}#toolbar=0&navpanes=0" style="width: 100%; height: 100%; min-height: 600px; border: none; border-radius: 4px; background: #ffffff;"></iframe>`;
                } else if (currentReceiptMedia.dataUrl && (currentReceiptMedia.type.startsWith('image/') || (currentReceiptMedia.name && /[.](jpe?g|png|webp|bmp|gif)$/i.test(currentReceiptMedia.name)))) {
                    docViewerHtml = `<img src="${currentReceiptMedia.dataUrl}" alt="Kassenbon Vorschau" style="max-width: 100%; height: auto; object-fit: contain; border-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.35);">`;
                } else if (currentReceiptMedia.text) {
                    docViewerHtml = `<pre style="white-space: pre-wrap; font-family: 'Consolas', 'Courier New', monospace; font-size: 0.85rem; color: #eceff1; background: #263238; padding: 1rem; border-radius: 4px; width: 100%; box-sizing: border-box; margin: 0; max-height: 600px; overflow-y: auto;">${escapeHtml(currentReceiptMedia.text)}</pre>`;
                }
            }
            if (!docViewerHtml) {
                docViewerHtml = `<div style="color: #bbb; padding: 2rem; text-align: center; font-size: 0.9rem;">Kein Originalbeleg zur Vorschau vorhanden</div>`;
            }

            container.innerHTML = `
                <div class="preview-split-container">
                    ${currentReceiptMedia ? `
                        <div class="preview-doc-panel ${splitScreenVisible ? '' : 'hidden'}" id="preview-doc-panel">
                            <div class="preview-doc-header">
                                <span style="font-weight: bold; font-size: 0.88rem; color: #1b5e20; display: flex; align-items: center; gap: 0.4rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                    📄 Original-Beleg: <em>${escapeHtml(currentReceiptMedia ? currentReceiptMedia.name : 'Beleg')}</em>
                                </span>
                                <button type="button" class="btn btn-sm btn-secondary" onclick="toggleSplitScreen()" title="Beleg-Vorschau ausblenden" style="padding: 2px 8px; font-size: 0.8rem; cursor: pointer;">✕ Ausblenden</button>
                            </div>
                            <div class="preview-doc-body" id="preview-doc-body">
                                ${docViewerHtml}
                            </div>
                        </div>
                    ` : ''}

                    <div class="preview-table-panel">
                        <div class="preview-card" style="margin-top: 0;">
                            <div class="preview-header">
                                <div>
                                    <h3 style="margin: 0; color: #2e7d32;">📋 Kassenbon-Vorschau prüfen</h3>
                                    <div class="preview-meta" style="margin-top: 0.35rem; display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;">
                                        <span>🏪 <strong>${escapeHtml(currentReceiptMetadata.supermarket)}</strong></span>
                                        ${currentReceiptMetadata.purchaseDate ? `<span>📅 Kaufdatum: <strong>${escapeHtml(currentReceiptMetadata.purchaseDate)}${currentReceiptMetadata.purchaseTime ? ' ' + escapeHtml(currentReceiptMetadata.purchaseTime) : ''}</strong></span>` : ''}
                                        ${currentReceiptMedia ? `
                                            <button type="button" id="toggle-splitscreen-btn" class="btn btn-sm btn-secondary" onclick="toggleSplitScreen()" style="padding: 0.25rem 0.6rem; font-size: 0.8rem; cursor: pointer;">
                                                📄 ${splitScreenVisible ? 'Beleg verbergen' : 'Beleg anzeigen (Split-Screen)'}
                                            </button>
                                        ` : ''}
                                    </div>
                                </div>
                                <div style="text-align: right;">
                                    <span id="preview-badge-count" class="badge badge-green" style="font-size: 0.85rem; padding: 0.35rem 0.75rem;">
                                        ${currentPreviewItems.length} Artikel
                                    </span>
                                    <div style="margin-top: 0.35rem; font-size: 1.05rem; font-weight: bold; color: #1b5e20;">
                                        Gesamtsumme: <span id="preview-total-sum">0,00 €</span>
                                    </div>
                                </div>
                            </div>

                            ${duplicateAlertHtml}

                            ${currentReceiptMetadata.receiptTotal != null ? `
                                <div id="receipt-total-comparison" style="margin-bottom: 0.85rem; font-size: 0.95rem; background: #f1f8e9; border: 1.5px solid #a5d6a7; border-radius: 6px; padding: 0.6rem 0.85rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem;">
                                    <!-- Live Summenabgleich -->
                                </div>
                            ` : ''}

                            ${nonFoodHtml}

                            ${currentReceiptMetadata.depositSummary && currentReceiptMetadata.depositSummary.total ? `
                                <div style="margin-bottom: 0.85rem; padding: 0.6rem 0.85rem; background: #e3f2fd; border: 1.5px solid #90caf9; border-radius: 6px; font-size: 0.88rem; color: #0d47a1; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem;">
                                    <span>🪙 <strong>Pfand-Verwaltung:</strong> ${(currentReceiptMetadata.depositSummary.total).toFixed(2).replace('.', ',')} € (${(currentReceiptMetadata.depositSummary.items || []).map(d => `${d.type}: ${d.amount > 0 ? '+' : ''}${d.amount.toFixed(2).replace('.', ',')} €`).join(', ')})</span>
                                    <span style="font-size: 0.78rem; background: #bbdefb; padding: 3px 8px; border-radius: 4px; font-weight: bold;">Wird auf Pfandkonto saldiert (nicht im Vorrat)</span>
                                </div>
                            ` : ''}

                            <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.75rem;">
                                <input type="text" id="preview-search-input" placeholder="🔍 Artikel im Kassenbon suchen..." class="preview-input" value="${escapeHtml(previewFilterQuery)}" oninput="filterPreviewRows(this.value)">
                                <button type="button" class="btn btn-secondary btn-sm" onclick="clearPreviewSearch()" title="Suche zurücksetzen" style="padding: 0.4rem 0.75rem; white-space: nowrap;">✕ Zurücksetzen</button>
                            </div>

                            <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem; flex-wrap: wrap; background: #f5f5f5; padding: 0.5rem 0.75rem; border-radius: 6px; border: 1px solid #e0e0e0;">
                                <span style="font-size: 0.85rem; font-weight: bold; color: #424242;">📦 Markierte verschieben nach:</span>
                                <button type="button" class="btn btn-sm" onclick="batchSetStorage('Kühlschrank')" style="background-color: #1976d2; color: white; padding: 0.3rem 0.65rem; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;">❄️ Kühlschrank</button>
                                <button type="button" class="btn btn-sm" onclick="batchSetStorage('Vorratskammer')" style="background-color: #f57c00; color: white; padding: 0.3rem 0.65rem; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;">🥫 Vorratskammer</button>
                                <button type="button" class="btn btn-sm" onclick="batchSetStorage('Gefrierfach')" style="background-color: #00838f; color: white; padding: 0.3rem 0.65rem; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;">🧊 Tiefkühler</button>
                            </div>

                            <div id="preview-undo-bar" class="preview-undo-bar" style="display: none;"></div>

                            <div style="overflow-x: auto;">
                                <table class="preview-table">
                                    <thead>
                                        <tr>
                                            <th style="width: 30px; text-align: center;"><input type="checkbox" id="preview-select-all" checked onchange="toggleAllPreview(this.checked)"></th>
                                            <th style="cursor: pointer; user-select: none;" onclick="sortPreviewTable('name')" title="Klicken zum Sortieren nach Artikelname">
                                                Artikel <span id="sort-indicator-name" style="font-size: 0.8rem; margin-left: 2px;">${getSortIndicator('name')}</span>
                                            </th>
                                            <th style="width: 110px; text-align: center;">Menge</th>
                                            <th style="width: 105px;">Einheit</th>
                                            <th style="width: 105px; cursor: pointer; user-select: none;" onclick="sortPreviewTable('price')" title="Klicken zum Sortieren nach Preis">
                                                Preis (€) <span id="sort-indicator-price" style="font-size: 0.8rem; margin-left: 2px;">${getSortIndicator('price')}</span>
                                            </th>
                                            <th style="width: 145px; cursor: pointer; user-select: none;" onclick="sortPreviewTable('storageLocation')" title="Klicken zum Sortieren nach Lagerort">
                                                Lagerort <span id="sort-indicator-storageLocation" style="font-size: 0.8rem; margin-left: 2px;">${getSortIndicator('storageLocation')}</span>
                                            </th>
                                            <th style="width: 135px;">MHD</th>
                                            <th style="width: 120px;">Kaufdatum</th>
                                            <th style="width: 35px; text-align: center;">✕</th>
                                        </tr>
                                    </thead>
                                    <tbody id="preview-tbody">
                                        ${renderPreviewRows()}
                                    </tbody>
                                </table>
                            </div>

                            <div style="margin-top: 0.5rem; margin-bottom: 0.75rem;">
                                <button type="button" class="btn btn-sm" onclick="addPreviewRow()" style="background-color: #2e7d32; color: white; display: inline-flex; align-items: center; gap: 0.35rem; font-weight: bold; padding: 0.45rem 0.9rem; cursor: pointer;">
                                    ➕ Artikel hinzufügen
                                </button>
                            </div>

                            <div class="preview-actions">
                                <label style="font-size: 0.9rem; cursor: pointer; user-select: none;">
                                    <input type="checkbox" checked onchange="toggleAllPreview(this.checked)"> Alle Artikel auswählen
                                </label>
                                <div style="display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;">
                                    <span style="font-weight: bold; font-size: 1.1rem; color: #2e7d32;">
                                        Summe: <span id="preview-bottom-total">0,00 €</span>
                                    </span>
                                    <button class="btn btn-secondary" onclick="cancelPreview()">Abbrechen</button>
                                    ${currentReceiptMetadata.duplicateStatus === 'duplicate_rejected' ? `
                                        <button class="btn btn-secondary" id="preview-import-btn" disabled style="opacity: 0.55; cursor: not-allowed; background-color: #757575;" title="Beleg ist bereits vorhanden">⚠️ Bereits vorhanden (Abgewiesen)</button>
                                    ` : currentReceiptMetadata.duplicateStatus === 'duplicate_better' ? `
                                        <button class="btn" id="preview-import-btn" onclick="commitPreviewImport()" style="background-color: #1976d2;">🔄 Alten Einkauf aktualisieren & ersetzen</button>
                                    ` : `
                                        <button class="btn" id="preview-import-btn" onclick="commitPreviewImport()">✅ In den Vorrat übernehmen</button>
                                    `}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            updateTotals();
        }

        function filterPreviewRows(query) {
            previewFilterQuery = (query || '').toLowerCase().trim();
            const rows = document.querySelectorAll('#preview-tbody tr');
            rows.forEach(tr => {
                const idxStr = tr.id.replace('preview-row-', '');
                const idx = parseInt(idxStr, 10);
                const item = currentPreviewItems[idx];
                if (item) {
                    const matches = !previewFilterQuery || (item.name || '').toLowerCase().includes(previewFilterQuery);
                    tr.style.display = matches ? '' : 'none';
                }
            });
        }

        function clearPreviewSearch() {
            previewFilterQuery = '';
            const input = document.getElementById('preview-search-input');
            if (input) input.value = '';
            filterPreviewRows('');
        }

        function addPreviewRow() {
            const newId = currentPreviewItems.length > 0 ? Math.max(...currentPreviewItems.map(it => it.id || 0)) + 1 : 0;
            const defaultExpiry = new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0];
            const newItem = {
                id: newId,
                name: '',
                quantity: 1,
                unit: 'Stk.',
                price: 0.0,
                storageLocation: 'Kühlschrank',
                supermarket: currentReceiptMetadata.supermarket || 'Import',
                expiryDateStr: defaultExpiry,
                purchaseDate: currentReceiptMetadata.purchaseDate || new Date().toLocaleDateString('de-DE'),
                selected: true,
                duplicateOf: null,
                restockExisting: false
            };
            currentPreviewItems.push(newItem);
            const tbody = document.getElementById('preview-tbody');
            if (tbody) tbody.innerHTML = renderPreviewRows();
            updateTotals();

            setTimeout(() => {
                const newIdx = currentPreviewItems.length - 1;
                const newRow = document.getElementById(`preview-row-${newIdx}`);
                if (newRow) {
                    const nameInput = newRow.querySelector('input[type="text"]');
                    if (nameInput) {
                        nameInput.focus();
                        newRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    }
                }
            }, 50);
        }

        function updatePreviewName(idx, val) {
            updatePreviewField(idx, 'name', val);
            updatePreviewRowIcon(idx);
            const isPet = /perle|gourmet|felix|whiskas|sheba|chappi|pedigree|katzen|hunde|tiernahr|tierfutter|dreamies|kitekat|purina/i.test(val || '');
            if (currentPreviewItems[idx]) {
                currentPreviewItems[idx].isPetFood = isPet;
                if (isPet && currentPreviewItems[idx].storageLocation === 'Kühlschrank') {
                    currentPreviewItems[idx].storageLocation = 'Vorratskammer';
                    const storageEl = document.getElementById(`preview-storage-${idx}`);
                    if (storageEl) {
                        storageEl.value = 'Vorratskammer';
                        storageEl.className = 'preview-input storage-badge ' + getStorageBadgeClass('Vorratskammer');
                    }
                }
            }
            const petBadge = document.getElementById(`preview-pet-badge-${idx}`);
            if (petBadge) {
                if (isPet) {
                    petBadge.className = 'badge';
                    petBadge.style.backgroundColor = '#6d4c41';
                    petBadge.style.color = '#fff';
                    petBadge.style.fontSize = '0.72rem';
                    petBadge.style.padding = '2px 6px';
                    petBadge.style.borderRadius = '4px';
                    petBadge.style.whiteSpace = 'nowrap';
                    petBadge.style.display = 'inline-block';
                    petBadge.innerText = '🐱 Tiernahrung';
                } else {
                    petBadge.style.display = 'none';
                }
            }
            const dup = checkInventoryDuplicate(val);
            if (currentPreviewItems[idx]) {
                currentPreviewItems[idx].duplicateOf = dup ? { id: dup.id, name: dup.name, quantity: dup.quantity, unit: dup.unit } : null;
                if (!dup) currentPreviewItems[idx].restockExisting = false;
            }
            const dupEl = document.getElementById(`preview-dup-${idx}`);
            if (dupEl) {
                if (dup) {
                    dupEl.innerHTML = `
                        <span style="font-size: 0.72rem; background: #e8f5e9; color: #1b5e20; border: 1px solid #81c784; border-radius: 4px; padding: 1px 5px; font-weight: 600;" title="Artikel ist bereits im Vorrat vorhanden">
                            📦 Im Vorrat (${dup.quantity} ${dup.unit || 'Stk.'})
                        </span>
                        <label style="font-size: 0.75rem; color: #1b5e20; display: inline-flex; align-items: center; gap: 3px; cursor: pointer; font-weight: 500;">
                            <input type="checkbox" ${currentPreviewItems[idx].restockExisting ? 'checked' : ''} onchange="updatePreviewField(${idx}, 'restockExisting', this.checked)">
                            Vorrat aufstocken
                        </label>
                    `;
                    dupEl.style.display = 'flex';
                } else {
                    dupEl.style.display = 'none';
                }
            }
        }

        function renderPreviewRows() {
            const units = ['Stk.', 'Packung', 'Becher', 'Flasche', 'Dose', 'g', 'kg', 'l'];
            return currentPreviewItems.map((item, idx) => {
                const icon = getCategoryIcon(item.name);
                const isPet = item.isPetFood || /perle|gourmet|felix|whiskas|sheba|chappi|pedigree|katzen|hunde|tiernahr|tierfutter|dreamies|kitekat|purina/i.test(item.name || '');
                const isMulti = item.quantity > 1 && item.price > 0;
                const unitPrice = isMulti ? (item.price / item.quantity).toFixed(2).replace('.', ',') : '';
                const matches = !previewFilterQuery || (item.name || '').toLowerCase().includes(previewFilterQuery);
                const rowStyle = matches ? '' : 'display: none;';
                const storageClass = getStorageBadgeClass(item.storageLocation);

                return `
                <tr id="preview-row-${idx}" style="${rowStyle}">
                    <td style="text-align: center;">
                        <input type="checkbox" class="preview-chk" ${item.selected ? 'checked' : ''} onchange="updatePreviewSelection(${idx}, this.checked)">
                    </td>
                    <td>
                        <div style="display: flex; align-items: center; gap: 0.4rem;">
                            <span id="preview-icon-${idx}" style="font-size: 1.25rem;" title="Warengruppe">${icon}</span>
                            <input type="text" class="preview-input" value="${escapeHtml(item.name)}" oninput="updatePreviewName(${idx}, this.value)">
                            ${isPet ? `<span id="preview-pet-badge-${idx}" class="badge" style="background-color: #6d4c41; color: #fff; font-size: 0.72rem; padding: 2px 6px; border-radius: 4px; white-space: nowrap;" title="Tiernahrung erkannt & Vorratskammer zugewiesen">🐱 Tiernahrung</span>` : `<span id="preview-pet-badge-${idx}" style="display: none;"></span>`}
                            ${item.isLearned ? `<span class="badge" style="background-color: #7b1fa2; color: #fff; font-size: 0.72rem; padding: 2px 6px; border-radius: 4px; white-space: nowrap;" title="Bereits in Lerndatenbank gelernt">🧠 Gelernt</span>` : ''}
                        </div>
                        <div id="preview-dup-${idx}" style="margin-top: 3px; display: ${item.duplicateOf ? 'flex' : 'none'}; align-items: center; gap: 0.45rem; flex-wrap: wrap;">
                            ${item.duplicateOf ? `
                                <span style="font-size: 0.72rem; background: #e8f5e9; color: #1b5e20; border: 1px solid #81c784; border-radius: 4px; padding: 1px 5px; font-weight: 600;" title="Artikel ist bereits im Vorrat vorhanden">
                                    📦 Im Vorrat (${item.duplicateOf.quantity} ${item.duplicateOf.unit || 'Stk.'})
                                </span>
                                <label style="font-size: 0.75rem; color: #1b5e20; display: inline-flex; align-items: center; gap: 3px; cursor: pointer; font-weight: 500;">
                                    <input type="checkbox" ${item.restockExisting ? 'checked' : ''} onchange="updatePreviewField(${idx}, 'restockExisting', this.checked)">
                                    Vorrat aufstocken (+${item.quantity})
                                </label>
                            ` : ''}
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div style="display: inline-flex; align-items: center; gap: 2px;">
                            <button type="button" class="btn btn-sm btn-secondary" style="padding: 2px 7px; font-weight: bold; line-height: 1;" onclick="changePreviewQty(${idx}, -1)">−</button>
                            <input type="number" step="0.1" min="0.1" class="preview-input" style="width: 50px; text-align: center; padding: 0.35rem 0.15rem;" value="${item.quantity}" oninput="updatePreviewField(${idx}, 'quantity', parseFloat(this.value) || 1); updateTotals(); updateUnitPriceDisplay(${idx});">
                            <button type="button" class="btn btn-sm btn-secondary" style="padding: 2px 7px; font-weight: bold; line-height: 1;" onclick="changePreviewQty(${idx}, 1)">+</button>
                        </div>
                    </td>
                    <td>
                        <select class="preview-input" onchange="updatePreviewField(${idx}, 'unit', this.value); updateUnitPriceDisplay(${idx});">
                            ${units.map(u => `
                                <option value="${u}" ${item.unit === u ? 'selected' : ''}>${u}</option>
                            `).join('')}
                        </select>
                    </td>
                    <td>
                        <input type="number" step="0.01" class="preview-input" value="${item.price ? item.price.toFixed(2) : '0.00'}" oninput="updatePreviewField(${idx}, 'price', parseFloat(this.value) || 0); updateTotals(); updateUnitPriceDisplay(${idx});">
                        <div id="unit-price-${idx}" style="font-size: 0.75rem; color: #555; margin-top: 2px; ${isMulti ? '' : 'display: none;'}">
                            ${isMulti ? `${unitPrice} € / ${escapeHtml(item.unit || 'Stk.')}` : ''}
                        </div>
                        ${item.priceDiff && item.priceDiff !== '0,00 €' ? `
                            <div style="margin-top: 2px;">
                                <span class="badge ${item.priceDiffVal > 0 ? 'badge-red' : 'badge-green'}" style="font-size: 0.72rem; padding: 1px 5px;" title="Vorheriger Einkaufspreis: ${item.previousPrice != null ? item.previousPrice.toFixed(2).replace('.', ',') + ' €' : 'unbekannt'}">
                                    ${item.priceDiffVal > 0 ? '📈 ' : '📉 '}${escapeHtml(item.priceDiff)}
                                </span>
                            </div>
                        ` : ''}
                    </td>
                    <td>
                        <select id="preview-storage-${idx}" class="preview-input storage-badge ${storageClass}" onchange="updatePreviewStorage(${idx}, this)">
                            <option value="Kühlschrank" ${item.storageLocation === 'Kühlschrank' ? 'selected' : ''}>❄️ Kühlschrank</option>
                            <option value="Gefrierfach" ${item.storageLocation === 'Gefrierfach' ? 'selected' : ''}>🧊 Tiefkühler</option>
                            <option value="Vorratskammer" ${item.storageLocation === 'Vorratskammer' ? 'selected' : ''}>🥫 Vorratskammer</option>
                        </select>
                    </td>
                    <td>
                        <input type="date" class="preview-input" value="${item.expiryDateStr || ''}" onchange="updatePreviewField(${idx}, 'expiryDateStr', this.value)">
                    </td>
                    <td>
                        <input type="text" class="preview-input" placeholder="DD.MM.YYYY" value="${escapeHtml(item.purchaseDate || currentReceiptMetadata.purchaseDate || '')}" onchange="updatePreviewField(${idx}, 'purchaseDate', this.value)">
                    </td>
                    <td style="text-align: center;">
                        <button class="btn btn-danger btn-sm" onclick="removePreviewRow(${idx})" title="Zeile löschen">✕</button>
                    </td>
                </tr>
            `}).join('');
        }

        function updatePreviewRowIcon(idx) {
            const item = currentPreviewItems[idx];
            if (item) {
                const iconEl = document.getElementById(`preview-icon-${idx}`);
                if (iconEl) iconEl.innerText = getCategoryIcon(item.name);
            }
        }

        function updateUnitPriceDisplay(idx) {
            const item = currentPreviewItems[idx];
            if (item) {
                const upEl = document.getElementById(`unit-price-${idx}`);
                if (upEl) {
                    if (item.quantity > 1 && item.price > 0) {
                        const unitPrice = (item.price / item.quantity).toFixed(2).replace('.', ',');
                        upEl.innerText = `${unitPrice} € / ${item.unit || 'Stk.'}`;
                        upEl.style.display = 'block';
                    } else {
                        upEl.style.display = 'none';
                    }
                }
            }
        }

        function changePreviewQty(idx, delta) {
            const item = currentPreviewItems[idx];
            if (item) {
                let q = Math.round(((item.quantity || 1) + delta) * 10) / 10;
                if (q < 0.1) q = 0.1;
                item.quantity = q;
                const tbody = document.getElementById('preview-tbody');
                if (tbody) tbody.innerHTML = renderPreviewRows();
                updateTotals();
            }
        }

        function updateTotals() {
            const selected = currentPreviewItems.filter(it => it.selected);
            const total = selected.reduce((sum, it) => sum + (parseFloat(it.price) || 0), 0);
            const count = selected.length;
            const totalStr = total.toFixed(2).replace('.', ',') + ' €';

            const topTotal = document.getElementById('preview-total-sum');
            if (topTotal) topTotal.innerText = totalStr;

            const btmTotal = document.getElementById('preview-bottom-total');
            if (btmTotal) btmTotal.innerText = totalStr;

            const badge = document.getElementById('preview-badge-count');
            if (badge) badge.innerText = `${count} von ${currentPreviewItems.length} ausgewählt (${totalStr})`;

            const importBtn = document.getElementById('preview-import-btn');
            if (importBtn) {
                importBtn.innerText = `✅ In den Vorrat übernehmen (${count} Artikel • ${totalStr})`;
            }

            // Kassenbon-Summenabgleich
            const compBox = document.getElementById('receipt-total-comparison');
            if (compBox && currentReceiptMetadata.receiptTotal != null) {
                const bonTotal = currentReceiptMetadata.receiptTotal;
                const diff = Math.round((total - bonTotal) * 100) / 100;
                const isExact = Math.abs(diff) < 0.01;
                const diffSign = diff > 0 ? '+' : '';
                const diffStr = diffSign + diff.toFixed(2).replace('.', ',') + ' €';

                let statusBadge = '';
                if (isExact) {
                    statusBadge = `<span style="color: #2e7d32; font-weight: bold; background: #e8f5e9; border: 1px solid #81c784; padding: 2px 8px; border-radius: 4px;">✅ Exakte Übereinstimmung (0,00 €)</span>`;
                } else {
                    statusBadge = `<span style="color: ${diff < 0 ? '#c62828' : '#e65100'}; font-weight: bold; background: ${diff < 0 ? '#ffebee' : '#fff3e0'}; border: 1px solid ${diff < 0 ? '#ef9a9a' : '#ffb74d'}; padding: 2px 8px; border-radius: 4px;">⚠️ Differenz: ${diffStr}</span>`;
                }

                compBox.innerHTML = `
                    <div style="display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
                        <span>🧾 Belegsumme: <strong>${bonTotal.toFixed(2).replace('.', ',')} €</strong></span>
                        <span style="color: #bbb;">|</span>
                        <span>🛒 Markierte Artikel: <strong>${totalStr}</strong></span>
                    </div>
                    <div>${statusBadge}</div>
                `;
            }
        }

        function updatePreviewField(idx, field, val) {
            if (currentPreviewItems[idx]) {
                currentPreviewItems[idx][field] = val;
            }
        }

        function updatePreviewSelection(idx, checked) {
            if (currentPreviewItems[idx]) {
                currentPreviewItems[idx].selected = checked;
                updateTotals();
            }
        }

        function toggleAllPreview(checked) {
            currentPreviewItems.forEach(it => it.selected = checked);
            document.querySelectorAll('.preview-chk').forEach(chk => chk.checked = checked);
            const selectAll = document.getElementById('preview-select-all');
            if (selectAll) selectAll.checked = checked;
            updateTotals();
        }

        function removePreviewRow(idx) {
            const removed = currentPreviewItems[idx];
            if (!removed) return;

            lastDeletedPreviewItem = { item: { ...removed }, index: idx };
            currentPreviewItems = currentPreviewItems.filter((_, i) => i !== idx);
            currentPreviewItems.forEach((it, i) => it.id = i);

            const tbody = document.getElementById('preview-tbody');
            if (tbody) tbody.innerHTML = renderPreviewRows();
            updateTotals();

            showPreviewUndo(removed.name || 'Artikel');
        }

        function showPreviewUndo(itemName) {
            if (previewUndoTimer) {
                clearTimeout(previewUndoTimer);
                previewUndoTimer = null;
            }
            const undoBar = document.getElementById('preview-undo-bar');
            if (undoBar) {
                const displayLabel = itemName ? `„${escapeHtml(itemName)}“` : 'Artikel';
                undoBar.innerHTML = `
                    <span>🗑️ ${displayLabel} entfernt.</span>
                    <button type="button" onclick="undoDeletePreviewRow()">↩️ Rückgängig machen (5s)</button>
                `;
                undoBar.style.display = 'flex';

                previewUndoTimer = setTimeout(() => {
                    undoBar.style.display = 'none';
                    lastDeletedPreviewItem = null;
                    previewUndoTimer = null;
                }, 5000);
            }
        }

        function undoDeletePreviewRow() {
            if (!lastDeletedPreviewItem) return;
            if (previewUndoTimer) {
                clearTimeout(previewUndoTimer);
                previewUndoTimer = null;
            }
            const undoBar = document.getElementById('preview-undo-bar');
            if (undoBar) undoBar.style.display = 'none';

            const insertIndex = Math.min(lastDeletedPreviewItem.index, currentPreviewItems.length);
            currentPreviewItems.splice(insertIndex, 0, lastDeletedPreviewItem.item);
            currentPreviewItems.forEach((it, i) => it.id = i);
            lastDeletedPreviewItem = null;

            const tbody = document.getElementById('preview-tbody');
            if (tbody) tbody.innerHTML = renderPreviewRows();
            updateTotals();
            showToast('↩️ Artikel wiederhergestellt.');
        }

        function cancelPreview() {
            if (previewUndoTimer) {
                clearTimeout(previewUndoTimer);
                previewUndoTimer = null;
            }
            lastDeletedPreviewItem = null;
            previewFilterQuery = '';
            previewSortColumn = '';
            previewSortAsc = true;
            currentPreviewItems = [];
            document.getElementById('receipt-preview-container').innerHTML = '';
            document.getElementById('receipt-results').innerHTML = '<p style="color: #757575;">Import abgebrochen.</p>';
            document.getElementById('receipt-text').value = '';
        }

        async function commitPreviewImport() {
            const selected = currentPreviewItems.filter(it => it.selected && it.name.trim().length > 0);
            if (selected.length === 0) {
                alert('Bitte wähle mindestens einen Artikel zum Übernehmen aus.');
                return;
            }

            if (currentReceiptMetadata.duplicateStatus === 'duplicate_rejected') {
                alert('⚠️ Beleg bereits mit gleichem/besserem Ergebnis vorhanden. Import abgewiesen.');
                return;
            }

            const itemsToSend = selected.map(it => {
                let exp = it.expiryDate;
                if (it.expiryDateStr) {
                    const parsed = Date.parse(it.expiryDateStr + 'T00:00:00');
                    if (!isNaN(parsed)) exp = parsed;
                }
                return {
                    name: it.name,
                    rawName: it.rawName || it.originalName || it.name,
                    category: it.category || 'Vorrat',
                    quantity: it.quantity,
                    unit: it.unit,
                    price: it.price,
                    storageLocation: it.storageLocation,
                    supermarket: it.supermarket || currentReceiptMetadata.supermarket || 'Import',
                    expiryDate: exp,
                    expiryDateStr: it.expiryDateStr,
                    purchaseDate: it.purchaseDate || currentReceiptMetadata.purchaseDate || '',
                    purchaseTime: currentReceiptMetadata.purchaseTime || '',
                    receiptKey: currentReceiptMetadata.receiptKey || '',
                    restockExisting: !!it.restockExisting,
                    existingId: it.duplicateOf ? it.duplicateOf.id : null
                };
            });

            try {
                const res = await fetch('/api/inventory/import-batch', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        items: itemsToSend,
                        receiptKey: currentReceiptMetadata.receiptKey,
                        duplicateStatus: currentReceiptMetadata.duplicateStatus,
                        qualityScore: currentReceiptMetadata.qualityScore,
                        supermarket: currentReceiptMetadata.supermarket,
                        purchaseDate: currentReceiptMetadata.purchaseDate,
                        purchaseTime: currentReceiptMetadata.purchaseTime,
                        receiptTotal: currentReceiptMetadata.receiptTotal,
                        depositSummary: currentReceiptMetadata.depositSummary
                    })
                });

                if (res.status === 409) {
                    const errData = await res.json();
                    alert('⚠️ ' + (errData.message || 'Beleg bereits mit gleichem/besserem Ergebnis vorhanden.'));
                    return;
                }

                const data = await res.json();
                const count = data.count || selected.length;
                
                if (previewUndoTimer) {
                    clearTimeout(previewUndoTimer);
                    previewUndoTimer = null;
                }
                lastDeletedPreviewItem = null;
                currentPreviewItems = [];

                if (data.depositBalance !== undefined) {
                    const pfandEl = document.getElementById('pfand-balance-display');
                    if (pfandEl) pfandEl.innerText = (data.depositBalance).toFixed(2).replace('.', ',') + ' €';
                }

                const container = document.getElementById('receipt-preview-container');
                if (container) {
                    container.innerHTML = `
                        <div class="preview-card" style="border: 2px solid #2e7d32; background: #f1f8e9; text-align: center; padding: 2.25rem 1.5rem; box-shadow: 0 4px 12px rgba(46,125,50,0.15);">
                            <div style="font-size: 3rem; line-height: 1; margin-bottom: 0.75rem;">🎉</div>
                            <h3 style="color: #1b5e20; margin: 0 0 0.5rem 0; font-size: 1.4rem;">Import erfolgreich abgeschlossen!</h3>
                            <p style="font-size: 1.05rem; color: #2e7d32; margin: 0 0 1rem 0;">
                                Es wurden <strong>${count}</strong> Artikel erfolgreich in den Vorrat übernommen.
                            </p>
                            ${data.depositSummary && data.depositSummary.total ? `
                                <div style="margin: 0.75rem auto 1.25rem auto; max-width: 500px; padding: 0.5rem 0.75rem; background: #e3f2fd; border: 1.5px solid #90caf9; border-radius: 6px; font-size: 0.88rem; color: #0d47a1;">
                                    🪙 <strong>Pfandkonto saldiert:</strong> +${(data.depositSummary.total).toFixed(2).replace('.', ',')} € (Neuer Saldo: ${(data.depositBalance || 0).toFixed(2).replace('.', ',')} €)
                                </div>
                            ` : ''}
                            ${data.recipeIdeas && data.recipeIdeas.length > 0 ? `
                                <div style="margin: 1.5rem auto; max-width: 650px; text-align: left; background: #ffffff; border: 2px solid #2e7d32; border-radius: 8px; padding: 1rem 1.25rem; box-shadow: 0 2px 8px rgba(0,0,0,0.06);">
                                    <div style="font-weight: bold; font-size: 1.1rem; color: #1b5e20; margin-bottom: 0.75rem; display: flex; align-items: center; gap: 0.5rem;">
                                        🍳 Sofort-Rezeptideen für deinen Einkauf:
                                    </div>
                                    ${data.recipeIdeas.map((r, i) => `
                                        <div style="background: #f9fbe7; border-left: 4px solid #7cb342; padding: 0.75rem; border-radius: 4px; margin-bottom: ${i < data.recipeIdeas.length - 1 ? '0.75rem' : '0'};">
                                            <div style="font-weight: bold; color: #2e7d32; font-size: 0.98rem;">${escapeHtml(r.title)} <span style="font-size: 0.78rem; color: #689f38; font-weight: normal;">(${escapeHtml(r.time || '15 Min.')})</span></div>
                                            <div style="font-size: 0.85rem; color: #555; margin: 3px 0;"><strong>Zutaten:</strong> ${escapeHtml(r.ingredients)}</div>
                                            <div style="font-size: 0.85rem; color: #333; white-space: pre-line;">${escapeHtml(r.instructions)}</div>
                                        </div>
                                    `).join('')}
                                </div>
                            ` : ''}
                            <div style="display: flex; justify-content: center; gap: 1rem; flex-wrap: wrap; margin-top: 1rem;">
                                <button class="btn" style="padding: 0.65rem 1.6rem; font-size: 1.05rem; font-weight: bold;" onclick="showTab('inv'); loadInventory();">
                                    👉 Direkt zum Vorrat wechseln
                                </button>
                                <button class="btn btn-secondary" style="padding: 0.65rem 1.2rem;" onclick="cancelPreview()">
                                    🧾 Weiteren Kassenbon scannen
                                </button>
                            </div>
                        </div>
                    `;
                }
                document.getElementById('receipt-results').innerHTML = '';
                document.getElementById('receipt-text').value = '';
                showToast(`✅ ${count} Artikel erfolgreich in den Vorrat übernommen!`);
                loadInventory();
            } catch (e) {
                alert('Fehler beim Speichern in den Vorrat: ' + e.message);
            }
        }

        async function deduplicateInventory() {
            try {
                const res = await fetch('/api/inventory/deduplicate', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({})
                });
                const data = await res.json();
                const merged = data.mergedCount || 0;
                showToast(merged > 0 ? `🧹 ${merged} doppelte Artikel zusammengefasst!` : '✓ Keine Duplikate vorhanden.');
                loadInventory();
            } catch (e) {
                alert('Fehler beim Zusammenfassen: ' + e.message);
            }
        }

        async function cleanNonFoodInventory() {
            try {
                const res = await fetch('/api/inventory/clean-non-food', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({})
                });
                const data = await res.json();
                const removed = data.removedCount || 0;
                showToast(removed > 0 ? `🛡️ ${removed} Drogerie- und Non-Food Artikel entfernt!` : '✓ Keine Non-Food Artikel im Vorrat gefunden.');
                loadInventory();
            } catch (e) {
                alert('Fehler beim Aussortieren: ' + e.message);
            }
        }

        async function clearAllInventory() {
            if (!confirm('Möchtest du wirklich den gesamten Vorrat leeren? Alle Artikel werden gelöscht.')) {
                return;
            }
            try {
                await fetch('/api/inventory/clear-all', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({})
                });
                showToast('🗑️ Vorrat vollständig geleert.');
                loadInventory();
            } catch (e) {
                alert('Fehler beim Leeren des Vorrats: ' + e.message);
            }
        }



        async function loadShopping() {
            const res = await fetch('/api/shopping');
            const data = await res.json();
            const tbody = document.getElementById('shopping-table');
            tbody.innerHTML = data.map(item => `
                <tr>
                    <td><input type="checkbox" ${item.isChecked ? 'checked' : ''} onclick="toggleShop('${item.id}')"></td>
                    <td><span style="${item.isChecked ? 'text-decoration: line-through;' : ''}">${item.name}</span></td>
                    <td>${item.quantity} ${item.unit || 'Stk.'}</td>
                    <td><button class="btn btn-sm" onclick="toggleShop('${item.id}')">Umschalten</button></td>
                </tr>
            `).join('');
        }

        async function addShopItem() {
            const name = document.getElementById('shop-name').value;
            if (!name) return;
            await fetch('/api/shopping/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name, quantity: 1, unit: 'Stk.' })
            });
            document.getElementById('shop-name').value = '';
            loadShopping();
        }

        async function toggleShop(id) {
            await fetch('/api/shopping/toggle', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            });
            loadShopping();
        }

        async function loadRecipes() {
            const res = await fetch('/api/recipes');
            const recipes = await res.json();
            const div = document.getElementById('recipes-list');
            div.innerHTML = recipes.map(r => `
                <div class="card" style="border-left: 4px solid #2e7d32;">
                    <h3>🍳 ${r.title}</h3>
                    <p><strong>Zutaten:</strong> ${r.ingredients}</p>
                    <p style="white-space: pre-line;">${r.instructions}</p>
                </div>
            `).join('');
        }

        async function loadHistory() {
            const res = await fetch('/api/history');
            const data = await res.json();
            const tbody = document.getElementById('history-table');
            tbody.innerHTML = data.map(item => {
                const date = new Date(item.date).toLocaleDateString('de-DE');
                return `
                    <tr>
                        <td>${date}</td>
                        <td>${item.itemName}</td>
                        <td>${item.quantity} ${item.unit || 'Stk.'}</td>
                        <td>${(item.price || 0).toFixed(2)} €</td>
                    </tr>
                `;
            }).join('');
        }

        async function loadDepositAccount() {
            try {
                const res = await fetch('/api/deposit-account');
                const data = await res.json();
                const el = document.getElementById('pfand-balance-display');
                if (el) el.innerText = (data.balance || 0).toFixed(2).replace('.', ',') + ' €';
            } catch (e) {}
        }
    </script>
</body>
</html>
"""

class FridgeRequestHandler(http.server.BaseHTTPRequestHandler):

    def send_cors_headers(self, status_code, content_type="application/json"):
        self.send_response(status_code)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        self.send_header("Connection", "close")

    def do_OPTIONS(self):
        self.send_cors_headers(204, "text/plain")
        self.end_headers()

    def do_GET(self):
        clean_path = self.path.split("?")[0]

        if clean_path in ["/", "/index.html"]:
            self.send_cors_headers(200, "text/html; charset=UTF-8")
            self.end_headers()
            self.wfile.write(HTML_CONTENT.encode("utf-8"))
            return

        if clean_path == "/manifest.json":
            manifest = {
                "name": "Kühlschrank Profi 🍏 PC-Begleiter",
                "short_name": "Kühlschrank Profi",
                "start_url": "/",
                "display": "standalone",
                "background_color": "#2e7d32",
                "theme_color": "#2e7d32"
            }
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(manifest).encode("utf-8"))
            return

        if clean_path == "/sw.js":
            sw = "self.addEventListener('install', e => self.skipWaiting()); self.addEventListener('fetch', e => e.respondWith(fetch(e.request)));"
            self.send_cors_headers(200, "application/javascript")
            self.end_headers()
            self.wfile.write(sw.encode("utf-8"))
            return

        if clean_path == "/api/ping":
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')
            return

        if clean_path == "/api/inventory":
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(db["inventory"], ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/shopping":
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(db["shopping"], ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/history":
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(db.get("history", []), ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/recipes":
            names = [it["name"] for it in db["inventory"] if it.get("name")]
            sample = names[:3] if names else ["Kartoffeln", "Möhren", "Käse"]
            recipes = [
                {
                    "title": f"Resten-Pfanne mit {' & '.join(sample[:2])}",
                    "ingredients": ", ".join(sample),
                    "instructions": "1. Zutaten klein schneiden.\n2. In einer Pfanne mit etwas Öl 10-15 Min. anbraten.\n3. Nach Geschmack würzen und servieren!"
                },
                {
                    "title": f"Herzhafter Auflauf mit {sample[-1] if sample else 'Gemüse'}",
                    "ingredients": ", ".join(sample),
                    "instructions": "1. Backofen auf 180°C Umluft vorheizen.\n2. Zutaten in eine Auflaufform geben.\n3. Mit Sahne/Käse überbacken und 25 Min. garen."
                }
            ]
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(recipes, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/learned-products":
            items = load_learned_products()
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "items": items}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/deposit-account":
            account = db.get("depositAccount", {"balance": 0.0, "transactions": []})
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(account, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/receipt-archive":
            archive = load_receipt_archive()
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "items": archive}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/price-radar":
            history = load_price_history()
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "history": history}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/sync_corrections":
            corrections = load_receipt_corrections()
            learned = load_learned_products()
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "status": "ok",
                "receiptCorrectionsCount": len(corrections),
                "learnedProductsCount": len(learned),
                "receiptCorrections": corrections
            }, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/offers":
            from urllib.parse import parse_qs, urlparse
            query_components = parse_qs(urlparse(self.path).query)
            zip_code = query_components.get("zip", ["07743"])[0]

            sample_offers = [
                {
                    "title": "Bio Vollmilch 3.8%",
                    "price": 1.19,
                    "retailer": "REWE",
                    "validityPeriod": "Aktionswoche",
                    "zip": zip_code,
                    "explicitBrochureUrl": f"https://shop.rewe.de/angebote?search=Bio+Vollmilch&zip={zip_code}"
                },
                {
                    "title": "Gouda jung in Scheiben",
                    "price": 1.79,
                    "retailer": "Kaufland",
                    "validityPeriod": "Aktionswoche",
                    "zip": zip_code,
                    "explicitBrochureUrl": f"https://www.kaufland.de/angebote.html?q=Gouda&zip={zip_code}"
                },
                {
                    "title": "Frische Eier Freiland 10er",
                    "price": 1.99,
                    "retailer": "Lidl",
                    "validityPeriod": "Aktionswoche",
                    "zip": zip_code,
                    "explicitBrochureUrl": f"https://www.lidl.de/c/billiger-mittwoch/a10006037?query=Eier&zip={zip_code}"
                },
                {
                    "title": "Butter",
                    "price": 1.49,
                    "retailer": "Netto",
                    "validityPeriod": "Aktionswoche",
                    "zip": zip_code,
                    "explicitBrochureUrl": f"https://www.netto-online.de/filialangebote/?search=Butter&zip={zip_code}"
                },
                {
                    "title": "Bananen",
                    "price": 1.29,
                    "retailer": "Edeka",
                    "validityPeriod": "Aktionswoche",
                    "zip": zip_code,
                    "explicitBrochureUrl": f"https://www.edeka.de/eh/angebote.jsp?query=Bananen&marketSearch={zip_code}"
                }
            ]
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "zip": zip_code, "offers": sample_offers}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path in ["/api/products/search", "/api/learned-products/search"]:
            from urllib.parse import parse_qs, urlparse, quote
            query_components = parse_qs(urlparse(self.path).query)
            q = query_components.get("q", [""])[0].strip()

            results = []
            if q:
                norm_q = q.lower()
                all_learned = load_learned_products()
                for item in all_learned:
                    name = item.get("name", "")
                    raw_n = item.get("rawName", "")
                    if norm_q in name.lower() or norm_q in raw_n.lower():
                        results.append({
                            "cleanName": name,
                            "rawName": raw_n,
                            "category": item.get("category", "Vorrat"),
                            "unit": item.get("unit", "Stk."),
                            "source": "learned_products.db"
                        })

                # Robotoff / OpenFoodFacts Fallback, falls keine Treffer in learned_products
                if not results and len(q) >= 2:
                    try:
                        import urllib.request
                        encoded_q = quote(q)
                        off_url = f"https://de.openfoodfacts.org/cgi/search.pl?search_terms={encoded_q}&search_simple=1&action=process&json=1&page_size=5"
                        req = urllib.request.Request(off_url, headers={"User-Agent": "KuehlschrankProfi/1.0"})
                        with urllib.request.urlopen(req, timeout=2.5) as resp:
                            data = json.loads(resp.read().decode("utf-8"))
                            for prod in data.get("products", [])[:5]:
                                p_name = prod.get("product_name_de") or prod.get("product_name")
                                if p_name:
                                    results.append({
                                        "cleanName": p_name.strip(),
                                        "rawName": q,
                                        "category": prod.get("categories", "Lebensmittel").split(",")[0].strip() if prod.get("categories") else "Lebensmittel",
                                        "unit": "Stk.",
                                        "source": "robotoff"
                                    })
                    except Exception:
                        pass

            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "query": q, "results": results[:10]}, ensure_ascii=False).encode("utf-8"))
            return

        self.send_cors_headers(404, "text/plain")
        self.end_headers()
        self.wfile.write(b"Not Found")

    def do_POST(self):
        clean_path = self.path.split("?")[0]
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else "{}"
        
        try:
            params = json.loads(body)
        except Exception:
            params = {}

        if clean_path == "/api/auth":
            pin = str(params.get("pin", ""))
            if pin == db.get("pin", "1234"):
                self.send_cors_headers(200, "application/json")
                self.end_headers()
                res = {"status": "ok", "token": db.get("persistent_token", "default-token")}
                self.wfile.write(json.dumps(res).encode("utf-8"))
            else:
                self.send_cors_headers(401, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"error","message":"Falsche PIN"}')
            return

        if clean_path == "/api/verify-session":
            token = str(params.get("token", ""))
            if token and token == db.get("persistent_token", "default-token"):
                self.send_cors_headers(200, "application/json")
                self.end_headers()
                res = {"status": "ok", "token": token}
                self.wfile.write(json.dumps(res).encode("utf-8"))
            else:
                self.send_cors_headers(401, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"unauthorized"}')
            return

        if clean_path == "/api/inventory/add":
            name = params.get("name", "").strip()
            if name:
                new_item = {
                    "id": str(uuid.uuid4()),
                    "name": name,
                    "quantity": params.get("quantity", 1),
                    "unit": params.get("unit", "Stk."),
                    "storageLocation": params.get("storageLocation", "Kühlschrank"),
                    "expiryDate": int((datetime.now() + timedelta(days=7)).timestamp() * 1000)
                }
                db["inventory"].append(new_item)
                save_data(db)
                self.send_cors_headers(200, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"ok"}')
            else:
                self.send_cors_headers(400, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"error","message":"Name fehlt"}')
            return

        if clean_path == "/api/inventory/update-mhd":
            item_id = params.get("id")
            date_str = params.get("expiryDate")
            target = next((it for it in db["inventory"] if it["id"] == item_id), None)
            if target and date_str:
                try:
                    dt = datetime.strptime(date_str, "%Y-%m-%d")
                    target["expiryDate"] = int(dt.timestamp() * 1000)
                    save_data(db)
                    self.send_cors_headers(200, "application/json")
                    self.end_headers()
                    self.wfile.write(b'{"status":"ok"}')
                    return
                except Exception:
                    pass
            self.send_cors_headers(404, "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"not_found"}')
            return

        if clean_path == "/api/inventory/consume":
            item_id = params.get("id")
            target = next((it for it in db["inventory"] if it["id"] == item_id), None)
            if target:
                qty = target.get("quantity", 1)
                if qty > 1:
                    target["quantity"] = qty - 1
                else:
                    db["inventory"] = [it for it in db["inventory"] if it["id"] != item_id]
                save_data(db)
                self.send_cors_headers(200, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"ok"}')
            else:
                self.send_cors_headers(404, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"not_found"}')
            return

        if clean_path == "/api/inventory/delete":
            item_id = params.get("id")
            db["inventory"] = [it for it in db["inventory"] if it["id"] != item_id]
            save_data(db)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')
            return

        if clean_path == "/api/shopping/add":
            name = params.get("name", "").strip()
            if name:
                db["shopping"].append({
                    "id": str(uuid.uuid4()),
                    "name": name,
                    "quantity": params.get("quantity", 1),
                    "unit": params.get("unit", "Stk."),
                    "isChecked": False
                })
                save_data(db)
                self.send_cors_headers(200, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"ok"}')
            else:
                self.send_cors_headers(400, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"error"}')
            return

        if clean_path == "/api/shopping/toggle":
            item_id = params.get("id")
            target = next((it for it in db["shopping"] if it["id"] == item_id), None)
            if target:
                target["isChecked"] = not target.get("isChecked", False)
                save_data(db)
                self.send_cors_headers(200, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"ok"}')
            else:
                self.send_cors_headers(404, "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"not_found"}')
            return

        if clean_path == "/api/sync_corrections":
            corrections = params if isinstance(params, list) else (params.get("corrections", []) if isinstance(params, dict) else [])
            saved_count = 0
            for corr in corrections:
                if not isinstance(corr, dict):
                    continue
                raw_name = (corr.get("rawName") or corr.get("raw") or corr.get("originalName") or corr.get("shortcut") or "").strip()
                clean_name = (corr.get("cleanName") or corr.get("name") or "").strip()
                unit = corr.get("unit", "Stk.")
                category = corr.get("category", "Vorrat")
                storage_location = corr.get("storageLocation") or corr.get("location", "Kühlschrank")
                supermarket = corr.get("supermarket", "")

                if raw_name and clean_name:
                    add_receipt_correction(raw_name, clean_name)
                    learn_product(raw_name, clean_name, unit, category, storage_location, supermarket)
                    saved_count += 1
                elif clean_name:
                    learn_product(clean_name, clean_name, unit, category, storage_location, supermarket)
                    saved_count += 1

            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "status": "ok",
                "syncedCount": saved_count,
                "totalLearned": len(load_learned_products())
            }, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/learned-products/save":
            raw_name = params.get("rawName") or params.get("name", "")
            clean_name = params.get("name", "")
            unit = params.get("unit", "Stk.")
            category = params.get("category", "Vorrat")
            storage_location = params.get("storageLocation", "Kühlschrank")
            supermarket = params.get("supermarket", "")
            learn_product(raw_name, clean_name, unit, category, storage_location, supermarket)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "items": load_learned_products()}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/learned-products/delete":
            item_id = params.get("id")
            raw_name = params.get("rawName")
            items = load_learned_products()
            if item_id:
                items = [it for it in items if it.get("id") != item_id]
            elif raw_name:
                items = [it for it in items if it.get("rawName") != raw_name]
            save_learned_products(items)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "items": items}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/parse-receipt":
            analysis = analyze_uploaded_receipt(params)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(analysis, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/inventory/import-batch":
            items_to_add = params.get("items", [])
            receipt_key = params.get("receiptKey")
            dup_status = params.get("duplicateStatus")
            quality_score = float(params.get("qualityScore", 0.0) or 0.0)
            supermarket = params.get("supermarket", "")
            purchase_date = params.get("purchaseDate", "")
            purchase_time = params.get("purchaseTime", "")
            receipt_total = params.get("receiptTotal")

            # Falls kein receipt_key vom Frontend gesendet wurde, aber supermarket / purchase_date da sind
            if not receipt_key and supermarket and purchase_date:
                receipt_key = generate_receipt_key(supermarket, purchase_date, purchase_time, receipt_total)

            history = load_receipt_history()

            if receipt_key and receipt_key in history:
                prev_record = history[receipt_key]
                prev_score = float(prev_record.get("qualityScore", 0.0) or 0.0)
                prev_count = int(prev_record.get("itemCount", 0) or 0)
                current_score = quality_score if quality_score > 0 else calculate_recognition_quality(items_to_add, receipt_total)

                # Prüfen, ob neues Ergebnis besser ist
                is_better = (current_score > prev_score or len(items_to_add) > prev_count)
                if not is_better or dup_status == "duplicate_rejected":
                    self.send_cors_headers(409, "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({
                        "status": "rejected",
                        "message": "Beleg bereits mit gleichem/besserem Ergebnis vorhanden"
                    }, ensure_ascii=False).encode("utf-8"))
                    return

                # Bei besserer Qualität: Alte Artikel dieses Belegs aus dem Vorrat entfernen
                prev_ids = set(prev_record.get("itemIds", []))
                db["inventory"] = [
                    it for it in db["inventory"]
                    if it.get("receiptKey") != receipt_key and it.get("id") not in prev_ids
                ]

            elif dup_status == "duplicate_rejected":
                self.send_cors_headers(409, "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({
                    "status": "rejected",
                    "message": "Beleg bereits mit gleichem/besserem Ergebnis vorhanden"
                }, ensure_ascii=False).encode("utf-8"))
                return

            deposit_summary = params.get("depositSummary") or {}
            dep_items = deposit_summary.get("items", [])
            dep_total = float(deposit_summary.get("total", 0.0) or 0.0)
            if dep_total != 0.0 or dep_items:
                if "depositAccount" not in db:
                    db["depositAccount"] = {"balance": 0.0, "transactions": []}
                db["depositAccount"]["balance"] = round(db["depositAccount"].get("balance", 0.0) + dep_total, 2)
                for dep in dep_items:
                    db["depositAccount"]["transactions"].append({
                        "id": str(uuid.uuid4()),
                        "date": purchase_date or datetime.now().strftime("%d.%m.%Y"),
                        "supermarket": supermarket or "Supermarkt",
                        "type": dep.get("type", "Pfand"),
                        "amount": dep.get("amount", 0.0),
                        "description": dep.get("description", "Pfand"),
                        "timestamp": datetime.now().isoformat()
                    })

            now = datetime.now()
            imported = []
            for p in items_to_add:
                name = p.get("name", "").strip()
                if not name or is_non_food(name):
                    continue
                qty = p.get("quantity", 1)

                # Preis-Radar: Einkaufspreis protokollieren
                record_price_radar_purchase(
                    item_name=name,
                    price=p.get("price", 0.0),
                    quantity=qty,
                    supermarket=p.get("supermarket", supermarket or ""),
                    purchase_date=p.get("purchaseDate", purchase_date or "")
                )

                # Automatischer Lernprozess bei Bestätigung
                raw_name = p.get("rawName") or p.get("originalName") or name
                learn_product(
                    raw_name=raw_name,
                    clean_name=name,
                    unit=p.get("unit", "Stk."),
                    category=p.get("category", "Vorrat"),
                    storage_location=p.get("storageLocation", "Kühlschrank"),
                    supermarket=p.get("supermarket", supermarket or "")
                )

                if p.get("restockExisting"):
                    existing_id = p.get("existingId")
                    existing_item = None
                    if existing_id:
                        existing_item = next((it for it in db["inventory"] if it.get("id") == existing_id), None)
                    if not existing_item:
                        existing_item = next((it for it in db["inventory"] if it.get("name", "").strip().lower() == name.lower()), None)
                    if existing_item:
                        existing_item["quantity"] = round(existing_item.get("quantity", 0) + qty, 2)
                        if p.get("price") and p["price"] > 0:
                            existing_item["price"] = p["price"]
                        imported.append(existing_item)
                        continue

                exp_ts = p.get("expiryDate")
                if p.get("expiryDateStr"):
                    try:
                        dt = datetime.strptime(p["expiryDateStr"], "%Y-%m-%d")
                        exp_ts = int(dt.timestamp() * 1000)
                    except Exception:
                        pass
                if not exp_ts:
                    exp_ts = int((now + timedelta(days=7)).timestamp() * 1000)

                item = {
                    "id": str(uuid.uuid4()),
                    "name": name,
                    "quantity": qty,
                    "unit": p.get("unit", "Stk."),
                    "storageLocation": p.get("storageLocation", "Kühlschrank"),
                    "price": p.get("price", 0.0),
                    "supermarket": p.get("supermarket", supermarket or "Import"),
                    "expiryDate": exp_ts,
                    "purchaseDate": p.get("purchaseDate", purchase_date or ""),
                    "purchaseTime": p.get("purchaseTime", purchase_time or ""),
                    "receiptKey": receipt_key or p.get("receiptKey", ""),
                    "category": p.get("category", "Vorrat")
                }
                if p.get("barcode"):
                    item["barcode"] = p["barcode"]
                db["inventory"].append(item)
                imported.append(item)

            if receipt_key:
                history[receipt_key] = {
                    "receiptKey": receipt_key,
                    "supermarket": supermarket,
                    "purchaseDate": purchase_date,
                    "purchaseTime": purchase_time,
                    "receiptTotal": receipt_total,
                    "itemCount": len(imported),
                    "qualityScore": quality_score if quality_score > 0 else calculate_recognition_quality(imported, receipt_total),
                    "itemIds": [it["id"] for it in imported if "id" in it],
                    "timestamp": datetime.now().isoformat()
                }
                save_receipt_history(history)

            recipe_ideas = generate_instant_recipe_ideas(imported)

            save_data(db)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            res_obj = {
                "status": "ok",
                "count": len(imported),
                "items": imported,
                "recipeIdeas": recipe_ideas,
                "depositSummary": deposit_summary,
                "depositBalance": db.get("depositAccount", {}).get("balance", 0.0)
            }
            self.wfile.write(json.dumps(res_obj, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/inventory/deduplicate":
            db["inventory"], count = deduplicate_inventory_items(db["inventory"])
            save_data(db)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "mergedCount": count}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/inventory/clean-non-food":
            db["inventory"], count = clean_non_food_from_inventory(db["inventory"])
            save_data(db)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "removedCount": count}, ensure_ascii=False).encode("utf-8"))
            return

        if clean_path == "/api/inventory/clear-all":
            db["inventory"] = []
            save_data(db)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')
            return

        if clean_path == "/api/upload-receipt":
            analysis = analyze_uploaded_receipt(params)
            dup_status = analysis.get("duplicateStatus")
            receipt_key = analysis.get("receiptKey")
            supermarket = analysis.get("supermarket", "Import")
            purchase_date = analysis.get("purchaseDate", "")
            purchase_time = analysis.get("purchaseTime", "")
            receipt_total = analysis.get("receiptTotal")
            quality_score = float(analysis.get("qualityScore", 0.0) or 0.0)

            if dup_status == "duplicate_rejected":
                self.send_cors_headers(409, "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({
                    "status": "rejected",
                    "message": analysis.get("duplicateMessage", "Beleg bereits mit gleichem/besserem Ergebnis vorhanden")
                }, ensure_ascii=False).encode("utf-8"))
                return

            deposit_summary = analysis.get("depositSummary") or {}
            dep_items = deposit_summary.get("items", [])
            dep_total = float(deposit_summary.get("total", 0.0) or 0.0)
            if dep_total != 0.0 or dep_items:
                if "depositAccount" not in db:
                    db["depositAccount"] = {"balance": 0.0, "transactions": []}
                db["depositAccount"]["balance"] = round(db["depositAccount"].get("balance", 0.0) + dep_total, 2)
                for dep in dep_items:
                    db["depositAccount"]["transactions"].append({
                        "id": str(uuid.uuid4()),
                        "date": purchase_date or datetime.now().strftime("%d.%m.%Y"),
                        "supermarket": supermarket or "Supermarkt",
                        "type": dep.get("type", "Pfand"),
                        "amount": dep.get("amount", 0.0),
                        "description": dep.get("description", "Pfand"),
                        "timestamp": datetime.now().isoformat()
                    })

            history = load_receipt_history()
            if receipt_key and (dup_status == "duplicate_better" or receipt_key in history):
                prev_record = history.get(receipt_key, {})
                prev_ids = set(prev_record.get("itemIds", []))
                db["inventory"] = [
                    it for it in db["inventory"]
                    if it.get("receiptKey") != receipt_key and it.get("id") not in prev_ids
                ]

            products = analysis.get("items", [])
            imported = []

            for p in products:
                name = p.get("name", "").strip()
                if not name or is_non_food(name):
                    continue
                raw_name = p.get("rawName") or name
                record_price_radar_purchase(
                    item_name=name,
                    price=p.get("price", 0.0),
                    quantity=p.get("quantity", 1),
                    supermarket=p.get("supermarket", supermarket or ""),
                    purchase_date=p.get("purchaseDate", purchase_date or "")
                )
                learn_product(
                    raw_name=raw_name,
                    clean_name=name,
                    unit=p.get("unit", "Stk."),
                    category=p.get("category", "Vorrat"),
                    storage_location=p.get("storageLocation", "Kühlschrank"),
                    supermarket=p.get("supermarket", supermarket or "")
                )
                item = {
                    "id": str(uuid.uuid4()),
                    "name": name,
                    "quantity": p.get("quantity", 1),
                    "unit": p.get("unit", "Stk."),
                    "storageLocation": p.get("storageLocation", "Kühlschrank"),
                    "price": p.get("price", 0.0),
                    "supermarket": p.get("supermarket", supermarket),
                    "expiryDate": p.get("expiryDate", int((datetime.now() + timedelta(days=7)).timestamp() * 1000)),
                    "purchaseDate": p.get("purchaseDate", purchase_date),
                    "purchaseTime": p.get("purchaseTime", purchase_time),
                    "receiptKey": receipt_key or "",
                    "category": p.get("category", "Vorrat")
                }
                if p.get("barcode"):
                    item["barcode"] = p["barcode"]
                db["inventory"].append(item)
                imported.append({
                    "name": item["name"],
                    "quantity": item["quantity"],
                    "unit": item["unit"],
                    "storageLocation": item["storageLocation"],
                    "price": item["price"],
                    "purchaseDate": item["purchaseDate"],
                    "category": item["category"]
                })

            if receipt_key:
                history[receipt_key] = {
                    "receiptKey": receipt_key,
                    "supermarket": supermarket,
                    "purchaseDate": purchase_date,
                    "purchaseTime": purchase_time,
                    "receiptTotal": receipt_total,
                    "itemCount": len(imported),
                    "qualityScore": quality_score,
                    "itemIds": [it["id"] for it in db["inventory"] if it.get("receiptKey") == receipt_key],
                    "timestamp": datetime.now().isoformat()
                }
                save_receipt_history(history)

            save_data(db)
            self.send_cors_headers(200, "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(imported, ensure_ascii=False).encode("utf-8"))
            return


        self.send_cors_headers(404, "text/plain")
        self.end_headers()
        self.wfile.write(b"Not Found")

    def log_message(self, format, *args):
        # Sauberes Konsolen-Logging
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {args[0]} - {args[1]}")

def run_server():
    wlan_ip = get_wlan_ip()
    server_address = (HOST, PORT)
    
    # Ermögliche sofortige Wiederverwendung des Ports
    socketserver.TCPServer.allow_reuse_address = True
    
    with socketserver.TCPServer(server_address, FridgeRequestHandler) as httpd:
        print("=" * 60)
        print("🍏 Kühlschrank Profi Webservice läuft!")
        print("=" * 60)
        print(f"🌐 Host:              {HOST} (Alle Netzwerk-Schnittstellen)")
        print(f"🔌 Port:              {PORT}")
        print(f"💻 Lokal am PC:       http://localhost:{PORT}")
        print(f"📱 Für dein Smartphone: http://{wlan_ip}:{PORT}")
        print(f"🔑 PIN-Kopplungscode: {db.get('pin', '1234')}")
        print("=" * 60)
        print("Drücke Strg+C zum Beenden.")
        print("-" * 60)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nWebservice wird beendet...")

if __name__ == "__main__":
    run_server()
