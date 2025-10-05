package io.ryhunwashere.auditlogger.handler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AuthHandler implements HttpHandler {
    private final RoutingHandler routes;
    private final JWTVerifier verifier;
    private final Set<String> publicRoutes;

    public AuthHandler(RoutingHandler routes, String secret, String issuer, Set<String> publicRoutes) {
        this.routes = routes;
        this.publicRoutes = publicRoutes;

        Algorithm algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .withClaim("role", "mc-server")
                .build();
    }

    @Override
    public void handleRequest(@NotNull HttpServerExchange exchange) throws Exception {
        String route = exchange.getRequestPath();
        if (publicRoutes.contains(route)) {
            routes.handleRequest(exchange);
            return;
        }

        String authHeader = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.setStatusCode(401);
            exchange.getResponseSender().send("Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring("Bearer ".length());
        try {
            verifier.verify(token);
            routes.handleRequest(exchange);
        } catch (JWTVerificationException e) {
            exchange.setStatusCode(401);
            exchange.getResponseSender().send("Invalid or expired token");
        }
    }
}
