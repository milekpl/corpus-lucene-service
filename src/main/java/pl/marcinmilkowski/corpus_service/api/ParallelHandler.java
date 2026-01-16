package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.SearchService;
import pl.marcinmilkowski.corpus_service.SearchService.ConcordanceResult;

import java.io.IOException;
import java.util.List;

/**
 * GET /parallel?en={term}&pl={term}&limit=20
 *
 * Finds sentences containing both English and Polish terms.
 */
public class ParallelHandler extends HttpServlet {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 500;

    private final SearchService searchService;
    private final Gson gson = new Gson();

    public ParallelHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String enTerm = req.getParameter("en");
        String plTerm = req.getParameter("pl");
        String limitStr = req.getParameter("limit");

        if (enTerm == null || enTerm.isBlank()) {
            sendError(resp, 400, "Missing 'en' parameter");
            return;
        }

        if (plTerm == null || plTerm.isBlank()) {
            sendError(resp, 400, "Missing 'pl' parameter");
            return;
        }

        int limit = parseIntOrDefault(limitStr, DEFAULT_LIMIT);
        limit = Math.min(limit, MAX_LIMIT);

        try {
            ConcordanceResult result = searchService.parallel(enTerm, plTerm, limit);
            sendJson(resp, new ParallelResponse(
                    result.total(),
                    result.hits().stream()
                            .map(h -> new HitResponse(h.en(), h.pl()))
                            .toList()
            ));
        } catch (Exception e) {
            sendError(resp, 500, "Search error: " + e.getMessage());
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
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

    record HitResponse(String en, String pl) {}
    record ParallelResponse(int total, List<HitResponse> hits) {}
    record ErrorResponse(String error) {}
}
