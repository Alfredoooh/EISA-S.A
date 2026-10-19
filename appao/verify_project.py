from pathlib import Path
import re, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent / 'app' / 'src' / 'main'
errors = []

# XML well-formedness
for p in ROOT.rglob('*.xml'):
    try:
        ET.parse(p)
    except Exception as e:
        errors.append(f'XML parse error: {p}: {e}')

# Collect resources. Style names retain dots; R fields normalize dots to underscores.
res_names = {k:set() for k in ['layout','drawable','mipmap','color','string','dimen','style','anim','animator','xml','menu']}
res_root = ROOT / 'res'
for d in res_root.iterdir():
    if not d.is_dir():
        continue
    typ = d.name.split('-')[0]
    if typ == 'values':
        for p in d.glob('*.xml'):
            try: rt = ET.parse(p).getroot()
            except: continue
            for el in rt:
                tag = el.tag.split('}')[-1]
                name = el.attrib.get('name')
                if name in res_names:
                    res_names[name].add(name)
                if tag in res_names and name:
                    res_names[tag].add(name)
    elif typ in res_names:
        for p in d.iterdir():
            if p.is_file():
                res_names[typ].add(p.stem)

# IDs
ids=set()
for p in res_root.rglob('*.xml'):
    try: rt=ET.parse(p).getroot()
    except: continue
    for el in rt.iter():
        v=el.attrib.get('{http://schemas.android.com/apk/res/android}id','')
        if v.startswith('@+id/') or v.startswith('@id/'):
            ids.add(v.split('/')[-1])

# Java/Kotlin resource refs (only project resources; framework refs are ignored because they use android.R)
for p in ROOT.rglob('*.kt'):
    text=p.read_text(encoding='utf-8', errors='ignore')
    for typ in res_names:
        for m in re.finditer(rf'(?<![\w.])R\.{typ}\.([A-Za-z_][A-Za-z0-9_]*)', text):
            field=m.group(1)
            logical=field
            if typ == 'style':
                # R.style converts dots in style names to underscores.
                matches=[n for n in res_names['style'] if n.replace('.', '_') == field]
                if not matches:
                    errors.append(f'Missing style resource: {p}:{field}')
            elif field not in res_names[typ]:
                errors.append(f'Missing {typ} resource: {p}:{field}')
    for m in re.finditer(r'(?<![\w.])R\.id\.([A-Za-z_][A-Za-z0-9_]*)', text):
        if m.group(1) not in ids:
            errors.append(f'Missing id resource: {p}:{m.group(1)}')

# XML project resource refs
for p in ROOT.rglob('*.xml'):
    text=p.read_text(encoding='utf-8', errors='ignore')
    for typ in ['layout','drawable','mipmap','color','string','dimen','style','anim','animator','xml','menu']:
        for m in re.finditer(rf'@{typ}/([A-Za-z_][A-Za-z0-9_.]*)', text):
            val=m.group(1)
            if typ == 'style':
                ok = val in res_names['style']
            else:
                ok = val in res_names[typ]
            if not ok:
                errors.append(f'Missing XML {typ} resource: {p}:{val}')

# Known corruption signatures from previous Codemagic failures.
for p in ROOT.rglob('*.kt'):
    text=p.read_text(encoding='utf-8', errors='ignore')
    for bad in ['getChildAte','R.drawable/','ite.']:
        if bad in text:
            errors.append(f'Forbidden/corrupt token {bad}: {p}')

if errors:
    print('\n'.join(sorted(set(errors))))
    raise SystemExit(1)

print(f'PROJECT STATIC CHECK: PASS | XML={len(list(ROOT.rglob("*.xml")))} | KT={len(list(ROOT.rglob("*.kt")))}')
