#!/usr/bin/env python3
"""Adds reviewed channels to Grapefruit FAST's catalog from official first-party metadata only:

- Xumo's own guide (valencia-app-mds.xumo.com): channel -> live asset -> SIMULCAST providers.sources,
  as for beta 1's Xumo channels. DRM sources are refused.
- DW's own live page (www.dw.com/graph-api livestream data): DW English's hlsVideoSrc.
- ABC Australia's own News Channel page (www.abc.net.au/news/newschannel): its live player source.
- CPAC's own home page (www.cpac.ca): its live player's HLS master, recorded from the player's requests
  in a headless browser (beta 8; the page's JW Player builds it at run time, so it isn't in the HTML).
- Cheddar's own home page (www.cheddar.com): its live player's stream (PoWa id "cheddar-live"), found
  by opening the page in a headless browser and recording what its player loads, then read from
  the page's own player setup. Xumo's copy (US) is kept as a fallback.

No community playlists, no proxies, no location or ad workarounds. Provider ads remain.
Run from repo root: python3 tools/fast/sources.py  (then tools/fast/check_streams.py)

Bias review for news (no right-wing or partisan news; nothing News Corp or Fox owned, no Piers
Morgan): see NEWS_REVIEW below and docs/fast/catalog-sources.json.
"""
import json, pathlib, time, urllib.parse, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CATALOG = ROOT / 'fast/src/main/assets/channels.json'
SOURCES = ROOT / 'docs/fast/catalog-sources.json'
UA = 'GrapefruitFAST/1.0-beta-5 (source review)'
APP_VERSION = '1.0-beta-5'
XUMO = 'https://valencia-app-mds.xumo.com/v2'

# Optional Xumo ad macros, filled as for beta 1: Android/app identity, limit-ad-tracking, a zero
# advertising ID, unknown device, no coordinates or other targeting. Provider keys and SSAI tags kept.
MACROS = {
    '[PLATFORM]': 'android', '[IFA_TYPE]': '', '[IFA]': '00000000-0000-0000-0000-000000000000',
    '[AMZN_APP_ID]': '', '[LAT]': '', '[LON]': '', '[OS]': 'Android', '[OS_VERSION]': '4.2.2',
    '[IS_LAT]': '1', '[CCPA_Value]': '1---', '[IAB_content_category]': '', '[content_language]': 'en',
    '[content_rating]': '', '[device_make]': 'unknown', '[device_model]': 'unknown',
    '[publica_site_id]': '', '[APP_VERSION]': APP_VERSION,
    '[app_bundle]': 'io.github.hayliewordsman.grapefruitfast', '[app_store_url]': '',
    # Optional content-targeting fields some channels add: left blank, as the others.
    '[CONTENT_IMDB_GENRE]': '', '[content_genre]': '', '[PROVIDER_NAME]': '', '[ASSET_ID]': '', '[PROVIDER_ID]': '',
    # The same fields, spelled in capitals by some providers.
    '[APP_BUNDLE]': 'io.github.hayliewordsman.grapefruitfast', '[APP_STORE_URL]': '', '[APP_STOREURL]': '',
    '[DEVICE_MAKE]': 'unknown', '[DEVICE_MODEL]': 'unknown',
}

# (catalog id, Xumo channel id, name, country, category, availability)
XUMO_CHANNELS = [
    # News
    ('cheddar-news', 99991220, 'Cheddar News', 'US', 'News'),
    ('bbc-news', 99951298, 'BBC News', 'GB', 'News'),
    ('sky-news', 99951223, 'Sky News', 'GB', 'News'),
    ('reuters', 99951197, 'Reuters 60', 'GB', 'News'),
    ('bloomberg-tv-plus', 99951533, 'Bloomberg TV+', 'US', 'News'),
    ('cnn-headlines', 99951386, 'CNN Headlines', 'US', 'News'),
    # UK entertainment
    ('top-gear', 99951249, 'Top Gear', 'GB', 'Lifestyle'),
    ('midsomer-murders', 99991698, 'Midsomer Murders', 'GB', 'Series'),
    ('classic-doctor-who', 99951248, 'Classic Doctor Who', 'GB', 'Series'),
    ('bbc-drama', 99951605, 'BBC Drama', 'GB', 'Series'),
    ('great-british-baking', 99951607, 'The Great British Baking Channel', 'GB', 'Lifestyle'),
    ('graham-norton', 99951450, 'The Graham Norton Show', 'GB', 'Series'),
    ('repair-shop', 99951604, 'The Repair Shop', 'GB', 'Reality'),
    ('come-dine-with-me', 99951451, 'Come Dine With Me', 'GB', 'Reality'),
    ('bbc-earth', 99951231, 'BBC Earth', 'GB', 'Lifestyle'),
    ('jamie-oliver', 99951153, 'The Jamie Oliver Channel', 'GB', 'Lifestyle'),
    # Australian entertainment
    ('mcleods-daughters', 99951138, "McLeod's Daughters", 'AU', 'Series'),
    # Canadian entertainment
    ('highway-thru-hell', 99951509, 'Highway Thru Hell', 'CA', 'Reality'),
    ('just-for-laughs-gags', 99991204, 'Just for Laughs Gags', 'CA', 'Series'),
    ('mayday', 99951262, 'Mayday: Air Disaster', 'CA', 'Reality'),
    ('property-brothers', 99951537, 'Property Brothers', 'CA', 'Lifestyle'),
    ('love-nature', 99991398, 'Love Nature', 'CA', 'Lifestyle'),
    ('gusto-tv', 99991328, 'GustoTV', 'CA', 'Lifestyle'),
    # More US entertainment
    ('hells-kitchen', 99991681, "Hell's Kitchen", 'US', 'Reality'),
    ('family-feud-classic', 99951339, 'Family Feud Classic', 'US', 'Reality'),
    ('little-house', 99951240, 'Little House on the Prairie', 'US', 'Series'),
    ('johnny-carson', 99991157, 'Johnny Carson TV', 'US', 'Series'),
    ('this-old-house', 99991293, 'This Old House', 'US', 'Lifestyle'),
    ('mythbusters', 99951396, 'MythBusters', 'US', 'Reality'),
    ('pbs-nature', 99951195, 'PBS Nature', 'US', 'Lifestyle'),
    ('lassie', 99951259, 'Lassie', 'US', 'Series'),
]

# Canada's public broadcaster through Xumo's public guide. CBC Gem's own live channels (including the
# local CBC News channels) are for signed-in Gem members, so they are not taken from CBC's services.
XUMO_CHANNELS += [
    ('cbc-news', 99991276, 'CBC News', 'CA', 'News'),
    ('heartland', 99951494, 'Heartland', 'CA', 'Series'),
    ('murdoch-mysteries', 99951452, 'Murdoch Mysteries', 'CA', 'Series'),
]

NEWS_REVIEW = {
    'cheddar-news': 'Media Bias/Fact Check: Left-Center, High factual (mediabiasfactcheck.com/cheddar). Business news; not News Corp/Fox.',
    'bbc-news': 'Media Bias/Fact Check: Left-Center, High factual. UK public broadcaster; impartiality required by its Charter and Ofcom.',
    'sky-news': 'Media Bias/Fact Check: Least Biased, High factual; Ad Fontes: Middle, reliable (mediabiasfactcheck.com/sky-news). Owned by Comcast (Sky Group), not News Corp; Ofcom impartiality rules. Not Sky News Australia (News Corp, excluded).',
    'reuters': 'Media Bias/Fact Check: Least Biased, Very High factual; Ad Fontes: Middle, reliable (mediabiasfactcheck.com/reuters).',
    'bloomberg-tv-plus': 'Media Bias/Fact Check: Left-Center; Ad Fontes: Middle (adfontesmedia.com/bloomberg-news-bias-and-reliability). Business news.',
    'cnn-headlines': 'Media Bias/Fact Check: Left (mediabiasfactcheck.com/cnn); Ad Fontes Media: Skews Left.',
    'dw-news': 'Media Bias/Fact Check: Left-Center, High factual (mediabiasfactcheck.com/dw-news). Germany\'s international public broadcaster.',
    'abc-news-australia': 'Media Bias/Fact Check: Left-Center, High factual (mediabiasfactcheck.com/abc-news-australia). Australia\'s public broadcaster (not News Corp; Sky News Australia excluded).',
    'cbc-news': 'Media Bias/Fact Check: Left-Center, High factual; Ad Fontes: Middle, reliable (mediabiasfactcheck.com/cbc-news). Canada\'s public broadcaster.',
}

EXCLUDED_NEWS = [
    {'id': 'newsmax2', 'reason': 'Right-wing news (Media Bias/Fact Check: Right). Excluded by policy.'},
    {'id': 'oan-plus', 'reason': 'Right-wing news (One America News). Excluded by policy.'},
    {'id': 'salem-news-channel', 'reason': 'Right-wing talk/news. Excluded by policy.'},
    {'id': 'livenow-from-fox', 'reason': 'Fox Corporation (Murdoch). Excluded by policy alongside News Corp.'},
    {'id': 'fox-weather', 'reason': 'Fox Corporation (Murdoch). Excluded by policy alongside News Corp.'},
    {'id': 'sky-news-australia', 'reason': 'News Corp owned. Excluded by policy.'},
    {'id': 'talktv', 'reason': 'News Corp owned; Piers Morgan. Excluded by policy.'},
    {'id': 'gb-news', 'reason': 'Right-wing news. Excluded by policy.'},
    {'id': 'tyt', 'reason': 'Partisan (left) commentary rather than non-partisan news. Excluded by policy.'},
    {'id': 'france-24-english', 'reason': 'Official site plays France 24 English through YouTube only; no first-party HLS for a non-WebView app. Not added.'},
    {'id': 'euronews-english', 'reason': 'Euronews states its English stream is unavailable in the USA; not globally available, and no first-party HLS was obtained. Not added.'},
    {'id': 'cbc-news-local', 'reason': 'CBC Gem offers its local CBC News channels to signed-in members only. Grapefruit requires no account and does not get around sign-in, so they are not added.'},
    {'id': 'antiques-roadshow-uk', 'reason': 'Current official Xumo metadata marks the channel DRM; excluded without attempting to bypass it.'},
]


def get(url, tries=4):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode('utf-8'))
        except (TimeoutError, OSError) as e:
            if attempt == tries - 1:
                raise
            time.sleep(2 ** (attempt + 1))


def quote_query(url):
    """Percent-encodes query values as beta 1's entries do (commas and so on), keys and order kept."""
    base, _, query = url.partition('?')
    parts = []
    for pair in query.split('&'):
        k, _, v = pair.partition('=')
        parts.append(k + '=' + urllib.parse.quote(v, safe='-_.~'))
    return base + '?' + '&'.join(parts)


def xumo_guide():
    items = get(XUMO + '/channels/list/10006.json?sort=hybrid&geoId=unknown')['channel']['item']
    return {int(i['guid']['value']): i for i in items}


def xumo_stream(channel_id):
    asset = get('%s/channels/channel/%d/broadcast.json?hour=3' % (XUMO, channel_id))['assets'][0]['id']
    source_url = '%s/assets/asset/%s.json?f=providers&f=connectorId' % (XUMO, asset)
    data = get(source_url)
    if data.get('contentType') != 'SIMULCAST':
        raise ValueError('not a live simulcast in Xumo metadata (%s): no single live stream' % data.get('contentType'))
    for provider in data['providers']:
        for s in provider['sources']:
            if s.get('drm') or 'drm' in json.dumps(s).lower():
                raise ValueError('DRM declared')
            if s.get('produces') == 'application/x-mpegURL' and s['uri'].startswith('https://'):
                template = s['uri']
                url = template
                for k, v in MACROS.items():
                    url = url.replace(k, v)
                if '[' in url:
                    raise ValueError('unfilled macro in ' + url)
                # The app names itself, as beta 1's Xumo entries do.
                url = url.replace('ads.appName=XumoPlay', 'ads.appName=GrapefruitFAST').replace('ads.appName=xumo', 'ads.appName=GrapefruitFAST')
                return source_url, template, quote_query(url)
    raise ValueError('no clear HLS source')


def short(text, limit=150):
    text = ' '.join(text.split())
    if len(text) <= limit:
        return text
    cut = text[:limit].rsplit(' ', 1)[0].rstrip(',;:')
    return cut + '…'


def main():
    catalog = json.loads(CATALOG.read_text())
    sources = json.loads(SOURCES.read_text())
    have = {c['id'] for c in catalog['channels']}
    guide = xumo_guide()
    added, failed = [], []

    def add(channel, record):
        if channel['id'] in have:
            return
        catalog['channels'].append(channel)
        sources['sources'].append(record)
        have.add(channel['id'])
        added.append(channel['id'])

    for cid, xid, name, country, category in XUMO_CHANNELS:
        try:
            item = guide[xid]
            slug = item['title'].strip().lower().replace("'", '').replace('&', 'and')
            slug = '-'.join(''.join(ch if ch.isalnum() else ' ' for ch in slug).split())
            source_url, template, stream = xumo_stream(xid)
            page = 'https://play.xumo.com/live-guide/' + slug
            channel = {'id': cid, 'name': name, 'country': country, 'category': category,
                       'description': short(item.get('description') or name), 'website': page,
                       'streams': [stream], 'provenance': source_url, 'availability': 'United States (Xumo)'}
            record = {'id': cid, 'viewing_page': page, 'source': source_url,
                      'evidence': {'official_channel_id': xid, 'source_field': 'SIMULCAST providers.sources',
                                   'template': template,
                                   'advertising_macros': 'Android/app identity; limit-ad-tracking=1; zero advertising ID; unknown device make/model; no coordinates or other optional targeting. All provider query keys and SSAI tags retained. No country parameter added or altered.',
                                   'availability': 'Xumo Play is a US service; outside the US the provider may refuse the stream.'},
                      'streams': [stream]}
            if cid in NEWS_REVIEW:
                record['evidence']['bias_review'] = NEWS_REVIEW[cid]
            add(channel, record)
        except Exception as e:
            failed.append((cid, str(e)))

    # DW English, from DW's own live page.
    try:
        page = 'https://www.dw.com/en/live-tv/channel-english'
        req = urllib.request.Request(page, headers={'User-Agent': 'Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile Safari/537.36'})
        html = urllib.request.urlopen(req, timeout=20).read().decode('utf-8', 'replace')
        marker = '"name":"DW English","namedUrl":"/en/live-tv/channel-english","hlsVideoSrc":"'
        start = html.index(marker) + len(marker)
        stream = html[start:html.index('"', start)]
        if not stream.startswith('https://'):
            raise ValueError('DW stream not HTTPS')
        add({'id': 'dw-news', 'name': 'DW News', 'country': 'DE', 'category': 'News',
             'description': "Germany's international broadcaster: world news, business and documentaries in English.",
             'website': page, 'streams': [stream], 'provenance': page, 'availability': 'Worldwide'},
            {'id': 'dw-news', 'viewing_page': page, 'source': page,
             'evidence': {'source_field': 'livestreamChannels[name=DW English].hlsVideoSrc (and the page\'s own <video> source)',
                          'availability': 'DW streams DW English worldwide on its own site.',
                          'bias_review': NEWS_REVIEW['dw-news']},
             'streams': [stream]})
    except Exception as e:
        failed.append(('dw-news', str(e)))

    # ABC News (Australia), from the ABC's own News Channel page.
    try:
        page = 'https://www.abc.net.au/news/newschannel'
        req = urllib.request.Request(page, headers={'User-Agent': 'Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile Safari/537.36'})
        html = urllib.request.urlopen(req, timeout=30).read().decode('utf-8', 'replace')
        marker = '"sources":[{"file":"'
        start = html.index('"moduleUri":"app://module/channel-newschannel-live"')
        start = html.index(marker, start) + len(marker)
        stream = html[start:html.index('"', start)]
        if not stream.startswith('https://') or '.m3u8' not in stream:
            raise ValueError('ABC stream not HTTPS HLS')
        add({'id': 'abc-news-australia', 'name': 'ABC News Australia', 'country': 'AU', 'category': 'News',
             'description': "The ABC's 24-hour news channel: Australian and world news, Australia's public broadcaster.",
             'website': page, 'streams': [stream], 'provenance': page, 'availability': 'Worldwide (ABC website)'},
            {'id': 'abc-news-australia', 'viewing_page': page, 'source': page,
             'evidence': {'source_field': 'module channel-newschannel-live: video.config.sources[0].file (streamType live)',
                          'availability': 'Public live player on the ABC News site; no sign-in. Probed from outside Australia.',
                          'bias_review': NEWS_REVIEW['abc-news-australia']},
             'streams': [stream]})
    except Exception as e:
        failed.append(('abc-news-australia', str(e)))

    # Cheddar News, first-party: Cheddar's own live player, ahead of Xumo's US copy.
    try:
        page = 'https://www.cheddar.com/'
        req = urllib.request.Request(page, headers={'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) Chrome/130 Safari/537.36'})
        html = urllib.request.urlopen(req, timeout=30).read().decode('utf-8', 'replace')
        start = html.index('id: \\"cheddar-live\\"')
        start = html.index("stream: '", start) + len("stream: '")
        stream = html[start:html.index("'", start)]
        if not stream.startswith('https://') or '.m3u8' not in stream:
            raise ValueError('Cheddar stream not HTTPS HLS')
        for c in catalog['channels']:
            if c['id'] == 'cheddar-news' and c['streams'][0] != stream:
                c['streams'] = [stream] + [u for u in c['streams'] if u != stream]
                c['website'] = page
                c['provenance'] = page
                c['availability'] = 'cheddar.com, and Xumo in the United States'
        for r in sources['sources']:
            if r['id'] == 'cheddar-news' and r['streams'][0] != stream:
                r['xumo_fallback'] = {'source': r['source'], 'evidence': r['evidence']}
                r['viewing_page'] = page
                r['source'] = page
                r['evidence'] = {'source_field': 'PoWa player id "cheddar-live" stream (the page\'s own live player); confirmed by the player\'s requests in a headless browser',
                                 'availability': "Cheddar's own public live player; no sign-in. Availability outside the US not established.",
                                 'bias_review': NEWS_REVIEW['cheddar-news']}
                r['streams'] = [stream] + [u for u in r['streams'] if u != stream]
    except Exception as e:
        failed.append(('cheddar-news first-party', str(e)))

    # Beta 1's Xumo channels carry the same US availability as the rest (Region hides them elsewhere).
    for c in catalog['channels']:
        if 'xumo.com' in c['provenance'] and not c.get('availability'):
            c['availability'] = 'United States (Xumo)'

    # CPAC: Canada's Parliament and public affairs, from CPAC's own live player (found in a headless
    # browser on www.cpac.ca; probed adaptive 360p-1080p, H.264/AAC, English and French audio).
    cpac = 'https://cpac-ca-live.cdn.vustreams.com/groupa/live/f9809cea-1e07-47cd-a94d-2ddd3e1351db/live.isml/.m3u8'
    add({'id': 'cpac', 'name': 'CPAC', 'country': 'CA', 'category': 'News',
         'description': "Canada's Parliament live, committee hearings and public affairs, in full and unedited.",
         'website': 'https://www.cpac.ca/', 'streams': [cpac], 'provenance': 'https://www.cpac.ca/',
         'availability': 'cpac.ca (not limited to Canada)'},
        {'id': 'cpac', 'viewing_page': 'https://www.cpac.ca/', 'source': 'https://www.cpac.ca/',
         'evidence': {'source_field': "Live player's HLS master on CPAC's home page, recorded from the player's own requests in a headless browser (November 3, 2026)",
                      'availability': "CPAC's public live player; no sign-in. Probed from the US.",
                      'note': 'Non-partisan public-affairs broadcaster (Parliament of Canada coverage).'},
         'streams': [cpac]})

    known = {e['id'] for e in sources.get('excluded_after_source_review', [])}
    for e in EXCLUDED_NEWS:
        if e['id'] not in known:
            sources.setdefault('excluded_after_source_review', []).append(e)
    catalog['schema'] = 1
    catalog['notice'] = ('Official broadcaster/distributor sources. Regional restrictions and provider ads remain. '
                         'Live probes establish reachability only; JP5S hardware playback remains untested. '
                         'CP24 excluded: no stream obtained from cp24.com.')
    CATALOG.write_text(json.dumps(catalog, indent=1, ensure_ascii=False) + '\n')
    SOURCES.write_text(json.dumps(sources, indent=1, ensure_ascii=False) + '\n')
    print('added %d: %s' % (len(added), ', '.join(added)))
    for cid, why in failed:
        print('NOT ADDED', cid, '-', why)


if __name__ == '__main__':
    main()
