package tn.esprit.tpfoyer.repositories;

import org.springframework.data.jpa.repository.Query;
import tn.esprit.tpfoyer.entities.Foyer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FoyerRepository extends JpaRepository<Foyer, Long> {
    @Query("SELECT f FROM Foyer f " +
            "WHERE f.idFoyer = (" +
            "  SELECT f2.idFoyer FROM Foyer f2 " +
            "  LEFT JOIN f2.blocs b2 " +
            "  LEFT JOIN b2.chambres c2 " +
            "  GROUP BY f2.idFoyer " +
            "  ORDER BY COUNT(c2) DESC " +
            "  LIMIT 1" +
            ")")
    Optional<Foyer> getFoyerWithMaxChambres();
}