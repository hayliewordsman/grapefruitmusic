#!/usr/bin/env python3
"""SPDX-License-Identifier: GPL-3.0-or-later
Refresh the bundled Mapsforge catalogue; never bulk-download map or raster tiles.
"""
import concurrent.futures, html, json, pathlib, re, urllib.request
BASE = 'https://download.mapsforge.org/maps/v5/'
OUT = pathlib.Path(__file__).resolve().parents[2] / 'maps/src/main/assets/countries.json'
NAMES = {'us': 'United States', 'great-britain': 'United Kingdom', 'ireland-and-northern-ireland': 'Ireland and Northern Ireland', 'czech-republic': 'Czechia', 'russia': 'Russia'}
def name(s):
    return NAMES.get(s, s.replace('-', ' ').replace('_', ' ').title())
def crawl(path):
    req = urllib.request.Request(BASE + path, headers={'User-Agent': 'GrapefruitMaps-catalogue/1.0 (github.com/hayliewordsman/grapefruitmusic)'})
    text = urllib.request.urlopen(req, timeout=45).read().decode('latin1')
    files, dirs = [], []
    for row in re.findall(r'<tr>(.*?)</tr>', text, re.S):
        m = re.search(r'href="([^"?]+)"', row)
        if not m: continue
        link = html.unescape(m[1])
        if link.startswith('/') or '..' in link: continue
        if link.endswith('/'):
            dirs.append(path + link)
        elif link.endswith('.map'):
            cells = re.findall(r'<td[^>]*>(.*?)</td>', row)
            files.append((path + link, re.sub('<[^>]+>', '', cells[3]).strip()))
    return files, dirs
pending = [x + '/' for x in ['africa','asia','australia-oceania','central-america','europe','north-america','russia','south-america']]
rows = []
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    while pending:
        batch, pending = pending, []
        for files, dirs in pool.map(crawl, batch):
            pending.extend(dirs)
            for path, size in files:
                bits = path[:-4].split('/')
                region = name(bits[-1])
                country = name(bits[1] if len(bits)>2 else bits[-1])
                if bits[0] == 'russia': country = 'Russia'
                if bits[1].startswith('us-'): country = 'United States'
                if bits[1]=='brazil': country='Brazil'
                rows.append(dict(country=country, region=region, path=path, size=size))
rows.sort(key=lambda r:(r['country'].casefold(),r['region'].casefold()))
OUT.write_text(json.dumps(rows, ensure_ascii=False, indent=2)+'\n')
print(f'{len(rows)} maps, {len(set(r["country"] for r in rows))} country/territory groups')
