# ⚠️ 2026-10-06 (DAO complet, demande front du 06/10) — assemble le DAO complet par Word (automation COM).
# Entrée : un manifeste JSON (UTF-8) { garde: [{ texte, taille, gras }], titreSommaire, entete, parties: [{ titre, fichier }],
# docx, pdf }. Word crée le document, pose la page de garde et le sommaire, insère chaque partie dans une nouvelle section (titre
# au style « Titre 1 », pour le sommaire), pose l'en-tête et le pied « page n / N » (numérotation continue), met à jour le sommaire
# et les champs, puis enregistre en .docx et en .pdf. Lancé dans son propre processus, avec un délai, par DaoCompletWord.
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

    # Le style des titres de partie : seul lui nourrit le sommaire (les titres internes des parties n'y vont pas).
    $style = $doc.Styles.Add('Partie DAO', 1)
    $style.Font.Size = 14
    $style.Font.Bold = 1
    $style.ParagraphFormat.Alignment = 1
    $style.ParagraphFormat.SpaceAfter = 18
    $style.ParagraphFormat.OutlineLevel = 1
    $style.NextParagraphStyle = $doc.Styles.Item(-1)

    # La page de garde.
    foreach ($l in $m.garde) {
        $r = Fin
        $r.InsertAfter([string]$l.texte)
        $r.ParagraphFormat.Alignment = 1
        $r.Font.Size = [int]$l.taille
        $r.Font.Bold = [int][bool]$l.gras
        $r.ParagraphFormat.SpaceAfter = 12
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
    $separateur = [string]$word.International(17)   # wdListSeparator : « ; » en français, « , » en anglais
    $doc.Fields.Add($r, -1, ('TOC \t "Partie DAO' + $separateur + '1" \h \z'), $false) | Out-Null

    # Les parties, chacune dans sa section.
    foreach ($p in $m.parties) {
        $r = Fin
        $r.InsertBreak(2)
        if ([string]$p.titre -ne '') {
            $r = Fin
            $r.InsertAfter([string]$p.titre)
            $r.Style = 'Partie DAO'
            $r.InsertParagraphAfter()
        }
        $r = Fin
        $r.Style = -1
        $r.InsertFile([string]$p.fichier)
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
