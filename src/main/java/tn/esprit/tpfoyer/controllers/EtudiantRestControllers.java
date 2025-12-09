package tn.esprit.tpfoyer.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.entities.Etudiant;
import tn.esprit.tpfoyer.services.IEtudiantService;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Gestion des Étudiants", description = "API pour la gestion des étudiants")
@RestController
@RequestMapping("/etudiant")
@AllArgsConstructor
public class EtudiantRestControllers {

    private IEtudiantService etudiantServices;

    @Operation(summary = "Ajouter un étudiant", description = "Créer un nouvel étudiant")
    @PostMapping("/addEtudiant")
    Etudiant addEtudiant(@RequestBody Etudiant etudiant) {
        return etudiantServices.addEtudiants(List.of(etudiant)).get(0);
    }

    @Operation(summary = "Ajouter plusieurs étudiants", description = "Créer plusieurs étudiants en une seule requête")
    @PostMapping("/addEtudiants")
    List<Etudiant> addEtudiants(@RequestBody List<Etudiant> etudiants) {
        return etudiantServices.addEtudiants(etudiants);
    }

    @Operation(summary = "Liste tous les étudiants", description = "Récupère la liste de tous les étudiants")
    @GetMapping("/getAllEtudiants")
    List<Etudiant> getAllEtudiants(){
        return etudiantServices.retrieveAllEtudiants();
    }

    @Operation(summary = "Récupérer un étudiant par ID", description = "Obtenir les détails d'un étudiant spécifique")
    @GetMapping("/getEtudiantByID/{idE}")
    Etudiant getEtudiantByID(@PathVariable("idE") long idEtudiant){
        return etudiantServices.retrieveEtudiant(idEtudiant);
    }

    @Operation(summary = "Modifier un étudiant", description = "Mettre à jour les informations d'un étudiant")
    @PutMapping("/updateEtudiant")
    Etudiant updateEtudiant(@RequestBody Etudiant etudiant) {
        return etudiantServices.updateEtudiant(etudiant);
    }

    @Operation(summary = "Supprimer un étudiant", description = "Supprimer un étudiant par son ID")
    @DeleteMapping("/deleteEtudiant/{idE}")
    void deleteEtudiant(@PathVariable("idE") long idEtudiant){
        etudiantServices.removeEtudiant(idEtudiant);
    }


    @Operation(summary = "Lister les étudiants avec réservation valide par année",
            description = "Retourne la liste des étudiants ayant au moins une réservation validée (estValide = true) durant une année universitaire donnée")
    @GetMapping("/getEtudiantsAvecReservationValidePourAnnee/{annee}")
    public ResponseEntity<Map<String, Object>> getEtudiantsAvecReservationValidePourAnnee(
            @PathVariable("annee") int annee) {

        try {
            // Appeler le service
            List<Etudiant> etudiants = etudiantServices.getEtudiantsAvecReservationValidePourAnnee(annee);

            // Préparer la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("annee", annee);
            response.put("nombreEtudiants", etudiants.size());

            // Formater la liste des étudiants (éviter les relations circulaires)
            List<Map<String, Object>> etudiantsFormatted = etudiants.stream()
                    .map(e -> {
                        Map<String, Object> etudiantInfo = new HashMap<>();
                        etudiantInfo.put("idEtudiant", e.getIdEtudiant());
                        etudiantInfo.put("nomComplet", e.getNomEt() + " " + e.getPrenomEt());
                        etudiantInfo.put("cin", e.getCin());
                        etudiantInfo.put("ecole", e.getEcole());
                        etudiantInfo.put("dateNaissance", e.getDateNaissance());

                        // Compter le nombre de réservations valides pour cette année
                        long nbReservationsValides = e.getReservations() != null ?
                                e.getReservations().stream()
                                        .filter(r -> r.getEstValide() != null && r.getEstValide() &&
                                                r.getAnneeUniversitaire() != null &&
                                                r.getAnneeUniversitaire().getYear() + 1900 == annee)
                                        .count() : 0;

                        etudiantInfo.put("nombreReservationsValides", nbReservationsValides);
                        return etudiantInfo;
                    })
                    .toList();

            response.put("etudiants", etudiantsFormatted);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            // Gestion des erreurs
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("annee", annee);
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @Operation(summary = "Lister les étudiants sans réservation",
            description = "Retourne la liste de tous les étudiants qui n'ont aucune réservation enregistrée")
    @GetMapping("/getEtudiantsSansReservation")
    public ResponseEntity<Map<String, Object>> getEtudiantsSansReservation() {

        try {
            // Appeler le service
            List<Etudiant> etudiants = etudiantServices.getEtudiantsSansReservation();

            // Préparer la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Liste des étudiants sans réservation récupérée avec succès");
            response.put("nombreEtudiants", etudiants.size());

            // Formater la liste des étudiants
            List<Map<String, Object>> etudiantsFormatted = etudiants.stream()
                    .map(e -> {
                        Map<String, Object> etudiantInfo = new HashMap<>();
                        etudiantInfo.put("idEtudiant", e.getIdEtudiant());
                        etudiantInfo.put("nomComplet", e.getNomEt() + " " + e.getPrenomEt());
                        etudiantInfo.put("cin", e.getCin());
                        etudiantInfo.put("ecole", e.getEcole());
                        etudiantInfo.put("dateNaissance", e.getDateNaissance());

                        // Calculer l'âge si la date de naissance est disponible
                        if (e.getDateNaissance() != null) {
                            int age = etudiantServices.calculerAge(e);
                            etudiantInfo.put("age", age);
                        }

                        return etudiantInfo;
                    })
                    .toList();

            response.put("etudiants", etudiantsFormatted);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            // Gestion des erreurs
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}