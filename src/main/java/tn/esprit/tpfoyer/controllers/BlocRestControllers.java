package tn.esprit.tpfoyer.controllers;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.entities.Bloc;

import tn.esprit.tpfoyer.services.IBlocService;

import java.util.List;

@Tag(name = "Gestion des Blocs", description = "API pour la gestion des blocs dans les foyers")
@RestController
@RequestMapping("/bloc")
@AllArgsConstructor
public class BlocRestControllers {

    private IBlocService blocServices;

    @Operation(summary = "Ajouter un bloc", description = "Créer un nouveau bloc dans un foyer")
    @PostMapping("/addBloc")
    Bloc addBloc(@RequestBody Bloc bloc) {
        return blocServices.addBloc(bloc);
    }

    @Operation(summary = "Liste tous les blocs", description = "Récupère la liste de tous les blocs")
    @GetMapping("/getAllBlocs")
    List<Bloc> getAllBlocs(){
        return blocServices.retrieveBlocs();
    }

    @Operation(summary = "Récupérer un bloc par ID", description = "Obtenir les détails d'un bloc spécifique par son identifiant")
    @GetMapping("/getBlocByID/{idB}")
    Bloc getBlocByID(@PathVariable("idB") long idBloc){
        return blocServices.retrieveBloc(idBloc);
    }

    @Operation(summary = "Modifier un bloc", description = "Mettre à jour les informations d'un bloc existant")
    @PutMapping("/updateBloc")
    Bloc updateBloc(@RequestBody Bloc bloc) {
        return blocServices.updateBloc(bloc);
    }

    @Operation(summary = "Supprimer un bloc", description = "Supprimer un bloc par son identifiant")
    @DeleteMapping("/deleteBloc/{idB}")
    void deleteBloc(@PathVariable("idB") long idBloc){
        blocServices.removeBloc(idBloc);
    }

    // Services Avancées
    @Operation(summary = "Affecter des chambres à un bloc",
            description = "Affecte une liste de chambres (par leurs numéros) à un bloc spécifique")
    @PutMapping("/affecterChambres/{idBloc}")
    public Bloc affecterChambresABloc(
            @RequestBody List<Long> numChambre,
            @PathVariable("idBloc") long idBloc) {
        return blocServices.affecterChambresABloc(numChambre, idBloc);
    }
}