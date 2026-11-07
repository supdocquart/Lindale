# Starts a built Lindale on the GitHub Windows runner and checks it like a first-time user would:
# start-up log (first frame, VLC, SMTC), skip the welcome guide, search, play the first result and
# wait for the position to move. Screenshots after each step. Used by .github/workflows/check.yml.
# Exit code = number of failed checks. Results (log, screenshots, summary) go to -Out.
param(
    [Parameter(Mandatory = $true)][string]$App,
    [string]$Out = "windows-check",
    [string]$Query = "never gonna give you up",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force $Out | Out-Null
$log = Join-Path $env:LOCALAPPDATA "Lindale\logs\latest.log"
$results = [System.Collections.Generic.List[string]]::new()
$failed = 0

Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Win {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    public delegate bool EnumProc(IntPtr h, IntPtr p);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
    // The largest visible top-level window of the process (the main window, not a dialog or the tray).
    public static IntPtr MainWindow(uint pid) {
        IntPtr best = IntPtr.Zero; long bestArea = 0;
        EnumWindows((h, p) => {
            uint owner; GetWindowThreadProcessId(h, out owner);
            RECT r;
            if (owner == pid && IsWindowVisible(h) && GetWindowRect(h, out r)) {
                long area = (long)(r.Right - r.Left) * (r.Bottom - r.Top);
                if (area > bestArea) { bestArea = area; best = h; }
            }
            return true;
        }, IntPtr.Zero);
        return best;
    }
    public static void Click(int x, int y) {
        SetCursorPos(x, y);
        System.Threading.Thread.Sleep(150);
        mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
        System.Threading.Thread.Sleep(60);
        mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
    }
}
"@

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

function WaitLog([string]$pattern, [int]$seconds, [int]$since = 0) {
    $end = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $end) {
        $t = LogText
        if ($t.Length -gt $since -and $t.Substring($since) -match $pattern) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Screenshot([string]$name) {
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bmp = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
        $bmp.Save((Join-Path $Out $name), [System.Drawing.Imaging.ImageFormat]::Png)
        return $true
    } catch {
        Write-Host "screenshot failed: $_"
        return $false
    } finally { $g.Dispose(); $bmp.Dispose() }
}

function MainRect {
    $h = [Win]::MainWindow([uint32]$proc.Id)
    $r = New-Object Win+RECT
    [void][Win]::GetWindowRect($h, [ref]$r)
    [void][Win]::SetForegroundWindow($h)
    return $r
}

$proc = Start-Process -FilePath (Resolve-Path $App) -PassThru
$null = WaitLog "\[STARTUP\] first_frame" $TimeoutSeconds
# Let the background start-up (VLC, media session, tray, home page) finish.
Start-Sleep -Seconds 20

$text = LogText
Check "app is still running" (-not $proc.HasExited)
Check "first frame drawn" ($text -match "\[STARTUP\] first_frame")
Check "VLC engine loads" ($text -match "Audio engine: vlc")
Check "system media controls (SMTC) ready" ($text -match "media session: system media controls ready")
Check "screenshot taken" (Screenshot "1-start.png")

if (-not $proc.HasExited) {
    # Welcome guide: "Skip" sits left of the centre of the dialog, which is centred on the window.
    $r = MainRect
    Write-Host "main window: $($r.Left),$($r.Top) - $($r.Right),$($r.Bottom)"
    $cx = [int](($r.Left + $r.Right) / 2); $cy = [int](($r.Top + $r.Bottom) / 2)
    [Win]::Click($cx - 204, $cy + 132)
    Start-Sleep -Seconds 2

    # Search with the keyboard (Ctrl+K), then play the first result.
    $mark = (LogText).Length
    $r = MainRect
    [System.Windows.Forms.SendKeys]::SendWait("^k")
    Start-Sleep -Milliseconds 600
    [System.Windows.Forms.SendKeys]::SendWait($Query + "{ENTER}")
    Check "search opens the results page" (WaitLog "\[UI:NAV\] route=search" 20 $mark)
    Start-Sleep -Seconds 6
    [void](Screenshot "2-search.png")

    $mark = (LogText).Length
    [Win]::Click($r.Left + 272, $r.Top + 196)
    Check "a search result starts" (WaitLog "\[UI:NOW_PLAYING\] id=" 20 $mark)
    # The queue is saved every 15 s with the position; a position past 3 s means audio is playing.
    $played = WaitLog "Persisted queue: \d+ items, index=\d+, position=([3-9]\d{3}|\d{5,}) ms" 60 $mark
    $after = (LogText).Substring($mark)
    Check "the stream resolves and plays" ($played -and $after -notmatch "Failed to resolve stream")
    [void](Screenshot "3-playing.png")
}

Check "no crash in the log" ((LogText) -notmatch "\[CRASH\]")

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
