"""Exercise signal selection on a test emulator, preserving its keyboard preferences.

Install a debug APK first. Usage: python tools/signal_ui_smoke_test.py [serial] [package]
The emulator must contain the default reader keyboard and use Simplified Chinese.
Screenshots and accessibility trees are saved under the module's build/ui-review.
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
PACKAGE = sys.argv[2] if len(sys.argv) > 2 else 'com.example.joyvolumeremote'
OUT = Path(__file__).resolve().parents[1] / ('compat' if PACKAGE.endswith('.blecompat') else 'app') / 'build/ui-review/signals'
OUT.mkdir(parents=True, exist_ok=True)
results = []
width, height = 1080, 2400


def adb(*args):
    return subprocess.run(ADB + list(args), check=True, capture_output=True).stdout


def tree():
    for attempt in range(3):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/flifli-signals.xml')
            break
        except subprocess.CalledProcessError:
            if attempt == 2:
                raise
            time.sleep(1)
    return ET.fromstring(adb('shell', 'cat', '/sdcard/flifli-signals.xml'))


def texts():
    return [node.get('text') for node in tree().iter('node')]


def nodes(label):
    matches = []
    for node in tree().iter('node'):
        if node.get('text') != label and node.get('content-desc') != label:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        if x2 - x1 > 12 and y2 - y1 > 12 and 0 <= y1 < y2 <= height:
            matches.append((node, (x1, y1, x2, y2)))
    return matches


def swipe(up=True):
    adb('shell', 'input', 'swipe', str(width * 3 // 4), str(int(height * (.75 if up else .4))),
        str(width * 3 // 4), str(int(height * (.4 if up else .75))), '300')
    time.sleep(.2)


def tap(label, seek=False):
    matches = nodes(label)
    if not matches and seek:
        for direction in [True] * 7 + [False] * 9:
            swipe(direction)
            matches = nodes(label)
            if matches:
                break
    assert matches, 'Control not visible: ' + label
    node, (x1, y1, x2, y2) = matches[-1]
    assert node.get('enabled') == 'true', 'Control disabled: ' + label
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(.25)


def capture(name, expected):
    current = tree()
    current_texts = [node.get('text') for node in current.iter('node')]
    for label in expected:
        assert label in current_texts, (name, label, current_texts)
    ET.ElementTree(current).write(OUT / (name + '.xml'), encoding='utf-8')
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    results.append(name)
    print('PASS', name, flush=True)


def tab(label):
    if not nodes(label):
        for _ in range(10):
            visible_tabs = [match for caption in ['键盘按键', 'Android 控制', '媒体控制', '触控手势'] for match in nodes(caption)]
            if visible_tabs:
                _, (_, y1, _, y2) = visible_tabs[0]
                adb('shell', 'input', 'swipe', str(int(width * .85)), str((y1 + y2) // 2),
                    str(int(width * .15)), str((y1 + y2) // 2), '300')
                if nodes(label):
                    break
            else:
                swipe(True)
    tap(label)


def top():
    # Scroll the editor body, leaving the always-visible mode/footer controls alone.
    for _ in range(4):
        swipe(False)


def keyboard_key(label):
    if nodes(label):
        tap(label)
        return
    top()
    for leftward in [True] * 4 + [False] * 5:
        current = tree()
        keyboards = [node for node in current.iter('node')
                     if node.get('class') == 'android.widget.HorizontalScrollView'
                     and any(child.get('class') == 'android.widget.Button' and child.get('content-desc')
                             for child in node.iter('node'))]
        if not keyboards:
            top()
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', keyboards[-1].get('bounds')))
        visible_keys = [node for node in keyboards[-1].iter('node') if node.get('class') == 'android.widget.Button']
        key_bounds = list(map(int, re.findall(r'\d+', visible_keys[0].get('bounds'))))
        y = (key_bounds[1] + key_bounds[3]) // 2
        travel = (x2 - x1) // 2
        start, end = (x2 - 24, x2 - 24 - travel) if leftward else (x1 + 24, x1 + 24 + travel)
        adb('shell', 'input', 'swipe', str(start), str(y), str(end), str(y), '500')
        if nodes(label):
            tap(label)
            return
    raise AssertionError('Keyboard key not reachable: ' + label)


def launch():
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-n', PACKAGE + '/com.example.joyvolumeremote.MainActivity')
    time.sleep(.5)


def open_binding():
    tap('设置')
    tap('阅读器翻页', True)
    edit_binding_signal()


def edit_binding_signal():
    tap('单指单击 · 全屏', True)
    tap('发送信号')


def commit_signal():
    tap('完成')
    tap('完成')


def cancel_signal():
    tap('取消')
    tap('取消')


def resize(w, h, density, font=1.0):
    global width, height
    width, height = w, h
    adb('shell', 'wm', 'size', f'{w}x{h}')
    adb('shell', 'wm', 'density', str(density))
    adb('shell', 'settings', 'put', 'system', 'font_scale', str(font))
    time.sleep(.5)


launch()
adb('shell', 'am', 'force-stop', PACKAGE)
adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files')
adb('shell', 'run-as', PACKAGE, 'cp', 'shared_prefs/pager_settings.xml', 'files/signal-test-backup.xml')
try:
    resize(1080, 2400, 420)
    launch()
    open_binding()
    tap('A', True)
    tap('B', True)
    top()
    capture('single-replaces', ['B', '单个信号'])
    assert 'A + B' not in texts()
    commit_signal()
    capture('single-commits', ['B'])
    edit_binding_signal()
    tap('清空')
    assert nodes('完成')[-1][0].get('enabled') == 'false'
    capture('empty-disabled', ['请在下方选择信号'])
    tap('组合信号')
    tap('Ctrl', True)
    tap('C', True)
    top()
    capture('ctrl-c', ['Ctrl + C'])
    tap('C', True)
    top()
    assert 'Ctrl + C' not in texts()
    tap('撤销')
    assert nodes('完成')[-1][0].get('enabled') == 'false'
    tap('Ctrl', True)
    tap('C', True)
    top()
    tab('触控手势')
    capture('touch-compatibility', ['屏幕中心单击', '屏幕中心双击'])
    if PACKAGE.endswith('.blecompat'):
        assert nodes('屏幕中心单击')[-1][0].get('enabled') == 'false'
    else:
        tap('屏幕中心单击', True)
        capture('incompatible-chord-kept', ['Ctrl + C', '组合支持最多 6 个普通键、修饰键和 1 个媒体/Android 控制键。触控手势和禁用须单独使用。'])
    tap('信号序列')
    top()
    tap('＋下一步', True)
    tap('键盘按键', True)
    tap('Tab', True)
    top()
    tap('＋下一步', True)
    tap('本步骤使用组合信号', True)
    tap('Ctrl', True)
    tap('V', True)
    top()
    tap('步骤间隔：100 毫秒 · 点击切换', True)
    capture('sequence-mixed-steps', ['1.  Ctrl + C', '2.  Tab', '3.  Ctrl + V', '步骤间隔：250 毫秒 · 点击切换'])
    commit_signal()
    capture('sequence-summary', ['Ctrl + C → Tab → Ctrl + V'])
    tap('完成', True)
    launch()
    open_binding()
    capture('sequence-persists', ['1.  Ctrl + C', '2.  Tab', '3.  Ctrl + V', '步骤间隔：250 毫秒 · 点击切换'])
    tap('清空')
    cancel_signal()
    edit_binding_signal()
    capture('cancel-preserves', ['1.  Ctrl + C', '2.  Tab', '3.  Ctrl + V'])
    # Active step editing and removal must preserve the other steps and their order.
    tap('2.  Tab')
    tap('A', True)
    top()
    capture('sequence-edit-step', ['1.  Ctrl + C', '2.  A', '3.  Ctrl + V'])
    tap('删除步骤 2')
    capture('sequence-remove-step', ['1.  Ctrl + C', '2.  Ctrl + V'])
    cancel_signal()
    # Reopen after a configuration change because the Activity owns unsaved editor state.
    resize(720, 1280, 360, 1.3)
    launch()
    open_binding()
    capture('small-large-text-sequence', ['信号序列', '完成'])
    tab('触控手势')
    capture('small-touch', ['触控手势', '完成'])
    cancel_signal()
    resize(1600, 2560, 240)
    launch()
    open_binding()
    capture('tablet-sequence', ['1.  Ctrl + C', '2.  Tab', '3.  Ctrl + V', '完成'])
    cancel_signal()
    resize(1080, 2400, 420)
    launch()
    open_binding()
    tap('组合信号')
    tap('清空')
    for key in ['Ctrl', 'Shift', 'Alt', 'A', 'S', 'D', 'F', 'G', 'H']:
        keyboard_key(key)
    top()
    capture('long-combination', ['Ctrl + Shift + Alt + A + S + D + F + G + H'])
    keyboard_key('Q')
    capture('rollover-limit', ['Ctrl + Shift + Alt + A + S + D + F + G + H', '组合支持最多 6 个普通键、修饰键和 1 个媒体/Android 控制键。触控手势和禁用须单独使用。'])
    cancel_signal()
    (OUT / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Passed', len(results), 'signal UI checks', flush=True)
except Exception:
    capture('failure', [])
    raise
finally:
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'run-as', PACKAGE, 'cp', 'files/signal-test-backup.xml', 'shared_prefs/pager_settings.xml')
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/signal-test-backup.xml')
    resize(1080, 2400, 420)
    launch()
