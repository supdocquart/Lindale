# Creates Desktop + Start Menu shortcuts for the installed Metrolist (used by kur.bat).
param([string]$Exe)
$shell = New-Object -ComObject WScript.Shell
$places = @(
    [Environment]::GetFolderPath('Desktop'),
    (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs')
)
foreach ($dir in $places) {
    $lnkPath = Join-Path $dir 'Metrolist.lnk'
    # Keep an existing shortcut that points somewhere else (e.g. the developer Metrolist.bat).
    if (Test-Path $lnkPath) {
        $old = $shell.CreateShortcut($lnkPath)
        if ($old.TargetPath -and ($old.TargetPath -ne $Exe)) {
            $keep = Join-Path $dir 'Metrolist (gelistirici).lnk'
            Move-Item -Force $lnkPath $keep
            Write-Output "Eski kisayol korundu: $keep"
        }
    }
    $lnk = $shell.CreateShortcut($lnkPath)
    $lnk.TargetPath = $Exe
    $lnk.WorkingDirectory = Split-Path $Exe
    $lnk.IconLocation = "$Exe,0"
    $lnk.Description = 'Metrolist'
    $lnk.Save()
    Write-Output "Kisayol: $lnkPath"
}
