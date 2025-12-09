package tn.esprit.tpfoyer.services;

import tn.esprit.tpfoyer.entities.Etudiant;
import java.util.List;

public interface IEtudiantService {
    List<Etudiant> retrieveAllEtudiants();
    List<Etudiant> addEtudiants(List<Etudiant> etudiants);
    Etudiant updateEtudiant(Etudiant e);
    Etudiant retrieveEtudiant(long idEtudiant);
    void removeEtudiant(long idEtudiant);

    List<Etudiant> getEtudiantsAvecReservationValidePourAnnee(int annee);

    List<Etudiant> getEtudiantsSansReservation();

    int calculerAge(Etudiant e);
}