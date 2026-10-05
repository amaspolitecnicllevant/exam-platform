package com.examplatform.domain.service;

import com.examplatform.config.JwtUtil;
import com.examplatform.domain.model.*;
import com.examplatform.dto.*;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InvitacioService {

    private final InvitacioRepository    invitacioRepository;
    private final ModulRepository        modulRepository;
    private final UserRepository         userRepository;
    private final MatriculaRepository    matriculaRepository;
    private final GrupRepository         grupRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtUtil                jwtUtil;
    private final ImparticioRepository   imparticioRepository;

    private static final int DIES_VALIDESA = 7;
    static final String CREDENCIALS_INCORRECTES =
            "Ja existeix un compte amb aquest correu i la contrasenya no és correcta. Introdueix la "
                    + "contrasenya del teu compte o, si entres amb Google, inicia sessió i torna a obrir l'enllaç";

    // ── Gestió (professor/admin) ──────────────────────────────────────────────

    @Transactional
    public InvitacioDto create(UUID modulId, String curs, Integer maxUses, UUID grupId, User professor) {
        Modul modul = modulRepository.findById(modulId)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + modulId));

        boolean admin = professor.getRole() == Role.ADMIN;
        // Un convit matricula al mòdul (i dona accés als seus exàmens): només qui l'imparteix
        if (!admin && !imparticioRepository.professorImparteixModul(professor.getId(), modulId)) {
            throw new AccessDeniedException("No imparteixes el mòdul " + modul.getCodi());
        }

        Grup grup = null;
        if (grupId != null) {
            grup = grupRepository.findById(grupId)
                    .orElseThrow(() -> new NoSuchElementException("Grup no trobat: " + grupId));
            if (!admin && !grup.getCreatedBy().getId().equals(professor.getId())) {
                throw new AccessDeniedException("El grup " + grup.getName() + " no és teu");
            }
        }

        Invitacio inv = Invitacio.builder()
                .modul(modul)
                .grup(grup)
                .curs(curs)
                .createdBy(professor)
                .expiresAt(LocalDateTime.now().plusDays(DIES_VALIDESA))
                .maxUses(maxUses)
                .build();

        return InvitacioDto.from(invitacioRepository.save(inv));
    }

    @Transactional(readOnly = true)
    public List<InvitacioDto> findByModulAndCurs(UUID modulId, String curs, User requestingUser) {
        List<Invitacio> list = invitacioRepository
                .findByModulIdAndCursOrderByCreatedAtDesc(modulId, curs);
        if (requestingUser.getRole() == Role.ADMIN) return list.stream().map(InvitacioDto::from).toList();
        return list.stream()
                .filter(i -> i.getCreatedBy().getId().equals(requestingUser.getId()))
                .map(InvitacioDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InvitacioDto> findMeves(User professor) {
        return invitacioRepository.findByCreatedByIdOrderByCreatedAtDesc(professor.getId())
                .stream().map(InvitacioDto::from).toList();
    }

    @Transactional
    public void deactivate(UUID invitacioId, User requestingUser) {
        Invitacio inv = invitacioRepository.findById(invitacioId)
                .orElseThrow(() -> new NoSuchElementException("Invitació no trobada: " + invitacioId));
        if (requestingUser.getRole() != Role.ADMIN
                && !inv.getCreatedBy().getId().equals(requestingUser.getId())) {
            throw new AccessDeniedException("No tens permís sobre aquesta invitació");
        }
        inv.setActive(false);
        invitacioRepository.save(inv);
    }

    // ── Endpoint públic ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InvitacioPublicaDto getPublica(UUID token) {
        Invitacio inv = findValidaOrThrow(token);
        return InvitacioPublicaDto.from(inv);
    }

    /**
     * Accepta el convit creant un compte nou i matriculant l'alumne.
     * Retorna un JWT perquè el frontend faci login automàtic.
     */
    @Transactional
    public LoginResponse acceptar(UUID token, AcceptarConvitRequest req) {
        Invitacio inv = findValidaOrThrow(token);

        Optional<User> existent = userRepository.findByEmail(req.email());
        User alumne;
        if (existent.isPresent()) {
            // Compte existent: cal demostrar que és seu (si no, qualsevol amb l'enllaç
            // podria entrar al compte d'un altre alumne només sabent-ne el correu)
            alumne = existent.get();
            // Mateix missatge si el compte entra amb Google o si la contrasenya no és correcta:
            // la resposta no ha de revelar quin tipus de compte té un correu
            if (alumne.getPasswordHash() == null
                    || !passwordEncoder.matches(req.password(), alumne.getPasswordHash())) {
                throw new IllegalArgumentException(CREDENCIALS_INCORRECTES);
            }
            if (alumne.getRole() != Role.STUDENT) {
                throw new IllegalArgumentException("Aquest correu pertany a un compte que no és d'alumne");
            }
        } else {
            alumne = userRepository.save(User.builder()
                    .name(req.nom())
                    .email(req.email())
                    .passwordHash(passwordEncoder.encode(req.password()))
                    .role(Role.STUDENT)
                    .build());
        }

        // Matricular si no ho estava ja
        String curs = inv.getCurs();
        UUID modulId = inv.getModul().getId();
        if (!matriculaRepository.existsByAlumneIdAndModulIdAndCurs(alumne.getId(), modulId, curs)) {
            matriculaRepository.save(
                    Matricula.builder().alumne(alumne).modul(inv.getModul()).curs(curs).build());
        }

        // Afegir al grup si el convit en té un assignat
        if (inv.getGrup() != null) {
            Grup grup = grupRepository.findByIdWithStudents(inv.getGrup().getId())
                    .orElseThrow(() -> new NoSuchElementException("Grup no trobat"));
            grup.getStudents().add(alumne);
            grupRepository.save(grup);
        }

        inv.setUsesCount(inv.getUsesCount() + 1);
        invitacioRepository.save(inv);

        String jwt = jwtUtil.generateToken(alumne);
        return new LoginResponse(jwt, alumne.getId(), alumne.getName(), alumne.getEmail(), alumne.getRole());
    }

    /**
     * Accepta el convit per a un usuari ja autenticat (té compte, fa clic al link mentre és dins).
     */
    @Transactional
    public InvitacioPublicaDto acceptarAutenticat(UUID token, User alumne) {
        if (alumne.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Només els alumnes poden acceptar convits");
        }
        Invitacio inv = findValidaOrThrow(token);

        String curs = inv.getCurs();
        UUID modulId = inv.getModul().getId();
        if (!matriculaRepository.existsByAlumneIdAndModulIdAndCurs(alumne.getId(), modulId, curs)) {
            matriculaRepository.save(
                    Matricula.builder().alumne(alumne).modul(inv.getModul()).curs(curs).build());
        }

        if (inv.getGrup() != null) {
            Grup grup = grupRepository.findByIdWithStudents(inv.getGrup().getId())
                    .orElseThrow(() -> new NoSuchElementException("Grup no trobat"));
            grup.getStudents().add(alumne);
            grupRepository.save(grup);
        }

        inv.setUsesCount(inv.getUsesCount() + 1);
        invitacioRepository.save(inv);

        return InvitacioPublicaDto.from(inv);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Invitacio findValidaOrThrow(UUID token) {
        Invitacio inv = invitacioRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Link de convit no vàlid"));
        if (!inv.isValida()) {
            throw new IllegalStateException("Aquest link ha caducat o ha estat desactivat");
        }
        return inv;
    }
}
