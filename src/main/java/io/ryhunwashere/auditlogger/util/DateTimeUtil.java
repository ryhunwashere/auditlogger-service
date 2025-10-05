package io.ryhunwashere.auditlogger.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {
    private final static String DEFAULT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private final static String DEFAULT_ZONE_ID = "Asia/Jakarta";

    public static Instant stringToInstant(String dt, String format, String timezone) {
        LocalDateTime local = LocalDateTime.parse(dt, DateTimeFormatter.ofPattern(format));
        return local.atZone(ZoneId.of(timezone)).toInstant();
    }

    public static Instant stringToInstant(String dt, String format) {
        LocalDateTime local = LocalDateTime.parse(dt, DateTimeFormatter.ofPattern(format));
        return local.atZone(ZoneId.of(DEFAULT_ZONE_ID)).toInstant();
    }

    public static Instant stringToInstant(String dt) {
        try {
            LocalDateTime local = LocalDateTime.parse(dt, DateTimeFormatter.ofPattern(DEFAULT_FORMAT));
            return local.atZone(ZoneId.of(DEFAULT_ZONE_ID)).toInstant();
        } catch (Exception e) {
            return OffsetDateTime.parse(dt).toInstant();
        }
    }
}
