package com.hotel.backoffice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class TimezoneConfig {

    @Value("${app.timezone:America/Lima}")
    private String timezone;

    @PostConstruct
    void configurarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(timezone));
    }
}