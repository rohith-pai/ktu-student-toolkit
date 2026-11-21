package com.ktutoolkit.etlab.model;

/**
 * One row of the ETLab subjects page.
 */
public class SubjectInfo {
    private final String serialNumber;
    private final String code;
    private final String name;
    private final String type;
    private final String teachers;
    private final int presentHours;
    private final int totalHours;
    private final double attendancePercentage;

    public SubjectInfo(String serialNumber, String code, String name, String type, String teachers,
                       int presentHours, int totalHours, double attendancePercentage) {
        this.serialNumber = serialNumber;
        this.code = code;
        this.name = name;
        this.type = type;
        this.teachers = teachers;
        this.presentHours = presentHours;
        this.totalHours = totalHours;
        this.attendancePercentage = attendancePercentage;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getTeachers() {
        return teachers;
    }

    public int getPresentHours() {
        return presentHours;
    }

    public int getTotalHours() {
        return totalHours;
    }

    public double getAttendancePercentage() {
        return attendancePercentage;
    }
}
