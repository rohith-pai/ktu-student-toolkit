package com.ktutoolkit.etlab.model;

import java.util.Collections;
import java.util.List;

/**
 * One month of the ETLab attendance grid.
 */
public class MonthlyAttendance {
    private final int semester;
    private final int month;
    private final int year;
    private final List<Day> days;

    public MonthlyAttendance(int semester, int month, int year, List<Day> days) {
        this.semester = semester;
        this.month = month;
        this.year = year;
        this.days = Collections.unmodifiableList(days);
    }

    public int getSemester() {
        return semester;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }

    public List<Day> getDays() {
        return days;
    }

    public int count(Status status) {
        int total = 0;
        for (Day day : days) {
            for (Cell cell : day.getPeriods()) {
                if (cell.getStatus() == status) {
                    total++;
                }
            }
        }
        return total;
    }

    public static final class Day {
        private final String label;
        private final List<Cell> periods;

        public Day(String label, List<Cell> periods) {
            this.label = label;
            this.periods = Collections.unmodifiableList(periods);
        }

        public String getLabel() {
            return label;
        }

        public List<Cell> getPeriods() {
            return periods;
        }
    }

    public static final class Cell {
        private final Status status;
        private final String subject;

        public Cell(Status status, String subject) {
            this.status = status;
            this.subject = subject == null ? "" : subject;
        }

        public Status getStatus() {
            return status;
        }

        public String getSubject() {
            return subject;
        }
    }

    public enum Status {
        PRESENT("P"),
        ABSENT("A"),
        HOLIDAY("H"),
        LEAVE("L"),
        NOT_APPLICABLE("-"),
        UNKNOWN("?");

        private final String symbol;

        Status(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }

        public static Status fromClasses(String classes) {
            if (classes == null) {
                return UNKNOWN;
            }
            String value = classes.toLowerCase();
            if (value.contains("absent")) {
                return ABSENT;
            }
            if (value.contains("holiday")) {
                return HOLIDAY;
            }
            if (value.contains("dutyleave") || value.contains("leave")
                    || value.contains("onduty") || value.contains("suspension")
                    || value.contains("special")) {
                return LEAVE;
            }
            if (value.contains("n-a")) {
                return NOT_APPLICABLE;
            }
            if (value.contains("present")) {
                return PRESENT;
            }
            return UNKNOWN;
        }
    }
}
