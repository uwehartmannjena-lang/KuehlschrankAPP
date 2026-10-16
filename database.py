# -*- coding: utf-8 -*-
import sqlite3
import os
import uuid
import time
from typing import List, Dict, Any, Optional

class FridgeDatabase:
    """
    Verwaltet die lokale SQLite-Datenbank fuer Belege und Lebensmittel.
    Vollstaendig kompatibel mit Android Room (Prepackaged Database).
    """

    def __init__(self, db_path: str = "fridge_database.db"):
        self.db_path = db_path
        self.init_database()

    def get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON;")
        return conn

    def init_database(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()

            # Room-Master-Tabelle zur Validierung der Prepackaged Database
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS room_master_table (
                id INTEGER PRIMARY KEY,
                identity_hash TEXT
            );
            """)
            cursor.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, 'aa5247d472fabb9ffef51233392f274c');")

            # Tabelle: receipts (Android Room Entity: Receipt)
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS receipts (
                id TEXT NOT NULL PRIMARY KEY,
                uri TEXT NOT NULL,
                supermarket TEXT,
                date INTEGER NOT NULL,
                total REAL NOT NULL DEFAULT 0.0,
                note TEXT NOT NULL DEFAULT ''
            );
            """)

            # Tabelle: fridge_items (Android Room Entity: FridgeItem)
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS fridge_items (
                id TEXT NOT NULL PRIMARY KEY,
                item_name TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 1,
                unit TEXT NOT NULL DEFAULT 'Stk.',
                category TEXT NOT NULL DEFAULT 'Sonstiges',
                expiry_date INTEGER,
                min_stock INTEGER NOT NULL DEFAULT 0,
                storage_location TEXT NOT NULL DEFAULT 'Kühlschrank',
                price REAL NOT NULL DEFAULT 0.0,
                brand TEXT,
                kcal INTEGER NOT NULL DEFAULT 0,
                nutriScore TEXT NOT NULL DEFAULT '',
                allergens TEXT NOT NULL DEFAULT '',
                isOrganic INTEGER NOT NULL DEFAULT 0,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                imageUrl TEXT,
                barcode TEXT,
                hasOffer INTEGER NOT NULL DEFAULT 0,
                purchaseDate INTEGER NOT NULL,
                ecoScore TEXT NOT NULL DEFAULT '',
                isFrozen INTEGER NOT NULL DEFAULT 0,
                defrostTimestamp INTEGER,
                lastPrice REAL NOT NULL DEFAULT 0.0,
                carbonFootprint REAL NOT NULL DEFAULT 0.0,
                boundingBox TEXT,
                householdId TEXT NOT NULL DEFAULT 'default',
                lastModified INTEGER NOT NULL,
                sugar REAL NOT NULL DEFAULT 0.0,
                salt REAL NOT NULL DEFAULT 0.0,
                protein REAL NOT NULL DEFAULT 0.0,
                fat REAL NOT NULL DEFAULT 0.0,
                isVegan INTEGER NOT NULL DEFAULT 0,
                isVegetarian INTEGER NOT NULL DEFAULT 0,
                isGlutenFree INTEGER NOT NULL DEFAULT 0,
                isLactoseFree INTEGER NOT NULL DEFAULT 0,
                isLowCarb INTEGER NOT NULL DEFAULT 0,
                freezerDrawer INTEGER NOT NULL DEFAULT 0,
                openedDate INTEGER,
                consumptionAfterOpeningDays INTEGER,
                isRecallChecked INTEGER NOT NULL DEFAULT 0,
                lastRecallStatus TEXT NOT NULL DEFAULT 'OK',
                isDonationCandidate INTEGER NOT NULL DEFAULT 0,
                sharedWithUserId TEXT,
                importHash TEXT,
                receiptId TEXT,
                supermarket TEXT,
                nfcTagId TEXT,
                favoriteColor INTEGER
            );
            """)
            cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_fridge_items_importHash ON fridge_items(importHash);")

            # Tabelle: purchase_history (Android Room Entity: PurchaseHistoryEntry)
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS purchase_history (
                id TEXT NOT NULL PRIMARY KEY,
                productName TEXT NOT NULL,
                purchaseDate TEXT NOT NULL,
                storeName TEXT NOT NULL,
                price REAL NOT NULL,
                timestamp INTEGER NOT NULL
            );
            """)
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_purchase_history_product_name ON purchase_history(productName);")

            # Tabelle: market_dictionary (Android Room Entity: MarketProductEntry)
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS market_dictionary (
                id TEXT NOT NULL PRIMARY KEY,
                market TEXT NOT NULL,
                market_id TEXT,
                receiptPattern TEXT NOT NULL,
                cleanName TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'SONSTIGES',
                defaultStorage TEXT NOT NULL DEFAULT 'Kühlschrank',
                defaultShelfLifeDays INTEGER NOT NULL DEFAULT 7,
                purchase_count INTEGER NOT NULL DEFAULT 0,
                synonyms TEXT
            );
            """)
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_market ON market_dictionary(market);")
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_market_id ON market_dictionary(market_id);")
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_receipt_pattern ON market_dictionary(receiptPattern);")
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_clean_name ON market_dictionary(cleanName);")
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_receipt_pattern_nocase ON market_dictionary(receiptPattern COLLATE NOCASE);")
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_clean_name_nocase ON market_dictionary(cleanName COLLATE NOCASE);")

            conn.commit()

    def insert_receipt(self, receipt_data: Dict[str, Any], uri: str = "") -> str:
        """
        Speichert einen ausgelesenen Beleg inklusive aller Artikel und Preishistorie ab.
        """
        receipt_id = str(uuid.uuid4())
        supermarket = receipt_data.get("supermarket") or "Unbekannt"
        date_str = receipt_data.get("purchaseDate") or ""
        total = float(receipt_data.get("receiptTotal") or 0.0)

        # Datum in Unix-Timestamp (ms) konvertieren
        now_ms = int(time.time() * 1000)
        purchase_timestamp = now_ms
        if date_str:
            try:
                parts = date_str.strip().split(".")
                if len(parts) == 3:
                    day, month, year = int(parts[0]), int(parts[1]), int(parts[2])
                    struct_time = time.strptime(f"{year:04d}-{month:02d}-{day:02d}", "%Y-%m-%d")
                    purchase_timestamp = int(time.mktime(struct_time) * 1000)
            except Exception:
                purchase_timestamp = now_ms

        with self.get_connection() as conn:
            cursor = conn.cursor()

            # Beleg-Kopf einfuegen
            cursor.execute("""
            INSERT INTO receipts (id, uri, supermarket, date, total, note)
            VALUES (?, ?, ?, ?, ?, ?);
            """, (receipt_id, uri, supermarket, purchase_timestamp, total, f"Importiert von {supermarket}"))

            # Artikel verarbeiten
            items = receipt_data.get("items") or []
            for item in items:
                item_id = str(uuid.uuid4())
                name = (item.get("name") or "").strip()
                if not name:
                    continue

                raw_name = (item.get("rawName") or name).strip()
                quantity = int(item.get("quantity") or 1)
                unit = item.get("unit") or "Stk."
                category = item.get("category") or "Sonstiges"
                storage_location = item.get("storageLocation") or "Kühlschrank"
                price = float(item.get("price") or item.get("totalPrice") or 0.0)
                brand = item.get("brand")

                # MHD berechnen (Kaufdatum + mhdDays)
                mhd_days = int(item.get("mhdDays") or 7)
                expiry_date = purchase_timestamp + (mhd_days * 86400 * 1000)

                # Eindeutiger Import-Hash gegen Duplikate
                import_hash = f"{supermarket}_{date_str}_{name}_{price:.2f}"

                cursor.execute("""
                INSERT OR REPLACE INTO fridge_items (
                    id, item_name, quantity, unit, category, expiry_date, min_stock,
                    storage_location, price, brand, kcal, nutriScore, allergens,
                    isOrganic, isFavorite, notes, imageUrl, barcode, hasOffer,
                    purchaseDate, ecoScore, isFrozen, defrostTimestamp, lastPrice,
                    carbonFootprint, boundingBox, householdId, lastModified, sugar,
                    salt, protein, fat, isVegan, isVegetarian, isGlutenFree,
                    isLactoseFree, isLowCarb, freezerDrawer, openedDate,
                    consumptionAfterOpeningDays, isRecallChecked, lastRecallStatus,
                    isDonationCandidate, sharedWithUserId, importHash, receiptId,
                    supermarket, nfcTagId, favoriteColor
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, 0,
                    ?, ?, ?, 0, '', '',
                    0, 0, ?, NULL, NULL, 0,
                    ?, '', 0, NULL, ?,
                    0.0, NULL, 'default', ?, 0.0,
                    0.0, 0.0, 0.0, 0, 0, 0,
                    0, 0, 0, NULL,
                    ?, 0, 'OK',
                    0, NULL, ?, ?,
                    ?, NULL, NULL
                );
                """, (
                    item_id, name, quantity, unit, category, expiry_date,
                    storage_location, price, brand, f"Bon-Text: {raw_name}",
                    purchase_timestamp, price, now_ms,
                    mhd_days, import_hash, receipt_id,
                    supermarket
                ))

                # Kaufhistorie fuer Preisverlauf & Statistik
                history_id = str(uuid.uuid4())
                cursor.execute("""
                INSERT INTO purchase_history (id, productName, purchaseDate, storeName, price, timestamp)
                VALUES (?, ?, ?, ?, ?, ?);
                """, (history_id, name, date_str or time.strftime("%d.%m.%Y"), supermarket, price, purchase_timestamp))

                # Markt-Lexikon aktualisieren / Zaehler erhoehen
                cursor.execute("""
                INSERT INTO market_dictionary (
                    id, market, market_id, receiptPattern, cleanName, category,
                    defaultStorage, defaultShelfLifeDays, purchase_count, synonyms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                ON CONFLICT(id) DO UPDATE SET purchase_count = purchase_count + 1;
                """, (
                    str(uuid.uuid4()), supermarket.lower(), supermarket.lower(),
                    raw_name, name, category, storage_location, mhd_days, raw_name
                ))

            conn.commit()
        return receipt_id

    def export_for_room(self, target_path: str = "app/src/main/assets/fridge_database.db") -> str:
        """
        Formatiert und exportiert die SQLite-Datenbank so, dass sie direkt
        als Prepackaged Database in Android Room via createFromAsset geladen werden kann.
        Fuehrt WAL-Checkpoint und VACUUM durch, um eine saubere Single-File-DB zu garantieren.
        """
        os.makedirs(os.path.dirname(os.path.abspath(target_path)), exist_ok=True)
        
        with self.get_connection() as conn:
            # WAL zuruecksetzen auf DELETE-Modus fuer maximale Kompatibilitaet im Android APK/Asset-Ordner
            conn.execute("PRAGMA journal_mode = DELETE;")
            conn.execute("PRAGMA wal_checkpoint(FULL);")
            conn.execute("VACUUM;")
            conn.commit()

        # Kopieren in den Zielpfad (z.B. Android assets)
        import shutil
        shutil.copy2(self.db_path, target_path)
        return target_path

    def get_receipts(self) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM receipts ORDER BY date DESC;")
            return [dict(row) for row in cursor.fetchall()]

    def get_fridge_items(self, receipt_id: Optional[str] = None) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if receipt_id:
                cursor.execute("SELECT * FROM fridge_items WHERE receiptId = ? ORDER BY item_name ASC;", (receipt_id,))
            else:
                cursor.execute("SELECT * FROM fridge_items ORDER BY lastModified DESC;")
            return [dict(row) for row in cursor.fetchall()]

    def find_matching_product(self, term: str, market: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """
        Blitzschnelle Indizierungssuche ueber 'cleanName' oder 'receiptPattern'.
        Nutzt B-Tree-Indizes fuer O(log N) Suchzeiten.
        """
        term = term.strip()
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if market:
                cursor.execute("""
                    SELECT * FROM market_dictionary
                    WHERE (market = ? OR market = 'general')
                      AND (receiptPattern = ? COLLATE NOCASE OR cleanName = ? COLLATE NOCASE)
                    ORDER BY purchase_count DESC LIMIT 1;
                """, (market.lower(), term, term))
            else:
                cursor.execute("""
                    SELECT * FROM market_dictionary
                    WHERE receiptPattern = ? COLLATE NOCASE OR cleanName = ? COLLATE NOCASE
                    ORDER BY purchase_count DESC LIMIT 1;
                """, (term, term))
            row = cursor.fetchone()
            return dict(row) if row else None

    def vorab_abgleich(self, bon_zeilen: List[str], market: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Fuehrt einen schnellen Vorab-Abgleich aller extrahierten Belegzeilen
        gegen die indizierten 'receiptPattern'- und 'cleanName'-Spalten durch.
        """
        ergebnisse = []
        for zeile in bon_zeilen:
            treffer = self.find_matching_product(zeile, market=market)
            if treffer:
                ergebnisse.append({
                    "original": zeile,
                    "cleanName": treffer["cleanName"],
                    "category": treffer["category"],
                    "defaultStorage": treffer["defaultStorage"],
                    "defaultShelfLifeDays": treffer["defaultShelfLifeDays"],
                    "match": True
                })
            else:
                ergebnisse.append({
                    "original": zeile,
                    "cleanName": zeile,
                    "category": "Sonstiges",
                    "defaultStorage": "Kühlschrank",
                    "defaultShelfLifeDays": 7,
                    "match": False
                })
        return ergebnisse
