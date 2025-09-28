package io.ryhunwashere.auditlogger.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ryhunwashere.auditlogger.process.LogBatcher;
import io.ryhunwashere.auditlogger.process.LogDTO;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handles data retrieval in a form of JSON invoked from a POST endpoint.
 */
public class LogHandler implements HttpHandler {
    private final LogBatcher batcher;
    private final ObjectMapper mapper;

    public LogHandler(LogBatcher batcher) {
        this.batcher = batcher;
        this.mapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build()
                .registerModule(new JavaTimeModule());
    }

    @Override
    public void handleRequest(@NotNull HttpServerExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            exchange.setStatusCode(415);
            exchange.getResponseSender()
                    .send("{\"status\":\"error\",\"message\":\"Content-Type must be application/json\"}");
            return;
        }

        if (exchange.getRequestMethod().equals(Methods.POST)) {
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
                    e.printStackTrace();
                    ex.setStatusCode(400);
                    ex.getResponseSender().send("{\"status\":\"error\",\"message\":\"Invalid JSON format!\"}");
                }
            });
        } else {
            exchange.setStatusCode(405);
            exchange.getResponseSender().send("{\"status\":\"error\",\"message\":\"Only POST method allowed!\"}");
        }
    }
}
