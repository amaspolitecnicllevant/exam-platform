package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "questions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "ordre"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(nullable = false)
    private int ordre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType tipus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enunciat;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal punts;

    @Column(columnDefinition = "TEXT")
    private String modelResposta;

    @Column(columnDefinition = "TEXT")
    private String outputContains;

    @Column(columnDefinition = "TEXT")
    private String outputExact;

    @Column(columnDefinition = "TEXT")
    private String outputRegex;

    @Column(columnDefinition = "TEXT")
    private String testScript;

    /** Conceptes clau (bloc :::clau) per proposar la nota de preguntes de text. */
    @Column(columnDefinition = "TEXT")
    private String claus;

    @Column(columnDefinition = "TEXT")
    private String choices;

    @Column(name = "correct_choice", length = 10)
    private String correctChoice;

    /** CHOICE: si és cert, cada alumne veu les opcions en un ordre diferent. */
    @Column(name = "barrejar_opcions", nullable = false)
    @Builder.Default
    private boolean barrejarOpcions = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean anulada = false;

    /** Es poden fer servir apunts (en paper) en aquesta pregunta. */
    @Column(name = "amb_apunts", nullable = false)
    @Builder.Default
    private boolean ambApunts = false;

    @Column(length = 100)
    private String ra;

    @Column(length = 10)
    private String dificultat;

    /** Només FILE_UPLOAD: extensions admeses separades per comes (docx,xlsx,pkt…); null = totes les permeses. */
    @Column(name = "formats_permesos", length = 200)
    private String formatsPermesos;
}
