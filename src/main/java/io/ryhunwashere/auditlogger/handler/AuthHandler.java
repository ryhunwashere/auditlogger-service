package io.ryhunwashere.auditlogger.handler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;

import java.util.Date;
import java.util.UUID;

public class AuthHandler implements HttpHandler {
    private final RoutingHandler routes;
    private final JWTVerifier verifier;
    private final String testToken;

    public String getTestToken() {
        return testToken;
    }

    public AuthHandler(RoutingHandler routes, String secret, String issuer) {
        this.routes = routes;
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);

            // Build a verifier (for incoming requests)
            this.verifier = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withClaim("role", "mc-server")
                    .build();

            // create a token for testing (copy to Postman)
            Date expiry = new Date(System.currentTimeMillis() + 60 * 60 * 1000); // 1 hour
            this.testToken = JWT.create()
                    .withIssuer(issuer)
                    .withIssuedAt(new Date())
                    .withExpiresAt(expiry)
                    .withClaim("role", "mc-server")
                    .withJWTId(UUID.randomUUID().toString())
                    .sign(algorithm);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize AuthHandler", e);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String authHeader = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.setStatusCode(401);
            exchange.getResponseSender().send("Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring("Bearer ".length());
        try {
            verifier.verify(token);
            routes.handleRequest(exchange);     // If valid, forward to real routes

        } catch (JWTVerificationException e) {
            exchange.setStatusCode(401);
            exchange.getResponseSender().send("Invalid or expired token");
        }
    }
}
