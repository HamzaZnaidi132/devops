package tn.esprit.tpfoyer.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.entities.Chambre;
import tn.esprit.tpfoyer.entities.TypeChambre;
import tn.esprit.tpfoyer.services.IChambreService;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Gestion des Chambres", description = "API pour la gestion des chambres dans les blocs")
@RestController
@RequestMapping("/chambre")
@AllArgsConstructor
public class ChambreRestControllers {

    private IChambreService chambreServices;


    @Operation(summary = "Ajouter une chambre", description = "Créer une nouvelle chambre dans un bloc")
    @PostMapping("/addChambre")
    Chambre addChambre(@RequestBody Chambre chambre) {
        return chambreServices.addChambre(chambre);
    }

    @Operation(summary = "Liste toutes les chambres", description = "Récupère la liste de toutes les chambres")
    @GetMapping("/getAllChambres")
    List<Chambre> getAllChambres(){
        return chambreServices.retrieveAllChambres();
    }

    @Operation(summary = "Récupérer une chambre par ID", description = "Obtenir les détails d'une chambre spécifique par son identifiant")
    @GetMapping("/getChambreByID/{idC}")
    Chambre getChambreByID(@PathVariable("idC") long idChambre){
        return chambreServices.retrieveChambre(idChambre);
    }

    @Operation(summary = "Modifier une chambre", description = "Mettre à jour les informations d'une chambre existante")
    @PutMapping("/updateChambre")
    Chambre updateChambre(@RequestBody Chambre chambre) {
        return chambreServices.updateChambre(chambre);
    }

    // Services Avancées
    @Operation(summary = "Récupérer les chambres par nom d'université",
            description = "Obtenir la liste de toutes les chambres d'une université spécifique par son nom")
    @GetMapping("/getChambresParUniversite/{nomUniversite}")
    public List<Chambre> getChambresParNomUniversite(@PathVariable("nomUniversite") String nomUniversite) {
        return chambreServices.getChambresParNomUniversite(nomUniversite);
    }

    @Operation(summary = "Récupérer les chambres par bloc et type (Keywords)",
            description = "Obtenir la liste des chambres d'un bloc selon leur type en utilisant les keywords Spring Data")
    @GetMapping("/getChambresParBlocEtType/{idBloc}/{typeC}")
    public List<Chambre> getChambresParBlocEtType(
            @PathVariable("idBloc") long idBloc,
            @PathVariable("typeC") TypeChambre typeC) {
        return chambreServices.getChambresParBlocEtType(idBloc, typeC);
    }

    @Operation(summary = "Récupérer les chambres par bloc et type (JPQL)",
            description = "Obtenir la liste des chambres d'un bloc selon leur type en utilisant JPQL")
    @GetMapping("/getChambresParBlocEtTypeJPQL/{idBloc}/{typeC}")
    public List<Chambre> getChambresParBlocEtTypeJPQL(
            @PathVariable("idBloc") long idBloc,
            @PathVariable("typeC") TypeChambre typeC) {
        return chambreServices.getChambresParBlocEtTypeAvecJPQL(idBloc, typeC);
    }

    @Operation(summary = "Récupérer les chambres non réservées par nom d'université et type de chambre",
            description = "Obtenir la liste des chambres non réservées pour une université et un type de chambre donné pour l'année en cours")
    @GetMapping("/getChambresNonReserveParNomUniversiteEtTypeChambre/{nomUniversite}/{type}")
    public List<Chambre> getChambresNonReserveParNomUniversiteEtTypeChambre(
            @PathVariable("nomUniversite") String nomUniversite,
            @PathVariable("type") TypeChambre type) {
        return chambreServices.getChambresNonReserveParNomUniversiteEtTypeChambre(nomUniversite, type);
    }
    // Dans ReservationRestControllers.java ou un nouveau contrôleur
    @Operation(summary = "Trouver la chambre d'un étudiant par CIN",
            description = "Récupère la chambre occupée par un étudiant selon son CIN")
    @GetMapping("/getChambreByCinEtudiant/{cinEtudiant}")
    public ResponseEntity<Map<String, Object>> getChambreByCinEtudiant(
            @PathVariable("cinEtudiant") long cinEtudiant) {

        try {
            Chambre chambre = chambreServices.getChambreByCinEtudiant(cinEtudiant);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cinEtudiant", cinEtudiant);

            Map<String, Object> chambreInfo = new HashMap<>();
            chambreInfo.put("idChambre", chambre.getIdChambre());
            chambreInfo.put("numeroChambre", chambre.getNumeroChambre());
            chambreInfo.put("typeChambre", chambre.getTypeC());

            if (chambre.getBloc() != null) {
                Map<String, Object> blocInfo = new HashMap<>();
                blocInfo.put("idBloc", chambre.getBloc().getIdBloc());
                blocInfo.put("nomBloc", chambre.getBloc().getNomBloc());
                chambreInfo.put("bloc", blocInfo);
            }

            response.put("chambre", chambreInfo);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @Operation(summary = "Compter les chambres par type pour une université",
            description = "Retourne le nombre total de chambres groupées par type (SIMPLE, DOUBLE, TRIPLE) pour une université donnée")
    @GetMapping("/countChambresParTypePourUniversite/{nomUniversite}")
    public ResponseEntity<Map<String, Object>> countChambresParTypePourUniversite(
            @PathVariable("nomUniversite") String nomUniversite) {

        try {
            // Appeler le service
            Map<TypeChambre, Long> statistiques = chambreServices.countChambresParTypePourUniversite(nomUniversite);

            // Calculer le total
            Long totalChambres = statistiques.values().stream()
                    .mapToLong(Long::longValue)
                    .sum();

            // Préparer la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("universite", nomUniversite);
            response.put("totalChambres", totalChambres);

            // Convertir la Map<TypeChambre, Long> en Map<String, Long> pour une meilleure sérialisation JSON
            Map<String, Long> statistiquesString = new HashMap<>();
            for (Map.Entry<TypeChambre, Long> entry : statistiques.entrySet()) {
                statistiquesString.put(entry.getKey().name(), entry.getValue());
            }

            response.put("statistiquesParType", statistiquesString);

            // Ajouter des pourcentages
            if (totalChambres > 0) {
                Map<String, Double> pourcentages = new HashMap<>();
                for (Map.Entry<TypeChambre, Long> entry : statistiques.entrySet()) {
                    double pourcentage = (entry.getValue() * 100.0) / totalChambres;
                    pourcentages.put(entry.getKey().name(), Math.round(pourcentage * 100.0) / 100.0);
                }
                response.put("pourcentages", pourcentages);
            }

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            // Gestion des erreurs
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("universite", nomUniversite);
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    @Operation(summary = "Récupérer toutes les chambres disponibles",
            description = "Retourne la liste de toutes les chambres qui n'ont aucune réservation valide (estValide = true)")
    @GetMapping("/getAllChambresDisponibles")
    public ResponseEntity<Map<String, Object>> getAllChambresDisponibles() {

        try {
            // Appeler le service pour obtenir les chambres disponibles
            List<Chambre> chambresDisponibles = chambreServices.getAllChambresDisponibles();

            // Préparer la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Liste des chambres disponibles récupérée avec succès");
            response.put("nombreChambresDisponibles", chambresDisponibles.size());

            // Formater la liste des chambres avec leurs informations
            List<Map<String, Object>> chambresFormatted = chambresDisponibles.stream()
                    .map(c -> {
                        Map<String, Object> chambreInfo = new HashMap<>();
                        chambreInfo.put("idChambre", c.getIdChambre());
                        chambreInfo.put("numeroChambre", c.getNumeroChambre());
                        chambreInfo.put("typeChambre", c.getTypeC() != null ? c.getTypeC().name() : "NON_DEFINI");

                        // Informations sur le bloc
                        if (c.getBloc() != null) {
                            Map<String, Object> blocInfo = new HashMap<>();
                            blocInfo.put("idBloc", c.getBloc().getIdBloc());
                            blocInfo.put("nomBloc", c.getBloc().getNomBloc());
                            blocInfo.put("capaciteBloc", c.getBloc().getCapaciteBloc());
                            chambreInfo.put("bloc", blocInfo);

                            // Informations sur le foyer
                            if (c.getBloc().getFoyer() != null) {
                                Map<String, Object> foyerInfo = new HashMap<>();
                                foyerInfo.put("idFoyer", c.getBloc().getFoyer().getIdFoyer());
                                foyerInfo.put("nomFoyer", c.getBloc().getFoyer().getNomFoyer());
                                blocInfo.put("foyer", foyerInfo);

                                // Informations sur l'université
                                if (c.getBloc().getFoyer().getUniversite() != null) {
                                    Map<String, Object> universiteInfo = new HashMap<>();
                                    universiteInfo.put("idUniversite", c.getBloc().getFoyer().getUniversite().getIdUniversite());
                                    universiteInfo.put("nomUniversite", c.getBloc().getFoyer().getUniversite().getNomUniversite());
                                    foyerInfo.put("universite", universiteInfo);
                                }
                            }
                        }

                        return chambreInfo;
                    })
                    .toList();

            response.put("chambres", chambresFormatted);

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