package tn.esprit.tpfoyer.repositories;


import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.tpfoyer.entities.Etudiant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {
    Optional<Etudiant> findByCin(Long cin);

    @Query("SELECT DISTINCT e FROM Etudiant e " +
            "JOIN e.reservations r " +
            "WHERE r.estValide = true " +
            "AND FUNCTION('YEAR', r.anneeUniversitaire) = :annee")
    List<Etudiant> findEtudiantsByReservationValideAndAnnee(@Param("annee") int annee);

    @Query("SELECT e FROM Etudiant e " +
            "WHERE NOT EXISTS (" +
            "  SELECT 1 FROM Reservation r " +
            "  JOIN r.etudiants et " +
            "  WHERE et.idEtudiant = e.idEtudiant" +
            ")")
    List<Etudiant> findEtudiantsSansReservation();

}