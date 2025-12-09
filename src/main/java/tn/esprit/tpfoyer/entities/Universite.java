package tn.esprit.tpfoyer.entities;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Universite  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUniversite;

    private String nomUniversite;
    private String adresse;

    // Relation OneToOne avec Foyer (Universite est le parent)
    @OneToOne(cascade = CascadeType.ALL)
    private Foyer foyer;
}