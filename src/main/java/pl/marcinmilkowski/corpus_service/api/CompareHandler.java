package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.SearchService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * POST /compare
 * Body: { "terms": ["lifespan", "life span", "life-span"], "field": "en" }
 */
public class CompareHandler extends HttpServlet {

    private final SearchService searchService;
    private final Gson gson = new Gson();

    public CompareHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CompareRequest request;
        try {
            request = gson.fromJson(req.getReader(), CompareRequest.class);
        } catch (Exception e) {
            sendError(resp, 400, "Invalid JSON body");
            return;
        }

        if (request.terms == null || request.terms.isEmpty()) {
            sendError(resp, 400, "Missing 'terms' array");
            return;
        }

        String field = request.field != null ? request.field : "en";
        if (!field.equals("en") && !field.equals("pl")) {
            sendError(resp, 400, "Field must be 'en' or 'pl'");
            return;
        }

        try {
            Map<String, Integer> counts = searchService.compare(request.terms, field);

            List<TermResult> results = new ArrayList<>();
            String dominant = null;
            int maxCount = 0;

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                results.add(new TermResult(entry.getKey(), entry.getValue()));
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    dominant = entry.getKey();
                }
            }

            sendJson(resp, new CompareResponse(results, dominant));
        } catch (Exception e) {
            sendError(resp, 500, "Search error: " + e.getMessage());
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

    static class CompareRequest {
        List<String> terms;
        String field;
    }

    record TermResult(String term, int count) {}
    record CompareResponse(List<TermResult> results, String dominant) {}
    record ErrorResponse(String error) {}
}
