package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
