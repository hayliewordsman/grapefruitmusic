import copy
import hashlib
import io
import tempfile
import unittest
import zipfile
from pathlib import Path
from blue_release import extract_blue, verify_originals, verify_existing, ORIGINAL_IDS


class BlueReleaseTest(unittest.TestCase):
    def archive(self, data=b'APK', checksum=None, extra=None):
        result = io.BytesIO()
        name = 'release/Blue-Grapefruit-1.0.apk'
        with zipfile.ZipFile(result, 'w') as archive:
            archive.writestr(name, data)
            archive.writestr(name + '.sha256', (checksum or hashlib.sha256(data).hexdigest()) + '  Blue-Grapefruit-1.0.apk\n')
            if extra:
                archive.writestr(*extra)
        result.seek(0)
        return result

    def test_only_verified_apk_is_written_even_with_unsafe_extra_path(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / 'apks'
            output = extract_blue(self.archive(extra=('../../outside', 'ignored')), 'blue-v1.0', target)
            self.assertEqual(b'APK', output.read_bytes())
            self.assertEqual([output], list(target.iterdir()))
            self.assertFalse((Path(folder) / 'outside').exists())

    def test_mismatched_checksum_additional_apk_and_beta_tag_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            for archive, tag in [(self.archive(checksum='0' * 64), 'blue-v1.0'),
                                 (self.archive(extra=('other.apk', b'other')), 'blue-v1.0'),
                                 (self.archive(), 'blue-v1.0-beta-7')]:
                with self.assertRaises(ValueError):
                    extract_blue(archive, tag, folder)
            self.assertEqual([], list(Path(folder).iterdir()))

    def test_blue_addition_is_allowed_but_any_original_change_is_rejected(self):
        before = {'packages': {app: {'metadata': {'name': app}, 'versions': {'sha': {'code': 1}}} for app in ORIGINAL_IDS}}
        after = copy.deepcopy(before)
        after['packages']['blue'] = {'versions': {'new': {'code': 8}}}
        verify_originals(before, after)
        for app in ORIGINAL_IDS:
            changed = copy.deepcopy(after)
            changed['packages'][app]['versions']['sha']['code'] = 2
            with self.assertRaises(ValueError):
                verify_originals(before, changed)

    def test_archive_absence_is_preserved_without_weakening_main_catalog_check(self):
        empty = {'packages': {}}
        verify_originals(empty, empty, allow_absent=True)
        with self.assertRaises(ValueError):
            verify_originals(empty, empty)
        after = {'packages': {ORIGINAL_IDS[0]: {'versions': {}}}}
        with self.assertRaises(ValueError):
            verify_originals(empty, after, allow_absent=True)

    def test_maps_addition_preserves_every_existing_app(self):
        before = {'packages': {app: {'versions': {'sha': {'code': 1}}}
                               for app in (*ORIGINAL_IDS, 'blue')}}
        after = copy.deepcopy(before)
        after['packages']['maps'] = {'versions': {'maps-sha': {'code': 7}}}
        verify_existing(before, after)
        for app in before['packages']:
            changed = copy.deepcopy(after)
            changed['packages'][app]['versions']['sha']['code'] = 2
            with self.assertRaises(ValueError):
                verify_existing(before, changed)
            del changed['packages'][app]
            with self.assertRaises(ValueError):
                verify_existing(before, changed)
        verify_existing({'packages': {}}, {'packages': {}})


if __name__ == '__main__':
    unittest.main()
