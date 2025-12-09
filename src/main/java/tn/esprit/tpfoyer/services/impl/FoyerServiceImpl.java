package tn.esprit.tpfoyer.services.impl;

import jakarta.transaction.Transactional;
import tn.esprit.tpfoyer.entities.Bloc;
import tn.esprit.tpfoyer.entities.Chambre;
import tn.esprit.tpfoyer.entities.Foyer;
import tn.esprit.tpfoyer.entities.Universite;
import tn.esprit.tpfoyer.repositories.FoyerRepository;
import tn.esprit.tpfoyer.repositories.UniversiteRepository;
import tn.esprit.tpfoyer.services.IFoyerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FoyerServiceImpl implements IFoyerService {

    private final FoyerRepository foyerRepository;
    private final UniversiteRepository universiteRepository;


    @Override
    public List<Foyer> retrieveAllFoyers() {
        return foyerRepository.findAll();
    }

    @Override
    public Foyer addFoyer(Foyer f) {
        return foyerRepository.save(f);
    }

    @Override
    public Foyer updateFoyer(Foyer f) {
        // Vérifier si le foyer existe
        if (foyerRepository.existsById(f.getIdFoyer())) {
            return foyerRepository.save(f);
        }
        throw new RuntimeException("Foyer non trouvé avec ID: " + f.getIdFoyer());
    }

    @Override
    public Foyer retrieveFoyer(long idFoyer) {
        return foyerRepository.findById(idFoyer)
                .orElseThrow(() -> new RuntimeException("Foyer non trouvé avec ID: " + idFoyer));
    }

    @Override
    public void removeFoyer(long idFoyer) {
        if (foyerRepository.existsById(idFoyer)) {
            foyerRepository.deleteById(idFoyer);
        } else {
            throw new RuntimeException("Foyer non trouvé avec ID: " + idFoyer);
        }
    }

    // Services Avancées
    @Override
    @Transactional
    public Foyer ajouterFoyerEtAffecterAUniversite(Foyer foyer, long idUniversite) {
        // 1. Validation de l'université
        Universite universite = universiteRepository.findById(idUniversite)
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec ID: " + idUniversite));

        if (universite.getFoyer() != null) {
            throw new RuntimeException("L'université '" + universite.getNomUniversite() + "' a déjà un foyer affecté: " + universite.getFoyer().getNomFoyer());
        }

        // 2. Validation du foyer
        if (foyer.getNomFoyer() == null || foyer.getNomFoyer().trim().isEmpty()) {
            throw new RuntimeException("Le nom du foyer est obligatoire");
        }

        // 3. Initialiser et valider les blocs
        if (foyer.getBlocs() == null) {
            foyer.setBlocs(new HashSet<>());
        }

        // Validation des noms de blocs uniques
        Set<String> nomsBlocs = new HashSet<>();
        for (Bloc bloc : foyer.getBlocs()) {
            if (bloc.getNomBloc() == null || bloc.getNomBloc().trim().isEmpty()) {
                throw new RuntimeException("Le nom du bloc est obligatoire");
            }
            if (!nomsBlocs.add(bloc.getNomBloc().toLowerCase())) {
                throw new RuntimeException("Nom de bloc dupliqué: " + bloc.getNomBloc());
            }

            // Établir la relation avec le foyer
            bloc.setFoyer(foyer);

            // Traiter les chambres si elles existent
            if (bloc.getChambres() != null) {
                Set<Long> numerosChambres = new HashSet<>();
                for (Chambre chambre : bloc.getChambres()) {
                    if (chambre.getNumeroChambre() == null) {
                        throw new RuntimeException("Le numéro de chambre est obligatoire");
                    }
                    if (!numerosChambres.add(chambre.getNumeroChambre())) {
                        throw new RuntimeException("Numéro de chambre dupliqué: " + chambre.getNumeroChambre());
                    }
                    chambre.setBloc(bloc);
                }
            }
        }

        // 4. Établir les relations bidirectionnelles
        foyer.setUniversite(universite);
        universite.setFoyer(foyer);

        // 5. Calculer la capacité totale du foyer
        long capaciteTotale = foyer.getBlocs().stream()
                .mapToLong(bloc -> bloc.getCapaciteBloc() != null ? bloc.getCapaciteBloc() : 0L)
                .sum();
        foyer.setCapaciteFoyer(capaciteTotale);

        // 6. Sauvegarder
        Foyer savedFoyer = foyerRepository.save(foyer);
        universiteRepository.save(universite);

        return savedFoyer;
    }

    @Override
    public Foyer getFoyerWithMaxChambres() {
        // Utiliser la version simple qui retourne uniquement le foyer
        return foyerRepository.getFoyerWithMaxChambres()
                .orElseThrow(() -> new RuntimeException("Aucun foyer trouvé dans la base de données"));
    }
}