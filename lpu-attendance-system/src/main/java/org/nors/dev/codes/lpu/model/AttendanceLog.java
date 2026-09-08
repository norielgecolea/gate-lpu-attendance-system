package org.nors.dev.codes.lpu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_logs")
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Exactly one of student / employee is set per log. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate = LocalDate.now();

    @Column(name = "time_in", nullable = false)
    private Instant timeIn;

    @Column(name = "time_out")
    private Instant timeOut;

    /** Latest tap action for today: TIME_IN or TIME_OUT (supports multiple cycles). */
    @Column(name = "last_action", nullable = false, length = 20)
    private String lastAction = "TIME_IN";

    @Column(name = "tapped_by_user_id")
    private Long tappedByUserId;

    @Column(name = "time_in_location", length = 100)
    private String timeInLocation;

    @Column(name = "time_out_location", length = 100)
    private String timeOutLocation;

    /** Number of accepted taps recorded for this person on this date. */
    @Column(name = "tap_count", nullable = false)
    private int tapCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "kiosk_group", nullable = false, length = 20)
    private KioskGroup kioskGroup = KioskGroup.MAIN_GATES;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public Instant getTimeIn() {
        return timeIn;
    }

    public void setTimeIn(Instant timeIn) {
        this.timeIn = timeIn;
    }

    public Instant getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(Instant timeOut) {
        this.timeOut = timeOut;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public Long getTappedByUserId() {
        return tappedByUserId;
    }

    public void setTappedByUserId(Long tappedByUserId) {
        this.tappedByUserId = tappedByUserId;
    }

    public String getTimeInLocation() {
        return timeInLocation;
    }

    public void setTimeInLocation(String timeInLocation) {
        this.timeInLocation = timeInLocation;
    }

    public String getTimeOutLocation() {
        return timeOutLocation;
    }

    public void setTimeOutLocation(String timeOutLocation) {
        this.timeOutLocation = timeOutLocation;
    }

    public int getTapCount() {
        return tapCount;
    }

    public void setTapCount(int tapCount) {
        this.tapCount = tapCount;
    }

    public KioskGroup getKioskGroup() {
        return kioskGroup;
    }

    public void setKioskGroup(KioskGroup kioskGroup) {
        this.kioskGroup = kioskGroup;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
