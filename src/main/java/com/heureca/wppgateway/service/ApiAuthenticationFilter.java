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
        // ❗ NÃO ignorar /admin
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String rapidHost = request.getHeader("X-RapidAPI-Host");
        String rapidProxySecret = request.getHeader("X-RapidAPI-Proxy-Secret");
        String internalKey = request.getHeader("X-Api-Key");
        String path = request.getRequestURI();

        try {
            ApiClient client = null;

            // ==========================
            // 🔥 Fluxo RapidAPI
            // ==========================
            if (rapidHost != null && rapidProxySecret != null) {

                if (!RAPIDAPI_PROXY_SECRET.equals(rapidProxySecret)) {
                    throw new UnauthorizedException("Invalid RapidAPI proxy origin");
                }

                // 🟡 RapidAPI SEM X-Api-Key
                if (internalKey == null) {

                    // ✅ Permite APENAS criar o client
                    if (path.equals("/admin/create-client")) {
                        logger.debug(
                                "RapidAPI bootstrap request allowed: {}",
                                path);
                        request.setAttribute("clientSource", ClientSource.RAPID);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    logger.debug(
                            "RapidAPI request missing X-Api-Key | path={}",
                            path);
                    throw new UnauthorizedException("Missing API key");
                }

                // 🔐 RapidAPI COM X-Api-Key
                client = clientService.validateInternalClient(internalKey, ClientSource.RAPID);

            // ==========================
            // 🔐 Fluxo cliente interno
            // ==========================
            } else if (internalKey != null) {

                ClientSource source = (path.equals("/admin/create-client"))?ClientSource.ADMIN:ClientSource.INTERNAL;
                client = clientService.validateInternalClient(internalKey,source);

            // ==========================
            // ❌ Nenhuma credencial
            // ==========================
            } else {
                logger.debug(
                        "Unauthorized request | rapidHost={} | rapidProxySecret={} | internalKey={}",
                        rapidHost != null,
                        rapidProxySecret != null,
                        internalKey != null);
                throw new UnauthorizedException("Missing API key");
            }

            // ==========================
            // 🛑 Proteção de rotas ADMIN
            // ==========================
            if (path.startsWith("/admin")
                    && client.getSource() != ClientSource.ADMIN) {
                logger.debug(
                        "Admin access denied | clientSource={}",
                        client.getSource());
                throw new UnauthorizedException("Admin privileges required");
            }
            request.setAttribute("clientSource", ClientSource.INTERNAL);

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
            response.sendError(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Internal authentication error");
        }
    }
}
