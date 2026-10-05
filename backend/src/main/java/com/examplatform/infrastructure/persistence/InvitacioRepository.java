package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Invitacio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitacioRepository extends JpaRepository<Invitacio, UUID> {

    Optional<Invitacio> findByToken(UUID token);

    List<Invitacio> findByModulIdAndCursOrderByCreatedAtDesc(UUID modulId, String curs);

    List<Invitacio> findByCreatedByIdOrderByCreatedAtDesc(UUID professorId);
}
