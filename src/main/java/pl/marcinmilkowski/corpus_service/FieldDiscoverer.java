package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers language fields from an existing Lucene index.
 *
 * Extracts language codes from field names like:
 * - en_text, en_exact, en_raw -> language: en
 * - pl_text, pl_exact, pl_raw -> language: pl
 */
public class FieldDiscoverer {

    private static final Pattern FIELD_PATTERN = Pattern.compile("^([a-z]+)_(text|exact|raw)$");

    private final Set<String> languageFields;
    private final Map<String, Analyzer> fieldAnalyzers;

    public FieldDiscoverer(DirectoryReader reader) {
        this.languageFields = new HashSet<>();
        this.fieldAnalyzers = new HashMap<>();

        discoverFields(reader);
    }

    private void discoverFields(IndexReader reader) {
        // Get all field names from all leaf readers
        Set<String> allFieldNames = new HashSet<>();

        for (LeafReaderContext context : reader.leaves()) {
            LeafReader leafReader = context.reader();
            FieldInfos fieldInfos = leafReader.getFieldInfos();

            for (FieldInfo fieldInfo : fieldInfos) {
                allFieldNames.add(fieldInfo.name);
            }
        }

        for (String fieldName : allFieldNames) {
            Matcher matcher = FIELD_PATTERN.matcher(fieldName);

            if (matcher.matches()) {
                String languageCode = matcher.group(1);
                String fieldType = matcher.group(2);
                String fullLangCode = languageCode.toLowerCase();

                // Add to set of supported languages
                languageFields.add(fullLangCode);

                // Register analyzer for this language's text field
                if (fieldType.equals("text")) {
                    Analyzer existing = fieldAnalyzers.get(fullLangCode);
                    if (existing == null) {
                        fieldAnalyzers.put(fullLangCode, LanguageConfig.getTextAnalyzer(fullLangCode));
                    }
                }

                // Always register exact analyzer
                String exactField = fullLangCode + "_exact";
                if (!fieldAnalyzers.containsKey(exactField)) {
                    fieldAnalyzers.put(exactField, LanguageConfig.getExactAnalyzer());
                }
            }
        }
    }

    /**
     * Get the set of language codes discovered from the index.
     */
    public Set<String> getLanguageFields() {
        return languageFields;
    }

    /**
     * Build a PerFieldAnalyzerWrapper for the discovered fields.
     */
    public Analyzer buildAnalyzer() {
        if (fieldAnalyzers.isEmpty()) {
            return LanguageConfig.getTextAnalyzer("en");
        }

        // Use the first discovered language as default
        String defaultLang = languageFields.stream().findFirst().orElse("en");
        Analyzer defaultAnalyzer = LanguageConfig.getTextAnalyzer(defaultLang);

        return new PerFieldAnalyzerWrapper(defaultAnalyzer, new HashMap<>(fieldAnalyzers));
    }
}
