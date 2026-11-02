package com.sevis.ordersservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    public static final class InFlight extends RuntimeException {
        public InFlight() { super("A request with this Idempotency-Key is still being processed"); }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private volatile boolean ready;

    private void ensure() {
        if (ready) return;
        jdbc.execute("CREATE TABLE IF NOT EXISTS idempotency_keys (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, dealer_id BIGINT NOT NULL, idem_key VARCHAR(120) NOT NULL, endpoint VARCHAR(40) NOT NULL, response_json MEDIUMTEXT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_idem (dealer_id, endpoint, idem_key))");
        ready = true;
    }

    public <T> String runJson(Long dealerId, String endpoint, String key, Supplier<T> action) {
        try {
            if (key == null || key.isBlank()) return mapper.writeValueAsString(action.get());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        ensure();
        String k = key.length() > 120 ? key.substring(0, 120) : key;
        try {
            jdbc.update("INSERT INTO idempotency_keys (dealer_id, endpoint, idem_key) VALUES (?, ?, ?)", dealerId, endpoint, k);
        } catch (DuplicateKeyException e) {
            List<String> rows = jdbc.queryForList("SELECT response_json FROM idempotency_keys WHERE dealer_id = ? AND endpoint = ? AND idem_key = ?", String.class, dealerId, endpoint, k);
            if (rows.isEmpty() || rows.get(0) == null) throw new InFlight();
            return rows.get(0);
        }
        try {
            String json = mapper.writeValueAsString(action.get());
            jdbc.update("UPDATE idempotency_keys SET response_json = ? WHERE dealer_id = ? AND endpoint = ? AND idem_key = ?", json, dealerId, endpoint, k);
            return json;
        } catch (RuntimeException e) {
            jdbc.update("DELETE FROM idempotency_keys WHERE dealer_id = ? AND endpoint = ? AND idem_key = ?", dealerId, endpoint, k);
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
