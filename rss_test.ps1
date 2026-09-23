$html = Invoke-WebRequest -Uri "https://www.lebensmittelwarnung.de/DE/Home/home_node.html"
$html.Content | Select-String -Pattern "href=.([^>]+rss[^>]+)." | Out-Host
