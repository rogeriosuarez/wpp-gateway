package com.example.wppgateway.service;

import com.example.wppgateway.model.SessionUsage;
import com.example.wppgateway.repository.SessionUsageRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class SessionUsageService {

    private final SessionUsageRepository sessionUsageRepository;
    private static final int SESSION_DAILY_LIMIT = 450;

    public SessionUsageService(SessionUsageRepository sessionUsageRepository) {
        this.sessionUsageRepository = sessionUsageRepository;
    }

    /**
     * Verifica se a sessão pode enviar mais mensagens hoje
     */
    public boolean canSendMessage(String sessionName) {
        Integer count = sessionUsageRepository.countTodayBySession(sessionName, LocalDate.now());
        return count == null || count < SESSION_DAILY_LIMIT;
    }

    /**
     * Registra uso da sessão (incrementa contador)
     */
    public void recordUsage(String sessionName) {
        Optional<SessionUsage> usageOpt = sessionUsageRepository
                .findBySessionNameAndDate(sessionName, LocalDate.now());

        if (usageOpt.isPresent()) {
            // Se já existe registro, incrementa
            sessionUsageRepository.incrementCount(sessionName, LocalDate.now());
        } else {
            // Se não existe, cria novo
            SessionUsage usage = new SessionUsage(sessionName);
            usage.setCount(1);
            sessionUsageRepository.save(usage);
        }
    }

    /**
     * Obtém contagem de uso da sessão hoje
     */
    public int getUsageToday(String sessionName) {
        Integer count = sessionUsageRepository.countTodayBySession(sessionName, LocalDate.now());
        return count != null ? count : 0;
    }

    /**
     * Verifica limite por sessão e retorna mensagem de erro se excedido
     */
    public Optional<String> checkSessionLimit(String sessionName) {
        int used = getUsageToday(sessionName);
        if (used >= SESSION_DAILY_LIMIT) {
            return Optional.of(String.format(
                    "Session daily limit exceeded (anti-block protection). Limit: %d, Used: %d",
                    SESSION_DAILY_LIMIT, used));
        }
        return Optional.empty();
    }
}