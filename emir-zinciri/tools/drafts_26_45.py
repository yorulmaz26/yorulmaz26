"""26–45. bölüm taslakları (bir kerelik üretim; ardından ./gradlew :logic:levelTool --args="6 9")."""
import json, os
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "levels")
F,B,TL,TR,W = "FORWARD","BACK","TURN_LEFT","TURN_RIGHT","WAIT"
PICK,DROP,PUSH,PRESS,SEND,WM = "PICK","DROP","PUSH","PRESS","SEND","WAIT_MSG"
def R(*rs): return [dict(id=i,x=x,y=y,dir=d) for (i,x,y,d) in rs]
def EX(*ids): return [{"type":"REACH_EXIT","robots":list(ids)}]
L=[]
def lv(i,name,goal,hint,mp,robots,cmds,maxc,obj=None,**kw):
    d=dict(id=i,area=(i-1)//5+1,name=name,goalText=goal,hint=hint,map=mp,robots=robots,commands=cmds,maxCommands=maxc,par=0,
           objectives=obj or [{"type":"REACH_EXIT"}])
    d.update(kw); L.append(d)

# ---------------- Alan 6: İkili Komuta
lv(26,"İkinci Robot","İki robotu da çıkışa götür.","Her robotun kendi zinciri var. Üstteki sekmeden robot seç; ikisi aynı anda çalışır.",
   ["....E","     ","...  ","  ..E"],R(("A",0,0,"E"),("B",0,2,"E")),[F,TL,TR,W],14,tutorial="second_robot",
   story="Tebrikler komutan! İkinci robotun uyandı. Artık iki komut zinciri aynı anda çalışacak.")
lv(27,"Kapıyı Tut","B robotunu çıkışa ulaştır.","A plakada durdukça kapı açık kalır. B'nin zamanlamasını ayarla.",
   ["...p   ","       ","...D..E"],R(("A",0,0,"E"),("B",0,2,"E")),[F,TL,TR,W],14,EX("B"))
lv(28,"Karşılıklı Yardım","İki robot da çıkışa ulaşsın.","Önce biri diğerine kapı açar, sonra diğeri ona. Kimin ne zaman ayrılacağını düşün.",
   ["..p...DE","        ","..D..p.E"],R(("A",0,0,"E"),("B",0,2,"E")),[F,W,B],20,channels={"6,0":2,"5,2":2})
lv(29,"Sinyal","B çıkışa ulaşsın. Yalnızca 16 kart var!","Yeni kartlar: Mesaj gönder ve Mesaj bekle. Uzun beklemeler için çok sayıda 'Bekle' yerine sinyal kullan.",
   ["......s","       ","..D...E"],R(("A",0,0,"E"),("B",0,2,"E")),[F,W,PRESS,SEND,WM],16,EX("B"),tutorial="signal",
   solution={"A":[F,F,F,F,F,PRESS,SEND],"B":[F,WM,F,F,F,F,F]})
lv(30,"İkili Komuta Finali","İki robot da çıkışa ulaşsın.","A plakayla B'ye yol açar, B zamanlı düğmeyle A'ya. Zamanlama her şey.",
   ["..p..DE","       ","..D.t  ","   ..E "],R(("A",0,0,"E"),("B",0,2,"E")),[F,TL,TR,W,PRESS],22,channels={"5,0":2,"4,2":2})
# ---------------- Alan 7: Bant Fabrikası
lv(31,"İlk Bant","Kutuyu hedefe, robotu çıkışa götür.","Bantlar üzerlerindeki kutu ve robotları her adım bir hücre taşır. Kutuyu banda it, gerisini bant yapsın.",
   [".B>>>x",".....E"],R(("A",0,0,"E")),[F,TL,TR,W,PUSH],10,[{"type":"BOXES_ON_TARGETS"},{"type":"REACH_EXIT"}],tutorial="conveyor",
   story="Bant Fabrikası'nda üretim hatları kendi kendine çalışıyor. Bantlar seni de taşır: üstünde durma, yoksa sürüklenirsin!")
lv(32,"Kaygan Yol","Bant seni boşluğa taşımadan in.","Bant üstünde dönmek veya beklemek seni de taşır. Doğru anda in.",
   ["  E  ",">>>> "],R(("A",0,1,"N")),[F,TL,TR,W],6)
lv(33,"Dönerken Taşın","Çıkışa ulaş.","Bant üstünde dönmek bedava yolculuktur: hem dönersin hem ilerlersin.",
   [">>>>v","#...v",".E..v","<<<<<"],R(("A",0,0,"E")),[F,B,TL,TR,W],10)
lv(34,"Teslimat Hattı","Kutuyu hedefe taşı.","A kutuyu banda yükler; B hattın sonunda kutuyu karşılayıp hedefe iter.",
   [".B>>>v .","     v .","  x.....","        "],R(("A",0,0,"E"),("B",7,0,"S")),[F,TL,TR,W,PUSH],14,[{"type":"BOXES_ON_TARGETS"}])
lv(35,"Bant Finali","İki robot da çıkışa ulaşsın.","Bant B'yi plakaya taşır; plaka A'nın kapısını açar. Kimse boşluğa düşmesin.",
   ["....DE",".     ",">>>p  ",".  ..E"],R(("A",0,0,"E"),("B",0,2,"E")),[F,TL,TR,W,B],18)
# ---------------- Alan 8: Güvenlik Koridoru
lv(36,"Lazer Işını","Lazeri kapat ve çıkışa geç.","Kırmızı ışına giren robot kaybolur. Anahtar lazeri kapatır.",
   [" E    ","L.....","s.... "],R(("A",2,2,"N")),[F,TL,TR,W,PRESS],10,tutorial="laser",
   story="Güvenlik Koridoru lazerlerle korunuyor. Işınları kapat, kes ya da tam zamanında geç.")
lv(37,"Kutu Kalkanı","Çıkışa ulaş.","Kutu ışını keser: kutunun arkası güvenlidir.",
   ["  E   ","L.....",".B....","..    "],R(("A",1,3,"N")),[F,TL,TR,W,PUSH],10)
lv(38,"Zamanlı Geçit","Lazer kapalıyken koridoru geç.","Zamanlı düğme lazeri 7 adım kapatır. En kısa yolu bul.",
   [".....EL","t     "],R(("A",0,0,"S")),[F,TL,TR,W,PRESS],10,values={"6,0":3,"0,1":7})
lv(39,"İkili Kalkan","İki robot da çıkışa geçsin.","Plakalardan biri basılıyken lazer kapalı. Biri geçerken diğeri nöbet tutsun.",
   ["   E.Ep","L......",".p.....","...    "],R(("A",0,2,"E"),("B",3,2,"N")),[F,B,TL,TR,W],20)
lv(40,"Güvenlik Finali","İki robot da çıkışa ulaşsın.","A lazeri anahtarla kapatır, B kutuyu plakaya iter. Sıra ve zamanlama önemli.",
   ["L....DE","s......","..B.p.E"],R(("A",0,2,"N"),("B",1,2,"E")),[F,TL,TR,W,PRESS,PUSH],20,channels={"5,0":2,"4,2":2})
# ---------------- Alan 9: Koordinasyon Merkezi
lv(41,"Çift Kilit","A çıkışa ulaşsın.","Mor çerçeveli kilit: iki plakanın ikisine birden basılmalı. Kutu da plakaya basar.",
   [".B.p    ","......DE","...p    "],R(("A",0,0,"E"),("B",0,2,"E")),[F,TL,TR,W,PUSH],16,EX("A"),andChannels=[1],tutorial="and_lock",
   story="Koordinasyon Merkezi'nde kilitler tek başına açılmıyor. Robotların aynı anda doğru yerde olmalı.")
lv(42,"Aynı Anda","İki robot çıkışlara aynı adımda varsın.","Yeni hedef: senkron varış. Kısa yoldaki robot beklemeli.",
   ["......E","       ","...E   "],R(("A",0,0,"E"),("B",0,2,"E")),[F,W,TL,TR],16,[{"type":"SYNC_EXIT"}],tutorial="sync")
lv(43,"Uzaktan Kumanda","B çıkışa ulaşsın.","A anahtara basınca B'ye sinyal göndersin; B sinyali beklesin.",
   ["........s","         ","E.D......"],R(("A",0,0,"E"),("B",8,2,"W")),[F,TL,TR,W,PRESS,SEND,WM],18,EX("B"),
   solution={"A":[F,F,F,F,F,F,F,PRESS,SEND],"B":[F,F,F,F,F,WM,F,F,F]})
lv(44,"Senkron Bant","Çıkışlara aynı anda varın.","Bant bir robotu hızlandırır. Varışları eşitle.",
   [">>>>>E","      ","....E "],R(("A",0,0,"E"),("B",0,2,"E")),[F,W,TL,TR],14,[{"type":"SYNC_EXIT"}])
lv(45,"Koordinasyon Finali","Çıkışlara aynı anda varın.","Zamanlı düğme lazeri yalnızca 3 adım kapatır. İkisi de bu pencerede geçmeli ve aynı anda varmalı.",
   ["..E.E..","L......",".t....."],R(("A",2,2,"W"),("B",4,2,"N")),[F,B,TL,TR,W,PRESS],12,[{"type":"SYNC_EXIT"}],values={"1,2":3})

for a in range(6,10):
    lv_=[d for d in L if d["area"]==a]
    with open(f"{OUT}/area_{a:03d}.json","w") as f: json.dump({"area":a,"levels":lv_},f,ensure_ascii=False,indent=2)
print(len(L))
