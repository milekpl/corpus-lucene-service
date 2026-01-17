package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.SearchService;

import java.io.IOException;
import java.util.Set;

/**
 * GET /count?q={term}&field={lang}
 *
 * Returns the number of documents matching the query in the specified language field.
 */
public class CountHandler extends HttpServlet {

    private final SearchService searchService;
    private final Gson gson = new Gson();

    public CountHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = req.getParameter("q");
        String field = req.getParameter("field");

        if (query == null || query.isBlank()) {
            sendError(resp, 400, "Missing 'q' parameter");
            return;
        }

        Set<String> availableLangs = searchService.getSupportedLanguages();

        if (field == null || field.isBlank()) {
            // Default to source language
            field = searchService.getSourceLanguage();
        }

        if (!availableLangs.contains(field.toLowerCase())) {
            sendError(resp, 400, "Unsupported language: " + field +
                    ". Available: " + String.join(", ", availableLangs));
            return;
        }

        try {
            int count = searchService.count(query, field);
            sendJson(resp, new CountResponse(query, field, count));
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

    record CountResponse(String query, String field, int count) {}
    record ErrorResponse(String error) {}
}
