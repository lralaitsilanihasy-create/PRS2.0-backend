package cnm.prs.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ 2026-10-06 (DAO complet, demande front du 06/10 ; arbitrage : Word sur le serveur) — l'<strong>assemblage par Word</strong> :
 * écrit les parties dans un dossier temporaire, lance le script {@code word/assembler-dao.ps1} dans son propre processus
 * PowerShell (Word par automation COM), attend avec un délai, relit le {@code .docx} et le {@code .pdf}. Un seul assemblage à la
 * fois. Désactivable ({@code app.dao-complet.actif}, faux dans les tests sans Word) : sans Word, le DAO complet n'est pas produit et
 * les documents séparés restent servis.
 */
@Component
public class DaoCompletWord {

    private static final Logger log = LoggerFactory.getLogger(DaoCompletWord.class);

    /** Un titre du plan, au niveau 1 (partie du DAO) à 4 (formulaire, annexe) ; seuls ces titres nourrissent le sommaire. */
    public record Titre(String texte, int niveau) {
    }

    /**
     * Une partie : une nouvelle section, ses titres (aucun : suite de la partie précédente), puis son fichier Word ({@code null} :
     * les titres seuls).
     */
    public record Partie(List<Titre> titres, byte[] docx) {
    }

    /** Une ligne de la page de garde. */
    public record LigneGarde(String texte, int taille, boolean gras) {
    }

    public record Resultat(byte[] docx, byte[] pdf) {
    }

    private final boolean actif;
    private final long delaiSecondes;
    private final ObjectMapper mapper;

    public DaoCompletWord(@Value("${app.dao-complet.actif:true}") boolean actif,
            @Value("${app.dao-complet.delai-secondes:300}") long delaiSecondes, ObjectMapper mapper) {
        this.actif = actif && System.getProperty("os.name", "").toLowerCase().contains("windows");
        this.delaiSecondes = delaiSecondes;
        this.mapper = mapper;
    }

    public boolean actif() {
        return actif;
    }

    /** Assemble ; {@link IllegalStateException} sur un échec (Word absent, délai dépassé, script en erreur). */
    public synchronized Resultat assembler(List<LigneGarde> garde, String titreSommaire, String entete, List<Partie> parties) {
        Path dossier = null;
        try {
            dossier = Files.createTempDirectory("dao-complet-");
            Path script = dossier.resolve("assembler-dao.ps1");
            try (InputStream in = DaoCompletWord.class.getResourceAsStream("/word/assembler-dao.ps1")) {
                if (in == null) {
                    throw new IllegalStateException("Script d'assemblage introuvable.");
                }
                // PowerShell 5.1 lit un .ps1 sans BOM dans la page de code ANSI : le BOM garde les accents.
                byte[] corps = in.readAllBytes();
                Files.write(script, concat(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, corps));
            }
            List<Map<String, Object>> lignes = new ArrayList<>();
            for (int i = 0; i < parties.size(); i++) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("titres", parties.get(i).titres() == null ? List.of() : parties.get(i).titres());
                p.put("fichier", "");
                if (parties.get(i).docx() != null) {
                    Path f = dossier.resolve(String.format("partie-%02d.docx", i));
                    Files.write(f, parties.get(i).docx());
                    p.put("fichier", f.toAbsolutePath().toString());
                }
                lignes.add(p);
            }
            // ⚠️ C1 (recette du 06/10) — l'emblème de la République en tête de la page de garde, celui de l'avis spécifique.
            Path embleme = dossier.resolve("embleme.png");
            try (InputStream in = DaoCompletWord.class.getResourceAsStream("/modeles/images/embleme.png")) {
                if (in != null) {
                    Files.write(embleme, in.readAllBytes());
                }
            }
            Path docx = dossier.resolve("dao-complet.docx");
            Path pdf = dossier.resolve("dao-complet.pdf");
            Map<String, Object> manifeste = new LinkedHashMap<>();
            manifeste.put("embleme", Files.exists(embleme) ? embleme.toAbsolutePath().toString() : "");
            manifeste.put("garde", garde);
            manifeste.put("titreSommaire", titreSommaire);
            manifeste.put("entete", entete);
            manifeste.put("parties", lignes);
            manifeste.put("docx", docx.toAbsolutePath().toString());
            manifeste.put("pdf", pdf.toAbsolutePath().toString());
            Path json = dossier.resolve("manifeste.json");
            Files.writeString(json, mapper.writeValueAsString(manifeste), StandardCharsets.UTF_8);
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                    script.toString(), json.toString()).redirectErrorStream(true).redirectOutput(dossier.resolve("sortie.txt").toFile())
                    .start();
            if (!p.waitFor(delaiSecondes, TimeUnit.SECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                throw new IllegalStateException("Assemblage du DAO complet : délai de " + delaiSecondes + " s dépassé.");
            }
            String sortie = Files.readString(dossier.resolve("sortie.txt"), Charsets.lire(dossier.resolve("sortie.txt")));
            if (p.exitValue() != 0 || !Files.exists(docx) || !Files.exists(pdf)) {
                throw new IllegalStateException("Assemblage du DAO complet en échec : " + sortie.strip());
            }
            return new Resultat(Files.readAllBytes(docx), Files.readAllBytes(pdf));
        } catch (IOException e) {
            throw new IllegalStateException("Assemblage du DAO complet en échec : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Assemblage du DAO complet interrompu.", e);
        } finally {
            supprimer(dossier);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void supprimer(Path dossier) {
        if (dossier == null) {
            return;
        }
        try (Stream<Path> s = Files.walk(dossier)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        } catch (IOException e) {
            log.warn("[DAO_COMPLET] dossier temporaire non supprimé : {}", dossier);
        }
    }

    /** La sortie de PowerShell : UTF-8 si elle se lit ainsi, sinon la page de code du système. */
    private static final class Charsets {
        static java.nio.charset.Charset lire(Path f) {
            try {
                StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(f)));
                return StandardCharsets.UTF_8;
            } catch (Exception e) {
                return java.nio.charset.Charset.defaultCharset();
            }
        }
    }
}
