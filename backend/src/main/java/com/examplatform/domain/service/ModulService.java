package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ImparticioDto;
import com.examplatform.dto.ModulDto;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModulService {

    private final ModulRepository       modulRepository;
    private final CicleRepository       cicleRepository;
    private final ImparticioRepository  imparticioRepository;
    private final UserRepository        userRepository;

    @Transactional(readOnly = true)
    public List<ModulDto> findAll() {
        return modulRepository.findAll().stream()
                .sorted((a, b) -> a.getCodi().compareToIgnoreCase(b.getCodi()))
                .map(ModulDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ModulDto> findByCicle(UUID cicleId) {
        return modulRepository.findByCicleIdOrderByCodiAsc(cicleId).stream()
                .map(ModulDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ModulDto> findByDepartament(UUID departamentId) {
        return modulRepository.findByDepartamentId(departamentId).stream()
                .map(ModulDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ImparticioDto> findImparticions(UUID modulId) {
        return imparticioRepository.findByModulIdAndCurs(modulId, curActual()).stream()
                .map(ImparticioDto::from).toList();
    }

    @Transactional
    public ModulDto create(String codi, String nom, UUID cicleId) {
        if (modulRepository.findByCodi(codi).isPresent()) {
            throw new IllegalArgumentException("Ja existeix un mòdul amb el codi: " + codi);
        }
        Cicle cicle = cicleRepository.findById(cicleId)
                .orElseThrow(() -> new NoSuchElementException("Cicle no trobat: " + cicleId));
        return ModulDto.from(modulRepository.save(
                Modul.builder().codi(codi).nom(nom).cicle(cicle).build()));
    }

    @Transactional
    public ModulDto update(UUID id, String codi, String nom) {
        Modul modul = modulRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + id));
        modulRepository.findByCodi(codi).ifPresent(existing -> {
            if (!existing.getId().equals(id))
                throw new IllegalArgumentException("Ja existeix un mòdul amb el codi: " + codi);
        });
        modul.setCodi(codi);
        modul.setNom(nom);
        return ModulDto.from(modulRepository.save(modul));
    }

    @Transactional
    public void delete(UUID id) {
        Modul modul = modulRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + id));
        modulRepository.delete(modul);
    }

    @Transactional
    public ImparticioDto addImparticio(UUID modulId, UUID professorId, String curs) {
        Modul modul = modulRepository.findById(modulId)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + modulId));
        User professor = userRepository.findById(professorId)
                .orElseThrow(() -> new NoSuchElementException("Usuari no trobat: " + professorId));

        if (professor.getRole() != Role.PROFESSOR && professor.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException(professor.getEmail() + " no és professor");
        }
        if (imparticioRepository.existsByProfessorIdAndModulIdAndCurs(professorId, modulId, curs)) {
            throw new IllegalArgumentException(
                    professor.getEmail() + " ja imparteix " + modul.getCodi() + " el curs " + curs);
        }

        return ImparticioDto.from(imparticioRepository.save(
                Imparticio.builder().professor(professor).modul(modul).curs(curs).build()));
    }

    @Transactional
    public void removeImparticio(UUID imparticioId) {
        Imparticio imp = imparticioRepository.findById(imparticioId)
                .orElseThrow(() -> new NoSuchElementException("Impartició no trobada: " + imparticioId));
        imparticioRepository.delete(imp);
    }

    private String curActual() {
        int any = java.time.LocalDate.now().getYear();
        // Setembre–desembre: curs any/(any+1); gener–agost: curs (any-1)/any
        int inici = java.time.LocalDate.now().getMonthValue() >= 9 ? any : any - 1;
        return inici + "-" + String.valueOf(inici + 1).substring(2);
    }
}
