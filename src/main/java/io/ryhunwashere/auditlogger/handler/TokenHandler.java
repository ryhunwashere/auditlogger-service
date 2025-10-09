package io.ryhunwashere.auditlogger.handler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ryhunwashere.auditlogger.util.PropsLoader;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class TokenHandler implements HttpHandler {
    private final Algorithm algorithm;
    private final String issuer;
    private final String serverIssuer;
    private final String serverSecret;
    private final ObjectMapper mapper;
    private final ExecutorService vt;

    public TokenHandler(String secret, String issuer, ExecutorService vt) {
        this.vt = vt;
        this.issuer = issuer;
        algorithm = Algorithm.HMAC256(secret);
        serverIssuer = PropsLoader.getConfig("auditconfig").getString("auth.issuer");
        serverSecret = PropsLoader.getConfig("auditconfig").getString("auth.secret");
        mapper = new ObjectMapper();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
         vt.submit(() -> {
            if (!exchange.getRequestMethod().equals(Methods.POST)) {
                exchange.setStatusCode(403);
                exchange.getResponseSender().send("{\"status\":\"error\",\"message\":\"Only POST methods allowed.\"}");
                return;
            }

            exchange.getRequestReceiver().receiveFullString((ex, json) -> {
                Map<String, String> authMap;
                try {
                    authMap = mapper.readValue(json, new TypeReference<>() {
                    });
                } catch (JsonProcessingException e) {
                    ex.setStatusCode(400);
                    ex.getResponseSender().send("Bad request: " + e.getMessage());
                    return;
                }

                if (!authMap.containsKey("issuer") || !authMap.containsKey("secret")) {
                    ex.setStatusCode(400);
                    ex.getResponseSender().send("{\"status\":\"error\",\"message\":\"JSON must contain 'issuer' and 'secret'.\"}");
                    return;
                }

                String clientIssuer = authMap.get("issuer");
                String clientSecret = authMap.get("secret");
                if (!clientIssuer.equals(serverIssuer) || !clientSecret.equals(serverSecret)) {
                    ex.setStatusCode(401);
                    ex.getResponseSender().send("Invalid client credentials.");
                    return;
                }

                Date expiry = new Date(System.currentTimeMillis() + 60 * 60 * 1000); // 1 hour
                String token = JWT.create()
                        .withIssuer(this.issuer)
                        .withIssuedAt(new Date())
                        .withExpiresAt(expiry)
                        .withClaim("role", "mc-server")
                        .withJWTId(UUID.randomUUID().toString())
                        .sign(this.algorithm);
                ex.setStatusCode(200);
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                ex.getResponseSender().send("{\"token\":\"" + token + "\"}");
            });
         });
    }
}