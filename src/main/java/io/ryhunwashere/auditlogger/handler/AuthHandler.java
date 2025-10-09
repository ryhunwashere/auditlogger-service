package io.ryhunwashere.auditlogger.handler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ExecutorService;

public class AuthHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);
    private final RoutingHandler routes;
    private final JWTVerifier verifier;
    private final Set<String> publicRoutes;
    private final ExecutorService vt;

    public AuthHandler(RoutingHandler routes, String secret, String issuer, Set<String> publicRoutes, ExecutorService vt) {
        this.routes = routes;
        this.publicRoutes = publicRoutes;
        this.vt = vt;

        Algorithm algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .withClaim("role", "mc-server")
                .build();
    }

    @Override
    public void handleRequest(@NotNull HttpServerExchange exchange) {
        vt.submit(() -> {
            String route = exchange.getRequestPath();
            if (publicRoutes.contains(route)) {
                try {
                    routes.handleRequest(exchange);
                    return;
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    exchange.getResponseSender().send("An error occured.");
                    log.error(e.getMessage());
                }
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
            } catch (Exception e) {
                exchange.setStatusCode(401);
                exchange.getResponseSender().send("Invalid or expired token");
            }
        });
    }
}
