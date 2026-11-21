package com.ktutoolkit.etlab.model;

import java.util.Collections;
import java.util.List;

/**
 * Overall attendance summary returned by ETLab's subject-wise report.
 */
public class AttendanceSummary {
    private final String universityRegisterNumber;
    private final String rollNumber;
    private final String name;
    private final List<Attendance> subjects;
    private final int totalPresentHours;
    private final int totalHours;
    private final double overallPercentage;
    private final String dutyLeave;
    private final String dutyLeavePercentage;

    public AttendanceSummary(String universityRegisterNumber, String rollNumber, String name,
                             List<Attendance> subjects, int totalPresentHours, int totalHours,
                             double overallPercentage, String dutyLeave, String dutyLeavePercentage) {
        this.universityRegisterNumber = universityRegisterNumber;
        this.rollNumber = rollNumber;
        this.name = name;
        this.subjects = Collections.unmodifiableList(subjects);
        this.totalPresentHours = totalPresentHours;
        this.totalHours = totalHours;
        this.overallPercentage = overallPercentage;
        this.dutyLeave = dutyLeave;
        this.dutyLeavePercentage = dutyLeavePercentage;
    }

    public String getUniversityRegisterNumber() {
        return universityRegisterNumber;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public String getName() {
        return name;
    }

    public List<Attendance> getSubjects() {
        return subjects;
    }

    public int getTotalPresentHours() {
        return totalPresentHours;
    }

    public int getTotalHours() {
        return totalHours;
    }

    public double getOverallPercentage() {
        return overallPercentage;
    }

    public String getDutyLeave() {
        return dutyLeave;
    }

    public String getDutyLeavePercentage() {
        return dutyLeavePercentage;
    }
}
