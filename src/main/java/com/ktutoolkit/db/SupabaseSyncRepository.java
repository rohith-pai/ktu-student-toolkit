package com.ktutoolkit.db;

import com.ktutoolkit.core.JsonObject;
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

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBC upserts for the ETLab tables in Supabase PostgreSQL.
 */
public class SupabaseSyncRepository implements AutoCloseable {
    private final DatabaseConnection database = new DatabaseConnection();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public Connection connection() throws SQLException {
        return database.connect();
    }

    public long upsertStudent(String etlabUserId, String username, StudentProfile profile)
            throws SQLException {
        Map<String, String> fields = allFields(profile);
        String name = first(fields, "Name");
        String gender = first(fields, "Gender");
        String regNo = first(fields, "University Reg No");
        String rollNo = first(fields, "Admission No");
        String phone = first(fields, "Student Mobile No", "Whatsapp Number", "Phone Number (Home)");
        String email = first(fields, "Email", "College Email Id");

        return queryLong(
                "insert into students (etlab_user_id, username, university_reg_no, roll_number, "
                        + "name, gender, date_of_birth, phone, email, raw_profile, updated_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?::jsonb, now()) "
                        + "on conflict (etlab_user_id) do update set "
                        + "username=excluded.username, university_reg_no=excluded.university_reg_no, "
                        + "roll_number=excluded.roll_number, name=excluded.name, "
                        + "gender=excluded.gender, date_of_birth=excluded.date_of_birth, "
                        + "phone=excluded.phone, email=excluded.email, "
                        + "raw_profile=excluded.raw_profile, updated_at=now() "
                        + "returning id",
                etlabUserId, username, regNo, rollNo, name, gender,
                parseDate(first(fields, "Date of Birth")), phone, email, json(fields));
    }

    public void replaceGuardians(long studentId, StudentProfile profile) throws SQLException {
        Map<String, String> fields = allFields(profile);
        Map<String, String> father = new LinkedHashMap<>();
        Map<String, String> mother = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String label = entry.getKey().toLowerCase(Locale.ROOT);
            if (label.contains("father")) {
                father.put(entry.getKey(), entry.getValue());
            } else if (label.contains("mother")) {
                mother.put(entry.getKey(), entry.getValue());
            }
        }
        execute("delete from guardians where student_id=?", studentId);
        execute("insert into guardians (student_id, father, mother, raw, updated_at) "
                        + "values (?,?::jsonb,?::jsonb,?::jsonb, now())",
                studentId, json(father), json(mother), json(fields));
    }

    public void replaceBankAccounts(long studentId, StudentProfile profile) throws SQLException {
        Map<String, String> fields = allFields(profile);
        Map<String, String> bank = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String label = entry.getKey().toLowerCase(Locale.ROOT);
            if (label.contains("bank") || label.contains("branch")
                    || label.contains("account") || label.contains("ifsc")) {
                bank.put(entry.getKey(), entry.getValue());
            }
        }
        execute("delete from bank_accounts where student_id=?", studentId);
        execute("insert into bank_accounts (student_id, details, updated_at) "
                        + "values (?,?::jsonb, now())",
                studentId, json(bank));
    }

    public void upsertSubjects(List<SubjectInfo> subjects) throws SQLException {
        for (SubjectInfo subject : subjects) {
            execute("insert into subjects (subject_code, subject_name, type, updated_at) "
                            + "values (?,?,?,now()) "
                            + "on conflict (subject_code) do update set "
                            + "subject_name=excluded.subject_name, type=excluded.type, "
                            + "updated_at=now()",
                    subject.getCode(), subject.getName(), subject.getType());
        }
    }

    public Map<String, Long> upsertTeachers(FacultyDetails details) throws SQLException {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (FacultyDetails.Contact contact : details.getHeadsOfDepartment()) {
            rememberTeacher(ids, contact.getName(), contact.getEmail(), contact.getPhone(), "HOD");
        }
        for (FacultyDetails.Contact contact : details.getStaffAdvisors()) {
            rememberTeacher(ids, contact.getName(), contact.getEmail(), contact.getPhone(),
                    "STAFF_ADVISOR");
        }
        for (FacultyDetails.TeachingAssignment assignment : details.getTeachingAssignments()) {
            long teacherId = rememberTeacher(ids, assignment.getTeacher(), assignment.getEmail(),
                    "", "TEACHER");
            String subjectLabel = assignment.getSubject();
            String code = extractCode(subjectLabel);
            String name = subjectLabel.contains(" - ")
                    ? subjectLabel.substring(subjectLabel.indexOf(" - ") + 3).trim()
                    : subjectLabel;
            execute("insert into subjects (subject_code, subject_name, updated_at) "
                            + "values (?,?,now()) "
                            + "on conflict (subject_code) do update set "
                            + "subject_name=excluded.subject_name, updated_at=now()",
                    code, name);
            execute("insert into teacher_subjects (teacher_id, subject_code) values (?,?) "
                            + "on conflict do nothing",
                    teacherId, code);
        }
        return ids;
    }

    private long rememberTeacher(Map<String, Long> ids, String name, String email, String phone,
                                 String role) throws SQLException {
        String key = normalise(name);
        if (ids.containsKey(key)) {
            return ids.get(key);
        }
        long id = queryLong(
                "insert into teachers (name, email, phone, role) "
                        + "values (?, coalesce(?, ''), coalesce(?, ''), ?) "
                        + "on conflict (name, email) do update set "
                        + "phone=excluded.phone, role=excluded.role returning id",
                name, email, phone, role);
        ids.put(key, id);
        return id;
    }

    public void replaceTimetable(long studentId, Timetable timetable, Map<String, Long> teacherIds)
            throws SQLException {
        execute("delete from timetable_slots where student_id=?", studentId);
        for (Timetable.Day day : timetable.getDays()) {
            List<String> periods = day.getPeriods();
            for (int i = 0; i < periods.size(); i++) {
                String text = periods.get(i);
                Long teacherId = null;
                for (Map.Entry<String, Long> entry : teacherIds.entrySet()) {
                    if (!entry.getKey().isEmpty() && normalise(text).contains(entry.getKey())) {
                        teacherId = entry.getValue();
                        break;
                    }
                }
                execute("insert into timetable_slots "
                                + "(student_id, day, period, subject_code, teacher_id, raw_text, updated_at) "
                                + "values (?,?,?,?,?,?,now())",
                        studentId, day.getLabel(), i + 1, nullIfBlank(extractCode(text)),
                        teacherId, nullIfBlank(text));
            }
        }
    }

    public void upsertAttendanceSummary(long studentId, AttendanceSummary summary)
            throws SQLException {
        for (Attendance attendance : summary.getSubjects()) {
            execute("insert into attendance_summary "
                            + "(student_id, subject_code, present_hours, total_hours, percentage, updated_at) "
                            + "values (?,?,?,?,?,now()) "
                            + "on conflict (student_id, subject_code) do update set "
                            + "present_hours=excluded.present_hours, "
                            + "total_hours=excluded.total_hours, "
                            + "percentage=excluded.percentage, updated_at=now()",
                    studentId, attendance.getSubjectCode(), attendance.getPresentHours(),
                    attendance.getTotalHours(), attendance.getPercentage());
        }
    }

    public void replaceMonthAttendance(long studentId, MonthlyAttendance attendance)
            throws SQLException {
        LocalDate from = YearMonth.of(attendance.getYear(), attendance.getMonth()).atDay(1);
        LocalDate to = YearMonth.of(attendance.getYear(), attendance.getMonth()).atEndOfMonth();
        execute("delete from attendance_daily where student_id=? and attendance_date between ? and ?",
                studentId, Date.valueOf(from), Date.valueOf(to));

        for (MonthlyAttendance.Day day : attendance.getDays()) {
            int dayNumber;
            try {
                dayNumber = Integer.parseInt(day.getLabel());
            } catch (NumberFormatException e) {
                continue;
            }
            LocalDate date;
            try {
                date = YearMonth.of(attendance.getYear(), attendance.getMonth()).atDay(dayNumber);
            } catch (DateTimeException e) {
                continue;
            }
            List<MonthlyAttendance.Cell> periods = day.getPeriods();
            for (int i = 0; i < periods.size(); i++) {
                MonthlyAttendance.Cell cell = periods.get(i);
                execute("insert into attendance_daily "
                                + "(student_id, attendance_date, period, status, subject_code, topic, updated_at) "
                                + "values (?,?,?,?,?,?,now())",
                        studentId, Date.valueOf(date), i + 1, cell.getStatus().name(),
                        nullIfBlank(extractCode(cell.getSubject())), nullIfBlank(cell.getSubject()));
            }
        }
    }

    public void replaceMarks(long studentId, List<MarksRecord> marks) throws SQLException {
        execute("delete from marks where student_id=?", studentId);
        for (MarksRecord mark : marks) {
            execute("insert into subjects (subject_code, subject_name, updated_at) "
                            + "values (?,?,now()) on conflict (subject_code) do nothing",
                    mark.getSubjectCode(), mark.getSubject());
            execute("insert into marks "
                            + "(student_id, subject_code, exam, max_marks, marks_obtained, updated_at) "
                            + "values (?,?,?,?,?,now())",
                    studentId, mark.getSubjectCode(), nullIfBlank(mark.getExam()),
                    mark.getMaxMarks(), mark.getMarksObtained());
        }
    }

    public void upsertResult(long studentId, UniversityResult result) throws SQLException {
        long resultId = queryLong(
                "insert into university_results "
                        + "(student_id, semester, sgpa, cgpa, earned_credits, total_credits, status, raw, updated_at) "
                        + "values (?,?,?,?,?,?,?,?::jsonb,now()) "
                        + "on conflict (student_id, semester) do update set "
                        + "sgpa=excluded.sgpa, cgpa=excluded.cgpa, "
                        + "earned_credits=excluded.earned_credits, "
                        + "total_credits=excluded.total_credits, status=excluded.status, "
                        + "raw=excluded.raw, updated_at=now() returning id",
                studentId, result.getSemester(), number(result.getSgpa()), number(result.getCgpa()),
                number(result.getEarnedCredits()), number(result.getTotalCredits()),
                result.getStatus(), "{}");

        execute("delete from result_subjects where result_id=?", resultId);
        for (ResultSubject subject : result.getSubjects()) {
            execute("insert into result_subjects "
                            + "(result_id, subject_code, subject_name, attendance, attendance_pct, "
                            + "earned_credits, total_credits, grade, result) "
                            + "values (?,?,?,?,?,?,?,?,?)",
                    resultId, subject.getSubjectCode(), subject.getSubjectName(),
                    nullIfBlank(subject.getAttendance()), nullIfBlank(subject.getAttendancePercentage()),
                    nullIfBlank(subject.getEarnedCredits()), nullIfBlank(subject.getTotalCredits()),
                    nullIfBlank(subject.getGrade()), nullIfBlank(subject.getResult()));
        }
    }

    public void log(long studentId, String endpoint, String status) throws SQLException {
        execute("insert into sync_log (student_id, endpoint, status, synced_at) values (?,?,?,now())",
                studentId, endpoint, status);
    }

    /* ---------------- Read-back methods (Supabase cache mode) ---------------- */

    public boolean hasSyncedData() {
        try (PreparedStatement statement = connection()
                .prepareStatement("select 1 from sync_log limit 1");
             ResultSet result = statement.executeQuery()) {
            return result.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public StudentProfile readProfile() throws SQLException {
        Map<String, String> personal = new LinkedHashMap<>();
        Map<String, String> parent = new LinkedHashMap<>();
        Map<String, String> bank = new LinkedHashMap<>();

        try (PreparedStatement statement = connection().prepareStatement(
                "select name, gender, university_reg_no, roll_number, phone, email, "
                        + "raw_profile::text from students limit 1");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                personal.putAll(JsonObject.parse(result.getString(7)));
                putIfAbsent(personal, "Name", result.getString(1));
                putIfAbsent(personal, "Gender", result.getString(2));
                putIfAbsent(personal, "University Reg No", result.getString(3));
                putIfAbsent(personal, "Admission No", result.getString(4));
                putIfAbsent(personal, "Student Mobile No", result.getString(5));
                putIfAbsent(personal, "Email", result.getString(6));
            }
        }

        try (PreparedStatement statement = connection()
                .prepareStatement("select father::text, mother::text from guardians limit 1");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                parent.putAll(JsonObject.parse(result.getString(1)));
                parent.putAll(JsonObject.parse(result.getString(2)));
            }
        }

        try (PreparedStatement statement = connection()
                .prepareStatement("select details::text from bank_accounts limit 1");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                bank.putAll(JsonObject.parse(result.getString(1)));
            }
        }

        List<StudentProfile.Section> sections = new ArrayList<>();
        if (!personal.isEmpty()) {
            sections.add(new StudentProfile.Section("Personal Details", personal));
        }
        if (!parent.isEmpty()) {
            sections.add(new StudentProfile.Section("Parent Details", parent));
        }
        if (!bank.isEmpty()) {
            sections.add(new StudentProfile.Section("Bank Details", bank));
        }
        return new StudentProfile(sections);
    }

    public Timetable readTimetable() throws SQLException {
        Map<String, Map<Integer, String>> grouped = new LinkedHashMap<>();
        try (PreparedStatement statement = connection().prepareStatement(
                "select day, period, raw_text from timetable_slots order by id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String day = result.getString(1);
                grouped.computeIfAbsent(day, key -> new java.util.TreeMap<>())
                        .put(result.getInt(2), orEmpty(result.getString(3)));
            }
        }

        List<Timetable.Day> days = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, String>> entry : grouped.entrySet()) {
            List<String> periods = new ArrayList<>();
            for (int i = 1; i <= 9; i++) {
                periods.add(entry.getValue().getOrDefault(i, ""));
            }
            days.add(new Timetable.Day(entry.getKey(), periods));
        }
        return new Timetable(days);
    }

    public List<SubjectInfo> readSubjects() throws SQLException {
        List<SubjectInfo> subjects = new ArrayList<>();
        String sql = "select s.subject_code, s.subject_name, s.type, "
                + "coalesce(a.present_hours, 0), coalesce(a.total_hours, 0), "
                + "coalesce(a.percentage, 0), "
                + "(select string_agg(t.name, ' ') from teacher_subjects ts "
                + " join teachers t on t.id = ts.teacher_id where ts.subject_code = s.subject_code) "
                + "from subjects s left join attendance_summary a "
                + "on a.subject_code = s.subject_code order by s.subject_code";

        try (PreparedStatement statement = connection().prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                subjects.add(new SubjectInfo(
                        "",
                        orEmpty(result.getString(1)),
                        orEmpty(result.getString(2)),
                        orEmpty(result.getString(3)),
                        orEmpty(result.getString(7)),
                        result.getInt(4),
                        result.getInt(5),
                        result.getDouble(6)));
            }
        }
        return subjects;
    }

    public FacultyDetails readFaculty() throws SQLException {
        List<FacultyDetails.Contact> heads = new ArrayList<>();
        List<FacultyDetails.Contact> advisors = new ArrayList<>();
        List<FacultyDetails.TeachingAssignment> assignments = new ArrayList<>();

        try (PreparedStatement statement = connection().prepareStatement(
                "select name, email, phone, role from teachers order by id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                FacultyDetails.Contact contact = new FacultyDetails.Contact(
                        orEmpty(result.getString(1)), orEmpty(result.getString(2)),
                        orEmpty(result.getString(3)));
                String role = orEmpty(result.getString(4));
                if ("HOD".equalsIgnoreCase(role)) {
                    heads.add(contact);
                } else if ("STAFF_ADVISOR".equalsIgnoreCase(role)) {
                    advisors.add(contact);
                }
            }
        }

        String sql = "select ts.subject_code, coalesce(s.subject_name, ts.subject_code), "
                + "t.name, coalesce(t.email, '') "
                + "from teacher_subjects ts join teachers t on t.id = ts.teacher_id "
                + "left join subjects s on s.subject_code = ts.subject_code "
                + "where t.role = 'TEACHER' order by t.id, ts.subject_code";
        try (PreparedStatement statement = connection().prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            int serial = 1;
            while (result.next()) {
                assignments.add(new FacultyDetails.TeachingAssignment(
                        String.valueOf(serial++), orEmpty(result.getString(3)),
                        orEmpty(result.getString(1)) + " - " + orEmpty(result.getString(2)),
                        orEmpty(result.getString(4))));
            }
        }
        return new FacultyDetails(heads, advisors, assignments);
    }

    public AttendanceSummary readAttendanceSummary() throws SQLException {
        String regNo = "";
        String rollNo = "";
        String name = "";
        try (PreparedStatement statement = connection().prepareStatement(
                "select university_reg_no, roll_number, name from students limit 1");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                regNo = orEmpty(result.getString(1));
                rollNo = orEmpty(result.getString(2));
                name = orEmpty(result.getString(3));
            }
        }

        List<Attendance> subjects = new ArrayList<>();
        int presentTotal = 0;
        int hoursTotal = 0;
        try (PreparedStatement statement = connection().prepareStatement(
                "select subject_code, present_hours, total_hours, percentage "
                        + "from attendance_summary order by subject_code");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                int present = result.getInt(2);
                int hours = result.getInt(3);
                double percentage = result.getDouble(4);
                subjects.add(new Attendance(orEmpty(result.getString(1)), present, hours, percentage));
                presentTotal += present;
                hoursTotal += hours;
            }
        }
        double overall = hoursTotal == 0 ? 0.0 : (presentTotal * 100.0) / hoursTotal;
        return new AttendanceSummary(regNo, rollNo, name, subjects, presentTotal, hoursTotal,
                overall, "", "");
    }

    public MonthlyAttendance readMonthlyAttendance() throws SQLException {
        int semester = 1;
        int month = java.time.LocalDate.now().getMonthValue();
        int year = java.time.LocalDate.now().getYear();

        try (PreparedStatement statement = connection().prepareStatement(
                "select max(attendance_date) from attendance_daily");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                Date date = result.getDate(1);
                if (date != null) {
                    java.time.LocalDate local = date.toLocalDate();
                    month = local.getMonthValue();
                    year = local.getYear();
                }
            }
        }

        Map<String, List<MonthlyAttendance.Cell>> grouped = new LinkedHashMap<>();
        try (PreparedStatement statement = connection().prepareStatement(
                "select attendance_date, period, status, subject_code, topic "
                        + "from attendance_daily "
                        + "where extract(month from attendance_date) = ? "
                        + "and extract(year from attendance_date) = ? "
                        + "order by attendance_date, period")) {
            statement.setInt(1, month);
            statement.setInt(2, year);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String day = String.valueOf(rows.getDate(1).toLocalDate().getDayOfMonth());
                    MonthlyAttendance.Status status;
                    try {
                        status = MonthlyAttendance.Status.valueOf(orEmpty(rows.getString(3)));
                    } catch (IllegalArgumentException e) {
                        status = MonthlyAttendance.Status.UNKNOWN;
                    }
                    grouped.computeIfAbsent(day, key -> new ArrayList<>())
                            .add(new MonthlyAttendance.Cell(status, orEmpty(rows.getString(5))));
                }
            }
        }

        List<MonthlyAttendance.Day> days = new ArrayList<>();
        for (Map.Entry<String, List<MonthlyAttendance.Cell>> entry : grouped.entrySet()) {
            List<MonthlyAttendance.Cell> periods = new ArrayList<>(entry.getValue());
            while (periods.size() < 9) {
                periods.add(new MonthlyAttendance.Cell(MonthlyAttendance.Status.NOT_APPLICABLE, ""));
            }
            days.add(new MonthlyAttendance.Day(entry.getKey(), periods));
        }
        return new MonthlyAttendance(semester, month, year, days);
    }

    public List<UniversityResult> readResults() throws SQLException {
        List<ResultRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection()
                .prepareStatement("select id, semester, sgpa, cgpa, earned_credits, "
                        + "total_credits, status from university_results order by semester");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(new ResultRow(
                        result.getLong(1),
                        result.getInt(2),
                        formatNumber(result, 3),
                        formatNumber(result, 4),
                        formatNumber(result, 5),
                        formatNumber(result, 6),
                        orEmpty(result.getString(7))));
            }
        }

        // Use a fresh statement per result after the outer result set is closed.
        // Nested prepared statements on one connection can clash on PostgreSQL.
        List<UniversityResult> results = new ArrayList<>();
        for (ResultRow row : rows) {
            List<ResultSubject> subjects = new ArrayList<>();
            try (PreparedStatement statement = connection().prepareStatement(
                    "select subject_code, subject_name, attendance, attendance_pct, "
                            + "earned_credits, total_credits, grade, result "
                            + "from result_subjects where result_id=?")) {
                statement.setLong(1, row.id);
                try (ResultSet subjectRows = statement.executeQuery()) {
                    while (subjectRows.next()) {
                        subjects.add(new ResultSubject(
                                orEmpty(subjectRows.getString(1)),
                                orEmpty(subjectRows.getString(2)),
                                orEmpty(subjectRows.getString(3)),
                                orEmpty(subjectRows.getString(4)),
                                orEmpty(subjectRows.getString(5)),
                                orEmpty(subjectRows.getString(6)),
                                orEmpty(subjectRows.getString(7)),
                                orEmpty(subjectRows.getString(8))));
                    }
                }
            }
            results.add(new UniversityResult(
                    row.semester, row.sgpa, row.cgpa, row.earnedCredits,
                    row.totalCredits, row.status, subjects));
        }
        return results;
    }

    @Override
    public void close() {
        database.close();
    }

    private static final class ResultRow {
        private final long id;
        private final int semester;
        private final String sgpa;
        private final String cgpa;
        private final String earnedCredits;
        private final String totalCredits;
        private final String status;

        private ResultRow(long id, int semester, String sgpa, String cgpa,
                          String earnedCredits, String totalCredits, String status) {
            this.id = id;
            this.semester = semester;
            this.sgpa = sgpa;
            this.cgpa = cgpa;
            this.earnedCredits = earnedCredits;
            this.totalCredits = totalCredits;
            this.status = status;
        }
    }

    private static void putIfAbsent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank() && (map.get(key) == null || map.get(key).isBlank())) {
            map.put(key, value.trim());
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatNumber(ResultSet result, int column) throws SQLException {
        double value = result.getDouble(column);
        return result.wasNull() ? "N/A" : String.format(Locale.ROOT, "%.2f", value);
    }

    private void execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    private long queryLong(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong(1);
                }
            }
        }
        throw new SQLException("Database did not return an id.");
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static Map<String, String> allFields(StudentProfile profile) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (StudentProfile.Section section : profile.getSections()) {
            fields.putAll(section.getFields());
        }
        return fields;
    }

    private static String first(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Date.valueOf(LocalDate.parse(value.trim(), DATE_FORMAT));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static Double number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractCode(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String text = label.trim();
        int separator = text.indexOf(" - ");
        if (separator > 0) {
            return text.substring(0, separator).trim();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([A-Z]{2,}\\d+)").matcher(text.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static Object nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String json(Map<String, String> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        String text = value == null ? "" : value;
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': builder.append("\\\""); break;
                case '\\': builder.append("\\\\"); break;
                case '\n': builder.append("\\n"); break;
                case '\r': builder.append("\\r"); break;
                case '\t': builder.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        return builder.toString();
    }
}
