package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** L'estat d'un ordinador net i validat, amb el qual es comparen tots els altres. Només n'hi ha una (id = 1). */
@Entity
@Table(name = "equips_referencia")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EquipsReferencia {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String integritat;

    @Column(name = "integritat_resum", nullable = false, length = 64)
    private String integritatResum;

    @Column(name = "origen_nom", length = 100)
    private String origenNom;

    @Column(name = "fixada_per")
    private UUID fixadaPer;

    @Column(name = "fixada_el", nullable = false)
    private LocalDateTime fixadaEl;
}
