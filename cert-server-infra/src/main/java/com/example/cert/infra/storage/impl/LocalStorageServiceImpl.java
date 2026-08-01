package com.example.cert.infra.storage.impl;

import com.example.cert.infra.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements FileStorageService {

    @Value("${storage.local.base-path:.cert-storage}")
    private String basePath;

    @Override
    public String upload(InputStream inputStream, String fileName, long contentLength) {
        try {
            Path dir = Paths.get(basePath);
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String upload(byte[] data, String fileName) {
        try {
            Path dir = Paths.get(basePath);
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Files.write(target, data);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }
}
