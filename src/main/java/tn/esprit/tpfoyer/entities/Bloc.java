package tn.esprit.tpfoyer.entities;

import jakarta.persistence.*;
import lombok.*;


import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bloc  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idBloc;

    private String nomBloc;
    private Long capaciteBloc;

    // Relation ManyToOne avec Foyer
    @ManyToOne
    private Foyer foyer;

    // Relation OneToMany avec Chambre
    @OneToMany(mappedBy = "bloc", cascade = CascadeType.ALL)
    private Set<Chambre> chambres;
}