$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Mux = Join-Path $Root "third_party\Turnable\pkg\connection\mux.go"

if (!(Test-Path $Mux)) {
    throw "mux.go not found: $Mux"
}

$txt = Get-Content -Raw $Mux

if ($txt -notmatch "\bmuxHealthLogInterval\s*=") {
    if ($txt -match '(?m)^(\s*)(muxPingTimeout\s*=\s*.+)$') {
        $txt = [regex]::Replace(
            $txt,
            '(?m)^(\s*)(muxPingTimeout\s*=\s*.+)$',
            "`$1`$2`r`n`$1`r`n`$1// muxHealthLogInterval limits debug health logs emitted by the client.`r`n`$1muxHealthLogInterval = 10 * time.Second",
            1
        )
    } elseif ($txt -match '(?m)^(\s*)(muxPingInterval\s*=\s*.+)$') {
        $txt = [regex]::Replace(
            $txt,
            '(?m)^(\s*)(muxPingInterval\s*=\s*.+)$',
            "`$1`$2`r`n`$1`r`n`$1// muxHealthLogInterval limits debug health logs emitted by the client.`r`n`$1muxHealthLogInterval = 10 * time.Second",
            1
        )
    } else {
        throw "Could not find mux ping const line to insert muxHealthLogInterval"
    }
}

if ($txt -notmatch "lastHealthLog\s+atomic\.Int64") {
    $txt = [regex]::Replace(
        $txt,
        '(firstUnanswered\s+atomic\.Int64\s*)(\r?\n\s*pingCtx)',
        "`$1`r`n`r`n`tlastHealthLog atomic.Int64`$2"
    )
}

if ($txt -notmatch "client\.lastHealthLog\.Store\(now\)") {
    $txt = [regex]::Replace(
        $txt,
        '(client\.lastPingSent\.Store\(now\)\s*\r?\n\s*client\.lastPong\.Store\(now\))',
        "`$1`r`n`tclient.lastHealthLog.Store(now)"
    )
}

$oldPongCase = 'case muxControlTypePong:\s*\r?\n\s*c\.lastPong\.Store\(time\.Now\(\)\.UnixNano\(\)\)'

$newPongCase = @'
case muxControlTypePong:
		now := time.Now()
		nowNano := now.UnixNano()

		sentNano := c.lastPingSent.Load()

		c.lastPong.Store(nowNano)

		lastLogNano := c.lastHealthLog.Load()
		if now.Sub(time.Unix(0, lastLogNano)) >= muxHealthLogInterval {
			if c.lastHealthLog.CompareAndSwap(lastLogNano, nowNano) {
				rttMs := int64(0)

				if sentNano > 0 && nowNano >= sentNano {
					rttMs = (nowNano - sentNano) / int64(time.Millisecond)
				}

				c.mux.log.Debug(
					"tinymux client health ok",
					"rtt_ms", rttMs,
				)
			}
		}
'@

if ($txt -notmatch "tinymux client health ok") {
    $matches = [regex]::Matches($txt, $oldPongCase)

    if ($matches.Count -ne 1) {
        throw "Expected exactly one muxControlTypePong case to patch, found $($matches.Count)"
    }

    $txt = [regex]::Replace($txt, $oldPongCase, $newPongCase, 1)
}

Set-Content -Encoding UTF8 -NoNewline -Path $Mux -Value $txt

gofmt -w $Mux

if ($LASTEXITCODE -ne 0) {
    throw "gofmt failed"
}

Write-Host "Patch applied successfully:"
git -C (Join-Path $Root "third_party\Turnable") diff -- pkg/connection/mux.go