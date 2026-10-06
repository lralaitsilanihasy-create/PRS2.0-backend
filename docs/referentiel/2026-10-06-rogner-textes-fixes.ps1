# Rogne un texte fixe de l'ARMP : supprime tout jusqu'au paragraphe d'ancre inclus (regex sensible à la casse),
# puis la table des matières propre au document et son titre, puis les paragraphes vides de tête.
param([string]$source, [string]$cible, [string]$ancre)
$word = New-Object -ComObject Word.Application
$word.Visible = $false; $word.DisplayAlerts = 0; $word.AutomationSecurity = 3
try {
    $d = $word.Documents.Open($source, $false, $false)
    if ($d.ProtectionType -ne -1) { $d.Close($false); $d = $word.Documents.Add(); $d.Range().InsertFile($source) }
    $fin = -1
    $i = 0
    foreach ($p in $d.Paragraphs) {
        $i++
        $t = ($p.Range.Text -replace "[\r\n\a\f\v]", " ").Trim()
        if ($t -cmatch $ancre) { $fin = $p.Range.End; break }
        if ($i -gt 400) { break }
    }
    if ($fin -lt 0) { throw "Ancre introuvable : $ancre" }
    $d.Range(0, $fin).Delete() | Out-Null
    while ($d.TablesOfContents.Count -gt 0) {
        $toc = $d.TablesOfContents.Item(1)
        $debut = $toc.Range.Start
        $avant = $d.Range(0, $debut).Paragraphs
        if ($avant.Count -gt 0) {
            # le titre « SOMMAIRE » / « TABLE DES MATIERES » qui précède, et les vides entre les deux
            for ($k = $avant.Count; $k -ge 1; $k--) {
                $pp = $avant.Item($k)
                $tt = ($pp.Range.Text -replace "[\r\n\a\f\v]", " ").Trim()
                if ($tt -eq '') { continue }
                if ($tt -match '^(SOMMAIRE|TABLE DES MATI)') { $debut = $pp.Range.Start }
                break
            }
        }
        $d.Range($debut, $toc.Range.End).Delete() | Out-Null
        $toc = $null
        if ($d.TablesOfContents.Count -gt 0 -and $d.TablesOfContents.Item(1).Range.Start -eq $debut) { $d.TablesOfContents.Item(1).Delete() }
    }
    # paragraphes vides de tête (sauts de page compris), hors tableau
    for ($n = 0; $n -lt 40; $n++) {
        $p = $d.Paragraphs.Item(1)
        if ($p.Range.Information(12)) { break }
        $t = ($p.Range.Text -replace "[\r\n\a\f\v\s]", "")
        if ($t -ne '' -or $d.Paragraphs.Count -lt 2) { break }
        $p.Range.Delete() | Out-Null
    }
    $d.SaveAs2($cible, 16)
    $d.Close($false)
    'OK'
} finally { $word.Quit() }
