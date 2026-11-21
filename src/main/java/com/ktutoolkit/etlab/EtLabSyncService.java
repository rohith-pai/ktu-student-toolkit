package com.ktutoolkit.etlab;

import com.ktutoolkit.db.DatabaseConnection;
import com.ktutoolkit.db.SupabaseSyncRepository;
import com.ktutoolkit.etlab.model.AttendanceSummary;
import com.ktutoolkit.etlab.model.FacultyDetails;
import com.ktutoolkit.etlab.model.MarksRecord;
import com.ktutoolkit.etlab.model.MonthlyAttendance;
import com.ktutoolkit.etlab.model.StudentProfile;
import com.ktutoolkit.etlab.model.SubjectInfo;
import com.ktutoolkit.etlab.model.Timetable;
import com.ktutoolkit.etlab.model.UniversityResult;
import com.ktutoolkit.exceptions.DataNotAvailableException;
import com.ktutoolkit.settings.AppConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches the current ETLab data and upserts it into Supabase over JDBC.
 */
public class EtLabSyncService {
    private final AppConfig config = new AppConfig();
    private final EtLabApiClient client = new EtLabApiClient();

    /**
     * Runs the sync. Throws on authentication, network or database errors so
     * the caller can show an appropriate message.
     */
    public void sync() throws Exception {
        if (!DatabaseConnection.isConfigured()) {
            throw new IllegalStateException(
                    "Supabase JDBC is not configured. The rest of the toolkit works without it.");
        }
        if (!config.hasCredentials()) {
            throw new IllegalStateException(
                    "No saved ETLab credentials. Open ETLab Dashboard and sign in first.");
        }

        client.setCredentials(config.getUsername(), config.getPassword());
        String token = config.getSessionToken();
        if (!token.isBlank()) {
            client.setSessionToken(token);
        }
        if (!client.isSessionValid()) {
            client.login();
            config.saveSessionToken(client.getSessionToken());
        }

        step(1, 7, "Fetching profile");
        StudentProfile profile = EtLabDataParser.parseProfile(client.get("/student/profile"));

        step(2, 7, "Fetching timetable");
        Timetable timetable = EtLabDataParser.parseTimetable(client.get("/student/timetable"));

        step(3, 7, "Fetching subjects and faculty");
        List<SubjectInfo> subjects = EtLabDataParser.parseSubjects(
                client.get("/ktuacademics/student/subject"));
        FacultyDetails faculty = EtLabDataParser.parseFacultyDetails(
                client.get("/ktuacademics/student/teacher"));

        step(4, 7, "Fetching attendance");
        String attendancePage = client.get("/ktuacademics/student/attendance");
        String reportLink = EtLabDataParser.findLinkByText(attendancePage, "Attendance by subject");
        String summaryHtml = reportLink == null ? attendancePage : client.get(reportLink);
        AttendanceSummary attendanceSummary = EtLabDataParser.parseAttendanceSummary(summaryHtml);

        int[] period = EtLabDataParser.latestAttendancePeriod(attendancePage);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("semester", String.valueOf(period[0]));
        form.put("month", String.valueOf(period[1]));
        form.put("year", String.valueOf(period[2]));
        MonthlyAttendance monthly = EtLabDataParser.parseMonthlyAttendance(
                client.postForm("/ktuacademics/student/attendance", form),
                period[0], period[1], period[2]);

        step(5, 7, "Fetching university results and marks");
        List<UniversityResult> results = new ArrayList<>();
        List<MarksRecord> marks = new ArrayList<>();
        for (int semester = 1; semester <= 8; semester++) {
            try {
                String html = client.get("/ktuacademics/student/results?sem_position=" + semester);
                results.add(EtLabDataParser.parseUniversityResult(html, semester));
                marks.addAll(EtLabDataParser.parseInternalMarks(html, semester));
            } catch (DataNotAvailableException e) {
                // Not every semester has published results; skip it.
            }
        }

        step(6, 7, "Writing to Supabase");
        String regNo = attendanceSummary.getUniversityRegisterNumber();
        if (regNo.isBlank()) {
            regNo = config.getUsername();
        }

        try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
            db.connection(); // Fail early with a clear JDBC error.

            long studentId = db.upsertStudent(regNo, config.getUsername(), profile);
            db.replaceGuardians(studentId, profile);
            db.replaceBankAccounts(studentId, profile);
            db.upsertSubjects(subjects);

            Map<String, Long> teacherIds = db.upsertTeachers(faculty);
            db.replaceTimetable(studentId, timetable, teacherIds);
            db.upsertAttendanceSummary(studentId, attendanceSummary);
            db.replaceMonthAttendance(studentId, monthly);
            db.replaceMarks(studentId, marks);
            for (UniversityResult result : results) {
                db.upsertResult(studentId, result);
            }

            step(7, 7, "Recording sync log");
            db.log(studentId, "etlab.profile", "OK");
            db.log(studentId, "etlab.timetable", "OK");
            db.log(studentId, "etlab.subjects", "OK");
            db.log(studentId, "etlab.teachers", "OK");
            db.log(studentId, "etlab.attendance", "OK");
            db.log(studentId, "etlab.results", "OK");
        }
    }

    private static void step(int current, int total, String message) {
        System.out.println("  [" + current + "/" + total + "] " + message + "...");
    }
}
