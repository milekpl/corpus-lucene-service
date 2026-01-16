package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.marcinmilkowski.corpus_service.analyzers.ExactTokenAnalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds Lucene index from PostgreSQL parallel corpus.
 *
 * Uses streaming to avoid memory issues with large result sets.
 * Commits documents in batches for efficiency.
 *
 * Creates documents with fields:
 * - en_text: English text analyzed with StandardAnalyzer
 * - en_exact: English text with punctuation preserved
 * - en_raw: Original English text (stored only)
 * - pl_text: Polish text analyzed with MorfologikAnalyzer
 * - pl_exact: Polish text with punctuation preserved
 * - pl_raw: Original Polish text (stored only)
 */
public class IndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(IndexBuilder.class);

    private static final int BATCH_SIZE = 10000;
    private static final int LOG_INTERVAL = 100000;

    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    private final Path indexPath;

    public IndexBuilder(String jdbcUrl, String jdbcUser, String jdbcPassword, Path indexPath) {
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
        this.indexPath = indexPath;
    }

    /**
     * Build index from database using streaming.
     *
     * @param query SQL query returning (source_text, target_text) columns
     * @return number of documents indexed
     */
    public long build(String query) throws SQLException, IOException {
        log.info("Building index from database...");
        log.info("JDBC URL: {}", jdbcUrl);
        log.info("Index path: {}", indexPath);
        log.info("Batch size: {} documents", BATCH_SIZE);

        Analyzer analyzer = createAnalyzer();

        try (Directory directory = new NIOFSDirectory(indexPath)) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            config.setRAMBufferSizeMB(256); // Lucene in-memory buffer

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                log.info("Connecting to database with streaming...");

                // Create connection with auto-commit disabled for streaming
                Connection conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                conn.setAutoCommit(false);

                try (Statement stmt = conn.createStatement(
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY)) {

                    // Set fetch size for streaming
                    stmt.setFetchSize(BATCH_SIZE);
                    log.info("Executing query with fetch size {}...", BATCH_SIZE);

                    try (ResultSet rs = stmt.executeQuery(query)) {
                        long count = 0;
                        long startTime = System.currentTimeMillis();

                        // Process rows one at a time (streaming)
                        while (rs.next()) {
                            String enText = rs.getString(1);
                            String plText = rs.getString(2);

                            if (enText != null && plText != null) {
                                Document doc = createDocument(enText, plText);
                                writer.addDocument(doc);
                                count++;

                                // Periodic logging
                                if (count % LOG_INTERVAL == 0) {
                                    long elapsed = System.currentTimeMillis() - startTime;
                                    long rate = count * 1000 / Math.max(elapsed, 1);
                                    log.info("Indexed {} documents ({} docs/sec)", count, rate);

                                    // Periodic commit to reduce memory
                                    if (count % 100000 == 0) {
                                        writer.commit();
                                        log.info("Committed at {}", count);
                                    }
                                }
                            }
                        }

                        // Final commit
                        writer.commit();

                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("Indexing complete: {} documents in {} seconds",
                                count, elapsed / 1000);

                        return count;
                    }
                } finally {
                    conn.close();
                }
            }
        }
    }

    private Analyzer createAnalyzer() {
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();

        // English fields
        fieldAnalyzers.put("en_text", new StandardAnalyzer());
        fieldAnalyzers.put("en_exact", new ExactTokenAnalyzer());

        // Polish fields
        fieldAnalyzers.put("pl_text", new MorfologikAnalyzer());
        fieldAnalyzers.put("pl_exact", new ExactTokenAnalyzer());

        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), fieldAnalyzers);
    }

    private Document createDocument(String enText, String plText) {
        Document doc = new Document();

        // English fields
        doc.add(new TextField("en_text", enText, Field.Store.NO));
        doc.add(new TextField("en_exact", enText, Field.Store.NO));
        doc.add(new StoredField("en_raw", enText));

        // Polish fields
        doc.add(new TextField("pl_text", plText, Field.Store.NO));
        doc.add(new TextField("pl_exact", plText, Field.Store.NO));
        doc.add(new StoredField("pl_raw", plText));

        return doc;
    }

    /**
     * CLI entry point for index building.
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: IndexBuilder <jdbcUrl> <user> <password> <indexPath> [query]");
            System.err.println("Example: IndexBuilder jdbc:postgresql://localhost/dictionary_analytics " +
                    "dict_user dict_pass /path/to/index");
            System.exit(1);
        }

        String jdbcUrl = args[0];
        String user = args[1];
        String password = args[2];
        Path indexPath = Path.of(args[3]);
        String query = args.length > 4 ? args[4] :
                "SELECT source_text, target_text FROM public.parallel_corpus";

        IndexBuilder builder = new IndexBuilder(jdbcUrl, user, password, indexPath);

        try {
            long count = builder.build(query);
            System.out.println("Successfully indexed " + count + " documents");
        } catch (Exception e) {
            log.error("Index build failed", e);
            System.exit(1);
        }
    }
}
