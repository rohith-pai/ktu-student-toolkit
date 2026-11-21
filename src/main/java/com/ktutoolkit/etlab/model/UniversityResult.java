package com.ktutoolkit.etlab.model;

import java.util.Collections;
import java.util.List;

/**
 * Parsed university result for one semester.
 */
public class UniversityResult {
    private final int semester;
    private final String sgpa;
    private final String cgpa;
    private final String earnedCredits;
    private final String totalCredits;
    private final String status;
    private final List<ResultSubject> subjects;

    public UniversityResult(int semester, String sgpa, String cgpa, String earnedCredits,
                            String totalCredits, String status, List<ResultSubject> subjects) {
        this.semester = semester;
        this.sgpa = sgpa;
        this.cgpa = cgpa;
        this.earnedCredits = earnedCredits;
        this.totalCredits = totalCredits;
        this.status = status;
        this.subjects = Collections.unmodifiableList(subjects);
    }

    public int getSemester() {
        return semester;
    }

    public String getSgpa() {
        return sgpa;
    }

    public String getCgpa() {
        return cgpa;
    }

    public String getEarnedCredits() {
        return earnedCredits;
    }

    public String getTotalCredits() {
        return totalCredits;
    }

    public String getStatus() {
        return status;
    }

    public List<ResultSubject> getSubjects() {
        return subjects;
    }
}
