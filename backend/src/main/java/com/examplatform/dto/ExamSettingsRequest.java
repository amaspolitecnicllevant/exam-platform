package com.examplatform.dto;

import java.math.BigDecimal;

/** Canvis parcials de configuració: els camps null no es modifiquen. */
public record ExamSettingsRequest(BigDecimal penalitzacioChoice, Boolean unaPreguntaPerPantalla,
                                  String title, Integer durada, com.examplatform.domain.model.TipusExamen tipus) {

    public ExamSettingsRequest(BigDecimal penalitzacioChoice, Boolean unaPreguntaPerPantalla, String title, Integer durada) {
        this(penalitzacioChoice, unaPreguntaPerPantalla, title, durada, null);
    }

    public ExamSettingsRequest(BigDecimal penalitzacioChoice) {
        this(penalitzacioChoice, null, null, null, null);
    }

    public ExamSettingsRequest(BigDecimal penalitzacioChoice, Boolean unaPreguntaPerPantalla) {
        this(penalitzacioChoice, unaPreguntaPerPantalla, null, null, null);
    }
}
