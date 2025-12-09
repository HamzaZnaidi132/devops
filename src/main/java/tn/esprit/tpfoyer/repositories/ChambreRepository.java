package tn.esprit.tpfoyer.repositories;


import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.tpfoyer.entities.Chambre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.tpfoyer.entities.TypeChambre;

import java.util.List;
import java.util.Optional;


@Repository
public interface ChambreRepository extends JpaRepository<Chambre, Long> {

    Optional<Chambre> findByNumeroChambre(Long numeroChambre);

    List<Chambre> findByBlocIdBlocAndTypeC(Long idBloc, TypeChambre typeC);

    @Query("SELECT c FROM Chambre c WHERE c.bloc.idBloc = :idBloc AND c.typeC = :typeC")
    List<Chambre> findChambresByBlocAndType(@Param("idBloc") Long idBloc, @Param("typeC") TypeChambre typeC);

    @Query("SELECT c FROM Chambre c " +
            "JOIN c.bloc b " +
            "JOIN b.foyer f " +
            "JOIN f.universite u " +
            "WHERE u.nomUniversite = :nomUniversite " +
            "AND c.typeC = :type " +
            "AND c NOT IN (" +
            "  SELECT ch FROM Chambre ch " +
            "  JOIN ch.reservations r " +
            "  WHERE FUNCTION('YEAR', r.anneeUniversitaire) = :anneeActuelle " +
            "  AND r.estValide = true" +
            ")")
    List<Chambre> findChambresNonReserveesParNomUniversiteEtType(
            @Param("nomUniversite") String nomUniversite,
            @Param("type") TypeChambre type,
            @Param("anneeActuelle") int anneeActuelle);

    @Query("SELECT DISTINCT c FROM Chambre c " +
            "JOIN c.reservations r " +
            "JOIN r.etudiants e " +
            "WHERE e.cin = :cin AND r.estValide = true")
    List<Chambre> findChambreByCinEtudiant(@Param("cin") long cin);

    @Query("SELECT c.typeC, COUNT(c) FROM Chambre c " +
            "JOIN c.bloc b " +
            "JOIN b.foyer f " +
            "JOIN f.universite u " +
            "WHERE u.nomUniversite = :nomUniversite " +
            "GROUP BY c.typeC")
    List<Object[]> countChambresParTypePourUniversite(@Param("nomUniversite") String nomUniversite);

    @Query("SELECT c FROM Chambre c " +
            "WHERE c.idChambre NOT IN (" +
            "  SELECT DISTINCT ch.idChambre FROM Chambre ch " +
            "  JOIN ch.reservations r " +
            "  WHERE r.estValide = true" +
            ")")
    List<Chambre> findAllChambresDisponibles();

}