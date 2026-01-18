package com.heureca.wppgateway.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.heureca.wppgateway.exception.RateLimitExceededException;
import com.heureca.wppgateway.exception.UnauthorizedException;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.ClientSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger =
            LoggerFactory.getLogger(ApiAuthenticationFilter.class);

    private static final String RAPIDAPI_PROXY_SECRET =
            "d2686930-efc1-11f0-b75f-c5c7dea38db1";

    private final ApiClientService clientService;

    public ApiAuthenticationFilter(ApiClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Ignora apenas rotas realmente públicas
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String rapidUser = request.getHeader("X-RapidAPI-User");
        String rapidSubscription = request.getHeader("X-RapidAPI-Subscription");
        // String rapidVersion = request.getHeader("X-RapidAPI-Version");

        String rapidHost = request.getHeader("X-RapidAPI-Host");
        String rapidProxySecret = request.getHeader("X-RapidAPI-Proxy-Secret");

        String internalKey = request.getHeader("X-Api-Key");
        String path = request.getRequestURI();

        logger.debug("Rapid headers | user={} | subscription={}",
                rapidUser, rapidSubscription);

        try {
            ApiClient client;

            // ==========================
            // 🔥 Fluxo RapidAPI (PRIORITÁRIO)
            // ==========================
            if (rapidUser != null && rapidHost != null && rapidProxySecret != null) {

                if (!RAPIDAPI_PROXY_SECRET.equals(rapidProxySecret)) {
                    throw new UnauthorizedException("Invalid RapidAPI proxy origin");
                }

                // 🔐 Resolve ou cria o cliente via RapidUser
                client = clientService.getOrCreateRapidClient(rapidUser);

            // ==========================
            // 🔐 Fluxo cliente interno / admin
            // ==========================
            } else if (internalKey != null) {

                ClientSource source =
                        path.startsWith("/admin")
                                ? ClientSource.ADMIN
                                : ClientSource.INTERNAL;

                client = clientService.validateInternalClient(internalKey, source);
                request.setAttribute("clientSource", source);

            // ==========================
            // ❌ Nenhuma credencial
            // ==========================
            } else {
                throw new UnauthorizedException("Missing authentication credentials");
            }

            // ==========================
            // 🛑 Proteção de rotas ADMIN
            // ==========================
            if (path.startsWith("/admin")
                    && client.getSource() != ClientSource.ADMIN) {
                throw new UnauthorizedException("Admin privileges required");
            }

            // ==========================
            // 🚦 Rate limit centralizado
            // ==========================
            clientService.validateRateLimit(client);

            // ==========================
            // 📌 Disponibiliza o cliente
            // ==========================
            request.setAttribute("apiClient", client);

            filterChain.doFilter(request, response);

        } catch (RateLimitExceededException ex) {
            response.sendError(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    ex.getMessage());

        } catch (UnauthorizedException ex) {
            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    ex.getMessage());

        } catch (Exception ex) {
            logger.error("Authentication error", ex);
            response.sendError(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Internal authentication error");
        }
    }
}
