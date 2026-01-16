package pl.marcinmilkowski.corpus_service;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.marcinmilkowski.corpus_service.api.*;

import java.nio.file.Path;

/**
 * Main entry point for the Corpus Lucene Service.
 *
 * Usage:
 *   java -jar corpus-service.jar serve --index /path/to/index --port 8081
 *   java -jar corpus-service.jar build --jdbc jdbc:postgresql://... --index /path/to/index
 */
public class CorpusServer {

    private static final Logger log = LoggerFactory.getLogger(CorpusServer.class);

    private static final int DEFAULT_PORT = 8081;

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];

        try {
            switch (command) {
                case "serve" -> runServer(args);
                case "build" -> runBuild(args);
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            log.error("Fatal error", e);
            System.exit(1);
        }
    }

    private static void runServer(String[] args) throws Exception {
        String indexPath = null;
        int port = DEFAULT_PORT;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--index" -> indexPath = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
            }
        }

        if (indexPath == null) {
            System.err.println("Missing --index parameter");
            System.exit(1);
        }

        log.info("Starting Corpus Lucene Service");
        log.info("Index path: {}", indexPath);
        log.info("Port: {}", port);

        SearchService searchService = new SearchService(Path.of(indexPath));

        // Bind to all interfaces (0.0.0.0) for WSL2/remote access
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("0.0.0.0");
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        context.addServlet(new ServletHolder(new CountHandler(searchService)), "/count");
        context.addServlet(new ServletHolder(new CompareHandler(searchService)), "/compare");
        context.addServlet(new ServletHolder(new ConcordanceHandler(searchService)), "/concordance");
        context.addServlet(new ServletHolder(new ParallelHandler(searchService)), "/parallel");
        context.addServlet(new ServletHolder(new HealthHandler(searchService)), "/health");
        context.addServlet(new ServletHolder(new OptimizeHandler(searchService)), "/optimize");
        context.addServlet(new ServletHolder(new ClearHandler(searchService)), "/clear");

        server.setHandler(context);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            try {
                searchService.close();
                server.stop();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }));

        server.start();
        log.info("Server started on port {}", port);
        log.info("Documents indexed: {}", searchService.getDocCount());

        server.join();
    }

    private static void runBuild(String[] args) throws Exception {
        String jdbcUrl = null;
        String user = null;
        String password = null;
        String indexPath = null;
        String query = "SELECT source_text, target_text FROM public.parallel_corpus";

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--jdbc" -> jdbcUrl = args[++i];
                case "--user" -> user = args[++i];
                case "--password" -> password = args[++i];
                case "--index" -> indexPath = args[++i];
                case "--query" -> query = args[++i];
            }
        }

        if (jdbcUrl == null || indexPath == null) {
            System.err.println("Missing required parameters: --jdbc and --index");
            System.exit(1);
        }

        if (user == null) user = "";
        if (password == null) password = "";

        IndexBuilder builder = new IndexBuilder(jdbcUrl, user, password, Path.of(indexPath));
        long count = builder.build(query);

        log.info("Build complete: {} documents indexed", count);
    }

    private static void printUsage() {
        System.out.println("Corpus Lucene Service");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  corpus-service serve --index <path> [--port 8081]");
        System.out.println("  corpus-service build --jdbc <url> --index <path> [--user <user>] [--password <pass>] [--query <sql>]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar corpus-service.jar serve --index /data/corpus-index --port 8081");
        System.out.println("  java -jar corpus-service.jar build --jdbc jdbc:postgresql://localhost/dictionary_analytics \\");
        System.out.println("       --user dict_user --password dict_pass --index /data/corpus-index");
    }
}
