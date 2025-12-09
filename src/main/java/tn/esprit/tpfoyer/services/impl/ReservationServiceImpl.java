package tn.esprit.tpfoyer.services.impl;

import jakarta.transaction.Transactional;
import tn.esprit.tpfoyer.entities.*;
import tn.esprit.tpfoyer.repositories.*;
import tn.esprit.tpfoyer.services.IReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements IReservationService {

    private final ReservationRepository reservationRepository;
    private final ChambreRepository chambreRepository;
    private final EtudiantRepository etudiantRepository;
    private final UniversiteRepository universiteRepository;


    @Override
    public List<Reservation> retrieveAllReservation() {
        return reservationRepository.findAll();
    }

    @Override
    public Reservation updateReservation(Reservation res) {
        if (reservationRepository.existsById(res.getIdReservation())) {
            return reservationRepository.save(res);
        }
        throw new RuntimeException("Réservation non trouvée avec ID: " + res.getIdReservation());
    }

    @Override
    public Reservation retrieveReservation(String idReservation) {
        return reservationRepository.findById(idReservation)
                .orElseThrow(() -> new RuntimeException("Réservation non trouvée avec ID: " + idReservation));
    }

    // Services Avancées
    @Override
    @Transactional
    public Reservation ajouterReservation(long idChambre, long cinEtudiant) {
        // 1. Rechercher la chambre par ID
        Chambre chambre = chambreRepository.findById(idChambre)
                .orElseThrow(() -> new RuntimeException("Chambre non trouvée avec ID: " + idChambre));

        // 2. Rechercher l'étudiant par CIN
        Etudiant etudiant = etudiantRepository.findByCin(cinEtudiant)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé avec CIN: " + cinEtudiant));

        // 3. Vérifier la capacité de la chambre
        if (!estChambreDisponible(chambre)) {
            throw new RuntimeException("La chambre " + chambre.getNumeroChambre() + " a atteint sa capacité maximale. Type: " + chambre.getTypeC() + ", capacité max: " + getCapaciteMaxParType(chambre.getTypeC()));
        }

        // 4. Vérifier si l'étudiant a déjà une réservation valide
        if (aDejaReservationValide(etudiant)) {
            throw new RuntimeException("L'étudiant " + etudiant.getNomEt() + " " + etudiant.getPrenomEt() + " a déjà une réservation valide");
        }

        // 5. Générer le numéro de réservation selon le format demandé
        String numReservation = genererNumReservation(chambre);

        // 6. Vérifier si une réservation avec le même ID existe déjà
        if (reservationRepository.existsById(numReservation)) {
            throw new RuntimeException("Une réservation avec le numéro " + numReservation + " existe déjà");
        }

        // 7. Créer la réservation
        Reservation reservation = Reservation.builder()
                .idReservation(numReservation)
                .anneeUniversitaire(new Date())
                .estValide(true)
                .etudiants(new HashSet<>())
                .build();

        // 8. Ajouter l'étudiant à la réservation
        reservation.getEtudiants().add(etudiant);

        // 9. Ajouter la réservation à la chambre
        if (chambre.getReservations() == null) {
            chambre.setReservations(new HashSet<>());
        }
        chambre.getReservations().add(reservation);

        // 10. Ajouter la réservation à l'étudiant
        if (etudiant.getReservations() == null) {
            etudiant.setReservations(new HashSet<>());
        }
        etudiant.getReservations().add(reservation);

        // 11. Sauvegarder
        Reservation savedReservation = reservationRepository.save(reservation);
        chambreRepository.save(chambre);
        etudiantRepository.save(etudiant);

        return savedReservation;
    }

    // Méthode pour vérifier si la chambre est disponible
    private boolean estChambreDisponible(Chambre chambre) {
        if (chambre.getReservations() == null) {
            return true;
        }

        // Compter les réservations valides pour cette chambre
        long reservationsValides = chambre.getReservations().stream()
                .filter(res -> res.getEstValide() != null && res.getEstValide())
                .count();

        // Vérifier la capacité selon le type de chambre
        int capaciteMax = getCapaciteMaxParType(chambre.getTypeC());

        return reservationsValides < capaciteMax;
    }

    // Méthode pour obtenir la capacité maximale selon le type de chambre
    private int getCapaciteMaxParType(TypeChambre type) {
        if (type == null) return 1;

        switch (type) {
            case SIMPLE: return 1;
            case DOUBLE: return 2;
            case TRIPLE: return 3;
            default: return 1;
        }
    }

    // Méthode pour vérifier si l'étudiant a déjà une réservation valide
    private boolean aDejaReservationValide(Etudiant etudiant) {
        if (etudiant.getReservations() == null) {
            return false;
        }

        return etudiant.getReservations().stream()
                .anyMatch(res -> res.getEstValide() != null && res.getEstValide());
    }

    // Méthode pour générer le numéro de réservation selon le format demandé
    private String genererNumReservation(Chambre chambre) {
        // Format: numChambre-nomBloc-anneeUniversitaire
        String numChambre = String.valueOf(chambre.getNumeroChambre());

        // Vérifier que le bloc n'est pas null
        if (chambre.getBloc() == null) {
            throw new RuntimeException("La chambre " + chambre.getNumeroChambre() + " n'est pas affectée à un bloc");
        }

        String nomBloc = chambre.getBloc().getNomBloc();

        // Obtenir l'année universitaire courante (format: AAAA)
        Calendar cal = Calendar.getInstance();
        int annee = cal.get(Calendar.YEAR);

        return numChambre + "-" + nomBloc + "-" + annee;
    }

    @Override
    @Transactional
    public Reservation annulerReservation(long cinEtudiant) {
        // 1. Rechercher l'étudiant par CIN
        Etudiant etudiant = etudiantRepository.findByCin(cinEtudiant)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé avec CIN: " + cinEtudiant));

        // 2. Trouver la réservation valide de l'étudiant
        Reservation reservation = trouverReservationValide(etudiant);
        if (reservation == null) {
            throw new RuntimeException("Aucune réservation valide trouvée pour l'étudiant avec CIN: " + cinEtudiant);
        }

        // 3. Mettre à jour l'état de la réservation (estValide: false)
        reservation.setEstValide(false);

        // 4. Désaffecter l'étudiant de la réservation
        reservation.getEtudiants().remove(etudiant);

        // 5. Désaffecter la réservation de l'étudiant
        etudiant.getReservations().remove(reservation);

        // 6. Trouver la chambre associée à cette réservation
        Chambre chambre = trouverChambreParReservation(reservation);
        if (chambre != null) {
            // 7. Désaffecter la réservation de la chambre
            chambre.getReservations().remove(reservation);
            chambreRepository.save(chambre);
        }

        // 8. Sauvegarder les modifications
        Reservation savedReservation = reservationRepository.save(reservation);
        etudiantRepository.save(etudiant);

        return savedReservation;
    }

    @Override
    public List<Reservation> getReservationParAnneeUniversitaireEtNomUniversite(Date anneeUniversite, String nomUniversite) {
        return List.of();
    }
    // Méthode pour trouver la réservation valide d'un étudiant
    private Reservation trouverReservationValide(Etudiant etudiant) {
        if (etudiant.getReservations() == null || etudiant.getReservations().isEmpty()) {
            return null;
        }

        return etudiant.getReservations().stream()
                .filter(res -> res.getEstValide() != null && res.getEstValide())
                .findFirst()
                .orElse(null);
    }

    // Méthode pour trouver la chambre associée à une réservation
    private Chambre trouverChambreParReservation(Reservation reservation) {
        // Cette méthode nécessite une relation entre Chambre et Reservation
        // Si vous n'avez pas cette relation, vous devez l'implémenter

        // Alternative: parcourir toutes les chambres pour trouver celle qui contient la réservation
        List<Chambre> toutesChambres = chambreRepository.findAll();

        for (Chambre chambre : toutesChambres) {
            if (chambre.getReservations() != null && chambre.getReservations().contains(reservation)) {
                return chambre;
            }
        }

        return null;
    }

    @Override
    public List<Reservation> getReservationParAnneeEtNomUniversite(int annee, String nomUniversite) {
        if (nomUniversite == null || nomUniversite.trim().isEmpty()) {
            throw new RuntimeException("Le nom de l'université ne peut pas être vide");
        }

        if (annee <= 0) {
            throw new RuntimeException("L'année doit être positive");
        }

        // Vérifier que l'université existe
        universiteRepository.findByNomUniversite(nomUniversite.trim())
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec le nom: " + nomUniversite));

        List<Reservation> reservations = reservationRepository.findByAnneeAndUniversite(annee, nomUniversite);

        return reservations;
    }


}