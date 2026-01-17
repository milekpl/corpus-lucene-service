package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Lucene search service for corpus queries.
 *
 * Dynamically discovers language fields from the index at startup.
 * Supports any language pair that was used to build the index.
 */
public class SearchService implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final Directory directory;
    private final DirectoryReader reader;
    private final IndexWriter writer;
    private final IndexSearcher searcher;
    private final Set<String> supportedFields;
    private final String sourceLang;
    private final String targetLang;

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

        // Discover fields from index and build analyzer
        FieldDiscoverer discoverer = new FieldDiscoverer(reader);
        this.supportedFields = discoverer.getLanguageFields();

        // Determine source and target languages
        String[] langs = supportedFields.toArray(new String[0]);
        this.sourceLang = langs.length > 0 ? langs[0] : "en";

        if (langs.length > 1) {
            this.targetLang = langs[1];
        } else if (langs.length > 0) {
            this.targetLang = langs[0];
        } else {
            this.targetLang = "pl";
        }

        log.info("SearchService initialized with {} documents", reader.numDocs());
        log.info("Discovered language fields: {}", supportedFields);
    }

    /**
     * Get the source language code (first language discovered).
     */
    public String getSourceLanguage() {
        return sourceLang;
    }

    /**
     * Get the target language code (second language discovered).
     */
    public String getTargetLanguage() {
        return targetLang;
    }

    /**
     * Add a document to the index.
     *
     * @param doc the document to add
     * @throws IOException if an I/O error occurs
     */
    public void addDocument(Document doc) throws IOException {
        writer.addDocument(doc);
    }

    /**
     * Commit pending changes to the index.
     *
     * @throws IOException if an I/O error occurs
     */
    public void commit() throws IOException {
        writer.commit();
    }

    /**
     * Get the set of language codes available in the index.
     */
    public Set<String> getSupportedLanguages() {
        return Collections.unmodifiableSet(supportedFields);
    }

    /**
     * Check if a language field exists in the index.
     */
    public boolean hasLanguage(String languageCode) {
        return supportedFields.contains(languageCode.toLowerCase());
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

        String rawField1 = sourceLang + "_raw";
        String rawField2 = targetLang + "_raw";

        for (int i = offset; i < Math.min(offset + limit, topDocs.scoreDocs.length); i++) {
            Document doc = searcher.storedFields().document(topDocs.scoreDocs[i].doc);
            hits.add(new ConcordanceHit(
                    doc.get(rawField1),
                    doc.get(rawField2)
            ));
        }

        return new ConcordanceResult(total, hits);
    }

    /**
     * Find parallel sentences containing both language terms.
     *
     * @param term1 Term in source language
     * @param term2 Term in target language
     * @param limit max results
     * @return concordance result
     */
    public ConcordanceResult parallel(String term1, String term2, int limit) throws IOException {
        Query query1 = buildQuery(term1, sourceLang);
        Query query2 = buildQuery(term2, targetLang);

        BooleanQuery combined = new BooleanQuery.Builder()
                .add(query1, BooleanClause.Occur.MUST)
                .add(query2, BooleanClause.Occur.MUST)
                .build();

        TopDocs topDocs = searcher.search(combined, limit);
        int total = (int) topDocs.totalHits.value;

        String rawField1 = sourceLang + "_raw";
        String rawField2 = targetLang + "_raw";

        List<ConcordanceHit> hits = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            hits.add(new ConcordanceHit(
                    doc.get(rawField1),
                    doc.get(rawField2)
            ));
        }

        return new ConcordanceResult(total, hits);
    }

    /**
     * Build appropriate query based on term characteristics.
     */
    private Query buildQuery(String term, String field) {
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

    private Query buildPhraseQuery(String phrase, String field) {
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

    public record ConcordanceHit(String source, String target) {}

    public record ConcordanceResult(int total, List<ConcordanceHit> hits) {}
}
