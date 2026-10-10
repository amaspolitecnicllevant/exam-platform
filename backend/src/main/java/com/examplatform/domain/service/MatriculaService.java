package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.MatriculaDto;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final UserRepository      userRepository;
    private final ModulRepository     modulRepository;
    private final ImparticioRepository imparticioRepository;

    /**
     * Mòduls les matrícules dels quals pot veure l'usuari: {@code null} vol dir tots (l'administració); un professor, només els que
     * imparteix; qualsevol altre rol, cap. Sense això, un professor podria consultar els alumnes de qualsevol mòdul o les matrícules de
     * qualsevol alumne.
     */
    private Set<UUID> modulsVisibles(User usuari) {
        if (usuari.getRole() == Role.ADMIN) return null;
        if (usuari.getRole() != Role.PROFESSOR) return Set.of();
        return new HashSet<>(imparticioRepository.findModulIdsByProfessorId(usuari.getId()));
    }

    private List<MatriculaDto> nomesVisibles(List<Matricula> matricules, User usuari) {
        Set<UUID> moduls = modulsVisibles(usuari);
        return matricules.stream()
                .filter(m -> moduls == null || moduls.contains(m.getModul().getId()))
                .map(MatriculaDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MatriculaDto> findByAlumne(UUID alumneId, User usuari) {
        return nomesVisibles(matriculaRepository.findByAlumneId(alumneId), usuari);
    }

    @Transactional(readOnly = true)
    public List<MatriculaDto> findByModulAndCurs(UUID modulId, String curs, User usuari) {
        return nomesVisibles(matriculaRepository.findByModulIdAndCurs(modulId, curs), usuari);
    }

    /**
     * Matrícules que l'usuari pot veure: l'administrador, totes; un professor, només les dels
     * mòduls que imparteix (no veu a quins altres mòduls estan matriculats els alumnes).
     */
    @Transactional(readOnly = true)
    public List<MatriculaDto> findVisibles(User usuari) {
        if (usuari.getRole() == Role.ADMIN) {
            return matriculaRepository.findAllAmbDetall().stream().map(MatriculaDto::from).toList();
        }
        if (usuari.getRole() != Role.PROFESSOR) {
            return List.of();
        }
        List<UUID> modulIds = imparticioRepository.findModulIdsByProfessorId(usuari.getId());
        if (modulIds.isEmpty()) {
            return List.of();
        }
        return matriculaRepository.findByModulIdsAmbDetall(modulIds).stream()
                .map(MatriculaDto::from).toList();
    }

    @Transactional
    public MatriculaDto enroll(UUID alumneId, UUID modulId, String curs) {
        User alumne = userRepository.findById(alumneId)
                .orElseThrow(() -> new NoSuchElementException("Usuari no trobat: " + alumneId));
        if (alumne.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException(alumne.getEmail() + " no és alumne");
        }
        Modul modul = modulRepository.findById(modulId)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + modulId));
        if (matriculaRepository.existsByAlumneIdAndModulIdAndCurs(alumneId, modulId, curs)) {
            throw new IllegalArgumentException(
                    alumne.getEmail() + " ja està matriculat a " + modul.getCodi() + " el curs " + curs);
        }

        return MatriculaDto.from(matriculaRepository.save(
                Matricula.builder().alumne(alumne).modul(modul).curs(curs).build()));
    }

    /**
     * Matricula molts alumnes al mateix mòdul i curs. Els que ja hi estan matriculats es compten
     * però no donen error; si algun identificador no és d'un alumne, no es matricula ningú.
     */
    @Transactional
    public MatriculaDto.LotResultat enrollLot(List<UUID> alumneIds, UUID modulId, String curs) {
        Modul modul = modulRepository.findById(modulId)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + modulId));
        List<User> alumnes = userRepository.findAllById(new java.util.LinkedHashSet<>(alumneIds));
        if (alumnes.size() != new java.util.HashSet<>(alumneIds).size()) {
            throw new NoSuchElementException("Algun dels alumnes no existeix");
        }
        for (User u : alumnes) {
            if (u.getRole() != Role.STUDENT) throw new IllegalArgumentException(u.getEmail() + " no és alumne");
        }
        List<MatriculaDto> noves = new java.util.ArrayList<>();
        int jaHiEren = 0;
        for (User u : alumnes) {
            if (matriculaRepository.existsByAlumneIdAndModulIdAndCurs(u.getId(), modulId, curs)) {
                jaHiEren++;
                continue;
            }
            noves.add(MatriculaDto.from(matriculaRepository.save(
                    Matricula.builder().alumne(u).modul(modul).curs(curs).build())));
        }
        return new MatriculaDto.LotResultat(noves, jaHiEren);
    }

    @Transactional
    public void unenroll(UUID matriculaId) {
        Matricula m = matriculaRepository.findById(matriculaId)
                .orElseThrow(() -> new NoSuchElementException("Matrícula no trobada: " + matriculaId));
        matriculaRepository.delete(m);
    }
}
