@echo off
setlocal
cd /d "%~dp0"
title Akilli Tahmin - Kurulum
echo.
echo  AKILLI TAHMIN - KURULUM
echo  =======================
echo.

rem Paketler Python 3.10-3.13 ile uyumludur; once 3.13 ve 3.12 aranir.
set "PY="
where py >nul 2>nul
if errorlevel 1 goto nopy
py -3.13 -c "import sys" >nul 2>nul
if not errorlevel 1 set "PY=py -3.13"
if defined PY goto checkver
py -3.12 -c "import sys" >nul 2>nul
if not errorlevel 1 set "PY=py -3.12"
if defined PY goto checkver
set "PY=py -3"
goto checkver
:nopy
where python >nul 2>nul
if errorlevel 1 goto nopython
set "PY=python"

:checkver
%PY% -c "import sys; sys.exit(0 if (3, 10) <= sys.version_info[:2] <= (3, 13) else 1)" >nul 2>nul
if errorlevel 1 goto nopython

if exist ".venv\Scripts\python.exe" goto venvok
echo Python ortami hazirlaniyor...
%PY% -m venv .venv
if errorlevel 1 goto hata
:venvok

echo Gerekli paketler kuruluyor. Internet baglantisi gerekir...
".venv\Scripts\python.exe" -m pip install --disable-pip-version-check -r requirements.txt
if errorlevel 1 goto hata

echo.
".venv\Scripts\python.exe" -m akilli_tahmin kurulum --db gercek.sqlite3
if errorlevel 1 goto hata

echo.
echo  Kurulum tamam. Simdi baslat.bat dosyasina cift tiklayin.
echo.
pause
exit /b 0

:nopython
echo.
echo  Uygun Python bulunamadi. Python 3.13 gerekli.
echo  1. python.org/downloads/windows adresinden Python 3.13 surumunu indirip kurun.
echo     En yeni surum 3.14 veya ustu ise listeden 3.13 olani secin.
echo  2. Kurulumun ilk ekraninda "Add python.exe to PATH" kutusunu isaretleyin.
echo  3. Sonra bu dosyaya yeniden cift tiklayin.
echo.
pause
exit /b 1

:hata
echo.
echo  Bir hata olustu. Bu penceredeki yazilarin fotografini cekip gonderin.
echo.
pause
exit /b 1
