package com.examplatform.dto;

import java.math.BigDecimal;

public record ConfiguracioUpdateRequest(
        String     nomCentre,
        String     colorMarca,
        String     cursActiu,
        Integer    duradaDefecte,
        BigDecimal penalitzacioDefecte,
        Integer    focusLossThreshold,
        Integer    gracePeriodSeconds,
        String     dominisOauth,
        Integer    copiesLlindar,
        Integer    copiesLlindarApunts,
        Boolean    pujadaFitxersActiva
) {}
