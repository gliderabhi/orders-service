package com.sevis.ordersservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

// In-process (Caffeine) caching — this service runs as a single instance with
// no shared/replicated state, so there's no need for a centralized cache here;
// each cache below is evicted explicitly by the write paths that invalidate it
// (see @CacheEvict usages), with a short TTL as a backstop only.
//
// Order/job-card *status* is deliberately given short TTLs (15-30s) even where
// the corresponding write endpoint already evicts on every status change —
// customers/dealers/technicians expect that field to update close to
// instantly, so the TTL is only a backstop, never the primary invalidation
// path. Reads whose data can never carry a live status (slow-changing
// catalogue/roster-style data) get longer TTLs.
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                // ── Orders (status field on every response — short TTL backstop) ──
                buildCache("orderById", 20, TimeUnit.SECONDS, 1000),
                buildCache("orderList", 20, TimeUnit.SECONDS, 10),
                buildCache("ordersByUser", 20, TimeUnit.SECONDS, 500),

                // ── Job cards (status/billing mutate on many endpoints — short TTL) ──
                buildCache("jobCardById", 20, TimeUnit.SECONDS, 1000),
                buildCache("jobCardList", 20, TimeUnit.SECONDS, 200),

                // ── Invoices (immutable-ish once generated; evicted on regeneration) ──
                buildCache("invoiceById", 3, TimeUnit.MINUTES, 1000),
                buildCache("invoiceList", 1, TimeUnit.MINUTES, 200),
                buildCache("invoicesByJobCard", 1, TimeUnit.MINUTES, 1000),

                // ── Audit/reporting rollup — aggregate dashboard, not a single
                //    order's live status, so a short TTL alone is an acceptable
                //    backstop without wiring eviction into every write path
                //    across job cards/invoices/salaries.
                buildCache("auditSummary", 30, TimeUnit.SECONDS, 50),

                // ── Loyalty (points/tier act like an account balance) ──
                buildCache("loyaltyByCustomer", 30, TimeUnit.SECONDS, 1000),
                buildCache("loyaltyByPhone", 30, TimeUnit.SECONDS, 1000),

                // ── Technician roster (slow-changing staff data) ──
                buildCache("technicianList", 2, TimeUnit.MINUTES, 100),

                // ── Technician salary records (financial, infrequently read) ──
                buildCache("salaryByTechnician", 2, TimeUnit.MINUTES, 500),
                buildCache("salaryByMonthYear", 2, TimeUnit.MINUTES, 200),

                // ── Vehicles (slow-changing master data; new vehicles may take
                //    up to the TTL to appear in list views — accepted staleness) ──
                buildCache("vehicleById", 5, TimeUnit.MINUTES, 2000),
                buildCache("vehicleList", 1, TimeUnit.MINUTES, 200),
                // Job-card history per vehicle carries job-card status — short TTL
                buildCache("vehicleHistory", 20, TimeUnit.SECONDS, 1000)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long ttl, TimeUnit unit, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl, unit)
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }
}
