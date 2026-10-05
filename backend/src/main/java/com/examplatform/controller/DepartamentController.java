package com.examplatform.controller;

import com.examplatform.domain.service.DepartamentService;
import com.examplatform.dto.DepartamentDto;
import com.examplatform.dto.ProfessorDepartamentDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/departaments")
@RequiredArgsConstructor
public class DepartamentController {

    private final DepartamentService departamentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<DepartamentDto> list() {
        return departamentService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartamentDto create(@Valid @RequestBody DepartamentDto.CreateRequest req) {
        return departamentService.create(req.nom());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        departamentService.delete(id);
    }

    @GetMapping("/{id}/professors")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<ProfessorDepartamentDto> listProfessors(@PathVariable UUID id) {
        return departamentService.findProfessors(id);
    }

    @PostMapping("/{id}/professors")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfessorDepartamentDto addProfessor(@PathVariable UUID id,
                                                @Valid @RequestBody ProfessorDepartamentDto.AddRequest req) {
        return departamentService.addProfessor(id, req.professorId(), req.esCap());
    }

    @DeleteMapping("/{id}/professors/{professorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProfessor(@PathVariable UUID id, @PathVariable UUID professorId) {
        departamentService.removeProfessor(id, professorId);
    }
}
