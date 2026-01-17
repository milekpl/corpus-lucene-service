package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.SearchService;

import java.io.IOException;

/**
 * POST /clear
 *
 * Clears all documents from the index.
 * Use with caution - this operation cannot be undone.
 */
public class ClearHandler extends HttpServlet {

    private final SearchService searchService;
    private final Gson gson = new Gson();

    public ClearHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            int count = searchService.clear();
            sendJson(resp, new ClearResponse(count));
        } catch (Exception e) {
            sendError(resp, 500, "Clear error: " + e.getMessage());
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

    record ClearResponse(int documentsDeleted) {}
    record ErrorResponse(String error) {}
}
