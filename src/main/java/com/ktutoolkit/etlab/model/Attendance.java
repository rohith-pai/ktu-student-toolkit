package com.ktutoolkit.etlab.model;

/**
 * Attendance for one subject, as shown in ETLab's subject-wise summary.
 */
public class Attendance {
    private final String subjectCode;
    private final int presentHours;
    private final int totalHours;
    private final double percentage;

    public Attendance(String subjectCode, int presentHours, int totalHours, double percentage) {
        this.subjectCode = subjectCode;
        this.presentHours = presentHours;
        this.totalHours = totalHours;
        this.percentage = percentage;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public int getPresentHours() {
        return presentHours;
    }

    public int getTotalHours() {
        return totalHours;
    }

    public double getPercentage() {
        return percentage;
    }
}
