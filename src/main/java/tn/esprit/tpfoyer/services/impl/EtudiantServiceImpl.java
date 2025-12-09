package tn.esprit.tpfoyer.services.impl;

import tn.esprit.tpfoyer.entities.Etudiant;
import tn.esprit.tpfoyer.repositories.EtudiantRepository;
import tn.esprit.tpfoyer.services.IEtudiantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EtudiantServiceImpl implements IEtudiantService {

    private final EtudiantRepository etudiantRepository;

    @Override
    public List<Etudiant> retrieveAllEtudiants() {
        return etudiantRepository.findAll();
    }

    @Override
    public List<Etudiant> addEtudiants(List<Etudiant> etudiants) {
        return etudiantRepository.saveAll(etudiants);
    }

    @Override
    public Etudiant updateEtudiant(Etudiant e) {
        if (etudiantRepository.existsById(e.getIdEtudiant())) {
            return etudiantRepository.save(e);
        }
        throw new RuntimeException("Étudiant non trouvé avec ID: " + e.getIdEtudiant());
    }

    @Override
    public Etudiant retrieveEtudiant(long idEtudiant) {
        return etudiantRepository.findById(idEtudiant)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé avec ID: " + idEtudiant));
    }

    @Override
    public void removeEtudiant(long idEtudiant) {
        if (etudiantRepository.existsById(idEtudiant)) {
            etudiantRepository.deleteById(idEtudiant);
        } else {
            throw new RuntimeException("Étudiant non trouvé avec ID: " + idEtudiant);
        }
    }

    @Override
    public List<Etudiant> getEtudiantsAvecReservationValidePourAnnee(int annee) {
        // Validation de l'année
        if (annee <= 0) {
            throw new RuntimeException("L'année doit être positive. Année donnée: " + annee);
        }

        // Validation que l'année est raisonnable (par exemple entre 2000 et 2100)
        int currentYear = java.time.Year.now().getValue();
        if (annee < 2000 || annee > currentYear + 5) {
            throw new RuntimeException("L'année doit être entre 2000 et " + (currentYear + 5) + ". Année donnée: " + annee);
        }

        // Appel du repository
        List<Etudiant> etudiants = etudiantRepository.findEtudiantsByReservationValideAndAnnee(annee);

        // Vérifier si des étudiants ont été trouvés
        if (etudiants.isEmpty()) {
            throw new RuntimeException("Aucun étudiant trouvé avec une réservation valide pour l'année " + annee);
        }

        return etudiants;
    }

    @Override
    public List<Etudiant> getEtudiantsSansReservation() {
        // Utiliser la version avec NOT EXISTS (la plus efficace)
        List<Etudiant> etudiantsSansReservation = etudiantRepository.findEtudiantsSansReservation();

        // Vérifier si des étudiants ont été trouvés
        if (etudiantsSansReservation.isEmpty()) {
            throw new RuntimeException("Tous les étudiants ont au moins une réservation");
        }

        return etudiantsSansReservation;
    }

    public int calculerAge(Etudiant etudiant) {
        if (etudiant.getDateNaissance() == null) {
            return 0;
        }

        LocalDate dateNaissance = etudiant.getDateNaissance().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate aujourdhui = LocalDate.now();

        return Period.between(dateNaissance, aujourdhui).getYears();
    }
}