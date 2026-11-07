# Starts a built Lindale on the GitHub Windows runner, waits for the first frame, checks the start-up
# log, takes a screenshot of the desktop and closes the app. Used by .github/workflows/check.yml.
# Exit code 0 = every check passed. Results (log, screenshot, summary) go to -Out.
param(
    [Parameter(Mandatory = $true)][string]$App,
    [string]$Out = "windows-check",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force $Out | Out-Null
$log = Join-Path $env:LOCALAPPDATA "Lindale\logs\latest.log"
$results = [System.Collections.Generic.List[string]]::new()
$failed = 0

function Check([string]$name, [bool]$ok) {
    $line = $(if ($ok) { "PASS " } else { "FAIL " }) + $name
    Write-Host $line
    $results.Add($line)
    if (-not $ok) { $script:failed++ }
}

function LogText {
    if (Test-Path $log) {
        # The app keeps the file open; read it shared.
        $fs = [System.IO.File]::Open($log, "Open", "Read", "ReadWrite")
        try { (New-Object System.IO.StreamReader($fs)).ReadToEnd() } finally { $fs.Dispose() }
    } else { "" }
}

function Screenshot([string]$path) {
    Add-Type -AssemblyName System.Windows.Forms, System.Drawing
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bmp = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
        $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        return $true
    } catch {
        Write-Host "screenshot failed: $_"
        return $false
    } finally { $g.Dispose(); $bmp.Dispose() }
}

$proc = Start-Process -FilePath (Resolve-Path $App) -PassThru
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline -and -not $proc.HasExited -and (LogText) -notmatch "\[STARTUP\] first_frame") {
    Start-Sleep -Seconds 2
}
# Let the background start-up (VLC, media session, tray, home page) finish.
Start-Sleep -Seconds 25

$text = LogText
Check "app is still running" (-not $proc.HasExited)
Check "first frame drawn" ($text -match "\[STARTUP\] first_frame")
Check "VLC engine loads" ($text -match "Audio engine: vlc")
Check "system media controls (SMTC) ready" ($text -match "media session: system media controls ready")
Check "no crash in the log" ($text -notmatch "\[CRASH\]")
$shot = Screenshot (Join-Path $Out "screen.png")
Check "screenshot taken" $shot

if (-not $proc.HasExited) {
    # A normal close first, so the app writes its queue and settings like on a real PC.
    $null = $proc.CloseMainWindow()
    if (-not $proc.WaitForExit(15000)) { Stop-Process -Id $proc.Id -Force }
}
Start-Sleep -Seconds 2
Copy-Item $log (Join-Path $Out "latest.log") -ErrorAction SilentlyContinue
$results | Set-Content (Join-Path $Out "summary.txt")
Write-Host "$($results.Count - $failed)/$($results.Count) passed"
exit $failed
