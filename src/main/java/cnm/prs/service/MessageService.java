package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MessageDto;
import cnm.prs.dto.MessageEnvoiRequest;
import cnm.prs.entity.Message;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MessageMapper;
import cnm.prs.repository.MessageRepository;
import cnm.prs.security.CurrentUser;

/**
 * Messagerie interne (§ Module 04). L'expéditeur est toujours l'utilisateur authentifié ;
 * un utilisateur ne voit que les messages dont il est expéditeur ou destinataire.
 */
@Service
@Transactional
public class MessageService {

    private final MessageRepository repository;
    private final NotificationService notificationService;

    public MessageService(MessageRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    /** Tous les messages impliquant l'utilisateur courant (confidentialité). */
    @Transactional(readOnly = true)
    public List<MessageDto> findAll() {
        String ref = CurrentUser.ref().orElse(null);
        if (ref == null) {
            return List.of();
        }
        return repository.findImpliquant(ref).stream().map(MessageMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MessageDto findById(Integer id) {
        Message entity = load(id);
        controlerAcces(entity);
        return MessageMapper.toDto(entity);
    }

    /**
     * Création générique : l'expéditeur est forcé à l'utilisateur courant.
     *
     * <p>La PK vient de {@code seq_message} et l'{@code idMessage} du corps est désormais IGNORÉ. Il
     * était auparavant repris tel quel quand le client en fournissait un : sur PK assignée,
     * {@code save()} est un merge — un id pointant un message existant l'aurait écrasé, expéditeur et
     * date compris. Le repli {@code max+1} qui servait en son absence collisionnait par ailleurs entre
     * deux envois simultanés.
     */
    public MessageDto create(MessageDto dto) {
        Message entity = MessageMapper.toEntity(dto);
        entity.setIdMessage(repository.nextIdMessage().intValue());
        entity.setExpediteurIm(expediteurCourant());
        entity.setDateEnvoi(LocalDateTime.now());
        entity.setLu(false);
        return MessageMapper.toDto(repository.save(entity));
    }

    /** Envoi d'un message : expéditeur = utilisateur courant, non lu, horodaté. */
    public MessageDto envoyer(MessageEnvoiRequest req) {
        Message m = new Message();
        m.setIdMessage(repository.nextIdMessage().intValue());   // PK serveur (seq_message)
        m.setExpediteurIm(expediteurCourant());
        m.setDestinataireIm(req.destinataireIm());
        m.setSujet(req.sujet());
        m.setCorps(req.corps());
        m.setIdDossier(req.idDossier());
        m.setIdMessageParent(req.idMessageParent());
        m.setDateEnvoi(LocalDateTime.now());
        m.setLu(false);
        Message saved = repository.save(m);

        // [Auto] Notifie le destinataire du nouveau message (même transaction).
        notificationService.emettreControleur(TypeNotification.NOUVEAU_MESSAGE, saved.getDestinataireIm(),
                null, saved.getIdMessage(), TypeObjet.MESSAGE, saved.getIdDossier(),
                "Nouveau message" + (saved.getSujet() != null ? " : " + saved.getSujet() : ""),
                "Vous avez reçu un message de " + saved.getExpediteurIm() + ".");
        return MessageMapper.toDto(saved);
    }

    /** Boîte de réception de l'utilisateur courant. */
    @Transactional(readOnly = true)
    public List<MessageDto> recus() {
        return repository.findByDestinataireImOrderByDateEnvoiDesc(expediteurCourant())
                .stream().map(MessageMapper::toDto).toList();
    }

    /** Messages envoyés par l'utilisateur courant. */
    @Transactional(readOnly = true)
    public List<MessageDto> envoyes() {
        return repository.findByExpediteurImOrderByDateEnvoiDesc(expediteurCourant())
                .stream().map(MessageMapper::toDto).toList();
    }

    /** Marque un message comme lu — seul le destinataire le peut. */
    public MessageDto marquerLu(Integer id) {
        Message m = load(id);
        String ref = CurrentUser.ref().orElse(null);
        if (ref == null || !ref.equals(m.getDestinataireIm())) {
            throw new AccessDeniedException("Seul le destinataire peut marquer ce message comme lu.");
        }
        m.setLu(true);
        return MessageMapper.toDto(repository.save(m));
    }

    public MessageDto update(Integer id, MessageDto dto) {
        Message existing = load(id);
        controlerAcces(existing);
        existing.setSujet(dto.getSujet());
        existing.setCorps(dto.getCorps());
        existing.setIdDossier(dto.getIdDossier());
        existing.setIdMessageParent(dto.getIdMessageParent());
        return MessageMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        Message existing = load(id);
        controlerAcces(existing);
        repository.delete(existing);
    }

    private Message load(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message introuvable : " + id));
    }

    private String expediteurCourant() {
        return CurrentUser.ref()
                .orElseThrow(() -> new AccessDeniedException("Utilisateur non identifié."));
    }

    /** Confidentialité : seuls l'expéditeur et le destinataire accèdent au message. */
    private void controlerAcces(Message m) {
        String ref = CurrentUser.ref().orElse(null);
        if (ref == null || (!ref.equals(m.getExpediteurIm()) && !ref.equals(m.getDestinataireIm()))) {
            throw new AccessDeniedException("Message hors de votre périmètre.");
        }
    }
}
