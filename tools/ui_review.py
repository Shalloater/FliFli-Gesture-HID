"""ADB UI inspection helper. Saves screenshots and accessibility trees in app/build/ui-review."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import sys
sys.stdout.reconfigure(encoding='utf-8')

parser = argparse.ArgumentParser()
parser.add_argument('action', choices=['capture', 'tap', 'texts'])
parser.add_argument('value', nargs='?', default='screen')
parser.add_argument('--serial', default='emulator-5556')
args = parser.parse_args()
adb = ['D:/Android/Sdk/platform-tools/adb.exe', '-s', args.serial]
out = Path(__file__).resolve().parents[1] / 'app/build/ui-review'
out.mkdir(parents=True, exist_ok=True)

def run(*command):
    return subprocess.run(adb + list(command), check=True, capture_output=True).stdout

run('shell', 'uiautomator', 'dump', '/sdcard/flifli-ui.xml')
tree = ET.fromstring(run('shell', 'cat', '/sdcard/flifli-ui.xml'))
if args.action == 'tap':
    candidates = [node for node in tree.iter('node') if node.get('text') == args.value
                  or node.get('content-desc') == args.value]
    if not candidates:
        raise RuntimeError('Visible control not found: ' + args.value)
    node = candidates[-1]
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(0.6)
elif args.action == 'capture':
    (out / (args.value+'.png')).write_bytes(run('exec-out', 'screencap', '-p'))
    ET.ElementTree(tree).write(out / (args.value+'.xml'), encoding='utf-8')
    print(out / (args.value+'.png'))
else:
    for node in tree.iter('node'):
        if node.get('text'):
            print(node.get('text'), node.get('bounds'))
