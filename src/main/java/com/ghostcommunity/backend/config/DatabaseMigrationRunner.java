package com.ghostcommunity.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("Running automatic database schema self-healing migration...");

            // 1. War points for users
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS war_points integer DEFAULT 100;");
            jdbcTemplate.execute("UPDATE users SET war_points = 100 WHERE war_points IS NULL;");

            // 2. Sections message count, total bricks, campus level
            jdbcTemplate.execute("ALTER TABLE sections ADD COLUMN IF NOT EXISTS message_count integer DEFAULT 0;");
            jdbcTemplate.execute("UPDATE sections SET message_count = 0 WHERE message_count IS NULL;");

            jdbcTemplate.execute("ALTER TABLE sections ADD COLUMN IF NOT EXISTS total_bricks integer DEFAULT 0;");
            jdbcTemplate.execute("UPDATE sections SET total_bricks = 0 WHERE total_bricks IS NULL;");

            jdbcTemplate.execute("ALTER TABLE sections ADD COLUMN IF NOT EXISTS campus_level integer DEFAULT 1;");
            jdbcTemplate.execute("UPDATE sections SET campus_level = 1 WHERE campus_level IS NULL;");

            // 3. Campus structures bricks required & bricks placed
            jdbcTemplate.execute("ALTER TABLE campus_structures ADD COLUMN IF NOT EXISTS bricks_required integer DEFAULT 10;");
            jdbcTemplate.execute("UPDATE campus_structures SET bricks_required = 10 WHERE bricks_required IS NULL;");

            jdbcTemplate.execute("ALTER TABLE campus_structures ADD COLUMN IF NOT EXISTS bricks_placed integer DEFAULT 0;");
            jdbcTemplate.execute("UPDATE campus_structures SET bricks_placed = 0 WHERE bricks_placed IS NULL;");

            log.info("Schema self-healing completed successfully!");
        } catch (Exception e) {
            log.warn("Schema self-healing migration message: {}", e.getMessage());
        }
    }
}
