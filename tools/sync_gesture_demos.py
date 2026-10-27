"""Package the approved SVG previews and shared thumbnail geometry as offline Android assets."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
source = root / 'output/gesture-previews'
target = root / 'app/src/main/assets/gesture_demos'
(target / 'svg').mkdir(parents=True, exist_ok=True)
previews = json.loads((source / 'manifest.json').read_text(encoding='utf-8'))
assert len(previews) == 77 and len({item['id'] for item in previews}) == 77
records = []
for item in previews:
    svg = (source / item['file']).read_text(encoding='utf-8')
    ET.fromstring(svg)
    (target / item['file']).write_text(svg, encoding='utf-8')
    records.append({key: item[key] for key in ['id', 'kind', 'category', 'fingers', 'area', 'contacts', 'cycle', 'file']}
                   | {'tapCount': item.get('tapCount', 0)})
(target / 'catalog.json').write_text(json.dumps(records, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print('Packaged', len(records), 'approved offline SVG animations and thumbnail paths')
