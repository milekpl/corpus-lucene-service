# Corpus Lucene Service

A high-performance REST service for parallel corpus queries using Apache Lucene.

## Features

- **Fast document counting** - Count occurrences of terms in English or Polish
- **Concordance search** - Find parallel sentences with context
- **Parallel text retrieval** - Cross-language search for translation pairs
- **Term comparison** - Compare frequency of multiple terms
- **REST API** - Simple JSON endpoints

## Quick Start

### Build

```bash
mvn clean package
```

### Run the Server

```bash
java -Xmx4g -jar target/corpus-lucene-service-*.jar serve --index /path/to/index --port 8082
```

### Build Index from PostgreSQL

```bash
java -jar target/corpus-lucene-service-*.jar build \
  --jdbc "jdbc:postgresql://localhost/dictionary_analytics" \
  --user your_user --password your_password \
  --index /path/to/index
```

## API Endpoints

### Health Check

```bash
GET /health
```

Response:
```json
{
  "status": "ok",
  "docs": 74740856,
  "heapUsedMb": 57,
  "heapMaxMb": 4096,
  "uptimeSeconds": 36
}
```

### Count Terms

```bash
GET /count?q={term}&field={en|pl}
```

Example:
```bash
curl "http://localhost:8082/count?q=butterfly&field=en"
```

Response:
```json
{
  "query": "butterfly",
  "field": "en",
  "count": 1234
}
```

### Concordance Search

```bash
GET /concordance?q={term}&field={en|pl}&limit=100&offset=0
```

### Parallel Text Search

```bash
GET /parallel?en={english_term}&pl={polish_term}&limit=100
```

### Compare Terms

```bash
POST /compare
Content-Type: application/json

{
  "terms": ["house", "home", "building"]
}
```

## Requirements

- Java 17+
- 4GB RAM minimum (for large indices)
- PostgreSQL JDBC driver (included)

## Architecture

- **Lucene 9.11.1** - Search index
- **Jetty 11** - HTTP server
- **Gson** - JSON serialization
- **Morfologik** - Polish language stemming

## License

MIT License - see [LICENSE](LICENSE) file.
