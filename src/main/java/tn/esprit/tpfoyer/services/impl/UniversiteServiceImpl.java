package tn.esprit.tpfoyer.services.impl;

import jakarta.transaction.Transactional;
import tn.esprit.tpfoyer.entities.Bloc;
import tn.esprit.tpfoyer.entities.Chambre;
import tn.esprit.tpfoyer.entities.Foyer;
import tn.esprit.tpfoyer.entities.Universite;
import tn.esprit.tpfoyer.repositories.FoyerRepository;
import tn.esprit.tpfoyer.repositories.UniversiteRepository;
import tn.esprit.tpfoyer.services.IUniversiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UniversiteServiceImpl implements IUniversiteService {

    private final UniversiteRepository universiteRepository;
    private final FoyerRepository foyerRepository;

    @Override
    public List<Universite> retrieveAllUniversities() {
        return universiteRepository.findAll();
    }

    @Override
    public Universite addUniversite(Universite u) {
        return universiteRepository.save(u);
    }

    @Override
    public Universite updateUniversite(Universite u) {
        if (universiteRepository.existsById(u.getIdUniversite())) {
            return universiteRepository.save(u);
        }
        throw new RuntimeException("Université non trouvée avec ID: " + u.getIdUniversite());
    }

    @Override
    public Universite retrieveUniversite(long idUniversite) {
        return universiteRepository.findById(idUniversite)
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec ID: " + idUniversite));
    }

    // Services Avancées
    @Override
    @Transactional
    public Universite affecterFoyerAUniversite(long idFoyer, String nomUniversite) {
        // 1. Rechercher l'université par nom
        Universite universite = universiteRepository.findByNomUniversite(nomUniversite)
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec le nom: " + nomUniversite));

        // 2. Rechercher le foyer par ID
        Foyer foyer = foyerRepository.findById(idFoyer)
                .orElseThrow(() -> new RuntimeException("Foyer non trouvé avec ID: " + idFoyer));

        // 3. Vérifier si le foyer est déjà affecté à une autre université
        if (foyer.getUniversite() != null) {
            throw new RuntimeException("Ce foyer est déjà affecté à l'université: " + foyer.getUniversite().getNomUniversite());
        }

        // 4. Affecter le foyer à l'université (côté parent)
        universite.setFoyer(foyer);

        // 5. Affecter l'université au foyer (côté child) - important pour la bidirectionnalité
        foyer.setUniversite(universite);

        // 6. Sauvegarder l'université (cascade ALL va sauvegarder le foyer aussi)
        return universiteRepository.save(universite);
    }

    @Override
    @Transactional
    public Universite desaffecterFoyerAUniversite(long idUniversite) {
        // 1. Rechercher l'université par ID
        Universite universite = universiteRepository.findById(idUniversite)
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec ID: " + idUniversite));

        // 2. Vérifier si l'université a un foyer affecté
        Foyer foyer = universite.getFoyer();
        if (foyer == null) {
            throw new RuntimeException("Aucun foyer n'est affecté à cette université");
        }

        // 3. Désaffecter le foyer de l'université (côté parent)
        universite.setFoyer(null);

        // 4. Désaffecter l'université du foyer (côté child)
        foyer.setUniversite(null);

        // 5. Sauvegarder les deux entités
        universiteRepository.save(universite);
        foyerRepository.save(foyer);

        return universite;
    }
}