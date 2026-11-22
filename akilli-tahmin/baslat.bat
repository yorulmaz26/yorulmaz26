@echo off
setlocal
cd /d "%~dp0"
title Akilli Tahmin
if not exist ".venv\Scripts\python.exe" goto kurulmamis
if not exist "gercek.sqlite3" goto kurulmamis

if defined API_FOOTBALL_KEY goto anahtarvar
echo.
echo  Canli radar icin API-Football anahtari gerekir.
echo  Anahtarinizi yapistirip Enter'a basin. Anahtar yoksa sadece Enter'a basin.
echo  Yapistirmak icin pencerede sag tiklayin.
echo.
set "API_FOOTBALL_KEY="
set /p "API_FOOTBALL_KEY=Anahtar: "
if not defined API_FOOTBALL_KEY goto anahtaryok
setx API_FOOTBALL_KEY "%API_FOOTBALL_KEY%" >nul
echo  Anahtar bu bilgisayardaki kullanici ayarlarina kaydedildi; bir daha sorulmayacak.
goto anahtarvar

:anahtaryok
echo  Anahtar girilmedi: canli radar kapali, diger bolumler calisir.
set "AKILLI_TAHMIN_WINDOW_RADAR=0"

:anahtarvar
echo.
echo  Akilli Tahmin calisiyor. Tarayici birkac saniye icinde acilacak:
echo  http://127.0.0.1:8000
echo  Bu pencere acik kaldigi surece program calisir. Kapatmak icin pencereyi kapatin.
echo.
start "" /min cmd /c "ping -n 6 127.0.0.1 >nul & start http://127.0.0.1:8000"
".venv\Scripts\python.exe" -m akilli_tahmin serve --db gercek.sqlite3
echo.
echo  Program durdu. Hata yazisi varsa fotografini cekip gonderin.
pause
exit /b 0

:kurulmamis
echo.
echo  Once kur.bat dosyasina cift tiklayin.
echo.
pause
exit /b 1
