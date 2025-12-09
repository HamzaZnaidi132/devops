package tn.esprit.tpfoyer.services;

import tn.esprit.tpfoyer.entities.Foyer;
import java.util.List;

public interface IFoyerService {
    List<Foyer> retrieveAllFoyers();
    Foyer addFoyer(Foyer f);
    Foyer updateFoyer(Foyer f);
    Foyer retrieveFoyer(long idFoyer);
    void removeFoyer(long idFoyer);

    // Services Avancées
    Foyer ajouterFoyerEtAffecterAUniversite(Foyer foyer, long idUniversite);

    Foyer getFoyerWithMaxChambres();

}
