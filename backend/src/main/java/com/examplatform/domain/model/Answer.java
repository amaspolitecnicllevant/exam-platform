package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "answers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "question_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String contingut;

    @Column(columnDefinition = "TEXT")
    private String executionOutput;

    @Column(precision = 4, scale = 2)
    private BigDecimal autoScore;

    @Column(precision = 4, scale = 2)
    private BigDecimal manualScore;

    /** Motius de la nota proposada (autoScore), una línia per motiu. */
    @Column(columnDefinition = "TEXT")
    private String autoFeedback;

    /** Comentari del professor per a l'alumne (visible quan es publiquen les notes). */
    @Column(columnDefinition = "TEXT")
    private String comentari;

    private LocalDateTime correctedAt;

    /** Fitxer pujat per l'alumne (preguntes FILE_UPLOAD): nom original, ruta al disc, mida i empremta. */
    @Column(name = "fitxer_nom", length = 255)
    private String fitxerNom;

    @Column(name = "fitxer_ruta", length = 500)
    private String fitxerRuta;

    @Column(name = "fitxer_mida")
    private Long fitxerMida;

    @Column(name = "fitxer_sha256", length = 64)
    private String fitxerSha256;

    @Column(name = "fitxer_pujat_el")
    private LocalDateTime fitxerPujatEl;

    public boolean teFitxer() {
        return fitxerRuta != null;
    }

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("executedAt DESC")
    @Builder.Default
    private List<Execution> executions = new ArrayList<>();
}
