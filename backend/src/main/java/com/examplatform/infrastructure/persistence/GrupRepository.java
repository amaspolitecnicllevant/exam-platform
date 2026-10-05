package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Grup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupRepository extends JpaRepository<Grup, UUID> {

    @Query("SELECT g FROM Grup g LEFT JOIN FETCH g.students WHERE g.createdBy.id = :userId")
    List<Grup> findByCreatedByIdWithStudents(@Param("userId") UUID userId);

    @Query("SELECT g FROM Grup g LEFT JOIN FETCH g.students")
    List<Grup> findAllWithStudents();

    @Query("SELECT g FROM Grup g LEFT JOIN FETCH g.students WHERE g.id = :id")
    Optional<Grup> findByIdWithStudents(@Param("id") UUID id);

    List<Grup> findByModulId(UUID modulId);

    boolean existsByCreatedById(UUID userId);

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM Grup g JOIN g.students st WHERE g.id = :grupId AND st.id = :alumneId")
    boolean teAlumne(@Param("grupId") UUID grupId, @Param("alumneId") UUID alumneId);
}
