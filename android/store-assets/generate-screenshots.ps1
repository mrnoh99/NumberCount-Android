Add-Type -AssemblyName System.Drawing

function New-Color([int]$r, [int]$g, [int]$b, [int]$a = 255) {
    return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

function Get-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = [Math]::Min($r * 2, [Math]::Min($w, $h))
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-RoundedFill($g, $brush, $x, $y, $w, $h, $r) {
    $path = Get-RoundedPath $x $y $w $h $r
    $g.FillPath($brush, $path)
    $path.Dispose()
}

function Draw-RoundedBorder($g, $pen, $x, $y, $w, $h, $r) {
    $path = Get-RoundedPath $x $y $w $h $r
    $g.DrawPath($pen, $path)
    $path.Dispose()
}

function New-Font($family, $size, $style = [System.Drawing.FontStyle]::Regular) {
    $sz = [single]$size
    foreach ($fam in @($family, "Malgun Gothic", "Segoe UI", "Arial")) {
        try {
            return New-Object System.Drawing.Font $fam, $sz, $style
        } catch {}
    }
    return New-Object System.Drawing.Font "Arial", $sz, $style
}

function Measure-Text($g, $text, $font) {
    return $g.MeasureString($text, $font)
}

function Draw-CenteredText($g, $text, $font, $brush, $cx, $cy) {
    $size = Measure-Text $g $text $font
    $g.DrawString($text, $font, $brush, ($cx - $size.Width / 2), ($cy - $size.Height / 2))
}

function Draw-SegmentToggle($g, $x, $y, $w, $h, $labels, $selectedIndex, $scale) {
    $track = New-Object System.Drawing.SolidBrush (New-Color 236 236 236)
    $orange = New-Object System.Drawing.SolidBrush (New-Color 224 134 0)
    $white = New-Object System.Drawing.SolidBrush (New-Color 255 255 255)
    $inactive = New-Object System.Drawing.SolidBrush (New-Color 120 120 120)
    $font = New-Font "Malgun Gothic" ([int](15 * $scale)) ([System.Drawing.FontStyle]::Bold)
    Draw-RoundedFill $g $track $x $y $w $h ([int](18 * $scale))
    $segW = $w / $labels.Count
    for ($i = 0; $i -lt $labels.Count; $i++) {
        $sx = $x + $segW * $i
        if ($i -eq $selectedIndex) {
            Draw-RoundedFill $g $orange ($sx + 2) ($y + 2) ($segW - 4) ($h - 4) ([int](16 * $scale))
            Draw-CenteredText $g $labels[$i] $font $white ($sx + $segW / 2) ($y + $h / 2)
        } else {
            Draw-CenteredText $g $labels[$i] $font $inactive ($sx + $segW / 2) ($y + $h / 2)
        }
    }
    $track.Dispose(); $orange.Dispose(); $white.Dispose(); $inactive.Dispose(); $font.Dispose()
}

function Draw-EmojiGrid($g, $emoji, $count, $x, $y, $w, $h, $scale, [int]$highlight = -1) {
    $cols = if ($count -le 3) { $count } elseif ($count -le 6) { 3 } else { 4 }
    $rows = [Math]::Ceiling($count / $cols)
    $fontSize = [int]([Math]::Min(72, [Math]::Max(28, (36 * $scale) * (1.0 - ($count - 4) * 0.04))))
    $font = New-Font "Segoe UI Emoji" $fontSize
    $brush = New-Object System.Drawing.SolidBrush (New-Color 30 30 30)
    $dim = New-Object System.Drawing.SolidBrush (New-Color 170 170 170)
    $cellW = $w / $cols
    $cellH = $h / $rows
    $idx = 0
    for ($r = 0; $r -lt $rows; $r++) {
        $itemsInRow = [Math]::Min($cols, $count - $r * $cols)
        $rowW = $cellW * $itemsInRow
        $startX = $x + ($w - $rowW) / 2
        for ($c = 0; $c -lt $itemsInRow; $c++) {
            $cx = $startX + $cellW * $c + $cellW / 2
            $cy = $y + $cellH * $r + $cellH / 2
            $b = if ($idx -lt $highlight) { $dim } else { $brush }
            Draw-CenteredText $g $emoji $font $b $cx $cy
            $idx++
        }
    }
    $font.Dispose(); $brush.Dispose(); $dim.Dispose()
}

function Draw-GameScreen($g, $W, $H, $scale, $mode, $target, $emoji, $options, $borderArgb, $score, $modeSelected) {
    $bg = New-Object System.Drawing.SolidBrush (New-Color 255 247 232)
    $g.FillRectangle($bg, 0, 0, $W, $H)
    $bg.Dispose()

    $padH = [int](34 * $scale)
    $topY = [int](80 * $scale)
    $toggleH = [int](44 * $scale)
    $diffW = [int](130 * $scale)
    $modeW = [int](360 * $scale)
    Draw-SegmentToggle $g $padH $topY $diffW $toggleH @("1-5", "1-10") 1 $scale
    Draw-SegmentToggle $g ($W / 2 - $modeW / 2) ($topY + $toggleH + [int](12 * $scale)) $modeW $toggleH @("갯수 - 숫자", "숫자 - 갯수") $modeSelected $scale

    $starY = $topY + $toggleH * 2 + [int](28 * $scale)
    $starFont = New-Font "Segoe UI" ([int](22 * $scale))
    $gold = New-Object System.Drawing.SolidBrush (New-Color 255 204 0)
    $starChar = [string][char]0x2605
    for ($i = 0; $i -lt [Math]::Min($score, 9); $i++) {
        $g.DrawString($starChar, $starFont, $gold, ($W / 2 - 60 * $scale + $i * 28 * $scale), $starY)
    }
    $starFont.Dispose(); $gold.Dispose()

    $gearSize = [int](36 * $scale)
    $gearX = $W - $padH - $gearSize - [int](8 * $scale)
    $gearBrush = New-Object System.Drawing.SolidBrush (New-Color 224 134 0 30)
    Draw-RoundedFill $g $gearBrush ($gearX - 6) ($topY - 4) ($gearSize + 12) ($gearSize + 12) ([int](20 * $scale))
    $gearFont = New-Font "Segoe UI Symbol" ([int](22 * $scale))
    $orange = New-Object System.Drawing.SolidBrush (New-Color 224 134 0)
    Draw-CenteredText $g ([string][char]0x2699) $gearFont $orange ($gearX + $gearSize / 2) ($topY + $gearSize / 2)
    $gearBrush.Dispose(); $gearFont.Dispose(); $orange.Dispose()

    $panelSize = [int]([Math]::Min($W * 0.72, $H * 0.34))
    $panelX = ($W - $panelSize) / 2
    $panelY = $starY + [int](70 * $scale)
    $white = New-Object System.Drawing.SolidBrush (New-Color 255 255 255)
    $borderPen = New-Object System.Drawing.Pen (New-Color $borderArgb[0] $borderArgb[1] $borderArgb[2]), ([int](6 * $scale))
    Draw-RoundedFill $g $white $panelX $panelY $panelSize $panelSize ([int](28 * $scale))
    Draw-RoundedBorder $g $borderPen $panelX $panelY $panelSize $panelSize ([int](28 * $scale))

    if ($mode -eq "number") {
        $numFont = New-Font "Malgun Gothic" ([int](120 * $scale)) ([System.Drawing.FontStyle]::Bold)
        $numBrush = New-Object System.Drawing.SolidBrush (New-Color $borderArgb[0] $borderArgb[1] $borderArgb[2])
        Draw-CenteredText $g ([string]$target) $numFont $numBrush ($W / 2) ($panelY + $panelSize / 2)
        $numFont.Dispose(); $numBrush.Dispose()
    } else {
        Draw-EmojiGrid $g $emoji $target ($panelX + 20) ($panelY + 20) ($panelSize - 40) ($panelSize - 40) ($scale * 1.4)
    }
    $white.Dispose(); $borderPen.Dispose()

    $optH = [int](110 * $scale)
    $gap = [int](14 * $scale)
    $gridW = [int]($W * 0.88)
    $gridX = ($W - $gridW) / 2
    $gridY = $panelY + $panelSize + [int](36 * $scale)
    $cellW = ($gridW - $gap) / 2
    $colors = @(
        @(255, 149, 0), @(48, 176, 199), @(175, 82, 222), @(255, 59, 48)
    )
    for ($i = 0; $i -lt 4; $i++) {
        $row = [Math]::Floor($i / 2)
        $col = $i % 2
        $cx = $gridX + $col * ($cellW + $gap)
        $cy = $gridY + $row * ($optH + $gap)
        $c = $colors[$i]
        $fill = New-Object System.Drawing.SolidBrush (New-Color 255 255 255)
        $pen = New-Object System.Drawing.Pen (New-Color $c[0] $c[1] $c[2]), ([int](5 * $scale))
        Draw-RoundedFill $g $fill $cx $cy $cellW $optH ([int](22 * $scale))
        Draw-RoundedBorder $g $pen $cx $cy $cellW $optH ([int](22 * $scale))
        if ($mode -eq "number") {
            Draw-EmojiGrid $g $emoji $options[$i] ($cx + 8) ($cy + 8) ($cellW - 16) ($optH - 16) ($scale * 0.95)
        } else {
            $numFont = New-Font "Malgun Gothic" ([int](52 * $scale)) ([System.Drawing.FontStyle]::Bold)
            $numBrush = New-Object System.Drawing.SolidBrush (New-Color $c[0] $c[1] $c[2])
            Draw-CenteredText $g ([string]$options[$i]) $numFont $numBrush ($cx + $cellW / 2) ($cy + $optH / 2)
            $numFont.Dispose(); $numBrush.Dispose()
        }
        $fill.Dispose(); $pen.Dispose()
    }
}

function Draw-SettingsScreen($g, $W, $H, $scale) {
    $bg = New-Object System.Drawing.SolidBrush (New-Color 255 246 230)
    $g.FillRectangle($bg, 0, 0, $W, $H)
    $bg.Dispose()

    $accent = New-Object System.Drawing.SolidBrush (New-Color 224 134 0)
    $titleBrush = New-Object System.Drawing.SolidBrush (New-Color 26 26 26)
    $bodyBrush = New-Object System.Drawing.SolidBrush (New-Color 66 66 66)
    $pad = [int](24 * $scale)
    $titleFont = New-Font "Malgun Gothic" ([int](26 * $scale)) ([System.Drawing.FontStyle]::Bold)
    $sectionFont = New-Font "Malgun Gothic" ([int](19 * $scale)) ([System.Drawing.FontStyle]::Bold)
    $bodyFont = New-Font "Malgun Gothic" ([int](17 * $scale))
    $smallFont = New-Font "Malgun Gothic" ([int](15 * $scale))

    $y = [int](70 * $scale)
    $g.DrawString("<  완료", $bodyFont, $accent, $pad, $y)
    Draw-CenteredText $g "설정" $sectionFont $accent ($W / 2) ($y + 10)

    $y += [int](70 * $scale)
    $g.DrawString("숫자세기", $titleFont, $titleBrush, $pad, $y)
    $y += [int](42 * $scale)
    $g.DrawString("퀴즈 화면과 같은 느낌으로 옵션을 바꿀 수 있어요.", $bodyFont, $bodyBrush, $pad, $y)

    $y += [int](50 * $scale)
    $g.DrawString("언어", $sectionFont, $accent, $pad, $y)
    $y += [int](38 * $scale)
    Draw-ChoiceBtn $g "한국어" $true $pad $y ([int](100 * $scale)) ([int](44 * $scale)) $scale
    Draw-ChoiceBtn $g "English" $false ($pad + [int](110 * $scale)) $y ([int](100 * $scale)) ([int](44 * $scale)) $scale

    $y += [int](70 * $scale)
    $g.DrawString("아이템 종류", $sectionFont, $accent, $pad, $y)
    $y += [int](38 * $scale)
    foreach ($item in @(@("과일", $true), @("자동차", $true), @("채소", $true))) {
        $g.DrawString($item[0], $bodyFont, $titleBrush, $pad, $y)
        Draw-ChoiceBtn $g "ON" $item[1] ($W - $pad - [int](90 * $scale)) ($y - 4) ([int](80 * $scale)) ([int](36 * $scale)) $scale
        $y += [int](48 * $scale)
    }

    $y += [int](20 * $scale)
    $g.DrawString("배경음", $sectionFont, $accent, $pad, $y)
    $y += [int](38 * $scale)
    $g.DrawString("배경음 켜기", $bodyFont, $titleBrush, $pad, $y)
    Draw-Toggle $g ($W - $pad - [int](70 * $scale)) ($y - 2) ([int](60 * $scale)) ([int](32 * $scale)) $true $scale

    $y += [int](60 * $scale)
    $g.DrawString("맞춤 음성 녹음", $sectionFont, $accent, $pad, $y)
    $y += [int](34 * $scale)
    $g.DrawString("버튼을 누르고 있는 동안 녹음해요.", $smallFont, $bodyBrush, $pad, $y)
    $y += [int](40 * $scale)
    Draw-RecordCard $g $pad $y ($W - $pad * 2) ([int](130 * $scale)) "정답 (한국어)" $scale
    $y += [int](150 * $scale)
    Draw-RecordCard $g $pad $y ($W - $pad * 2) ([int](130 * $scale)) "오답 (한국어)" $scale

    $titleFont.Dispose(); $sectionFont.Dispose(); $bodyFont.Dispose(); $smallFont.Dispose()
    $accent.Dispose(); $titleBrush.Dispose(); $bodyBrush.Dispose()
}

function Draw-ChoiceBtn($g, $text, $selected, $x, $y, $w, $h, $scale) {
    $orange = New-Object System.Drawing.SolidBrush (New-Color 224 134 0)
    $track = New-Object System.Drawing.SolidBrush (New-Color 236 236 236)
    $white = New-Object System.Drawing.SolidBrush (New-Color 255 255 255)
    $gray = New-Object System.Drawing.SolidBrush (New-Color 100 100 100)
    $font = New-Font "Malgun Gothic" ([int](15 * $scale)) ([System.Drawing.FontStyle]::Bold)
    if ($selected) { Draw-RoundedFill $g $orange $x $y $w $h ([int](14 * $scale)); Draw-CenteredText $g $text $font $white ($x + $w / 2) ($y + $h / 2) }
    else { Draw-RoundedFill $g $track $x $y $w $h ([int](14 * $scale)); Draw-CenteredText $g $text $font $gray ($x + $w / 2) ($y + $h / 2) }
    $orange.Dispose(); $track.Dispose(); $white.Dispose(); $gray.Dispose(); $font.Dispose()
}

function Draw-Toggle($g, $x, $y, $w, $h, $on, $scale) {
    $brush = New-Object System.Drawing.SolidBrush (New-Color 224 134 0)
    Draw-RoundedFill $g $brush $x $y $w $h ([int](16 * $scale))
    $knob = New-Object System.Drawing.SolidBrush (New-Color 255 255 255)
    $kx = if ($on) { $x + $w - $h + 4 } else { $x + 4 }
    Draw-RoundedFill $g $knob $kx ($y + 4) ($h - 8) ($h - 8) ([int](14 * $scale))
    $brush.Dispose(); $knob.Dispose()
}

function Draw-RecordCard($g, $x, $y, $w, $h, $title, $scale) {
    $card = New-Object System.Drawing.SolidBrush (New-Color 255 255 255)
    Draw-RoundedFill $g $card $x $y $w $h ([int](18 * $scale))
    $titleFont = New-Font "Malgun Gothic" ([int](16 * $scale)) ([System.Drawing.FontStyle]::Bold)
    $hintFont = New-Font "Malgun Gothic" ([int](13 * $scale))
    $tb = New-Object System.Drawing.SolidBrush (New-Color 26 26 26)
    $hb = New-Object System.Drawing.SolidBrush (New-Color 120 120 120)
    $g.DrawString($title, $titleFont, $tb, ($x + 16), ($y + 14))
    $g.DrawString("누르고 있는 동안 녹음", $hintFont, $hb, ($x + 16), ($y + 40))
    Draw-ChoiceBtn $g "녹음" $false ($x + 16) ($y + 72) ([int](100 * $scale)) ([int](40 * $scale)) $scale
    Draw-ChoiceBtn $g "재생" $false ($x + [int](130 * $scale)) ($y + 72) ([int](100 * $scale)) ([int](40 * $scale)) $scale
    $card.Dispose(); $titleFont.Dispose(); $hintFont.Dispose(); $tb.Dispose(); $hb.Dispose()
}

function Draw-CelebrationScreen($g, $W, $H, $scale, $number, $comment) {
    Draw-GameScreen $g $W $H $scale "number" $number "🍎" @(3, 5, 7, 9) @(255, 149, 0) 6 1
    $overlay = New-Object System.Drawing.SolidBrush (New-Color 0 0 0 136)
    $g.FillRectangle($overlay, 0, 0, $W, $H)
    $overlay.Dispose()

    $box = [int](280 * $scale)
    $bx = ($W - $box) / 2
    $by = ($H - $box) / 2
    $white = New-Object System.Drawing.SolidBrush (New-Color 255 255 255)
    Draw-RoundedFill $g $white $bx $by $box $box ([int](24 * $scale))
    $starFont = New-Font "Segoe UI Emoji" ([int](48 * $scale))
    $numFont = New-Font "Malgun Gothic" ([int](72 * $scale)) ([System.Drawing.FontStyle]::Bold)
    $cmtFont = New-Font "Malgun Gothic" ([int](34 * $scale)) ([System.Drawing.FontStyle]::Bold)
    $orange = New-Object System.Drawing.SolidBrush (New-Color 255 149 0)
    $green = New-Object System.Drawing.SolidBrush (New-Color 34 197 94)
    Draw-CenteredText $g ([string][char]0x2B50) $starFont $orange ($W / 2) ($by + 70 * $scale)
    Draw-CenteredText $g ([string]$number) $numFont $orange ($W / 2) ($by + 140 * $scale)
    Draw-CenteredText $g $comment $cmtFont $green ($W / 2) ($by + 210 * $scale)
    $white.Dispose(); $starFont.Dispose(); $numFont.Dispose(); $cmtFont.Dispose(); $orange.Dispose(); $green.Dispose()
}

function Save-Screen($path, $W, $H, $drawFn) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp = New-Object System.Drawing.Bitmap $W, $H, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    & $drawFn $g
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output "Created $path (${W}x${H})"
}

$assets = $PSScriptRoot

$devices = @(
    @{ Name = "phone"; W = 1080; H = 1920; Scale = 1.0 },
    @{ Name = "tablet-7"; W = 1200; H = 1920; Scale = 1.12 },
    @{ Name = "tablet-10"; W = 1600; H = 2560; Scale = 1.45 }
)

$scenes = @(
    @{
        File = "01-number-to-count.png"
        Draw = {
            param($g, $dev)
            Draw-GameScreen $g $dev.W $dev.H $dev.Scale "number" 5 "🍎" @(2, 5, 7, 9) @(255, 149, 0) 4 1
        }
    },
    @{
        File = "02-count-to-number.png"
        Draw = {
            param($g, $dev)
            Draw-GameScreen $g $dev.W $dev.H $dev.Scale "objects" 7 "🍌" @(5, 7, 9, 3) @(255, 167, 38) 5 0
        }
    },
    @{
        File = "03-settings.png"
        Draw = {
            param($g, $dev)
            Draw-SettingsScreen $g $dev.W $dev.H $dev.Scale
        }
    },
    @{
        File = "04-celebration.png"
        Draw = {
            param($g, $dev)
            Draw-CelebrationScreen $g $dev.W $dev.H $dev.Scale 5 "잘했어요!"
        }
    }
)

foreach ($dev in $devices) {
    $outDir = Join-Path $assets "screenshots\$($dev.Name)"
    foreach ($scene in $scenes) {
        $path = Join-Path $outDir $scene.File
        Save-Screen $path $dev.W $dev.H {
            param($g)
            & $scene.Draw $g $dev
        }
    }
}

Write-Output "Done. Upload folders under android/store-assets/screenshots/"
