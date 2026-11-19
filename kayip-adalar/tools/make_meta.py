# data/meta.json üretici: ekonomi, bina, araştırma, market, koleksiyon, başarım, görev, sefer, olay verileri.
import json, os
B = [
 ("CASTLE","Kraliyet Kalesi","Diğer binaların azami seviyesini belirler (kale seviyesi + 1).",10,{"GOLD":120,"STONE":40},1.7,("BUILDING_CAP",1)),
 ("LUMBER","Oduncu Atölyesi","Saatlik odun üretimi.",10,{"GOLD":60,"WOOD":10},1.5,("WOOD_PER_HOUR",12)),
 ("QUARRY","Maden Ocağı","Saatlik taş ve demir üretimi.",10,{"GOLD":70,"WOOD":20},1.5,("STONE_PER_HOUR",10)),
 ("FARM","Çiftlik","Saatlik yiyecek üretimi.",10,{"GOLD":60,"WOOD":15},1.5,("FOOD_PER_HOUR",12)),
 ("CRYSTAL_LAB","Kristal Laboratuvarı","Saatlik kristal üretimi ve bölümlerde kristal enerjisi.",8,{"GOLD":150,"STONE":40},1.6,("CRYSTAL_PER_HOUR",3)),
 ("BARRACKS","Asker Kışlası","Yeni birlik türleri açar, bölümlerde birlik canını artırır.",6,{"GOLD":120,"WOOD":40,"IRON":10},1.7,("UNIT_HP",1)),
 ("HARBOR","Ticaret Limanı","Kaynak takası oranını iyileştirir, saatlik altın üretir.",8,{"GOLD":100,"WOOD":40},1.55,("GOLD_PER_HOUR",15)),
 ("ENGINEERING","Ada Mühendisliği Merkezi","Bölümlere ek başlangıç enerjisiyle başlarsın (2 seviyede bir).",6,{"GOLD":140,"STONE":50,"IRON":10},1.7,("START_ENERGY",0.5)),
 ("ACADEMY","Araştırma Akademisi","Araştırma maliyetlerini düşürür.",8,{"GOLD":130,"STONE":30,"CRYSTAL":5},1.6,("RESEARCH_DISCOUNT_PCT",4)),
 ("TREASURY","Hazine Binası","Bina üretiminin birikebileceği süreyi uzatır.",8,{"GOLD":100,"STONE":40},1.55,("STORAGE_HOURS",2)),
 ("WATCHTOWER","Keşif Kulesi","Ek sefer yuvası açar (2 seviyede bir) ve sefer süresini kısaltır.",6,{"GOLD":110,"WOOD":30,"FOOD":20},1.6,("EXPEDITION_SLOTS",0.5)),
 ("MUSEUM","Koleksiyon Müzesi","Bölüm altın ödüllerini artırır.",8,{"GOLD":150,"STONE":30},1.6,("GOLD_REWARD_PCT",4)),
 ("QUEST_HALL","Günlük Görev Merkezi","Görev ödüllerini artırır.",6,{"GOLD":100,"WOOD":30},1.6,("QUEST_REWARD_PCT",10)),
 ("MAGIC_WORKSHOP","Büyü Atölyesi","Kombo hasarını artırır (2 seviyede bir).",6,{"GOLD":160,"CRYSTAL":10},1.7,("COMBO_DAMAGE",0.5)),
 ("DEFENSE_TOWER","Savunma Kulesi","Bölümlerde ve krallık savunmasında kale canını artırır.",8,{"GOLD":120,"STONE":50,"IRON":10},1.6,("CASTLE_HP",2)),
]
buildings=[{"id":i,"name":n,"desc":d,"maxLevel":m,"baseCost":c,"growth":g,"effect":{"kind":k,"value":v}} for i,n,d,m,c,g,(k,v) in B]
for b in buildings:
    if b["id"] in ("MAGIC_WORKSHOP","CRYSTAL_LAB"): b["unlockLevel"]=36
    if b["id"] in ("BARRACKS","DEFENSE_TOWER"): b["unlockLevel"]=10
    if b["id"] in ("WATCHTOWER",): b["unlockLevel"]=5
R = []
def node(i,br,t,n,d,c,k,v,req=(),ex=None):
    R.append({"id":i,"branch":br,"tier":t,"name":n,"desc":d,"cost":c,"requires":list(req),"exclusive":ex,"effect":{"kind":k,"value":v}})
node("ENG1","ENGINEERING",1,"Sağlam Köprüler","+1 başlangıç enerjisi.",{"GOLD":150,"STONE":30},"START_ENERGY",1)
node("ENG2","ENGINEERING",2,"Hafif Ada Motorları","+1 başlangıç enerjisi.",{"GOLD":300,"STONE":60,"IRON":10},"START_ENERGY",1,["ENG1"])
node("ENG3A","ENGINEERING",3,"Rüzgâr Yelkenleri","Her bölümde +1 hamle hakkı.",{"GOLD":600,"WOOD":100,"IRON":30},"MOVE_LIMIT",1,["ENG2"],"ENG3")
node("ENG3B","ENGINEERING",3,"Kristal Pistonlar","Kristal adaları tur başına +1 ek enerji verir.",{"GOLD":600,"CRYSTAL":30},"CRYSTAL_ENERGY",1,["ENG2"],"ENG3")
node("ENG4","ENGINEERING",4,"Yerçekimi Çapası","+2 başlangıç enerjisi.",{"GOLD":1000,"STONE":150,"IRON":60},"START_ENERGY",2,["ENG3A|ENG3B"])
node("ENG5","ENGINEERING",5,"Usta Mimar","Bina maliyetleri %10 azalır.",{"GOLD":1600,"STONE":250,"CRYSTAL":40},"BUILD_DISCOUNT_PCT",10,["ENG4"])
node("MIL1","MILITARY",1,"Keskin Kılıçlar","Birliklere +1 saldırı.",{"GOLD":180,"IRON":20},"UNIT_ATK",1)
node("MIL2","MILITARY",2,"Takviyeli Zırh","Birliklere +1 savunma.",{"GOLD":350,"IRON":40},"UNIT_DEF",1,["MIL1"])
node("MIL3A","MILITARY",3,"Saldırı Doktrini","Birliklere +1 saldırı.",{"GOLD":700,"IRON":80},"UNIT_ATK",1,["MIL2"],"MIL3")
node("MIL3B","MILITARY",3,"Kale Doktrini","Kale canı +4.",{"GOLD":700,"STONE":120},"CASTLE_HP",4,["MIL2"],"MIL3")
node("MIL4","MILITARY",4,"Seçkin Muhafızlar","Birliklere +2 can.",{"GOLD":1100,"FOOD":200,"IRON":80},"UNIT_HP",2,["MIL3A|MIL3B"])
node("MIL5","MILITARY",5,"Savaş Ustası","Birliklere +1 savunma ve kale canı +2.",{"GOLD":1700,"IRON":200},"UNIT_DEF",1,["MIL4"])
node("ECO1","ECONOMY",1,"Verimli Testereler","Bina üretimi %10 artar.",{"GOLD":120,"WOOD":40},"PRODUCTION_PCT",10)
node("ECO2","ECONOMY",2,"Geniş Ambarlar","Birikme süresi +2 saat.",{"GOLD":260,"WOOD":80},"STORAGE_HOURS",2,["ECO1"])
node("ECO3A","ECONOMY",3,"Tüccar Loncası","Takas oranı %15 iyileşir.",{"GOLD":550,"FOOD":120},"TRADE_RATE_PCT",15,["ECO2"],"ECO3")
node("ECO3B","ECONOMY",3,"Hazine Defteri","Bölüm altın ödülleri %15 artar.",{"GOLD":550,"STONE":100},"GOLD_REWARD_PCT",15,["ECO2"],"ECO3")
node("ECO4","ECONOMY",4,"Bereketli Topraklar","Bina üretimi %20 artar.",{"GOLD":950,"FOOD":200,"WOOD":200},"PRODUCTION_PCT",20,["ECO3A|ECO3B"])
node("ECO5","ECONOMY",5,"Altın Çağ","Bölüm altın ödülleri %20 artar.",{"GOLD":1500,"CRYSTAL":50},"GOLD_REWARD_PCT",20,["ECO4"])
node("MAG1","MAGIC",1,"Element Uyumu","Kombo hasarı +1.",{"GOLD":200,"CRYSTAL":10},"COMBO_DAMAGE",1)
node("MAG2","MAGIC",2,"Kristal Rezonansı","Kristal adaları +1 ek enerji.",{"GOLD":380,"CRYSTAL":25},"CRYSTAL_ENERGY",1,["MAG1"])
node("MAG3A","MAGIC",3,"Fırtına Çağrısı","Kombo hasarı +1.",{"GOLD":750,"CRYSTAL":50},"COMBO_DAMAGE",1,["MAG2"],"MAG3")
node("MAG3B","MAGIC",3,"Koruyucu Büyü","Kale canı +3.",{"GOLD":750,"CRYSTAL":50},"CASTLE_HP",3,["MAG2"],"MAG3")
node("MAG4","MAGIC",4,"Kadim Bilgi","+1 başlangıç enerjisi ve kristal üretimi %25.",{"GOLD":1200,"CRYSTAL":80,"ANCIENT_SHARD":2},"START_ENERGY",1,["MAG3A|MAG3B"])
node("MAG5","MAGIC",5,"Yıldız Büyüsü","Kombo hasarı +2.",{"GOLD":1800,"CRYSTAL":120,"STARDUST":5},"COMBO_DAMAGE",2,["MAG4"])

S=[]
def item(i,cat,n,price,color="",kind="COSMETIC",bundle=None,rep=0):
    d={"id":i,"category":cat,"name":n,"price":price,"kind":kind,"color":color,"reputation":rep}
    if bundle: d["bundle"]=bundle
    S.append(d)
for i,(n,c,p) in enumerate([("Bahar Çayırı","#7ED957",150),("Altın Kum","#F2C94C",250),("Mercan Kıyısı","#FF8A80",300),("Lavanta Bulutu","#B39DDB",400),("Gökkuşağı Taşı","#4FC3F7",600)]):
    item(f"ISL_{i}","ISLAND_SKIN",n,{"GOLD":p},c,rep=i)
for i,(n,c,p) in enumerate([("Mermer Kale","#ECEFF1",200),("Altın Kubbe","#FFD54F",350),("Gül Kulesi","#F48FB1",450),("Buz Sarayı","#81D4FA",600)]):
    item(f"CAS_{i}","CASTLE_SKIN",n,{"GOLD":p},c,rep=i)
for i,(n,c,p) in enumerate([("Halat Köprü","#C49A6C",120),("Taş Kemer","#B0BEC5",220),("Kristal Köprü","#80DEEA",380),("Altın Zincir","#FFCA28",520)]):
    item(f"BRG_{i}","BRIDGE",n,{"GOLD":p},c,rep=i)
for i,(n,c,p) in enumerate([("Kırmızı Pelerin","#E53935",180),("Zümrüt Miğfer","#43A047",260),("Gece Mavisi Zırh","#3949AB",340),("Güneş Tacı","#FDD835",500)]):
    item(f"UNI_{i}","UNIT_COSTUME",n,{"GOLD":p},c,rep=i)
for i,(n,c,p) in enumerate([("Konfeti Yağmuru","#FF7043",200),("Yıldız Patlaması","#FFEE58",320),("Kelebek Sürüsü","#BA68C8",450)]):
    item(f"ANI_{i}","ANIMATION",n,{"GOLD":p,"CRYSTAL":5*(i+1)},c,rep=i)
for i,(n,c,p) in enumerate([("Meşe Çerçeve","#8D6E63",100),("Gümüş Çerçeve","#B0BEC5",250),("Altın Çerçeve","#FFC107",500),("Ejder Çerçevesi","#EF5350",900)]):
    item(f"FRM_{i}","FRAME",n,{"GOLD":p},c,rep=i*2)
for i,(n,c,p) in enumerate([("Çiçek Bahçesi","#F06292",150),("Fıskiye","#4FC3F7",250),("Bayrak Direği","#E53935",200),("Heykel","#BDBDBD",450),("Yel Değirmeni","#FFB74D",380),("Fener","#FFF176",300)]):
    item(f"DEC_{i}","DECORATION",n,{"GOLD":p,"WOOD":20},c,rep=i)
for i,(n,price,b) in enumerate([("Oduncu Sandığı",{"GOLD":120},{"WOOD":150}),("Taş Yığını",{"GOLD":140},{"STONE":150}),("Demir Külçeleri",{"GOLD":200},{"IRON":80}),("Erzak Paketi",{"GOLD":120},{"FOOD":150}),("Kristal Kesesi",{"GOLD":300},{"CRYSTAL":30}),("Yıldız Tozu Şişesi",{"CRYSTAL":40},{"STARDUST":3})]):
    item(f"BND_{i}","BUNDLE",n,price,kind="BUNDLE",bundle=b)
for i,(n,price) in enumerate([("Gizemli Harita Parçası",{"STARDUST":4}),("Kadim Sandık",{"ANCIENT_SHARD":3}),("Kraliyet Hediyesi",{"ROYAL_SEAL":1})]):
    item(f"COL_{i}","COLLECTIBLE",n,price,kind="COLLECTION")
for i,(n,c,p) in enumerate([("Gün Doğumu Gökyüzü","#FFE0B2",300),("Nane Rüzgârı","#C8F7DC",400),("Pembe Akşam","#F8BBD0",500),("Masmavi Yaz","#B3E5FC",250)]):
    item(f"THM_{i}","THEME",n,{"GOLD":p},c,rep=i)

C=[
 ("MAP","Kadim Harita Parçaları","Harita Parçası",("GOLD_REWARD_PCT",5)),
 ("CRYSTAL","Efsanevi Kristaller","Efsanevi Kristal",("CRYSTAL_ENERGY",1)),
 ("SYMBOL","Kayıp Krallık Sembolleri","Krallık Sembolü",("CASTLE_HP",2)),
 ("RELIC","Nadir Ada Kalıntıları","Ada Kalıntısı",("PRODUCTION_PCT",10)),
 ("RUNE","Rüzgâr Runları","Rüzgâr Runu",("START_ENERGY",1)),
 ("FEATHER","Ejder Tüyleri","Ejder Tüyü",("UNIT_ATK",1)),
 ("SHELL","Gök Deniz Kabukları","Gök Kabuğu",("STORAGE_HOURS",2)),
 ("COIN","Eski Sikkeler","Eski Sikke",("GOLD_REWARD_PCT",5)),
 ("SEED","Sihirli Tohumlar","Sihirli Tohum",("PRODUCTION_PCT",10)),
 ("STAR","Yıldız Haritaları","Yıldız Haritası",("COMBO_DAMAGE",1)),
]
coll=[{"id":i,"name":n,"itemName":it,"bonus":{"kind":k,"value":v}} for i,n,it,(k,v) in C]
A=[
 ("levelsCompleted","Bölüm Fatihi","{n} bölüm tamamla.",[1,5,10,25,50,100,200,300,400,500],60,0),
 ("perfectWins","Kusursuz Strateji","{n} bölümü 3 yıldızla bitir.",[1,10,25,50,100,250,500],80,1),
 ("bridgesFormed","Köprü Ustası","{n} ada bağlantısı kur.",[10,100,500,1000,5000,10000],50,0),
 ("combos","Element Bilgesi","{n} element kombosu oluştur.",[1,10,50,100,250,500,1000],60,0),
 ("combosDiscovered","Kâşif Büyücü","{n} farklı kombo keşfet.",[1,5,10,15,20,24],100,1),
 ("islandsMoved","Ada Taşıyıcı","{n} ada hareketi yap.",[50,250,1000,5000,10000],40,0),
 ("rotations","Döndürme Ustası","{n} ada döndür.",[25,100,500,2000],40,0),
 ("enemiesDefeated","Krallık Kılıcı","{n} düşman yen.",[10,50,100,250,500,1000],70,0),
 ("bossesDefeated","Patron Avcısı","{n} alan patronu yen.",[1,5,10,25,50,100],120,2),
 ("buildingsUpgraded","İnşaat Ustası","{n} bina geliştir.",[1,10,25,50,100],60,0),
 ("researchDone","Bilge Akademisyen","{n} araştırma tamamla.",[1,5,10,15,20],80,1),
 ("collectionItems","Koleksiyoncu","{n} koleksiyon öğesi bul.",[1,10,25,50,75,100],90,1),
 ("expeditionsDone","Gezgin Muhafız","{n} sefer tamamla.",[1,10,25,50,100],60,0),
 ("endlessBest","Sonsuz Yolcu","Sonsuz Takımadalar'da {n}. aşamaya ulaş.",[5,10,20,30,50],90,1),
 ("dailyPuzzles","Günün Zekâsı","{n} günlük bulmaca çöz.",[1,7,30,100],70,1),
 ("goldEarned","Altın Hazinesi","Toplam {n} altın kazan.",[1000,10000,50000,250000],50,0),
 ("challengesWon","Mücadele Ustası","{n} özel mücadele kazan.",[1,10,50,100],80,1),
 ("eventsResolved","Krallık Hâkimi","{n} krallık olayını sonuçlandır.",[1,10,50],50,0),
 ("shopPurchases","Cömert Hükümdar","Marketten {n} ürün al.",[1,10,30],50,0),
 ("defensesWon","Kale Savunucusu","{n} krallık savunması kazan.",[1,10,50],90,1),
]
ach=[{"stat":s,"name":n,"desc":d,"tiers":t,"rewardGold":g,"rewardStardust":sd} for s,n,d,t,g,sd in A]
assert sum(len(a["tiers"]) for a in ach)>=100, sum(len(a["tiers"]) for a in ach)
Q=[
 ("Q_LEVELS","levelsCompleted","{n} bölüm tamamla.",3,15,{"GOLD":80},{"GOLD":400,"STARDUST":2}),
 ("Q_COMBOS","combos","{n} element kombosu oluştur.",5,30,{"GOLD":70,"CRYSTAL":3},{"GOLD":350,"CRYSTAL":20}),
 ("Q_BUILD","buildingsUpgraded","{n} bina geliştir.",1,6,{"GOLD":60,"WOOD":30},{"GOLD":300,"ANCIENT_SHARD":1}),
 ("Q_BRIDGES","bridgesFormed","{n} köprü kur.",15,100,{"GOLD":60,"STONE":30},{"GOLD":300,"STONE":150}),
 ("Q_ENEMIES","enemiesDefeated","{n} düşman yen.",5,40,{"GOLD":80,"IRON":10},{"GOLD":400,"IRON":60}),
 ("Q_STARS","starsEarned","{n} yıldız kazan.",6,40,{"GOLD":90},{"GOLD":450,"STARDUST":2}),
 ("Q_MOVES","islandsMoved","{n} ada hareketi yap.",25,200,{"GOLD":50,"FOOD":30},{"GOLD":250,"FOOD":150}),
 ("Q_EXPED","expeditionsDone","{n} sefer tamamla.",1,7,{"GOLD":60,"FOOD":20},{"GOLD":300,"ANCIENT_SHARD":1}),
 ("Q_PUZZLE","dailyPuzzles","Günlük bulmacayı çöz.",1,5,{"GOLD":70,"CRYSTAL":5},{"GOLD":350,"ROYAL_SEAL":1}),
 ("Q_ENDLESS","endlessStages","Sonsuz modda {n} aşama geç.",3,20,{"GOLD":70},{"GOLD":350,"STARDUST":3}),
]
quests=[{"id":i,"stat":s,"desc":d,"daily":a,"weekly":w,"reward":r,"weeklyReward":wr} for i,s,d,a,w,r,wr in Q]
E=[
 ("SCOUT_SHORT","Yakın Adalar Keşfi",5,{"FOOD":10},{"WOOD":40,"GOLD":30},5,0),
 ("FOREST_TRIP","Orman Seferi",15,{"FOOD":25},{"WOOD":120,"FOOD":20},8,0),
 ("MINE_TRIP","Derin Maden Seferi",30,{"FOOD":40},{"STONE":120,"IRON":40},10,5),
 ("CRYSTAL_TRIP","Kristal Mağarası Seferi",60,{"FOOD":60,"GOLD":50},{"CRYSTAL":30,"GOLD":120},15,20),
 ("RUINS_TRIP","Kadim Harabeler",120,{"FOOD":90,"GOLD":100},{"ANCIENT_SHARD":2,"GOLD":250},30,40),
 ("STAR_TRIP","Yıldız Adası Seferi",240,{"FOOD":150,"CRYSTAL":20},{"STARDUST":4,"GOLD":400,"ROYAL_SEAL":1},45,80),
]
exps=[{"id":i,"name":n,"minutes":m,"cost":c,"rewards":r,"collectionChancePct":p,"unlockLevel":u} for i,n,m,c,r,p,u in E]
EV=[
 ("MERCHANT","Gezgin Tüccar","Uzak adalardan bir tüccar geldi. Yiyeceğini altınla takas etmek istiyor.",[("Takas et (50 yiyecek → 80 altın)",{"FOOD":50},{"GOLD":80}),("Kristal karşılığı sat (40 odun → 6 kristal)",{"WOOD":40},{"CRYSTAL":6}),("Teşekkür et ve gönder",{},{})]),
 ("CRYSTAL_RAIN","Kristal Yağmuru","Gökyüzünden parlak kristaller yağıyor!",[("Herkesi toplamaya gönder (20 yiyecek)",{"FOOD":20},{"CRYSTAL":15}),("Sadece kale bahçesindekileri topla",{},{"CRYSTAL":5})]),
 ("QUAKE","Ada Depremi","Ana ada sarsıldı, bazı yapılar hasar gördü.",[("Taşla onar (40 taş)",{"STONE":40},{"GOLD":60}),("Halk kendi onarsın (yiyecek dağıt)",{"FOOD":30},{"WOOD":30})]),
 ("TREASURE","Kayıp Hazine","Keşifçiler eski bir sandık buldu. Açmak için mühendis gerekiyor.",[("Mühendis çağır (50 altın)",{"GOLD":50},{"ANCIENT_SHARD":1,"GOLD":40}),("Sandığı müzeye bağışla",{},{"STARDUST":1})]),
 ("VISITOR","Gizemli Ziyaretçi","Pelerinli bir yabancı bir harita satmak istiyor.",[("Haritayı al (2 kadim parça)",{"ANCIENT_SHARD":2},{"STARDUST":3,"CRYSTAL":10}),("Ona yiyecek ver",{"FOOD":20},{"GOLD":40}),("Kibarca reddet",{},{})]),
 ("FESTIVAL","Hasat Şenliği","Halk şenlik yapmak istiyor.",[("Şenliği finanse et (60 altın)",{"GOLD":60},{"FOOD":80,"WOOD":40}),("Sade bir kutlama yap",{},{"FOOD":20})]),
 ("STORM","Fırtına Yaklaşıyor","Rüzgâr adası yönünden büyük bir fırtına geliyor.",[("Köprüleri sabitle (30 odun)",{"WOOD":30},{"GOLD":50,"STONE":20}),("Fırtınanın enerjisini topla",{},{"CRYSTAL":4})]),
]
evs=[{"id":i,"title":t,"text":x,"choices":[{"label":l,"cost":c,"reward":r} for l,c,r in ch]} for i,t,x,ch in EV]
rep=[{"xp":x,"title":t} for x,t in [(0,"Ada Çırağı"),(100,"Köy Muhafızı"),(300,"Köprü Ustası"),(700,"Ada Lordu"),(1300,"Takımada Beyi"),(2200,"Gök Kontu"),(3500,"Krallık Dükü"),(5200,"Yüce Muhafız"),(7500,"Efsane Hükümdar"),(10500,"Birleşik Krallık Kralı")]]
meta={"economy":{},"buildings":buildings,"research":R,"shop":S,"collection":coll,"achievements":ach,"quests":quests,"expeditions":exps,"events":evs,"reputation":rep}
out=os.path.join(os.path.dirname(__file__),"..","assets","data","meta.json")
json.dump(meta,open(out,"w"),ensure_ascii=False,indent=1)
print("başarım:",sum(len(a["tiers"]) for a in ach),"market:",len(S),"araştırma:",len(R))
