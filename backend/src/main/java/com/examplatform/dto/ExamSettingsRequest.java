package com.examplatform.dto;

import java.math.BigDecimal;

/** Canvis parcials de configuració: els camps null no es modifiquen. */
public record ExamSettingsRequest(BigDecimal penalitzacioChoice, Boolean unaPreguntaPerPantalla,
                                  String title, Integer durada) {

    public ExamSettingsRequest(BigDecimal penalitzacioChoice) {
        this(penalitzacioChoice, null, null, null);
    }

    public ExamSettingsRequest(BigDecimal penalitzacioChoice, Boolean unaPreguntaPerPantalla) {
        this(penalitzacioChoice, unaPreguntaPerPantalla, null, null);
    }
}
