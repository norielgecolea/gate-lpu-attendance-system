package org.nors.dev.codes.lpu.controller;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness for Docker healthchecks and load-balancer probes. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redis;

    public HealthController(
            DataSource dataSource,
            @Autowired(required = false) StringRedisTemplate redis
    ) {
        this.dataSource = dataSource;
        this.redis = redis;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean postgres = pingPostgres();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("postgres", postgres);
        boolean ok = postgres;
        if (redis != null) {
            boolean redisOk = pingRedis();
            body.put("redis", redisOk);
            ok = ok && redisOk;
        }
        body.put("status", ok ? "UP" : "DOWN");
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean pingPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean pingRedis() {
        try {
            String pong = redis.execute(RedisConnection::ping);
            return pong != null && !pong.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }
}
