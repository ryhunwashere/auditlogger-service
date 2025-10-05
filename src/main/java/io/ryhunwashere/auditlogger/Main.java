package io.ryhunwashere.auditlogger;

import io.ryhunwashere.auditlogger.dao.LogsDAO;
import io.ryhunwashere.auditlogger.handler.AuthHandler;
import io.ryhunwashere.auditlogger.handler.LogsHandler;
import io.ryhunwashere.auditlogger.handler.TokenHandler;
import io.ryhunwashere.auditlogger.process.LogsManager;
import io.ryhunwashere.auditlogger.util.PropsLoader;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static Undertow server;

    static void main() {
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
        PropsLoader.initialize(Map.of(
                "auditconfig", "/auditconfig.properties"
        ));

        String secret = PropsLoader.getConfig("auditconfig").getString("auth.secret");
        String issuer = PropsLoader.getConfig("auditconfig").getString("auth.issuer");

        if (secret == null)
            throw new IllegalStateException("Missing 'auth.secret' in config properties file.");
        if (issuer == null)
            throw new IllegalStateException("Missing 'auth.issuer' in config properties file.");

        String mainTableName = PropsLoader.getConfig("auditconfig").getString("db.mainLogsTableName");
        String fallbackTableName = PropsLoader.getConfig("auditconfig").getString("db.fallbackLogsTableName");

        LogsDAO logsDao = new LogsDAO(mainTableName, fallbackTableName);
        int batchSize = PropsLoader.getConfig("auditconfig").getInt("db.logsBatchSize");
        LogsManager logsManager = new LogsManager(logsDao, vtExecutor, batchSize);
        LogsHandler logsHandler = new LogsHandler(logsManager, vtExecutor);

        RoutingHandler routes = new RoutingHandler()
                .get("/logs", logsHandler)
                .post("/logs", logsHandler)
                .post("/token", new TokenHandler(secret, issuer, vtExecutor));
        Set<String> publicRoutes = Set.of("/token");
        AuthHandler authHandler = new AuthHandler(routes, secret, issuer, publicRoutes);

        int port = PropsLoader.getConfig("auditconfig").getInt("server.port", DEFAULT_PORT);
        String host = PropsLoader.getConfig("auditconfig").getString("server.host", DEFAULT_HOST);

        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(authHandler)
                .build();
    }
}
