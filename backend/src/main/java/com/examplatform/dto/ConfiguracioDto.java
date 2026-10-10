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
        boolean pujadaFitxersActiva,
        /** Hi ha login amb Google configurat (GOOGLE_CLIENT_ID i GOOGLE_CLIENT_SECRET). */
        boolean googleActiu
) {
    /**
     * El que veu qui no és professorat ni administració (també qui encara no ha entrat): només la imatge del centre i el
     * que cal per entrar. Els llindars de detecció de còpies i de pèrdues de focus NO s'hi inclouen: un alumne que els
     * coneix pot ajustar el que fa perquè no salti cap avís.
     */
    public static ConfiguracioDto publica(ConfiguracioSistema c, boolean googleActiu) {
        return new ConfiguracioDto(
                c.getNomCentre(),
                c.getLogoBase64() != null,
                c.getColorMarca(),
                "",
                0,
                BigDecimal.ZERO,
                0,
                0,
                "",
                0,
                0,
                !Boolean.FALSE.equals(c.getPujadaFitxersActiva()),
                googleActiu
        );
    }

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
                !Boolean.FALSE.equals(c.getPujadaFitxersActiva()),
                googleActiu
        );
    }
}
