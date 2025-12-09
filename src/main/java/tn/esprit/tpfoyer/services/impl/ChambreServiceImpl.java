package tn.esprit.tpfoyer.services.impl;

import tn.esprit.tpfoyer.entities.*;
import tn.esprit.tpfoyer.repositories.BlocRepository;
import tn.esprit.tpfoyer.repositories.ChambreRepository;
import tn.esprit.tpfoyer.repositories.UniversiteRepository;
import tn.esprit.tpfoyer.services.IChambreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChambreServiceImpl implements IChambreService {

    private final ChambreRepository chambreRepository;
    private final UniversiteRepository universiteRepository;
    private final BlocRepository blocRepository;

    @Override
    public List<Chambre> retrieveAllChambres() {
        return chambreRepository.findAll();
    }

    @Override
    public Chambre addChambre(Chambre c) {
        return chambreRepository.save(c);
    }

    @Override
    public Chambre updateChambre(Chambre c) {
        if (chambreRepository.existsById(c.getIdChambre())) {
            return chambreRepository.save(c);
        }
        throw new RuntimeException("Chambre non trouvée avec ID: " + c.getIdChambre());
    }

    @Override
    public Chambre retrieveChambre(long idChambre) {
        return chambreRepository.findById(idChambre)
                .orElseThrow(() -> new RuntimeException("Chambre non trouvée avec ID: " + idChambre));
    }

    // Services Avancées
    @Override
    public List<Chambre> getChambresParNomUniversite(String nomUniversite) {
        // 1. Valider le paramètre d'entrée
        if (nomUniversite == null || nomUniversite.trim().isEmpty()) {
            throw new RuntimeException("Le nom de l'université ne peut pas être vide");
        }

        // 2. Rechercher l'université par nom
        Universite universite = universiteRepository.findByNomUniversite(nomUniversite.trim())
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec le nom: " + nomUniversite));

        // 3. Vérifier si l'université a un foyer affecté
        if (universite.getFoyer() == null) {
            throw new RuntimeException("Aucun foyer n'est affecté à l'université: " + nomUniversite);
        }

        Foyer foyer = universite.getFoyer();

        // 4. Vérifier si le foyer a des blocs
        if (foyer.getBlocs() == null || foyer.getBlocs().isEmpty()) {
            return new ArrayList<>(); // Retourner une liste vide si pas de blocs
        }

        // 5. Collecter toutes les chambres de tous les blocs du foyer
        List<Chambre> chambres = new ArrayList<>();

        for (Bloc bloc : foyer.getBlocs()) {
            if (bloc.getChambres() != null && !bloc.getChambres().isEmpty()) {
                chambres.addAll(bloc.getChambres());
            }
        }

        return chambres;
    }

    @Override
    public List<Chambre> getChambresParBlocEtTypeAvecJPQL(long idBloc, TypeChambre typeC) {
        // Validation des paramètres
        if (idBloc <= 0) {
            throw new RuntimeException("L'ID du bloc doit être positif");
        }

        if (typeC == null) {
            throw new RuntimeException("Le type de chambre ne peut pas être null");
        }

        // Vérifier si le bloc existe
        if (!blocRepository.existsById(idBloc)) {
            throw new RuntimeException("Bloc non trouvé avec ID: " + idBloc);
        }

        // Utiliser la solution avec JPQL
        List<Chambre> chambres = chambreRepository.findChambresByBlocAndType(idBloc, typeC);

        return chambres;
    }

    @Override
    public List<Chambre> getChambresParBlocEtType(long idBloc, TypeChambre typeC) {
        // Validation des paramètres
        if (idBloc <= 0) {
            throw new RuntimeException("L'ID du bloc doit être positif");
        }

        if (typeC == null) {
            throw new RuntimeException("Le type de chambre ne peut pas être null");
        }

        // Vérifier si le bloc existe
        if (!blocRepository.existsById(idBloc)) {
            throw new RuntimeException("Bloc non trouvé avec ID: " + idBloc);
        }

        // Utiliser la solution avec Keywords (méthode dérivée)
        List<Chambre> chambres = chambreRepository.findByBlocIdBlocAndTypeC(idBloc, typeC);

        return chambres;
    }

    @Override
    public List<Chambre> getChambresNonReserveParNomUniversiteEtTypeChambre(String nomUniversite, TypeChambre type) {
        // Validation des paramètres
        if (nomUniversite == null || nomUniversite.trim().isEmpty()) {
            throw new RuntimeException("Le nom de l'université ne peut pas être vide");
        }

        if (type == null) {
            throw new RuntimeException("Le type de chambre ne peut pas être null");
        }

        // Vérifier que l'université existe
        universiteRepository.findByNomUniversite(nomUniversite.trim())
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec le nom: " + nomUniversite));

        // Utiliser le repository pour récupérer les chambres non réservées
        List<Chambre> chambresNonReservees = chambreRepository.findChambresNonReserveesParNomUniversiteEtType(
                nomUniversite, type, getAnneeUniversitaireActuelle());

        return chambresNonReservees;
    }

    // Méthode utilitaire pour obtenir l'année universitaire actuelle
    private int getAnneeUniversitaireActuelle() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR);
    }

    @Override
    public Chambre getChambreByCinEtudiant(long cinEtudiant) {
        // Validation du paramètre
        if (cinEtudiant <= 0) {
            throw new RuntimeException("Le CIN de l'étudiant doit être positif");
        }

        // Utiliser la requête JPQL directe
        List<Chambre> chambres = chambreRepository.findChambreByCinEtudiant(cinEtudiant);

        if (chambres.isEmpty()) {
            throw new RuntimeException("Aucune chambre trouvée pour l'étudiant avec CIN " + cinEtudiant);
        }

        return chambres.get(0);
    }

    @Override
    public Map<TypeChambre, Long> countChambresParTypePourUniversite(String nomUniversite) {
        // Validation du paramètre
        if (nomUniversite == null || nomUniversite.trim().isEmpty()) {
            throw new RuntimeException("Le nom de l'université ne peut pas être vide");
        }

        // Vérifier que l'université existe
        universiteRepository.findByNomUniversite(nomUniversite.trim())
                .orElseThrow(() -> new RuntimeException("Université non trouvée avec le nom: " + nomUniversite));

        // Exécuter la requête JPQL
        List<Object[]> resultats = chambreRepository.countChambresParTypePourUniversite(nomUniversite);

        // Créer une map pour stocker les résultats
        Map<TypeChambre, Long> statistiques = new HashMap<>();

        // Initialiser tous les types à 0
        for (TypeChambre type : TypeChambre.values()) {
            statistiques.put(type, 0L);
        }

        // Remplir la map avec les résultats de la requête
        for (Object[] resultat : resultats) {
            TypeChambre type = (TypeChambre) resultat[0];
            Long count = (Long) resultat[1];
            statistiques.put(type, count);
        }

        return statistiques;
    }

    @Override
    public List<Chambre> getAllChambresDisponibles() {
        // Utiliser la version 1: Chambres sans aucune réservation valide
        List<Chambre> chambresDisponibles = chambreRepository.findAllChambresDisponibles();

        // Vérifier si des chambres sont disponibles
        if (chambresDisponibles.isEmpty()) {
            throw new RuntimeException("Aucune chambre disponible (toutes ont des réservations valides)");
        }

        return chambresDisponibles;
    }

    // Méthode supplémentaire: Pour obtenir les statistiques des chambres disponibles
    public Map<String, Object> getStatistiquesChambresDisponibles() {
        List<Chambre> toutesChambres = retrieveAllChambres();
        List<Chambre> chambresDisponibles = getAllChambresDisponibles();

        Map<String, Object> statistiques = new HashMap<>();
        statistiques.put("totalChambres", toutesChambres.size());
        statistiques.put("chambresDisponibles", chambresDisponibles.size());
        statistiques.put("chambresOccupees", toutesChambres.size() - chambresDisponibles.size());

        // Calculer les pourcentages
        if (toutesChambres.size() > 0) {
            double pourcentageDisponible = (chambresDisponibles.size() * 100.0) / toutesChambres.size();
            double pourcentageOccupe = 100.0 - pourcentageDisponible;

            statistiques.put("pourcentageDisponible", Math.round(pourcentageDisponible * 100.0) / 100.0);
            statistiques.put("pourcentageOccupe", Math.round(pourcentageOccupe * 100.0) / 100.0);
        } else {
            statistiques.put("pourcentageDisponible", 0.0);
            statistiques.put("pourcentageOccupe", 0.0);
        }

        // Statistiques par type
        Map<String, Long> disponiblesParType = new HashMap<>();
        Map<String, Long> totalParType = new HashMap<>();

        for (TypeChambre type : TypeChambre.values()) {
            disponiblesParType.put(type.name(), 0L);
            totalParType.put(type.name(), 0L);
        }

        // Compter les chambres disponibles par type
        for (Chambre chambre : chambresDisponibles) {
            if (chambre.getTypeC() != null) {
                String type = chambre.getTypeC().name();
                disponiblesParType.put(type, disponiblesParType.get(type) + 1);
            }
        }

        // Compter toutes les chambres par type
        for (Chambre chambre : toutesChambres) {
            if (chambre.getTypeC() != null) {
                String type = chambre.getTypeC().name();
                totalParType.put(type, totalParType.get(type) + 1);
            }
        }

        statistiques.put("disponiblesParType", disponiblesParType);
        statistiques.put("totalParType", totalParType);

        return statistiques;
    }


}