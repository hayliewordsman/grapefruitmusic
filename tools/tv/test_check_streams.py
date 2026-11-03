"""Offline regression checks for probe evidence: no live network required."""
import unittest
from unittest.mock import patch
import urllib.error
import check_streams as probe

ROOT = 'https://provider.example/master.m3u8'
MASTER = '''#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=400000,CODECS="avc1.4d401e,mp4a.40.2",RESOLUTION=640x360
low.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2000000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720
high.m3u8
'''
MEDIA = '#EXTM3U\n#EXTINF:6,\none.ts\n#EXTINF:6,\ntwo.ts\n'
TS = (b'\x47'+bytes(187))*3


class ProbeTest(unittest.TestCase):
    def fetch(self, url, *args, **kwargs):
        if url == ROOT:
            return url, MASTER.encode()
        if url.endswith('.m3u8'):
            return url, MEDIA.encode()
        return url, TS

    def test_checks_every_video_rendition_and_codec_list(self):
        with patch.object(probe, 'fetch', side_effect=self.fetch) as fetch:
            result = probe.probe_source(ROOT)
        self.assertEqual('media-reachable', result['result'])
        self.assertEqual(2, result['video_variants'])
        self.assertTrue(result['adaptive'])
        self.assertEqual('avc1.4d401e,mp4a.40.2', result['renditions'][0]['codecs'])
        self.assertIn('https://provider.example/high.m3u8', [c.args[0] for c in fetch.call_args_list])

    def test_working_low_rendition_does_not_hide_broken_high(self):
        def fetch(url, *args, **kwargs):
            if url.endswith('high.m3u8'):
                raise urllib.error.HTTPError(url, 404, 'Not Found', {}, None)
            return self.fetch(url, *args, **kwargs)
        with patch.object(probe, 'fetch', side_effect=fetch):
            result = probe.probe_source(ROOT)
        self.assertEqual('not-verified', result['result'])
        self.assertEqual(404, result['renditions'][1]['http_status'])

    def test_separate_audio_failure_is_not_success(self):
        master = MASTER+'#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="English",URI="audio.m3u8"\n'
        def fetch(url, *args, **kwargs):
            if url == ROOT:
                return url, master.encode()
            if url.endswith('audio.m3u8'):
                raise ValueError('missing audio')
            return self.fetch(url, *args, **kwargs)
        with patch.object(probe, 'fetch', side_effect=fetch):
            result = probe.probe_source(ROOT)
        self.assertEqual('not-verified', result['result'])
        self.assertEqual('missing audio', result['audio'][0]['error'])

    def test_rejects_html_returned_with_http_200_for_media(self):
        with patch.object(probe, 'fetch', return_value=(ROOT, b'<html>Access denied</html>')):
            with self.assertRaisesRegex(ValueError, 'unrecognized media'):
                probe.sample_media(ROOT)

    def test_checks_fmp4_initialization(self):
        text = '#EXTM3U\n#EXT-X-MAP:URI="init.mp4"\n#EXTINF:6,\nchunk.m4s\n'
        with patch.object(probe, 'sample_media', return_value={'framing':'fmp4'}) as sample:
            result = probe.check_media(ROOT, text)
        self.assertEqual(1, len(result['initializations']))
        self.assertEqual(('https://provider.example/init.mp4', probe.SAMPLE, 0, True), sample.call_args_list[0].args)

    def test_rejects_drm_and_ended_or_empty_live_playlists(self):
        for text in [MEDIA+'#EXT-X-ENDLIST', '#EXTM3U',
                     '#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI="key"\n'+MEDIA]:
            with self.assertRaises(ValueError):
                probe.check_media(ROOT, text)

    def test_audio_only_master_is_not_a_video_success(self):
        master = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000,CODECS="mp4a.40.2"\naudio.m3u8\n'
        def fetch(url, *args, **kwargs):
            return (url, master.encode()) if url == ROOT else self.fetch(url, *args, **kwargs)
        with patch.object(probe, 'fetch', side_effect=fetch):
            result = probe.probe_source(ROOT)
        self.assertEqual('not-verified', result['result'])
        self.assertIn('audio-only', result['error'])

    def test_checks_fallback_even_if_primary_succeeds(self):
        with patch.object(probe, 'probe_source', side_effect=[{'result':'media-reachable','adaptive':True}, {'result':'not-verified'}]) as source:
            result = probe.probe({'id':'c','name':'Channel','streams':[ROOT,ROOT+'?fallback']})
        self.assertEqual(2, source.call_count)
        self.assertEqual('not-verified', result['sources'][1]['result'])

    def test_rejects_insecure_sources_and_redirects(self):
        for url in ['http://provider.example/live', 'https://user:pass@provider.example/live']:
            with self.assertRaises(ValueError):
                probe.secure(url)
        with self.assertRaises(ValueError):
            probe.SecureRedirect().redirect_request(None,None,302,'Found',{},'http://provider.example/live')

    def test_distinguishes_runtime_proxy_denial_from_provider_403(self):
        error = urllib.error.URLError('Tunnel connection failed: 403 Forbidden')
        with patch.object(probe, '_fetch', side_effect=error):
            with self.assertRaises(urllib.error.URLError) as caught:
                probe.fetch(ROOT)
        info = probe.error_info(caught.exception)
        self.assertEqual('provider.example', info['failed_host'])
        self.assertEqual('runtime-proxy-denied', info['failure_kind'])
        provider = probe.error_info(urllib.error.HTTPError(ROOT,403,'Forbidden',{},None))
        self.assertEqual(403, provider['http_status'])
        self.assertNotIn('failure_kind', provider)

    def test_byte_range_and_penultimate_segment(self):
        text = '#EXTM3U\n#EXTINF:6,\n#EXT-X-BYTERANGE:400@100\nall.ts\n#EXTINF:6,\n#EXT-X-BYTERANGE:400\nall.ts\n'
        with patch.object(probe, 'sample_media', return_value={'framing':'mpeg-ts'}) as sample:
            probe.check_media(ROOT, text)
        self.assertEqual(('https://provider.example/all.ts',400,100),sample.call_args.args)


if __name__ == '__main__':
    unittest.main()
