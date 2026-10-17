import urllib.request
import json

# TRAGE HIER DEINEN NEUEN SCHLÜSSEL EIN (Er muss mit AIzaSy anfangen!)
API_KEY = "AIzaSyALZQ6B82ECoPLL3WYy86P283aPW-BmQPI"

url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={API_KEY}"
data = {"contents": [{"parts": [{"text": "Hallo, antworte mir bitte mit einem kurzen 'Ja, ich bin bereit!'."}]}]}
headers = {'Content-Type': 'application/json'}

print("Sende Test-Anfrage an Google...")
try:
    req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers, method='POST')
    with urllib.request.urlopen(req) as response:
        res = json.loads(response.read().decode('utf-8'))
        print("\n✅ ERFOLG! Die KI antwortet:")
        print(res['candidates'][0]['content']['parts'][0]['text'])
except Exception as e:
    print("\n❌ FEHLER:", e)