@echo off
rem Builds Lindale and installs it to %LOCALAPPDATA%\Programs\Lindale with Desktop and
rem Start Menu shortcuts. Run again to update. Settings and library are kept (%APPDATA%\Lindale).
cd /d "%~dp0"
if not exist logs mkdir logs
set LOG=logs\install.log
set SRC=desktop\build\compose\binaries\main\app\Lindale
set DEST=%LOCALAPPDATA%\Programs\Lindale
set OLD_DEST=%LOCALAPPDATA%\Programs\Metrolist
echo Lindale kuruluyor... (sonuc %LOG% dosyasina yaziliyor)
echo [1/4] VLC hazirlaniyor > %LOG%

set VLC_SRC=%LOCALAPPDATA%\Lindale\vlc\vlc-3.0.24
if not exist "%VLC_SRC%\libvlc.dll" set VLC_SRC=%LOCALAPPDATA%\Metrolist\vlc\vlc-3.0.24
if not exist "%VLC_SRC%\libvlc.dll" set VLC_SRC=C:\Program Files\VideoLAN\VLC
if not exist "%VLC_SRC%\libvlc.dll" (
    echo VLC bulunamadi: once Metrolist.bat ile bir kez calistirin. >> %LOG%
    echo INSTALL FAILED >> %LOG%
    echo VLC bulunamadi. & pause & exit /b 1
)
if not exist desktop\appResources\windows\vlc\libvlc.dll robocopy "%VLC_SRC%" desktop\appResources\windows\vlc /E /NFL /NDL /NJH /NJS /NP >> %LOG%

echo [2/4] Uygulama derleniyor >> %LOG%
call .\gradlew.bat :desktop:createDistributable --no-configuration-cache --console=plain >> %LOG% 2>&1
if not %ERRORLEVEL%==0 (
    echo INSTALL FAILED >> %LOG%
    echo Derleme HATALI - ayrintilar: %LOG%
    pause & exit /b 1
)

echo [3/4] Kopyalaniyor: %DEST% >> %LOG%
taskkill /IM Lindale.exe /F >nul 2>&1
taskkill /IM Metrolist.exe /F >nul 2>&1
timeout /t 1 /nobreak >nul
rem VLC eklentileri her derlemede yeni tarihle uretiliyor; hepsini yeniden kopyalamak Windows Defender
rem taramasina yol aciyor ve ilk acilisi ~12 sn yavaslatiyordu. VLC klasorunde sadece eksik dosyalar kopyalanir
rem (VLC surumu degisirse kurulu app\resources\vlc klasorunu silip tekrar kurun).
robocopy "%SRC%" "%DEST%" /MIR /XD "%SRC%\app\resources\vlc" "%SRC%\app\resources\mpv" /NFL /NDL /NJH /NJS /NP >> %LOG%
set RC=%ERRORLEVEL%
robocopy "%SRC%\app\resources\vlc" "%DEST%\app\resources\vlc" /MIR /XO /XN /NFL /NDL /NJH /NJS /NP >> %LOG%
if %RC% LSS %ERRORLEVEL% set RC=%ERRORLEVEL%
rem libmpv (tek buyuk DLL) de ayni sekilde: sadece degistiyse kopyala.
robocopy "%SRC%\app\resources\mpv" "%DEST%\app\resources\mpv" /MIR /XO /XN /NFL /NDL /NJH /NJS /NP >> %LOG%
if %RC% LSS %ERRORLEVEL% set RC=%ERRORLEVEL%
if %RC% GEQ 8 (
    echo INSTALL FAILED >> %LOG%
    echo Kopyalama HATALI - Lindale acik olabilir, kapatip tekrar deneyin.
    pause & exit /b 1
)

echo [4/4] Kisayollar olusturuluyor >> %LOG%
powershell -NoProfile -ExecutionPolicy Bypass -File tools\kisayol.ps1 -Exe "%DEST%\Lindale.exe" -OldExe "%OLD_DEST%\Metrolist.exe" >> %LOG% 2>&1
rem Eski isimle (Metrolist) yapilmis kurulumu kaldir; ayarlar ve kutuphane uygulama ilk acilista Lindale klasorune tasinir.
if exist "%OLD_DEST%\Metrolist.exe" rmdir /s /q "%OLD_DEST%" >> %LOG% 2>&1

echo INSTALL OK >> %LOG%
echo.
echo Kuruldu: %DEST%
echo Masaustundeki ve Baslat menusundeki "Lindale" kisayolunu kullanabilirsiniz.
pause
