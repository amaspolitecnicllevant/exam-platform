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
        comprovaLongitud(req.role(), req.password());
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

    /** Llargada mínima de la contrasenya: més exigent per al professorat i l'administració, que tenen més accés. */
    public static int minimLongitud(Role rol) {
        return rol == Role.STUDENT ? 8 : 12;
    }

    private static void comprovaLongitud(Role rol, String contrasenya) {
        int min = minimLongitud(rol);
        if (contrasenya == null || contrasenya.length() < min || contrasenya.length() > 72) {
            throw new IllegalArgumentException("La contrasenya ha de tenir entre " + min + " i 72 caràcters");
        }
    }

    /** L'administrador posa una contrasenya nova a un usuari; les sessions que tenia obertes es tanquen. */
    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User user = getEntityById(id);
        comprovaLongitud(user.getRole(), newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    /** Tanca totes les sessions obertes d'un usuari (els seus tokens deixen de ser vàlids). */
    @Transactional
    public void revocaSessions(UUID id) {
        User user = getEntityById(id);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    /**
     * Canvi de contrasenya pel mateix usuari: exigeix l'actual. Els comptes de Google (sense
     * contrasenya local) no en poden canviar.
     */
    @Transactional
    public User canviaContrasenya(UUID userId, String actual, String nova) {
        User user = getEntityById(userId);
        if (user.getPasswordHash() == null)
            throw new IllegalArgumentException("Aquest compte entra amb Google i no té contrasenya pròpia");
        if (actual == null || !passwordEncoder.matches(actual, user.getPasswordHash()))
            throw new IllegalArgumentException("La contrasenya actual no és correcta");
        int min = minimLongitud(user.getRole());
        if (nova == null || nova.length() < min || nova.length() > 72)
            throw new IllegalArgumentException("La contrasenya nova ha de tenir entre " + min + " i 72 caràcters");
        if (nova.equals(actual))
            throw new IllegalArgumentException("La contrasenya nova ha de ser diferent de l'actual");
        user.setPasswordHash(passwordEncoder.encode(nova));
        // Les altres sessions (un token robat, un altre ordinador) es tanquen; qui canvia la contrasenya rep un token nou
        user.setTokenVersion(user.getTokenVersion() + 1);
        return userRepository.save(user);
    }
}
