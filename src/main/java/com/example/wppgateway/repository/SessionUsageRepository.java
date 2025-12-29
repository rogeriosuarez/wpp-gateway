package com.example.wppgateway.repository;

import com.example.wppgateway.model.SessionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;

public interface SessionUsageRepository extends JpaRepository<SessionUsage, Long> {

    // Buscar por sessão e data
    Optional<SessionUsage> findBySessionNameAndDate(String sessionName, LocalDate date);

    // Incrementar contador
    @Modifying
    @Transactional
    @Query("UPDATE SessionUsage su SET su.count = su.count + 1 WHERE su.sessionName = :sessionName AND su.date = :date")
    void incrementCount(@Param("sessionName") String sessionName, @Param("date") LocalDate date);

    // Obter contagem do dia
    @Query("SELECT COALESCE(SUM(su.count), 0) FROM SessionUsage su WHERE su.sessionName = :sessionName AND su.date = :date")
    Integer countTodayBySession(@Param("sessionName") String sessionName, @Param("date") LocalDate date);
}