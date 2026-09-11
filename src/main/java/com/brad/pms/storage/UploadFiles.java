package com.brad.pms.storage;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Shared MIME and size checks for local disk and S3-compatible object storage. */
public final class UploadFiles {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private UploadFiles() {
    }

    public static String requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.error("请选择要上传的图片");
        if (file.getSize() > MAX_FILE_SIZE) throw BusinessException.error("图片大小不能超过 5MB");
        String declaredType = declaredType(file);
        String actualType = detectImageType(file);
        if (!IMAGE_TYPES.contains(actualType) || !actualType.equals(declaredType)) {
            throw BusinessException.error("文件类型与图片内容不匹配");
        }
        return actualType;
    }

    public static String requireAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.error("请选择要上传的附件");
        if (file.getSize() > MAX_FILE_SIZE) throw BusinessException.error("附件大小不能超过 5MB");
        String declaredType = declaredType(file);
        String actualType = detectImageType(file);
        if (actualType.isEmpty() && isPdf(file) && "application/pdf".equals(declaredType)) {
            actualType = "application/pdf";
        }
        if (actualType.isEmpty() || (!IMAGE_TYPES.contains(actualType) && !"application/pdf".equals(actualType))) {
            throw BusinessException.error("附件仅支持图片或 PDF");
        }
        if (!actualType.equals(declaredType)) {
            throw BusinessException.error("文件类型与内容不匹配");
        }
        return actualType;
    }

    public static String newKey(String contentType) {
        return UUID.randomUUID() + extensionOf(contentType);
    }

    public static boolean isSafeStorageKey(String key) {
        return key != null && key.matches("[0-9a-fA-F-]{36}\\.(png|jpg|gif|webp|pdf)");
    }

    public static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }

    static boolean isPdf(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            return header.length >= 5
                    && header[0] == '%' && header[1] == 'P' && header[2] == 'D'
                    && header[3] == 'F' && header[4] == '-';
        } catch (IOException ex) {
            throw BusinessException.error("无法读取上传文件");
        }
    }

    static String detectImageType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && (header[4] & 0xff) == 0x0d && (header[5] & 0xff) == 0x0a
                    && (header[6] & 0xff) == 0x1a && (header[7] & 0xff) == 0x0a) return "image/png";
            if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) {
                return "image/jpeg";
            }
            if (header.length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a') return "image/gif";
            if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "image/webp";
            return "";
        } catch (IOException ex) {
            throw BusinessException.error("无法读取上传文件");
        }
    }

    private static String declaredType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    }
}
