package com.examplatform.domain.service;

import java.util.UUID;

/** Es publica quan una sessió d'examen passa a SUBMITTED (per l'alumne o forçada). */
public record SessioEntregadaEvent(UUID sessionId) {}
