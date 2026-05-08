package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService {

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_TYPES = List.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    @Value("${app.upload.dir:uploads/profile-pictures}")
    private String uploadDirProperty;

    private Path uploadDir;

    @PostConstruct
    public void init() throws IOException {
        uploadDir = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    public String salvarFoto(MultipartFile file, Long userId) throws IOException {
        validateFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String filename = "user_" + userId + "_" + UUID.randomUUID() + "." + extension;

        Path destination = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        return "/api/uploads/profile-pictures/" + filename;
    }

    public void deletarFoto(String fotoUrl) {
        if (fotoUrl == null || fotoUrl.isBlank()) return;
        try {
            String filename = fotoUrl.substring(fotoUrl.lastIndexOf('/') + 1);
            Path file = uploadDir.resolve(filename);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Log but don't fail; orphan files are non-critical
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo não pode ser vazio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Arquivo excede o tamanho máximo de 5 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use JPEG, PNG, GIF ou WebP");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
