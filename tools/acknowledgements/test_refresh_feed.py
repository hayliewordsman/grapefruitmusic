import copy,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from refresh_feed import compact_snapshot,download_source,fetch_source

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

if __name__=='__main__':unittest.main()
