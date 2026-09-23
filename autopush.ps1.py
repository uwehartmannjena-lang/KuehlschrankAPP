while ($true) {
$status = git status --porcelain
if ($status) {
$zeit = Get-Date -Format "dd.MM.yyyy HH:mm:ss"
git add .
git commit -m "Automatisches Backup: $zeit"
git push origin master
Write-Host "Änderungen hochgeladen um $zeit" -ForegroundColor Green
}
Start-Sleep -Seconds 300 # Prüft alle 5 Minuten (300 Sekunden)
}