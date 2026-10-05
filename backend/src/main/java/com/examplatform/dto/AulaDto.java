package com.examplatform.dto;

import com.examplatform.domain.model.Aula;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record AulaDto(UUID id, String nom, String xarxaCidr) {

    public static AulaDto from(Aula a) {
        return new AulaDto(a.getId(), a.getNom(), a.getXarxaCidr());
    }

    public record CreateRequest(
            @NotBlank String nom,
            @NotBlank
            @Pattern(regexp = "^(\\d{1,3}\\.){3}\\d{1,3}/\\d{1,2}$",
                     message = "El format ha de ser CIDR, p. ex. 192.168.10.0/24")
            String xarxaCidr) {}
}
