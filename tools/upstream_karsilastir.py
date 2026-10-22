import os, re, difflib, json, sys
ROOT=os.path.expanduser('~/mnt/Metrolist-main')
UP=os.path.join(ROOT,'upstream')
pairs=[(os.path.join(UP,'app/src/main/kotlin'),[os.path.join(ROOT,'shared/src/commonMain/kotlin'),os.path.join(ROOT,'shared/src/desktopMain/kotlin')]),
       (os.path.join(UP,'innertube/src/main/kotlin'),[os.path.join(ROOT,'innertube/src/commonMain/kotlin')])]
SUBS=[(r'\bR\.string\.','Res.string.'),(r'\bR\.drawable\.','Res.drawable.'),(r'\bR\.plurals\.','Res.plurals.'),
      (r'\bR\.array\.','Res.array.'),(r'LocalContext\.current','LocalAppContext.current'),(r'\bContext\b','AppContext'),
      (r'\bAppAppContext\b','AppContext'),(r'androidx\.compose\.ui\.res\.',''),(r'@AndroidEntryPoint',''),(r'@HiltViewModel',''),
      (r'hiltViewModel\(','metrolistViewModel('),(r'@Inject constructor',''),(r'@ApplicationContext ','')]
def norm(text):
    text=text.replace('\r\n','\n')
    text=re.sub(r'/\*.*?\*/','',text,flags=re.S)
    out=[]
    for l in text.split('\n'):
        s=l.strip()
        if not s or s.startswith('//') or s.startswith('import ') or s.startswith('package ') or s.startswith('@file:'): continue
        s=re.sub(r'\s*//.*$','',s)
        for a,b in SUBS: s=re.sub(a,b,s)
        s=re.sub(r'\s+',' ',s)
        if s: out.append(s)
    return out
rows=[]
for up_root,ours_roots in pairs:
    for dp,_,fs in os.walk(up_root):
        for f in fs:
            if not f.endswith('.kt'): continue
            up=os.path.join(dp,f); rel=os.path.relpath(up,up_root)
            ours=next((os.path.join(r,rel) for r in ours_roots if os.path.exists(os.path.join(r,rel))),None)
            ul=norm(open(up,encoding='utf-8',errors='ignore').read())
            if not ours:
                rows.append(dict(rel=rel,status='MISSING',up=len(ul),ours=0,ratio=0,desktop=0,changed=len(ul)));continue
            raw=open(ours,encoding='utf-8',errors='ignore').read()
            ol=norm(raw)
            sm=difflib.SequenceMatcher(None,ul,ol,autojunk=False)
            ratio=sm.ratio()
            changed=sum(max(i2-i1,j2-j1) for t,i1,i2,j1,j2 in sm.get_opcodes() if t!='equal')
            desk=raw.count('DESKTOP')
            st='SAME' if changed==0 else ('MARKED' if desk else 'UNMARKED')
            rows.append(dict(rel=rel,status=st,up=len(ul),ours=len(ol),ratio=round(ratio,3),desktop=desk,changed=changed,where=os.path.relpath(ours,ROOT)))
# ours-only
up_rels=set()
for up_root,_ in pairs:
    for dp,_,fs in os.walk(up_root):
        for f in fs:
            if f.endswith('.kt'): up_rels.add(os.path.relpath(os.path.join(dp,f),up_root))
only=[]
for _,ours_roots in pairs:
    for r in ours_roots:
        for dp,_,fs in os.walk(r):
            for f in fs:
                if f.endswith('.kt'):
                    rel=os.path.relpath(os.path.join(dp,f),r)
                    if rel not in up_rels: only.append(os.path.relpath(os.path.join(dp,f),ROOT))
json.dump(dict(rows=rows,only=only),open(os.path.expanduser('~/tools/compare.json'),'w'),indent=1)
from collections import Counter
print(Counter(r['status'] for r in rows)); print('ours-only',len(only))
