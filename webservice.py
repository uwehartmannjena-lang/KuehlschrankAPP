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

from kassenzettel_parser import parse_receipt_text, process_uploaded_receipt


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
        ]
    }

def load_data():
    if os.path.exists(DATA_FILE):
        try:
            with open(DATA_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
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

HTML_CONTENT = """<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
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
        .form-group input, .form-group select { width: 100%; box-sizing: border-box; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; }
    </style>
</head>
<body>
    <header>
        <div>
            <h1>🍏 Kühlschrank Profi PC-Begleiter</h1>
            <span id="connection-status" style="font-size: 0.8rem; background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 10px;">🟡 Verbindung wird hergestellt...</span>
        </div>
        <nav>
            <button id="tab-inv" class="active" onclick="showTab('inv')">📦 Vorrat</button>
            <button id="tab-receipt" onclick="showTab('receipt')">🧾 Kassenbon-Import</button>
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
                     ondragover="handleDragOver(event)" 
                     ondragleave="handleDragLeave(event)" 
                     ondrop="handleFileDrop(event)">
                    <p style="font-size: 1.1rem; margin-top: 0; font-weight: bold;">📄 Ziehe eine Kassenbon-PDF oder ein Foto hierher</p>
                    <p style="color: #555; font-size: 0.9rem; margin-bottom: 0.8rem;">oder wähle eine Datei direkt von deinem Gerät aus:</p>
                    <input type="file" id="receipt-file-input" accept=".pdf,image/*,.txt,.json" style="display: none;" onchange="handleFileSelect(event)">
                    <button type="button" class="btn" onclick="document.getElementById('receipt-file-input').click()">📂 Datei auswählen (PDF / Bild)</button>
                    
                    <div style="margin: 1.5rem 0 0.5rem 0; text-align: left;">
                        <label style="font-size: 0.85rem; font-weight: bold; color: #555;">Oder Bon-Text direkt eingeben / hineinkopieren:</label>
                        <textarea id="receipt-text" rows="4" placeholder="Kassenbon-Text hier hineinkopieren (oder Datei oben ablegen)..." style="width: 100%; box-sizing: border-box; margin-top: 0.25rem; font-family: inherit; padding: 0.5rem; border-radius: 4px; border: 1px solid #ccc;"></textarea>
                    </div>
                    <button class="btn" style="width: 100%;" onclick="uploadReceiptManualText()">Kassenbon verarbeiten</button>
                </div>
                <div id="receipt-results" style="margin-top: 1rem;"></div>
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
    </main>

    <script>
        let rawInventory = [];
        let currentLocationFilter = '';
        let authToken = localStorage.getItem('companion_token') || '';

        const originalFetch = window.fetch;
        window.fetch = async function(url, options = {}) {
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
            ['inv', 'receipt', 'shop', 'recipes', 'hist'].forEach(t => {
                document.getElementById('section-' + t).style.display = t === tab ? 'block' : 'none';
                document.getElementById('tab-' + t).classList.toggle('active', t === tab);
            });
            if (tab === 'inv') loadInventory();
            if (tab === 'shop') loadShopping();
            if (tab === 'recipes') loadRecipes();
            if (tab === 'hist') loadHistory();
        }

        async function loadInventory() {
            const res = await fetch('/api/inventory');
            rawInventory = await res.json();
            renderInventory(rawInventory);
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

                return `
                    <tr>
                        <td><span class="badge ${badgeClass}">${badgeText}</span></td>
                        <td><strong>${item.name}</strong></td>
                        <td>${item.quantity} ${item.unit || 'Stk.'}</td>
                        <td>${item.storageLocation || 'Kühlschrank'}</td>
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

        function handleDragOver(e) {
            e.preventDefault();
            const zone = document.getElementById('drop-zone');
            if (zone) zone.classList.add('dragover');
        }

        function handleDragLeave(e) {
            e.preventDefault();
            const zone = document.getElementById('drop-zone');
            if (zone) zone.classList.remove('dragover');
        }

        async function handleFileDrop(e) {
            e.preventDefault();
            const zone = document.getElementById('drop-zone');
            if (zone) zone.classList.remove('dragover');
            const files = e.dataTransfer.files;
            if (files && files.length > 0) {
                await processSelectedFile(files[0]);
            }
        }

        async function handleFileSelect(e) {
            const files = e.target.files;
            if (files && files.length > 0) {
                await processSelectedFile(files[0]);
            }
        }

        async function processSelectedFile(file) {
            const div = document.getElementById('receipt-results');
            div.innerHTML = '<p style="color: #2e7d32; font-weight: bold;">⏳ Datei <strong>' + file.name + '</strong> (' + Math.round(file.size / 1024) + ' KB) wird analysiert...</p>';

            const isTextOrJson = file.name.endsWith('.txt') || file.name.endsWith('.json') || (file.type && file.type.startsWith('text/'));
            
            if (isTextOrJson) {
                const text = await file.text();
                document.getElementById('receipt-text').value = text;
                await uploadReceiptPayload({ text: text, filename: file.name });
            } else {
                // PDF oder Bild: Als Base64 übertragen (KEIN Rohcode im Textfeld!)
                document.getElementById('receipt-text').value = '📄 Kassenbon-Datei geladen: ' + file.name + ' (' + Math.round(file.size / 1024) + ' KB)';
                const reader = new FileReader();
                reader.onload = async function() {
                    const base64Data = reader.result.split(',')[1];
                    await uploadReceiptPayload({
                        filename: file.name,
                        fileType: file.type || 'application/pdf',
                        fileBase64: base64Data
                    });
                };
                reader.readAsDataURL(file);
            }
        }

        async function uploadReceiptManualText() {
            const text = document.getElementById('receipt-text').value;
            if (!text || text.startsWith('📄 Kassenbon-Datei')) return;
            const div = document.getElementById('receipt-results');
            div.innerHTML = '<p style="color: #2e7d32; font-weight: bold;">⏳ Kassenbon-Text wird verarbeitet...</p>';
            await uploadReceiptPayload({ text: text });
        }

        async function uploadReceiptPayload(payload) {
            const div = document.getElementById('receipt-results');
            try {
                const res = await fetch('/api/upload-receipt', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();
                if (!data || data.length === 0) {
                    div.innerHTML = '<p style="color: #f57c00; font-weight: bold;">⚠️ Keine Artikel im Kassenbon gefunden. Bitte überprüfe den Beleg.</p>';
                } else {
                    div.innerHTML = '<h3>Erkannte Artikel (' + data.length + '):</h3><ul style="line-height: 1.6;">' +
                        data.map(p => '<li><strong>' + p.name + '</strong> - ' + p.quantity + ' ' + (p.unit || 'Stk.') + ' - ' + (p.price ? p.price.toFixed(2) : '0.00') + ' € <small style="color: #666;">(' + (p.storageLocation || 'Kühlschrank') + ')</small></li>').join('') +
                        '</ul><p style="color: green; font-weight: bold;">✓ Erfolgreich in den Kühlschrank übernommen!</p>';
                }
                loadInventory();
            } catch (err) {
                div.innerHTML = '<p style="color: red; font-weight: bold;">❌ Fehler beim Verarbeiten: ' + err.message + '</p>';
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

        if clean_path == "/api/upload-receipt":
            products = process_uploaded_receipt(params)
            imported = []

            for p in products:
                item = {
                    "id": str(uuid.uuid4()),
                    "name": p["name"],
                    "quantity": p.get("quantity", 1),
                    "unit": p.get("unit", "Stk."),
                    "storageLocation": p.get("storageLocation", "Kühlschrank"),
                    "price": p.get("price", 0.0),
                    "supermarket": p.get("supermarket", "Import"),
                    "expiryDate": p.get("expiryDate", int((datetime.now() + timedelta(days=7)).timestamp() * 1000))
                }
                if p.get("barcode"):
                    item["barcode"] = p["barcode"]
                db["inventory"].append(item)
                imported.append({
                    "name": p["name"],
                    "quantity": p.get("quantity", 1),
                    "unit": p.get("unit", "Stk."),
                    "storageLocation": p.get("storageLocation", "Kühlschrank"),
                    "price": p.get("price", 0.0)
                })
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
