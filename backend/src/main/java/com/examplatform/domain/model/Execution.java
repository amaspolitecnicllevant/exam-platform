package com.examplatform.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "executions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @Column(nullable = false, updatable = false)
    private LocalDateTime executedAt;

    @Column(columnDefinition = "TEXT")
    private String output;

    private Integer exitCode;

    private Long durationMs;

    @PrePersist
    void onCreate() { this.executedAt = LocalDateTime.now(); }
}
