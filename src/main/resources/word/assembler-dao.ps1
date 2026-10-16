# ⚠️ 2026-10-06 (DAO complet, demande front du 06/10 ; constats de recette C1-C3 du même jour) — assemble le DAO complet par
# Word (automation COM). Entrée : un manifeste JSON (UTF-8) { embleme, garde: [{ texte, taille, gras, gauche }], separateur: [...],
# titreSommaire, enteteGauche, enteteDroite, piedGauche, piedCentre, parties: [{ titres: [{ texte, niveau }], fichier, separateur,
# partie }], docx, pdf }. Word crée le document, pose la page de garde (emblème puis lignes) et le sommaire, insère chaque partie
# dans une nouvelle section, précédée de ses titres (styles « Partie DAO 1 » à « Partie DAO 4 », seuls lus par le sommaire ; une
# partie sans fichier n'a que ses titres), pose l'en-tête et le pied « page n / N » (numérotation continue), met à jour le sommaire
# et les champs, puis enregistre en .docx et en .pdf. Lancé dans son propre processus, avec un délai, par DaoCompletWord.
#
# ⚠️ 2026-10-16 (demande front « charte-dao », §B1-B2, §B4) — la charte : Arial 10,5 pt justifié, interligne 1,3, 1,8 mm après ;
# marges 22 / 20 / 20 / 24 mm ; ni césure ni paragraphe coupé ; titres du plan en Arial ; une page de séparation devant chaque
# section du sommaire (bloc de l'autorité, n°, « Lancé le », objet, partie et section), sans en-tête ni pied, comme la garde ;
# en-tête courant « DAO n° … » à gauche, objet à droite, filet bas ; pied « autorité — PRMP », « Fiche v… validée le … »,
# « page n / N », filet haut. Les parties arrivent déjà chartées (CharteDao) ; leurs styles homonymes prennent ceux d'ici.
param([string]$manifeste)
$ErrorActionPreference = 'Stop'
$m = Get-Content -Raw -Encoding UTF8 $manifeste | ConvertFrom-Json
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
$word.AutomationSecurity = 3
$doc = $null
$police = 'Arial'
$mm = 2.8346457   # points par millimètre
try {
    $doc = $word.Documents.Add()
    $doc.AutoHyphenation = $false
    function Fin { $r = $doc.Content; $r.Collapse(0); return $r }
    function Marges($s) {
        $s.PageSetup.TopMargin = [single](22 * $mm)
        $s.PageSetup.RightMargin = [single](20 * $mm)
        $s.PageSetup.BottomMargin = [single](20 * $mm)
        $s.PageSetup.LeftMargin = [single](24 * $mm)
        $s.PageSetup.HeaderDistance = [single](10 * $mm)
        $s.PageSetup.FooterDistance = [single](9 * $mm)
    }
    Marges $doc.Sections.Item(1)
    $largeur = $doc.Sections.Item(1).PageSetup.PageWidth - 44 * $mm   # la largeur du texte

    # Le corps : le style Normal, que prennent aussi les styles homonymes des parties insérées.
    $normal = $doc.Styles.Item(-1)
    $normal.Font.Name = $police
    $normal.Font.Size = 10.5
    $normal.ParagraphFormat.Alignment = 3
    $normal.ParagraphFormat.SpaceBefore = 0
    $normal.ParagraphFormat.SpaceAfter = [single](1.8 * $mm)
    $normal.ParagraphFormat.LineSpacingRule = 5   # multiple
    $normal.ParagraphFormat.LineSpacing = [single]$word.LinesToPoints(1.3)
    $normal.ParagraphFormat.KeepTogether = -1
    $normal.ParagraphFormat.Hyphenation = 0

    # Les styles intégrés, que prennent les styles homonymes des textes fixes (IC, CCAG) : Arial, noir ; titre 1 = article (11 pt,
    # gras, capitales, filet bas), titre 2 = sous-article (10,5 pt, gras, souligné), les suivants 10,5 pt gras.
    foreach ($s in $doc.Styles) {
        try {
            if ($s.Type -eq 1 -or $s.Type -eq 2) { $s.Font.Name = $police; $s.Font.Color = -16777216 }
        } catch { }
    }
    foreach ($k in 1..9) {
        $s = $doc.Styles.Item(-1 - $k)   # wdStyleHeading1 = -2 … wdStyleHeading9 = -10
        $s.Font.Name = $police
        $s.Font.Color = -16777216
        $s.Font.Bold = 1
        $s.Font.Italic = 0
        $s.Font.Size = [single]$(if ($k -eq 1) { 11 } else { 10.5 })
        $s.Font.AllCaps = [int]($k -eq 1)
        $s.Font.Underline = [int]($k -eq 2)
        $s.ParagraphFormat.Alignment = 0
        $s.ParagraphFormat.SpaceBefore = [single]$(if ($k -eq 1) { 10 } else { 6 })
        $s.ParagraphFormat.SpaceAfter = [single]$(if ($k -eq 1) { 5 } else { 3 })
        $s.ParagraphFormat.LineSpacingRule = 0
        $s.ParagraphFormat.KeepWithNext = -1
        if ($k -eq 1) {
            $b = $s.ParagraphFormat.Borders.Item(-3)
            $b.LineStyle = 1
            $b.LineWidth = 4
        }
    }

    # Les styles des titres du plan : seuls eux nourrissent le sommaire (les titres internes des parties n'y vont pas).
    $formes = @{ 1 = @(13, 1, 12, 1); 2 = @(12, 1, 12, 0); 3 = @(11, 0, 8, 0); 4 = @(10.5, 0, 6, 0) }   # taille, centré, espace après, capitales
    foreach ($n in 1..4) {
        $s = $doc.Styles.Add("Partie DAO $n", 1)
        $s.Font.Name = $police
        $s.Font.Size = [single]$formes[$n][0]
        $s.Font.Bold = 1
        $s.Font.AllCaps = [int]$formes[$n][3]
        $s.ParagraphFormat.Alignment = [int]$formes[$n][1]
        $s.ParagraphFormat.SpaceBefore = 6
        $s.ParagraphFormat.SpaceAfter = [single]$formes[$n][2]
        $s.ParagraphFormat.LineSpacingRule = 0
        $s.ParagraphFormat.OutlineLevel = $n
        $s.ParagraphFormat.KeepWithNext = -1
        $s.NextParagraphStyle = $doc.Styles.Item(-1)
    }

    # Une ligne de la garde ou d'une page de séparation : centrée (ou à gauche), sans retrait.
    function Ligne($l) {
        $r = Fin
        $r.InsertAfter([string]$l.texte)
        $r.Style = -1
        $r.ParagraphFormat.Alignment = $(if ([bool]$l.gauche) { 0 } else { 1 })
        $r.ParagraphFormat.SpaceBefore = $(if ([int]$l.taille -ge 15) { 40 } elseif ([bool]$l.gauche -and [string]$l.texte -like 'Objet*') { 30 } else { 0 })
        $r.ParagraphFormat.SpaceAfter = $(if ([int]$l.taille -le 10) { 0 } else { 6 })
        $r.ParagraphFormat.LineSpacingRule = 0
        $r.Font.Size = [single]$l.taille
        $r.Font.Bold = [int][bool]$l.gras
        $r.InsertParagraphAfter()
    }

    # La page de garde : l'emblème national, puis les lignes.
    if ([string]$m.embleme -ne '') {
        $r = Fin
        $r.ParagraphFormat.Alignment = 1
        $img = $doc.InlineShapes.AddPicture([string]$m.embleme, $false, $true, $r)
        if ($img.Height -gt 85) { $img.LockAspectRatio = -1; $img.Height = 85 }
        $r = Fin
        $r.InsertParagraphAfter()
    }
    foreach ($l in $m.garde) { Ligne $l }
    $r = Fin
    $r.InsertBreak(7)

    # Le sommaire.
    $r = Fin
    $r.InsertAfter([string]$m.titreSommaire)
    $r.ParagraphFormat.Alignment = 1
    $r.Font.Size = 13
    $r.Font.Bold = 1
    $r.InsertParagraphAfter()
    $r = Fin
    $sep = [string]$word.International(17)   # wdListSeparator : « ; » en français, « , » en anglais
    $styles = (1..4 | ForEach-Object { "Partie DAO $_" + $sep + $_ }) -join $sep
    $doc.Fields.Add($r, -1, ('TOC \t "' + $styles + '" \h \z'), $false) | Out-Null

    # Les parties, chacune dans sa section, précédée de ses titres ; une section du sommaire s'ouvre sur sa page de séparation.
    $separees = @(1)
    foreach ($p in $m.parties) {
        $r = Fin
        $r.InsertBreak(2)
        $titres = @($p.titres)
        if ([bool]$p.separateur) {
            $separees += $doc.Sections.Count
            foreach ($l in $m.separateur) { Ligne $l }
            if ([string]$p.partie -ne '') {
                $r = Fin
                $r.InsertAfter([string]$p.partie)
                $r.Style = -1
                $r.ParagraphFormat.Alignment = 1
                $r.ParagraphFormat.SpaceBefore = 36
                $r.Font.Size = 13
                $r.Font.Bold = 1
                $r.Font.AllCaps = 1
                $r.InsertParagraphAfter()
            }
            $premier = $true
            foreach ($t in ($titres | Where-Object { [int]$_.niveau -le 2 })) {
                $r = Fin
                $r.InsertAfter([string]$t.texte)
                $r.Style = "Partie DAO " + [int]$t.niveau
                if ($premier -and [string]$p.partie -eq '') { $r.ParagraphFormat.SpaceBefore = 36 }
                $premier = $false
                $r.InsertParagraphAfter()
            }
            $r = Fin
            $r.Style = -1
            $r.InsertBreak(7)
            $titres = @($titres | Where-Object { [int]$_.niveau -gt 2 })
        }
        foreach ($t in $titres) {
            $r = Fin
            $r.InsertAfter([string]$t.texte)
            $r.Style = "Partie DAO " + [int]$t.niveau
            $r.InsertParagraphAfter()
        }
        if ([string]$p.fichier -ne '') {
            $r = Fin
            $r.Style = -1
            $r.InsertFile([string]$p.fichier)
        }
    }

    # Marges, première page sans en-tête ni pied (garde, séparateurs), en-tête et pied communs ; numérotation continue.
    foreach ($s in $doc.Sections) {
        Marges $s
        $s.PageSetup.OddAndEvenPagesHeaderFooter = 0
        $s.PageSetup.DifferentFirstPageHeaderFooter = $(if ($separees -contains $s.Index) { -1 } else { 0 })
        if ($s.Index -gt 1) {
            $s.Headers.Item(1).LinkToPrevious = $true
            $s.Footers.Item(1).LinkToPrevious = $true
        }
        if ($separees -contains $s.Index) {
            $s.Headers.Item(2).LinkToPrevious = $false
            $s.Footers.Item(2).LinkToPrevious = $false
            $s.Headers.Item(2).Range.Text = ''
            $s.Footers.Item(2).Range.Text = ''
        }
        $s.Footers.Item(1).PageNumbers.RestartNumberingAtSection = $false
    }
    # ⚠️ R2 (recette du 16/10) — en-tête et pied en tableaux sans filets intérieurs : deux zones (« DAO n° … », objet) et trois
    # (autorité, version, page). Une autorité au nom long ne chevauche plus la zone suivante, comme avec des tabulations.
    function Zones($range, $textes, $largeurs, $bord) {
        $range.Text = ''
        $t = $range.Tables.Add($range, 1, $textes.Count)
        $t.Borders.Enable = 0
        $t.TopPadding = 0
        $t.BottomPadding = 0
        $t.LeftPadding = 0
        $t.RightPadding = 0
        $t.Range.Font.Name = $police
        $t.Range.Font.Size = 8.5
        $t.Range.ParagraphFormat.SpaceAfter = 0
        $t.Range.ParagraphFormat.SpaceBefore = 0
        $t.Range.ParagraphFormat.LineSpacingRule = 0
        for ($i = 1; $i -le $textes.Count; $i++) {
            $c = $t.Cell(1, $i)
            $c.Width = [single]$largeurs[$i - 1]
            $c.Range.Text = [string]$textes[$i - 1]
            $c.Range.ParagraphFormat.Alignment = $(if ($i -eq 1) { 0 } elseif ($i -eq $textes.Count) { 2 } else { 1 })
        }
        $b = $t.Borders.Item($bord)
        $b.LineStyle = 1
        $b.LineWidth = 4
        return $t
    }
    Zones $doc.Sections.Item(1).Headers.Item(1).Range @([string]$m.enteteGauche, [string]$m.enteteDroite) @(($largeur * 0.4), ($largeur * 0.6)) -3 | Out-Null
    $t = Zones $doc.Sections.Item(1).Footers.Item(1).Range @([string]$m.piedGauche, [string]$m.piedCentre, 'page ') @(($largeur * 0.42), ($largeur * 0.36), ($largeur * 0.22)) -1
    $r = $t.Cell(1, 3).Range
    $r.MoveEnd(1, -1) | Out-Null
    $r.Collapse(0)
    $doc.Fields.Add($r, 33) | Out-Null
    $r = $t.Cell(1, 3).Range
    $r.MoveEnd(1, -1) | Out-Null
    $r.Collapse(0)
    $r.InsertAfter(' / ')
    $r.Collapse(0)
    $doc.Fields.Add($r, 26) | Out-Null
    # Le paragraphe qui suit un tableau d'en-tête ou de pied, imposé par Word : le plus petit possible.
    foreach ($z in @($doc.Sections.Item(1).Headers.Item(1).Range, $doc.Sections.Item(1).Footers.Item(1).Range)) {
        $p = $z.Paragraphs.Last.Range
        $p.Font.Size = 1
        $p.ParagraphFormat.SpaceAfter = 0
        $p.ParagraphFormat.SpaceBefore = 0
    }

    # ⚠️ D2 (contre-recette du 06/10) — un même style pour les quatre niveaux du sommaire : les textes fixes insérés redéfinissent
    # TM 1 à TM 3, TM 4 gardait le défaut de Word. Posé après les insertions ; ⚠️ charte (16/10) : Arial.
    foreach ($n in 1..4) {
        $s = $doc.Styles.Item(-19 - $n)   # wdStyleTOC1 = -20 … wdStyleTOC4 = -23
        $s.Font.Name = $police
        $s.Font.Size = 10
        $s.Font.Bold = [int]($n -le 2)
        $s.Font.AllCaps = 0
        $s.ParagraphFormat.SpaceBefore = $(if ($n -eq 1) { 6 } else { 0 })
        $s.ParagraphFormat.SpaceAfter = 0
        $s.ParagraphFormat.LineSpacingRule = 0   # simple
        $s.ParagraphFormat.LeftIndent = 12 * ($n - 1)
    }

    $doc.Repaginate()
    $doc.TablesOfContents.Item(1).Update()
    $doc.Fields.Update() | Out-Null
    $doc.TablesOfContents.Item(1).UpdatePageNumbers()
    $doc.SaveAs2([string]$m.docx, 16)
    $doc.SaveAs2([string]$m.pdf, 17)
    'OK'
} catch {
    # La ligne du script en cause, dans le message rendu à DaoCompletWord.
    throw ("ligne " + $_.InvocationInfo.ScriptLineNumber + " : " + $_.Exception.Message)
} finally {
    if ($doc -ne $null) { $doc.Close($false) }
    $word.Quit()
}
