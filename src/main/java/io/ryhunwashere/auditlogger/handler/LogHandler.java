package io.ryhunwashere.auditlogger.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ryhunwashere.auditlogger.process.LogBatcher;
import io.ryhunwashere.auditlogger.process.LogData;
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
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Override
    public void handleRequest(@NotNull HttpServerExchange exchange) {
        if (exchange.getRequestMethod().equals(Methods.POST)) {
            exchange.getRequestReceiver().receiveFullString((ex, json) -> {
                try {
                    if (json.trim().startsWith("[")) {    // If JSON have multiple objects
                        List<LogData> logs = mapper.readValue(json, new TypeReference<>() {
                        });
                        batcher.addLogs(logs);
                    } else {                            // If there's only 1 object
                        LogData log = mapper.readValue(json, LogData.class);
                        batcher.addLog(log);
                    }
                    ex.setStatusCode(202);
                    ex.getResponseSender().send("{\"status\":\"Accepted!\"}");

                } catch (Exception e) {
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
