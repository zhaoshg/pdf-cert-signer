package com.example.cert.infra.storage;

import java.io.InputStream;

public interface FileStorageService {

    String upload(InputStream inputStream, String fileName, long contentLength);

    String upload(byte[] data, String fileName);
}
