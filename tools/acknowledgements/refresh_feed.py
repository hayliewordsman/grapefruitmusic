#!/usr/bin/env python3
"""Refresh public election projections independently of app releases."""
import argparse,copy,csv,datetime,hashlib,io,json,re,shutil,subprocess,sys,tempfile,urllib.request
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
from zoneinfo import ZoneInfo
from bs4 import BeautifulSoup
from build_snapshot import Builder,normal,district,code_party

HERE=Path(__file__).resolve().parent
MAX_SOURCE_BYTES=12*1024*1024
RUNTIME_MEMBER_FIELDS={'id','name','state','chamber','district','partyCode','office','status','sources','flags','identitySources'}
TRACKERS={'mvp-state-results.html':'state-legislatures','mvp-house-results.html':'us-house','mvp-us-senate-results.html':'us-senate','mvp-governors-results.html':'governors'}

def download_source(url):
    # Some source CDNs answer automated requests with an empty HTTP 200. Never cache that
    # as fresh evidence, and retry through their ordinary public browser representation.
    errors=[]
    for attempt in range(2):
        target=url if not attempt else url+('&' if '?' in url else '?')+'blue_roll_call='+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H')
        request=urllib.request.Request(target,headers={'User-Agent':'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36','Accept':'text/html,application/json,text/plain,*/*','Accept-Language':'en-US,en;q=0.9','Cache-Control':'no-cache'})
        try:
            with urllib.request.urlopen(request,timeout=25) as response:
                if response.status!=200:raise ValueError('HTTP '+str(response.status))
                data=response.read(MAX_SOURCE_BYTES+1)
            if len(data)>MAX_SOURCE_BYTES:raise ValueError('Source is too large')
            if len(data)<64:raise ValueError('Empty or truncated source response')
            return data
        except Exception as error:errors.append(str(error))
    # curl handles CDN transfer/compression quirks that urllib can encounter on hosted runners.
    with tempfile.TemporaryFile() as output:
        result=subprocess.run(['curl','--fail','--silent','--show-error','--location','--compressed','--max-time','25','--max-filesize',str(MAX_SOURCE_BYTES),'--user-agent','Mozilla/5.0',url],stdout=output,stderr=subprocess.PIPE)
        output.seek(0);data=output.read(MAX_SOURCE_BYTES+1)
    if result.returncode==0 and 64<=len(data)<=MAX_SOURCE_BYTES:return data
    raise ValueError('; '.join(errors)+ '; curl: '+str(result.returncode)+' / '+str(len(data))+' bytes')

def fetch_source(source,cache):
    source=dict(source);file=cache/source['file']
    try:
        data=download_source(source.get('retrievalUrl',source['url']))
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

def tracker_contest(race,states,ballots):
    card=race.get('card',{});kind=race.get('type');title=card.get('title','')
    if kind=='state-legislatures':
        match=re.fullmatch(r'([A-Z]{2}) (House|Senate|Assembly) (.+)',title)
        if not match:return None
        state,office,seat=match.groups();chamber='state_senate' if office=='Senate' else 'state_house'
        seat=re.sub(r'\b0+(\d+)',r'\1',district(seat));seat=re.sub(r'\s+District\s+',' ',seat,flags=re.I)
    elif kind=='us-house':
        match=re.fullmatch(r'U\.S\. House ([A-Z]{2})(?:-(\d+|AL)| (at-large))',title)
        if not match:return None
        state,number,at_large=match.groups();chamber='us_house';seat='At-large' if at_large or number=='AL' else str(int(number))
    elif kind=='us-senate':
        match=re.fullmatch(r'U\.S\. Senate [–-] ([A-Z]{2})( \(special\))?',title)
        if not match:return None
        state,special=match.groups();chamber='us_senate';seat='Special election' if special else 'Class 2'
    elif kind=='governors':
        match=re.fullmatch(r'([A-Z]{2}) Governor',title)
        if not match:return None
        state=match.group(1);chamber='governor';seat=''
    else:return None
    key=(state,chamber,normal(seat));ballot=ballots.get(key)
    if not ballot or race.get('state')!=state or card.get('stateName')!=states.get(state) or not card.get('href','').startswith('/election-results/2026/'):return None
    return key,ballot,seat

def update_from_trackers(builder,previous,ballots):
    """Refresh accessible, explicit AP calls without discarding already verified other races.

    The reviewed ballot seed supplies candidate identity only, never winner status. Complete
    replacement calls remove a previous projection even if the new winner is Republican.
    A retracted tracker call removes entries that depended on that tracker. Other verified
    coverage stays dated and provisional; an unavailable page never supplies a new winner.
    """
    builder.members={m['id']:copy.deepcopy(m) for m in previous['members']}
    builder.pending={(g['state'],g['chamber'],normal(g['district'])):copy.deepcopy(g) for g in previous['unresolved']}
    for filename,kind in TRACKERS.items():
        source=next((s for s in builder.manifest['sources'] if s['file']==filename and s.get('fresh')),None)
        if not source:raise ValueError('Live AP-backed tracker unavailable; keep the published feed')
        if source['url']!='https://movement.vote/election-results/2026/races/'+kind+'/':raise ValueError('Unexpected tracker election scope')
        races=list(builder.tracker_races((builder.base/filename).read_text()))
        contests=[(r,tracker_contest(r,builder.states,ballots)) for r in races if r.get('type')==kind]
        contests=[(r,c) for r,c in contests if c]
        if not contests:raise ValueError('Live tracker has no matching 2026 general-election contests')
        for race,(key,ballot,seat) in contests:
            state,chamber,_=key;card=race['card'];refs={source['url'],'https://movement.vote'+card['href']}
            members=[m for m in builder.members.values() if (m['state'],m['chamber'],normal(m['district']))==key]
            called=card.get('foot','').startswith('Called by AP at ');winners={}
            for candidate in card.get('cands',[]):
                if candidate.get('winner') is not True or candidate.get('party') not in ('Dem','GOP'):continue
                democratic=candidate['party']=='Dem'
                matches=[p for p in ballot['candidates'] if (code_party(p['party']) is not None if democratic else 'Republican' in p['party']) and (normal(p['name'])==normal(candidate['name']) or builder.person_key(p['name'])==builder.person_key(candidate['name']))]
                if len(matches)==1:winners[normal(matches[0]['name'])]=matches[0]
            if called and len(winners)>=ballot['seats']:
                for member in members:builder.members.pop(member['id'],None)
                builder.pending.pop(key,None)
                if chamber.startswith('us_'):builder.all_federal.add(key);builder.called_federal.add(key)
            elif called and winners and key in builder.pending:builder.gap(state,chamber,seat,'Some winners verified; remaining places in this multi-member district not yet verified',source['url'])
            elif not called:
                retracted=[m for m in members if m['status']=='projected' and refs.intersection(m['sources'])]
                for member in retracted:builder.members.pop(member['id'],None)
                if retracted:builder.gap(state,chamber,seat,'Previous tracker projection is no longer called; awaiting a verified result',source['url'])
            if called:
                for person in winners.values():builder.add(person['name'],state,chamber,seat,person['party'],'projected',['https://movement.vote'+card['href'],source['url'],ballot['source'],person['url']])
    return builder

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
    manifest.update(sources=results,retrievedAt=datetime.datetime.now(datetime.timezone.utc).isoformat());(cache/'source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    shapes=[]
    for source in results:
        path=cache/source['file']
        if not path.exists() or not source['file'].endswith('.html'):continue
        page=BeautifulSoup(path.read_text(),'html.parser')
        shapes.append({'file':source['file'],'bytes':path.stat().st_size,'title':page.title.get_text(' ',strip=True) if page.title else '', 'candidateTables':len(page.select('table.candidateListTablePartisan,table.candidateListTableNonPartisan')),'winnerRows':len(page.select('tr.results_row.winner')),'sample':page.get_text(' ',strip=True)[:240]})
    (cache/'source-shapes.json').write_text(json.dumps(shapes,indent=2)+'\n')
    previous_path=args.previous or HERE.parents[1]/'blue-victory/src/main/assets/acknowledgements/roster.json';previous=json.loads(previous_path.read_text());b=Builder(cache)
    partial=sum(s.get('fresh',False) for s in results)<len(results)*.9
    if partial:
        seed_ballots=json.loads((seed/'general-ballots.json').read_text())
        if seed_ballots['scope']!='post-2026':raise ValueError('Unexpected ballot scope')
        ballots={(r['state'],r['chamber'],r['district']):{k:v for k,v in r.items() if k in ('seats','candidates','source')} for r in seed_ballots['ballots']}
        update_from_trackers(b,previous,ballots)
    else:
        b.parse_states();b.hawaii_outright();b.federal_widgets('us-senate-2026.html','us_senate')
        for source in results:
            if source['kind']=='us_house':b.federal_house_bullets(source);b.federal_voteboxes(source)
        b.nbc();b.governors();b.district_results();b.called_state_tracker();b.called_house_tracker();b.departures();b.holdovers();b.nebraska()
    b.pride();b.pride_supplements();b.governor_flags();b.identity_evidence()
    snapshot=b.output(cache/'research-roster.json')
    if partial:
        snapshot['note']+=' Live AP-backed legislative, Senate and governor trackers were checked; other previously verified results are retained where sources are currently unavailable.'
        snapshot['refreshMode']='AP-backed tracker update with retained verified coverage'
        snapshot['ballotsReviewedAt']=seed_ballots['reviewed']
    snapshot=compact_snapshot(snapshot,previous)
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(snapshot,ensure_ascii=False,separators=(',',':'))+'\n')
    print('Publishable feed:',len(snapshot['members']),'members;',len(snapshot['unresolved']),'unresolved/unverified contests;',args.output.stat().st_size,'bytes',flush=True)

if __name__=='__main__':main()
