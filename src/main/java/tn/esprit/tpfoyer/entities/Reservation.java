package tn.esprit.tpfoyer.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation  {
    @Id
    private String idReservation;

    private Date anneeUniversitaire;
    private Boolean estValide;



    // Relation ManyToMany avec Etudiant (Reservation est le parent)
    @ManyToMany(cascade = CascadeType.ALL)
    private Set<Etudiant> etudiants;


}