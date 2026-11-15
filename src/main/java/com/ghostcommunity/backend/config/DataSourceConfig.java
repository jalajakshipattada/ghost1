package com.ghostcommunity.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Slf4j
@Configuration
public class DataSourceConfig {

    @Value("${SPRING_DATASOURCE_URL:${DATABASE_URL:jdbc:postgresql://localhost:5432/ghost}}")
    private String rawUrl;

    @Value("${SPRING_DATASOURCE_USERNAME:${DATABASE_USERNAME:postgres}}")
    private String defaultUsername;

    @Value("${SPRING_DATASOURCE_PASSWORD:${DATABASE_PASSWORD:root}}")
    private String defaultPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        String clean = rawUrl != null ? rawUrl.trim() : "";
        if (clean.startsWith("jdbc:")) {
            clean = clean.substring(5);
        }
        if (!clean.startsWith("postgresql://") && !clean.startsWith("postgres://")) {
            clean = "postgresql://" + clean;
        }
        clean = clean.replaceFirst("^postgres://", "postgresql://");

        String username = defaultUsername;
        String password = defaultPassword;
        String jdbcUrl;

        try {
            URI uri = new URI(clean);
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                String[] parts = uri.getUserInfo().split(":", 2);
                username = parts[0];
                if (parts.length > 1) {
                    password = parts[1];
                }
            }

            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath() != null && !uri.getPath().isEmpty() ? uri.getPath() : "/ghost";
            String query = uri.getQuery();

            // Auto-append sslmode=require for cloud PostgreSQL (e.g. render.com) if not present
            if ((query == null || !query.contains("sslmode")) && host != null && host.contains(".render.com")) {
                query = (query == null || query.isEmpty()) ? "sslmode=require" : query + "&sslmode=require";
            }

            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + (query != null && !query.isEmpty() ? "?" + query : "");
        } catch (Exception e) {
            log.warn("Could not parse database URL as URI: {}. Falling back to raw URL.", rawUrl, e);
            jdbcUrl = rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl;
        }

        log.info("DataSource configured successfully. JDBC URL: {}, Username: {}", jdbcUrl, username);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(20000);
        config.setInitializationFailTimeout(-1);

        return new HikariDataSource(config);
    }
}
