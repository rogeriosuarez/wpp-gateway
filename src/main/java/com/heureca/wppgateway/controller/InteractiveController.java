package com.heureca.wppgateway.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.dto.ListSection;
import com.heureca.wppgateway.dto.SendButtonsRequest;
import com.heureca.wppgateway.dto.SendListRequest;
import com.heureca.wppgateway.dto.SendPollRequest;
import com.heureca.wppgateway.dto.SendReplyRequest;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.ClientService;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.UsageService;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/interactive")
@Tag(name = "Interactive", description = "Send interactive WhatsApp messages")
@SecurityRequirement(name = "ApiKeyAuth")
public class InteractiveController {

    private final ClientService clientService;
    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public InteractiveController(
            ClientService clientService,
            UsageService usageService,
            SessionUsageService sessionUsageService,
            SessionRepository sessionRepository,
            WppService wppService) {
        this.clientService = clientService;
        this.usageService = usageService;
        this.sessionUsageService = sessionUsageService;
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
    }

    @Operation(summary = "Send interactive list message", description = "Send a WhatsApp interactive list (menu with selectable options). API key must be in header 'X-Api-Key'")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List message sent successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid API Key"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not ready"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/send-list")
    public ResponseEntity<?> sendList(
            @Valid @RequestBody SendListRequest dto,
            HttpServletRequest request) {

        ResponseEntity<?> validation = validateRequest(dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful())
            return validation;

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        String apiKey = (String) request.getAttribute("clientApiKey"); // ✅ pega do filter

        Map<String, Object> body = Map.of(
                "phone", dto.getPhone(),
                "isGroup", dto.isGroup(),
                "buttonText", dto.getButtonText(),
                "description", dto.getDescription() != null ? dto.getDescription() : "",
                "sections", convertSections(dto.getSections()));

        Map<?, ?> response = wppService.sendListMessage(dto.getSession(), token, body);

        usageService.increment(apiKey, 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Send interactive buttons", description = "Send WhatsApp buttons message (legacy but still supported). API key must be in header 'X-Api-Key'")
    @PostMapping("/send-buttons")
    public ResponseEntity<?> sendButtons(
            @Valid @RequestBody SendButtonsRequest dto,
            HttpServletRequest request) {

        ResponseEntity<?> validation = validateRequest(dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful())
            return validation;

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        String apiKey = (String) request.getAttribute("clientApiKey");

        Map<String, Object> body = Map.of(
                "phone", dto.getPhone(),
                "isGroup", dto.isGroup(),
                "message", dto.getMessage(),
                "title", dto.getTitle(),
                "buttons", dto.getButtons().stream()
                        .map(b -> Map.of(
                                "buttonId", b.getButtonId(),
                                "buttonText", b.getButtonText()))
                        .toList());

        Map<?, ?> response = wppService.sendButtons(dto.getSession(), token, body);

        usageService.increment(apiKey, 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Send poll message", description = "Send an interactive WhatsApp poll. API key must be in header 'X-Api-Key'")
    @PostMapping("/send-poll")
    public ResponseEntity<?> sendPoll(
            @Valid @RequestBody SendPollRequest dto,
            HttpServletRequest request) {

        ResponseEntity<?> validation = validateRequest(dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful())
            return validation;

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        String apiKey = (String) request.getAttribute("clientApiKey");

        Map<String, Object> body = Map.of(
                "phone", dto.getPhone(),
                "isGroup", dto.isGroup(),
                "name", dto.getName(),
                "choices", dto.getChoices(),
                "options", dto.getOptions() != null ? dto.getOptions() : Map.of("selectableCount", 1));

        Map<?, ?> response = wppService.sendPollMessage(dto.getSession(), token, body);

        usageService.increment(apiKey, 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Send reply buttons", description = "Send WhatsApp quick reply buttons. API key must be in header 'X-Api-Key'")
    @PostMapping("/send-reply")
    public ResponseEntity<?> sendReply(
            @Valid @RequestBody SendReplyRequest dto,
            HttpServletRequest request) {

        ResponseEntity<?> validation = validateRequest(dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful())
            return validation;

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        String apiKey = (String) request.getAttribute("clientApiKey");

        Map<String, Object> body = Map.of(
                "phone", dto.getPhone(),
                "isGroup", dto.isGroup(),
                "message", dto.getMessage(),
                "options", Map.of(
                        "buttons", dto.getButtons().stream()
                                .map(b -> Map.of(
                                        "buttonId", b.getId(),
                                        "buttonText", b.getText()))
                                .toList()));

        Map<?, ?> response = wppService.sendReply(dto.getSession(), token, body);

        usageService.increment(apiKey, 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(response);
    }

    /*
     * =========================
     * Shared validation logic
     * =========================
     */

    private ResponseEntity<?> validateRequest(String sessionName) {
        var sessionOpt = sessionRepository.findBySessionName(sessionName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity session = sessionOpt.get();

        if (session.getWppToken() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing"));
        }

        return ResponseEntity.ok(Map.of(
                "session", session,
                "token", session.getWppToken()));
    }

    private Object convertSections(List<ListSection> sections) {
        return sections.stream()
                .map(s -> Map.of(
                        "title", s.getTitle(),
                        "rows", s.getRows().stream()
                                .map(r -> Map.of(
                                        "rowId", r.getRowId(),
                                        "title", r.getTitle(),
                                        "description", r.getDescription()))
                                .toList()))
                .toList();
    }
}
