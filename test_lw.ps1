$res = Invoke-WebRequest -Uri "https://www.lebensmittelwarnung.de/"
$res.Content | Select-String -Pattern "href=.(.*?rss.*?)."
$res.Content | Select-String -Pattern "href=.(.*?xml.*?)."
$res.Content | Select-String -Pattern "href=.(.*?api.*?)."
