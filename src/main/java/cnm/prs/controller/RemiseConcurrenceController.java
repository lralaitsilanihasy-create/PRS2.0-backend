package cnm.prs.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.RemiseDto;
import cnm.prs.entity.RemiseReponse;
import cnm.prs.service.RemiseConcurrenceService;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-2 ; V125) — la remise en concurrence des titulaires d'un contrat-cadre en
 * vigueur : le lancement et l'enregistrement des réponses papier (PRMP, UGPM), l'évaluation (président de la CAO du contrat-cadre, ⚠️ H1),
 * la décision (PRMP), la lecture (PRMP et UGPM de la fiche, membres de sa CAO, Commission).
 */
@RestController
public class RemiseConcurrenceController {

    private final RemiseConcurrenceService service;

    public RemiseConcurrenceController(RemiseConcurrenceService service) {
        this.service = service;
    }

    /**
     * 201 ; 400 {@code CHAMP_OBLIGATOIRE}, {@code ECHEANCE_INVALIDE} ; 409 {@code CONTRAT_ECHU}, {@code FORME_SANS_REMISE},
     * {@code SANS_TITULAIRE}, {@code REMISE_EN_COURS}.
     */
    @PostMapping("/api/contrats-en-vigueur/{idContrat}/remises")
    public ResponseEntity<RemiseDto> lancer(@PathVariable Long idContrat, @RequestBody(required = false) RemiseDto.RemiseRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.lancer(idContrat, r));
    }

    @GetMapping("/api/contrats-en-vigueur/{idContrat}/remises")
    public List<RemiseDto> duContrat(@PathVariable Long idContrat) {
        return service.listerDuContrat(idContrat);
    }

    @GetMapping("/api/remises")
    public List<RemiseDto> lister() {
        return service.lister();
    }

    @GetMapping("/api/remises/{id}")
    public RemiseDto lire(@PathVariable Long id) {
        return service.lire(id);
    }

    /** La lettre d'invitation, en PDF ou en Word ({@code ?format=docx}). */
    @GetMapping("/api/remises/{id}/lettre")
    public ResponseEntity<byte[]> lettre(@PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return Telechargements.fichier("lettre-invitation-remise-" + id + (docx ? ".docx" : ".pdf"), docx ? "docx" : "pdf", service.lettre(id, docx));
    }

    /** Le rapport simplifié de la CAO, en PDF ou en Word ; 404 avant l'évaluation. */
    @GetMapping("/api/remises/{id}/rapport")
    public ResponseEntity<byte[]> rapport(@PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return Telechargements.fichier("rapport-simplifie-remise-" + id + (docx ? ".docx" : ".pdf"), docx ? "docx" : "pdf", service.rapport(id, docx));
    }

    /** Le fichier d'une réponse ; 409 {@code REPONSES_FERMEES} avant l'échéance. */
    @GetMapping("/api/remises/{id}/reponses/{idReponse}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Long id, @PathVariable Long idReponse) {
        RemiseReponse y = service.fichier(id, idReponse);
        return Telechargements.fichier(y.getNom(), y.getFormat(), y.getContenu());
    }

    /**
     * Une réponse reçue sur papier : multipart {@code idOffre}, {@code nature}, {@code montantHt}, {@code montantTtc}, {@code delai},
     * {@code dateReception} (ISO), {@code fichier} ; 201 ; 400 {@code NATURE_INVALIDE}, {@code MONTANT_INVALIDE}, {@code DATE_INVALIDE},
     * {@code FICHIER_OBLIGATOIRE}, {@code FORMAT_INVALIDE} ; 409 {@code REMISE_CLOSE}.
     */
    @PostMapping(value = "/api/remises/{id}/reponses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RemiseDto> enregistrerPapier(@PathVariable Long id, @RequestParam(value = "idOffre", required = false) String idOffre,
            @RequestParam(value = "nature", required = false) String nature,
            @RequestParam(value = "montantHt", required = false) BigDecimal montantHt,
            @RequestParam(value = "montantTtc", required = false) BigDecimal montantTtc,
            @RequestParam(value = "delai", required = false) String delai,
            @RequestParam(value = "dateReception", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateReception,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.enregistrerPapier(id, idOffre, nature, montantHt, montantTtc, delai, dateReception, fichier));
    }

    /**
     * Le président de la CAO : 400 {@code PRIX_MOYEN_INVALIDE}, {@code DECISIONS_INCOMPLETES}, {@code MOTIF_OBLIGATOIRE} ; 409
     * {@code DEJA_EVALUEE}, {@code ECHEANCE_NON_PASSEE}, {@code OFFRE_NON_CONFORME}.
     */
    @PostMapping("/api/remises/{id}/evaluation")
    public RemiseDto evaluer(@PathVariable Long id, @RequestBody(required = false) RemiseDto.EvaluationRequest r) {
        return service.evaluer(id, r);
    }

    /** La PRMP : 409 {@code NON_EVALUEE}, {@code AUCUNE_PROPOSITION}. */
    @PostMapping("/api/remises/{id}/attribution")
    public RemiseDto attribuer(@PathVariable Long id, @RequestBody(required = false) RemiseDto.DecisionRequest r) {
        return service.attribuer(id, r);
    }

    /** La PRMP : 400 {@code MOTIF_OBLIGATOIRE} ; 409 {@code NON_EVALUEE}. */
    @PostMapping("/api/remises/{id}/infructueuse")
    public RemiseDto declarerInfructueuse(@PathVariable Long id, @RequestBody(required = false) RemiseDto.DecisionRequest r) {
        return service.declarerInfructueuse(id, r);
    }
}
