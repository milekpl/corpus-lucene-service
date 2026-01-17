package pl.marcinmilkowski.corpus_service.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import pl.marcinmilkowski.corpus_service.LanguageConfig;
import pl.marcinmilkowski.corpus_service.SearchService;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * POST /upload
 *
 * Uploads parallel corpus data for indexing.
 *
 * Request body:
 * {
 *   "sourceLang": "en",
 *   "targetLang": "pl",
 *   "sentences": [
 *     {"source": "Hello world", "target": "Witaj swiecie"},
 *     {"source": "Good morning", "target": "Dzien dobry"}
 *   ]
 * }
 *
 * Query parameters:
 * - optimize=true (optional, merge segments after upload)
 */
public class UploadHandler extends HttpServlet {

    private final SearchService searchService;
    private final Gson gson = new Gson();

    public UploadHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().readLine();

        if (body == null || body.isBlank()) {
            sendError(resp, 400, "Missing request body");
            return;
        }

        // Parse request
        UploadRequest request;
        try {
            request = gson.fromJson(body, UploadRequest.class);
        } catch (Exception e) {
            sendError(resp, 400, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (request.sentences == null || request.sentences.isEmpty()) {
            sendError(resp, 400, "Missing 'sentences' array");
            return;
        }

        // Validate and normalize language codes
        String sourceLang = request.sourceLang != null ? request.sourceLang.toLowerCase() : searchService.getSourceLanguage();
        String targetLang = request.targetLang != null ? request.targetLang.toLowerCase() : searchService.getTargetLanguage();

        // Check if languages are supported
        if (!LanguageConfig.isSupported(sourceLang)) {
            sendError(resp, 400, "Unsupported source language: " + sourceLang +
                    ". Available: " + String.join(", ", LanguageConfig.getSupportedLanguages()));
            return;
        }

        if (!LanguageConfig.isSupported(targetLang)) {
            sendError(resp, 400, "Unsupported target language: " + targetLang +
                    ". Available: " + String.join(", ", LanguageConfig.getSupportedLanguages()));
            return;
        }

        // Check if languages match the existing index or if index is empty
        if (!searchService.getSupportedLanguages().isEmpty()) {
            if (!searchService.hasLanguage(sourceLang) || !searchService.hasLanguage(targetLang)) {
                sendError(resp, 400, "Language mismatch. Index contains: " +
                        searchService.getSupportedLanguages() +
                        ". Requested: " + sourceLang + " -> " + targetLang);
                return;
            }
        }

        try {
            int count = 0;
            List<FailedSentence> failed = new ArrayList<>();

            for (int i = 0; i < request.sentences.size(); i++) {
                SentencePair pair = request.sentences.get(i);
                try {
                    if (pair.source != null && pair.target != null) {
                        Document doc = createDocument(pair.source, pair.target, sourceLang, targetLang);
                        searchService.addDocument(doc);
                        count++;
                    }
                } catch (Exception e) {
                    failed.add(new FailedSentence(i, pair.source, e.getMessage()));
                }
            }

            // Commit changes
            searchService.commit();

            // Check for optimization request
            String optimize = req.getParameter("optimize");
            if ("true".equalsIgnoreCase(optimize)) {
                searchService.optimize();
            }

            sendJson(resp, new UploadResponse(count, failed.size(), failed));

        } catch (Exception e) {
            sendError(resp, 500, "Indexing error: " + e.getMessage());
        }
    }

    private Document createDocument(String source, String target, String sourceLang, String targetLang) {
        Document doc = new Document();

        // Source language fields
        doc.add(new TextField(sourceLang + "_text", source, Field.Store.NO));
        doc.add(new TextField(sourceLang + "_exact", source, Field.Store.NO));
        doc.add(new StoredField(sourceLang + "_raw", source));

        // Target language fields
        doc.add(new TextField(targetLang + "_text", target, Field.Store.NO));
        doc.add(new TextField(targetLang + "_exact", target, Field.Store.NO));
        doc.add(new StoredField(targetLang + "_raw", target));

        return doc;
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

    static class UploadRequest {
        String sourceLang;
        String targetLang;
        List<SentencePair> sentences;
    }

    static class SentencePair {
        String source;
        String target;
    }

    static class FailedSentence {
        int index;
        String source;
        String error;

        FailedSentence(int index, String source, String error) {
            this.index = index;
            this.source = source;
            this.error = error;
        }
    }

    record UploadResponse(int indexed, int failed, List<FailedSentence> failures) {}
    record ErrorResponse(String error) {}
}
