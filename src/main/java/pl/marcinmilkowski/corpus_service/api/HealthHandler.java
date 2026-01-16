package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.SearchService;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * GET /health
 *
 * Returns service health status and statistics.
 */
public class HealthHandler extends HttpServlet {

    private final SearchService searchService;
    private final Gson gson = new Gson();
    private final long startTime;

    public HealthHandler(SearchService searchService) {
        this.searchService = searchService;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;

        HealthResponse health = new HealthResponse(
                "ok",
                searchService.getDocCount(),
                heapUsed,
                heapMax,
                uptimeSeconds
        );

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(health));
    }

    record HealthResponse(
            String status,
            int docs,
            long heapUsedMb,
            long heapMaxMb,
            long uptimeSeconds
    ) {}
}
