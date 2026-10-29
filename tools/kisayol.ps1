# Creates Desktop + Start Menu shortcuts for the installed Lindale (used by kur.bat).
# -OldExe: shortcuts named Metrolist that point there (the pre-rename install) are removed.
param([string]$Exe, [string]$OldExe)
$shell = New-Object -ComObject WScript.Shell
$places = @(
    [Environment]::GetFolderPath('Desktop'),
    (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs')
)
foreach ($dir in $places) {
    $legacy = Join-Path $dir 'Metrolist.lnk'
    if ($OldExe -and (Test-Path $legacy)) {
        $l = $shell.CreateShortcut($legacy)
        if ($l.TargetPath -eq $OldExe) { Remove-Item -Force $legacy; Write-Output "Eski kisayol silindi: $legacy" }
    }
    $lnkPath = Join-Path $dir 'Lindale.lnk'
    # Keep an existing shortcut that points somewhere else (e.g. a developer build).
    if (Test-Path $lnkPath) {
        $old = $shell.CreateShortcut($lnkPath)
        if ($old.TargetPath -and ($old.TargetPath -ne $Exe)) {
            $keep = Join-Path $dir 'Lindale (gelistirici).lnk'
            Move-Item -Force $lnkPath $keep
            Write-Output "Eski kisayol korundu: $keep"
        }
    }
    $lnk = $shell.CreateShortcut($lnkPath)
    $lnk.TargetPath = $Exe
    $lnk.WorkingDirectory = Split-Path $Exe
    $lnk.IconLocation = "$Exe,0"
    $lnk.Description = 'Lindale'
    $lnk.Save()
    Write-Output "Kisayol: $lnkPath"
}
