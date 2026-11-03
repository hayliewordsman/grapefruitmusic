#!/usr/bin/env python3
"""Probe catalog masters, media playlists and a short segment sample; no geo/DRM workarounds.
Run from repo root: python3 tools/fast/check_streams.py --output work/stream-report.json
A successful probe is not a grant of distribution rights or a hardware playback test.
"""
import argparse, concurrent.futures, datetime, json, pathlib, re, urllib.request, urllib.parse
LIMIT=3*1024*1024

def fetch(url, limit=LIMIT):
    request=urllib.request.Request(url,headers={'User-Agent':'GrapefruitFAST/1.0-beta-1 (source check)'})
    with urllib.request.urlopen(request, timeout=18) as response:
        content=response.read(limit+1)
        if len(content)>limit: raise ValueError('response exceeds probe limit')
        return response.geturl(),content

def parse_manifest(url):
    actual,data=fetch(url,512*1024)
    text=data.decode('utf-8-sig')
    if not text.lstrip().startswith('#EXTM3U'): raise ValueError('not an HLS playlist')
    return actual,text

def probe(channel):
    attempts=[]
    for source in channel['streams']:
        try:
            actual,text=parse_manifest(source)
            variants=[];pending=None
            for line in text.splitlines():
                if line.startswith('#EXT-X-STREAM-INF:'): pending=line
                elif pending and line.strip() and not line.startswith('#'):
                    resolution=re.search(r'RESOLUTION=(\d+)x(\d+)',pending)
                    variants.append((int(resolution[2]) if resolution else 0,urllib.parse.urljoin(actual,line.strip()),pending))
                    pending=None
            adaptive=len(variants)>1
            if variants:
                candidates=[v for v in variants if 0<v[0]<=720] or variants
                chosen=sorted(candidates)[0]
                actual,text=parse_manifest(chosen[1])
            if 'SAMPLE-AES' in text or 'KEYFORMAT="com.apple' in text:
                raise ValueError('DRM playlist is unsuitable for beta 1')
            segments=[urllib.parse.urljoin(actual,l.strip()) for l in text.splitlines() if l.strip() and not l.startswith('#')]
            if not segments: raise ValueError('no media segments')
            # A bounded range request proves media is accessible without downloading a programme.
            request=urllib.request.Request(segments[-1],headers={'User-Agent':'GrapefruitFAST/1.0-beta-1','Range':'bytes=0-65535'})
            with urllib.request.urlopen(request,timeout=18) as response:
                sample=response.read(65536)
                if not sample: raise ValueError('empty segment')
            return {'id':channel['id'],'name':channel['name'],'result':'media-reachable','adaptive_variants':len(variants),'adaptive':adaptive,'segment_sample_bytes':len(sample),'host':urllib.parse.urlparse(actual).hostname}
        except Exception as e:
            attempts.append({'host':urllib.parse.urlparse(source).hostname,'error':str(e)[:240]})
    return {'id':channel['id'],'name':channel['name'],'result':'not-verified','attempts':attempts}

def main():
    p=argparse.ArgumentParser();p.add_argument('--catalog',default='fast/src/main/assets/channels.json');p.add_argument('--output',default='work/stream-report.json');a=p.parse_args()
    catalog=json.loads(pathlib.Path(a.catalog).read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: results=list(pool.map(probe,catalog['channels']))
    report={'checked_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'channels':results}
    out=pathlib.Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n')
    for row in results: print(row['result']+': '+row['name'])
    print('Report: '+str(out))
    return 0 if all(r['result']=='media-reachable' for r in results) else 1
if __name__=='__main__': raise SystemExit(main())
