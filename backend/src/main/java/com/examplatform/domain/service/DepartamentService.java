package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.DepartamentDto;
import com.examplatform.dto.ProfessorDepartamentDto;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartamentService {

    private final DepartamentRepository         departamentRepository;
    private final CicleRepository               cicleRepository;
    private final ProfessorDepartamentRepository profDepRepository;
    private final UserRepository                userRepository;

    @Transactional(readOnly = true)
    public List<DepartamentDto> findAll() {
        return departamentRepository.findAllByOrderByNomAsc().stream()
                .map(DepartamentDto::from).toList();
    }

    @Transactional
    public DepartamentDto create(String nom) {
        if (departamentRepository.findByNom(nom).isPresent()) {
            throw new IllegalArgumentException("Ja existeix un departament amb el nom: " + nom);
        }
        return DepartamentDto.from(departamentRepository.save(
                Departament.builder().nom(nom).build()));
    }

    @Transactional
    public void delete(UUID id) {
        Departament dep = departamentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Departament no trobat: " + id));
        if (!cicleRepository.findByDepartamentIdOrderByCodiAsc(id).isEmpty()) {
            throw new IllegalStateException(
                    "No es pot eliminar un departament que té cicles associats");
        }
        departamentRepository.delete(dep);
    }

    // ── Gestió de professors ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProfessorDepartamentDto> findProfessors(UUID departamentId) {
        return profDepRepository.findByDepartamentId(departamentId).stream()
                .map(ProfessorDepartamentDto::from).toList();
    }

    @Transactional
    public ProfessorDepartamentDto addProfessor(UUID departamentId, UUID professorId, boolean esCap) {
        Departament dep = departamentRepository.findById(departamentId)
                .orElseThrow(() -> new NoSuchElementException("Departament no trobat: " + departamentId));
        User prof = userRepository.findById(professorId)
                .orElseThrow(() -> new NoSuchElementException("Usuari no trobat: " + professorId));
        if (prof.getRole() != Role.PROFESSOR && prof.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException(prof.getEmail() + " no és professor");
        }
        if (profDepRepository.existsByProfessorIdAndDepartamentId(professorId, departamentId)) {
            throw new IllegalArgumentException(prof.getEmail() + " ja pertany a aquest departament");
        }
        return ProfessorDepartamentDto.from(profDepRepository.save(
                ProfessorDepartament.builder().professor(prof).departament(dep).esCap(esCap).build()));
    }

    @Transactional
    public void removeProfessor(UUID departamentId, UUID professorId) {
        if (!profDepRepository.existsByProfessorIdAndDepartamentId(professorId, departamentId)) {
            throw new NoSuchElementException("El professor no pertany a aquest departament");
        }
        profDepRepository.deleteByProfessorIdAndDepartamentId(professorId, departamentId);
    }
}
