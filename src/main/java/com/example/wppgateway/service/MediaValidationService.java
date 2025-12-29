// MediaValidationService.java (opcional, mas recomendado)
package com.example.wppgateway.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Service
public class MediaValidationService {

    /**
     * Valida tamanho máximo de mídias (evita bloqueio)
     * WhatsApp limita: 16MB para documentos, 64MB para vídeos
     */
    public boolean validateMediaSize(String base64, long maxSizeBytes) {
        if (base64 == null)
            return false;

        // Calcular tamanho aproximado em bytes
        // base64: cada 4 caracteres = 3 bytes
        int length = base64.length();
        int padding = 0;

        if (base64.endsWith("==")) {
            padding = 2;
        } else if (base64.endsWith("=")) {
            padding = 1;
        }

        long estimatedBytes = (length * 3L / 4) - padding;

        return estimatedBytes <= maxSizeBytes;
    }

    /**
     * Valida tipos MIME comuns permitidos
     */
    public boolean isValidMimeType(String base64) {
        try {
            // Extrair MIME type do base64
            String header = base64.substring(0, Math.min(base64.length(), 100));

            // Verificar padrões comuns
            if (header.startsWith("/9j/") || header.startsWith("iVBORw0KGgo")) {
                return true; // JPEG ou PNG
            } else if (header.startsWith("JVBERi0")) {
                return true; // PDF
            } else if (header.startsWith("UklGR")) {
                return true; // WAV audio
            } else if (header.startsWith("SUQz")) {
                return true; // MP3
            }

            return true; // Para MVP, aceita tudo com warning
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Recomendações de tamanho por tipo
     */
    public long getMaxSizeForType(String mediaType) {
        switch (mediaType.toLowerCase()) {
            case "image":
                return 5 * 1024 * 1024; // 5MB
            case "audio":
                return 16 * 1024 * 1024; // 16MB
            case "document":
                return 100 * 1024 * 1024; // 100MB (WPPConnect permite)
            case "sticker":
                return 500 * 1024; // 500KB (recomendado para stickers)
            default:
                return 16 * 1024 * 1024; // 16MB default
        }
    }
}