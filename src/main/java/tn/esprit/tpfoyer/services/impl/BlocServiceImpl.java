package tn.esprit.tpfoyer.services.impl;

import jakarta.transaction.Transactional;
import tn.esprit.tpfoyer.entities.Bloc;
import tn.esprit.tpfoyer.entities.Chambre;
import tn.esprit.tpfoyer.repositories.BlocRepository;
import tn.esprit.tpfoyer.repositories.ChambreRepository;
import tn.esprit.tpfoyer.services.IBlocService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlocServiceImpl implements IBlocService {

    private final BlocRepository blocRepository;
    private final ChambreRepository chambreRepository;


    @Override
    public List<Bloc> retrieveBlocs() {
        return blocRepository.findAll();
    }

    @Override
    public Bloc updateBloc(Bloc bloc) {
        if (blocRepository.existsById(bloc.getIdBloc())) {
            return blocRepository.save(bloc);
        }
        throw new RuntimeException("Bloc non trouvé avec ID: " + bloc.getIdBloc());
    }

    @Override
    public Bloc addBloc(Bloc bloc) {
        return blocRepository.save(bloc);
    }

    @Override
    public Bloc retrieveBloc(long idBloc) {
        return blocRepository.findById(idBloc)
                .orElseThrow(() -> new RuntimeException("Bloc non trouvé avec ID: " + idBloc));
    }

    @Override
    public void removeBloc(long idBloc) {
        if (blocRepository.existsById(idBloc)) {
            blocRepository.deleteById(idBloc);
        } else {
            throw new RuntimeException("Bloc non trouvé avec ID: " + idBloc);
        }
    }

    // Services Avancées
    @Override
    @Transactional
    public Bloc affecterChambresABloc(List<Long> numChambre, long idBloc) {
        // 1. Rechercher le bloc par ID
        Bloc bloc = blocRepository.findById(idBloc)
                .orElseThrow(() -> new RuntimeException("Bloc non trouvé avec ID: " + idBloc));

        // 2. Vérifier que la liste des numéros de chambre n'est pas vide
        if (numChambre == null || numChambre.isEmpty()) {
            throw new RuntimeException("La liste des numéros de chambre ne peut pas être vide");
        }

        // 3. Initialiser la collection si elle est null
        if (bloc.getChambres() == null) {
            bloc.setChambres(new HashSet<>());
        }

        // 4. Parcourir tous les numéros de chambre et les affecter au bloc
        for (Long numeroChambre : numChambre) {
            // Rechercher la chambre par son numéro
            Chambre chambre = chambreRepository.findByNumeroChambre(numeroChambre)
                    .orElseThrow(() -> new RuntimeException("Chambre non trouvée avec le numéro: " + numeroChambre));

            // Vérifier si la chambre est déjà affectée à un autre bloc
            if (chambre.getBloc() != null && !chambre.getBloc().getIdBloc().equals(idBloc)) {
                throw new RuntimeException("La chambre " + numeroChambre + " est déjà affectée au bloc: " + chambre.getBloc().getNomBloc());
            }

            // Affecter le bloc à la chambre
            chambre.setBloc(bloc);

            // Ajouter la chambre à la collection du bloc (éviter les doublons)
            if (!bloc.getChambres().contains(chambre)) {
                bloc.getChambres().add(chambre);
            }

            // Sauvegarder la chambre
            chambreRepository.save(chambre);
        }

        // 5. Sauvegarder et retourner le bloc mis à jour
        return blocRepository.save(bloc);
    }
}