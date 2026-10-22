@echo off
cd /d "%~dp0"
if not exist logs mkdir logs
if not exist dist mkdir dist
set LOG=logs\package.log
echo Paketleniyor... (sonuc %LOG% dosyasina yaziliyor)
echo [1/4] VLC kopyalaniyor > %LOG%

set VLC_SRC=%LOCALAPPDATA%\Metrolist\vlc\vlc-3.0.24
if not exist "%VLC_SRC%\libvlc.dll" set VLC_SRC=C:\Program Files\VideoLAN\VLC
if not exist "%VLC_SRC%\libvlc.dll" (
    echo VLC bulunamadi: once Metrolist'i bir kez calistirin. >> %LOG%
    echo PACKAGE FAILED >> %LOG%
    echo VLC bulunamadi. & pause & exit /b 1
)
if exist desktop\appResources\windows\vlc rmdir /s /q desktop\appResources\windows\vlc
robocopy "%VLC_SRC%" desktop\appResources\windows\vlc /E /NFL /NDL /NJH /NJS /NP >> %LOG%

echo [2/4] Uygulama paketleniyor >> %LOG%
call .\gradlew.bat :desktop:createDistributable --no-configuration-cache --console=plain >> %LOG% 2>&1
if not %ERRORLEVEL%==0 (
    echo PACKAGE FAILED >> %LOG%
    echo Paketleme HATALI - ayrintilar: %LOG%
    pause & exit /b 1
)

echo [3/4] Zip olusturuluyor >> %LOG%
if exist dist\Metrolist-demo.zip del /q dist\Metrolist-demo.zip
powershell -NoProfile -Command "Compress-Archive -Path 'desktop\build\compose\binaries\main\app\Metrolist' -DestinationPath 'dist\Metrolist-demo.zip' -CompressionLevel Optimal" >> %LOG% 2>&1

echo [4/4] Kurulum exe'si olusturuluyor >> %LOG%
call .\gradlew.bat :desktop:packageExe --no-configuration-cache --console=plain >> %LOG% 2>&1
if %ERRORLEVEL%==0 (
    for %%f in (desktop\build\compose\binaries\main\exe\*.exe) do copy /y "%%f" dist\Metrolist-demo-setup.exe >> %LOG%
) else (
    echo EXE adimi basarisiz, zip yine de hazir. >> %LOG%
)

echo PACKAGE OK >> %LOG%
echo Hazir: dist klasorune bakin.
pause
