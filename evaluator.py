import json
from pathlib import Path


def lade_json(dateipfad: Path) -> dict:
    with open(dateipfad, "r", encoding="utf-8") as f:
        return json.load(f)


def finde_wert_rekursiv(daten, schluessel_kandidaten, standard=None):
    """Sucht Schlüssel sowohl auf oberster Ebene als auch verschachtelt."""
    if not isinstance(daten, dict):
        return standard

    # 1. Direkte Suche (Groß-/Kleinschreibung ignorieren)
    for ziel in schluessel_kandidaten:
        for k, v in daten.items():
            if k.lower() == ziel.lower():
                return v

    # 2. Verschachtelte Unter-Objekte durchsuchen (z. B. "receipt", "kassenbon", "data")
    for k, v in daten.items():
        if isinstance(v, dict):
            treffer = finde_wert_rekursiv(v, schluessel_kandidaten, None)
            if treffer is not None:
                return treffer
    return standard


def wandle_in_float(wert) -> float:
    if wert is None:
        return 0.0
    if isinstance(wert, (int, float)):
        return float(wert)
    text = str(wert).strip().replace("€", "").replace(" ", "")
    ist_negativ = "-" in text
    text = text.replace("-", "").replace(",", ".")
    try:
        zahl = float(text)
        return -zahl if ist_negativ else zahl
    except ValueError:
        return 0.0


def werte_kassenbon_aus(daten: dict) -> dict:
    # Falls das JSON ein Wrapper-Objekt hat (z. B. {"receipt": {...}}), dieses nutzen
    fokus = daten
    for wrapper in ["receipt", "kassenbon", "bon", "data", "result", "beleg"]:
        for k, v in daten.items():
            if k.lower() == wrapper and isinstance(v, dict):
                fokus = v
                break

    markt_kandidaten = [
        "markt",
        "market",
        "supermarket",
        "supermarkt",
        "store",
        "haendler",
        "vendor",
        "name",
    ]
    summen_kandidaten = [
        "summe",
        "gesamt",
        "gesamtbetrag",
        "total",
        "endbetrag",
        "gesamt_preis",
        "total_amount",
        "totalamount",
        "total_price",
        "grand_total",
        "sum",
    ]
    posten_kandidaten = [
        "artikel",
        "items",
        "produkte",
        "products",
        "posten",
        "lines",
        "positions",
        "waren",
    ]
    preis_kandidaten = [
        "preis",
        "price",
        "gesamtpreis",
        "betrag",
        "amount",
        "single_price",
        "item_price",
    ]

    markt = finde_wert_rekursiv(fokus, markt_kandidaten, "UNBEKANNT")
    bon_summe = wandle_in_float(
        finde_wert_rekursiv(fokus, summen_kandidaten, 0.0)
    )
    posten_liste = (
        finde_wert_rekursiv(fokus, posten_kandidaten, []) or []
    )

    berechnete_summe = 0.0
    if isinstance(posten_liste, list):
        for eintrag in posten_liste:
            if isinstance(eintrag, dict):
                preis_roh = finde_wert_rekursiv(
                    eintrag, preis_kandidaten, 0.0
                )
                berechnete_summe += wandle_in_float(preis_roh)

    berechnete_summe = round(berechnete_summe, 2)
    bon_summe = round(bon_summe, 2)
    diff = round(berechnete_summe - bon_summe, 2)

    ist_korrekt = abs(diff) <= 0.01 and bon_summe > 0

    return {
        "markt": str(markt).upper(),
        "posten": len(posten_liste) if isinstance(posten_liste, list) else 0,
        "berechnet": berechnete_summe,
        "bon_summe": bon_summe,
        "diff": diff,
        "status": "OK" if ist_korrekt else "FEHLER",
        "rohe_keys": list(fokus.keys()) if isinstance(fokus, dict) else [],
    }


def main():
    ordner = Path("Kassenzettel json")
    if not ordner.exists():
        ordner = Path(".")

    json_dateien = sorted(list(ordner.glob("*.json")))
    if not json_dateien:
        print("Keine JSON-Dateien gefunden.")
        return

    print(
        f"\n| {'Datei':<16} | {'Markt':<10} | {'Posten':<6} | {'Berechnet':<10} | {'Bon-Summe':<10} | {'Diff':<8} | {'Status':<6} |"
    )
    print("-" * 80)

    erfolgreich = 0
    for datei in json_dateien:
        try:
            daten = lade_json(datei)
            erg = werte_kassenbon_aus(daten)
            if erg["status"] == "OK":
                erfolgreich += 1
            print(
                f"| {datei.name:<16} | {erg['markt'][:10]:<10} | {erg['posten']:>6} | {erg['berechnet']:>8.2f}€ | {erg['bon_summe']:>8.2f}€ | {erg['diff']:>6.2f}€ | {erg['status']:<6} |"
            )
        except Exception:
            print(
                f"| {datei.name:<16} | {'FEHLER':<10} | {'-':<6} | {'-':<10} | {'-':<10} | {'-':<8} | {'ERR':<6} |"
            )

    quote = (erfolgreich / len(json_dateien) * 100) if json_dateien else 0
    print(
        f"\nGesamt-Erfolgsquote: {quote:.1f}% ({erfolgreich} / {len(json_dateien)} Bons korrekt)"
    )


if __name__ == "__main__":
    main()