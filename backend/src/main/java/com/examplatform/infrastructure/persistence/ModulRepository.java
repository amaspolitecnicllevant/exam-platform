package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Modul;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModulRepository extends JpaRepository<Modul, UUID> {
    List<Modul> findByCicleIdOrderByCodiAsc(UUID cicleId);
    Optional<Modul> findByCodi(String codi);

    @Query("SELECT m FROM Modul m WHERE m.cicle.departament.id = :departamentId ORDER BY m.codi")
    List<Modul> findByDepartamentId(@Param("departamentId") UUID departamentId);
}
