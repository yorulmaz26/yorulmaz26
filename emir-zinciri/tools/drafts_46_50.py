"""46–50. bölüm taslakları (Alan 10: Robot İsyanı). Ardından ./gradlew :logic:levelTool --args="10 10"."""
import json, os
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "levels")
F,B,TL,TR,W,PRESS,ATT,SH = "FORWARD","BACK","TURN_LEFT","TURN_RIGHT","WAIT","PRESS","ATTACK","SHIELD"
def R(*rs): return [dict(id=i,x=x,y=y,dir=d,type=t) for (i,x,y,d,t) in rs]
def E(*es): return [dict(id=i,type=t,x=x,y=y,dir=d,**kw) for (i,t,x,y,d,kw) in es]
L=[]
def lv(i,name,goal,hint,mp,robots,enemies,cmds,maxc,obj=None,**kw):
    d=dict(id=i,area=10,name=name,goalText=goal,hint=hint,map=mp,robots=robots,enemies=enemies,commands=cmds,maxCommands=maxc,par=0,
           objectives=obj or [{"type":"REACH_EXIT"}],maxTicks=60)
    d.update(kw); L.append(d)
EX=[{"type":"REACH_EXIT"}]; DEF=[{"type":"DEFEAT_ENEMIES"}]
lv(46,"İlk Düşman","Devriyeyi aş ve çıkışa ulaş.","Yeni kart: Saldır. Robotlar düşmanlardan önce hareket eder; önce vuran avantajlıdır.",
   ["......E"],R(("A",0,0,"E","BASIC")),E(("P","PATROL",3,0,"W",{})),[F,TL,TR,W,ATT],14,tutorial="combat",
   story="İsyan başladı! Bazı robotlar merkezi yapay zekânın kontrolüne geçti. Onları etkisiz hâle getir ama kendi robotlarını koru.")
lv(47,"Taret Koridoru","Taretin önünden geçip çıkışa ulaş.","Yeni kart: Kalkan aç. Taret hattındaki ilk robota her adım ateş eder.",
   ["   E  ","......","..    "],R(("A",0,2,"N","BASIC")),E(("T","TURRET",5,1,"W",{"range":6})),[F,B,TL,TR,W,SH],12,tutorial="shield")
lv(48,"Takipçi","Takipçiyi etkisiz hâle getir ve çıkışa ulaş.","Takipçi en yakın robota yürür. Lazeri zamanlı düğmeyle kapatıp geç; peşindeki takipçi ışına yakalansın.",
   ["...E..","L.....","......","t....."],R(("A",1,3,"W","BASIC")),E(("C","CHASER",5,3,"W",{"hp":3})),[F,TL,TR,W,PRESS,ATT],14,DEF+EX,
   values={"0,3":4},tutorial="chaser")
lv(49,"İkili Savunma","Tareti etkisiz hâle getir.","Kalkan robotu (mavi, 5 can, 3 adımlık kalkan) önde atışları karşılar; savaş robotu (2 hasar) taret dibine ulaşmalı.",
   ["#########",".........","######.##"],R(("A",1,1,"E","SHIELD"),("B",0,1,"E","WARRIOR")),E(("T","TURRET",8,1,"W",{"range":8,"hp":4})),[F,TL,TR,W,ATT,SH],20,DEF)
lv(50,"Robot İsyanı","İsyan Lideri'ni yen!","Kalkan robotu plakada durdukça arena kapısı açık kalır. Patron önündekine 2 hasar verir ve döner; canı yarıya inince en yakın robota saldırır!",
   ["#######","#.....#",".D....#","#.....#","####p##","####.##"],R(("A",0,2,"E","WARRIOR"),("B",4,5,"N","SHIELD")),E(("Z","BOSS",4,2,"N",{"hp":10})),
   [F,TL,TR,W,ATT,SH],16,DEF,tutorial="boss",
   story="İsyanın lideri karşında. Bu zaferle ilk büyük bölge kurtulacak!")
with open(f"{OUT}/area_010.json","w") as f: json.dump({"area":10,"levels":L},f,ensure_ascii=False,indent=2)
print(len(L))
