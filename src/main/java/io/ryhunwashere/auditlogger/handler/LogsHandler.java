package io.ryhunwashere.auditlogger.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ryhunwashere.auditlogger.dto.LogDTO;
import io.ryhunwashere.auditlogger.process.LogsManager;
import io.ryhunwashere.auditlogger.util.DateTimeUtil;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class LogsHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(LogsHandler.class);
    private final LogsManager batcher;
    private final ObjectMapper mapper;
    private final ExecutorService vt;

    public LogsHandler(LogsManager batcher, ExecutorService vt) {
        this.batcher = batcher;
        this.vt = vt;
        this.mapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build()
                .registerModule(new JavaTimeModule());
    }

    @Override
    public void handleRequest(@NotNull HttpServerExchange exchange) {
        vt.submit(() -> {
            HttpString method = exchange.getRequestMethod();
            if (method.equals(Methods.POST)) {
                postLogs(exchange);
            } else if (method.equals(Methods.GET)) {
                getLogs(exchange);
            } else {
                exchange.setStatusCode(405);
                exchange.getResponseSender()
                        .send("{\"status\":\"error\",\"message\":\"Only GET & POST methods allowed!\"}");
            }
        });
    }

    private void postLogs(@NotNull HttpServerExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE_STRING);
        if (contentType == null || !contentType.contains("application/json")) {
            exchange.setStatusCode(415);
            exchange.getResponseSender()
                    .send("{\"status\":\"error\",\"message\":\"Content-Type must be application/json\"}");
            return;
        }

        exchange.getRequestReceiver().receiveFullString((ex, json) -> {
            try {
                if (json.trim().startsWith("[")) { // If JSON have multiple objects
                    List<LogDTO> logs = mapper.readValue(json, new TypeReference<>() {
                    });
                    logs.forEach(LogDTO::generateLogUUID);
                    batcher.addLogs(logs);
                } else { // If there's only 1 object
                    LogDTO log = mapper.readValue(json, LogDTO.class);
                    log.generateLogUUID();
                    batcher.addLog(log);
                }
                ex.setStatusCode(202);
                ex.getResponseSender().send("{\"status\":\"Accepted!\"}");
            } catch (Exception e) {
                ex.setStatusCode(400);
                ex.getResponseSender().send("{\"status\":\"error\",\"message\":\"Invalid JSON format!\"}");
            }
        });
    }

    private void getLogs(@NotNull HttpServerExchange exchange) {
        Map<String, Deque<String>> params = exchange.getQueryParameters();

        String sinceStr = getParam(params, "since");
        String untilStr = getParam(params, "until");
        String limitStr = getParam(params, "limit");
        if (sinceStr == null || untilStr == null || limitStr == null) {
            exchange.setStatusCode(400);
            exchange.getResponseSender()
                    .send("{\"status\":\"error\",\"message\":\"Query must contain 'since', 'until', & 'limit'.\"}");
            return;
        }

        Instant since = DateTimeUtil.stringToInstant(sinceStr);
        Instant until = DateTimeUtil.stringToInstant(untilStr);
        Duration difference = Duration.between(since, until);
        if (difference.isNegative()) {
            exchange.setStatusCode(400);
            exchange.getResponseSender()
                    .send("{\"status\":\"error\",\"message\":\"'until' cannot be earlier than 'since'.\"}");
            return;
        }

        int limit = Integer.parseInt(limitStr);
        String playerUuidStr = getParam(params, "player_uuid");
        if (playerUuidStr != null) {
            UUID playerUuid = UUID.fromString(playerUuidStr);
            List<LogDTO> logDTOList = batcher.getLogsOfPlayer(playerUuid, since, until, limit);
            sendJson(exchange, logDTOList);
            return;
        }

        String world = getParam(params, "world");
        String radiusStr = getParam(params, "radius");
        String xStr = getParam(params, "x");
        String zStr = getParam(params, "z");
        if (world == null || radiusStr == null || xStr == null || zStr == null) {
            exchange.setStatusCode(400);
            exchange.getResponseSender()
                    .send("{\"status\":\"error\",\"message\":\"Query without playerUuid must contain 'world', 'radius', 'x', & 'z'.\"}");
        } else {
            double radius = Double.parseDouble(radiusStr);
            double x = Double.parseDouble(xStr);
            double z = Double.parseDouble(zStr);
            List<LogDTO> logDTOList = batcher.getLogsOnCurrentLoc(world, radius, x, z, since, until, limit);
            sendJson(exchange, logDTOList);
        }
    }

    private void sendJson(@NotNull HttpServerExchange exchange, List<LogDTO> logDTOList) {
        if (logDTOList == null) {
            exchange.setStatusCode(204);
            exchange.getResponseSender().send("No logs found.");
            return;
        }

        try {
            String jsonQueryResult = mapper.writeValueAsString(logDTOList);
            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(jsonQueryResult);
        } catch (JsonProcessingException e) {
            exchange.setStatusCode(400);
            exchange.getResponseSender()
                    .send("{\"status\":\"error\",\"message\":\"Error while serializing JSON!\"}");
            log.error(e.getMessage());
        }
    }

    private @Nullable String getParam(@NotNull Map<String, Deque<String>> params, String key) {
        try {
            return params.get(key).getFirst();
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
