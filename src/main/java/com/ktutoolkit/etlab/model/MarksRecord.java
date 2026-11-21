package com.ktutoolkit.etlab.model;

/**
 * One internal-marks row from an ETLab result page.
 */
public class MarksRecord {
    private final int semester;
    private final String subjectCode;
    private final String subject;
    private final String exam;
    private final double maxMarks;
    private final double marksObtained;

    public MarksRecord(int semester, String subjectCode, String subject, String exam,
                       double maxMarks, double marksObtained) {
        this.semester = semester;
        this.subjectCode = subjectCode;
        this.subject = subject;
        this.exam = exam;
        this.maxMarks = maxMarks;
        this.marksObtained = marksObtained;
    }

    public int getSemester() {
        return semester;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getSubject() {
        return subject;
    }

    public String getExam() {
        return exam;
    }

    public double getMaxMarks() {
        return maxMarks;
    }

    public double getMarksObtained() {
        return marksObtained;
    }
}
