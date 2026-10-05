package com.examplatform.domain.service;

import com.examplatform.domain.model.Cicle;
import com.examplatform.domain.model.Departament;
import com.examplatform.dto.CicleDto;
import com.examplatform.infrastructure.persistence.CicleRepository;
import com.examplatform.infrastructure.persistence.DepartamentRepository;
import com.examplatform.infrastructure.persistence.ModulRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CicleService {

    private final CicleRepository cicleRepository;
    private final DepartamentRepository departamentRepository;
    private final ModulRepository modulRepository;

    @Transactional(readOnly = true)
    public List<CicleDto> findAll() {
        return cicleRepository.findAll().stream()
                .sorted((a, b) -> a.getCodi().compareToIgnoreCase(b.getCodi()))
                .map(CicleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CicleDto> findByDepartament(UUID departamentId) {
        return cicleRepository.findByDepartamentIdOrderByCodiAsc(departamentId).stream()
                .map(CicleDto::from).toList();
    }

    @Transactional
    public CicleDto create(String codi, String nom, UUID departamentId) {
        if (cicleRepository.findByCodi(codi).isPresent()) {
            throw new IllegalArgumentException("Ja existeix un cicle amb el codi: " + codi);
        }
        Departament dep = departamentRepository.findById(departamentId)
                .orElseThrow(() -> new NoSuchElementException("Departament no trobat: " + departamentId));
        return CicleDto.from(cicleRepository.save(
                Cicle.builder().codi(codi.toUpperCase()).nom(nom).departament(dep).build()));
    }

    @Transactional
    public CicleDto update(UUID id, String codi, String nom) {
        Cicle cicle = cicleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cicle no trobat: " + id));
        cicleRepository.findByCodi(codi).ifPresent(existing -> {
            if (!existing.getId().equals(id))
                throw new IllegalArgumentException("Ja existeix un cicle amb el codi: " + codi);
        });
        cicle.setCodi(codi.toUpperCase());
        cicle.setNom(nom);
        return CicleDto.from(cicleRepository.save(cicle));
    }

    @Transactional
    public void delete(UUID id) {
        Cicle cicle = cicleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cicle no trobat: " + id));
        if (!modulRepository.findByCicleIdOrderByCodiAsc(id).isEmpty()) {
            throw new IllegalStateException(
                    "No es pot eliminar un cicle que té mòduls associats");
        }
        cicleRepository.delete(cicle);
    }
}
