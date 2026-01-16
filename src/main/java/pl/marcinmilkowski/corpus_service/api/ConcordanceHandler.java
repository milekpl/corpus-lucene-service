package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.SearchService;
import pl.marcinmilkowski.corpus_service.SearchService.ConcordanceHit;
import pl.marcinmilkowski.corpus_service.SearchService.ConcordanceResult;

import java.io.IOException;
import java.util.List;

/**
 * GET /concordance?q={term}&field={en|pl}&limit=50&offset=0
 */
public class ConcordanceHandler extends HttpServlet {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 1000;

    private final SearchService searchService;
    private final Gson gson = new Gson();

    public ConcordanceHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = req.getParameter("q");
        String field = req.getParameter("field");
        String limitStr = req.getParameter("limit");
        String offsetStr = req.getParameter("offset");

        if (query == null || query.isBlank()) {
            sendError(resp, 400, "Missing 'q' parameter");
            return;
        }

        if (field == null || field.isBlank()) {
            field = "en";
        }

        if (!field.equals("en") && !field.equals("pl")) {
            sendError(resp, 400, "Field must be 'en' or 'pl'");
            return;
        }

        int limit = parseIntOrDefault(limitStr, DEFAULT_LIMIT);
        int offset = parseIntOrDefault(offsetStr, 0);

        limit = Math.min(limit, MAX_LIMIT);
        offset = Math.max(offset, 0);

        try {
            ConcordanceResult result = searchService.concordance(query, field, limit, offset);
            sendJson(resp, new ConcordanceResponse(
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
    record ConcordanceResponse(int total, List<HitResponse> hits) {}
    record ErrorResponse(String error) {}
}
