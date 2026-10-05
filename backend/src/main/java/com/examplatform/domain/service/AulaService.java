package com.examplatform.domain.service;

import com.examplatform.domain.model.Aula;
import com.examplatform.dto.AulaDto;
import com.examplatform.infrastructure.persistence.AulaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AulaService {

    private final AulaRepository aulaRepository;

    @Transactional(readOnly = true)
    public List<AulaDto> findAll() {
        return aulaRepository.findAllByOrderByNomAsc().stream()
                .map(AulaDto::from).toList();
    }

    @Transactional
    public AulaDto create(String nom, String xarxaCidr) {
        if (aulaRepository.findByNom(nom).isPresent()) {
            throw new IllegalArgumentException("Ja existeix una aula amb el nom: " + nom);
        }
        return AulaDto.from(aulaRepository.save(
                Aula.builder().nom(nom).xarxaCidr(xarxaCidr).build()));
    }

    @Transactional
    public AulaDto update(UUID id, String nom, String xarxaCidr) {
        Aula aula = aulaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Aula no trobada: " + id));
        aula.setNom(nom);
        aula.setXarxaCidr(xarxaCidr);
        return AulaDto.from(aulaRepository.save(aula));
    }

    @Transactional
    public void delete(UUID id) {
        Aula aula = aulaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Aula no trobada: " + id));
        aulaRepository.delete(aula);
    }
}
