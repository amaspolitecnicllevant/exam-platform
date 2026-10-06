package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "configuracio_sistema")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfiguracioSistema {

    @Id
    private Integer id = 1;

    @Column(name = "nom_centre", nullable = false)
    private String nomCentre = "SEDEX";

    @Column(name = "logo_base64", columnDefinition = "TEXT")
    private String logoBase64;

    @Column(name = "logo_mime", length = 50)
    private String logoMime;

    @Column(name = "color_marca", nullable = false, length = 7)
    private String colorMarca = "#8b1a4a";

    @Column(name = "curs_actiu", nullable = false, length = 10)
    private String cursActiu = "2026-27";

    @Column(name = "durada_defecte", nullable = false)
    private Integer duradaDefecte = 90;

    @Column(name = "penalitzacio_defecte", nullable = false)
    private BigDecimal penalitzacioDefecte = BigDecimal.ZERO;

    @Column(name = "focus_loss_threshold", nullable = false)
    private Integer focusLossThreshold = 5;

    /** Semblança (%) a partir de la qual l'informe de possibles còpies marca una pregunta. */
    @Column(name = "copies_llindar", nullable = false)
    private Integer copiesLlindar = 80;

    /** Semblança (%) per a les preguntes amb apunts (més alta: s'hi pot copiar el mateix dels apunts). */
    @Column(name = "copies_llindar_apunts", nullable = false)
    private Integer copiesLlindarApunts = 95;

    @Column(name = "grace_period_seconds", nullable = false)
    private Integer gracePeriodSeconds = 0;

    /** Si és fals, els alumnes no poden pujar fitxers a les preguntes de lliurament. */
    @Column(name = "pujada_fitxers_activa", nullable = false)
    private Boolean pujadaFitxersActiva = true;

    @Column(name = "dominis_oauth", nullable = false)
    private String dominisOauth = "politecnicllevant.cat";
}
