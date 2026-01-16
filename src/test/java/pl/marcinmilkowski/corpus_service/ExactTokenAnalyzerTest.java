package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.corpus_service.analyzers.ExactTokenAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExactTokenAnalyzer.
 *
 * Verifies that punctuation (hyphens, periods) is preserved within tokens.
 */
class ExactTokenAnalyzerTest {

    private final ExactTokenAnalyzer analyzer = new ExactTokenAnalyzer();

    @Test
    void preservesHyphens() throws IOException {
        List<String> tokens = tokenize("life-span is hyphenated");

        assertEquals(3, tokens.size());
        assertEquals("life-span", tokens.get(0));
        assertEquals("is", tokens.get(1));
        assertEquals("hyphenated", tokens.get(2));
    }

    @Test
    void preservesPeriods() throws IOException {
        List<String> tokens = tokenize("Use i.e. or e.g. in text");

        assertEquals(6, tokens.size());
        assertEquals("use", tokens.get(0));
        assertEquals("i.e.", tokens.get(1));
        assertEquals("or", tokens.get(2));
        assertEquals("e.g.", tokens.get(3));
        assertEquals("in", tokens.get(4));
        assertEquals("text", tokens.get(5));
    }

    @Test
    void lowercasesTokens() throws IOException {
        List<String> tokens = tokenize("UPPERCASE Mixed-Case");

        assertEquals(2, tokens.size());
        assertEquals("uppercase", tokens.get(0));
        assertEquals("mixed-case", tokens.get(1));
    }

    @Test
    void handlesSingleAbbreviation() throws IOException {
        List<String> tokens = tokenize("etc.");

        assertEquals(1, tokens.size());
        assertEquals("etc.", tokens.get(0));
    }

    @Test
    void handlesMultiDotAbbreviation() throws IOException {
        List<String> tokens = tokenize("U.S.A. is a country");

        assertEquals(4, tokens.size());
        assertEquals("u.s.a.", tokens.get(0));
    }

    @Test
    void handlesComplexHyphenation() throws IOException {
        List<String> tokens = tokenize("problem-solving self-aware");

        assertEquals(2, tokens.size());
        assertEquals("problem-solving", tokens.get(0));
        assertEquals("self-aware", tokens.get(1));
    }

    private List<String> tokenize(String text) throws IOException {
        List<String> tokens = new ArrayList<>();

        try (TokenStream stream = analyzer.tokenStream("test", new StringReader(text))) {
            CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();

            while (stream.incrementToken()) {
                tokens.add(termAttr.toString());
            }

            stream.end();
        }

        return tokens;
    }
}
