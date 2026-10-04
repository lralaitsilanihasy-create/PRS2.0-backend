package cnm.prs.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ⚠️ 2026-10-04 (soumission en ligne, lot 3 ; ADR-0013 §7) — le <strong>stockage des offres chiffrées</strong>, sur disque, hors de
 * la base, sous {@code app.offres.repertoire}. Pendant le dépôt, un morceau par fichier ({@code <idOffre>/morceaux/<rang>.bin},
 * rejouable) ; au scellement, un fichier par offre ({@code <idOffre>.offre}) : la longueur de l'en-tête sur 4 octets (gros-boutiste),
 * l'en-tête JSON tel que reçu (UTF-8), puis les morceaux dans l'ordre. Le serveur ne déchiffre rien : il écrit, compte et hache.
 */
@Component
public class StockageOffres {

    private final Path racine;

    public StockageOffres(@Value("${app.offres.repertoire:${user.home}/prs-offres}") String repertoire) {
        this.racine = Path.of(repertoire).toAbsolutePath();
    }

    private Path dossierMorceaux(String idOffre) {
        return racine.resolve(idOffre).resolve("morceaux");
    }

    /** Écrit (ou remplace) un morceau. */
    public void ecrireMorceau(String idOffre, int rang, byte[] octets) {
        try {
            Path d = dossierMorceaux(idOffre);
            Files.createDirectories(d);
            Files.write(d.resolve(rang + ".bin"), octets);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du morceau " + rang + " de l'offre " + idOffre + " impossible.", e);
        }
    }

    /** Le résultat de l'assemblage : le chemin du conteneur et l'empreinte SHA-256 (hexadécimal) de l'en-tête puis des morceaux. */
    public record Assemblage(Path chemin, String empreinte, long taille) {
    }

    /**
     * Assemble le conteneur et calcule son empreinte en une passe : SHA-256 de l'en-tête (ses octets UTF-8) puis des morceaux dans
     * l'ordre — la longueur préfixée n'entre pas dans l'empreinte. Les morceaux restent en place : l'appelant les retire une fois
     * l'empreinte acceptée ({@link #retirerMorceaux}), ou retire le conteneur ({@link #supprimerConteneur}) si elle est refusée.
     */
    public Assemblage assembler(String idOffre, String enTete, int nombreMorceaux) {
        Path cible = racine.resolve(idOffre + ".offre");
        try {
            Files.createDirectories(racine);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] tete = enTete.getBytes(StandardCharsets.UTF_8);
            long taille = 0;
            try (OutputStream out = Files.newOutputStream(cible)) {
                out.write(ByteBuffer.allocate(4).putInt(tete.length).array());
                try (DigestOutputStream hache = new DigestOutputStream(OutputStream.nullOutputStream(), sha)) {
                    hache.write(tete);
                    out.write(tete);
                    for (int r = 0; r < nombreMorceaux; r++) {
                        byte[] m = Files.readAllBytes(dossierMorceaux(idOffre).resolve(r + ".bin"));
                        hache.write(m);
                        out.write(m);
                        taille += m.length;
                    }
                }
            }
            return new Assemblage(cible, HexFormat.of().formatHex(sha.digest()), taille);
        } catch (IOException e) {
            throw new UncheckedIOException("Assemblage de l'offre " + idOffre + " impossible.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ⚠️ Lot 4 — un conteneur relu : l'en-tête, les morceaux, et l'empreinte recalculée (SHA-256 de l'en-tête puis des morceaux). */
    public record Conteneur(String enTete, List<byte[]> morceaux, String empreinte) {
    }

    /** ⚠️ Lot 4 (§B3) — relit le conteneur scellé : longueur de l'en-tête (4 octets), en-tête, puis morceaux de taille pleine sauf le dernier. */
    public Conteneur lire(String chemin, int nombreMorceaux, int tailleMorceauChiffre) {
        try {
            byte[] tout = Files.readAllBytes(Path.of(chemin));
            int longueur = ByteBuffer.wrap(tout, 0, 4).getInt();
            String enTete = new String(tout, 4, longueur, StandardCharsets.UTF_8);
            List<byte[]> morceaux = new java.util.ArrayList<>();
            int pos = 4 + longueur;
            for (int r = 0; r < nombreMorceaux; r++) {
                int fin = r == nombreMorceaux - 1 ? tout.length : Math.min(tout.length, pos + tailleMorceauChiffre);
                morceaux.add(java.util.Arrays.copyOfRange(tout, pos, fin));
                pos = fin;
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(tout, 4, tout.length - 4);
            return new Conteneur(enTete, morceaux, HexFormat.of().formatHex(sha.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du conteneur " + chemin + " impossible.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ⚠️ Lot 4 (§B3) — le contenu en clair d'une offre ouverte (l'archive ZIP du candidat), au régime des pièces du dossier. */
    public void ecrireClair(String idOffre, byte[] zip) {
        try {
            Files.createDirectories(racine);
            Files.write(racine.resolve(idOffre + ".clair.zip"), zip);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du contenu de l'offre " + idOffre + " impossible.", e);
        }
    }

    /** ⚠️ Lot 4 — le contenu en clair d'une offre ouverte, {@code null} s'il n'existe pas. */
    public byte[] lireClair(String idOffre) {
        Path p = racine.resolve(idOffre + ".clair.zip");
        try {
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void retirerMorceaux(String idOffre) {
        supprimerArbre(racine.resolve(idOffre));
    }

    public void supprimerConteneur(String idOffre) {
        try {
            Files.deleteIfExists(racine.resolve(idOffre + ".offre"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** La purge d'un dépôt abandonné : les morceaux et un conteneur éventuel. */
    public void purger(String idOffre) {
        retirerMorceaux(idOffre);
        supprimerConteneur(idOffre);
    }

    private static void supprimerArbre(Path p) {
        if (!Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            List<Path> tous = s.sorted(Comparator.reverseOrder()).toList();
            for (Path x : tous) {
                Files.deleteIfExists(x);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
