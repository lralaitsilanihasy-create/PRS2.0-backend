# ⚠️ 2026-10-06 (DAO complet, demande front du 06/10 ; constats de recette C1-C3 du même jour) — assemble le DAO complet par
# Word (automation COM). Entrée : un manifeste JSON (UTF-8) { embleme, garde: [{ texte, taille, gras }], titreSommaire, entete,
# parties: [{ titres: [{ texte, niveau }], fichier }], docx, pdf }. Word crée le document, pose la page de garde (emblème puis
# lignes centrées) et le sommaire, insère chaque partie dans une nouvelle section, précédée de ses titres (styles « Partie DAO 1 »
# à « Partie DAO 4 », seuls lus par le sommaire ; une partie sans fichier n'a que ses titres), pose l'en-tête et le pied
# « page n / N » (numérotation continue), met à jour le sommaire et les champs, puis enregistre en .docx et en .pdf. Lancé dans
# son propre processus, avec un délai, par DaoCompletWord.
param([string]$manifeste)
$ErrorActionPreference = 'Stop'
$m = Get-Content -Raw -Encoding UTF8 $manifeste | ConvertFrom-Json
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
$word.AutomationSecurity = 3
$doc = $null
try {
    $doc = $word.Documents.Add()
    function Fin { $r = $doc.Content; $r.Collapse(0); return $r }

    # Les styles des titres du plan : seuls eux nourrissent le sommaire (les titres internes des parties n'y vont pas).
    $formes = @{ 1 = @(16, 1, 24); 2 = @(14, 1, 18); 3 = @(12, 0, 12); 4 = @(11, 0, 6) }   # taille, centré (1) ou à gauche (0), espace après
    foreach ($n in 1..4) {
        $s = $doc.Styles.Add("Partie DAO $n", 1)
        $s.Font.Size = $formes[$n][0]
        $s.Font.Bold = 1
        $s.ParagraphFormat.Alignment = $formes[$n][1]
        $s.ParagraphFormat.SpaceBefore = 6
        $s.ParagraphFormat.SpaceAfter = $formes[$n][2]
        $s.ParagraphFormat.OutlineLevel = $n
        $s.ParagraphFormat.KeepWithNext = -1
        $s.NextParagraphStyle = $doc.Styles.Item(-1)
    }

    # La page de garde : l'emblème, puis les lignes centrées.
    if ([string]$m.embleme -ne '') {
        $r = Fin
        $r.ParagraphFormat.Alignment = 1
        $img = $doc.InlineShapes.AddPicture([string]$m.embleme, $false, $true, $r)
        if ($img.Height -gt 90) { $img.LockAspectRatio = -1; $img.Height = 90 }
        $r = Fin
        $r.InsertParagraphAfter()
    }
    foreach ($l in $m.garde) {
        $r = Fin
        $r.InsertAfter([string]$l.texte)
        $r.ParagraphFormat.Alignment = 1
        $r.Font.Size = [int]$l.taille
        $r.Font.Bold = [int][bool]$l.gras
        $r.ParagraphFormat.SpaceAfter = 8
        $r.InsertParagraphAfter()
    }
    $r = Fin
    $r.InsertBreak(7)

    # Le sommaire.
    $r = Fin
    $r.InsertAfter([string]$m.titreSommaire)
    $r.ParagraphFormat.Alignment = 1
    $r.Font.Size = 14
    $r.Font.Bold = 1
    $r.InsertParagraphAfter()
    $r = Fin
    $sep = [string]$word.International(17)   # wdListSeparator : « ; » en français, « , » en anglais
    $styles = (1..4 | ForEach-Object { "Partie DAO $_" + $sep + $_ }) -join $sep
    $doc.Fields.Add($r, -1, ('TOC \t "' + $styles + '" \h \z'), $false) | Out-Null

    # Les parties, chacune dans sa section, précédée de ses titres.
    foreach ($p in $m.parties) {
        $r = Fin
        $r.InsertBreak(2)
        foreach ($t in $p.titres) {
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

    # En-tête et pied de page, communs à toutes les sections ; numérotation continue.
    foreach ($s in $doc.Sections) {
        $s.PageSetup.DifferentFirstPageHeaderFooter = 0
        $s.PageSetup.OddAndEvenPagesHeaderFooter = 0
        if ($s.Index -gt 1) {
            $s.Headers.Item(1).LinkToPrevious = $true
            $s.Footers.Item(1).LinkToPrevious = $true
        }
        $s.Footers.Item(1).PageNumbers.RestartNumberingAtSection = $false
    }
    $h = $doc.Sections.Item(1).Headers.Item(1).Range
    $h.Text = [string]$m.entete
    $h.ParagraphFormat.Alignment = 1
    $h.Font.Size = 9
    $f = $doc.Sections.Item(1).Footers.Item(1).Range
    $f.Text = 'page '
    $f.ParagraphFormat.Alignment = 1
    $f.Font.Size = 9
    $r = $doc.Sections.Item(1).Footers.Item(1).Range
    $r.MoveEnd(1, -1) | Out-Null
    $r.Collapse(0)
    $doc.Fields.Add($r, 33) | Out-Null
    $r = $doc.Sections.Item(1).Footers.Item(1).Range
    $r.MoveEnd(1, -1) | Out-Null
    $r.Collapse(0)
    $r.InsertAfter(' / ')
    $r.Collapse(0)
    $doc.Fields.Add($r, 26) | Out-Null

    $doc.Repaginate()
    $doc.TablesOfContents.Item(1).Update()
    $doc.Fields.Update() | Out-Null
    $doc.TablesOfContents.Item(1).UpdatePageNumbers()
    $doc.SaveAs2([string]$m.docx, 16)
    $doc.SaveAs2([string]$m.pdf, 17)
    'OK'
} finally {
    if ($doc -ne $null) { $doc.Close($false) }
    $word.Quit()
}
