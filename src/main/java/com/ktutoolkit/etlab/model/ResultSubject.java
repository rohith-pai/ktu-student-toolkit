package com.ktutoolkit.etlab.model;

/**
 * One subject row from the ETLab university result table.
 */
public class ResultSubject {
    private final String subjectCode;
    private final String subjectName;
    private final String attendance;
    private final String attendancePercentage;
    private final String earnedCredits;
    private final String totalCredits;
    private final String grade;
    private final String result;

    public ResultSubject(String subjectCode, String subjectName, String attendance,
                         String attendancePercentage, String earnedCredits, String totalCredits,
                         String grade, String result) {
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.attendance = attendance;
        this.attendancePercentage = attendancePercentage;
        this.earnedCredits = earnedCredits;
        this.totalCredits = totalCredits;
        this.grade = grade;
        this.result = result;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getAttendance() {
        return attendance;
    }

    public String getAttendancePercentage() {
        return attendancePercentage;
    }

    public String getEarnedCredits() {
        return earnedCredits;
    }

    public String getTotalCredits() {
        return totalCredits;
    }

    public String getGrade() {
        return grade;
    }

    public String getResult() {
        return result;
    }
}
