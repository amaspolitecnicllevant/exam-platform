package com.examplatform.dto;

import com.examplatform.domain.model.ConfiguracioSistema;

import java.math.BigDecimal;

public record ConfiguracioDto(
        String  nomCentre,
        boolean hasLogo,
        String  colorMarca,
        String  cursActiu,
        int     duradaDefecte,
        BigDecimal penalitzacioDefecte,
        int     focusLossThreshold,
        int     gracePeriodSeconds,
        String  dominisOauth,
        int     copiesLlindar,
        int     copiesLlindarApunts,
        /** Hi ha login amb Google configurat (GOOGLE_CLIENT_ID i GOOGLE_CLIENT_SECRET). */
        boolean googleActiu
) {
    public static ConfiguracioDto from(ConfiguracioSistema c, boolean googleActiu) {
        return new ConfiguracioDto(
                c.getNomCentre(),
                c.getLogoBase64() != null,
                c.getColorMarca(),
                c.getCursActiu(),
                c.getDuradaDefecte(),
                c.getPenalitzacioDefecte(),
                c.getFocusLossThreshold(),
                c.getGracePeriodSeconds(),
                c.getDominisOauth(),
                c.getCopiesLlindar(),
                c.getCopiesLlindarApunts(),
                googleActiu
        );
    }
}
