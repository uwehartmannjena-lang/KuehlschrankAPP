# -*- coding: utf-8 -*-
import sys
import pypdf
from kassenzettel_parser import parse_receipt_text

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def test_pdf(pdf_path):
    print(f"\n==========================================")
    print(f"Lese PDF: {pdf_path}")
    reader = pypdf.PdfReader(pdf_path)
    text = ""
    for page in reader.pages:
        text += page.extract_text() or ""
    
    print(f"Extrahierter Text: {len(text)} Zeichen")
    items = parse_receipt_text(text)
    print(f"Erkannte Artikel ({len(items)}):")
    for it in items:
        print(f"  * {it['name']}: {it['quantity']}x {it['unit']} für {it['price']} € ({it['storageLocation']})")
    return items

if __name__ == "__main__":
    test_pdf("test_belege/20260726_Kassenzettel Groß Kaufland.pdf")
    test_pdf("test_belege/Netto_Kassenbon_20250816-093409.pdf")
