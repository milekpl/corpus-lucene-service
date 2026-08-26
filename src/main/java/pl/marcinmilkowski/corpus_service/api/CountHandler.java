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
        String syntax = req.getParameter("syntax");

        if (query == null || query.isBlank()) {
            sendError(resp, 400, "Missing 'q' parameter");
            return;
        }

        if (syntax != null
                && !SearchService.SYNTAX_SIMPLE.equalsIgnoreCase(syntax)
                && !SearchService.SYNTAX_LUCENE.equalsIgnoreCase(syntax)) {
            sendError(resp, 400, "Unsupported syntax: " + syntax +
                    ". Available: " + SearchService.SYNTAX_SIMPLE + ", " + SearchService.SYNTAX_LUCENE);
            return;
        }
        String resolvedSyntax = (syntax == null || syntax.isBlank())
                ? SearchService.SYNTAX_SIMPLE : syntax.toLowerCase();

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
            int count = searchService.count(query, field, resolvedSyntax);
            sendJson(resp, new CountResponse(query, field, resolvedSyntax, count));
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, e.getMessage());
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

    record CountResponse(String query, String field, String syntax, int count) {}
    record ErrorResponse(String error) {}
}
