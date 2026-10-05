package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Departament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartamentRepository extends JpaRepository<Departament, UUID> {
    Optional<Departament> findByNom(String nom);
    List<Departament> findAllByOrderByNomAsc();
}
