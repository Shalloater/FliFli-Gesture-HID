"""Exercise the installed debug app through Android accessibility/UI and ADB.

Run only against a test emulator. It changes this app's language and theme.
Usage: python tools/ui_smoke_test.py [emulator-5556]
"""
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')

ADB = ['D:/Android/Sdk/platform-tools/adb.exe', '-s', sys.argv[1] if len(sys.argv) > 1 else 'emulator-5556']
PACKAGE = 'com.example.joyvolumeremote'
OUT = Path(__file__).resolve().parents[1] / 'app/build/ui-review'
OUT.mkdir(parents=True, exist_ok=True)
results = []

def adb(*args):
    return subprocess.run(ADB + list(args), check=True, capture_output=True).stdout

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/flifli-test.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/flifli-test.xml'))

def tap(text):
    nodes = [n for n in tree().iter('node') if n.get('text') == text or n.get('content-desc') == text]
    assert nodes, 'Control not visible: ' + text
    bounds = list(map(int, re.findall(r'\d+', nodes[-1].get('bounds'))))
    adb('shell', 'input', 'tap', str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
    time.sleep(.6)

def capture(name, expected):
    current = tree()
    texts = [n.get('text') for n in current.iter('node')]
    for text in expected:
        assert text in texts, (name, text, texts)
    ET.ElementTree(current).write(OUT/(name+'.xml'), encoding='utf-8')
    (OUT/(name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    results.append(name)
    print('PASS', name, flush=True)

adb('shell', 'am', 'force-stop', PACKAGE)
adb('shell', 'am', 'start', '-n', PACKAGE+'/.MainActivity')
time.sleep(1)
visible = [n.get('text') for n in tree().iter('node')]
settings_label = next(label for label in ['Settings', '设置', '設定', '설정'] if label in visible)
tap(settings_label)
visible = [n.get('text') for n in tree().iter('node')]
initial_language = next(label for label in ['Language', '语言', '語言', '언어', '言語'] if label in visible)
tap(initial_language)
tap('English')
language_label = 'Language'
for native, label, settings in [('简体中文','语言','设置'), ('繁體中文','語言','設定'),
                                ('한국어','언어','설정'), ('日本語','言語','設定'), ('English','Language','Settings')]:
    tap(language_label)
    tap(native)
    capture('language-'+native, [label, settings, native])
    language_label = label

for name in ['Pink', 'Purple', 'Green', 'Black', 'Blue']:
    tap('Color theme')
    tap(name)
    capture('theme-'+name.lower(), ['Color theme', name])
    resolved = adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN',
                   '-c', 'android.intent.category.LAUNCHER', PACKAGE).decode()
    assert '.Launcher'+name in resolved, resolved

tap('Color theme')
tap('Purple')
tap('Language')
tap('简体中文')
adb('shell', 'am', 'force-stop', PACKAGE)
adb('shell', 'am', 'start', '-n', PACKAGE+'/.LauncherPurple')
time.sleep(1)
capture('home-zh-purple', ['首页', '未连接设备'])
tap('连接')
capture('connection-zh', ['连接', '已配对设备'])
tap('设置')
capture('persisted-settings', ['紫色', '简体中文'])

# A small phone with enlarged system text exercises wrapping and scrolling.
adb('shell', 'wm', 'size', '720x1280')
adb('shell', 'wm', 'density', '360')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
time.sleep(1)
capture('small-large-text-settings', ['皮肤', '语言'])
tap('首页')
capture('small-large-text-home', ['首页', '未连接设备'])
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
adb('shell', 'wm', 'size', '1080x2400')
adb('shell', 'wm', 'density', '420')
(OUT/'smoke-results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
print('Passed', len(results), 'UI checks')
