import copy,unittest
from refresh_feed import compact_snapshot

class FeedTest(unittest.TestCase):
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
