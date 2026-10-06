package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatriculaRepository extends JpaRepository<Matricula, UUID> {

    List<Matricula> findByAlumneId(UUID alumneId);

    List<Matricula> findByModulIdAndCurs(UUID modulId, String curs);

    boolean existsByAlumneIdAndModulIdAndCurs(UUID alumneId, UUID modulId, String curs);

    @Query("SELECT COUNT(m) > 0 FROM Matricula m WHERE m.alumne.id = :alumneId AND m.modul.id = :modulId AND m.curs = :curs")
    boolean alumneMatriculatAModul(@Param("alumneId") UUID alumneId,
                                   @Param("modulId") UUID modulId,
                                   @Param("curs") String curs);

    boolean existsByAlumneIdAndModulId(UUID alumneId, UUID modulId);

    @Query("SELECT DISTINCT m.alumne.id FROM Matricula m WHERE m.modul.id = :modulId")
    List<UUID> findAlumneIdsByModulId(@Param("modulId") UUID modulId);

    /** Totes les matrícules, amb alumne i mòdul carregats en una sola consulta. */
    @Query("SELECT m FROM Matricula m JOIN FETCH m.alumne a JOIN FETCH m.modul mo "
            + "ORDER BY m.curs DESC, mo.codi, a.name")
    List<Matricula> findAllAmbDetall();

    /** Les matrícules d'un conjunt de mòduls, amb alumne i mòdul carregats en una sola consulta. */
    @Query("SELECT m FROM Matricula m JOIN FETCH m.alumne a JOIN FETCH m.modul mo "
            + "WHERE mo.id IN :modulIds ORDER BY m.curs DESC, mo.codi, a.name")
    List<Matricula> findByModulIdsAmbDetall(@Param("modulIds") Collection<UUID> modulIds);
}
