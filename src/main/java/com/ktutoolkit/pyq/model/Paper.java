package com.ktutoolkit.pyq.model;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * One question paper record from the KTU Archive Supabase catalogue.
 */
public class Paper {
    private final String subjectCode;
    private final String subjectName;
    private final String semester;
    private final String title;
    private final String url;

    public Paper(String subjectCode, String subjectName, String semester, String title, String url) {
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.semester = semester;
        this.title = title;
        this.url = url;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Uses the final segment of the storage URL as the local file name.
     */
    public String getFileName() {
        String name = title;
        int slash = url.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < url.length()) {
            name = url.substring(slash + 1);
            int query = name.indexOf('?');
            if (query >= 0) {
                name = name.substring(0, query);
            }
            try {
                name = URLDecoder.decode(name, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // Keep the raw URL segment if it contains an invalid escape.
            }
        }

        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isEmpty()) {
            name = subjectCode;
        }
        if (!name.toLowerCase().endsWith(".pdf")) {
            name += ".pdf";
        }
        return name;
    }
}
