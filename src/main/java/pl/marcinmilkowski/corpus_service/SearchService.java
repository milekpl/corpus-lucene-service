package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.marcinmilkowski.corpus_service.analyzers.ExactTokenAnalyzer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Lucene search service for corpus queries.
 */
public class SearchService implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final Directory directory;
    private final DirectoryReader reader;
    private final IndexWriter writer;
    private final IndexSearcher searcher;
    private final Analyzer analyzer;
    private final Analyzer exactAnalyzer;

    public SearchService(Path indexPath) throws IOException {
        this(new NIOFSDirectory(indexPath));
    }

    /**
     * Constructor for testing with in-memory directory.
     */
    protected SearchService(Directory directory) throws IOException {
        this.directory = directory;
        this.writer = new IndexWriter(directory, new IndexWriterConfig());
        this.reader = DirectoryReader.open(directory);
        this.searcher = new IndexSearcher(reader);
        this.analyzer = createAnalyzer();
        this.exactAnalyzer = new ExactTokenAnalyzer();

        log.info("SearchService initialized with {} documents", reader.numDocs());
    }

    private Analyzer createAnalyzer() {
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("en_text", new StandardAnalyzer());
        fieldAnalyzers.put("en_exact", new ExactTokenAnalyzer());
        fieldAnalyzers.put("pl_text", new MorfologikAnalyzer());
        fieldAnalyzers.put("pl_exact", new ExactTokenAnalyzer());
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), fieldAnalyzers);
    }

    /**
     * Get total document count.
     */
    public int getDocCount() {
        return reader.numDocs();
    }

    /**
     * Optimize the index by merging segments.
     * This improves search performance.
     *
     * @return number of segments before merge
     */
    public int optimize() throws IOException {
        int segmentCount = reader.leaves().size();
        writer.forceMerge(1);  // Merge into single segment
        writer.commit();
        // Reopen reader after commit
        return segmentCount;
    }

    /**
     * Clear all documents from the index.
     *
     * @return number of documents deleted
     */
    public int clear() throws IOException {
        int count = reader.numDocs();
        writer.deleteAll();
        writer.commit();
        return count;
    }

    /**
     * Count documents matching a term.
     *
     * @param term  search term
     * @param field language field ("en" or "pl")
     * @return match count
     */
    public int count(String term, String field) throws IOException {
        Query query = buildQuery(term, field);
        return searcher.count(query);
    }

    /**
     * Count multiple terms (batch operation).
     *
     * @param terms list of terms
     * @param field language field
     * @return map of term -> count
     */
    public Map<String, Integer> compare(List<String> terms, String field) throws IOException {
        Map<String, Integer> results = new LinkedHashMap<>();
        for (String term : terms) {
            results.put(term, count(term, field));
        }
        return results;
    }

    /**
     * Find concordance hits (parallel sentences).
     *
     * @param queryStr search query
     * @param field    language field
     * @param limit    max results
     * @param offset   skip first N results
     * @return list of hits with both languages
     */
    public ConcordanceResult concordance(String queryStr, String field, int limit, int offset)
            throws IOException {

        Query query = buildQuery(queryStr, field);
        TopDocs topDocs = searcher.search(query, offset + limit);

        List<ConcordanceHit> hits = new ArrayList<>();
        int total = (int) topDocs.totalHits.value;

        for (int i = offset; i < Math.min(offset + limit, topDocs.scoreDocs.length); i++) {
            Document doc = searcher.storedFields().document(topDocs.scoreDocs[i].doc);
            hits.add(new ConcordanceHit(
                    doc.get("en_raw"),
                    doc.get("pl_raw")
            ));
        }

        return new ConcordanceResult(total, hits);
    }

    /**
     * Find parallel sentences containing both English and Polish terms.
     *
     * @param enTerm English term
     * @param plTerm Polish term
     * @param limit  max results
     * @return concordance result
     */
    public ConcordanceResult parallel(String enTerm, String plTerm, int limit) throws IOException {
        Query enQuery = buildQuery(enTerm, "en");
        Query plQuery = buildQuery(plTerm, "pl");

        BooleanQuery combined = new BooleanQuery.Builder()
                .add(enQuery, BooleanClause.Occur.MUST)
                .add(plQuery, BooleanClause.Occur.MUST)
                .build();

        TopDocs topDocs = searcher.search(combined, limit);
        int total = (int) topDocs.totalHits.value;

        List<ConcordanceHit> hits = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            hits.add(new ConcordanceHit(
                    doc.get("en_raw"),
                    doc.get("pl_raw")
            ));
        }

        return new ConcordanceResult(total, hits);
    }

    /**
     * Build appropriate query based on term characteristics.
     */
    private Query buildQuery(String term, String field) throws IOException {
        String textField = field + "_text";
        String exactField = field + "_exact";

        // Use exact field for terms with punctuation
        boolean usesExact = term.contains("-") || term.contains(".");

        if (usesExact) {
            // Exact field: term query on lowercased term
            return new TermQuery(new Term(exactField, term.toLowerCase()));
        }

        // Check for phrase (contains space)
        if (term.contains(" ")) {
            return buildPhraseQuery(term, textField);
        }

        // Single word: term query
        return new TermQuery(new Term(textField, term.toLowerCase()));
    }

    private Query buildPhraseQuery(String phrase, String field) throws IOException {
        String[] words = phrase.toLowerCase().split("\\s+");
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        for (String word : words) {
            builder.add(new Term(field, word));
        }
        return builder.build();
    }

    @Override
    public void close() throws IOException {
        reader.close();
        writer.close();
        directory.close();
    }

    // Result classes

    public record ConcordanceHit(String en, String pl) {}

    public record ConcordanceResult(int total, List<ConcordanceHit> hits) {}
}
