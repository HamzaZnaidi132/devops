package tn.esprit.tpfoyer.controllers;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.entities.Universite;
import tn.esprit.tpfoyer.services.IUniversiteService;

import java.util.List;

@Tag(name = "Gestion des Universités", description = "API pour la gestion des universités")
@RestController
@RequestMapping("/universite")
@AllArgsConstructor
public class UniversiteRestControllers {

    private IUniversiteService universiteServices;


    @Operation(summary = "Ajouter une université", description = "Créer une nouvelle université")
    @PostMapping("/addUniversite")
    Universite addUniversite(@RequestBody Universite universite) {
        return universiteServices.addUniversite(universite);
    }

    @Operation(summary = "Liste toutes les universités", description = "Récupère la liste de toutes les universités")
    @GetMapping("/getAllUniversites")
    List<Universite> getAllUniversites(){
        return universiteServices.retrieveAllUniversities();
    }

    @Operation(summary = "Récupérer une université par ID", description = "Obtenir les détails d'une université spécifique par son identifiant")
    @GetMapping("/getUniversiteByID/{idU}")
    Universite getUniversiteByID(@PathVariable("idU") long idUniversite){
        return universiteServices.retrieveUniversite(idUniversite);
    }

    @Operation(summary = "Modifier une université", description = "Mettre à jour les informations d'une université existante")
    @PutMapping("/updateUniversite")
    Universite updateUniversite(@RequestBody Universite universite) {
        return universiteServices.updateUniversite(universite);
    }

    // Services Avancées
    @Operation(summary = "Affecter un foyer à une université",
            description = "Affecte un foyer existant à une université par son nom")
    @PutMapping("/affecterFoyer/{idFoyer}/{nomUniversite}")
    public Universite affecterFoyerAUniversite(
            @PathVariable("idFoyer") long idFoyer,
            @PathVariable("nomUniversite") String nomUniversite) {
        return universiteServices.affecterFoyerAUniversite(idFoyer, nomUniversite);
    }

    @Operation(summary = "Désaffecter un foyer d'une université",
            description = "Désaffecte le foyer actuellement affecté à une université")
    @PutMapping("/desaffecterFoyer/{idUniversite}")
    public Universite desaffecterFoyerAUniversite(
            @PathVariable("idUniversite") long idUniversite) {
        return universiteServices.desaffecterFoyerAUniversite(idUniversite);
    }
}