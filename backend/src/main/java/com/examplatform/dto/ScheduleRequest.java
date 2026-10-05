package com.examplatform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduleRequest(LocalDateTime scheduledAt, UUID grupId) {}
