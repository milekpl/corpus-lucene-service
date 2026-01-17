package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.SearchService;

import java.io.IOException;

/**
 * POST /optimize
 *
 * Optimizes the Lucene index by merging segments.
 * This improves search performance at the cost of index build time.
 */
public class OptimizeHandler extends HttpServlet {

    private final SearchService searchService;
    private final Gson gson = new Gson();

    public OptimizeHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            int segmentsBefore = searchService.getDocCount() > 0 ?
                    searchService.optimize() : 0;

            sendJson(resp, new OptimizeResponse(segmentsBefore));
        } catch (Exception e) {
            sendError(resp, 500, "Optimize error: " + e.getMessage());
        }
    }

    private void sendJson(HttpServletResponse resp, Object obj) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(obj));
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.getWriter().write(gson.toJson(new ErrorResponse(message)));
    }

    record OptimizeResponse(int segmentsMerged) {}
    record ErrorResponse(String error) {}
}
