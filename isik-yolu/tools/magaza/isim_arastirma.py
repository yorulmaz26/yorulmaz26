import subprocess, json, re, html, sys, urllib.parse, time
CANDS = ['Light Path','Path of Light','Lightway','Beam Route','Ray Route','Lumen Path','Lumipath','Lumaze','Lumora','Lumen Lines',
 'Prism Path','Prismway','Mirror Beam','Beam Bender','Refracto','Photon Path','Glint Path','Glowline','Shinepath','Bright Trail',
 'Lux Trail','Radiant Route','Lightloom','Beamwise','Lumen Quest']
def get(url, data=None, headers=()):
    cmd=['curl','-s','--max-time','40','-L','-H','Accept-Language: en-US,en;q=0.8']
    for h in headers: cmd+=['-H',h]
    if data is not None: cmd+=['--data',data]
    cmd.append(url)
    try: return subprocess.run(cmd,capture_output=True,timeout=60).stdout.decode('utf-8','replace')
    except Exception as e: return ''
norm=lambda s: re.sub(r'[^a-z0-9]','',html.unescape(s).lower())
def play(q, hl, gl):
    s=get(f'https://play.google.com/store/search?q={urllib.parse.quote(q)}&c=apps&hl={hl}&gl={gl}')
    if not s: return None
    t=re.findall(r'<span class="[A-Za-z0-9]+">([^<]{2,80})</span>',s)
    ids=re.findall(r'details\?id=([a-zA-Z0-9._]+)',s)
    return {'titles':[html.unescape(x) for x in t if not re.fullmatch(r'[\d.,• ]+',x)][:60], 'ids':sorted(set(ids))[:40]}
def appstore(q):
    s=get(f'https://apps.apple.com/us/iphone/search?term={urllib.parse.quote(q)}')
    if not s: return None
    t=[html.unescape(x) for x in re.findall(r'aria-label="([^"]{2,80})"',s)]
    return {'titles':[x for x in t if 'Stars' not in x and 'page is available' not in x and x!='App Store'][:40]}
def steam(q):
    s=get(f'https://store.steampowered.com/search/?term={urllib.parse.quote(q)}')
    if not s: return None
    return {'titles':[html.unescape(x) for x in re.findall(r'<span class="title">([^<]+)</span>',s)][:40]}
def uspto(q):
    body=json.dumps({"query":{"bool":{"must":[{"bool":{"should":[{"query_string":{"query":q,"default_operator":"AND","fields":["wordmark","wordmarkPseudoText"]}}]}}]}},"size":40,"_source":["wordmark","alive","internationalClass","ownerName","statusDescription","serialNumber"]})
    s=get('https://tmsearch.uspto.gov/prod-stage-v1-0-0/tmsearch', body, ['Content-Type: application/json'])
    try: d=json.loads(s)
    except Exception: return None
    hits=[h['source'] for h in d['hits']['hits']]
    return {'total':d['hits']['totalValue'],'marks':[{'mark':h.get('wordmark'),'alive':h.get('alive'),'classes':h.get('internationalClass'),'owner':(h.get('ownerName') or [''])[0] if isinstance(h.get('ownerName'),list) else h.get('ownerName'),'serial':h.get('serialNumber')} for h in hits]}
def matches(c, titles):
    n=norm(c); out=[]
    for t in titles or []:
        nt=norm(t)
        if not nt: continue
        if nt==n: out.append(('exact',t))
        elif nt.startswith(n) or n in nt: out.append(('contains',t))
    return out
res={}
for c in CANDS:
    r={'play_us':play(c,'en','US'),'play_tr':play(c,'tr','TR'),'appstore_us':appstore(c),'steam':steam(c),'uspto':uspto(c)}
    r['flags']={k:matches(c,(v or {}).get('titles')) for k,v in r.items() if k!='uspto'}
    u=r['uspto'] or {}
    r['flags']['uspto']=[(('exact' if norm(m['mark'] or '')==norm(c) else 'contains'), m['mark'], 'LIVE' if m['alive'] else 'dead', m['classes']) for m in u.get('marks',[]) if norm(c) in norm(m['mark'] or '')]
    res[c]=r
    print(c, {k:len(v) for k,v in r['flags'].items()}, 'uspto_total', u.get('total'), flush=True)
    time.sleep(1.5)
json.dump(res, open(sys.argv[1],'w'), ensure_ascii=False, indent=1)
