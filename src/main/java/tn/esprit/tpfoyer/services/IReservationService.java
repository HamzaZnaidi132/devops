package tn.esprit.tpfoyer.services;

import tn.esprit.tpfoyer.entities.Reservation;


import java.util.Date;
import java.util.List;

public interface IReservationService {
    List<Reservation> retrieveAllReservation();
    Reservation updateReservation(Reservation res);
    Reservation retrieveReservation(String idReservation);

    // Services Avancées
    Reservation ajouterReservation(long idChambre, long cinEtudiant);
    Reservation annulerReservation(long cinEtudiant);
    List<Reservation> getReservationParAnneeUniversitaireEtNomUniversite(Date anneeUniversite, String nomUniversite);
    List<Reservation> getReservationParAnneeEtNomUniversite(int annee, String nomUniversite);
}