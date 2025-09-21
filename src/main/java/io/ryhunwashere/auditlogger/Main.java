package io.ryhunwashere.auditlogger;

import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final int PORT = 8080;
    private static final String HOST = "0.0.0.0";

    public static void main(String[] args) {
        try (ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            runServer(vtExecutor);
        }
    }

    private static void runServer(ExecutorService vtExecutor) {
        LogDao logDao = new LogDao();
        LogBatcher logBatcher = new LogBatcher(logDao, vtExecutor);

        logBatcher.initFallbackLogsCount();

        RoutingHandler routes = new RoutingHandler()
                .post("/logs", new LogHandler(logBatcher));

        ConfigLoader config = new ConfigLoader("config.json");

        String secret = config.getSecret();
        String issuer = config.getIssuer();

        if (secret == null)
            throw new IllegalStateException("Missing 'secret' in config file.");
        if (issuer == null)
            throw new IllegalStateException("Missing 'issuer' in config file.");

        AuthHandler authHandler = new AuthHandler(routes, secret, issuer);

        Undertow server = Undertow.builder()
                .addHttpListener(PORT, HOST)
                .setHandler(authHandler)
                .build();

        server.start();

        System.out.println("Started on http://" + HOST + ":" + PORT + "/");
        System.out.println("Bearer: " + authHandler.getTestToken());
    }
}
