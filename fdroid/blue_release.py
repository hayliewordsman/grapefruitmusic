"""Fetch Blue's APK-only release tag and verify additive F-Droid publication.

The original apps' entries must remain exactly equal on a repository-only deployment.
No signing key or private music input is read by this helper.
"""
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import urllib.request
import zipfile

ORIGINAL_IDS = ('io.github.hayliewordsman.grapefruitmusic',
                'io.github.hayliewordsman.grapefruitfast')
MAX_ZIP_BYTES = 160 * 1024 * 1024


def extract_blue(archive, tag, destination):
    if not re.fullmatch(r'blue-v[0-9]+(?:\.[0-9]+){1,2}', tag):
        raise ValueError('Expected a stable Blue release tag')
    filename = 'Blue-Grapefruit-' + tag[len('blue-v'):] + '.apk'
    with zipfile.ZipFile(archive) as bundle:
        apks = [entry for entry in bundle.infolist() if entry.filename.endswith('.apk')]
        if len(apks) != 1 or Path(apks[0].filename).name != filename:
            raise ValueError('Release must contain exactly the named Blue APK')
        apk = apks[0]
        if apk.file_size > MAX_ZIP_BYTES:
            raise ValueError('APK exceeds release size limit')
        sums = [entry for entry in bundle.infolist()
                if entry.filename == apk.filename + '.sha256']
        if len(sums) != 1 or sums[0].file_size > 1024:
            raise ValueError('Missing or invalid APK checksum')
        fields = bundle.read(sums[0]).decode('ascii').split()
        if len(fields) != 2 or fields[1] != filename or not re.fullmatch(r'[a-f0-9]{64}', fields[0]):
            raise ValueError('Invalid checksum record')
        data = bundle.read(apk)
        if hashlib.sha256(data).hexdigest() != fields[0]:
            raise ValueError('APK checksum mismatch')
    # Never extract arbitrary paths or other files from the release ZIP.
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    output = destination / filename
    output.write_bytes(data)
    return output


def fetch_blue(tag, destination):
    if not re.fullmatch(r'blue-v[0-9]+(?:\.[0-9]+){1,2}', tag):
        raise ValueError('Expected a stable Blue release tag')
    url = 'https://codeload.github.com/hayliewordsman/grapefruitmusic/zip/refs/tags/' + tag
    with urllib.request.urlopen(url, timeout=90) as response:
        data = response.read(MAX_ZIP_BYTES + 1)
    if len(data) > MAX_ZIP_BYTES:
        raise ValueError('Release ZIP exceeds size limit')
    return extract_blue(io.BytesIO(data), tag, destination)


def verify_originals(before, after, allow_absent=False):
    for app_id in ORIGINAL_IDS:
        previous = before['packages'].get(app_id)
        current = after['packages'].get(app_id)
        if (previous is None and not allow_absent) or current != previous:
            raise ValueError('Original app catalog changed: ' + app_id)
    print('Original Audio and TV catalog entries, versions and APK hashes are unchanged.')


def verify_existing(before, after):
    """Adding an app must not mutate or remove any already-published app record."""
    for app_id, package in before['packages'].items():
        if after['packages'].get(app_id) != package:
            raise ValueError('Existing app catalog changed: ' + app_id)
    print('All preexisting app records, versions and APK hashes are unchanged.')


if __name__ == '__main__':
    if len(sys.argv) == 4 and sys.argv[1] == 'fetch':
        print(fetch_blue(sys.argv[2], sys.argv[3]))
    elif len(sys.argv) == 4 and sys.argv[1] in ('verify-originals', 'verify-originals-archive'):
        verify_originals(json.loads(Path(sys.argv[2]).read_text()),
                         json.loads(Path(sys.argv[3]).read_text()),
                         allow_absent=sys.argv[1].endswith('-archive'))
    elif len(sys.argv) == 4 and sys.argv[1] == 'verify-existing':
        verify_existing(json.loads(Path(sys.argv[2]).read_text()),json.loads(Path(sys.argv[3]).read_text()))
    else:
        sys.exit('Usage: blue_release.py fetch <stable tag> <directory> | verify-originals <before.json> <after.json>')
