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
public class Chambre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idChambre;

    private Long numeroChambre;

    @Enumerated(EnumType.STRING)
    private TypeChambre typeC;

    // Relation ManyToOne avec Bloc
    @ManyToOne
    private Bloc bloc;

    // Relation OneToMany avec Reservation
    @OneToMany( cascade = CascadeType.ALL)
    private Set<Reservation> reservations;
}