package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "professor_departaments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(ProfessorDepartament.PK.class)
public class ProfessorDepartament {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", nullable = false)
    private User professor;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departament_id", nullable = false)
    private Departament departament;

    @Column(nullable = false)
    @Builder.Default
    private boolean esCap = false;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID professor;
        private UUID departament;
    }
}
