package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.ConfiguracioSistema;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracioRepository extends JpaRepository<ConfiguracioSistema, Integer> {
}
