"""Build independent SVG demos from production touch reports and gesture definitions."""
from pathlib import Path
from xml.sax.saxutils import escape
import json
import math
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
SVG = OUT / 'svg'
SVG.mkdir(exist_ok=True)
SCREEN = (105, 96, 270, 360)
CYCLE = 3.2
labels = {n.get('name'): (n.text or '').strip('"') for p in (ROOT/'app/src/main/res/values-zh-rCN').glob('*.xml')
          for n in ET.parse(p).getroot() if n.tag == 'string'}
label_map = dict(re.findall(r'case "(touch_[^"]+)": resource = R\.string\.(\w+);',
                           (ROOT/'app/src/main/java/com/example/joyvolumeremote/ActionLabels.java').read_text(encoding='utf-8')))

def f(value):
    return ('%.4f' % value).rstrip('0').rstrip('.') or '0'

def point(x, y):
    sx, sy, w, h = SCREEN
    return sx + x * w, sy + y * h

def animation(attribute, samples, discrete=False, transform=False):
    times = ';'.join(f(t / CYCLE) for t, _ in samples)
    values = ';'.join(str(v) for _, v in samples)
    tag = 'animateTransform' if transform else 'animate'
    extra = ' type="translate"' if transform else ''
    return f'<{tag} attributeName="{attribute}"{extra} values="{values}" keyTimes="{times}" dur="{CYCLE}s" repeatCount="indefinite" calcMode="{"discrete" if discrete else "linear"}"/>'

def route(points):
    return 'M ' + ' L '.join(f'{f(point(x, y)[0])} {f(point(x, y)[1])}' for x, y in points)

def svg_for(demo):
    ident = demo['id'].replace(':', '-').replace('_', '-')
    is_input = demo['kind'] == 'action'
    role = '本机 · 触发动作' if is_input else '受控端 · 触控信号'
    paper = '#171b25' if is_input else '#ffffff'
    trace = 'var(--soft,#d5c7f2)' if is_input else 'var(--accent,#604985)'
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 540" role="img" aria-labelledby="{ident}-title {ident}-desc" data-demo="{demo["id"]}">',
           f'<title id="{ident}-title">{escape(demo["name"])}</title>',
           f'<desc id="{ident}-desc">{escape(demo["description"])} 动画为教学慢放，实心触点表示按下，触点淡出表示抬起。</desc>',
           '<style>text{font-family:"Segoe UI","Microsoft YaHei",sans-serif} .accent{fill:var(--accent,#604985)} .trace{stroke:'+trace+'} .soft{fill:var(--soft,#d5c7f2)}</style>',
           f'<defs><marker id="{ident}-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto" markerUnits="userSpaceOnUse"><path d="M0 0 L6 3 L0 6" fill="none" stroke="{trace}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></marker></defs>',
           f'<text x="240" y="30" text-anchor="middle" fill="#778198" font-size="12" letter-spacing="1.2">{role}</text>',
           '<rect x="93" y="77" width="294" height="398" rx="23" fill="#e9edf3" stroke="#dbe1e9"/>',
           f'<rect x="105" y="96" width="270" height="360" rx="9" fill="{paper}" stroke="#cfd6e1"/>',
           '<rect x="221" y="85" width="38" height="3" rx="1.5" fill="#c3cad7"/>']
    if is_input:
        regions = {'full':(0,0,1,1), 'left':(0,0,.5,1), 'right':(.5,0,.5,1), 'top':(0,0,1,.5), 'bottom':(0,.5,1,.5)}
        x, y, w, h = regions[demo.get('area','full')]
        px, py = point(x,y)
        out.append(f'<rect x="{f(px+1)}" y="{f(py+1)}" width="{f(w*270-2)}" height="{f(h*360-2)}" rx="7" class="soft" opacity=".13"/>')
        if demo.get('area','full') != 'full':
            line = 'M 240 96 V 456' if demo['area'] in ['left','right'] else 'M 105 276 H 375'
            out.append(f'<path d="{line}" stroke="#a6a1bd" stroke-dasharray="4 5" opacity=".5"/>')
        out.append(f'<text x="{f(px+w*135)}" y="{f(py+23)}" text-anchor="middle" fill="#c8c5dc" font-size="10">{escape(demo.get("areaLabel","全屏"))} · 有效区域</text>')
    else:
        out += ['<path d="M132 139 H256 M132 155 H330 M132 171 H309" fill="none" stroke="#edf0f5" stroke-width="5" stroke-linecap="round"/>',
                '<path d="M132 410 H230 M132 426 H305" fill="none" stroke="#edf0f5" stroke-width="5" stroke-linecap="round"/>']
        edge = demo.get('edge')
        edge_paths = {'top-left':'M105 96 H195','top-right':'M285 96 H375','left':'M105 168 V384','right':'M375 168 V384',
                      'bottom-left':'M105 456 H195','bottom-center':'M195 456 H285','bottom-right':'M285 456 H375',
                      'corner':'M335 96 H375 V136'}
        if edge:
            out.append(f'<path d="{edge_paths[edge]}" stroke="var(--soft,#d5c7f2)" stroke-width="7" stroke-linecap="round" fill="none"/>')
    first_press = min(s for c in demo['contacts'] for s,e in c['presses'])
    last_lift = max(e for c in demo['contacts'] for s,e in c['presses'])
    for index, contact in enumerate(demo['contacts']):
        path = contact['path']
        moving = len(set((x,y) for _,x,y in path)) > 1
        if moving:
            d = route([(x,y) for _,x,y in path])
            out.append(f'<path d="{d}" class="trace" fill="none" opacity=".24" stroke-width="2" stroke-dasharray="4 5" marker-end="url(#{ident}-arrow)"/>')
            out.append(f'<path d="{d}" class="trace" fill="none" stroke-width="3" stroke-linecap="round" pathLength="1" stroke-dasharray="1" stroke-dashoffset="1">')
            out.append(animation('stroke-dashoffset',[(0,1),(path[0][0],1),(path[-1][0],0),(CYCLE-.05,0),(CYCLE,1)]))
            out.append('</path>')
            sx,sy=point(path[0][1],path[0][2])
            out.append(f'<circle cx="{f(sx)}" cy="{f(sy)}" r="4" fill="white" class="trace" stroke-width="2"/>')
        positions=[(0,point(path[0][1],path[0][2]))]+[(t,point(x,y)) for t,x,y in path]+[(CYCLE,point(path[-1][1],path[-1][2]))]
        # A lifted fingertip disappears before the next loop, so resetting its position is not a reverse gesture.
        out.append(f'<g class="contact" data-contact="{index+1}" transform="translate({f(positions[0][1][0])} {f(positions[0][1][1])})">')
        out.append(animation('transform',[(t,f'{f(p[0])} {f(p[1])}') for t,p in positions],transform=True))
        out.append(animation('opacity',[(0,0),(max(.05,first_press-.18),0),(first_press,1),(last_lift+.08,1),(last_lift+.3,0),(CYCLE,0)]))
        out.append('<circle r="12" fill="white" class="trace" stroke-width="1.8" opacity=".7"/>')
        pressed=[(0,0)]
        for start,end in contact['presses']: pressed.extend([(start,1),(end,0)])
        pressed.append((CYCLE,0))
        out.append('<g class="pressed">'+animation('opacity',pressed,discrete=True)+'<circle r="23" class="soft" opacity=".33"/><circle r="11" class="accent"/>')
        if is_input:
            out.append(f'<text y="4" fill="white" font-size="11" font-weight="600" text-anchor="middle">{index+1}</text>')
        else: out.append('<circle r="3" fill="white"/>')
        out.append('</g></g>')
    out.append('<text x="240" y="498" fill="#69758b" font-size="12" text-anchor="middle">')
    out.append('同组触点一起操作' if len(demo['contacts'])>1 else '实心触点为按下，淡出为抬起')
    out.append('</text>')
    phases=demo['phases']+[(max(last_lift+.5,max(end for _,end,_ in demo['phases'])),CYCLE-.05,'等待下一轮')]
    for start,end,label in phases:
        out.append(f'<text x="240" y="61" text-anchor="middle" class="accent" font-size="15" font-weight="600" opacity="0">{escape(label)}')
        out.append(animation('opacity',[(0,0),(start,1),(end,0),(CYCLE,0)],discrete=True))
        out.append('</text>')
    out.append('</svg>')
    return ''.join(out)

def thumbnail(demo):
    parts=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 54 64" aria-hidden="true"><rect x="8" y="4" width="38" height="55" rx="7" fill="var(--tint)" stroke="var(--soft)"/>']
    for contact in demo['contacts']:
        xy=[(8+x*38,4+y*55) for _,x,y in contact['path']]
        if len(set(xy))>1:
            parts.append('<polyline points="'+' '.join(f'{f(x)},{f(y)}' for x,y in xy)+'" fill="none" stroke="var(--accent)" stroke-width="1.8" stroke-linecap="round"/>')
        x,y=xy[0];parts.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="3" fill="var(--accent)"/>')
    return ''.join(parts)+'</svg>'

demos=[]
production=json.loads((OUT/'touch-signals.json').read_text(encoding='utf-8'))
for item in production:
    ident=item['id']; events=item['events']; intervals=[]; start=None
    for event in events:
        t=.46+event['time']*.004
        if event['down'] and start is None: start=t
        if not event['down'] and start is not None: intervals.append((start,t));start=None
    down_events=[e for e in events if e['down']]
    moving=len(set((e['x'],e['y']) for e in down_events))>1
    path=[(.46+e['time']*.004,e['x']/32767,e['y']/32767) for e in down_events]
    if not moving: path=[(intervals[0][0],events[0]['x']/32767,events[0]['y']/32767)]
    edge=None
    if 'top_left' in ident:edge='top-left'
    elif 'top_right_diagonal' in ident:edge='corner'
    elif 'top_right' in ident:edge='top-right'
    elif 'bottom_left' in ident:edge='bottom-left'
    elif 'bottom_center' in ident:edge='bottom-center'
    elif 'bottom_right' in ident:edge='bottom-right'
    elif 'left_edge' in ident:edge='left'
    elif 'right_edge' in ident:edge='right'
    if moving:
        description='受控屏幕上，一个触点从标记起点沿箭头滑动，到达终点后抬起。'
        if edge in ['left','right'] and ident.endswith(('_up','_down')):description='从受控屏幕侧边中部起滑，沿边缘移动约四分之一屏高，然后抬起。'
        elif edge:description='从受控屏幕标出的边缘起滑，移动约四分之一屏后抬起。'
        phases=[(.12,.46,'准备'),(.46,.64,'按下'),(.64,path[-1][0]+.02,'沿箭头滑动'),(intervals[-1][1],intervals[-1][1]+.5,'抬起 · 结束')]
    else:
        description='在受控屏幕中心'+('连续点击两次，每次按下后抬起。' if len(intervals)==2 else '点击一次，按下后抬起。')
        phases=[]
        for i,(a,b) in enumerate(intervals):phases.extend([(a,b,f'第 {i+1} 次按下'),(b,min(b+.25,CYCLE-.1),'抬起')])
    demo={'id':ident,'name':labels[label_map[ident]],'kind':'signal','category':'edge' if edge else 'basic',
          'fingers':1,'tapCount':0 if moving else len(intervals),'area':'full','areaLabel':'整屏坐标','edge':edge,
          'description':description,'detail':'以当前 App 的实际发送坐标生成，动画按 4 倍时间慢放。',
          'contacts':[{'path':path,'presses':intervals}],'phases':phases,'cycle':CYCLE,
          'actualMs':events[-2]['time'] if moving else item['durationMs']}
    demos.append(demo)

area_names={'full':'全屏','left':'左半屏','right':'右半屏','top':'上半屏','bottom':'下半屏'}
centers={'full':(.5,.5),'left':(.25,.5),'right':(.75,.5),'top':(.5,.25),'bottom':(.5,.75)}
fingers_names={1:'单指',2:'双指',3:'三指'}
tap_names={1:'单击',2:'双击',3:'三击'}
for fingers in range(1,4):
    for taps in range(1,4):
        for area,area_label in area_names.items():
            cx,cy=centers[area];spacing=.11 if area in ['left','right'] else .15
            positions=[(cx+(i-(fingers-1)/2)*spacing,cy) for i in range(fingers)]
            intervals=[(.46+i*.36,.64+i*.36) for i in range(taps)]
            phases=[]
            for i,(a,b) in enumerate(intervals):phases.extend([(a,b,f'第 {i+1} 次轻点'),(b,b+.16,'抬起全部手指')])
            demo={'id':f'tap:{fingers}:{taps}:{area}','name':f'{fingers_names[fingers]}{tap_names[taps]} · {area_label}',
                  'kind':'action','category':'tap','fingers':fingers,'tapCount':taps,'area':area,'areaLabel':area_label,
                  'description':f'在{area_label}范围内，用'+['','一根','两根','三根'][fingers]+'手指'+('一起' if fingers>1 else '')+f'轻点 {taps} 次，每次都抬起。',
                  'detail':'高亮区域表示有效范围；数字代表同一组手指。连续轻点，无需长按。',
                  'contacts':[{'path':[(intervals[0][0],x,y)],'presses':intervals} for x,y in positions],
                  'phases':phases,'cycle':CYCLE}
            demos.append(demo)

directions={'up':((.5,.76),(.5,.24),'上滑'),'down':((.5,.24),(.5,.76),'下滑'),
            'left':((.76,.5),(.24,.5),'左滑'),'right':((.24,.5),(.76,.5),'右滑')}
for fingers in range(1,4):
    for direction,(start,end,label) in directions.items():
        contacts=[]
        for i in range(fingers):
            offset=(i-(fingers-1)/2)*.15
            sx,sy=start;ex,ey=end
            if direction in ['up','down']:sx+=offset;ex+=offset
            else:sy+=offset;ey+=offset
            contacts.append({'path':[(.46,sx,sy),(1.5,ex,ey)],'presses':[(.46,1.64)]})
        demos.append({'id':f'motion:{fingers}:swipe_{direction}','name':f'{fingers_names[fingers]}{label}',
                      'kind':'action','category':'swipe','fingers':fingers,'area':'full','areaLabel':'全屏',
                      'description':['','一根','两根','三根'][fingers]+f'手指按下后一起{label}，到达终点后全部抬起。',
                      'detail':'保持手指之间的距离，一起移动；整屏均可触发。', 'contacts':contacts,
                      'phases':[(.12,.46,'准备'),(.46,.66,'一起按下'),(.66,1.5,'沿箭头滑动'),(1.64,2.1,'全部抬起')],'cycle':CYCLE})
for clockwise,label in [(True,'顺时针'),(False,'逆时针')]:
    path=[]
    for i in range(49):
        angle=-math.pi/2+(1 if clockwise else -1)*math.tau*i/48
        path.append((.46+1.6*i/48,.5+.25*math.cos(angle),.5+.1875*math.sin(angle)))
    demos.append({'id':'motion:1:circle_'+('clockwise' if clockwise else 'counterclockwise'),
                  'name':'单指'+label+'画圈','kind':'action','category':'circle','fingers':1,'area':'full','areaLabel':'全屏',
                  'description':f'一根手指持续按住，{label}画一圈，再抬起。','detail':'保持连续接触，画出足够大的圆形轨迹。',
                  'contacts':[{'path':path,'presses':[(.46,2.18)]}],
                  'phases':[(.12,.46,'准备'),(.46,.66,'按下'),(.66,2.06,label+'画圈'),(2.18,2.7,'抬起 · 完成')],'cycle':CYCLE})

assert len(demos)==77
for demo in demos:
    demo['file']='svg/'+demo['id'].replace(':','-')+'.svg'
    demo['svg']=svg_for(demo)
    demo['thumbnail']=thumbnail(demo)
    ET.fromstring(demo['svg'])
    (OUT/demo['file']).write_text(demo['svg'],encoding='utf-8')
(OUT/'data.js').write_text('window.GESTURE_DEMOS='+json.dumps(demos,ensure_ascii=False,separators=(',',':'))+';',encoding='utf-8')
manifest=[{k:v for k,v in d.items() if k not in ['svg','thumbnail']} for d in demos]
(OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Generated {len(demos)} standalone animated SVGs: 18 signals, 45 tap-area combinations, 14 motions')
