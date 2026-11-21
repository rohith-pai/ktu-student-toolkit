package com.ktutoolkit.etlab;

import com.ktutoolkit.core.AnsiColors;
import com.ktutoolkit.core.CLITool;
import com.ktutoolkit.core.Input;
import com.ktutoolkit.etlab.model.Attendance;
import com.ktutoolkit.etlab.model.AttendanceSummary;
import com.ktutoolkit.etlab.model.FacultyDetails;
import com.ktutoolkit.etlab.model.MonthlyAttendance;
import com.ktutoolkit.etlab.model.ResultSubject;
import com.ktutoolkit.etlab.model.StudentProfile;
import com.ktutoolkit.etlab.model.SubjectInfo;
import com.ktutoolkit.etlab.model.Timetable;
import com.ktutoolkit.etlab.model.UniversityResult;
import com.ktutoolkit.exceptions.DataNotAvailableException;
import com.ktutoolkit.db.SupabaseCache;
import com.ktutoolkit.db.SupabaseSyncRepository;
import com.ktutoolkit.exceptions.LoginFailedException;
import com.ktutoolkit.settings.AppConfig;
import com.ktutoolkit.settings.SettingsTool;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class EtLabDashboardTool implements CLITool {
    private final AppConfig config;
    private final EtLabApiClient client;

    private boolean loggedIn = false;
    private boolean useSupabase;
    private String username;

    public EtLabDashboardTool() {
        this.config = new AppConfig();
        this.client = new EtLabApiClient();
    }

    @Override
    public void run() {
        Scanner scanner = Input.scanner();

        if (!loggedIn) {
            if (!ensureLoggedIn(scanner)) {
                return;
            }
        }

        useSupabase = SupabaseCache.hasSyncedData();

        while (loggedIn) {
            AnsiColors.clearScreen();
            System.out.println("  " + AnsiColors.bold("ETLAB DASHBOARD"));
            AnsiColors.horizontalRule();
            System.out.println("  " + AnsiColors.bold("LOGGED IN AS:") + " " + AnsiColors.cyan(username));
            System.out.println("  " + AnsiColors.bold("DATA SOURCE:") + " "
                    + (useSupabase
                        ? AnsiColors.green("SUPABASE (SYNCED - RUN SETTINGS -> SYNC TO REFRESH)")
                        : AnsiColors.cyan("ETLAB (LIVE - FETCHED ON EVERY REQUEST)")));
            AnsiColors.horizontalRule();
            AnsiColors.printMenuOption(1, "PROFILE");
            AnsiColors.printMenuOption(2, "TIMETABLE SLOTS");
            AnsiColors.printMenuOption(3, "ATTENDANCE SUMMARY");
            AnsiColors.printMenuOption(4, "PRESENT");
            AnsiColors.printMenuOption(5, "SUBJECTS");
            AnsiColors.printMenuOption(6, "FACULTY DETAILS");
            AnsiColors.printMenuOption(7, "UNIVERSITY RESULT");
            AnsiColors.printMenuOption(8, "SGPA / CGPA CALCULATOR");
            AnsiColors.printMenuOption(9, "SETTINGS (MAIN MENU)");
            AnsiColors.printMenuOption(0, "MAIN MENU");
            AnsiColors.horizontalRule();
            System.out.print("  " + AnsiColors.bold("ENTER CHOICE") + ": ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE ENTER A NUMBER."));
                continue;
            }

            switch (choice) {
                case 1:
                    showProfile(scanner);
                    break;
                case 2:
                    showTimetable(scanner);
                    break;
                case 3:
                    showAttendanceSummary(scanner);
                    break;
                case 4:
                    showMonthlyAttendance(scanner);
                    break;
                case 5:
                    showSubjects(scanner);
                    break;
                case 6:
                    showFacultyDetails(scanner);
                    break;
                case 7:
                    showUniversityResult(scanner);
                    break;
                case 8:
                    openGradeCalculator(scanner);
                    break;
                case 9:
                    openSettings(scanner);
                    break;
                case 0:
                    return;
                default:
                    System.out.println("  " + AnsiColors.red("INVALID CHOICE. PLEASE TRY AGAIN."));
            }
        }
    }

    /**
     * Tries the saved session token first, then the saved credentials, and
     * only asks the user for input when neither works.
     */
    private boolean ensureLoggedIn(Scanner scanner) {
        String savedUsername = config.getUsername();
        String savedPassword = config.getPassword();
        String savedToken = config.getSessionToken();

        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
            client.setCredentials(savedUsername, savedPassword);

            if (!savedToken.isEmpty()) {
                System.out.println("\n  " + AnsiColors.bold("ETLAB") + " "
                        + AnsiColors.yellow("CHECKING SAVED SESSION..."));
                client.setSessionToken(savedToken);
                if (client.isSessionValid()) {
                    username = savedUsername;
                    loggedIn = true;
                    System.out.println("  " + AnsiColors.green("SESSION RESTORED."));
                    pause(scanner);
                    return true;
                }
                System.out.println("  " + AnsiColors.yellow("SAVED SESSION EXPIRED. SIGNING IN AGAIN..."));
                config.clearSessionToken();
            }

            System.out.println();
            AnsiColors.printLoading("  AUTHENTICATING WITH SAVED CREDENTIALS");
            try {
                client.login();
                username = savedUsername;
                loggedIn = true;
                config.saveSession(savedUsername, savedPassword, client.getSessionToken());
                System.out.println("  " + AnsiColors.green("LOGIN SUCCESSFUL."));
                pause(scanner);
                return true;
            } catch (LoginFailedException e) {
                System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
                System.out.println("  " + AnsiColors.yellow("PLEASE ENTER YOUR ETLAB CREDENTIALS AGAIN."));
                pause(scanner);
            } catch (IOException e) {
                System.out.println("  " + AnsiColors.red("COULD NOT REACH ETLAB: " + e.getMessage()));
                pause(scanner);
                return false;
            }
        }

        return promptLogin(scanner, savedUsername);
    }

    private boolean promptLogin(Scanner scanner, String suggestedUsername) {
        System.out.println("\n  " + AnsiColors.bold("ETLAB LOGIN"));
        AnsiColors.horizontalRule();

        String usernameInput;
        if (suggestedUsername != null && !suggestedUsername.isEmpty()) {
            System.out.println("  " + AnsiColors.cyan("USERNAME") + ": " + suggestedUsername);
            System.out.print("  " + AnsiColors.cyan("NEW USERNAME") + " (PRESS ENTER TO KEEP): ");
            String typed = scanner.nextLine().trim();
            usernameInput = typed.isEmpty() ? suggestedUsername : typed;
        } else {
            System.out.print("  " + AnsiColors.cyan("USERNAME") + ": ");
            usernameInput = scanner.nextLine().trim();
        }

        System.out.print("  " + AnsiColors.cyan("PASSWORD") + ": ");
        String password = Input.readPassword();
        System.out.println();

        if (usernameInput.isEmpty() || password.isEmpty()) {
            System.out.println("  " + AnsiColors.red("USERNAME AND PASSWORD CANNOT BE EMPTY."));
            pause(scanner);
            return false;
        }

        AnsiColors.printLoading("  AUTHENTICATING");
        try {
            client.login(usernameInput, password);
            config.saveSession(usernameInput, password, client.getSessionToken());
            username = usernameInput;
            loggedIn = true;
            System.out.println("  " + AnsiColors.green("LOGIN SUCCESSFUL."));
            pause(scanner);
            return true;
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
            pause(scanner);
            return false;
        } catch (IOException e) {
            System.out.println("  " + AnsiColors.red("NETWORK ERROR: " + e.getMessage()));
            pause(scanner);
            return false;
        }
    }

    private void showProfile(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("PROFILE"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                useSupabase ? "READING FROM SUPABASE..." : "FETCHING DATA FROM ETLAB..."));
        AnsiColors.horizontalRule();

        try {
            if (useSupabase) {
                try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
                    printProfile(db.readProfile());
                } catch (Exception e) {
                    fallBackToLive(e);
                    printProfile(EtLabDataParser.parseProfile(client.get("/student/profile")));
                }
            } else {
                printProfile(EtLabDataParser.parseProfile(client.get("/student/profile")));
            }
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (DataNotAvailableException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("ERROR: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void printProfile(StudentProfile profile) {
        for (StudentProfile.Section section : profile.getSections()) {
            System.out.println();
            System.out.println("  " + AnsiColors.bold(
                    section.getTitle().toUpperCase(Locale.ROOT)));
            AnsiColors.horizontalRule();
            for (Map.Entry<String, String> field : section.getFields().entrySet()) {
                String value = field.getValue().isBlank() ? "-" : field.getValue();
                System.out.println("  " + AnsiColors.cyan(field.getKey()) + ": " + value);
            }
        }
    }

    private void showTimetable(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("TIMETABLE SLOTS"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                useSupabase ? "READING FROM SUPABASE..." : "FETCHING DATA FROM ETLAB..."));
        AnsiColors.horizontalRule();

        try {
            if (useSupabase) {
                try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
                    printTimetable(db.readTimetable());
                } catch (Exception e) {
                    fallBackToLive(e);
                    printTimetable(EtLabDataParser.parseTimetable(
                            client.get("/student/timetable")));
                }
            } else {
                printTimetable(EtLabDataParser.parseTimetable(client.get("/student/timetable")));
            }
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (DataNotAvailableException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("ERROR: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void printTimetable(Timetable timetable) {
        for (Timetable.Day day : timetable.getDays()) {
            System.out.println();
            System.out.println("  " + AnsiColors.bold(day.getLabel().toUpperCase(Locale.ROOT)));
            AnsiColors.horizontalRule();
            List<String> periods = day.getPeriods();
            for (int i = 0; i < periods.size(); i++) {
                System.out.printf("  %-3s %s%n", "P" + (i + 1), periods.get(i));
            }
        }
    }

    private void showMonthlyAttendance(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("PRESENT"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                useSupabase ? "READING ATTENDANCE FROM SUPABASE..."
                            : "SELECT THE SEMESTER, MONTH AND YEAR TO VIEW."));
        AnsiColors.horizontalRule();

        try {
            if (useSupabase) {
                try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
                    printMonthlyAttendance(db.readMonthlyAttendance());
                } catch (Exception e) {
                    fallBackToLive(e);
                    printLiveMonthlyAttendance(scanner);
                }
            } else {
                printLiveMonthlyAttendance(scanner);
            }
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (DataNotAvailableException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("ERROR: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void printLiveMonthlyAttendance(Scanner scanner)
            throws LoginFailedException, DataNotAvailableException, IOException {
        int semester = readInt(scanner, "SEMESTER (1-8)", 1, 8);
        int month = readInt(scanner, "MONTH (1-12)", 1, 12);
        int year = readYear(scanner);

        System.out.println();
        System.out.println("  " + AnsiColors.yellow("FETCHING ATTENDANCE FROM ETLAB..."));
        AnsiColors.horizontalRule();

        Map<String, String> form = new LinkedHashMap<>();
        form.put("semester", String.valueOf(semester));
        form.put("month", String.valueOf(month));
        form.put("year", String.valueOf(year));
        String html = client.postForm("/ktuacademics/student/attendance", form);
        printMonthlyAttendance(EtLabDataParser.parseMonthlyAttendance(html, semester, month, year));
    }

    private void printMonthlyAttendance(MonthlyAttendance attendance) {
        String monthName = java.time.Month.of(attendance.getMonth())
                .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);

        System.out.println();
        System.out.println("  " + AnsiColors.bold(monthName.toUpperCase(Locale.ROOT)
                + " " + attendance.getYear())
                + " | " + AnsiColors.bold("SEMESTER " + attendance.getSemester()));
        AnsiColors.horizontalRule();

        System.out.printf("  %-4s", "DAY");
        for (int i = 1; i <= 9; i++) {
            System.out.printf("%3d", i);
        }
        System.out.println();

        for (MonthlyAttendance.Day day : attendance.getDays()) {
            System.out.printf("  %-4s", day.getLabel());
            for (MonthlyAttendance.Cell cell : day.getPeriods()) {
                System.out.printf("%3s", cell.getStatus().getSymbol());
            }
            System.out.println();
        }

        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.bold("LEGEND") + ": "
                + "P = PRESENT, A = ABSENT, H = HOLIDAY, L = LEAVE/ON DUTY, - = NOT APPLICABLE");
        System.out.println();
        System.out.println("  " + AnsiColors.bold("PRESENT") + ": "
                + AnsiColors.green(String.valueOf(attendance.count(MonthlyAttendance.Status.PRESENT)))
                + "   " + AnsiColors.bold("ABSENT") + ": "
                + AnsiColors.red(String.valueOf(attendance.count(MonthlyAttendance.Status.ABSENT)))
                + "   " + AnsiColors.bold("HOLIDAYS") + ": "
                + attendance.count(MonthlyAttendance.Status.HOLIDAY)
                + "   " + AnsiColors.bold("LEAVE") + ": "
                + attendance.count(MonthlyAttendance.Status.LEAVE));
    }

    private void showSubjects(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("SUBJECTS"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                useSupabase ? "READING FROM SUPABASE..." : "FETCHING DATA FROM ETLAB..."));
        AnsiColors.horizontalRule();

        try {
            if (useSupabase) {
                try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
                    printSubjects(db.readSubjects());
                } catch (Exception e) {
                    fallBackToLive(e);
                    printSubjects(EtLabDataParser.parseSubjects(
                            client.get("/ktuacademics/student/subject")));
                }
            } else {
                printSubjects(EtLabDataParser.parseSubjects(
                        client.get("/ktuacademics/student/subject")));
            }
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (DataNotAvailableException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("ERROR: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void printSubjects(List<SubjectInfo> subjects) {
        for (SubjectInfo subject : subjects) {
            System.out.println();
            String prefix = subject.getSerialNumber().isBlank()
                    ? "" : subject.getSerialNumber() + ". ";
            System.out.println("  " + AnsiColors.bold(prefix + subject.getCode())
                    + " - " + subject.getName());
            System.out.println("     " + AnsiColors.cyan("TYPE") + ": " + subject.getType()
                    + "   " + AnsiColors.cyan("TEACHER") + ": " + subject.getTeachers());
            System.out.println("     " + AnsiColors.cyan("ATTENDANCE") + ": "
                    + subject.getPresentHours() + "/" + subject.getTotalHours()
                    + " (" + String.format(Locale.ROOT, "%.0f", subject.getAttendancePercentage())
                    + "%)");
        }
    }

    private void showFacultyDetails(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("FACULTY DETAILS"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                useSupabase ? "READING FROM SUPABASE..." : "FETCHING DATA FROM ETLAB..."));
        AnsiColors.horizontalRule();

        try {
            if (useSupabase) {
                try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
                    printFacultyDetails(db.readFaculty());
                } catch (Exception e) {
                    fallBackToLive(e);
                    printFacultyDetails(EtLabDataParser.parseFacultyDetails(
                            client.get("/ktuacademics/student/teacher")));
                }
            } else {
                printFacultyDetails(EtLabDataParser.parseFacultyDetails(
                        client.get("/ktuacademics/student/teacher")));
            }
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (DataNotAvailableException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("ERROR: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void printFacultyDetails(FacultyDetails details) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("HEAD OF DEPARTMENT"));
        AnsiColors.horizontalRule();
        for (FacultyDetails.Contact contact : details.getHeadsOfDepartment()) {
            System.out.println("  " + contact.getName() + "  " + contact.getEmail());
        }

        System.out.println();
        System.out.println("  " + AnsiColors.bold("STAFF ADVISORS"));
        AnsiColors.horizontalRule();
        for (FacultyDetails.Contact contact : details.getStaffAdvisors()) {
            String phone = contact.getPhone().isBlank() ? "" : "  " + contact.getPhone();
            System.out.println("  " + contact.getName() + "  " + contact.getEmail() + phone);
        }

        System.out.println();
        System.out.println("  " + AnsiColors.bold("TEACHING ASSIGNMENTS"));
        AnsiColors.horizontalRule();
        for (FacultyDetails.TeachingAssignment assignment : details.getTeachingAssignments()) {
            System.out.println("  " + assignment.getSerialNumber() + ". "
                    + AnsiColors.bold(assignment.getTeacher()));
            System.out.println("     " + assignment.getSubject());
            System.out.println("     " + assignment.getEmail());
        }
    }

    /**
     * Settings live only in the main menu. This option opens that same screen
     * and then brings the dashboard's login state back in sync with it.
     */
    private void openSettings(Scanner scanner) {
        new SettingsTool().run();
        config.reload();

        if (!config.hasCredentials() || config.getSessionToken().isBlank()) {
            client.clearSession();
            loggedIn = false;
            username = null;
            System.out.println("\n  " + AnsiColors.yellow(
                    "YOU WERE SIGNED OUT FROM SETTINGS. RETURNING TO THE MAIN MENU."));
            pause(scanner);
            return;
        }

        client.setCredentials(config.getUsername(), config.getPassword());
        client.setSessionToken(config.getSessionToken());
        useSupabase = SupabaseCache.hasSyncedData();
    }

    private void showUniversityResult(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("UNIVERSITY RESULT"));
        AnsiColors.horizontalRule();
        int semester = readInt(scanner, "SEMESTER (1-8)", 1, 8);

        System.out.println();
        System.out.println("  " + AnsiColors.yellow(useSupabase
                ? "READING SEMESTER " + semester + " RESULT FROM SUPABASE..."
                : "FETCHING SEMESTER " + semester + " RESULT FROM ETLAB..."));
        AnsiColors.horizontalRule();

        try {
            if (useSupabase) {
                try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
                    UniversityResult found = null;
                    for (UniversityResult candidate : db.readResults()) {
                        if (candidate.getSemester() == semester) {
                            found = candidate;
                            break;
                        }
                    }
                    if (found == null) {
                        System.out.println("  " + AnsiColors.yellow(
                                "NO CACHED RESULT FOR SEMESTER " + semester + "."));
                    } else {
                        printUniversityResult(found);
                    }
                } catch (Exception e) {
                    fallBackToLive(e);
                    String html = client.get(
                            "/ktuacademics/student/results?sem_position=" + semester);
                    printUniversityResult(EtLabDataParser.parseUniversityResult(html, semester));
                }
            } else {
                String html = client.get(
                        "/ktuacademics/student/results?sem_position=" + semester);
                printUniversityResult(EtLabDataParser.parseUniversityResult(html, semester));
            }
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (DataNotAvailableException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("ERROR: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void printUniversityResult(UniversityResult result) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("SEMESTER") + ": S" + result.getSemester());
        System.out.println("  " + AnsiColors.bold("SGPA") + ": " + result.getSgpa());
        System.out.println("  " + AnsiColors.bold("CGPA") + ": " + result.getCgpa());
        System.out.println("  " + AnsiColors.bold("EARNED CREDITS") + ": "
                + result.getEarnedCredits() + " / " + result.getTotalCredits());
        System.out.println("  " + AnsiColors.bold("STATUS") + ": " + result.getStatus());
        System.out.println();

        if (result.getSubjects().isEmpty()) {
            System.out.println("  " + AnsiColors.yellow("NO SUBJECT RESULTS WERE FOUND."));
            return;
        }

        System.out.printf("  %-12s %-36s %8s %8s %6s%n",
                "CODE", "SUBJECT", "ATT %", "CREDITS", "GRADE");
        AnsiColors.horizontalRule();

        for (ResultSubject subject : result.getSubjects()) {
            String attendance = subject.getAttendancePercentage().isEmpty()
                    ? subject.getAttendance()
                    : subject.getAttendancePercentage();
            String credits = subject.getEarnedCredits().equals("N/A")
                    || subject.getTotalCredits().equals("N/A")
                    ? "N/A"
                    : subject.getEarnedCredits() + "/" + subject.getTotalCredits();
            System.out.printf("  %-12s %-36s %8s %8s %6s%n",
                    subject.getSubjectCode(),
                    fit(subject.getSubjectName(), 36),
                    attendance,
                    credits,
                    subject.getGrade());
        }
    }

    private static String fit(String value, int width) {
        String text = value == null ? "" : value;
        if (text.length() <= width) {
            return text;
        }
        return text.substring(0, Math.max(0, width - 3)) + "...";
    }

    private void showAttendanceSummary(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("ATTENDANCE SUMMARY"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                useSupabase ? "READING FROM SUPABASE..." : "FETCHING DATA FROM ETLAB..."));
        AnsiColors.horizontalRule();

        try {
            if (useSupabase) {
                try (SupabaseSyncRepository db = new SupabaseSyncRepository()) {
                    printAttendanceSummary(db.readAttendanceSummary());
                } catch (Exception e) {
                    fallBackToLive(e);
                    String attendancePage = client.get("/ktuacademics/student/attendance");
                    String subjectReportLink =
                            EtLabDataParser.findLinkByText(attendancePage, "Attendance by subject");
                    String summaryHtml = subjectReportLink == null
                            ? attendancePage
                            : client.get(subjectReportLink);
                    printAttendanceSummary(EtLabDataParser.parseAttendanceSummary(summaryHtml));
                }
            } else {
                String attendancePage = client.get("/ktuacademics/student/attendance");
                String subjectReportLink =
                        EtLabDataParser.findLinkByText(attendancePage, "Attendance by subject");
                String summaryHtml = subjectReportLink == null
                        ? attendancePage
                        : client.get(subjectReportLink);
                printAttendanceSummary(EtLabDataParser.parseAttendanceSummary(summaryHtml));
            }
        } catch (LoginFailedException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (DataNotAvailableException e) {
            System.out.println("  " + AnsiColors.red(e.getMessage().toUpperCase()));
        } catch (Exception e) {
            System.out.println("  " + AnsiColors.red("ERROR: " + e.getMessage()));
        }
        pause(scanner);
    }

    private void printAttendanceSummary(AttendanceSummary summary) {
        System.out.println();
        System.out.println("  " + AnsiColors.bold("NAME") + ": " + summary.getName());
        System.out.println("  " + AnsiColors.bold("ROLL NO") + ": " + summary.getRollNumber());
        System.out.println("  " + AnsiColors.bold("UNIVERSITY REG NO") + ": "
                + summary.getUniversityRegisterNumber());
        System.out.println();

        System.out.printf("  %-14s %10s %10s %12s%n",
                "SUBJECT", "PRESENT", "TOTAL", "PERCENTAGE");
        AnsiColors.horizontalRule();

        for (Attendance attendance : summary.getSubjects()) {
            String percentage = String.format(Locale.ROOT, "%.2f%%", attendance.getPercentage());
            System.out.printf("  %-14s %10d %10d %12s%n",
                    attendance.getSubjectCode(), attendance.getPresentHours(),
                    attendance.getTotalHours(), percentage);
        }

        AnsiColors.horizontalRule();
        String overall = String.format(Locale.ROOT, "%.2f%%", summary.getOverallPercentage());
        System.out.printf("  %-14s %10d %10d %12s%n",
                "TOTAL", summary.getTotalPresentHours(), summary.getTotalHours(), overall);

        if (!summary.getDutyLeave().isEmpty()) {
            System.out.println();
            System.out.println("  " + AnsiColors.bold("DUTY LEAVE") + ": " + summary.getDutyLeave());
        }
        if (!summary.getDutyLeavePercentage().isEmpty()) {
            System.out.println("  " + AnsiColors.bold("DUTY LEAVE PERCENTAGE") + ": "
                    + summary.getDutyLeavePercentage());
        }

        System.out.println();
        String color = summary.getOverallPercentage() >= 75.0
                ? AnsiColors.GREEN
                : summary.getOverallPercentage() >= 60.0 ? AnsiColors.YELLOW : AnsiColors.RED;
        System.out.println("  " + AnsiColors.bold("OVERALL ATTENDANCE") + ": "
                + AnsiColors.colorize(overall, color));
    }

    private void openGradeCalculator(Scanner scanner) {
        while (true) {
            AnsiColors.clearScreen();
            System.out.println("  " + AnsiColors.bold("SGPA / CGPA CALCULATOR"));
            AnsiColors.horizontalRule();
            System.out.println("  " + AnsiColors.yellow(
                    "KTU 2024 GRADE POINTS: S=10, A+=9, A=8.5, B+=8, B=7.5,"));
            System.out.println("  " + AnsiColors.yellow(
                    "C+=7, C=6.5, D=6, P=5.5, F/FE/Ab=0"));
            System.out.println("  " + AnsiColors.cyan(
                    "P OR ABOVE EARNS CREDITS; F/FE/Ab STILL COUNT AS ZERO POINTS."));
            AnsiColors.horizontalRule();
            AnsiColors.printMenuOption(1, "CALCULATE SGPA");
            AnsiColors.printMenuOption(2, "CALCULATE CGPA");
            AnsiColors.printMenuOption(0, "BACK TO ETLAB DASHBOARD");
            AnsiColors.horizontalRule();
            System.out.print("  " + AnsiColors.bold("ENTER CHOICE") + ": ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE ENTER A NUMBER."));
                pause(scanner);
                continue;
            }

            switch (choice) {
                case 1:
                    calculateSgpa(scanner);
                    break;
                case 2:
                    calculateCgpa(scanner);
                    break;
                case 0:
                    return;
                default:
                    System.out.println("  " + AnsiColors.red("INVALID CHOICE. PLEASE TRY AGAIN."));
                    pause(scanner);
            }
        }
    }

    private void calculateSgpa(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("SGPA CALCULATOR"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.bold("SGPA") + " = SUM(CREDITS x GRADE POINT) / SUM(CREDITS)");
        AnsiColors.horizontalRule();

        int courseCount = readInt(scanner, "NUMBER OF COURSES", 1, 30);
        List<GradeCalculator.Course> courses = new ArrayList<>();

        for (int i = 1; i <= courseCount; i++) {
            System.out.println("\n  " + AnsiColors.bold("COURSE " + i));
            System.out.print("  " + AnsiColors.cyan("COURSE CODE (OPTIONAL)") + ": ");
            String code = scanner.nextLine().trim();
            int credits = readInt(scanner, "CREDITS", 1, 30);
            String grade = readGrade(scanner);
            courses.add(new GradeCalculator.Course(
                    code.isEmpty() ? "Course " + i : code, credits, grade));
        }

        double sgpa = GradeCalculator.calculateSGPA(courses);

        System.out.println();
        System.out.println("  " + AnsiColors.bold("RESULT"));
        AnsiColors.horizontalRule();
        System.out.printf("  %-16s %8s %8s %12s %14s%n",
                "COURSE", "CREDITS", "GRADE", "GRADE POINT", "CREDIT POINTS");
        AnsiColors.horizontalRule();

        int totalCredits = 0;
        double totalCreditPoints = 0.0;
        for (GradeCalculator.Course course : courses) {
            totalCredits += course.getCredits();
            totalCreditPoints += course.getCreditPoints();
            System.out.printf("  %-16s %8d %8s %12.1f %14.2f%n",
                    course.getCode(), course.getCredits(), course.getGrade(),
                    course.getGradePoint(), course.getCreditPoints());
        }

        AnsiColors.horizontalRule();
        System.out.printf("  %-16s %8d %8s %12s %14.2f%n",
                "TOTAL", totalCredits, "", "", totalCreditPoints);
        System.out.println();
        System.out.println("  " + AnsiColors.bold("SGPA") + ": "
                + AnsiColors.green(String.format(Locale.ROOT, "%.2f", sgpa)));
        pause(scanner);
    }

    private void calculateCgpa(Scanner scanner) {
        while (true) {
            AnsiColors.clearScreen();
            System.out.println("  " + AnsiColors.bold("CGPA CALCULATOR"));
            AnsiColors.horizontalRule();
            System.out.println("  " + AnsiColors.bold("CGPA")
                    + " = SUM(CREDITS x GRADE POINT) / SUM(CREDITS)");
            System.out.println("  " + AnsiColors.cyan(
                    "ALL REGISTERED COURSES ARE INCLUDED, INCLUDING FAILED ONES (ZERO POINTS)."));
            AnsiColors.horizontalRule();
            AnsiColors.printMenuOption(1, "FROM COURSES (EXACT)");
            AnsiColors.printMenuOption(2, "FROM SEMESTER SGPAS (APPROXIMATE)");
            AnsiColors.printMenuOption(0, "BACK TO GRADE CALCULATOR");
            AnsiColors.horizontalRule();
            System.out.print("  " + AnsiColors.bold("ENTER CHOICE") + ": ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("  " + AnsiColors.red("INVALID INPUT. PLEASE ENTER A NUMBER."));
                pause(scanner);
                continue;
            }

            switch (choice) {
                case 1:
                    calculateCgpaFromCourses(scanner);
                    return;
                case 2:
                    calculateCgpaFromSgpas(scanner);
                    return;
                case 0:
                    return;
                default:
                    System.out.println("  " + AnsiColors.red("INVALID CHOICE. PLEASE TRY AGAIN."));
                    pause(scanner);
            }
        }
    }

    private void calculateCgpaFromCourses(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("CGPA FROM COURSES (EXACT)"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                "ENTER EVERY COURSE REGISTERED SO FAR (INCLUDING FAILED COURSES)."));
        AnsiColors.horizontalRule();

        int courseCount = readInt(scanner, "TOTAL NUMBER OF COURSES", 1, 200);
        List<GradeCalculator.Course> courses = new ArrayList<>();
        for (int i = 1; i <= courseCount; i++) {
            System.out.println("\n  " + AnsiColors.bold("COURSE " + i));
            System.out.print("  " + AnsiColors.cyan("COURSE CODE (OPTIONAL)") + ": ");
            String code = scanner.nextLine().trim();
            int credits = readInt(scanner, "CREDITS", 1, 30);
            String grade = readGrade(scanner);
            courses.add(new GradeCalculator.Course(
                    code.isEmpty() ? "Course " + i : code, credits, grade));
        }

        double cgpa = GradeCalculator.calculateCGPA(courses);
        int totalCredits = 0;
        double totalCreditPoints = 0.0;
        for (GradeCalculator.Course course : courses) {
            totalCredits += course.getCredits();
            totalCreditPoints += course.getCreditPoints();
        }

        System.out.println();
        System.out.println("  " + AnsiColors.bold("RESULT"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.bold("TOTAL REGISTERED CREDITS") + ": " + totalCredits);
        System.out.println("  " + AnsiColors.bold("TOTAL CREDIT POINTS") + ": "
                + String.format(Locale.ROOT, "%.2f", totalCreditPoints));
        System.out.println();
        System.out.println("  " + AnsiColors.bold("CGPA") + ": "
                + AnsiColors.green(String.format(Locale.ROOT, "%.2f", cgpa)));
        pause(scanner);
    }

    private void calculateCgpaFromSgpas(Scanner scanner) {
        AnsiColors.clearScreen();
        System.out.println("  " + AnsiColors.bold("CGPA FROM SEMESTER SGPAS"));
        AnsiColors.horizontalRule();
        System.out.println("  " + AnsiColors.yellow(
                "APPROXIMATE: SEMESTER SGPAS ARE ALREADY ROUNDED TO 2 DECIMALS."));
        AnsiColors.horizontalRule();

        int semesterCount = readInt(scanner, "NUMBER OF SEMESTERS", 1, 12);
        List<GradeCalculator.Semester> semesters = new ArrayList<>();

        for (int i = 1; i <= semesterCount; i++) {
            System.out.println("\n  " + AnsiColors.bold("SEMESTER " + i));
            System.out.print("  " + AnsiColors.cyan("SEMESTER NAME (OPTIONAL)") + ": ");
            String label = scanner.nextLine().trim();
            int credits = readInt(scanner, "CREDITS (REGISTERED)", 1, 60);
            double sgpa = readDouble(scanner, "SGPA", 0.0, 10.0);
            semesters.add(new GradeCalculator.Semester(
                    label.isEmpty() ? "Semester " + i : label, credits, sgpa));
        }

        double cgpa = GradeCalculator.estimateCGPA(semesters);

        System.out.println();
        System.out.println("  " + AnsiColors.bold("RESULT"));
        AnsiColors.horizontalRule();
        System.out.printf("  %-16s %8s %8s %14s%n",
                "SEMESTER", "CREDITS", "SGPA", "WEIGHTED POINTS");
        AnsiColors.horizontalRule();

        int totalCredits = 0;
        double totalWeightedPoints = 0.0;
        for (GradeCalculator.Semester semester : semesters) {
            totalCredits += semester.getCredits();
            totalWeightedPoints += semester.getWeightedPoints();
            System.out.printf("  %-16s %8d %8s %14s%n",
                    semester.getLabel(), semester.getCredits(),
                    String.format(Locale.ROOT, "%.2f", semester.getSgpa()),
                    String.format(Locale.ROOT, "%.2f", semester.getWeightedPoints()));
        }

        AnsiColors.horizontalRule();
        System.out.printf("  %-16s %8d %8s %14s%n",
                "TOTAL", totalCredits, "",
                String.format(Locale.ROOT, "%.2f", totalWeightedPoints));
        System.out.println();
        System.out.println("  " + AnsiColors.bold("APPROXIMATE CGPA") + ": "
                + AnsiColors.green(String.format(Locale.ROOT, "%.2f", cgpa)));
        pause(scanner);
    }

    private int readInt(Scanner scanner, String label, int min, int max) {
        while (true) {
            System.out.print("  " + AnsiColors.cyan(label) + ": ");
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Prompt again below.
            }
            System.out.println("  " + AnsiColors.red(
                    "ENTER A NUMBER BETWEEN " + min + " AND " + max + "."));
        }
    }

    private int readYear(Scanner scanner) {
        int currentYear = LocalDate.now().getYear();
        while (true) {
            System.out.print("  " + AnsiColors.cyan("YEAR") + " [" + currentYear + "]: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return currentYear;
            }
            try {
                int value = Integer.parseInt(input);
                if (value >= 2000 && value <= 2100) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Prompt again below.
            }
            System.out.println("  " + AnsiColors.red("ENTER A VALID YEAR."));
        }
    }

    private double readDouble(Scanner scanner, String label, double min, double max) {
        while (true) {
            System.out.print("  " + AnsiColors.cyan(label) + ": ");
            try {
                double value = Double.parseDouble(scanner.nextLine().trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Prompt again below.
            }
            System.out.println("  " + AnsiColors.red(
                    "ENTER A NUMBER BETWEEN " + min + " AND " + max + "."));
        }
    }

    private String readGrade(Scanner scanner) {
        while (true) {
            System.out.print("  " + AnsiColors.cyan("GRADE (S/A+/A/B+/B/C+/C/D/P/F/FE/Ab)") + ": ");
            String grade = scanner.nextLine().trim().toUpperCase(Locale.ROOT);
            try {
                GradeCalculator.gradePointFor(grade);
                return grade;
            } catch (IllegalArgumentException e) {
                System.out.println("  " + AnsiColors.red(
                        "INVALID GRADE. USE S, A+, A, B+, B, C+, C, D, P, F, FE OR Ab."));
            }
        }
    }

    /**
     * Any Supabase read failure disables cache mode for the rest of this run
     * so the remaining screens still work from live ETLab data.
     */
    private void fallBackToLive(Exception e) {
        useSupabase = false;
        System.out.println();
        System.out.println("  " + AnsiColors.yellow(
                "SUPABASE READ FAILED: " + e.getMessage()));
        System.out.println("  " + AnsiColors.yellow(
                "FALLING BACK TO LIVE ETLAB FOR THIS RUN."));
    }

    private void pause(Scanner scanner) {
        System.out.println("\n  " + AnsiColors.bold("PRESS ENTER TO CONTINUE..."));
        scanner.nextLine();
    }
}
