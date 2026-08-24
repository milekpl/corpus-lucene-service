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
 * GET /parallel?source={term}&target={term}&limit=20&offset=0
 *
 * Finds sentences containing both terms from source and target languages.
 * Uses language codes from the index (e.g., ?en=hello&pl=halo).
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
        String sourceTerm = req.getParameter(searchService.getSourceLanguage());
        String targetTerm = req.getParameter(searchService.getTargetLanguage());
        String limitStr = req.getParameter("limit");
        String offsetStr = req.getParameter("offset");

        if (sourceTerm == null || sourceTerm.isBlank()) {
            sendError(resp, 400, "Missing '" + searchService.getSourceLanguage() + "' parameter");
            return;
        }

        if (targetTerm == null || targetTerm.isBlank()) {
            sendError(resp, 400, "Missing '" + searchService.getTargetLanguage() + "' parameter");
            return;
        }

        int limit = parseIntOrDefault(limitStr, DEFAULT_LIMIT);
        int offset = parseIntOrDefault(offsetStr, 0);
        limit = Math.min(limit, MAX_LIMIT);
        offset = Math.max(offset, 0);

        String sourceLang = searchService.getSourceLanguage();
        String targetLang = searchService.getTargetLanguage();

        try {
            ConcordanceResult result = searchService.parallel(sourceTerm, targetTerm, limit, offset);
            sendJson(resp, new ParallelResponse(
                    result.total(),
                    result.hits().stream()
                            .map(h -> new HitResponse(h.source(), h.target(), sourceLang, targetLang))
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

    record HitResponse(String source, String target, String sourceLang, String targetLang) {}
    record ParallelResponse(int total, List<HitResponse> hits) {}
    record ErrorResponse(String error) {}
}
