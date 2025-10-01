package io.ryhunwashere.auditlogger;

import io.ryhunwashere.auditlogger.handler.AuthHandler;
import io.ryhunwashere.auditlogger.handler.LogHandler;
import io.ryhunwashere.auditlogger.handler.TokenHandler;
import io.ryhunwashere.auditlogger.process.LogBatcher;
import io.ryhunwashere.auditlogger.process.LogDAO;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static Undertow server;

    public static void main(String[] args) {
        startServer();
    }

    public static void startServer() {
        try (ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            initServer(vtExecutor);
            if (server == null)
                throw new IllegalStateException("Cannot start server due to server not initialized yet.");
            server.start();
            System.out.println("Started on http://" + DEFAULT_HOST + ":" + DEFAULT_PORT + "/");
        }
    }

    private static void initServer(ExecutorService vtExecutor) {
        PropsLoader.loadProperties("/config.properties");

        String secret = PropsLoader.getString("auth.secret");
        String issuer = PropsLoader.getString("auth.issuer");

        if (secret == null)
            throw new IllegalStateException("Missing 'auth.secret' in config properties file.");
        if (issuer == null)
            throw new IllegalStateException("Missing 'auth.issuer' in config properties file.");

        String mainTableName = PropsLoader.getString("db.mainTableName");
        String fallbackTableName = PropsLoader.getString("db.fallbackTableName");

        LogDAO logDao = new LogDAO(mainTableName, fallbackTableName);
        int batchSize = PropsLoader.getInt("db.batchSize");

        LogBatcher logBatcher = new LogBatcher(logDao, vtExecutor, batchSize);

        RoutingHandler routes = new RoutingHandler()
                .post("/logs", new LogHandler(logBatcher))
                .post("/token", new TokenHandler(secret, issuer));

        Set<String> publicRoutes = Set.of("/token");
        AuthHandler authHandler = new AuthHandler(routes, secret, issuer, publicRoutes);

        int port = PropsLoader.getInt("server.port", DEFAULT_PORT);
        String host = PropsLoader.getString("server.host", DEFAULT_HOST);

        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(authHandler)
                .build();
    }
}
