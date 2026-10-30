@echo off
rem Lindale'yi deneysel mpv ses motoruyla baslatir. Normal kisayol VLC ile calismaya devam eder.
rem libmpv-2.dll "%LOCALAPPDATA%\Lindale\mpv" klasorunde olmali; yoksa _yedek\mpv-deneme\mpv-dll'den kopyalanir.
set APPDIR=%LOCALAPPDATA%\Lindale
set MPVDIR=%APPDIR%\mpv
set DLL_SRC=%~dp0..\_yedek\mpv-deneme\mpv-dll\libmpv-2.dll
if not exist "%MPVDIR%\libmpv-2.dll" (
    if not exist "%DLL_SRC%" (
        echo libmpv-2.dll bulunamadi. Indirip %MPVDIR% klasorune koyun.
        pause
        exit /b 1
    )
    if not exist "%MPVDIR%" mkdir "%MPVDIR%"
    copy /y "%DLL_SRC%" "%MPVDIR%\" >nul
)
if not exist "%APPDIR%\Lindale.exe" (
    echo Lindale kurulu degil, once kur.bat calistirin.
    pause
    exit /b 1
)
echo Acik Lindale kapatiliyor, mpv ile yeniden aciliyor...
taskkill /IM Lindale.exe /F >nul 2>&1
timeout /t 1 /nobreak >nul
set LINDALE_ENGINE=mpv
start "" "%APPDIR%\Lindale.exe"
echo.
echo Lindale mpv ile acildi. Log dosyasinda "Audio engine: mpv" satiri gorunmeli:
echo   %APPDIR%\logs\latest.log
echo VLC'ye donmek icin Lindale'yi kapatip normal kisayoldan acin.
pause
