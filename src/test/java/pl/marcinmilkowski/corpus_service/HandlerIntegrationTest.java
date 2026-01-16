package pl.marcinmilkowski.corpus_service;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pl.marcinmilkowski.corpus_service.api.CountHandler;
import pl.marcinmilkowski.corpus_service.api.HealthHandler;
import pl.marcinmilkowski.corpus_service.analyzers.ExactTokenAnalyzer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for HTTP handlers using mocked servlet objects
 * and in-memory Lucene index (ByteBuffersDirectory).
 */
class HandlerIntegrationTest {

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    private Directory directory;
    private SearchService searchService;
    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private Gson gson;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        directory = new ByteBuffersDirectory();

        // Create test index
        Analyzer analyzer = createAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            // Add test documents
            addDocument(writer, "The lifespan of a butterfly is short", "Zycie motyla jest krotkie");
            addDocument(writer, "Life span varies by species", "Dlugosc zycia rozni sie w zaleznosci od gatunku");
            addDocument(writer, "A life-span can be measured", "Dlugosc zycia mozna zmierzyc");
            addDocument(writer, "Use etc. for abbreviations", "Uzyj etc. dla skrotow");
            addDocument(writer, "For example, i.e. means that is", "Na przyklad, i.e. oznacza to jest");
            addDocument(writer, "Problem-solving skills are important", "Umiejetnosci rozwiazywania problemow sa wazne");
        }

        searchService = new SearchService(directory) {}; // Anonymous subclass to access protected constructor

        // Setup response mock for capturing output
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        gson = new Gson();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (searchService != null) {
            searchService.close();
        }
        if (printWriter != null) {
            printWriter.close();
        }
    }

    @Test
    void countHandlerReturnsCorrectCount() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn("butterfly");
        when(mockRequest.getParameter("field")).thenReturn("en");
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setContentType("application/json");
        verify(mockResponse).setCharacterEncoding("UTF-8");

        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"query\":\"butterfly\""));
        assertTrue(jsonResponse.contains("\"field\":\"en\""));
        assertTrue(jsonResponse.contains("\"count\":1"));
    }

    @Test
    void countHandlerWithPolishField() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn("motyla");
        when(mockRequest.getParameter("field")).thenReturn("pl");
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"query\":\"motyla\""));
        assertTrue(jsonResponse.contains("\"field\":\"pl\""));
        assertTrue(jsonResponse.contains("\"count\":1"));
    }

    @Test
    void countHandlerDefaultsFieldToEnglish() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn("butterfly");
        when(mockRequest.getParameter("field")).thenReturn(null);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"field\":\"en\""));
    }

    @Test
    void countHandlerReturnsZeroForMissingTerm() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn("nonexistentterm12345");
        when(mockRequest.getParameter("field")).thenReturn("en");
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"count\":0"));
    }

    @Test
    void countHandlerReturnsErrorForMissingQuery() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn(null);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(400);
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"error\""));
    }

    @Test
    void countHandlerReturnsErrorForInvalidField() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn("butterfly");
        when(mockRequest.getParameter("field")).thenReturn("de");
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(400);
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"error\""));
    }

    @Test
    void countHandlerCountsHyphenatedTerm() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn("life-span");
        when(mockRequest.getParameter("field")).thenReturn("en");
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"count\":1"));
    }

    @Test
    void countHandlerCountsAbbreviationWithPeriod() throws IOException {
        // Arrange
        when(mockRequest.getParameter("q")).thenReturn("etc.");
        when(mockRequest.getParameter("field")).thenReturn("en");
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableCountHandler handler = new TestableCountHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"count\":1"));
    }

    @Test
    void healthHandlerReturnsOkStatus() throws IOException {
        // Arrange
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableHealthHandler handler = new TestableHealthHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setContentType("application/json");
        verify(mockResponse).setCharacterEncoding("UTF-8");

        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"status\":\"ok\""));
    }

    @Test
    void healthHandlerReturnsDocumentCount() throws IOException {
        // Arrange
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableHealthHandler handler = new TestableHealthHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"docs\":6"));
    }

    @Test
    void healthHandlerReturnsHeapMetrics() throws IOException {
        // Arrange
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableHealthHandler handler = new TestableHealthHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        assertTrue(jsonResponse.contains("\"heapUsedMb\""));
        assertTrue(jsonResponse.contains("\"heapMaxMb\""));
        assertTrue(jsonResponse.contains("\"uptimeSeconds\""));
    }

    @Test
    void healthHandlerResponseIsValidJson() throws IOException {
        // Arrange
        when(mockResponse.getWriter()).thenReturn(printWriter);

        TestableHealthHandler handler = new TestableHealthHandler(searchService);

        // Act
        handler.doGetPublic(mockRequest, mockResponse);

        // Assert
        printWriter.flush();
        String jsonResponse = stringWriter.toString();

        // Verify it parses as valid JSON with expected structure
        assertDoesNotThrow(() -> gson.fromJson(jsonResponse, HealthResponse.class));

        HealthResponse response = gson.fromJson(jsonResponse, HealthResponse.class);
        assertEquals("ok", response.status);
        assertEquals(6, response.docs);
        assertTrue(response.heapUsedMb >= 0);
        assertTrue(response.heapMaxMb > 0);
        assertTrue(response.uptimeSeconds >= 0);
    }

    private Analyzer createAnalyzer() {
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("en_text", new StandardAnalyzer());
        fieldAnalyzers.put("en_exact", new ExactTokenAnalyzer());
        fieldAnalyzers.put("pl_text", new StandardAnalyzer()); // Simplified for test
        fieldAnalyzers.put("pl_exact", new ExactTokenAnalyzer());
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), fieldAnalyzers);
    }

    private void addDocument(IndexWriter writer, String en, String pl) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("en_text", en, Field.Store.NO));
        doc.add(new TextField("en_exact", en, Field.Store.NO));
        doc.add(new StoredField("en_raw", en));
        doc.add(new TextField("pl_text", pl, Field.Store.NO));
        doc.add(new TextField("pl_exact", pl, Field.Store.NO));
        doc.add(new StoredField("pl_raw", pl));
        writer.addDocument(doc);
    }

    // Helper record for parsing health response
    private record HealthResponse(String status, int docs, long heapUsedMb, long heapMaxMb, long uptimeSeconds) {}

    // Testable subclass that exposes protected doGet as public
    private static class TestableCountHandler extends CountHandler {
        public TestableCountHandler(SearchService searchService) {
            super(searchService);
        }

        public void doGetPublic(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            doGet(req, resp);
        }
    }

    // Testable subclass that exposes protected doGet as public
    private static class TestableHealthHandler extends HealthHandler {
        public TestableHealthHandler(SearchService searchService) {
            super(searchService);
        }

        public void doGetPublic(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            doGet(req, resp);
        }
    }
}
