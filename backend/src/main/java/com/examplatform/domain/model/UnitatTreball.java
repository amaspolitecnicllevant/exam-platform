package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Unitat de treball (UT01, UT02…) d'un mòdul, amb les competències que hi treballa. */
@Entity
@Table(name = "unitats_treball")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnitatTreball {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modul_id", nullable = false)
    private Modul modul;

    @Column(nullable = false, length = 20)
    private String codi;

    @Column(nullable = false, length = 200)
    @Builder.Default
    private String nom = "";

    @Column(nullable = false)
    private int ordre;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "unitat_competencies",
            joinColumns = @JoinColumn(name = "unitat_id"),
            inverseJoinColumns = @JoinColumn(name = "competencia_id"))
    @Builder.Default
    private Set<Competencia> competencies = new LinkedHashSet<>();
}
