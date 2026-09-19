package org.nors.dev.codes.lpu.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HealthControllerTest {

    @Test
    void health_upWhenPostgresIsValid() {
        ResponseEntity<Map<String, Object>> response = new HealthController(validDataSource(), null).health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals(true, response.getBody().get("postgres"));
        assertFalse(response.getBody().containsKey("redis"));
    }

    @Test
    void health_downWhenPostgresFails() {
        ResponseEntity<Map<String, Object>> response = new HealthController(failingDataSource(), null).health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
        assertTrue(response.getBody().get("postgres") instanceof Boolean);
        assertFalse((Boolean) response.getBody().get("postgres"));
    }

    private static DataSource validDataSource() {
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if ("isValid".equals(method.getName())) {
                        return true;
                    }
                    if ("close".equals(method.getName()) || "toString".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName()) && (args == null || args.length == 0)) {
                        return connection;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static DataSource failingDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName()) && (args == null || args.length == 0)) {
                        throw new SQLException("offline");
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }
}
