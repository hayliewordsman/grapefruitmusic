"""Fills in the front page of Grapefruit Music's F-Droid site (index.html here) and draws its QR code.

Run by .github/workflows/fdroid-repo.yml after fdroid update, with fdroidserver's Python (which has
the qrcode package): render.py <template> <repo/index-v2.json> <app id> <repo fingerprint> <page url> <out dir>
"""
import html
import json
import os
import re
import sys

import qrcode
import qrcode.image.svg


def installation_note(manifest):
    target = int(manifest.get('usesSdk', {}).get('targetSdkVersion', 0))
    if target >= 28:
        return ('Works with F-Droid and Droid-ify on Android 6 and newer. '
                'Grapefruit Music also supports Android 4.2.2 and newer through the APK download below.')
    return ("Works with F-Droid and Droid-ify on Android 6 to 13. Android 14 and newer may block "
            "the older release currently available in this repository.")


def main(template, index, app_id, fingerprint, url, out):
    if not re.fullmatch(r'[0-9A-F]{64}', fingerprint):
        sys.exit('fingerprint should be 64 hex digits: %r' % fingerprint)
    with open(index, encoding='utf-8') as f:
        package = json.load(f)['packages'][app_id]
    newest = max(package['versions'].values(), key=lambda v: v['manifest']['versionCode'])
    values = {
        'INSTALL_NOTE': installation_note(newest['manifest']),
        'SUMMARY': package['metadata']['summary']['en-US'],
        'FINGERPRINT': fingerprint,
        'VERSION': newest['manifest']['versionName'],
        'APK': newest['file']['name'].lstrip('/'),
        'APK_SIZE': '%.1f' % (newest['file']['size'] / 1e6),
    }
    with open(template, encoding='utf-8') as f:
        page = f.read()
    for key, value in values.items():
        page = page.replace('{{%s}}' % key, html.escape(value))
    left = re.findall(r'\{\{[A-Z_]+\}\}', page)
    if left:
        sys.exit('unfilled placeholders: %s' % ', '.join(sorted(set(left))))
    with open(os.path.join(out, 'index.html'), 'w', encoding='utf-8') as f:
        f.write(page)
    qrcode.make(url, image_factory=qrcode.image.svg.SvgPathImage, border=2).save(os.path.join(out, 'qr.svg'))
    print('front page: %s %s (%s), fingerprint %s' % (app_id, values['VERSION'], values['APK'], fingerprint))


if __name__ == '__main__':
    if len(sys.argv) != 7:
        sys.exit(__doc__)
    main(*sys.argv[1:])
