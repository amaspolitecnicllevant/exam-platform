package com.examplatform.dto;

/** Canvis parcials d'una pregunta: els camps null no es modifiquen. */
public record QuestionPatchRequest(String correctChoice, Boolean anulada, String ra, String dificultat,
                                   Boolean barrejarOpcions, Boolean ambApunts) {

    public QuestionPatchRequest(String correctChoice, Boolean anulada, String ra, String dificultat) {
        this(correctChoice, anulada, ra, dificultat, null, null);
    }

    public QuestionPatchRequest(String correctChoice, Boolean anulada, String ra, String dificultat,
                                Boolean barrejarOpcions) {
        this(correctChoice, anulada, ra, dificultat, barrejarOpcions, null);
    }
}
