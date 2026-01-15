package com.heureca.wppgateway.controller;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.UsageService;
import com.heureca.wppgateway.service.WppService;
import com.heureca.wppgateway.dto.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/media")
@Tag(name = "Media", description = "Send media messages via WhatsApp (image, file, voice and sticker)")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public MediaController(
            UsageService usageService,
            SessionUsageService sessionUsageService,
            SessionRepository sessionRepository,
            WppService wppService) {
        this.usageService = usageService;
        this.sessionUsageService = sessionUsageService;
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
    }

    /*
     * ==========================
     * Common validations
     * ==========================
     */
    private ResponseEntity<?> validateRequest(ApiClient client, String sessionName) {

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(sessionName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity session = sessionOpt.get();

        if (!client.getApiKey().equals(session.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "session does not belong to client"));
        }

        int clientUsed = usageService.getUsageToday(client.getApiKey());
        if (clientUsed + 1 > client.getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "client daily limit exceeded",
                    "limit", client.getDailyLimit(),
                    "used", clientUsed));
        }

        if (!sessionUsageService.canSendMessage(sessionName)) {
            int sessionUsed = sessionUsageService.getUsageToday(sessionName);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "session daily limit exceeded (anti-block protection)",
                    "limit", 450,
                    "used", sessionUsed,
                    "session", sessionName));
        }

        if (session.getWppToken() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "wpp token missing for session"));
        }

        return ResponseEntity.ok(Map.of(
                "token", session.getWppToken()));
    }

    private String extractSession(Map<String, Object> body) {
        Object sessionObj = body.get("session");
        if (sessionObj == null) {
            throw new IllegalArgumentException("missing session in request body");
        }
        return sessionObj.toString();
    }

    /*
     * ==========================
     * Send Image base64
     * ==========================
     */
    @Operation(summary = "Send image via base64", description = """
            Sends a image message using an existing WhatsApp session.

            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or base64"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Failed to send image")
    })
    @PostMapping("/send-image-base64")
    public ResponseEntity<?> sendImageBase64(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples
                    = @ExampleObject(name = "send-image-base64", summary = "send-image-base64 example", value = """
                    {
                        "session": "wpp_552199999999",
                        "session": "my-session",
                        "phone": "552199999999",
                        "isGroup": false,
                        "filename": "image.jpg",
                        "caption": "Hello",
                        "base64": "iVBORw0KGgoAAAANSUhEUgAA..."
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        String session = extractSession(body);
        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendImageBase64(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send Image path
     * ==========================
     */
    @Operation(summary = "Send image by file path", description = """
            Sends a image message using an existing WhatsApp session.
 
            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
           """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or file"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Failed to send image")
    })
    @PostMapping("/send-image")
    public ResponseEntity<?> sendImagePath(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples
                    = @ExampleObject(name = "send-image", summary = "send-image example", value = """
                    {
                        "session": "wpp_552199999999",
                        "session": "my-session",
                        "phone": "552199999999",
                        "isGroup": false,
                        "path": "/usr/src/app/media/image.jpg"
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        String session = extractSession(body);
        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendImagePath(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send File base64
     * ==========================
     */
    @Operation(summary = "Send file base64", description = """
            Sends a file message using an existing WhatsApp session.
            
            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
        """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File sent successfully"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Failed to send file")
    })
    @PostMapping("/send-file-base64")
    public ResponseEntity<?> sendFileBase64(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples
                    = @ExampleObject(name = "send-file-base64", summary = "send-file-base64 example", value = """
                    {
                        "session": "wpp_552199999999",
                        "phone": "5521999999999",
                        "isGroup": false,
                        "isNewsletter": false,
                        "isLid": false,
                        "filename": "file name lol",
                        "caption": "caption for my file",
                        "base64": "<base64> string"
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        String session = extractSession(body);
        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendFileBase64(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send File
     * ==========================
     */
    @Operation(summary = "Send file by path", description = """
            Sends a file message using an existing WhatsApp session.
            
            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File sent successfully"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Failed to send file")
    })

    @PostMapping("/send-file")
    public ResponseEntity<?> sendFilePath(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples
                    = @ExampleObject(name = "send-file", summary = "send-file example", value = """
                    {
                        "session": "wpp_552199999999",
                        "phone": "5521999999999",
                        "isGroup": false,
                        "isNewsletter": false,
                        "isLid": false,
                        "filename": "file name lol",
                        "caption": "caption for my file",
                        "path": "<path_file>"
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        String session = extractSession(body);
        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendFile(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }


    /*
 * ==========================
 * Send Voice (path / url)
 * ==========================
     */
    @Operation(
            summary = "Send voice message via file path or URL",
            description = """
            Sends a voice message using an existing WhatsApp session.
            
            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
                    
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Voice message sent"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Provider error")
    })
    @PostMapping("/send-voice")
    public ResponseEntity<?> sendVoice(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    name = "sendVoice",
                                    summary = "Send voice using file path",
                                    value = """
                                {
                                   "session": "wpp_5521999999999",
                                   "phone": "5521999999999",
                                   "isGroup": false,
                                   "path": "/tmp/audio.ogg",
                                   "quotedMessageId": "message Id"
                                }
                                """
                            )
                    )
            )
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        Object sessionObj = body.get("session");
        if (sessionObj == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing session in request body"));
        }

        String session = sessionObj.toString();

        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");

        // remove session before forwarding to provider
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendVoice(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }

    /*
 * ==========================
 * Send Voice (Base64 / PTT)
 * ==========================
     */
    @Operation(
            summary = "Send voice message via Base64 (PTT)",
            description = """
            Sends a voice message using an existing WhatsApp session.
            
            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
                    
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Voice message sent"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Provider error")
    })
    @PostMapping("/send-voice-base64")
    public ResponseEntity<?> sendVoiceBase64(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    name = "sendVoiceBase64",
                                    summary = "Send voice using base64 PTT",
                                    value = """
                                {
                                  "session": "wpp_552199999999",
                                  "phone": "5521999999999",
                                  "isGroup": false,
                                  "base64Ptt": "data:audio/ogg;base64,T2dnUwACAAAAAAAAAABVDx..."
                                }
                                """
                            )
                    )
            )
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        Object sessionObj = body.get("session");
        if (sessionObj == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing session in request body"));
        }

        String session = sessionObj.toString();

        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");

        // remove session before forwarding to provider
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendVoiceBase64(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }


    /*
     * ==========================
     * Send Sticker
     * ==========================
     */
    @Operation(summary = "Send a sticker by path", description = """
            Sends a sticker message using an existing WhatsApp session.
            
            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
                    
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File sent successfully"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Failed to send file")
    })
    @PostMapping("/send-sticker")
    public ResponseEntity<?> sendSticker(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples
                    = @ExampleObject(name = "send-sticker", summary = "send-sticker example", value = """
                    {
                        "session": "wpp_552199999999",
                        "phone": "5521999999999",
                        "isGroup": true,
                        "path": "<path_file>"
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        String session = extractSession(body);
        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendSticker(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send Sticker gif
     * ==========================
     */
    @Operation(
            summary = "Send animated sticker (GIF)",
            description = """
            Sends a animated sticker message using an existing WhatsApp session.
            
            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
                    
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File sent successfully"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
        @ApiResponse(responseCode = "409", description = "Session not ready"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Failed to send file")
    })

    @PostMapping("/send-sticker-gif")
    public ResponseEntity<?> sendStickerGif(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples
                    = @ExampleObject(name = "send-sticker", summary = "send-sticker example", value = """
                    {
                        "session": "wpp_552199999999",
                        "phone": "5521999999999",
                        "isGroup": true,
                        "path": "<path_file>"
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        String session = extractSession(body);
        ResponseEntity<?> validation = validateRequest(client, session);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");
        body.remove("session");

        ResponseEntity<?> resp = wppService.sendStickerGif(session, token, body);

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(session);

        return ResponseEntity.ok(resp);
    }


    /*
     * ==========================
     * Utils
     * ==========================
     */
    private boolean isValidBase64(String base64) {
        try {
            String clean = base64.contains(",") ? base64.split(",")[1] : base64;
            Base64.getDecoder().decode(clean);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
