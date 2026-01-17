package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.marcinmilkowski.corpus_service.LanguageConfig;

import java.io.IOException;

/**
 * GET /languages
 *
 * Returns information about supported languages and their analyzers.
 */
public class LanguagesHandler extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String[] supported = LanguageConfig.getSupportedLanguages();
            String[] withNames = LanguageConfig.getSupportedLanguagesWithNames();

            sendJson(resp, new LanguagesResponse(supported, withNames));
        } catch (Exception e) {
            sendError(resp, 500, "Error: " + e.getMessage());
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

    record LanguagesResponse(String[] codes, String[] withNames) {}
    record ErrorResponse(String error) {}
}
