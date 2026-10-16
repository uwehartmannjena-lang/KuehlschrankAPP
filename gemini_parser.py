# -*- coding: utf-8 -*-
import os
import sys
import json
import time
import base64
from typing import Dict, Any, Optional
from dotenv import load_dotenv

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# API-Schluessel strikt aus externer .env laden
load_dotenv()

from database import FridgeDatabase

MODEL_NAME = "gemini-2.5-flash"

class GeminiReceiptParser:
    """
    Kassenbon-Parser basierend auf dem Modell gemini-2.5-flash.
    Unterstuetzt Bilder (PNG/JPG/WEBP), PDF-Dokumente und reinen OCR-Text.
    Speichert erkannte Belege direkt in einer fuer Android Room kompatiblen SQLite-Datenbank.
    """

    def __init__(self, db: Optional[FridgeDatabase] = None):
        self.api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
        if not self.api_key:
            raise ValueError(
                "Kein Gemini API-Schlüssel gefunden! Bitte 'GEMINI_API_KEY' in der .env Datei konfigurieren."
            )
        self.endpoint = (
            f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL_NAME}:generateContent?key={self.api_key}"
        )
        self.db = db or FridgeDatabase()

    def _get_system_prompt(self) -> str:
        return (
            "Du bist ein praeziser KI-Experte fuer die Extraktion deutscher Kassenbons und digitaler E-Bons.\n"
            "Extrahiere Supermarkt-Name, Kaufdatum (Format DD.MM.YYYY), Kaufzeit (HH:MM), Gesamtsumme (Float) "
            "und alle gekauften Lebensmittel und Haushaltsartikel.\n"
            "Verrechne Rabatte und Coupons in den Endpreis (totalPrice). "
            "Ignoriere Pfand, Kartenzahlung, Summenzeilen, Steuern und TSE-Codes vollstaendig.\n"
            "Antworte AUSSCHLIESSLICH als valides JSON-Objekt ohne Markdown-Fences:\n"
            "{\n"
            '  "supermarket": "Kaufland",\n'
            '  "purchaseDate": "20.07.2026",\n'
            '  "purchaseTime": "08:16",\n'
            '  "receiptTotal": 48.60,\n'
            '  "items": [\n'
            '    {\n'
            '      "name": "Rinderhackfleisch",\n'
            '      "rawName": "Rinderhackfleisch",\n'
            '      "quantity": 1,\n'
            '      "unit": "Packung",\n'
            '      "price": 4.99,\n'
            '      "totalPrice": 4.99,\n'
            '      "category": "Fleisch & Fisch",\n'
            '      "storageLocation": "Kühlschrank",\n'
            '      "mhdDays": 3,\n'
            '      "brand": null\n'
            '    }\n'
            '  ]\n'
            "}"
        )

    def _call_gemini(self, parts: list) -> Dict[str, Any]:
        payload = {
            "contents": [{"parts": parts}],
            "generationConfig": {
                "response_mime_type": "application/json",
                "temperature": 0.1,
                "thinkingConfig": {
                    "thinkingBudget": 0
                }
            }
        }

        headers = {
            "Content-Type": "application/json",
            "User-Agent": "KuehlschrankApp-GeminiParser/1.0"
        }

        import requests
        last_error = None
        for attempt in range(3):
            try:
                resp = requests.post(
                    self.endpoint,
                    json=payload,
                    headers=headers,
                    timeout=60
                )
                if resp.status_code != 200:
                    raise RuntimeError(f"Gemini API HTTP {resp.status_code}: {resp.text}")

                res_data = resp.json()
                text_out = res_data["candidates"][0]["content"]["parts"][0]["text"].strip()
                if text_out.startswith("```json"):
                    text_out = text_out[7:]
                if text_out.startswith("```"):
                    text_out = text_out[3:]
                if text_out.endswith("```"):
                    text_out = text_out[:-3]
                return json.loads(text_out.strip())
            except Exception as e:
                last_error = e
                time.sleep(1.5 * (attempt + 1))

        raise RuntimeError(f"Fehler bei der Kommunikation mit Gemini nach 3 Versuchen: {last_error}")

    def _prepare_image(self, image_path: str) -> tuple[str, str]:
        """
        Optimiert Bilder fuer den Gemini-Upload (skaliert zu grosse Bilder herunter).
        Liefert (mime_type, base64_str).
        """
        try:
            from PIL import Image
            import io
            with Image.open(image_path) as img:
                max_dim = 1800
                if max(img.size) > max_dim:
                    img.thumbnail((max_dim, max_dim), Image.Resampling.LANCZOS)
                
                buf = io.BytesIO()
                if img.mode != "RGB":
                    img = img.convert("RGB")
                img.save(buf, format="JPEG", quality=85, optimize=True)
                b64 = base64.b64encode(buf.getvalue()).decode("utf-8")
                return "image/jpeg", b64
        except Exception:
            with open(image_path, "rb") as f:
                b64 = base64.b64encode(f.read()).decode("utf-8")
            mime = "image/jpeg" if image_path.lower().endswith((".jpg", ".jpeg")) else "image/png"
            return mime, b64

    def parse_text(self, receipt_text: str, uri: str = "") -> Dict[str, Any]:
        """
        Extrahiert Belegdaten aus reinem Text oder OCR-Ergebnissen und speichert sie in SQLite.
        """
        prompt = f"{self._get_system_prompt()}\n\nKASSENBON-TEXT:\n{receipt_text}"
        parts = [{"text": prompt}]
        result = self._call_gemini(parts)
        receipt_id = self.db.insert_receipt(result, uri=uri)
        result["db_receipt_id"] = receipt_id
        return result

    def parse_image(self, image_path: str) -> Dict[str, Any]:
        """
        Extrahiert Belegdaten aus einer Bilddatei (PNG/JPEG/WEBP) und speichert sie in SQLite.
        """
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Bilddatei nicht gefunden: {image_path}")

        mime_type, img_b64 = self._prepare_image(image_path)

        parts = [
            {"text": self._get_system_prompt()},
            {"inline_data": {"mime_type": mime_type, "data": img_b64}}
        ]
        result = self._call_gemini(parts)
        receipt_id = self.db.insert_receipt(result, uri=image_path)
        result["db_receipt_id"] = receipt_id
        return result

    def parse_pdf(self, pdf_path: str) -> Dict[str, Any]:
        """
        Extrahiert Belegdaten aus einem PDF-Dokument (digitaler E-Bon oder Scan).
        """
        if not os.path.exists(pdf_path):
            raise FileNotFoundError(f"PDF-Datei nicht gefunden: {pdf_path}")

        text = ""
        try:
            import pypdf
            reader = pypdf.PdfReader(pdf_path)
            for page in reader.pages:
                text += page.extract_text() or ""
        except ImportError:
            pass

        if text.strip():
            return self.parse_text(text, uri=pdf_path)

        # Fallback fuer rein bildbasierte PDFs: erste Seiten rendern oder binary mitsenden
        with open(pdf_path, "rb") as f:
            pdf_b64 = base64.b64encode(f.read()).decode("utf-8")

        parts = [
            {"text": self._get_system_prompt()},
            {"inline_data": {"mime_type": "application/pdf", "data": pdf_b64}}
        ]
        result = self._call_gemini(parts)
        receipt_id = self.db.insert_receipt(result, uri=pdf_path)
        result["db_receipt_id"] = receipt_id
        return result

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Verwendung: py gemini_parser.py <pfad_zu_kassenbon_bild_oder_pdf>")
        sys.exit(1)

    file_input = sys.argv[1]
    parser = GeminiReceiptParser()
    print(f"Analysiere Datei mit Modell {MODEL_NAME}: {file_input} ...")

    if file_input.lower().endswith(".pdf"):
        res = parser.parse_pdf(file_input)
    else:
        res = parser.parse_image(file_input)

    print("\nErfolgreich extrahiert und in Room-kompatibler SQLite-Datenbank gespeichert:")
    print(json.dumps(res, ensure_ascii=False, indent=2))
