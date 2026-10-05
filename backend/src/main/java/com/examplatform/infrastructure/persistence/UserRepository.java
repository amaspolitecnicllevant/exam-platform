package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    /** Sense distingir majúscules (els correus es desen en minúscules). */
    @Query("SELECT u FROM User u WHERE u.email = lower(trim(:email))")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = lower(trim(:email))")
    boolean existsByEmail(@Param("email") String email);
    List<User> findByRole(Role role);
}
