package com.ktutoolkit.etlab;

import com.ktutoolkit.etlab.model.Attendance;
import com.ktutoolkit.etlab.model.AttendanceSummary;
import com.ktutoolkit.etlab.model.FacultyDetails;
import com.ktutoolkit.etlab.model.MarksRecord;
import com.ktutoolkit.etlab.model.MonthlyAttendance;
import com.ktutoolkit.etlab.model.ResultSubject;
import com.ktutoolkit.etlab.model.StudentProfile;
import com.ktutoolkit.etlab.model.SubjectInfo;
import com.ktutoolkit.etlab.model.Timetable;
import com.ktutoolkit.etlab.model.UniversityResult;
import com.ktutoolkit.exceptions.DataNotAvailableException;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight helpers for reading ETLab HTML without third-party libraries.
 */
public final class EtLabDataParser {
    private static final Pattern ANCHOR = Pattern.compile(
            "(?is)<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
    private static final Pattern RATIO = Pattern.compile(
            "(\\d+)\\s*/\\s*(\\d+)(?:\\s*\\(([^)]+)\\))?");
    private static final Pattern PERCENTAGE = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern SGPA = Pattern.compile(
            "(?<!Average )SGPA\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CGPA = Pattern.compile(
            "CGPA\\s+Upto\\s+Current\\s+Semester\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EARNED_CREDITS = Pattern.compile(
            "Earned\\s+Cumulative\\s+Credit\\s*:\\s*(\\S+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_CREDITS = Pattern.compile(
            "Total\\s+Cumulative\\s+Credit\\s*:\\s*(\\S+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "(?is)<div[^>]*class\\s*=\\s*[\"'][^\"']*section-heading[^\"']*[\"'][^>]*>(.*?)</div>");
    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern DAY_NUMBER = Pattern.compile("(\\d+)");

    private EtLabDataParser() {
    }

    /**
     * Finds the first link whose visible text matches the supplied label.
     * Used to locate the "Attendance by subject" report from the attendance page.
     */
    public static String findLinkByText(String html, String label) {
        if (html == null || label == null) {
            return null;
        }
        String wanted = label.toLowerCase(Locale.ROOT);
        Matcher matcher = ANCHOR.matcher(html);
        while (matcher.find()) {
            String text = clean(matcher.group(2).replaceAll("(?is)<[^>]+>", " "));
            if (text.toLowerCase(Locale.ROOT).contains(wanted)) {
                return matcher.group(1).replace("&amp;", "&");
            }
        }
        return null;
    }

    /**
     * Parses the ETLab profile page into titled sections.
     */
    public static StudentProfile parseProfile(String html) throws DataNotAvailableException {
        try {
            String page = HTML_COMMENT.matcher(html == null ? "" : html).replaceAll("");
            List<Heading> headings = new ArrayList<>();
            Matcher matcher = SECTION_HEADING.matcher(page);
            while (matcher.find()) {
                String title = clean(matcher.group(1).replaceAll("(?is)<[^>]+>", " "));
                if (!title.isEmpty()) {
                    headings.add(new Heading(title, matcher.start(), matcher.end()));
                }
            }

            List<StudentProfile.Section> sections = new ArrayList<>();
            for (int i = 0; i < headings.size(); i++) {
                Heading heading = headings.get(i);
                int end = i + 1 < headings.size() ? headings.get(i + 1).start : page.length();
                String segment = page.substring(heading.end, Math.max(heading.end, end));

                Map<String, String> fields = new LinkedHashMap<>();
                for (Table table : parseTables(segment)) {
                    for (List<String> row : table.getRows()) {
                        if (row.size() >= 2) {
                            String label = cell(row, 0);
                            String value = cell(row, 1);
                            if (!label.isEmpty()) {
                                fields.put(label, value);
                            }
                        }
                    }
                }
                if (!fields.isEmpty()) {
                    sections.add(new StudentProfile.Section(heading.title, fields));
                }
            }

            if (sections.isEmpty()) {
                throw new DataNotAvailableException(
                        "Could not find any profile details in the ETLab response.");
            }
            return new StudentProfile(sections);
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab profile page.", e);
        }
    }

    /**
     * Parses the weekly timetable table.
     */
    public static Timetable parseTimetable(String html) throws DataNotAvailableException {
        try {
            Table target = null;
            for (Table table : parseTables(html)) {
                if (hasHeader(table, "Day") && table.getHeaders().size() > 1) {
                    target = table;
                    break;
                }
            }
            if (target == null) {
                throw new DataNotAvailableException(
                        "Could not find the timetable in the ETLab response.");
            }

            List<Timetable.Day> days = new ArrayList<>();
            for (List<String> row : target.getRows()) {
                if (row.size() < 2) {
                    continue;
                }
                String label = cell(row, 0);
                // ETLab labels rows like "Monday 01 Jan 1970"; keep only the day.
                label = label.replaceAll("\\s+\\d{1,2}\\s+\\w+\\s+\\d{4}.*$", "").trim();
                if (label.isEmpty()) {
                    continue;
                }
                List<String> periods = new ArrayList<>();
                for (int i = 1; i < row.size(); i++) {
                    periods.add(cell(row, i));
                }
                days.add(new Timetable.Day(label, periods));
            }
            if (days.isEmpty()) {
                throw new DataNotAvailableException(
                        "The ETLab timetable did not contain any days.");
            }
            return new Timetable(days);
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab timetable page.", e);
        }
    }

    /**
     * Parses the subjects page with teachers and attendance.
     */
    public static List<SubjectInfo> parseSubjects(String html) throws DataNotAvailableException {
        try {
            Table target = null;
            for (Table table : parseTables(html)) {
                if (hasHeader(table, "Subject") && hasHeader(table, "Attendance (%)")) {
                    target = table;
                    break;
                }
            }
            if (target == null) {
                throw new DataNotAvailableException(
                        "Could not find the subjects table in the ETLab response.");
            }

            List<SubjectInfo> subjects = new ArrayList<>();
            for (List<String> row : target.getRows()) {
                if (row.size() < 5) {
                    continue;
                }
                String serial = cell(row, 0);
                if (!serial.matches("\\d+")) {
                    continue;
                }

                String subject = cell(row, 1);
                String code = subject;
                String name = subject;
                int separator = subject.indexOf(" - ");
                if (separator > 0) {
                    code = subject.substring(0, separator).trim();
                    name = subject.substring(separator + 3).trim();
                }

                String attendance = cell(row, 4);
                int present = 0;
                int total = 0;
                int[] ratio = parseRatio(attendance);
                if (ratio != null) {
                    present = ratio[0];
                    total = ratio[1];
                }
                double percentage = parsePercentage(attendance);
                if (percentage < 0.0) {
                    percentage = total == 0 ? 0.0 : (present * 100.0) / total;
                }

                subjects.add(new SubjectInfo(serial, code, name, cell(row, 2),
                        cell(row, 3), present, total, percentage));
            }

            if (subjects.isEmpty()) {
                throw new DataNotAvailableException(
                        "The ETLab subjects table did not contain any subjects.");
            }
            return subjects;
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab subjects page.", e);
        }
    }

    /**
     * Parses the HOD, staff advisor and teacher allocation tables.
     */
    public static FacultyDetails parseFacultyDetails(String html)
            throws DataNotAvailableException {
        try {
            List<FacultyDetails.Contact> heads = new ArrayList<>();
            List<FacultyDetails.Contact> advisors = new ArrayList<>();
            List<FacultyDetails.TeachingAssignment> assignments = new ArrayList<>();

            for (Table table : parseTables(html)) {
                if (hasHeader(table, "HOD")) {
                    for (List<String> row : table.getRows()) {
                        String name = cell(row, 0);
                        if (!name.isEmpty()) {
                            heads.add(new FacultyDetails.Contact(name, cell(row, 1), ""));
                        }
                    }
                } else if (hasHeader(table, "Staff Advisor")) {
                    for (List<String> row : table.getRows()) {
                        String name = cell(row, 0);
                        if (!name.isEmpty()) {
                            advisors.add(new FacultyDetails.Contact(
                                    name, cell(row, 1), cell(row, 2)));
                        }
                    }
                } else if (hasHeader(table, "Teacher") && hasHeader(table, "Subject")) {
                    for (List<String> row : table.getRows()) {
                        if (row.size() < 4) {
                            continue;
                        }
                        String serial = cell(row, 0);
                        if (!serial.matches("\\d+")) {
                            continue;
                        }
                        assignments.add(new FacultyDetails.TeachingAssignment(
                                serial, cell(row, 1), cell(row, 2), cell(row, 3)));
                    }
                }
            }

            if (heads.isEmpty() && advisors.isEmpty() && assignments.isEmpty()) {
                throw new DataNotAvailableException(
                        "Could not find any faculty details in the ETLab response.");
            }
            return new FacultyDetails(heads, advisors, assignments);
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab faculty page.", e);
        }
    }

    /**
     * Reads the default values available in ETLab's attendance filter form.
     * Used by the sync service to request the latest month with real data.
     */
    public static int[] latestAttendancePeriod(String html) {
        int semester = optionValue(html, "semester", 1);
        int month = optionValue(html, "month", 1);
        int year = optionValue(html, "year", java.time.LocalDate.now().getYear());
        return new int[]{semester, month, year};
    }

    private static int optionValue(String html, String name, int fallback) {
        Pattern select = Pattern.compile(
                "(?is)<select[^>]*name\\s*=\\s*[\"']" + Pattern.quote(name)
                        + "[\"'][^>]*>(.*?)</select>");
        Matcher selectMatcher = select.matcher(html == null ? "" : html);
        if (!selectMatcher.find()) {
            return fallback;
        }
        Matcher optionMatcher = Pattern.compile("(?is)<option[^>]*value\\s*=\\s*[\"'](\\d+)[\"']")
                .matcher(selectMatcher.group(1));
        int best = fallback;
        while (optionMatcher.find()) {
            try {
                best = Integer.parseInt(optionMatcher.group(1));
            } catch (NumberFormatException ignored) {
                // Keep the previous value.
            }
        }
        return best;
    }

    /**
     * Parses the monthly attendance grid. Each period cell carries its status
     * in the CSS class (present, absent, holiday, n-a, leave, ...).
     */
    public static MonthlyAttendance parseMonthlyAttendance(String html, int semester,
                                                           int month, int year)
            throws DataNotAvailableException {
        AttendanceGridParser parser = new AttendanceGridParser();
        try {
            new ParserDelegator().parse(new StringReader(html == null ? "" : html), parser, true);
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab attendance page.", e);
        }

        if (!parser.foundTable) {
            throw new DataNotAvailableException(
                    "Could not find the monthly attendance grid in the ETLab response.");
        }

        List<MonthlyAttendance.Day> days = new ArrayList<>();
        for (List<AttendanceGridParser.Cell> row : parser.rows) {
            if (row.size() < 2) {
                continue;
            }
            String rawLabel = row.get(0).text;
            Matcher number = DAY_NUMBER.matcher(rawLabel);
            if (!number.find()) {
                continue;
            }
            String label = number.group(1);
            if (label.isBlank()) {
                continue;
            }

            List<MonthlyAttendance.Cell> periods = new ArrayList<>();
            for (int i = 1; i < row.size(); i++) {
                AttendanceGridParser.Cell source = row.get(i);
                periods.add(new MonthlyAttendance.Cell(
                        MonthlyAttendance.Status.fromClasses(source.classes),
                        source.text));
            }
            days.add(new MonthlyAttendance.Day(label, periods));
        }

        if (days.isEmpty()) {
            throw new DataNotAvailableException(
                    "The ETLab attendance grid did not contain any days.");
        }
        return new MonthlyAttendance(semester, month, year, days);
    }

    /**
     * Parses ETLab's subject-wise attendance summary table.
     *
     * <p>The table has one row: university register number, roll number,
     * student name, one attendance ratio per subject, then duty leave, total,
     * percentage and duty-leave percentage.</p>
     */
    public static AttendanceSummary parseAttendanceSummary(String html)
            throws DataNotAvailableException {
        try {
            List<Table> tables = parseTables(html);
            Table table = null;
            for (Table candidate : tables) {
                if (!candidate.getHeaders().isEmpty() && !candidate.getRows().isEmpty()) {
                    table = candidate;
                    break;
                }
            }
            if (table == null) {
                throw new DataNotAvailableException(
                        "Could not find the attendance summary table in the ETLab response.");
            }

            List<String> headers = table.getHeaders();
            List<String> row = table.getRows().get(0);
            if (headers.size() < 4 || row.size() < 4) {
                throw new DataNotAvailableException(
                        "The attendance summary table did not contain the expected columns.");
            }

            String universityRegisterNumber = cell(row, 0);
            String rollNumber = cell(row, 1);
            String name = cell(row, 2);

            List<Attendance> subjects = new ArrayList<>();
            int totalPresent = 0;
            int totalHours = 0;
            double overallPercentage = -1.0;
            String dutyLeave = "";
            String dutyLeavePercentage = "";

            int limit = Math.min(headers.size(), row.size());
            for (int i = 3; i < limit; i++) {
                String header = clean(headers.get(i));
                String value = cell(row, i);
                String normalized = header.toLowerCase(Locale.ROOT);

                if (normalized.equals("total")) {
                    int[] ratio = parseRatio(value);
                    if (ratio != null) {
                        totalPresent = ratio[0];
                        totalHours = ratio[1];
                    }
                } else if (normalized.equals("percentage")) {
                    overallPercentage = parsePercentage(value);
                } else if (normalized.equals("duty leave")) {
                    dutyLeave = value;
                } else if (normalized.equals("duty leave percentage")) {
                    dutyLeavePercentage = value;
                } else {
                    Attendance attendance = parseSubjectAttendance(header, value);
                    if (attendance != null) {
                        subjects.add(attendance);
                    }
                }
            }

            if (totalHours == 0) {
                for (Attendance attendance : subjects) {
                    totalPresent += attendance.getPresentHours();
                    totalHours += attendance.getTotalHours();
                }
            }
            if (overallPercentage < 0.0) {
                overallPercentage = totalHours == 0 ? 0.0 : (totalPresent * 100.0) / totalHours;
            }

            return new AttendanceSummary(universityRegisterNumber, rollNumber, name, subjects,
                    totalPresent, totalHours, overallPercentage, dutyLeave, dutyLeavePercentage);
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab attendance page.", e);
        }
    }

    /**
     * Parses ETLab's university result table for one semester.
     */
    public static UniversityResult parseUniversityResult(String html, int semester)
            throws DataNotAvailableException {
        try {
            List<Table> tables = parseTables(html);
            Table resultTable = null;
            for (Table candidate : tables) {
                if (hasHeader(candidate, "Subject Code")) {
                    resultTable = candidate;
                    break;
                }
            }
            if (resultTable == null) {
                throw new DataNotAvailableException(
                        "Could not find the university result table in the ETLab response.");
            }

            List<ResultSubject> subjects = new ArrayList<>();
            String sgpa = "";
            String earnedCredits = "";
            String totalCredits = "";
            String status = "";

            for (List<String> row : resultTable.getRows()) {
                String joined = String.join(" ", row);
                String upper = joined.toUpperCase(Locale.ROOT);

                if (upper.contains("EARNED CUMULATIVE CREDIT")) {
                    earnedCredits = firstGroup(EARNED_CREDITS, joined);
                    totalCredits = firstGroup(TOTAL_CREDITS, joined);
                    status = statusFromRow(row);
                    continue;
                }

                Matcher sgpaMatcher = SGPA.matcher(joined);
                if (sgpaMatcher.find()) {
                    sgpa = sgpaMatcher.group(1);
                    continue;
                }

                if (row.size() < 8) {
                    continue;
                }

                String code = cell(row, 0);
                if (code.isEmpty()) {
                    continue;
                }

                String grade = "";
                String result = "";
                for (int i = 6; i < row.size(); i++) {
                    String candidate = cell(row, i).toUpperCase(Locale.ROOT);
                    if (candidate.isEmpty()) {
                        continue;
                    }
                    if (candidate.equals("PASSED") || candidate.equals("FAILED")) {
                        result = candidate;
                    } else if (grade.isEmpty()
                            && candidate.matches("S|A\\+|A|B\\+|B|C|P|F|-|AB|I")) {
                        grade = candidate;
                    }
                }

                subjects.add(new ResultSubject(
                        code,
                        cell(row, 1),
                        cell(row, 2),
                        cell(row, 3),
                        cell(row, 4),
                        cell(row, 5),
                        grade,
                        result));
            }

            if (sgpa.isBlank()) {
                sgpa = firstGroup(SGPA, html);
            }
            String cgpa = firstGroup(CGPA, html);
            if (earnedCredits.isBlank()) {
                earnedCredits = firstGroup(EARNED_CREDITS, html);
            }
            if (totalCredits.isBlank()) {
                totalCredits = firstGroup(TOTAL_CREDITS, html);
            }

            return new UniversityResult(
                    semester,
                    orNotAvailable(sgpa),
                    orNotAvailable(cgpa),
                    orNotAvailable(earnedCredits),
                    orNotAvailable(totalCredits),
                    "N/A",
                    subjects);
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab university result page.", e);
        }
    }

    /**
     * Parses the internal-mark tables from a university result page.
     */
    public static List<MarksRecord> parseInternalMarks(String html, int semester)
            throws DataNotAvailableException {
        List<MarksRecord> marks = new ArrayList<>();
        try {
            for (Table table : parseTables(html)) {
                int maxIndex = headerIndex(table, "Maximum Marks");
                int obtainedIndex = headerIndex(table, "Marks Obtained");
                if (maxIndex < 0 || obtainedIndex < 0) {
                    continue;
                }
                int examIndex = headerIndex(table, "Exam");

                for (List<String> row : table.getRows()) {
                    String subject = cell(row, 0);
                    if (subject.isEmpty() || !subject.matches("(?i).*[A-Z]{2,}\\d+.*")) {
                        continue;
                    }
                    marks.add(new MarksRecord(
                            semester,
                            extractSubjectCode(subject),
                            subject,
                            examIndex >= 0 ? cell(row, examIndex) : "",
                            parseNumber(cell(row, maxIndex)),
                            parseNumber(cell(row, obtainedIndex))));
                }
            }
            return marks;
        } catch (IOException e) {
            throw new DataNotAvailableException("Could not parse the ETLab marks tables.", e);
        }
    }

    /**
     * Extracts a subject code from a label such as "GAMAT301 - MATHEMATICS...".
     */
    public static String extractSubjectCode(String label) {
        String text = clean(label);
        int separator = text.indexOf(" - ");
        if (separator > 0) {
            return text.substring(0, separator).trim();
        }
        Matcher matcher = Pattern.compile("([A-Z]{2,}\\d+)").matcher(text.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : text;
    }

    private static int headerIndex(Table table, String name) {
        for (int i = 0; i < table.getHeaders().size(); i++) {
            if (clean(table.getHeaders().get(i)).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static double parseNumber(String value) {
        Matcher matcher = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(value == null ? "" : value);
        if (!matcher.find()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean hasHeader(Table table, String header) {
        for (String value : table.getHeaders()) {
            if (clean(value).equalsIgnoreCase(header)) {
                return true;
            }
        }
        return false;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String statusFromRow(List<String> row) {
        for (int i = row.size() - 1; i >= 0; i--) {
            String value = cell(row, i).toUpperCase(Locale.ROOT);
            if (value.equals("PASSED") || value.equals("FAILED")) {
                return value;
            }
        }
        return "";
    }

    private static String orNotAvailable(String value) {
        return value == null || value.isBlank() ? "N/A" : clean(value);
    }

    private static Attendance parseSubjectAttendance(String subjectCode, String value) {
        int[] ratio = parseRatio(value);
        if (ratio == null) {
            return null;
        }
        double percentage = ratio[1] == 0 ? 0.0 : (ratio[0] * 100.0) / ratio[1];
        double shownPercentage = parsePercentage(value);
        if (shownPercentage >= 0.0) {
            percentage = shownPercentage;
        }
        return new Attendance(subjectCode, ratio[0], ratio[1], percentage);
    }

    private static int[] parseRatio(String value) {
        Matcher matcher = RATIO.matcher(value == null ? "" : value);
        if (!matcher.find()) {
            return null;
        }
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))
        };
    }

    private static double parsePercentage(String value) {
        Matcher matcher = PERCENTAGE.matcher(value == null ? "" : value);
        if (!matcher.find()) {
            return -1.0;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? clean(row.get(index)) : "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static List<Table> parseTables(String html) throws IOException {
        TableParser parser = new TableParser();
        new ParserDelegator().parse(new StringReader(html == null ? "" : html), parser, true);
        return parser.tables;
    }

    private static final class Heading {
        private final String title;
        private final int start;
        private final int end;

        private Heading(String title, int start, int end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
    }

    public static final class Table {
        private final List<String> headers = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();

        public List<String> getHeaders() {
            return headers;
        }

        public List<List<String>> getRows() {
            return rows;
        }
    }

    /**
     * ETLab emits unclosed &lt;tr&gt; and &lt;td&gt; tags, so the parser has to
     * flush the previous row/cell whenever a new one starts. Colspans are
     * expanded so that every row has the same number of columns.
     */
    private static final class TableParser extends HTMLEditorKit.ParserCallback {
        private final List<Table> tables = new ArrayList<>();
        private Table currentTable;
        private List<String> currentRow;
        private StringBuilder currentCell;
        private boolean currentRowAllHeaders;
        private int currentColspan = 1;
        private boolean inHead;
        private int tableDepth;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.TABLE) {
                tableDepth++;
                if (tableDepth == 1 && currentTable == null) {
                    currentTable = new Table();
                    inHead = false;
                }
                return;
            }
            if (currentTable == null) {
                return;
            }

            if (tag == HTML.Tag.TR) {
                flushRow();
                currentRow = new ArrayList<>();
                currentRowAllHeaders = true;
            } else if ("thead".equalsIgnoreCase(tag.toString())) {
                inHead = true;
            } else if ("tbody".equalsIgnoreCase(tag.toString())
                    || "tfoot".equalsIgnoreCase(tag.toString())) {
                inHead = false;
            } else if (tag == HTML.Tag.TH || tag == HTML.Tag.TD) {
                flushCell();
                if (currentRow == null) {
                    currentRow = new ArrayList<>();
                    currentRowAllHeaders = true;
                }
                currentCell = new StringBuilder();
                if (tag == HTML.Tag.TD) {
                    currentRowAllHeaders = false;
                }
                currentColspan = intAttribute(attributes, HTML.Attribute.COLSPAN);
            } else if (tag == HTML.Tag.BR && currentCell != null) {
                currentCell.append(' ');
            }
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (tag == HTML.Tag.TABLE) {
                if (tableDepth == 1 && currentTable != null) {
                    flushCell();
                    flushRow();
                    if (currentTable.headers.isEmpty() && !currentTable.rows.isEmpty()) {
                        currentTable.headers.addAll(currentTable.rows.remove(0));
                    }
                    tables.add(currentTable);
                    currentTable = null;
                }
                if (tableDepth > 0) {
                    tableDepth--;
                }
            } else if (currentTable != null) {
                if (tag == HTML.Tag.TH || tag == HTML.Tag.TD) {
                    flushCell();
                } else if (tag == HTML.Tag.TR) {
                    flushRow();
                } else if ("thead".equalsIgnoreCase(tag.toString())) {
                    inHead = false;
                }
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            if (currentCell != null) {
                currentCell.append(data);
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.BR && currentCell != null) {
                currentCell.append(' ');
            }
        }

        private void flushCell() {
            if (currentCell == null) {
                return;
            }
            if (currentRow == null) {
                currentRow = new ArrayList<>();
            }
            currentRow.add(clean(currentCell.toString()));
            for (int i = 1; i < currentColspan; i++) {
                currentRow.add("");
            }
            currentCell = null;
            currentColspan = 1;
        }

        private void flushRow() {
            if (currentRow == null) {
                return;
            }
            if (!currentRow.isEmpty() && currentTable != null) {
                if ((inHead || currentRowAllHeaders) && currentTable.headers.isEmpty()) {
                    currentTable.headers.addAll(currentRow);
                } else {
                    currentTable.rows.add(currentRow);
                }
            }
            currentRow = null;
            currentRowAllHeaders = false;
        }

        private static int intAttribute(MutableAttributeSet attributes, HTML.Attribute name) {
            Object value = attributes.getAttribute(name);
            if (value == null) {
                return 1;
            }
            try {
                return Math.max(1, Integer.parseInt(value.toString().trim()));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
    }

    /**
     * Captures the monthly attendance grid, including each cell's CSS class.
     */
    private static final class AttendanceGridParser extends HTMLEditorKit.ParserCallback {
        private final List<List<Cell>> rows = new ArrayList<>();
        private boolean foundTable;
        private boolean insideTable;
        private int nestedTableDepth;
        private List<Cell> currentRow;
        private StringBuilder currentText;
        private String currentClasses = "";
        private int currentColspan = 1;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.TABLE) {
                if (!insideTable) {
                    Object id = attributes.getAttribute(HTML.Attribute.ID);
                    if (id != null && "itsthetable".equalsIgnoreCase(id.toString())) {
                        insideTable = true;
                        foundTable = true;
                    }
                } else {
                    nestedTableDepth++;
                }
                return;
            }
            if (!insideTable) {
                return;
            }

            if (tag == HTML.Tag.TR) {
                flushRow();
                currentRow = new ArrayList<>();
            } else if (tag == HTML.Tag.TH || tag == HTML.Tag.TD) {
                flushCell();
                if (currentRow == null) {
                    currentRow = new ArrayList<>();
                }
                currentText = new StringBuilder();
                Object classes = attributes.getAttribute(HTML.Attribute.CLASS);
                currentClasses = classes == null ? "" : classes.toString();
                currentColspan = intAttribute(attributes, HTML.Attribute.COLSPAN);
            } else if (tag == HTML.Tag.BR && currentText != null) {
                currentText.append(' ');
            }
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (!insideTable) {
                return;
            }
            if (tag == HTML.Tag.TABLE) {
                if (nestedTableDepth > 0) {
                    nestedTableDepth--;
                } else {
                    flushCell();
                    flushRow();
                    insideTable = false;
                }
            } else if (tag == HTML.Tag.TH || tag == HTML.Tag.TD) {
                flushCell();
            } else if (tag == HTML.Tag.TR) {
                flushRow();
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            if (insideTable && currentText != null) {
                currentText.append(data);
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.BR && currentText != null) {
                currentText.append(' ');
            }
        }

        private void flushCell() {
            if (currentText == null) {
                return;
            }
            if (currentRow == null) {
                currentRow = new ArrayList<>();
            }
            Cell cell = new Cell(currentClasses, clean(currentText.toString()));
            currentRow.add(cell);
            for (int i = 1; i < currentColspan; i++) {
                currentRow.add(new Cell(currentClasses, ""));
            }
            currentText = null;
            currentClasses = "";
            currentColspan = 1;
        }

        private void flushRow() {
            if (currentRow != null && !currentRow.isEmpty()) {
                rows.add(currentRow);
            }
            currentRow = null;
        }

        private static int intAttribute(MutableAttributeSet attributes, HTML.Attribute name) {
            Object value = attributes.getAttribute(name);
            if (value == null) {
                return 1;
            }
            try {
                return Math.max(1, Integer.parseInt(value.toString().trim()));
            } catch (NumberFormatException e) {
                return 1;
            }
        }

        private static final class Cell {
            private final String classes;
            private final String text;

            private Cell(String classes, String text) {
                this.classes = classes == null ? "" : classes;
                this.text = text;
            }
        }
    }
}
