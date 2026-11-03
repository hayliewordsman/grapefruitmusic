#!/usr/bin/env python3
"""Probe all HLS video/audio renditions and bounded media samples, without geo/DRM workarounds.
Reachability is not decoder/hardware verification or permission to redistribute.
"""
import argparse, concurrent.futures, datetime, json, pathlib, re
import urllib.error, urllib.request, urllib.parse
UA = 'GrapefruitTV/1.0-beta-1 (source check)'
SAMPLE = 65536


def secure(url):
    p = urllib.parse.urlparse(url)
    if p.scheme != 'https' or not p.hostname or p.username:
        raise ValueError('source or redirect must use public HTTPS')
    return url


class SecureRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        secure(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


OPENER = urllib.request.build_opener(SecureRedirect())


def fetch(url, limit=512*1024, sample=False, offset=0):
    try:
        return _fetch(url, limit, sample, offset)
    except Exception as error:
        error.probe_host = urllib.parse.urlparse(getattr(error, 'url', url)).hostname
        raise


def _fetch(url, limit=512*1024, sample=False, offset=0):
    headers = {'User-Agent': UA, 'Cache-Control': 'no-cache'}
    if sample:
        headers['Range'] = 'bytes=%d-%d' % (offset, offset+limit-1)
    with OPENER.open(urllib.request.Request(secure(url), headers=headers), timeout=20) as r:
        secure(r.geturl())
        if sample and offset and r.status != 206:
            raise ValueError('server ignored nonzero byte range')
        data = r.read(limit if sample else limit+1)
        if not sample and len(data) > limit:
            raise ValueError('response exceeds probe limit')
        return r.geturl(), data


def parse_manifest(url):
    actual, data = fetch(url)
    text = data.decode('utf-8-sig').strip()
    if not text.startswith('#EXTM3U'):
        raise ValueError('not an HLS playlist')
    return actual, text


def attributes(line):
    return {m[1]: m[2].strip('"') for m in re.finditer(
        r'([A-Z0-9-]+)=("[^"]*"|[^,]*)', line.split(':', 1)[1])}


def variants_in(actual, text):
    variants, audio, pending = [], [], None
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith('#EXT-X-STREAM-INF:'):
            pending = attributes(line)
        elif line.startswith('#EXT-X-MEDIA:'):
            a = attributes(line)
            if a.get('TYPE') == 'AUDIO' and a.get('URI'):
                audio.append({'url': urllib.parse.urljoin(actual, a['URI']),
                              'group': a.get('GROUP-ID'), 'name': a.get('NAME')})
        elif line and not line.startswith('#') and pending is not None:
            resolution = pending.get('RESOLUTION', '0x0').split('x')
            variants.append({'url': urllib.parse.urljoin(actual, line),
                             'width': int(resolution[0]), 'height': int(resolution[1]),
                             'bandwidth': int(pending.get('BANDWIDTH', 0)),
                             'codecs': pending.get('CODECS', ''),
                             'frame_rate': float(pending.get('FRAME-RATE', 0))})
            pending = None
    if pending is not None:
        raise ValueError('variant has no playlist URI')
    return variants, audio


def sample_media(url, length=SAMPLE, offset=0, initialization=False):
    actual, data = fetch(url, min(length, SAMPLE), sample=True, offset=offset)
    if not data:
        raise ValueError('empty media sample')
    ts = len(data)>188 and data[0]==0x47 and data[188]==0x47
    mp4 = len(data)>8 and data[4:8] in (b'ftyp', b'styp', b'moof', b'sidx', b'mdat')
    aac = len(data)>2 and data[0]==0xff and data[1]&0xf6==0xf0
    id3 = data.startswith(b'ID3')
    if not (ts or mp4 or (not initialization and (aac or id3))):
        raise ValueError('unrecognized media framing (not TS, fMP4 or AAC)')
    return {'host': urllib.parse.urlparse(actual).hostname, 'sample_bytes': len(data),
            'framing': 'mpeg-ts' if ts else 'fmp4' if mp4 else 'aac/id3'}


def check_media(url, text):
    if '#EXT-X-ENDLIST' in text:
        raise ValueError('ended playlist; not a current live feed')
    keys, maps, segments = [], [], []
    offset, length, previous_end = 0, SAMPLE, 0
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith(('#EXT-X-KEY:', '#EXT-X-SESSION-KEY:')):
            a = attributes(line)
            if a.get('METHOD') not in ('NONE', 'AES-128') or a.get('KEYFORMAT', 'identity')!='identity':
                raise ValueError('DRM/encryption unsupported by beta 1 probe')
            if a.get('METHOD') == 'AES-128':
                key_url = urllib.parse.urljoin(url, a['URI'])
                if key_url not in keys:
                    _, key = fetch(key_url, 16)
                    if len(key)!=16:
                        raise ValueError('invalid AES-128 key')
                    keys.append(key_url)
        elif line.startswith('#EXT-X-MAP:'):
            a = attributes(line); value = a.get('BYTERANGE', str(SAMPLE)).split('@')
            maps.append(sample_media(urllib.parse.urljoin(url, a['URI']),
                        int(value[0]), int(value[1]) if len(value)>1 else 0, True))
        elif line.startswith('#EXT-X-BYTERANGE:'):
            value = line.split(':',1)[1].split('@')
            length = int(value[0]); offset = int(value[1]) if len(value)>1 else previous_end
        elif line and not line.startswith('#'):
            segments.append((urllib.parse.urljoin(url,line),length,offset))
            previous_end=offset+length; offset,length=0,SAMPLE
    if not segments or '#EXTINF:' not in text:
        raise ValueError('no media segments')
    chosen = segments[-2] if len(segments)>1 else segments[0]
    if keys:
        actual, data = fetch(chosen[0],min(chosen[1],SAMPLE),sample=True,offset=chosen[2])
        if not data:
            raise ValueError('empty encrypted segment')
        media = {'host':urllib.parse.urlparse(actual).hostname,'sample_bytes':len(data),
                 'framing':'AES-128 (key reachable; decoding not tested)'}
    else:
        media = sample_media(*chosen)
    return {'playlist_host':urllib.parse.urlparse(url).hostname,'segment_count':len(segments),
            'media':media,'initializations':maps,'aes128_keys_checked':len(keys)}


def error_info(error):
    detail = {'failed_host': getattr(error, 'probe_host', None)}
    if 'Tunnel connection failed: 403' in str(error):
        detail['failure_kind'] = 'runtime-proxy-denied'
    if isinstance(error,urllib.error.HTTPError):
        detail.update(error='HTTP %d: %s'%(error.code,error.reason),http_status=error.code)
    else:
        detail['error'] = str(error)[:240]
    return detail


def probe_source(source):
    row = {'source':source,'result':'not-verified','stage':'master'}
    try:
        actual,text = parse_manifest(source)
        variants,audio = variants_in(actual,text)
        row['master_host'] = urllib.parse.urlparse(actual).hostname
        if '#EXT-X-SESSION-KEY:' in text:
            raise ValueError('session-key master requires encryption/DRM review')
        row['renditions'],row['audio'] = [],[]
        for variant in variants or [{'url':actual,'height':0,'codecs':''}]:
            detail = {k:v for k,v in variant.items() if k!='url'};row['renditions'].append(detail)
            try:
                u,t = parse_manifest(variant['url']) if variants else (actual,text)
                detail.update(check_media(u,t));detail['result']='media-reachable'
            except Exception as error:
                detail.update(error_info(error));detail['result']='not-verified'
        for track in audio:
            detail = {k:v for k,v in track.items() if k!='url'};row['audio'].append(detail)
            try:
                u,t = parse_manifest(track['url'])
                detail.update(check_media(u,t));detail['result']='media-reachable'
            except Exception as error:
                detail.update(error_info(error));detail['result']='not-verified'
        video = [v for v in variants if v['height'] or re.search(r'avc|hvc|hev|vp0|av01',v['codecs'])]
        row['video_variants']=len(video)
        row['adaptive']=len({v['bandwidth'] for v in video})>1
        row['api17_renditions']=sum(1 for v in video if v['height']<=720 and v['frame_rate']<=30 and 'avc1' in v['codecs'])
        if variants and not video:
            raise ValueError('audio-only master; no video rendition')
        if not all(c['result']=='media-reachable' for c in row['renditions']+row['audio']):
            row['stage']='rendition/media';row['error']='one or more rendition/media checks failed'
        else:
            row['stage']='complete';row['result']='media-reachable'
        return row
    except Exception as error:
        row.update(error_info(error));return row


def probe(channel):
    # Also check every fallback: a good primary must not hide a stale URL.
    sources = [probe_source(s) for s in channel['streams']]
    reachable = [s for s in sources if s['result']=='media-reachable']
    return {'id':channel['id'],'name':channel['name'],
            'result':'media-reachable' if reachable else 'not-verified',
            'adaptive':bool(reachable) and reachable[0]['adaptive'],'sources':sources}


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--catalog',default='tv/src/main/assets/channels.json')
    p.add_argument('--output',default='work/stream-report.json');args=p.parse_args()
    catalog=json.loads(pathlib.Path(args.catalog).read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results=list(pool.map(probe,catalog['channels']))
    report={'checked_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'scope':'All configured masters, all video/audio renditions, initialization/key resources and bounded media samples. No decoder or hardware playback test.',
            'channels':results}
    out=pathlib.Path(args.output);out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,indent=2)+'\n')
    for row in results:print(row['result']+': '+row['name'],flush=True)
    print('Report: '+str(out))
    return 0 if all(s['result']=='media-reachable' for r in results for s in r['sources']) else 1


if __name__=='__main__':raise SystemExit(main())
