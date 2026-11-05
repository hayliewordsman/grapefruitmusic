#!/usr/bin/env python3
"""Build a provisional acknowledgements snapshot from cached, publicly sourced result pages.

Never infer winners from vote leads or prior incumbency. General-election winner markers and
continuing terms are separate evidence. Flags require publicly self-disclosed identity sources.
Raw source files stay under ignored work/, including unused contact fields from roster exports.
"""
import argparse,csv,datetime,hashlib,html,io,json,re,unicodedata
from pathlib import Path
from collections import Counter,defaultdict
from bs4 import BeautifulSoup,SoupStrainer
import yaml

OFF_LOWER={'LA','MS','NJ','VA'}
OFF_UPPER=OFF_LOWER|{'NM','SC','KS'}
ALL_UPPER={'AL','AZ','CT','GA','ID','ME','MD','MA','MI','MN','NH','NY','NC','RI','SD','VT'}

def normal(value):
    value=unicodedata.normalize('NFKD',html.unescape(value)).lower()
    return re.sub(r'[^a-z0-9]','',value)
def district(value):
    value=re.sub(r'\bdistrict\b','',value,flags=re.I).strip()
    value=re.sub(r'\s*\(\d+ seats?\)','',value)
    return re.sub(r'\s+',' ',value)
def code_party(value):
    if 'Democrat' in value or value in ('D','DFL','D-NPL','Democratic-NPL'):return 'DFL' if 'Farmer' in value or value=='DFL' else 'D-NPL' if 'NPL' in value else 'D'
    return None

def grid(table):
    spans={}
    for row in table.find_all('tr',recursive=False) if table.name=='tbody' else table.find_all('tr'):
        out={};future={}
        for col,(cell,left) in spans.items():
            out[col]=cell
            if left>1:future[col]=(cell,left-1)
        col=0
        for cell in row.find_all(['td','th'],recursive=False):
            while col in out:col+=1
            for offset in range(int(cell.get('colspan',1))):
                out[col+offset]=cell
                if int(cell.get('rowspan',1))>1:future[col+offset]=(cell,int(cell['rowspan'])-1)
            col+=int(cell.get('colspan',1))
        spans=future
        yield [out.get(n) for n in range(max(out,default=-1)+1)]

class Builder:
    def __init__(self,base):
        self.base=base;self.manifest=json.loads((base/'source-manifest.json').read_text());self.states=self.manifest['states'];self.members={};self.pending={};self.contested=defaultdict(set);self.ballots={};self.parsed=set();self.known_winners=set();self.all_federal=set();self.called_federal=set();self.sources={};self.current={};self.exiting=set()
        for source in self.manifest['sources']:
            if source['ok']:
                p=base/source['file'];self.sources[source['file']]={'url':source['url'],'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
                for key in ['retrievedAt','retrievalUrl','fresh']:
                    if key in source:self.sources[source['file']][key]=source[key]
                if source['kind']=='current':self.current[source['state']]=list(csv.DictReader(io.StringIO(p.read_text())))
        extra={'gov-2026.html':'https://ballotpedia.org/Gubernatorial_elections,_2026','senate-current.yaml':'https://github.com/unitedstates/congress-legislators/blob/main/legislators-current.yaml','nbc-ny-results.html':'https://www.nbcnewyork.com/elections/live-results-2026-ny-nj-ct-midterm-elections/6563336/','out-legislators.json':'https://outforamerica.org/','ne-members.html':'https://en.wikipedia.org/wiki/Nebraska_Legislature','vf-whaley.html':'https://victoryfund.org/candidate/joanna-whaley/','vf-hudgins.html':'https://victoryfund.org/candidate/bentley-hudgins/','vf-moss.html':'https://victoryfund.org/candidate/jeremy-moss/','vf-ford.html':'https://victoryfund.org/candidate/ford-j-d/','vf-von-wilpert.html':'https://victoryfund.org/candidate/marni-von-wilpert/','dina-nina-campaign.html':'https://www.votedinanina.com/','governor-healey.html':'https://www.mass.gov/news/governor-healey-touts-lgbtq-equality-economic-partnership-as-first-us-governor-to-address-irish-senate','governor-kotek.html':'https://www.oregon.gov/gov/Pages/meet-the-governor.aspx'}
        for filename,url in extra.items():
            path=base/filename
            if path.exists():self.sources.setdefault(filename,{}).update({'url':url,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'excerpt':filename=='dina-nina-campaign.html'})
        for path in base.glob('ne-party-*.html'):
            soup=BeautifulSoup(path.read_text(),'html.parser');canonical=soup.find('link',rel='canonical')
            if canonical:self.sources.setdefault(path.name,{}).update({'url':canonical['href'],'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    def office(self,kind,state):
        if kind=='governor':return 'Governor-elect'
        if kind=='us_senate':return 'Senator'
        if kind=='us_house':return 'Resident Commissioner' if state=='PR' else 'Delegate' if state in ('DC','VI','GU','MP','AS') else 'Representative'
        if kind=='state_senate':return 'State Senator'
        if state in ('MD','VA','WV'):return 'State Delegate'
        if state in ('CA','NV','NY','WI'):return 'State Assemblymember'
        return 'State Representative'
    def add(self,name,state,kind,seat,party,status,refs):
        name=html.unescape(name).strip();party=code_party(party)
        if not party:return
        if kind.startswith('us_'):party='D'
        key=kind+':'+state+':'+normal(seat)+':'+normal(name)
        self.members[key]={'id':key,'name':name,'state':state,'chamber':kind,'district':seat,'partyCode':party,'office':self.office(kind,state),'status':status,'sources':list(dict.fromkeys(('https://ballotpedia.org'+r if r.startswith('/') else r) for r in refs if r and r.startswith(('http://','https://','/')))),'flags':[],'identitySources':{}}
    def gap(self,state,kind,seat,reason,url):
        key=(state,kind,normal(seat));self.pending[key]={'state':state,'chamber':kind,'office':self.office(kind,state),'district':seat,'reason':reason,'sources':[url]}
    def source(self,filename):return self.sources[filename]['url']
    def parse_states(self):
        for source in self.manifest['sources']:
            kind=source['kind'];state=source['state']
            if not kind.startswith('state_'):continue
            if not source['ok']:
                self.gap(state,kind,'All contests','Result source not available in this snapshot',source['url']);continue
            soup=BeautifulSoup((self.base/source['file']).read_text(),'html.parser');found=False
            for table in soup.select('table.candidateListTablePartisan,table.candidateListTableNonPartisan'):
                heading=table.find(['h3','h4','caption']);title=heading.get_text(' ',strip=True) if heading else ''
                if not re.search(r'general election.*2026',title,re.I):continue
                found=True;headers=None
                for cells in grid(table):
                    texts=[c.get_text(' ',strip=True) if c else '' for c in cells]
                    if texts and texts[0]=='Office':headers=texts;continue
                    if not headers or not cells or cells[0] is None:continue
                    first=cells[0];a=first.find('a');seat=district(first.get_text(' ',strip=True))
                    if not a or not any(x in a.get('href','') for x in ['District','district']):continue
                    self.contested[(state,kind)].add(normal(seat));called=False;winner_names=set()
                    expected=re.search(r'\((\d+) seats?\)',first.get_text(' ',strip=True));ballot=self.ballots.setdefault((state,kind,normal(seat)),{'seats':int(expected.group(1)) if expected else 1,'candidates':[],'source':source['url']})
                    for col,cell in enumerate(cells[1:],1):
                        if cell is None:continue
                        party=headers[col] if col<len(headers) else ''
                        for span in cell.select('span.candidate'):
                            name=span.find('a')
                            if name:ballot['candidates'].append({'name':name.get_text(' ',strip=True),'party':party,'url':name.get('href')})
                            if not span.find('img',alt=lambda alt:alt and 'Green check mark' in alt):continue
                            called=True
                            if name:
                                winner_names.add(normal(name.get_text(' ',strip=True)));self.add(name.get_text(' ',strip=True),state,kind,seat,party,'projected',[source['url'],name.get('href')])
                    expected=re.search(r'\((\d+) seats?\)',first.get_text(' ',strip=True))
                    if not called:self.gap(state,kind,seat,'General-election winner not yet marked in the source',source['url'])
                    elif expected and len(winner_names)<int(expected.group(1)):self.gap(state,kind,seat,'Some winners verified; remaining places in this multi-member district not yet verified',source['url'])
            if found:self.parsed.add((state,kind))
            else:self.gap(state,kind,'All contests','The available page does not yet expose a verified general-election roster',source['url'])
    def hawaii_outright(self):
        # Hawaii cancels the general election when a primary candidate is elected outright.
        for source in self.manifest['sources']:
            if source['state']!='HI' or not source['kind'].startswith('state_') or not source['ok']:continue
            soup=BeautifulSoup((self.base/source['file']).read_text(),'html.parser');cancelled=set();winners=defaultdict(list)
            for table in soup.select('table.candidateListTablePartisan'):
                h=table.find('h4');title=h.get_text(' ',strip=True) if h else '';headers=None
                if not re.search(r'(general election|primary) 2026',title):continue
                for cells in grid(table):
                    texts=[c.get_text(' ',strip=True) if c else '' for c in cells]
                    if texts and texts[0]=='Office':headers=texts;continue
                    if not headers or not texts or not texts[0].startswith('District '):continue
                    seat=district(texts[0])
                    if 'general election 2026' in title and any('This general election was canceled.' in t for t in texts):cancelled.add(seat)
                    if 'primary 2026' not in title:continue
                    for col,cell in enumerate(cells[1:],1):
                        if cell is None:continue
                        for span in cell.select('span.candidate'):
                            if span.find('img',alt=lambda a:a and 'Green check mark' in a):
                                a=span.find('a')
                                if a:winners[seat].append((a.get_text(' ',strip=True),headers[col],a.get('href')))
            for seat in cancelled:
                found=list(dict.fromkeys(winners[seat]))
                if len(found)!=1:continue
                name,party,url=found[0];self.add(name,'HI',source['kind'],seat,party,'elected',[source['url'],url,'https://elections.hawaii.gov/resources/elected-officials/']);self.pending.pop(('HI',source['kind'],normal(seat)),None)
    def federal_widgets(self,filename,kind):
        soup=BeautifulSoup((self.base/filename).read_text(),'html.parser');url='https://ballotpedia.org/United_States_Senate_elections,_2026' if kind=='us_senate' else self.source(filename)
        for row in soup.select('tr'):
            candidate=row.select_one('td[data-cell="candidate"]');party=row.select_one('td[data-cell="party"]');office=row.select_one('td[data-cell="office"]');status=row.select_one('td[data-cell="status"]')
            if not all([candidate,party,office,status]):continue
            office_text=office.get_text(' ',strip=True);state=next((code for code,name in sorted(self.states.items(),key=lambda x:-len(x[1])) if re.search(r'\b'+re.escape(name)+r'\b',office_text)),None)
            if not state:continue
            office_link=office.find('a');href=office_link.get('href','') if office_link else ''
            seat='Special election' if 'special_election' in href else 'Class 2' if kind=='us_senate' else district(office_text.split(self.states[state],1)[-1])
            key=(state,kind,normal(seat));self.all_federal.add(key)
            if status.get_text(' ',strip=True)!='Won General':continue
            self.called_federal.add(key);a=candidate.select_one('.widget-candidate-info a') or candidate.find('a')
            if a:self.add(a.get_text(' ',strip=True),state,kind,seat,party.get_text(' ',strip=True),'projected',[url,href,a.get('href')])
    @staticmethod
    def person_key(name):
        tokens=re.findall(r"[A-Za-zÀ-ž]+",name)
        tokens=[t for t in tokens if len(t)>1 and t.lower() not in ('jr','sr')]
        return normal(tokens[0]+' '+tokens[-1]) if tokens else normal(name)
    def departures(self):
        path=self.base/'gov-2026.html'
        if path.exists():
            soup=BeautifulSoup(path.read_text(),'html.parser')
            for row in soup.select('tr'):
                candidate=row.select_one('td[data-cell="candidate"]');office=row.select_one('td[data-cell="office"]');status=row.select_one('td[data-cell="status"]')
                if not all([candidate,office,status]) or status.get_text(' ',strip=True)!='Won General':continue
                state=next((c for c,n in self.states.items() if re.search(r'\b'+re.escape(n)+r'\b',office.get_text(' ',strip=True))),None)
                a=candidate.select_one('.widget-candidate-info a') or candidate.find('a')
                if state and a:self.exiting.add((state,self.person_key(a.get_text(' ',strip=True))))
        # People explicitly elected to Congress cannot also remain state-legislative holdovers.
        self.exiting.update((m['state'],self.person_key(m['name'])) for m in self.members.values() if m['chamber'].startswith('us_'))
    def federal_house_bullets(self,source):
        if not source['ok']:return
        soup=BeautifulSoup((self.base/source['file']).read_text(),'html.parser');state=source['state'];kind='us_house';found=0;targets=[]
        for h in soup.find_all(['h2','h3']):
            heading=h.get_text(' ',strip=True);match=re.match(r'^District (.+)$',heading)
            if match:targets.append((h,match.group(1)))
            elif re.search('at.large',heading,re.I):targets.append((h,'At-large'))
        if not targets and state in {'AK','DE','ND','SD','VT','WY'}:targets=[(None,'At-large')]
        for h,seat in targets:
            general=None
            nodes=h.next_siblings if h else soup.find_all('p')
            for sibling in nodes:
                if h and getattr(sibling,'name',None) in ('h2','h3'):break
                if getattr(sibling,'name',None)=='p' and sibling.get_text(' ',strip=True)=='General election candidates':general=sibling.find_next_sibling('ul');break
            if general is None:continue
            found+=1;key=(state,kind,normal(seat));self.all_federal.add(key)
            ballot=self.ballots.setdefault(key,{'seats':1,'candidates':[],'source':source['url']})
            for li in general.find_all('li',recursive=False):
                a=li.find('a')
                if a:ballot['candidates'].append({'name':a.get_text(' ',strip=True),'party':li.get_text(' ',strip=True),'url':a.get('href')})
                marker=li.find('span',style=lambda style:style and '#6db24f' in style)
                if marker is None or not any(c in marker.get_text() for c in '✔✓'):continue
                self.called_federal.add(key);a=li.find('a')
                if a:self.add(a.get_text(' ',strip=True),state,kind,seat,li.get_text(' ',strip=True),'projected',[source['url'],a.get('href')])
        if not found:self.gap(state,kind,'All contests','The available source does not expose verified 2026 general-election winners',source['url'])
    def federal_voteboxes(self,source):
        if not source['ok']:return
        soup=BeautifulSoup((self.base/source['file']).read_text(),'html.parser');state=source['state'];kind='us_house'
        for box in soup.select('div.votebox'):
            heading=box.find(['h3','h4','h5']);title=heading.get_text(' ',strip=True) if heading else ''
            text=box.get_text(' ',strip=True)
            if not title.startswith('General election for U.S. House') or not re.search(r'on November 3, 2026\.',text):continue
            match=re.search(r'(?:District (\d+)|(At-large) District)',title);seat=(match.group(1) or 'At-large') if match else None
            if not seat:continue
            key=(state,kind,normal(seat));self.all_federal.add(key);self.pending.pop((state,kind,normal('All contests')),None)
            for row in box.select('tr.results_row.winner'):
                cell=row.select_one('.votebox-results-cell--text');a=cell.find('a') if cell else None
                if not a:continue
                self.called_federal.add(key)
                if re.search(r'\(D\)',cell.get_text(' ',strip=True)):self.add(a.get_text(' ',strip=True),state,kind,seat,'D','projected',[source['url'],a.get('href')])
    def district_results(self):
        # Statewide candidate indexes can lag behind the district's actual result table.
        # Match the exact office/seat and 2026 general election; never use a vote lead.
        for source in self.manifest['sources']:
            if source['kind'] not in ('district_results','us_senate') or not source['ok']:continue
            state=source['state'];kind='us_senate' if source['kind']=='us_senate' else source['chamber']
            if state=='NE' and kind.startswith('state_'):continue
            contests=source.get('contests') or [{'district':'Special election' if 'special_election' in source['url'] else 'Class 2','seats':1,'officeTitle':'U.S. Senate '+self.states[state]}]
            soup=BeautifulSoup((self.base/source['file']).read_text(),'html.parser',parse_only=SoupStrainer('div',class_='votebox'))
            for box in soup.select('div.votebox'):
                heading=box.find(['h3','h4','h5']);title=heading.get_text(' ',strip=True) if heading else ''
                intro=box.select_one('p.results_text') or box.find('p')
                if intro is None or not re.search(r'on November 3, 2026(?:\.|\s)',intro.get_text(' ',strip=True)):continue
                for contest in contests:
                    prefix=('Special general election for ' if kind=='us_senate' and contest['district']=='Special election' else 'General election for ')+contest['officeTitle']
                    if not title.startswith(prefix):continue
                    tail=title[len(prefix):].strip()
                    if kind=='us_senate':matches=not tail
                    else:matches=normal(district(tail))==normal(contest['district'])
                    if not matches:continue
                    winners=[]
                    for row in box.select('tr.results_row'):
                        # Ranked-choice tables explicitly mark the final winning round.
                        won_round=any(re.fullmatch(r'Won \(\d+\)',cell.get_text(' ',strip=True)) for cell in row.select('td.votebox-results-cell--number'))
                        if 'winner' not in row.get('class',[]) and not won_round:continue
                        cell=row.select_one('.votebox-results-cell--text');a=cell.find('a') if cell else None
                        if a is None:continue
                        text=cell.get_text(' ',strip=True);party=re.search(r'\((D|DFL|D-NPL|R|L|G|I|Independent|Republican|Democratic|Libertarian|Green)\)',text)
                        if not party:continue # Nonpartisan ballots require separate party evidence.
                        winners.append((a.get_text(' ',strip=True),party.group(1),a.get('href','')))
                    if not winners:continue
                    seat=contest['district'];key=(state,kind,normal(seat))
                    for name,party,href in winners:
                        if not code_party(party):continue
                        profile='https://ballotpedia.org'+href if href.startswith('/') else href
                        existing=next((m for m in self.members.values() if m['state']==state and m['chamber']==kind and normal(m['district'])==normal(seat) and (normal(m['name'])==normal(name) or profile in m['sources'])),None)
                        if existing:existing['sources']=list(dict.fromkeys(existing['sources']+[source['url'],profile]))
                        else:self.add(name,state,kind,seat,party,'projected',[source['url'],href])
                    if len(winners)>=contest['seats']:
                        self.pending.pop(key,None)
                        if kind.startswith('us_'):self.all_federal.add(key);self.called_federal.add(key)
                    else:self.gap(state,kind,seat,'Some winners verified; remaining places in this multi-member district not yet verified',source['url'])
    @staticmethod
    def tracker_races(page):
        # Decode public page hydration as data; never execute source JavaScript.
        soup=BeautifulSoup(page,'html.parser',parse_only=SoupStrainer('script'))
        for script in soup.find_all('script'):
            match=re.fullmatch(r'\s*self\.__next_f\.push\((.*)\);?\s*',script.string or '',re.S)
            if not match:continue
            frame=json.loads(match.group(1))
            if len(frame)<2 or not isinstance(frame[1],str):continue
            for start in re.finditer(r'"races":\s*\[',frame[1]):
                races=json.JSONDecoder().raw_decode(frame[1][start.end()-1:])[0]
                if isinstance(races,list):yield from races
    def called_state_tracker(self):
        filename='mvp-state-results.html';path=self.base/filename
        if not path.exists() or filename not in self.sources:return
        source=self.source(filename)
        if source!='https://movement.vote/election-results/2026/races/state-legislatures/':raise ValueError('Unexpected state tracker election scope')
        for race in self.tracker_races(path.read_text()):
            if race.get('type')!='state-legislatures':continue
            card=race.get('card',{});match=re.fullmatch(r'([A-Z]{2}) (House|Senate|Assembly) (.+)',card.get('title',''))
            if not match or not card.get('foot','').startswith('Called by AP at '):continue
            state,office,seat=match.groups();kind='state_senate' if office=='Senate' else 'state_house'
            seat=re.sub(r'\b0+(\d+)',r'\1',district(seat));seat=re.sub(r'\s+District\s+',' ',seat,flags=re.I);key=(state,kind,normal(seat));ballot=self.ballots.get(key)
            href=card.get('href','')
            if race.get('state')!=state or card.get('stateName')!=self.states.get(state) or not href.startswith('/election-results/2026/') or not ballot:continue
            winners=[]
            for candidate in card.get('cands',[]):
                if candidate.get('winner') is not True or candidate.get('party') not in ('Dem','GOP'):continue
                party='D' if candidate['party']=='Dem' else 'R'
                matches=[c for c in ballot['candidates'] if (code_party(c['party']) is not None if party=='D' else c['party'].startswith('Republican')) and (normal(c['name'])==normal(candidate['name']) or self.person_key(c['name'])==self.person_key(candidate['name']))]
                if len(matches)!=1:continue
                person=matches[0];winners.append(normal(person['name']))
                if party=='D':
                    name=person['name'];profile=person['url'];profile='https://ballotpedia.org'+profile if profile.startswith('/') else profile
                    existing=next((m for m in self.members.values() if m['state']==state and m['chamber']==kind and normal(m['district'])==normal(seat) and (normal(m['name'])==normal(name) or profile in m['sources'])),None)
                    refs=['https://movement.vote'+href,source,ballot['source'],profile]
                    if existing:existing['sources']=list(dict.fromkeys(existing['sources']+refs))
                    else:self.add(name,state,kind,seat,person['party'],'projected',refs)
            if len(set(winners))>=ballot['seats']:self.pending.pop(key,None)
    def called_house_tracker(self):
        filename='mvp-house-results.html';path=self.base/filename
        if not path.exists() or filename not in self.sources:return
        source=self.source(filename)
        if source!='https://movement.vote/election-results/2026/races/us-house/':raise ValueError('Unexpected House tracker election scope')
        for race in self.tracker_races(path.read_text()):
            card=race.get('card',{});match=re.fullmatch(r'U\.S\. House ([A-Z]{2})-(\d+|AL)',card.get('title',''))
            if race.get('type')!='us-house' or not match or not card.get('foot','').startswith('Called by AP at '):continue
            state,seat=match.groups();seat='At-large' if seat=='AL' else str(int(seat));key=(state,'us_house',normal(seat));ballot=self.ballots.get(key)
            href=card.get('href','')
            if not ballot or race.get('state')!=state or card.get('stateName')!=self.states.get(state) or not href.startswith('/election-results/2026/'):continue
            winners=[c for c in card.get('cands',[]) if c.get('winner') is True and c.get('party') in ('Dem','GOP')]
            if len(winners)!=1:continue
            candidate=winners[0];party='D' if candidate['party']=='Dem' else 'R'
            matches=[c for c in ballot['candidates'] if (code_party(c['party']) is not None if party=='D' else 'Republican' in c['party']) and (normal(c['name'])==normal(candidate['name']) or self.person_key(c['name'])==self.person_key(candidate['name']))]
            if len(matches)!=1:continue
            person=matches[0];self.all_federal.add(key);self.called_federal.add(key);self.pending.pop(key,None)
            if party=='D':
                name=person['name'];profile=person['url'];profile='https://ballotpedia.org'+profile if profile.startswith('/') else profile
                existing=next((m for m in self.members.values() if m['state']==state and m['chamber']=='us_house' and normal(m['district'])==normal(seat)),None)
                refs=['https://movement.vote'+href,source,ballot['source'],profile]
                if existing:
                    if normal(existing['name'])!=normal(name) and profile not in existing['sources']:raise ValueError('Conflicting projected House winners: '+state+' '+seat)
                    existing['sources']=list(dict.fromkeys(existing['sources']+refs))
                else:self.add(name,state,'us_house',seat,person['party'],'projected',refs)
    def nebraska(self):
        source='https://ballotpedia.org/Nebraska_State_Senate_elections,_2026';party_source='https://en.wikipedia.org/wiki/Nebraska_Legislature'
        parties={};soup=BeautifulSoup((self.base/'ne-members.html').read_text(),'html.parser')
        for table in soup.find_all('table'):
            if 'Next Election' not in table.get_text():continue
            for row in table.find_all('tr'):
                cells=row.find_all('td',recursive=False)
                if len(cells)<6:continue
                name=cells[1].get_text(' ',strip=True);party=cells[2].get_text(' ',strip=True);seat=cells[0].get_text(' ',strip=True)
                parties[normal(name)]=party
                if code_party(party) and cells[5].get_text(' ',strip=True).startswith('2028') and ('NE',self.person_key(name)) not in self.exiting:self.add(name,'NE','state_senate',seat,party,'holdover',[party_source])
        soup=BeautifulSoup((self.base/'NE-state-upper.html').read_text(),'html.parser');found=False
        for table in soup.find_all('table'):
            title=table.find('h4')
            if not title or title.get_text(' ',strip=True)!='Nebraska State Senate general election 2026':continue
            found=True
            for row in table.find_all('tr'):
                cells=row.find_all('td',recursive=False)
                if len(cells)!=2 or not cells[0].get_text(strip=True).startswith('District '):continue
                seat=district(cells[0].get_text(' ',strip=True));marker=cells[1].find('img',alt=lambda a:a and 'Green check mark' in a)
                if marker is None:self.gap('NE','state_senate',seat,'General-election winner not yet marked in the source',source);continue
                a=marker.find_next_sibling('a')
                if not a:self.gap('NE','state_senate',seat,'Winner name could not be verified',source);continue
                name=a.get_text(' ',strip=True);party=parties.get(normal(name));refs=[source,party_source]
                profile=self.base/('ne-party-'+normal(name)+'.html')
                if not party and profile.exists():
                    person=BeautifulSoup(profile.read_text(),'html.parser');label=person.find(string=lambda t:t and t.strip()=='Political party')
                    text=label.parent.parent.get_text(' ',strip=True) if label else ''
                    party='Democratic' if 'Democratic' in text else 'Republican' if 'Republican' in text else None;refs=[source,a['href']]
                if party:self.add(name,'NE','state_senate',seat,party,'projected',refs)
                else:self.gap('NE','state_senate',seat,'Winner verified; party on this nonpartisan ballot not yet verified',source)
        if found:self.pending.pop(('NE','state_senate',normal('All contests')),None)
    def pride_supplements(self):
        profiles=[('Joanna Whaley','MI','state_house','vf-whaley.html','https://victoryfund.org/candidate/joanna-whaley/',['bisexual','transgender']),('Bentley Hudgins','GA','state_house','vf-hudgins.html','https://victoryfund.org/candidate/bentley-hudgins/',['transgender']),('Jeremy Moss','MI','us_house','vf-moss.html','https://victoryfund.org/candidate/jeremy-moss/',['rainbow']),('J.D. Ford','IN','us_house','vf-ford.html','https://victoryfund.org/candidate/ford-j-d/',['rainbow']),('Marni von Wilpert','CA','us_house','vf-von-wilpert.html','https://victoryfund.org/candidate/marni-von-wilpert/',['bisexual'])]
        for name,state,kind,filename,url,flags in profiles:
            if not (self.base/filename).exists():continue
            text=BeautifulSoup((self.base/filename).read_text(),'html.parser').get_text(' ',strip=True)
            for member in self.members.values():
                if member['state']!=state or member['chamber']!=kind or normal(member['name'])!=normal(name):continue
                verified=[f for f in flags if (f=='rainbow' and 'Sexual Orientation Gay' in text) or (f=='bisexual' and 'Sexual Orientation Bisexual' in text) or (f=='transgender' and ('Gender Identity Transgender' in text or 'first trans person elected' in text))]
                for flag in verified:
                    if flag not in member['flags']:member['flags'].append(flag)
                    member['identitySources'][flag]=[url]
                member['identityEvidence']={'name':name,'flags':verified,'source':url}
        path=self.base/'dina-nina-campaign.html';url='https://www.votedinanina.com/'
        if path.exists() and 'proud transgender woman' in BeautifulSoup(path.read_text(),'html.parser').get_text(' ',strip=True):
            for member in self.members.values():
                if member['state']=='WI' and member['chamber']=='state_house' and normal(member['name'])==normal('Dina Nina Martinez-Rutherford'):
                    if 'transgender' not in member['flags']:member['flags'].append('transgender')
                    member['identitySources']['transgender']=[url]
                    member['identityEvidence']={'name':member['name'],'flags':['transgender'],'source':url}
    def governors(self):
        filename='gov-2026.html';url='https://ballotpedia.org/Gubernatorial_elections,_2026';soup=BeautifulSoup((self.base/filename).read_text(),'html.parser');all_seats=set();called=set()
        states=dict(self.states,GU='Guam',MP='Northern Mariana Islands',VI='U.S. Virgin Islands')
        for row in soup.select('tr'):
            candidate=row.select_one('td[data-cell="candidate"]');party=row.select_one('td[data-cell="party"]');office=row.select_one('td[data-cell="office"]');status=row.select_one('td[data-cell="status"]')
            if not all([candidate,party,office,status]):continue
            state=next((code for code,name in states.items() if re.search(r'\b'+re.escape(name)+r'\b',office.get_text(' ',strip=True))),None)
            if not state:continue
            all_seats.add(state)
            if status.get_text(' ',strip=True)!='Won General':continue
            called.add(state);a=candidate.select_one('.widget-candidate-info a') or candidate.find('a')
            if a:self.add(a.get_text(' ',strip=True),state,'governor','',party.get_text(' ',strip=True),'projected',[url,a.get('href')])
        for state in all_seats-called:self.gap(state,'governor','','Gubernatorial result not yet called or verified',url)
    def governor_flags(self):
        profiles=[('Maura Healey','MA','governor-healey.html','https://www.mass.gov/news/governor-healey-touts-lgbtq-equality-economic-partnership-as-first-us-governor-to-address-irish-senate'),('Tina Kotek','OR','governor-kotek.html','https://www.oregon.gov/gov/Pages/meet-the-governor.aspx')]
        for name,state,filename,url in profiles:
            path=self.base/filename
            if not path.exists() or 'openly lesbian' not in BeautifulSoup(path.read_text(),'html.parser').get_text(' ',strip=True):continue
            for member in self.members.values():
                if member['chamber']=='governor' and member['name']==name and member['state']==state:
                    member['flags']=['rainbow'];member['identitySources']={'rainbow':[url]};member['identityEvidence']={'name':name,'sexualOrientation':['Lesbian'],'source':url}
    def nbc(self):
        path=self.base/'nbc-ny-results.html'
        if not path.exists():return
        soup=BeautifulSoup(path.read_text(),'html.parser');embed=soup.select_one('[data-initial-races]')
        if embed is None:return
        url='https://www.nbcnewyork.com/elections/live-results-2026-ny-nj-ct-midterm-elections/6563336/'
        races=json.loads(embed['data-initial-races'])
        for race in races:
            title=html.unescape(race.get('title',{}).get('rendered',''));match=re.search(r'^(NY|NJ|CT)\s*[–—-]\s*(House|Senate)\s*(\d*)',title)
            if not match:continue
            state,office,number=match.groups();kind='us_house' if office=='House' else 'us_senate';seat=number if number else 'Class 2';key=(state,kind,normal(seat));self.all_federal.add(key)
            details=race.get('race_details',{})
            if details.get('race_call_status')!='Projected Winner':continue
            for c in details.get('candidates',[]):
                if str(c.get('winner',''))!='1' or c.get('call_status')!='Projected Winner':continue
                self.called_federal.add(key)
                if c.get('party')=='(D)':
                    name=c['name'];old=next((m for m in self.members.values() if m['state']==state and m['chamber']==kind and normal(m['district'])==normal(seat)),None)
                    if old:old['sources']=list(dict.fromkeys([url]+old['sources']))
                    else:self.add(name,state,kind,seat,'D','projected',[url])
    @staticmethod
    def name_keys(name):
        name=html.unescape(name).replace('“','"').replace('”','"').replace('‘',"'").replace('’',"'")
        keys={normal(name)}
        without=re.sub(r'(["\']).*?\1','',name);keys.add(normal(without))
        tokens=without.split();keys.add(normal(' '.join(t for t in tokens if len(t.rstrip('.'))>1)))
        for nickname in re.findall(r'["\']([^"\']+)["\']',name):
            if tokens:keys.add(normal(nickname+' '+tokens[-1]))
        return keys
    @staticmethod
    def published_name(record):
        if record.get('name'):return record['name']
        title=record.get('title','')
        if ',' in title:
            surname,given=title.split(',',1);return given.strip()+' '+surname.strip()
        return title
    def pride(self):
        path=self.base/'out-legislators.json'
        if not path.exists():return
        out=json.loads(path.read_text())['hits'];indexed=defaultdict(list)
        for record in out:
            match=re.search(r'\(([A-Z]{2})\)',(record.get('region') or ''))
            if not match:continue
            state=match.group(1)
            for key in self.name_keys(self.published_name(record)):indexed[(state,key)].append(record)
        for member in self.members.values():
            if member['chamber']=='governor':continue
            candidates={r['slug']:r for key in self.name_keys(member['name']) for r in indexed.get((member['state'],key),[]) if ('US Congress' in r.get('office_level',[]))==member['chamber'].startswith('us_')}
            if len(candidates)!=1:continue
            record=next(iter(candidates.values()));flags=[]
            if any(v in ('Gay','Lesbian') for v in record.get('sexual_orientation',[])):flags.append('rainbow')
            if 'Bisexual' in record.get('sexual_orientation',[]):flags.append('bisexual')
            if any('transgender' in v.lower() for v in record.get('gender',[])):flags.append('transgender')
            member['flags']=flags;member['identitySources']={flag:['https://outforamerica.org/'] for flag in flags}
            if flags:member['identityEvidence']={'name':self.published_name(record),'slug':record['slug'],'sexualOrientation':record.get('sexual_orientation',[]),'genderIdentity':record.get('gender',[]),'source':'https://outforamerica.org/'}
    @staticmethod
    def evidence_text(value):
        return re.sub(r'\s+',' ',unicodedata.normalize('NFKC',html.unescape(value)).replace('’',"'").replace('‘',"'")).strip().casefold()
    def identity_evidence(self,path=None):
        path=path or Path(__file__).with_name('identity-evidence.json')
        if not path.exists():return
        supplement=json.loads(path.read_text())
        if supplement.get('schema')!=1:raise ValueError('Unsupported identity-evidence schema')
        for row in supplement['members']:
            names=row['names'];aliases={key for name in names for key in self.name_keys(name)}
            if (len(names)>1 or row.get('displayName')) and not row.get('nameSources'):raise ValueError('An identity/name alias requires public name evidence')
            matches=[member for member in self.members.values() if member['state']==row['state'] and member['chamber']==row['chamber'] and normal(member['district'])==normal(row['district']) and self.name_keys(member['name'])&aliases]
            if not matches:continue # A candidate profile never supplies election/continuing-term evidence.
            for member in matches:
                for flag,proofs in row['flags'].items():
                    if flag not in ('rainbow','bisexual','transgender') or not proofs:raise ValueError('Invalid identity evidence flag')
                    refs=[]
                    for proof in proofs:
                        url=proof['url'];cached=self.base/proof['cacheFile']
                        if not url.startswith('https://') or not proof.get('requiredText'):raise ValueError('Identity evidence requires a public source and explicit statement')
                        if not cached.exists():raise ValueError('Missing verified identity evidence: '+str(cached))
                        text=BeautifulSoup(cached.read_text(),'html.parser').get_text(' ',strip=True)
                        if self.evidence_text(proof['requiredText']) not in self.evidence_text(text):raise ValueError('Public self-identification evidence no longer matches: '+url)
                        refs.append(url);self.sources[cached.name]={'url':url,'sha256':hashlib.sha256(cached.read_bytes()).hexdigest(),'identityVerifiedDate':supplement['verifiedDate'],'excerpt':'.excerpt.' in cached.name}
                    if flag not in member['flags']:member['flags'].append(flag)
                    member['identitySources'][flag]=list(dict.fromkeys(member['identitySources'].get(flag,[])+refs))
                member['sources']=list(dict.fromkeys(member['sources']+row.get('nameSources',[])))
                if row.get('displayName'):member['name']=row['displayName']
                member['supplementalIdentityEvidence']={'names':names,'verifiedDate':supplement['verifiedDate'],'sources':row['flags']}
    def holdovers(self):
        # Current CSVs identify current holders only; use them for seats known not to be on the ballot.
        for state,people in self.current.items():
            for person in people:
                kind='state_senate' if person['current_chamber']=='upper' or state=='NE' else 'state_house';seat=person['current_district']
                off_cycle=state in (OFF_UPPER if kind=='state_senate' else OFF_LOWER)
                if not off_cycle and (kind=='state_house' and state!='ND' or kind=='state_senate' and state in ALL_UPPER):continue
                if not off_cycle and ((state,kind) not in self.parsed or normal(seat) in self.contested[(state,kind)]):continue
                if (state,self.person_key(person['name'])) in self.exiting:continue
                links=[self.source(state+'-current.csv')]
                links.extend(v.strip() for v in person.get('sources','').split(';') if v.strip().startswith(('http://','https://')))
                self.add(person['name'],state,kind,seat,person['current_party'],'holdover',links)
        for person in yaml.safe_load((self.base/'senate-current.yaml').read_text()):
            term=person['terms'][-1]
            if term['type'] not in ('sen','rep') or term.get('party')!='Democrat' or str(term['end'])<='2027-01-03':continue
            name=person['name'].get('official_full') or person['name']['first']+' '+person['name']['last'];state=term['state'];kind='us_senate' if term['type']=='sen' else 'us_house'
            if (state,self.person_key(name)) in self.exiting:
                self.gap(state,kind,'Class '+str(term.get('class','')),'Current senator elected to another office; successor not yet verified','https://ballotpedia.org/Gubernatorial_elections,_2026');continue
            self.add(name,state,kind,'Class '+str(term.get('class','')) if kind=='us_senate' else str(term.get('district','')),term['party'],'holdover',['https://github.com/unitedstates/congress-legislators/blob/main/legislators-current.yaml',term.get('url','https://www.congress.gov/')])
    def output(self,path):
        for state,kind,seat in self.all_federal-self.called_federal:self.gap(state,kind,seat,'General-election result not yet called or verified', 'https://ballotpedia.org/United_States_Senate_elections,_2026' if kind=='us_senate' else 'https://ballotpedia.org/United_States_House_of_Representatives_elections,_2026')
        for state,kind,seat in self.called_federal:self.pending.pop((state,kind,seat),None)
        people=list(self.members.values());gaps=list(self.pending.values())
        date=datetime.date.fromisoformat(self.manifest.get('snapshotDate','2026-11-04'));label=date.strftime('%B')+' '+str(date.day)+', '+str(date.year)
        checked=max([v for v in [self.manifest.get('retrievedAt')]+[source.get('retrievedAt') for source in self.sources.values()] if v],default=None)
        data={'schema':1,'scope':'post-2026','snapshotLabel':label,'sourcesCheckedAt':checked,'complete':False,'note':'Verified projected winners and continuing terms after the 2026 election. Results are provisional; unresolved or not-yet-verified contests are noted. This is not a certified or complete roster.','members':people,'unresolved':gaps,'researchSources':self.sources,'coverage':{'states':list(self.states),'membersByChamber':dict(Counter(m['chamber'] for m in people)),'unresolvedByChamber':dict(Counter(m['chamber'] for m in gaps))}}
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        print('Members',len(people),data['coverage']['membersByChamber'],'unresolved',len(gaps),data['coverage']['unresolvedByChamber'])
        return data

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--snapshot-dir',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    b=Builder(args.snapshot_dir);b.parse_states();b.hawaii_outright();b.federal_widgets('us-senate-2026.html','us_senate');
    for source in b.manifest['sources']:
        if source['kind']=='us_house':b.federal_house_bullets(source);b.federal_voteboxes(source)
    b.nbc();b.governors();b.district_results();b.called_state_tracker();b.called_house_tracker();b.departures();b.holdovers();b.nebraska();b.pride();b.pride_supplements();b.governor_flags();b.identity_evidence();b.output(args.output)
if __name__=='__main__':main()
