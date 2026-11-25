import json,re,sys
sys.argv=['x','iq/quotes.json','/dev/null']
exec(open('iq/build.py').read().split("data={'topics'")[0])   # gives T, out (457 filtered), books, n()
IQq=out; IQb=[[b[0],b[1],'ابن القيم'] for b in books]
D=json.load(open('turath/data-v3.json')); TB={b['id']:b for b in (D['books'] if isinstance(D['books'],list) else D['books'].values())}
TA=D['authors']; TA={int(k):v for k,v in (TA.items() if isinstance(TA,dict) else [(a['id'],a) for a in TA])}
def clean(s):
  s=re.sub(r'\s*\(\^?\*?[٠-٩0-9]+\*?\)','',s)
  s=re.sub(r'(?<=[ء-يً-ْ])[٠-٩]+','',s)
  lines=[l.strip() for l in s.split('\n')]
  lines=[re.sub(r'^[٠-٩0-9]+\s*-?\s*','',l) for l in lines if l and not re.match(r'^[٠-٩0-9]+\s+\S+:',l)]
  out=[];L=0;cut=False
  for l in lines:
    if out and L+len(l)>380: cut=True;break
    out.append(l);L+=len(l)
  t='\n'.join(out).strip()
  return (t+'…') if cut and not t.endswith('…') else t
new=[]
# my 51 book picks
picks=json.load(open('turath/picks.json'))
pos={}
for b in set(p['b'] for p in picks):
  pg=json.load(open(f'turath/p{b}.json'))['pages']
  pos[b]={}
  for i,p in enumerate(pg): pos[b].setdefault(p['page'],i+1)
for p in picks:
  new.append(dict(t=p['t'],book_id=p['b'],printed=p['p'],vol=None,pos=pos[p['b']][p['p']],likes=0,src='book'))
# reader quotes
U={r['id']:r for r in json.load(open('turath/uq_verified.json'))}
IDS=[1876,1184,2116,393,53,2006,178,1614,789,2107,1826,1602,2953,1716,1444,2123,523,2004,415,2207,1877,2278,2445,1927,2974,1997,568,1526,2273,179,1522,2915,1825,658,1908,2712,156,2455,1463,1384,2651,2245,1600,3284,1089,2303,1607,3294,1830,2728,2237]
for i in IDS:
  r=U[i]
  if r['v'] not in ('exact','repaired'): print('skip unverified',i); continue
  new.append(dict(t=clean(r['text']),book_id=r['book_id'],printed=r['printed'] or r['pg'],vol=r.get('vol'),pos=r['pg'],likes=r['like_count'],src='reader'))
# dedupe against owner's list and within
have=[n(q[0]) for q in IQq]
def key(t): x=n(t); m=len(x)//2; return x[max(0,m-25):m+25]
kept=[];seen=[]
for q in new:
  k=key(q['t'])
  if len(k)>=30 and (any(k in h for h in have) or any(k in s for s in seen)): continue
  seen.append(n(q['t'])); kept.append(q)
print('new kept',len(kept),'of',len(new))
bidx={}
for q in kept:
  b=TB[q['book_id']]; au=TA.get(b['author_id'],{}).get('name','')
  if q['book_id'] not in bidx: bidx[q['book_id']]=len(IQb); IQb.append([b['name'],'',au])
  nt=n(q['t']); m=0;sc=0
  for i,(k,en,ar,rx,w) in enumerate(T):
    c=len(re.findall(rx,nt))
    if c: m|=1<<i; sc+=w*min(c,3)
  sc+=8+min(q['likes'],8)  # reviewed by hand: lift into the daily pool
  pg=q['printed']; vol=q['vol']
  IQq.append([q['t'],bidx[q['book_id']],pg,f"https://app.turath.io/book/{q['book_id']}?page={q['pos']}",m,sc,(vol if vol and str(vol)!='1' else ''),q['likes'],q['src']])
data={'topics':[[k,en,ar] for k,en,ar,_,_ in T],'books':IQb,'q':IQq}
open('iq/iq.js','w',encoding='utf-8').write('window.IQ='+json.dumps(data,ensure_ascii=False,separators=(',',':'))+';\n')
import collections
print('total',len(IQq)); print(collections.Counter(IQb[q[1]][2] for q in IQq).most_common(12))
