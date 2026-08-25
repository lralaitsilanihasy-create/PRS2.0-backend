package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {

    /** Boîte de réception (messages reçus), du plus récent au plus ancien. */
    List<Message> findByDestinataireImOrderByDateEnvoiDesc(String destinataireIm);

    /** Messages envoyés, du plus récent au plus ancien. */
    List<Message> findByExpediteurImOrderByDateEnvoiDesc(String expediteurIm);

    /** Messages impliquant l'utilisateur (expéditeur ou destinataire) — confidentialité. */
    @Query("select m from Message m where m.expediteurIm = :ref or m.destinataireIm = :ref order by m.dateEnvoi desc")
    List<Message> findImpliquant(@Param("ref") String ref);

    /**
     * Prochaine PK de message, allouée par la séquence serveur (Voie B — l'id client est ignoré).
     * Remplace un {@code max(ID_MESSAGE) + 1} : deux envois simultanés lisaient le même maximum et la
     * seconde insertion échouait en violation d'unicité.
     */
    @Query(value = "select nextval('seq_message')", nativeQuery = true)
    Long nextIdMessage();
}
