package com.oldbutgold.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class OgShopApplication {

    static {
        loadDotEnv();
    }

    public static void main(String[] args) {
        SpringApplication.run(OgShopApplication.class, args);
    }

    static void loadDotEnv() {
        Path[] possiblePaths = new Path[]{
                Paths.get(".env"),
                Paths.get("backend/.env"),
                Paths.get("O.G Shop/.env"),
                Paths.get("O.G Shop/backend/.env"),
                Paths.get("../.env"),
                Paths.get("../backend/.env"),
                Paths.get("../O.G Shop/.env"),
                Paths.get("../O.G Shop/backend/.env"),
                Paths.get("../../.env"),
                Paths.get("../../O.G Shop/.env"),
                Paths.get("../../O.G Shop/backend/.env")
        };

        boolean loaded = false;
        for (Path path : possiblePaths) {
            Path abs = path.toAbsolutePath().normalize();
            if (Files.exists(abs) && !Files.isDirectory(abs)) {
                try {
                    List<String> lines = Files.readAllLines(abs);
                    System.out.println("[ENV] Loading environment from: " + abs);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String value = line.substring(eqIdx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if ("SPRING_PROFILES_ACTIVE".equalsIgnoreCase(key) || "spring.profiles.active".equalsIgnoreCase(key)) {
                                if (System.getProperty("spring.profiles.active") == null && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
                                    System.setProperty("spring.profiles.active", value);
                                    System.setProperty("SPRING_PROFILES_ACTIVE", value);
                                    System.out.println("[ENV] Active Profile: " + value);
                                }
                            } else if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    loaded = true;
                    break;
                } catch (IOException ignored) {
                }
            }
        }
        if (!loaded) {
            System.err.println("[ENV] WARNING: No .env file found in standard locations. Falling back to defaults.");
        }
    }
}
