package com.ktutoolkit.etlab.model;

import java.util.Collections;
import java.util.List;

/**
 * Weekly ETLab timetable: period headers plus one row per day.
 */
public class Timetable {
    private final List<Day> days;

    public Timetable(List<Day> days) {
        this.days = Collections.unmodifiableList(days);
    }

    public List<Day> getDays() {
        return days;
    }

    public static final class Day {
        private final String label;
        private final List<String> periods;

        public Day(String label, List<String> periods) {
            this.label = label;
            this.periods = Collections.unmodifiableList(periods);
        }

        public String getLabel() {
            return label;
        }

        public List<String> getPeriods() {
            return periods;
        }
    }
}
