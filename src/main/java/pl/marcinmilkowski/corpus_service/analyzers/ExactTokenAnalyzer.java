package pl.marcinmilkowski.corpus_service.analyzers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

/**
 * Analyzer that preserves punctuation (hyphens, periods) within tokens.
 *
 * Unlike StandardAnalyzer which splits on punctuation:
 * - "life-span" stays as "life-span" (not split into "life" and "span")
 * - "i.e." stays as "i.e." (not stripped to "ie")
 * - "etc." stays as "etc."
 *
 * Used for exact matching of abbreviations and hyphenated compounds.
 */
public class ExactTokenAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream filter = new LowerCaseFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, filter);
    }
}
