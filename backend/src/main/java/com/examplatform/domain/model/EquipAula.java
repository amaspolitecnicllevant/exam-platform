package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Un ordinador d'una aula, tal com ell mateix l'ha descrit en el darrer informe. */
@Entity
@Table(name = "equips_aula",
       uniqueConstraints = @UniqueConstraint(columnNames = {"aula_id", "nom"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EquipAula {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "darrer_informe", nullable = false)
    private LocalDateTime darrerInforme;

    /** Llista de comprovació d'integritat tal com la va enviar (una línia per fitxer o fet). */
    @Column(columnDefinition = "TEXT")
    private String integritat;

    @Column(name = "integritat_resum", length = 64)
    private String integritatResum;

    @Column(name = "arriba_plataforma")
    private Boolean arribaPlataforma;

    @Column(name = "arriba_isard")
    private Boolean arribaIsard;

    @Column(length = 200)
    private String navegador;

    @Column(name = "disc_lliure_mb")
    private Integer discLliureMb;

    private LocalDateTime arrencada;

    @Column(name = "usuaris_dins")
    private Integer usuarisDins;

    /** Quan un administrador va demanar restaurar-lo; null si no hi ha cap restauració pendent. */
    @Column(name = "restauracio_demanada_el")
    private LocalDateTime restauracioDemanadaEl;

    /** Resultat de l'última restauració que l'ordinador ha informat: «OK» o el motiu de l'error. */
    @Column(name = "restauracio_resultat", length = 200)
    private String restauracioResultat;

    @Column(name = "restauracio_resultat_el")
    private LocalDateTime restauracioResultatEl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
