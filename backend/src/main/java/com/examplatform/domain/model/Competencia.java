package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Competència d'un mòdul (CO1, CO2…). Les preguntes dels exàmens en guarden el codi. */
@Entity
@Table(name = "competencies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Competencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modul_id", nullable = false)
    private Modul modul;

    @Column(nullable = false, length = 20)
    private String codi;

    @Column(nullable = false)
    @Builder.Default
    private String descripcio = "";

    @Column(nullable = false)
    private int ordre;
}
