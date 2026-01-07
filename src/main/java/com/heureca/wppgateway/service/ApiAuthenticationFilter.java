package com.heureca.wppgateway.service;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.heureca.wppgateway.exception.RateLimitExceededException;
import com.heureca.wppgateway.exception.UnauthorizedException;
import com.heureca.wppgateway.model.ApiClient;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private final ApiClientService clientService;

    public ApiAuthenticationFilter(ApiClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Ignora rotas públicas (Swagger, health, admin, etc)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/actuator")
                || path.startsWith("/admin");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String rapidKey = request.getHeader("X-RapidAPI-Key");
        String rapidHost = request.getHeader("X-RapidAPI-Host");
        String internalKey = request.getHeader("X-Api-Key");

        try {
            ApiClient client;

            // ==========================
            // 🔥 RapidAPI
            // ==========================
            if (rapidKey != null && rapidHost != null) {
                client = clientService.getOrCreateRapidClient(rapidKey);

                // ==========================
                // 🔐 Cliente interno
                // ==========================
            } else if (internalKey != null) {
                client = clientService.validateInternalClient(internalKey);

                // ==========================
                // ❌ Nenhuma chave enviada
                // ==========================
            } else {
                throw new UnauthorizedException("Missing API key");
            }

            // ==========================
            // 🚦 Rate limit centralizado
            // ==========================
            clientService.validateRateLimit(client);

            // ==========================
            // 📌 Disponibiliza o cliente
            // ==========================
            request.setAttribute("apiClient", client);

            // Continua o fluxo
            filterChain.doFilter(request, response);

        } catch (RateLimitExceededException ex) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage());

        } catch (UnauthorizedException ex) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());

        } catch (Exception ex) {
            response.sendError(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Internal authentication error");
        }
    }
}
