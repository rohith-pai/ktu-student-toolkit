package com.ktutoolkit.etlab;

import java.util.List;
import java.util.Locale;

/**
 * SGPA and CGPA calculations using the KTU 2024 B.Tech regulations.
 *
 * <p>The 2024 grade scale (R 7.21) is: S 10, A+ 9, A 8.5, B+ 8, B 7.5,
 * C+ 7, C 6.5, D 6, P 5.5, and F/FE/Ab 0. A grade of P or above earns the
 * course credits. F, FE and Ab still count in the denominator of SGPA and
 * CGPA, with zero grade points (R 9.1).</p>
 */
public final class GradeCalculator {
    private GradeCalculator() {
    }

    /**
     * Returns the 2024-regulation grade point for a letter grade.
     */
    public static double gradePointFor(String grade) {
        if (grade == null || grade.isBlank()) {
            throw new IllegalArgumentException("Grade cannot be empty.");
        }
        switch (grade.trim().toUpperCase(Locale.ROOT)) {
            case "S":
                return 10.0;
            case "A+":
                return 9.0;
            case "A":
                return 8.5;
            case "B+":
                return 8.0;
            case "B":
                return 7.5;
            case "C+":
                return 7.0;
            case "C":
                return 6.5;
            case "D":
                return 6.0;
            case "P":
                return 5.5;
            case "F":
            case "FE":
            case "AB":
                return 0.0;
            default:
                throw new IllegalArgumentException("Unknown grade: " + grade);
        }
    }

    /**
     * Calculates SGPA from the courses registered in one semester.
     * Failed/absent courses are included with zero grade points.
     */
    public static double calculateSGPA(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return 0.0;
        }

        int totalCredits = 0;
        double totalCreditPoints = 0.0;
        for (Course course : courses) {
            totalCredits += course.getCredits();
            totalCreditPoints += course.getCreditPoints();
        }
        return totalCredits == 0 ? 0.0 : totalCreditPoints / totalCredits;
    }

    /**
     * Calculates CGPA from every course registered up to a semester, as
     * defined in R 9.1 of the 2024 regulations.
     */
    public static double calculateCGPA(List<Course> allCourses) {
        if (allCourses == null || allCourses.isEmpty()) {
            return 0.0;
        }

        int totalCredits = 0;
        double totalCreditPoints = 0.0;
        for (Course course : allCourses) {
            totalCredits += course.getCredits();
            totalCreditPoints += course.getCreditPoints();
        }
        return totalCredits == 0 ? 0.0 : totalCreditPoints / totalCredits;
    }

    /**
     * Approximate CGPA when only each semester's (already rounded) SGPA and
     * registered credits are known. Course-level data is more accurate.
     */
    public static double estimateCGPA(List<Semester> semesters) {
        if (semesters == null || semesters.isEmpty()) {
            return 0.0;
        }

        int totalCredits = 0;
        double totalWeightedPoints = 0.0;
        for (Semester semester : semesters) {
            totalCredits += semester.getCredits();
            totalWeightedPoints += semester.getWeightedPoints();
        }
        return totalCredits == 0 ? 0.0 : totalWeightedPoints / totalCredits;
    }

    public static final class Course {
        private final String code;
        private final int credits;
        private final String grade;
        private final double gradePoint;

        public Course(String code, int credits, String grade) {
            if (credits <= 0) {
                throw new IllegalArgumentException("Credits must be greater than zero.");
            }
            this.code = code == null || code.isBlank() ? "Course" : code.trim();
            this.credits = credits;
            this.grade = grade == null ? "" : grade.trim().toUpperCase(Locale.ROOT);
            this.gradePoint = gradePointFor(this.grade);
        }

        public String getCode() {
            return code;
        }

        public int getCredits() {
            return credits;
        }

        public String getGrade() {
            return grade;
        }

        public double getGradePoint() {
            return gradePoint;
        }

        public double getCreditPoints() {
            return credits * gradePoint;
        }
    }

    public static final class Semester {
        private final String label;
        private final int credits;
        private final double sgpa;

        public Semester(String label, int credits, double sgpa) {
            if (credits <= 0) {
                throw new IllegalArgumentException("Semester credits must be greater than zero.");
            }
            if (sgpa < 0.0 || sgpa > 10.0) {
                throw new IllegalArgumentException("SGPA must be between 0 and 10.");
            }
            this.label = label == null || label.isBlank() ? "Semester" : label.trim();
            this.credits = credits;
            this.sgpa = sgpa;
        }

        public String getLabel() {
            return label;
        }

        public int getCredits() {
            return credits;
        }

        public double getSgpa() {
            return sgpa;
        }

        public double getWeightedPoints() {
            return credits * sgpa;
        }
    }
}
