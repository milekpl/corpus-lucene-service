package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for IndexBuilder using H2 in-memory database.
 * Tests the full workflow: H2 -> IndexBuilder -> SearchService
 */
class IndexBuilderTest {

    @TempDir
    Path tempDir;

    private Connection h2Connection;
    private IndexBuilder indexBuilder;
    private Path indexPath;

    // Use unique database name to ensure test isolation
    private String getH2JdbcUrl() {
        return "jdbc:h2:mem:testdb_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    @BeforeEach
    void setUp() throws SQLException {
        String jdbcUrl = getH2JdbcUrl();

        // Create H2 in-memory database connection
        h2Connection = DriverManager.getConnection(jdbcUrl, H2_USER, H2_PASSWORD);

        // Create test table with source_text and target_text columns
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS parallel_corpus (
                    id SERIAL PRIMARY KEY,
                    source_text VARCHAR(500),
                    target_text VARCHAR(500)
                )
                """);

            // Clear any existing data for clean test isolation
            stmt.execute("DELETE FROM parallel_corpus");

            // Insert base test data
            stmt.execute("""
                INSERT INTO parallel_corpus (source_text, target_text) VALUES
                ('The quick brown fox jumps over the lazy dog', 'Szybki brazowy lis skacze nad leniwym psem'),
                ('Hello world', 'Witaj swiecie'),
                ('Thank you very much', 'Bardzo dziekuje')
                """);
        }

        // Create IndexBuilder instance
        indexPath = tempDir.resolve("test-index");
        indexBuilder = new IndexBuilder(jdbcUrl, H2_USER, H2_PASSWORD, indexPath);
    }

    @AfterEach
    void tearDown() throws SQLException, IOException {
        // Close index if open
        if (indexBuilder != null) {
            indexBuilder = null;
        }

        // Close H2 connection
        if (h2Connection != null && !h2Connection.isClosed()) {
            h2Connection.close();
        }
    }

    @Test
    void buildIndexesDocumentsFromH2Database() throws Exception {
        String query = "SELECT source_text, target_text FROM parallel_corpus ORDER BY id";

        // Build index
        long docCount = indexBuilder.build(query);

        // Verify document count matches inserted data
        assertEquals(3, docCount, "Should index 3 documents");
    }

    @Test
    void buildCreatesSearchableIndex() throws Exception {
        String query = "SELECT source_text, target_text FROM parallel_corpus";

        // Build index
        indexBuilder.build(query);

        // Verify index is searchable with SearchService
        try (SearchService searchService = new SearchService(indexPath)) {
            assertEquals(3, searchService.getDocCount(), "Index should contain 3 documents");
        }
    }

    @Test
    void indexContainsSearchableEnglishText() throws Exception {
        String query = "SELECT source_text, target_text FROM parallel_corpus";

        indexBuilder.build(query);

        try (SearchService searchService = new SearchService(indexPath)) {
            // Search for English word
            int count = searchService.count("quick", "en");
            assertEquals(1, count, "Should find 'quick' in English text");
        }
    }

    @Test
    void indexContainsSearchablePolishText() throws Exception {
        String query = "SELECT source_text, target_text FROM parallel_corpus";

        indexBuilder.build(query);

        try (SearchService searchService = new SearchService(indexPath)) {
            // Search for Polish word (lowercased by analyzer)
            int count = searchService.count("lis", "pl");
            assertEquals(1, count, "Should find 'lis' in Polish text");
        }
    }

    @Test
    void customSqlQueryFiltersResults() throws Exception {
        // Clear base data and insert test data with unique content
        // NOTE: Using case-sensitive comparison with LOCATE() since H2 LIKE is case-insensitive by default
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("DELETE FROM parallel_corpus");
            h2Connection.commit();  // Ensure delete is committed

            stmt.execute("""
                INSERT INTO parallel_corpus (source_text, target_text) VALUES
                ('Alpha test data with searchterm here', 'Test filtru pierwsza tresc'),
                ('Beta test data with searchterm here too', 'Test filtru druga tresc'),
                ('Gamma test data without match anywhere', 'Test filtru trzecia tresc gdzie indziej')
                """);
            h2Connection.commit();  // Ensure inserts are committed
        }

        // Verify data count before building index using case-sensitive search
        try (var verifyStmt = h2Connection.createStatement();
             var rs = verifyStmt.executeQuery("SELECT COUNT(*) FROM parallel_corpus WHERE LOCATE('searchterm', source_text) > 0")) {
            rs.next();
            int matchingCount = rs.getInt(1);
            assertEquals(2, matchingCount, "Should have 2 rows with 'searchterm' in database");
        }

        // Custom query filtering for rows containing "searchterm"
        String query = "SELECT source_text, target_text FROM parallel_corpus WHERE LOCATE('searchterm', source_text) > 0";

        long docCount = indexBuilder.build(query);

        assertEquals(2, docCount, "Should only index 2 documents matching 'searchterm'");

        // Verify index only contains filtered results
        try (SearchService searchService = new SearchService(indexPath)) {
            assertEquals(2, searchService.getDocCount());
            assertEquals(0, searchService.count("anywhere", "en"), "Should not find 'anywhere'");
        }
    }

    @Test
    void concordanceReturnsParallelText() throws Exception {
        String query = "SELECT source_text, target_text FROM parallel_corpus";

        indexBuilder.build(query);

        try (SearchService searchService = new SearchService(indexPath)) {
            var result = searchService.concordance("quick", "en", 10, 0);

            assertEquals(1, result.total());
            assertEquals(1, result.hits().size());

            var hit = result.hits().get(0);
            assertTrue(hit.en().contains("quick"));
            assertTrue(hit.pl().contains("lis"));
        }
    }

    @Test
    void handlesEmptyResultSet() throws Exception {
        // Custom query with no results
        String query = "SELECT source_text, target_text FROM parallel_corpus WHERE source_text = 'nonexistent'";

        long docCount = indexBuilder.build(query);

        assertEquals(0, docCount, "Should index 0 documents for empty result set");
    }

    @Test
    void handlesNullValuesGracefully() throws Exception {
        // Clear base data and insert data with null values
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("DELETE FROM parallel_corpus");
            stmt.execute("""
                INSERT INTO parallel_corpus (source_text, target_text) VALUES
                ('Valid text', 'Valid translation'),
                (NULL, 'Missing source'),
                ('Another valid', NULL)
                """);
        }

        String query = "SELECT source_text, target_text FROM parallel_corpus";

        long docCount = indexBuilder.build(query);

        // Only rows where BOTH columns are non-null should be indexed
        // Row 1: both non-null -> indexed
        // Row 2: source is null -> skipped
        // Row 3: target is null -> skipped
        assertEquals(1, docCount, "Should only index rows where both columns are non-null");
    }
}
