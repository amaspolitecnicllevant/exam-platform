package com.examplatform.controller;

import com.examplatform.domain.service.CicleService;
import com.examplatform.dto.CicleDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cicles")
@RequiredArgsConstructor
public class CicleController {

    private final CicleService cicleService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CicleDto> list(@RequestParam(required = false) UUID departamentId) {
        return departamentId != null
                ? cicleService.findByDepartament(departamentId)
                : cicleService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CicleDto create(@Valid @RequestBody CicleDto.CreateRequest req) {
        return cicleService.create(req.codi(), req.nom(), req.departamentId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CicleDto update(@PathVariable UUID id, @Valid @RequestBody CicleDto.CreateRequest req) {
        return cicleService.update(id, req.codi(), req.nom());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        cicleService.delete(id);
    }
}
