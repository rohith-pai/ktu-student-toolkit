package com.ktutoolkit.etlab.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ETLab profile page, grouped by its section headings.
 */
public class StudentProfile {
    private final List<Section> sections;

    public StudentProfile(List<Section> sections) {
        this.sections = Collections.unmodifiableList(sections);
    }

    public List<Section> getSections() {
        return sections;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /**
     * One titled group of profile fields, for example "Personal Details".
     */
    public static final class Section {
        private final String title;
        private final Map<String, String> fields;

        public Section(String title, Map<String, String> fields) {
            this.title = title;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        public String getTitle() {
            return title;
        }

        public Map<String, String> getFields() {
            return fields;
        }
    }
}
