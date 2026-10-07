package com.examplatform.domain.service;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.dto.UserDto;
import com.examplatform.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.examplatform.infrastructure.persistence.ExamSessionRepository sessionRepository;
    private final com.examplatform.infrastructure.persistence.ExamRepository examRepository;
    private final com.examplatform.infrastructure.persistence.GrupRepository grupRepository;

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserDto> findByRole(Role role) {
        return userRepository.findByRole(role).stream().map(UserDto::from).toList();
    }

    /** Un professor només pot donar d'alta alumnes; l'admin, qualsevol rol. */
    private static void assertPotCrearRol(User caller, Role role) {
        if (caller.getRole() != Role.ADMIN && role != Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Només un administrador pot crear usuaris amb rol " + role);
        }
    }

    @Transactional
    public UserDto create(UserDto.CreateRequest req, User caller) {
        assertPotCrearRol(caller, req.role());
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Ja existeix un usuari amb l'email: " + req.email());
        }
        User user = User.builder()
                .name(req.name())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(req.role())
                .build();
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id, User caller) {
        if (id.equals(caller.getId())) {
            throw new IllegalStateException("No et pots eliminar a tu mateix");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuari no trobat: " + id));
        // Les sessions són les notes de l'alumne i els exàmens/grups, feina del professor:
        // no s'esborren en cascada (i la BD tampoc ho permet)
        if (sessionRepository.existsByStudentId(id)) {
            throw new IllegalStateException(user.getName() + " té exàmens fets: no es pot eliminar sense perdre'n les notes");
        }
        if (examRepository.existsByCreatedById(id) || grupRepository.existsByCreatedById(id)) {
            throw new IllegalStateException(user.getName() + " té exàmens o grups creats: no es pot eliminar");
        }
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public User getEntityById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuari no trobat: " + id));
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User user = getEntityById(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Canvi de contrasenya pel mateix usuari: exigeix l'actual. Els comptes de Google (sense
     * contrasenya local) no en poden canviar.
     */
    @Transactional
    public void canviaContrasenya(UUID userId, String actual, String nova) {
        User user = getEntityById(userId);
        if (user.getPasswordHash() == null)
            throw new IllegalArgumentException("Aquest compte entra amb Google i no té contrasenya pròpia");
        if (actual == null || !passwordEncoder.matches(actual, user.getPasswordHash()))
            throw new IllegalArgumentException("La contrasenya actual no és correcta");
        if (nova == null || nova.length() < 8 || nova.length() > 72)
            throw new IllegalArgumentException("La contrasenya nova ha de tenir entre 8 i 72 caràcters");
        if (nova.equals(actual))
            throw new IllegalArgumentException("La contrasenya nova ha de ser diferent de l'actual");
        user.setPasswordHash(passwordEncoder.encode(nova));
        userRepository.save(user);
    }
}
