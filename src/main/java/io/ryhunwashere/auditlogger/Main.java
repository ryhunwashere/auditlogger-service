package io.ryhunwashere.auditlogger;

import io.ryhunwashere.auditlogger.handler.AuthHandler;
import io.ryhunwashere.auditlogger.handler.LogHandler;
import io.ryhunwashere.auditlogger.process.LogBatcher;
import io.ryhunwashere.auditlogger.process.LogDao;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "0.0.0.0";

    public static void main(String[] args) {
        try (ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            runServer(vtExecutor);
        }
    }

    private static void runServer(ExecutorService vtExecutor) {
        PropsLoader.loadProperties("config.properties");

        String secret = PropsLoader.getString("auth.secret");
        String issuer = PropsLoader.getString("auth.issuer");

        if (secret == null)
            throw new IllegalStateException("Missing 'auth.secret' in config properties file.");
        if (issuer == null)
            throw new IllegalStateException("Missing 'auth.issuer' in config properties file.");

        int batchSize = PropsLoader.getInt("db.batchSize");

        LogDao logDao = new LogDao();
        LogBatcher logBatcher = new LogBatcher(logDao, vtExecutor, batchSize);

        RoutingHandler routes = new RoutingHandler()
                .post("/logs", new LogHandler(logBatcher));

        AuthHandler authHandler = new AuthHandler(routes, secret, issuer);

        int port = PropsLoader.getInt("server.port", DEFAULT_PORT);
        String host = PropsLoader.getString("server.host", DEFAULT_HOST);

        Undertow server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(authHandler)
                .build();

        server.start();

        System.out.println("Started on http://" + host + ":" + port + "/");

        // !!! REMOVE THIS LINE BELOW ON PRODUCTION !!!
        System.out.println("Bearer: " + authHandler.getTestToken());
    }
}
