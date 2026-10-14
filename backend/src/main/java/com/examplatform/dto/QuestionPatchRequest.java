package com.examplatform.dto;

/** Canvis parcials d'una pregunta: els camps null no es modifiquen. */
public record QuestionPatchRequest(String correctChoice, Boolean anulada, String competencia, String dificultat,
                                   Boolean barrejarOpcions, Boolean ambApunts) {

    public QuestionPatchRequest(String correctChoice, Boolean anulada, String competencia, String dificultat) {
        this(correctChoice, anulada, competencia, dificultat, null, null);
    }

    public QuestionPatchRequest(String correctChoice, Boolean anulada, String competencia, String dificultat,
                                Boolean barrejarOpcions) {
        this(correctChoice, anulada, competencia, dificultat, barrejarOpcions, null);
    }
}
