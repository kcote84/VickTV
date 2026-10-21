#!/usr/bin/env python3
"""Generate the public update manifest for a signed release APK."""
import argparse
import hashlib
import json
import pathlib
import re
p = argparse.ArgumentParser()
p.add_argument('--apk', required=True, type=pathlib.Path)
p.add_argument('--repo', default='kcote84/VickTV')
p.add_argument('--tag', required=True)
p.add_argument('--notes', default='Première version de VickTV : chaînes, films, séries, favoris et 50 petits mots doux.')
p.add_argument('--output', default='dist/update.json', type=pathlib.Path)
a = p.parse_args()
config = pathlib.Path('app/build.gradle.kts').read_text()
code = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
name = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1)
if a.tag != 'v' + name:
    raise SystemExit('Le tag doit correspondre à versionName : v' + name)
data = {'versionCode': code, 'versionName': name, 'notes': a.notes,
        'apkUrl': f'https://github.com/{a.repo}/releases/download/{a.tag}/VickTV.apk',
        'sha256': hashlib.file_digest(a.apk.open('rb'), 'sha256').hexdigest()}
a.output.parent.mkdir(parents=True, exist_ok=True)
a.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
print(f'Manifeste généré pour {name} ({code}).')
