# Creates Desktop + Start Menu shortcuts for the installed Lindale (used by kur.bat).
# -OldExe:  the pre-rename install (Metrolist); its shortcuts are removed.
# -PrevExe: the old kur.bat install folder; its shortcuts are removed so only one Lindale is left.
param([string]$Exe, [string]$OldExe, [string]$PrevExe)
$shell = New-Object -ComObject WScript.Shell
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'
$places = @([Environment]::GetFolderPath('Desktop'), $startMenu)
foreach ($dir in $places) {
    # Remove every Metrolist/Lindale shortcut that points at an old copy, including the
    # "Lindale (gelistirici)" ones earlier versions of this script left behind.
    Get-ChildItem -Path $dir -Filter '*.lnk' -ErrorAction SilentlyContinue |
        Where-Object { $_.BaseName -like 'Lindale*' -or $_.BaseName -like 'Metrolist*' } |
        ForEach-Object {
            $target = $shell.CreateShortcut($_.FullName).TargetPath
            if ($target -ne $Exe -and ($target -eq $OldExe -or $target -eq $PrevExe -or $_.BaseName -ne 'Lindale')) {
                Remove-Item -Force $_.FullName
                Write-Output "Eski kisayol silindi: $($_.FullName)"
            }
        }
    # The installer already puts Lindale in its own Start Menu folder; do not add a second entry.
    if ($dir -eq $startMenu -and (Test-Path (Join-Path $startMenu 'Lindale\Lindale.lnk'))) {
        $root = Join-Path $startMenu 'Lindale.lnk'
        if (Test-Path $root) { Remove-Item -Force $root }
        continue
    }
    $lnk = $shell.CreateShortcut((Join-Path $dir 'Lindale.lnk'))
    $lnk.TargetPath = $Exe
    $lnk.WorkingDirectory = Split-Path $Exe
    $lnk.IconLocation = "$Exe,0"
    $lnk.Description = 'Lindale'
    $lnk.Save()
    Write-Output "Kisayol: $(Join-Path $dir 'Lindale.lnk')"
}
