package com.ktutoolkit.etlab.model;

import java.util.Collections;
import java.util.List;

/**
 * HOD, staff advisors and teacher allocations from the ETLab faculty page.
 */
public class FacultyDetails {
    private final List<Contact> headsOfDepartment;
    private final List<Contact> staffAdvisors;
    private final List<TeachingAssignment> teachingAssignments;

    public FacultyDetails(List<Contact> headsOfDepartment, List<Contact> staffAdvisors,
                          List<TeachingAssignment> teachingAssignments) {
        this.headsOfDepartment = Collections.unmodifiableList(headsOfDepartment);
        this.staffAdvisors = Collections.unmodifiableList(staffAdvisors);
        this.teachingAssignments = Collections.unmodifiableList(teachingAssignments);
    }

    public List<Contact> getHeadsOfDepartment() {
        return headsOfDepartment;
    }

    public List<Contact> getStaffAdvisors() {
        return staffAdvisors;
    }

    public List<TeachingAssignment> getTeachingAssignments() {
        return teachingAssignments;
    }

    public static final class Contact {
        private final String name;
        private final String email;
        private final String phone;

        public Contact(String name, String email, String phone) {
            this.name = name;
            this.email = email;
            this.phone = phone;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }
    }

    public static final class TeachingAssignment {
        private final String serialNumber;
        private final String teacher;
        private final String subject;
        private final String email;

        public TeachingAssignment(String serialNumber, String teacher, String subject, String email) {
            this.serialNumber = serialNumber;
            this.teacher = teacher;
            this.subject = subject;
            this.email = email;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public String getTeacher() {
            return teacher;
        }

        public String getSubject() {
            return subject;
        }

        public String getEmail() {
            return email;
        }
    }
}
