import urllib.request, re
try:
    html = urllib.request.urlopen('https://www.lebensmittelwarnung.de/').read().decode('utf-8')
    print(re.findall(r'href=[\'\"]([^\'\"]*rss[^\'\"]*)[\'\"]', html, re.I))
except Exception as e:
    print(e)
