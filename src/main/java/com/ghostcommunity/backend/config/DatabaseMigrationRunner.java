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

            // 2. Sections message count
            jdbcTemplate.execute("ALTER TABLE sections ADD COLUMN IF NOT EXISTS message_count integer DEFAULT 0;");
            jdbcTemplate.execute("UPDATE sections SET message_count = 0 WHERE message_count IS NULL;");

            // 3. Reports table self-healing for universal reporting
            try {
                jdbcTemplate.execute("ALTER TABLE reports ALTER COLUMN reported_message_id DROP NOT NULL;");
            } catch (Exception ignored) {}
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS direct_message_id bigint;");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS chat_type varchar(30) DEFAULT 'COMMUNITY';");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS message_content text;");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS media_url varchar(500);");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS media_type varchar(20);");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS section_name varchar(100);");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS admin_action varchar(50) DEFAULT 'NONE';");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS admin_notes varchar(500);");
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS resolved_at timestamp;");

            // 4. Admin flag for users & ensure at least the first user is an admin
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin boolean DEFAULT false;");
            try {
                jdbcTemplate.execute("UPDATE users SET is_admin = true WHERE id = (SELECT min(id) FROM users);");
            } catch (Exception ignored) {}

            // 5. Posts table self-healing for video, media type, pinned admin posts, and nullable college
            try {
                jdbcTemplate.execute("ALTER TABLE posts ALTER COLUMN college_id DROP NOT NULL;");
            } catch (Exception ignored) {}
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN IF NOT EXISTS video_url varchar(500);");
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN IF NOT EXISTS media_type varchar(20);");
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN IF NOT EXISTS is_pinned boolean DEFAULT false;");

            log.info("Schema self-healing completed successfully!");
        } catch (Exception e) {
            log.warn("Schema self-healing migration message: {}", e.getMessage());
        }
    }
}
