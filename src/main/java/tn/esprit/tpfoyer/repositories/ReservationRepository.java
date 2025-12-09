package tn.esprit.tpfoyer.repositories;



import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.tpfoyer.entities.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;


@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Query("SELECT r FROM Chambre c " +
            "JOIN c.reservations r " +
            "JOIN c.bloc b " +
            "JOIN b.foyer f " +
            "JOIN f.universite u " +
            "WHERE u.nomUniversite = :nomUniversite " +
            "AND YEAR(r.anneeUniversitaire) = YEAR(:anneeUniversitaire)")
    List<Reservation> findByAnneeUniversitaireAndUniversite(
            @Param("anneeUniversitaire") Date anneeUniversitaire,
            @Param("nomUniversite") String nomUniversite);

    @Query("SELECT r FROM Chambre c " +
            "JOIN c.reservations r " +
            "JOIN c.bloc b " +
            "JOIN b.foyer f " +
            "JOIN f.universite u " +
            "WHERE u.nomUniversite = :nomUniversite " +
            "AND FUNCTION('YEAR', r.anneeUniversitaire) = :annee")
    List<Reservation> findByAnneeAndUniversite(
            @Param("annee") int annee,
            @Param("nomUniversite") String nomUniversite);
}