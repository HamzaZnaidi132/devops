package tn.esprit.tpfoyer.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.entities.Chambre;
import tn.esprit.tpfoyer.entities.Etudiant;
import tn.esprit.tpfoyer.entities.Reservation;
import tn.esprit.tpfoyer.entities.TypeChambre;
import tn.esprit.tpfoyer.repositories.ChambreRepository;
import tn.esprit.tpfoyer.services.IReservationService;

import java.util.*;

@Tag(name = "Gestion des Réservations", description = "API pour la gestion des réservations de chambres")
@RestController
@RequestMapping("/reservation")
@AllArgsConstructor
public class ReservationRestControllers {

    private IReservationService reservationServices;
    private ChambreRepository chambreRepository;


    @Operation(summary = "Liste toutes les réservations", description = "Récupère la liste de toutes les réservations")
    @GetMapping("/getAllReservations")
    List<Reservation> getAllReservations(){
        return reservationServices.retrieveAllReservation();
    }

    @Operation(summary = "Récupérer une réservation par ID", description = "Obtenir les détails d'une réservation spécifique par son identifiant")
    @GetMapping("/getReservationByID/{idR}")
    Reservation getReservationByID(@PathVariable("idR") String idReservation){
        return reservationServices.retrieveReservation(idReservation);
    }

    @Operation(summary = "Modifier une réservation", description = "Mettre à jour les informations d'une réservation existante")
    @PutMapping("/updateReservation")
    Reservation updateReservation(@RequestBody Reservation reservation) {
        return reservationServices.updateReservation(reservation);
    }

    // Services Avancées
    @Operation(summary = "Ajouter une réservation",
            description = "Ajouter une réservation pour un étudiant dans une chambre avec validation de capacité. Format du numéro: numChambre-nomBloc-anneeUniversitaire")
    @PostMapping("/ajouterReservation/{idChambre}/{cinEtudiant}")
    public ResponseEntity<Map<String, Object>> ajouterReservation(
            @PathVariable("idChambre") long idChambre,
            @PathVariable("cinEtudiant") long cinEtudiant) {

        try {
            // Appeler le service normalement
            Reservation reservation = reservationServices.ajouterReservation(idChambre, cinEtudiant);

            // Créer une réponse simplifiée manuellement
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Réservation créée avec succès");
            response.put("idReservation", reservation.getIdReservation());
            response.put("anneeUniversitaire", reservation.getAnneeUniversitaire());
            response.put("estValide", reservation.getEstValide());

            // Ajouter des informations basiques sur l'étudiant (sans les relations)
            if (!reservation.getEtudiants().isEmpty()) {
                Etudiant etudiant = reservation.getEtudiants().iterator().next();
                Map<String, Object> etudiantInfo = new HashMap<>();
                etudiantInfo.put("idEtudiant", etudiant.getIdEtudiant());
                etudiantInfo.put("nomComplet", etudiant.getNomEt() + " " + etudiant.getPrenomEt());
                etudiantInfo.put("cin", etudiant.getCin());
                response.put("etudiant", etudiantInfo);
            }

            // Ajouter des informations basiques sur la chambre (sans les relations)
            Chambre chambre = trouverChambreParReservation(reservation);
            if (chambre != null) {
                Map<String, Object> chambreInfo = new HashMap<>();
                chambreInfo.put("idChambre", chambre.getIdChambre());
                chambreInfo.put("numeroChambre", chambre.getNumeroChambre());
                chambreInfo.put("typeChambre", chambre.getTypeC());
                response.put("chambre", chambreInfo);
            }

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Méthode utilitaire pour trouver la chambre associée à une réservation
    private Chambre trouverChambreParReservation(Reservation reservation) {
        // Cette méthode existe déjà dans votre service, vous pouvez la réutiliser
        // ou appeler le service pour la récupérer
        List<Chambre> toutesChambres = chambreRepository.findAll();

        for (Chambre chambre : toutesChambres) {
            if (chambre.getReservations() != null && chambre.getReservations().contains(reservation)) {
                return chambre;
            }
        }
        return null;
    }

    @Operation(summary = "Annuler une réservation par CIN d'étudiant",
            description = "Annule la réservation valide d'un étudiant, désaffecte l'étudiant et la chambre")
    @PutMapping("/annulerReservationParCin/{cinEtudiant}")
    public Reservation annulerReservation(@PathVariable("cinEtudiant") long cinEtudiant) {
        return reservationServices.annulerReservation(cinEtudiant);
    }

    @Operation(summary = "Récupérer les réservations par année universitaire et nom d'université",
            description = "Obtenir la liste des réservations pour une année universitaire et une université spécifique")
    @GetMapping("/getReservationParAnneeUniversitaireEtNomUniversite")
    public List<Reservation> getReservationParAnneeUniversitaireEtNomUniversite(
            @RequestParam("anneeUniversitaire") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date anneeUniversitaire,
            @RequestParam("nomUniversite") String nomUniversite) {
        return reservationServices.getReservationParAnneeUniversitaireEtNomUniversite(anneeUniversitaire, nomUniversite);
    }


}