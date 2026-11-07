import copy,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from refresh_feed import compact_snapshot,download_source,fetch_source,update_from_trackers
from build_snapshot import Builder

class PublicResponse:
    status=200
    def __init__(self,data):self.data=data
    def read(self,limit):return self.data[:limit]
    def __enter__(self):return self
    def __exit__(self,*args):pass

class FeedTest(unittest.TestCase):
    def test_empty_success_response_retries_without_accepting_empty_evidence(self):
        with patch('refresh_feed.urllib.request.urlopen',side_effect=[PublicResponse(b''),PublicResponse(b'<html>'+b'Public election results '*8+b'</html>')]) as request:
            data=download_source('https://example.org/results');self.assertIn(b'Public election',data)
            self.assertEqual(2,request.call_count);self.assertIn('blue_roll_call=',request.call_args.args[0].full_url)
    def test_failed_download_preserves_previous_source_and_marks_it_stale(self):
        with tempfile.TemporaryDirectory() as directory:
            cache=Path(directory);(cache/'result.html').write_text('Previous verified evidence')
            with patch('refresh_feed.urllib.request.urlopen',return_value=PublicResponse(b'')),patch('refresh_feed.subprocess.run') as curl:
                curl.return_value.returncode=22
                source=fetch_source({'file':'result.html','kind':'us_house','url':'https://example.org/results'},cache)
            self.assertFalse(source['fresh']);self.assertTrue(source['ok']);self.assertEqual('Previous verified evidence',(cache/'result.html').read_text())
    def snapshot(self):
        return {'members':[{'id':str(n),'name':'Member '+str(n),'state':'GA','chamber':'state_house','district':str(n),'partyCode':'D','office':'State Representative','status':'projected','sources':['https://example.org/result'],'flags':[],'identitySources':{},'unusedContact':'never publish'} for n in range(3000)],'unresolved':[],'coverage':{'states':['GA']},'researchSources':{'raw':'never publish'}}
    def test_runtime_feed_omits_research_and_unused_contact_fields(self):
        snapshot=self.snapshot();result=compact_snapshot(snapshot,copy.deepcopy(snapshot));self.assertNotIn('researchSources',result);self.assertNotIn('unusedContact',result['members'][0]);self.assertGreater(result['feedUpdatedAt'],0)
    def test_identity_labels_follow_only_exact_previously_verified_members(self):
        snapshot=self.snapshot();previous=copy.deepcopy(snapshot);previous['members'][0].update(flags=['transgender'],identitySources={'transgender':['https://example.org/public-self-description']});previous['members'].append(dict(previous['members'][0],id='candidate-only'))
        result=compact_snapshot(snapshot,previous);self.assertEqual(['transgender'],result['members'][0]['flags']);self.assertEqual(3000,len(result['members']));self.assertFalse(result['members'][1]['flags'])
    def test_source_coverage_loss_refuses_to_replace_the_published_feed(self):
        previous=self.snapshot();snapshot=copy.deepcopy(previous);snapshot['members']=snapshot['members'][:2000]
        with self.assertRaises(ValueError):compact_snapshot(snapshot,previous)
        snapshot=self.snapshot();snapshot['coverage']['states'].append('CA')
        with self.assertRaises(ValueError):compact_snapshot(snapshot,previous)

class TrackerRefreshTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.cache=Path(self.tmp.name)
        self.manifest={'states':{'NY':'New York'},'sources':[
            {'file':'mvp-state-results.html','url':'https://movement.vote/election-results/2026/races/state-legislatures/','kind':'called_state_results','state':'','ok':True,'fresh':True},
            {'file':'mvp-house-results.html','url':'https://movement.vote/election-results/2026/races/us-house/','kind':'research','state':'','ok':True,'fresh':True}]}
        self.state={'type':'state-legislatures','state':'NY','card':{'title':'NY Assembly District 05','stateName':'New York','href':'/election-results/2026/new-york/state-house-district-5/','foot':'Called by AP at noon ET','cands':[{'name':'Jane Doe','party':'Dem','winner':True}]}}
        self.house={'type':'us-house','state':'NY','card':{'title':'U.S. House NY-05','stateName':'New York','href':'/election-results/2026/new-york/us-house-ny-05/','foot':'Not called yet','cands':[{'name':'Jane Doe','party':'Dem','winner':False}]}}
        self.ballots={( 'NY',kind,'5'):{'seats':1,'candidates':[{'name':'Jane Doe','party':'Democratic','url':'https://example.org/jane'},{'name':'Republican Winner','party':'Republican','url':'https://example.org/republican'}],'source':'https://example.org/general-2026'} for kind in ['state_house','us_house']}
        self.previous={'members':[{'id':'previous-jane','state':'NY','chamber':'state_house','district':'5','name':'Jane Doe','status':'projected','sources':['https://movement.vote/election-results/2026/races/state-legislatures/']}],'unresolved':[]}
        for kind,chamber,seat,title in [('us-senate','us_senate','class2','U.S. Senate – NY'),('governors','governor','','NY Governor')]:
            file='mvp-'+kind+'-results.html';self.manifest['sources'].append({'file':file,'url':'https://movement.vote/election-results/2026/races/'+kind+'/','kind':'research','state':'','ok':True,'fresh':True})
            self.ballots[('NY',chamber,seat)]=copy.deepcopy(self.ballots[('NY','us_house','5')])
    def tearDown(self):self.tmp.cleanup()
    def builder(self):
        (self.cache/'source-manifest.json').write_text(json.dumps(self.manifest))
        for filename,race in [('mvp-state-results.html',self.state),('mvp-house-results.html',self.house)]:
            (self.cache/filename).write_text('<script>self.__next_f.push('+json.dumps([1,'bc:'+json.dumps({'races':[race]})])+')</script>')
        for kind,title in [('us-senate','U.S. Senate – NY'),('governors','NY Governor')]:
            race=copy.deepcopy(self.house);race['type']=kind;race['card']['title']=title
            (self.cache/('mvp-'+kind+'-results.html')).write_text('<script>self.__next_f.push('+json.dumps([1,'bc:'+json.dumps({'races':[race]})])+')</script>')
        return Builder(self.cache)
    def test_new_verified_call_replaces_previous_projection_and_closes_gap(self):
        self.previous['unresolved']=[{'state':'NY','chamber':'state_house','district':'5','reason':'Unverified','sources':['https://example.org']}]
        result=update_from_trackers(self.builder(),self.previous,self.ballots)
        self.assertEqual(['Jane Doe'],[m['name'] for m in result.members.values()]);self.assertFalse(result.pending)
        self.assertIn('https://example.org/general-2026',next(iter(result.members.values()))['sources'])
    def test_republican_replacement_call_removes_stale_democratic_projection(self):
        self.state['card']['cands']=[{'name':'Republican Winner','party':'GOP','winner':True}]
        result=update_from_trackers(self.builder(),self.previous,self.ballots);self.assertFalse(result.members)
    def test_retracted_call_is_a_gap_instead_of_an_uncalled_winner(self):
        self.state['card']['foot']='Not called yet';self.state['card']['cands'][0]['winner']=False
        result=update_from_trackers(self.builder(),self.previous,self.ballots);self.assertFalse(result.members);self.assertEqual(1,len(result.pending))
    def test_name_not_on_general_ballot_does_not_replace_verified_entry(self):
        self.state['card']['cands'][0]['name']='Primary Only Candidate'
        result=update_from_trackers(self.builder(),self.previous,self.ballots);self.assertEqual(['previous-jane'],list(result.members))
    def test_unavailable_or_wrong_year_tracker_does_not_publish(self):
        self.manifest['sources'][0]['fresh']=False
        with self.assertRaises(ValueError):update_from_trackers(self.builder(),self.previous,self.ballots)
        self.manifest['sources'][0]['fresh']=True;self.state['card']['href']='/election-results/2024/old/'
        with self.assertRaises(ValueError):update_from_trackers(self.builder(),self.previous,self.ballots)
    def test_senate_special_and_governor_calls_use_separate_offices(self):
        builder=self.builder();self.ballots[('NY','us_senate','specialelection')]=self.ballots[('NY','us_senate','class2')]
        for kind,title in [('us-senate','U.S. Senate – NY (special)'),('governors','NY Governor')]:
            race=copy.deepcopy(self.state);race['type']=kind;race['card']['title']=title
            (self.cache/('mvp-'+kind+'-results.html')).write_text('<script>self.__next_f.push('+json.dumps([1,'bc:'+json.dumps({'races':[race]})])+')</script>')
        result=update_from_trackers(builder,self.previous,self.ballots)
        self.assertEqual({'state_house','us_senate','governor'},{m['chamber'] for m in result.members.values()})
        senator=next(m for m in result.members.values() if m['chamber']=='us_senate');self.assertEqual('Special election',senator['district'])

if __name__=='__main__':unittest.main()
