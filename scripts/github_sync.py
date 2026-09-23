import requests
import os
import json
import base64

# Konfiguration
GITHUB_TOKEN = "DEIN_GITHUB_TOKEN"
REPO_OWNER = "uwehartmannjena-lang"
REPO_NAME = "KuehlschrankApp"
API_BASE_URL = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}"

headers = {
    "Authorization": f"token {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v3+json"
}

def create_release(tag_name, body):
    url = f"{API_BASE_URL}/releases"
    data = {
        "tag_name": tag_name,
        "name": f"Release {tag_name}",
        "body": body,
        "draft": False,
        "prerelease": False
    }
    response = requests.post(url, headers=headers, json=data)
    return response.json()

def upload_backup(file_path):
    with open(file_path, "rb") as f:
        content = base64.b64encode(f.read()).decode("utf-8")
    
    file_name = os.path.basename(file_path)
    url = f"{API_BASE_URL}/contents/backups/{file_name}"
    
    data = {
        "message": f"Auto-Backup: {file_name}",
        "content": content
    }
    
    response = requests.put(url, headers=headers, json=data)
    return response.json()

if __name__ == "__main__":
    print("GitHub Sync Script bereit.")
    # Beispiel-Aufruf:
    # print(create_release("v1.0.1", "Initialer Profi-Release"))
