package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.corpus_service.analyzers.ExactTokenAnalyzer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SearchService using in-memory index.
 */
class SearchServiceTest {

    private Directory directory;
    private SearchService searchService;

    @BeforeEach
    void setUp() throws IOException {
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
    }

    @AfterEach
    void tearDown() throws IOException {
        if (searchService != null) {
            searchService.close();
        }
    }

    @Test
    void countsSingleWord() throws IOException {
        int count = searchService.count("butterfly", "en");
        assertEquals(1, count);
    }

    @Test
    void countsSolidCompound() throws IOException {
        int count = searchService.count("lifespan", "en");
        assertEquals(1, count);
    }

    @Test
    void countsHyphenatedTerm() throws IOException {
        int count = searchService.count("life-span", "en");
        assertEquals(1, count);
    }

    @Test
    void countsAbbreviationWithPeriod() throws IOException {
        int count = searchService.count("etc.", "en");
        assertEquals(1, count);
    }

    @Test
    void countsMultiDotAbbreviation() throws IOException {
        int count = searchService.count("i.e.", "en");
        assertEquals(1, count);
    }

    @Test
    void comparesTerms() throws IOException {
        Map<String, Integer> result = searchService.compare(
                List.of("lifespan", "life-span"),
                "en"
        );

        assertEquals(2, result.size());
        assertEquals(1, result.get("lifespan"));
        assertEquals(1, result.get("life-span"));
    }

    @Test
    void concordanceReturnsParallelText() throws IOException {
        var result = searchService.concordance("butterfly", "en", 10, 0);

        assertEquals(1, result.total());
        assertEquals(1, result.hits().size());

        var hit = result.hits().get(0);
        assertTrue(hit.en().contains("butterfly"));
        assertTrue(hit.pl().contains("motyla"));
    }

    @Test
    void parallelSearchFindsBothLanguages() throws IOException {
        var result = searchService.parallel("butterfly", "motyla", 10);

        assertEquals(1, result.total());
        assertEquals(1, result.hits().size());
    }

    @Test
    void parallelSearchMismatchReturnsZero() throws IOException {
        var result = searchService.parallel("butterfly", "problemow", 10);

        assertEquals(0, result.total());
    }

    @Test
    void docCountReturnsTotal() {
        assertEquals(6, searchService.getDocCount());
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

}
