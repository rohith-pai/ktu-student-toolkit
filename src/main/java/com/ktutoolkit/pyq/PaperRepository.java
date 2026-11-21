package com.ktutoolkit.pyq;

import com.ktutoolkit.pyq.model.Paper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the KTU Archive question paper catalogue from Supabase and downloads
 * the selected PDFs from Supabase Storage.
 */
public class PaperRepository {
    private static final String DEFAULT_SUPABASE_URL =
            "https://jotiuetuvhikqvqinfxa.supabase.co";
    private static final String DEFAULT_SUPABASE_KEY =
            "sb_publishable_2EFUAz5EIdnRohDdi3NLlQ_2qmAA_x5";

    private static final String PAPER_COLUMNS =
            "subject_code,subject_name,semester,title,url";

    private final String supabaseUrl;
    private final String supabaseKey;
    private final HttpClient httpClient;

    public PaperRepository() {
        this(
                setting("SUPABASE_URL", "ktu.supabase.url", DEFAULT_SUPABASE_URL),
                setting("SUPABASE_ANON_KEY", "ktu.supabase.key", DEFAULT_SUPABASE_KEY));
    }

    public PaperRepository(String supabaseUrl, String supabaseKey) {
        this.supabaseUrl = stripTrailingSlash(supabaseUrl);
        this.supabaseKey = supabaseKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetches all papers whose subject code matches the supplied code.
     */
    public List<Paper> findBySubjectCode(String subjectCode) throws IOException {
        if (subjectCode == null || subjectCode.isBlank()) {
            return List.of();
        }

        String query = supabaseUrl + "/rest/v1/papers"
                + "?subject_code=eq." + encode(subjectCode.trim().toUpperCase())
                + "&select=" + encode(PAPER_COLUMNS);

        HttpRequest request = HttpRequest.newBuilder(URI.create(query))
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(
                    StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The Supabase request was interrupted.", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Supabase request failed (HTTP "
                    + response.statusCode() + ").");
        }
        return parsePapers(response.body());
    }

    /**
     * Downloads one paper to the supplied local path.
     */
    public void download(Paper paper, Path target) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(paper.getUrl()))
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Accept", "application/pdf")
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The download was interrupted.", e);
        }

        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Paper download failed (HTTP "
                        + response.statusCode() + ").");
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<Paper> parsePapers(String json) throws IOException {
        JsonReader reader = new JsonReader(json == null ? "" : json);
        List<Paper> papers = new ArrayList<>();

        reader.skipWhitespace();
        reader.expect('[');
        reader.skipWhitespace();
        if (reader.consume(']')) {
            return papers;
        }

        while (true) {
            Map<String, String> record = reader.readObject();
            String code = record.getOrDefault("subject_code", "");
            String url = record.getOrDefault("url", "");
            if (!code.isBlank() && !url.isBlank()) {
                papers.add(new Paper(
                        code,
                        record.getOrDefault("subject_name", ""),
                        record.getOrDefault("semester", ""),
                        record.getOrDefault("title", code),
                        url));
            }

            reader.skipWhitespace();
            if (reader.consume(']')) {
                return papers;
            }
            reader.expect(',');
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String setting(String environmentName, String propertyName, String fallback) {
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Tiny JSON reader for the flat string objects returned by Supabase.
     */
    private static final class JsonReader {
        private final String source;
        private int index;

        private JsonReader(String source) {
            this.source = source;
        }

        private Map<String, String> readObject() throws IOException {
            Map<String, String> values = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (consume('}')) {
                return values;
            }

            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                String value = readValue();
                values.put(key, value);

                skipWhitespace();
                if (consume('}')) {
                    return values;
                }
                expect(',');
            }
        }

        private String readValue() throws IOException {
            skipWhitespace();
            if (peek() == '"') {
                return readString();
            }
            if (source.startsWith("null", index)) {
                index += 4;
                return "";
            }
            if (source.startsWith("true", index)) {
                index += 4;
                return "true";
            }
            if (source.startsWith("false", index)) {
                index += 5;
                return "false";
            }
            return readNumber();
        }

        private String readNumber() throws IOException {
            int start = index;
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                    break;
                }
                index++;
            }
            if (start == index) {
                throw new IOException("Invalid JSON value at position " + index + ".");
            }
            return source.substring(start, index);
        }

        private String readString() throws IOException {
            skipWhitespace();
            expect('"');
            StringBuilder value = new StringBuilder();

            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') {
                    return value.toString();
                }
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (index >= source.length()) {
                    break;
                }

                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        value.append(escaped);
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'u':
                        value.append(readUnicode());
                        break;
                    default:
                        throw new IOException("Invalid JSON escape at position " + index + ".");
                }
            }
            throw new IOException("Unterminated JSON string.");
        }

        private char readUnicode() throws IOException {
            if (index + 4 > source.length()) {
                throw new IOException("Incomplete JSON unicode escape.");
            }
            String hex = source.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid JSON unicode escape: " + hex, e);
            }
        }

        private void skipWhitespace() {
            while (index < source.length()
                    && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private char peek() throws IOException {
            if (index >= source.length()) {
                throw new IOException("Unexpected end of JSON.");
            }
            return source.charAt(index);
        }

        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) throws IOException {
            if (!consume(expected)) {
                throw new IOException("Expected '" + expected + "' at position " + index + ".");
            }
        }
    }
}
