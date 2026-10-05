package com.examplatform.controller;

import com.examplatform.dto.AulaDto;
import com.examplatform.domain.service.AulaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/aules")
@RequiredArgsConstructor
public class AulaController {

    private final AulaService aulaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<AulaDto> findAll() {
        return aulaService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AulaDto> create(@Valid @RequestBody AulaDto.CreateRequest req) {
        return ResponseEntity.ok(aulaService.create(req.nom(), req.xarxaCidr()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AulaDto update(@PathVariable UUID id,
                          @Valid @RequestBody AulaDto.CreateRequest req) {
        return aulaService.update(id, req.nom(), req.xarxaCidr());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        aulaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
