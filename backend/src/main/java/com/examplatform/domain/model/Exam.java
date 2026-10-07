package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "exams")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int durada;

    @Column(columnDefinition = "TEXT")
    private String instruccions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExamStatus status = ExamStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawMd;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordre ASC")
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime scheduledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_grup_id")
    private Grup scheduledGrup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modul_id")
    private Modul modul;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id")
    private Aula aula;

    /** Factor de penalització per resposta incorrecta CHOICE (0 = sense penalització, 0.33 = -1/3 del valor) */
    @Column(nullable = false)
    @Builder.Default
    private java.math.BigDecimal penalitzacioChoice = java.math.BigDecimal.ZERO;

    /** Quan és true, els alumnes poden veure les seves notes i correccions */
    @Column(nullable = false)
    @Builder.Default
    private boolean notesVisibles = false;

    /**
     * Si és cert, l'examen només és per als alumnes que hi tenen una sessió assignada (no per a tots els
     * matriculats al mòdul): permet activar-lo per a alumnes concrets i afegir-ne més després.
     */
    @Column(name = "restringit", nullable = false)
    @Builder.Default
    private boolean restringit = false;

    /** Si és cert, l'alumne veu una sola pregunta per pantalla (navegació lliure). */
    @Column(name = "una_pregunta_per_pantalla", nullable = false)
    @Builder.Default
    private boolean unaPreguntaPerPantalla = false;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
