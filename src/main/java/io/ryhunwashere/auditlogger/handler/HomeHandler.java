package io.ryhunwashere.auditlogger.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class HomeHandler implements HttpHandler {
    private final ObjectMapper mapper;
    private static final Set<String> KEYS = Set.of("player_uuid", "home_name", "world", "x", "y", "z");

    public HomeHandler() {
        mapper = new ObjectMapper();
    }

    @Override
    public void handleRequest(@NotNull HttpServerExchange exchange) {
        String playerUuid = exchange.getPathParameters().get("player_uuid").getFirst();
        HttpString method = exchange.getRequestMethod();

        if (method.equals(Methods.GET)) {
            return;
        }

        if (method.equals(Methods.POST)) {
            String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE_STRING);
            if (contentType == null || !contentType.contains("application/json")) {
                exchange.setStatusCode(415);
                exchange.getResponseSender()
                        .send("Content-Type must be application/json");
                return;
            }

            exchange.getRequestReceiver().receiveFullString((ex, json) -> {
                Map<String, String> homeMap;
                try {
                    homeMap = mapper.readValue(json, new TypeReference<>() {
                    });
                } catch (JsonProcessingException e) {
                    ex.setStatusCode(400);
                    ex.getResponseSender().send("Bad request: " + e.getMessage());
                    return;
                }

                for (String key : KEYS) {
                    if (!homeMap.containsKey(key)) {
                        ex.setStatusCode(400);
                        ex.getResponseSender()
                                .send("JSON must contain '" + key + "' key.");
                        return;
                    }
                }
            });

            return;
        }

        // /delhome
        if (method.equals(Methods.DELETE)) {
            return;
        }

        exchange.setStatusCode(403);
        exchange.getResponseSender()
                .send("Only GET, POST, & DELETE methods allowed.");
    }
}
