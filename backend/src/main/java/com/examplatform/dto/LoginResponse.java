package com.examplatform.dto;

import com.examplatform.domain.model.Role;
import java.util.UUID;

public record LoginResponse(String token, UUID userId, String name, String email, Role role) {}
