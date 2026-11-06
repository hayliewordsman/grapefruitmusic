#!/usr/bin/env python3
"""Refresh public election projections independently of app releases."""
import argparse,csv,datetime,hashlib,io,json,shutil,sys,urllib.request
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
from zoneinfo import ZoneInfo
from bs4 import BeautifulSoup
from build_snapshot import Builder

HERE=Path(__file__).resolve().parent
MAX_SOURCE_BYTES=12*1024*1024
RUNTIME_MEMBER_FIELDS={'id','name','state','chamber','district','partyCode','office','status','sources','flags','identitySources'}

def fetch_source(source,cache):
    source=dict(source);file=cache/source['file']
    try:
        request=urllib.request.Request(source.get('retrievalUrl',source['url']),headers={'User-Agent':'BlueGrapefruit-RollCall/1.1 (+https://github.com/hayliewordsman/grapefruitmusic)'})
        with urllib.request.urlopen(request,timeout=25) as response:data=response.read(MAX_SOURCE_BYTES+1)
        if len(data)>MAX_SOURCE_BYTES:raise ValueError('Source is too large')
        source['retrievedSha256']=hashlib.sha256(data).hexdigest()
        if source['kind']=='current':
            reader=csv.DictReader(io.StringIO(data.decode('utf-8-sig')));fields=['name','current_chamber','current_district','current_party','sources'];out=io.StringIO();writer=csv.DictWriter(out,fieldnames=fields);writer.writeheader()
            for row in reader:writer.writerow({key:row.get(key,'') for key in fields})
            data=out.getvalue().encode()
        file.write_bytes(data);source.update(ok=True,fresh=True,retrievedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    except Exception as error:source.update(ok=file.exists(),fresh=False,error=str(error)[:160])
    return source

def compact_snapshot(snapshot,previous):
    # Identity evidence is independent from race evidence and follows only the exact stable ID.
    old={member['id']:member for member in previous['members']}
    for member in snapshot['members']:
        prior=old.get(member['id'])
        if prior:
            for flag in prior.get('flags',[]):
                refs=prior.get('identitySources',{}).get(flag,[])
                if refs:
                    if flag not in member['flags']:member['flags'].append(flag)
                    member['identitySources'][flag]=list(dict.fromkeys(member['identitySources'].get(flag,[])+refs))
    if len(snapshot['members'])<max(3000,int(len(previous['members'])*.95)):
        raise ValueError('Roster unexpectedly lost coverage; keep the previous published feed for review')
    states={m['state'] for m in snapshot['members']}|{g['state'] for g in snapshot['unresolved']}
    if not set(snapshot['coverage']['states']).issubset(states):raise ValueError('Missing state coverage')
    snapshot['members']=[{k:v for k,v in member.items() if k in RUNTIME_MEMBER_FIELDS} for member in snapshot['members']]
    snapshot.pop('researchSources',None)
    snapshot.update(feedUpdatedAt=int(datetime.datetime.now(datetime.timezone.utc).timestamp()*1000),identityEvidenceReviewed='2026-11-05')
    return snapshot

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--previous',type=Path);parser.add_argument('--reuse-checked',action='store_true');args=parser.parse_args()
    cache=args.cache;cache.mkdir(parents=True,exist_ok=True);seed=HERE/'feed-seed';manifest=json.loads((seed/'sources.json').read_text());manifest['snapshotDate']=datetime.datetime.now(ZoneInfo('America/Toronto')).date().isoformat()
    shutil.copyfile(seed/'out-legislators.json',cache/'out-legislators.json')
    for file in (seed/'proofs').iterdir():shutil.copyfile(file,cache/file.name)
    results=[];checked={}
    if args.reuse_checked and (cache/'source-manifest.json').exists():
        old=json.loads((cache/'source-manifest.json').read_text());age=datetime.datetime.now(datetime.timezone.utc)-datetime.datetime.fromisoformat(old['retrievedAt'])
        if old.get('snapshotDate')==manifest['snapshotDate'] and age.total_seconds()<3600:checked={s['file']:s for s in old['sources'] if s.get('fresh') and (cache/s['file']).exists()}
    with ThreadPoolExecutor(max_workers=4) as pool:
        for source in manifest['sources']:
            if source['file'] in checked:results.append(checked[source['file']])
        for n,future in enumerate(as_completed([pool.submit(fetch_source,s,cache) for s in manifest['sources'] if s['file'] not in checked]),1):
            results.append(future.result())
            if n%40==0:print('Checked',n,'of',len(manifest['sources']),'sources',flush=True)
    if sum(s.get('fresh',False) for s in results)<len(results)*.9:raise ValueError('Too many sources unavailable; preserve the last published feed')
    manifest.update(sources=results,retrievedAt=datetime.datetime.now(datetime.timezone.utc).isoformat());(cache/'source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    shapes=[]
    for source in results:
        path=cache/source['file']
        if not path.exists() or not source['file'].endswith('.html'):continue
        page=BeautifulSoup(path.read_text(),'html.parser')
        shapes.append({'file':source['file'],'bytes':path.stat().st_size,'title':page.title.get_text(' ',strip=True) if page.title else '', 'candidateTables':len(page.select('table.candidateListTablePartisan,table.candidateListTableNonPartisan')),'winnerRows':len(page.select('tr.results_row.winner')),'sample':page.get_text(' ',strip=True)[:240]})
    (cache/'source-shapes.json').write_text(json.dumps(shapes,indent=2)+'\n')
    b=Builder(cache);b.parse_states();b.hawaii_outright();b.federal_widgets('us-senate-2026.html','us_senate')
    for source in results:
        if source['kind']=='us_house':b.federal_house_bullets(source);b.federal_voteboxes(source)
    b.nbc();b.governors();b.district_results();b.called_state_tracker();b.called_house_tracker();b.departures();b.holdovers();b.nebraska();b.pride();b.pride_supplements();b.governor_flags();b.identity_evidence()
    snapshot=b.output(cache/'research-roster.json');previous=args.previous or HERE.parents[1]/'blue-victory/src/main/assets/acknowledgements/roster.json';snapshot=compact_snapshot(snapshot,json.loads(previous.read_text()))
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(snapshot,ensure_ascii=False,separators=(',',':'))+'\n')
    print('Publishable feed:',len(snapshot['members']),'members;',len(snapshot['unresolved']),'unresolved/unverified contests;',args.output.stat().st_size,'bytes',flush=True)

if __name__=='__main__':main()
