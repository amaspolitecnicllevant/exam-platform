package com.examplatform.controller;

import com.examplatform.domain.service.ModulService;
import com.examplatform.dto.ImparticioDto;
import com.examplatform.dto.ModulDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moduls")
@RequiredArgsConstructor
public class ModulController {

    private final ModulService modulService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ModulDto> list(@RequestParam(required = false) UUID cicleId,
                               @RequestParam(required = false) UUID departamentId) {
        if (cicleId != null)       return modulService.findByCicle(cicleId);
        if (departamentId != null) return modulService.findByDepartament(departamentId);
        return modulService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ModulDto create(@Valid @RequestBody ModulDto.CreateRequest req) {
        return modulService.create(req.codi(), req.nom(), req.cicleId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ModulDto update(@PathVariable UUID id, @Valid @RequestBody ModulDto.CreateRequest req) {
        return modulService.update(id, req.codi(), req.nom());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        modulService.delete(id);
    }

    @GetMapping("/{modulId}/imparticions")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<ImparticioDto> imparticions(@PathVariable UUID modulId) {
        return modulService.findImparticions(modulId);
    }

    @PostMapping("/{modulId}/imparticions")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ImparticioDto addImparticio(@PathVariable UUID modulId,
                                       @Valid @RequestBody ImparticioDto.CreateRequest req) {
        return modulService.addImparticio(modulId, req.professorId(), req.curs());
    }

    @DeleteMapping("/{modulId}/imparticions/{imparticioId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImparticio(@PathVariable UUID modulId,
                                 @PathVariable UUID imparticioId) {
        modulService.removeImparticio(imparticioId);
    }
}
