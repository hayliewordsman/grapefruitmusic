import csv,io,json,tempfile,unittest
from pathlib import Path
from build_snapshot import Builder,district

class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.base=Path(self.tmp.name)
        self.manifest={'states':{'NY':'New York','DE':'Delaware','MI':'Michigan','VA':'Virginia'},'sources':[]}
        (self.base/'source-manifest.json').write_text(json.dumps(self.manifest));(self.base/'senate-current.yaml').write_text('[]')
    def tearDown(self):self.tmp.cleanup()
    def builder(self):return Builder(self.base)
    def test_nbc_never_promotes_an_uncalled_leader(self):
        races=[]
        for seat,called in [('17',False),('18',True)]:
            races.append({'title':{'rendered':'NY – House '+seat},'race_details':{'race_call_status':'Projected Winner' if called else 'Too Early to Call','candidates':[{'name':'Verified Winner' if called else 'Uncalled Leader','party':'(D)','winner':'1','call_status':'Projected Winner'}]}})
        from html import escape
        (self.base/'nbc-ny-results.html').write_text('<div data-initial-races="'+escape(json.dumps(races),quote=True)+'"></div>')
        b=self.builder();b.nbc();self.assertEqual(['Verified Winner'],[m['name'] for m in b.members.values()])
    def test_at_large_votebox_requires_2026_general_election(self):
        source={'state':'DE','kind':'us_house','file':'house.html','url':'https://example.org/elections','ok':True}
        boxes=[]
        for heading,date,name in [('General election for U.S. House Delaware At-large District','November 3, 2026','General Winner'),('Democratic primary for U.S. House Delaware At-large District','November 3, 2026','Primary Winner'),('General election for U.S. House Delaware At-large District','November 5, 2024','Old Winner')]:
            boxes.append('<div class="votebox"><h3>'+heading+'</h3><p>Election on '+date+'.</p><table><tr class="results_row winner"><td class="votebox-results-cell--text"><a href="https://example.org/person">'+name+'</a> (D)</td></tr></table></div>')
        (self.base/'house.html').write_text(''.join(boxes));b=self.builder();b.federal_voteboxes(source)
        self.assertEqual(['General Winner'],[m['name'] for m in b.members.values()])
    def test_current_incumbency_does_not_prove_reelection(self):
        b=self.builder();person={'name':'Unverified Incumbent','current_chamber':'lower','current_district':'5','current_party':'Democratic'}
        b.current={'MI':[person],'VA':[dict(person,name='Continuing Term')]};b.sources={'MI-current.csv':{'url':'https://example.org/mi'},'VA-current.csv':{'url':'https://example.org/va'}};b.parsed.add(('MI','state_house'))
        b.holdovers();self.assertEqual(['Continuing Term'],[m['name'] for m in b.members.values()])
    def test_same_name_in_different_office_gets_no_inferred_flag(self):
        (self.base/'out-legislators.json').write_text(json.dumps({'hits':[{'name':'Robert Garcia','slug':'robert','region':'California (CA)','office_level':['US Congress'],'sexual_orientation':['Gay'],'gender':['Cisgender Man']}]}))
        b=self.builder();b.add('Robert Garcia','CA','us_house','42','D','projected',['https://example.org']);b.add('Robert Garcia','CA','state_house','50','D','projected',['https://example.org']);b.pride()
        self.assertEqual(['rainbow'],next(m['flags'] for m in b.members.values() if m['chamber']=='us_house'));self.assertEqual([],next(m['flags'] for m in b.members.values() if m['chamber']=='state_house'))
    def test_governors_require_a_called_general_election(self):
        rows=[]
        for state,status,name in [('New York','Won General','Called Governor'),('Michigan','Won Primary','Uncalled Candidate')]:
            rows.append('<tr><td data-cell="candidate"><a href="https://example.org/person">'+name+'</a></td><td data-cell="party">Democratic</td><td data-cell="office">Governor of '+state+'</td><td data-cell="status">'+status+'</td></tr>')
        (self.base/'gov-2026.html').write_text('<table>'+''.join(rows)+'</table>');b=self.builder();b.governors()
        self.assertEqual(['Called Governor'],[m['name'] for m in b.members.values()]);self.assertEqual('Governor-elect',next(iter(b.members.values()))['office']);self.assertEqual('MI',next(iter(b.pending.values()))['state'])
    def test_snapshot_uses_checked_date_from_manifest(self):
        self.manifest['snapshotDate']='2026-11-05';self.manifest['retrievedAt']='2026-11-05T15:00:00+00:00';(self.base/'source-manifest.json').write_text(json.dumps(self.manifest));b=self.builder();data=b.output(self.base/'result.json');self.assertEqual('November 5, 2026',data['snapshotLabel']);self.assertEqual(self.manifest['retrievedAt'],data['sourcesCheckedAt'])
    def evidence(self,**changes):
        row={'names':['Verified Person'],'state':'CA','chamber':'state_house','district':'25','flags':{'transgender':[{'url':'https://example.org/self','cacheFile':'proof.html','requiredText':'I am trans and nonbinary'}]}};row.update(changes);path=self.base/'evidence.json';path.write_text(json.dumps({'schema':1,'verifiedDate':'2026-11-05','members':[row]}));(self.base/'proof.html').write_text('<p>I am trans and nonbinary</p>');return path
    def test_identity_evidence_matches_office_and_district_and_never_adds_candidates(self):
        b=self.builder();path=self.evidence();b.identity_evidence(path);self.assertEqual({},b.members)
        for kind,seat in [('state_house','25'),('state_house','26'),('us_house','25')]:b.add('Verified Person','CA',kind,seat,'D','projected',['https://example.org/result'])
        b.identity_evidence(path)
        self.assertEqual([('state_house','25')],[(m['chamber'],m['district']) for m in b.members.values() if m['flags']])
    def test_alias_requires_public_name_evidence(self):
        b=self.builder();path=self.evidence(names=['Verified Person','Verified Alias'])
        with self.assertRaises(ValueError):b.identity_evidence(path)
        path=self.evidence(names=['Verified Person','Verified Alias'],nameSources=['https://example.org/public-alias']);b.add('Verified Alias','CA','state_house','25','D','projected',['https://example.org/result']);b.identity_evidence(path);self.assertEqual(['transgender'],next(iter(b.members.values()))['flags'])
    def test_nonbinary_label_does_not_automatically_imply_a_transgender_flag(self):
        (self.base/'out-legislators.json').write_text(json.dumps({'hits':[{'name':'Verified Person','slug':'verified','region':'California (CA)','office_level':['State Legislature'],'sexual_orientation':['Queer'],'gender':['Genderqueer/Non-Binary']}]}));b=self.builder();b.add('Verified Person','CA','state_house','25','D','projected',['https://example.org/result']);b.pride();self.assertEqual([],next(iter(b.members.values()))['flags'])
        path=self.evidence();(self.base/'proof.html').write_text('<p>I am nonbinary</p>')
        with self.assertRaises(ValueError):b.identity_evidence(path)
    def test_canonical_sources_keep_their_fresh_retrieval_provenance(self):
        filename='ne-party-person.html';(self.base/filename).write_text('<link rel="canonical" href="https://example.org/person"><p>Public party evidence</p>');self.manifest['sources']=[{'file':filename,'url':'https://example.org/person','kind':'research','ok':True,'fresh':True,'retrievedAt':'2026-11-05T16:00:00+00:00'}];self.manifest['retrievedAt']='2026-11-05T15:00:00+00:00';(self.base/'source-manifest.json').write_text(json.dumps(self.manifest));b=self.builder();self.assertTrue(b.sources[filename]['fresh']);self.assertEqual('2026-11-05T16:00:00+00:00',b.output(self.base/'result.json')['sourcesCheckedAt'])
    def test_multimember_labels_have_stable_districts(self):self.assertEqual('5',district('District 5 (2 seats)'))
    def district_builder(self,boxes,contests=None,kind='state_house',office='New York State Assembly'):
        source={'state':'NY','kind':'district_results','chamber':kind,'file':'district.html','url':'https://example.org/district','ok':True,'contests':contests or [{'district':'5','seats':1,'officeTitle':office}]}
        (self.base/'district.html').write_text(''.join(boxes));self.manifest['sources']=[source];(self.base/'source-manifest.json').write_text(json.dumps(self.manifest));return self.builder()
    def result_box(self,name,seat='5',party='D',winner=True,heading='General election',year='2026',round_won=False):
        mark=' winner' if winner else '';result='<td class="votebox-results-cell--number">Won (2)</td>' if round_won else ''
        return '<div class="votebox"><h5>'+heading+' for New York State Assembly District '+seat+'</h5><p class="results_text">Election on November 3, '+year+'.</p><table><tr class="results_row'+mark+'"><td class="votebox-results-cell--text"><a href="https://example.org/'+name+'">'+name+'</a> ('+party+')</td>'+result+'</tr></table></div>'
    def test_district_results_require_exact_office_seat_year_and_winner(self):
        boxes=[self.result_box('Winner'),self.result_box('Other Seat',seat='6'),self.result_box('Primary',heading='Democratic primary'),self.result_box('Old',year='2024'),self.result_box('Leader',winner=False)]
        b=self.district_builder(boxes);b.gap('NY','state_house','5','Unverified','https://example.org/index');b.district_results();self.assertEqual(['Winner'],[m['name'] for m in b.members.values()]);self.assertFalse(b.pending)
    def test_old_result_is_not_dated_by_a_later_footnote(self):
        box=self.result_box('Old',year='2024').replace('</div>','<p>A rematch was held on November 3, 2026.</p></div>');b=self.district_builder([box]);b.district_results();self.assertFalse(b.members)
    def test_ranked_choice_explicit_winning_round_qualifies(self):
        b=self.district_builder([self.result_box('RCV Winner',winner=False,round_won=True)]);b.district_results();self.assertEqual(['RCV Winner'],[m['name'] for m in b.members.values()])
    def test_partial_multimember_win_keeps_a_gap(self):
        contest={'district':'5','seats':2,'officeTitle':'New York State Assembly'};b=self.district_builder([self.result_box('First Winner')],[contest]);b.district_results();self.assertEqual(1,len(b.members));self.assertEqual(1,len(b.pending))
        second=self.result_box('Second Winner').split('<table>',1)[1].split('</table>',1)[0];box=self.result_box('First Winner').replace('</table>',second+'</table>');b=self.district_builder([box],[contest]);b.district_results();self.assertEqual(2,len(b.members));self.assertFalse(b.pending)
    def test_republican_win_closes_coverage_gap_without_adding_a_democrat(self):
        b=self.district_builder([self.result_box('Republican',party='R')]);b.gap('NY','state_house','5','Unverified','https://example.org/index');b.district_results();self.assertFalse(b.members);self.assertFalse(b.pending)
    def test_district_profile_match_merges_spelling_variants(self):
        b=self.district_builder([self.result_box('Jane Middle Doe')]);b.add('Jane Doe','NY','state_house','5','D','projected',['https://example.org/index','https://example.org/Jane Middle Doe']);b.district_results();self.assertEqual(1,len(b.members));self.assertIn('https://example.org/district',next(iter(b.members.values()))['sources'])
    def test_special_senate_general_is_included_but_special_primary_is_not(self):
        general=self.result_box('Special Winner',heading='Special general election').replace('New York State Assembly District 5','U.S. Senate New York');primary=general.replace('Special general election','Special Democratic primary').replace('Special Winner','Primary Winner')
        b=self.district_builder([general,primary],[{'district':'Special election','seats':1,'officeTitle':'U.S. Senate New York'}],kind='us_senate');b.district_results();self.assertEqual(['Special Winner'],[m['name'] for m in b.members.values()])
    def tracker_builder(self,foot='Called by AP at 1:00 am ET, November 4',winner=True,party='Dem',name='Jane Doe',seats=1):
        race={'type':'state-legislatures','state':'NY','card':{'title':'NY Assembly District 05','stateName':'New York','href':'/election-results/2026/new-york/state-house-district-5/','foot':foot,'cands':[{'name':name,'party':party,'winner':winner}]}}
        frame='bc:'+json.dumps({'races':[race]});page='<script>self.__next_f.push('+json.dumps([1,frame])+')</script>'
        (self.base/'mvp-state-results.html').write_text(page);self.manifest['sources']=[{'file':'mvp-state-results.html','url':'https://movement.vote/election-results/2026/races/state-legislatures/','state':'','kind':'called_state_results','ok':True}];(self.base/'source-manifest.json').write_text(json.dumps(self.manifest));b=self.builder();b.ballots[('NY','state_house','5')]={'seats':seats,'candidates':[{'name':'Jane M. Doe','party':'Democratic','url':'https://example.org/jane'}],'source':'https://example.org/general-2026'};b.gap('NY','state_house','5','Unverified','https://example.org/general-2026');return b
    def test_tracker_requires_explicit_call_winner_and_matching_general_ballot(self):
        b=self.tracker_builder();b.called_state_tracker();self.assertEqual(['Jane M. Doe'],[m['name'] for m in b.members.values()]);self.assertFalse(b.pending)
        for args in [{'foot':'Not called yet · 99% counted'},{'winner':False},{'name':'Other Candidate'},{'party':'GOP'}]:
            b=self.tracker_builder(**args);b.called_state_tracker();self.assertFalse(b.members);self.assertTrue(b.pending)
    def test_tracker_partial_multimember_call_does_not_complete_the_contest(self):
        b=self.tracker_builder(seats=2);b.called_state_tracker();self.assertEqual(1,len(b.members));self.assertTrue(b.pending)
    def test_house_tracker_never_calls_a_leader_or_primary_only_candidate(self):
        filename='mvp-house-results.html';source={'file':filename,'url':'https://movement.vote/election-results/2026/races/us-house/','state':'','kind':'research','ok':True};self.manifest['sources']=[source]
        for foot,name,expected in [('Called by AP at noon ET','Jane Doe',1),('Not called yet · 99% counted','Jane Doe',0),('Called by AP at noon ET','Primary Candidate',0)]:
            race={'type':'us-house','state':'NY','card':{'title':'U.S. House NY-05','stateName':'New York','href':'/election-results/2026/new-york/us-house-ny-05/','foot':foot,'cands':[{'name':name,'party':'Dem','winner':True}]}}
            (self.base/filename).write_text('<script>self.__next_f.push('+json.dumps([1,'bc:'+json.dumps({'races':[race]})])+')</script>');(self.base/'source-manifest.json').write_text(json.dumps(self.manifest));b=self.builder();b.ballots[('NY','us_house','5')]={'seats':1,'candidates':[{'name':'Jane M. Doe','party':'Democratic','url':'https://example.org/jane'}],'source':'https://example.org/general-2026'};b.called_house_tracker();self.assertEqual(expected,len(b.members));self.assertEqual(expected,len(b.called_federal))

if __name__=='__main__':unittest.main()
