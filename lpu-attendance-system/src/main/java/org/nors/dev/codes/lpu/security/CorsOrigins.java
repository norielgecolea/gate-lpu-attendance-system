package org.nors.dev.codes.lpu.security;

import java.util.List;

/** Browser origins allowed to call the API and open the notification socket. */
public final class CorsOrigins {

    public static final List<String> PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://attendance.norielgecolea.com",
            "https://attendance.lpulaguna.com",
            "https://rfidattendance.lpulaguna.com",
            "https://attendancev2.lpu-laguna.edu.ph"
    );

    private CorsOrigins() {
    }
}
