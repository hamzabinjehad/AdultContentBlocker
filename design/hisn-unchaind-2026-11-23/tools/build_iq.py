import json,re,collections,sys
Q=json.load(open(sys.argv[1],encoding='utf-8'))
H=re.compile('[ً-ٰٟـ]')
def n(s): return H.sub('',s).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ة','ه').replace('ى','ي')
# topic: (key, en, ar, regex on normalized text, weight for recovery relevance)
T=[
 ('gaze','Gaze & desire','غض البصر والشهوة',r'النظر|البصر|بصره|ابصار|النظره|الشهو|العشق|عشق|الهوي|هواه|الزنا|الفاحش|الفواحش|الصور|العفه|عفه|الفرج|فرجه',5),
 ('sin','Sins & their harm','الذنوب وآثارها',r'الذنوب|ذنب|المعاصي|معصي|الخطايا|خطيئ|السيئات|الاثم',5),
 ('tawba','Repentance','التوبة والرجوع',r'توب|التوبه|التائب|استغفار|الاستغفار|يستغفر|الانابه|اناب',5),
 ('sabr','Patience','الصبر',r'الصبر|صبر|الصابر',4),
 ('nafs','The self & Shaytan','النفس والشيطان',r'النفس|نفسه|الشيطان|ابليس|وسوس|الخاطر|الخواطر|الخطرات',4),
 ('qalb','The heart','القلب',r'القلب|قلبه|القلوب',3),
 ('waqt','Time & life','الوقت والعمر',r'الوقت|الاوقات|العمر|عمره|الساعه|اضاعه',4),
 ('dhikr','Remembrance & duʿa','الذكر والدعاء',r'الذكر|ذكر الله|الذاكر|الدعاء|دعاء|يدعو',3),
 ('salah','Prayer & Qurʾan','الصلاة والقرآن',r'الصلاه|صلاته|القران|التلاوه',2),
 ('hubb','Love of Allah','محبة الله',r'محبه الله|المحبه|محبته|المحب|الحب',2),
 ('hamm','Worry & joy','الهم والسرور',r'الهم|الغم|الحزن|ضيق|السرور|انشراح|الفرح|اللذه|لذه|النعيم',3),
 ('akhira','Death & the Hereafter','الموت والآخرة',r'الجنه|الاخره|الموت|القبر|النار|القيامه',2),
]
FIQH={'رفع اليدين في الصلاة','أحكام أهل الذمة','تحفة المودود بأحكام المولود','المنار المنيف في الصحيح والضعيف','الطرق الحكمية في السياسة الشرعية','إعلام الموقعين عن رب العالمين','الصواعق المرسلة على الجهمية والمعطلة','اجتماع الجيوش الإسلامية','هداية الحيارى في أجوبة اليهود والنصارى','إغاثة اللهفان في حكم طلاق الغضبان','نونية ابن القيم الكافية الشافية','الفروسية المحمدية','تهذيب سنن أبي داود','فتيا في صيغة الحمد'}
CORE={'الداء والدواء':3,'الفوائد لابن القيم':3,'إغاثة اللهفان في مصايد الشيطان':2,'روضة المحبين ونزهة المشتاقين':2,'عدة الصابرين وذخيرة الشاكرين':2,'الوابل الصيب':2,'مدارج السالكين':1,'طريق الهجرتين وباب السعادتين':1,'الرسالة التبوكية زاد المهاجر إلى ربه':1,'رسالة ابن القيم إلى أحد إخوانه':1}
books=[];bi={}
out=[]; seen=set()
for q in Q:
  t=q['text'].strip()
  t=re.sub(r'[ \t]+\n','\n',t); t=re.sub(r'\n{3,}','\n\n',t)
  key=n(t)[:120]
  if key in seen: continue
  seen.add(key)
  nt=n(t)
  tops=[];score=0
  for i,(k,en,ar,rx,w) in enumerate(T):
    c=len(re.findall(rx,nt))
    if c: tops.append(i); score+=w*min(c,3)
  s=q['source']
  score+=CORE.get(s,0)*2
  if s in FIQH: score-=12
  L=len(t)
  if L>700: score-=6
  elif L>400: score-=2
  if L<50: score-=4
  if s not in bi: bi[s]=len(books); books.append([s,q.get('editionTitle','')])
  ed=q.get('editionTitle','').split(' - ')[-1] if ' - ' in q.get('editionTitle','') else ''
  out.append([t,bi[s],q.get('sourcePageNumber') or q.get('pageNumber') or 0,q['href'],sum(1<<i for i in tops),max(score,0)])
# drop passages contained in another
NN=[n(o[0]) for o in out]
order=sorted(range(len(out)),key=lambda i:-len(NN[i]))
keep=[];kept=[]
for i in order:
  t=NN[i]; m=len(t)//2; probe=t[max(0,m-30):m+30]
  if len(probe)>=40 and any(probe in k for k in kept): continue
  keep.append(i); kept.append(t)
drop=len(out)-len(keep)
out=[out[i] for i in sorted(keep)]
print('dropped dup',drop)
# rank
print(len(out),'kept')
top=sorted(range(len(out)),key=lambda i:-out[i][5])
for i in top[:12]: print(out[i][5],books[out[i][1]][0],out[i][0][:110].replace('\n',' '))
cnt=collections.Counter()
for o in out:
  for i in range(len(T)):
    if o[4]>>i&1: cnt[T[i][0]]+=1
print(cnt)
short=[o for o in out if len(o[0])<=260 and o[5]>=14]
print(len(short),'daily pool')
RAQ={'الداء والدواء','الفوائد لابن القيم','إغاثة اللهفان في مصايد الشيطان','الوابل الصيب','عدة الصابرين وذخيرة الشاكرين','روضة المحبين ونزهة المشتاقين','مدارج السالكين','طريق الهجرتين وباب السعادتين','الرسالة التبوكية زاد المهاجر إلى ربه','رسالة ابن القيم إلى أحد إخوانه','حادي الأرواح إلى بلاد الأفراح','مفتاح دار السعادة ومنشور ولاية العلم والإرادة','جلاء الأفهام','بدائع الفوائد','الكلام على مسألة السماع','زاد المعاد في هدي خير العباد'}
CORE_T=sum(1<<i for i in range(9))|(1<<10)  # gaze..salah + hamm
out=[o for o in out if books[o[1]][0] in RAQ and o[4]&CORE_T and o[5]>=12 and len(o[0])<=900]
used=sorted({o[1] for o in out}); remap={b:i for i,b in enumerate(used)}
books=[books[b] for b in used]
for o in out: o[1]=remap[o[1]]
out.sort(key=lambda o:-o[5])
print('tazkiya kept',len(out)); print(collections.Counter(books[o[1]][0] for o in out).most_common())
data={'topics':[[k,en,ar] for k,en,ar,_,_ in T],'books':books,'q':out}
open(sys.argv[2],'w',encoding='utf-8').write('window.IQ='+json.dumps(data,ensure_ascii=False,separators=(',',':'))+';\n')
