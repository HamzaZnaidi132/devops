package tn.esprit.tpfoyer.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.entities.Bloc;
import tn.esprit.tpfoyer.entities.Foyer;
import tn.esprit.tpfoyer.services.IFoyerService;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Gestion des Foyers", description = "API pour la gestion des foyers universitaires")
@RestController
@RequestMapping("/foyer")
@AllArgsConstructor
public class FoyerRestControllers {

    private IFoyerService foyerServices;

    @Operation(summary = "Ajouter un foyer", description = "Créer un nouveau foyer universitaire")
    @PostMapping("/addFoyer")
    Foyer addFoyer(@RequestBody Foyer foyer) {
        return foyerServices.addFoyer(foyer);
    }

    @Operation(summary = "Liste tous les foyers", description = "Récupère la liste de tous les foyers universitaires")
    @GetMapping("/getAllFoyers")
    List<Foyer> getAllFoyers(){
        return foyerServices.retrieveAllFoyers();
    }

    @Operation(summary = "Récupérer un foyer par ID", description = "Obtenir les détails d'un foyer spécifique par son identifiant")
    @GetMapping("/getFoyerByID/{idF}")
    Foyer getFoyerByID(@PathVariable("idF") long idFoyer){
        return foyerServices.retrieveFoyer(idFoyer);
    }

    @Operation(summary = "Modifier un foyer", description = "Mettre à jour les informations d'un foyer existant")
    @PutMapping("/updateFoyer")
    Foyer updateFoyer(@RequestBody Foyer foyer) {
        return foyerServices.updateFoyer(foyer);
    }

    @Operation(summary = "Supprimer un foyer", description = "Supprimer un foyer par son identifiant")
    @DeleteMapping("/deleteFoyer/{idF}")
    void deleteFoyer(@PathVariable("idF") long idFoyer){
        foyerServices.removeFoyer(idFoyer);
    }

    // Services Avancées
    @Operation(summary = "Ajouter un foyer avec ses blocs et l'affecter à une université",
            description = "Crée un nouveau foyer avec tous ses blocs associés et l'affecte à une université spécifique")
    @PostMapping("/ajouterFoyerEtAffecterAUniversite/{idUniversite}")
    public Foyer ajouterFoyerEtAffecterAUniversite(
            @RequestBody Foyer foyer,
            @PathVariable("idUniversite") long idUniversite) {
        return foyerServices.ajouterFoyerEtAffecterAUniversite(foyer, idUniversite);
    }

    @Operation(summary = "Trouver le foyer avec le plus grand nombre de chambres ",
            description = "Retourne uniquement les informations essentielles du foyer avec le plus grand nombre de chambres")
    @GetMapping("/getFoyerWithMaxChambres/simple")
    public ResponseEntity<Map<String, Object>> getFoyerWithMaxChambresSimple() {

        try {
            // Appeler le service pour obtenir le foyer
            Foyer foyer = foyerServices.getFoyerWithMaxChambres();

            // Calculer le nombre total de chambres
            long totalChambres = 0;
            if (foyer.getBlocs() != null) {
                for (Bloc bloc : foyer.getBlocs()) {
                    if (bloc.getChambres() != null) {
                        totalChambres += bloc.getChambres().size();
                    }
                }
            }

            // Préparer la réponse simplifiée
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("idFoyer", foyer.getIdFoyer());
            response.put("nomFoyer", foyer.getNomFoyer());
            response.put("capaciteFoyer", foyer.getCapaciteFoyer());
            response.put("totalChambres", totalChambres);
            response.put("nombreBlocs", foyer.getBlocs() != null ? foyer.getBlocs().size() : 0);

            // Ajouter le nom de l'université si disponible
            if (foyer.getUniversite() != null) {
                response.put("universite", foyer.getUniversite().getNomUniversite());
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
}