package com.example.cert.infra.ca;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SignerIdGenerator {

    private static final String PREFIX = "CERT";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generate() {
        String datePart = LocalDate.now().format(DATE_FMT);
        int random = ThreadLocalRandom.current().nextInt(0x1000, 0x10000);
        return PREFIX + "_" + datePart + "_" + Integer.toHexString(random).toUpperCase();
    }
}
