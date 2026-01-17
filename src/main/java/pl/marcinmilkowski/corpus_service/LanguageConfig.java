package pl.marcinmilkowski.corpus_service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import pl.marcinmilkowski.corpus_service.analyzers.ExactTokenAnalyzer;

import java.util.Map;
import java.util.Optional;

/**
 * Configuration for supported languages and their corresponding Lucene analyzers.
 *
 * Supported language codes:
 * - ar: Arabic (StandardAnalyzer)
 * - bg: Bulgarian (StandardAnalyzer)
 * - br: Breton (StandardAnalyzer)
 * - ca: Catalan (StandardAnalyzer)
 * - ckb: Kurdish (StandardAnalyzer)
 * - cs: Czech (StandardAnalyzer)
 * - cy: Welsh (StandardAnalyzer)
 * - da: Danish (StandardAnalyzer)
 * - de: German (StandardAnalyzer)
 * - el: Greek (StandardAnalyzer)
 * - en: English (StandardAnalyzer)
 * - es: Spanish (StandardAnalyzer)
 * - et: Estonian (StandardAnalyzer)
 * - eu: Basque (StandardAnalyzer)
 * - fa: Persian (StandardAnalyzer)
 * - fi: Finnish (StandardAnalyzer)
 * - fr: French (StandardAnalyzer)
 * - ga: Irish (StandardAnalyzer)
 * - gl: Galician (StandardAnalyzer)
 * - hi: Hindi (StandardAnalyzer)
 * - hu: Hungarian (StandardAnalyzer)
 * - hy: Armenian (StandardAnalyzer)
 * - id: Indonesian (StandardAnalyzer)
 * - is: Icelandic (StandardAnalyzer)
 * - it: Italian (StandardAnalyzer)
 * - ja: Japanese (StandardAnalyzer - requires kuromoji)
 * - lv: Latvian (StandardAnalyzer)
 * - lt: Lithuanian (StandardAnalyzer)
 * - mk: Macedonian (StandardAnalyzer)
 * - ms: Malay (StandardAnalyzer)
 * - nl: Dutch (StandardAnalyzer)
 * - no: Norwegian (StandardAnalyzer)
 * - pl: Polish (MorfologikAnalyzer)
 * - pt: Portuguese (StandardAnalyzer)
 * - ro: Romanian (StandardAnalyzer)
 * - ru: Russian (StandardAnalyzer)
 * - sk: Slovak (StandardAnalyzer)
 * - sl: Slovenian (StandardAnalyzer)
 * - sr: Serbian (StandardAnalyzer)
 * - sv: Swedish (StandardAnalyzer)
 * - th: Thai (StandardAnalyzer)
 * - tr: Turkish (StandardAnalyzer)
 * - uk: Ukrainian (StandardAnalyzer)
 * - vi: Vietnamese (StandardAnalyzer)
 * - zh: Chinese (StandardAnalyzer - requires smartcn)
 */
public class LanguageConfig {

    private static final Map<String, AnalyzerInfo> ANALYZERS = Map.ofEntries(
            // StandardAnalyzer languages
            Map.entry("ar", new AnalyzerInfo("Arabic", new StandardAnalyzer())),
            Map.entry("bg", new AnalyzerInfo("Bulgarian", new StandardAnalyzer())),
            Map.entry("br", new AnalyzerInfo("Breton", new StandardAnalyzer())),
            Map.entry("ca", new AnalyzerInfo("Catalan", new StandardAnalyzer())),
            Map.entry("ckb", new AnalyzerInfo("Kurdish", new StandardAnalyzer())),
            Map.entry("cs", new AnalyzerInfo("Czech", new StandardAnalyzer())),
            Map.entry("cy", new AnalyzerInfo("Welsh", new StandardAnalyzer())),
            Map.entry("da", new AnalyzerInfo("Danish", new StandardAnalyzer())),
            Map.entry("de", new AnalyzerInfo("German", new StandardAnalyzer())),
            Map.entry("el", new AnalyzerInfo("Greek", new StandardAnalyzer())),
            Map.entry("en", new AnalyzerInfo("English", new StandardAnalyzer())),
            Map.entry("es", new AnalyzerInfo("Spanish", new StandardAnalyzer())),
            Map.entry("et", new AnalyzerInfo("Estonian", new StandardAnalyzer())),
            Map.entry("eu", new AnalyzerInfo("Basque", new StandardAnalyzer())),
            Map.entry("fa", new AnalyzerInfo("Persian", new StandardAnalyzer())),
            Map.entry("fi", new AnalyzerInfo("Finnish", new StandardAnalyzer())),
            Map.entry("fr", new AnalyzerInfo("French", new StandardAnalyzer())),
            Map.entry("ga", new AnalyzerInfo("Irish", new StandardAnalyzer())),
            Map.entry("gl", new AnalyzerInfo("Galician", new StandardAnalyzer())),
            Map.entry("hi", new AnalyzerInfo("Hindi", new StandardAnalyzer())),
            Map.entry("hu", new AnalyzerInfo("Hungarian", new StandardAnalyzer())),
            Map.entry("hy", new AnalyzerInfo("Armenian", new StandardAnalyzer())),
            Map.entry("id", new AnalyzerInfo("Indonesian", new StandardAnalyzer())),
            Map.entry("is", new AnalyzerInfo("Icelandic", new StandardAnalyzer())),
            Map.entry("it", new AnalyzerInfo("Italian", new StandardAnalyzer())),
            Map.entry("lv", new AnalyzerInfo("Latvian", new StandardAnalyzer())),
            Map.entry("lt", new AnalyzerInfo("Lithuanian", new StandardAnalyzer())),
            Map.entry("mk", new AnalyzerInfo("Macedonian", new StandardAnalyzer())),
            Map.entry("ms", new AnalyzerInfo("Malay", new StandardAnalyzer())),
            Map.entry("nl", new AnalyzerInfo("Dutch", new StandardAnalyzer())),
            Map.entry("no", new AnalyzerInfo("Norwegian", new StandardAnalyzer())),
            Map.entry("pl", new AnalyzerInfo("Polish", new MorfologikAnalyzer())),
            Map.entry("pt", new AnalyzerInfo("Portuguese", new StandardAnalyzer())),
            Map.entry("ro", new AnalyzerInfo("Romanian", new StandardAnalyzer())),
            Map.entry("ru", new AnalyzerInfo("Russian", new StandardAnalyzer())),
            Map.entry("sk", new AnalyzerInfo("Slovak", new StandardAnalyzer())),
            Map.entry("sl", new AnalyzerInfo("Slovenian", new StandardAnalyzer())),
            Map.entry("sr", new AnalyzerInfo("Serbian", new StandardAnalyzer())),
            Map.entry("sv", new AnalyzerInfo("Swedish", new StandardAnalyzer())),
            Map.entry("th", new AnalyzerInfo("Thai", new StandardAnalyzer())),
            Map.entry("tr", new AnalyzerInfo("Turkish", new StandardAnalyzer())),
            Map.entry("uk", new AnalyzerInfo("Ukrainian", new StandardAnalyzer())),
            Map.entry("vi", new AnalyzerInfo("Vietnamese", new StandardAnalyzer())),
            // ExactTokenAnalyzer for all languages (fallback for punctuation-preserving searches)
            Map.entry("exact", new AnalyzerInfo("Exact Match", new ExactTokenAnalyzer()))
    );

    private static final Analyzer DEFAULT_ANALYZER = new StandardAnalyzer();

    /**
     * Get the analyzer for a specific language code.
     *
     * @param languageCode ISO 639-1 language code
     * @return Optional containing the analyzer info, or empty if not supported
     */
    public static Optional<AnalyzerInfo> getAnalyzer(String languageCode) {
        return Optional.ofNullable(ANALYZERS.get(languageCode.toLowerCase()));
    }

    /**
     * Get the text analyzer for a language (for analyzed field).
     *
     * @param languageCode ISO 639-1 language code
     * @return Analyzer for the language, or StandardAnalyzer if not found
     */
    public static Analyzer getTextAnalyzer(String languageCode) {
        return getAnalyzer(languageCode)
                .map(AnalyzerInfo::analyzer)
                .orElse(DEFAULT_ANALYZER);
    }

    /**
     * Get the exact analyzer for a language (preserves punctuation).
     *
     * @return ExactTokenAnalyzer
     */
    public static Analyzer getExactAnalyzer() {
        return new ExactTokenAnalyzer();
    }

    /**
     * Get the language name for a code.
     *
     * @param languageCode ISO 639-1 language code
     * @return Optional containing the language name
     */
    public static Optional<String> getLanguageName(String languageCode) {
        return getAnalyzer(languageCode).map(AnalyzerInfo::name);
    }

    /**
     * Check if a language code is supported.
     *
     * @param languageCode ISO 639-1 language code
     * @return true if supported
     */
    public static boolean isSupported(String languageCode) {
        return ANALYZERS.containsKey(languageCode.toLowerCase());
    }

    /**
     * Get all supported language codes.
     *
     * @return array of supported language codes
     */
    public static String[] getSupportedLanguages() {
        return ANALYZERS.keySet().toArray(new String[0]);
    }

    /**
     * Get all supported languages with their names.
     *
     * @return array of formatted language strings (e.g., "en - English")
     */
    public static String[] getSupportedLanguagesWithNames() {
        return ANALYZERS.entrySet().stream()
                .filter(e -> !e.getKey().equals("exact"))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " - " + e.getValue().name())
                .toArray(String[]::new);
    }

    /**
     * Record containing analyzer information.
     */
    public record AnalyzerInfo(String name, Analyzer analyzer) {}
}
