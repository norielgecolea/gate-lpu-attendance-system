package org.nors.dev.codes.lpu.dto;

public record AttendanceHourCountResponse(
        int hour,
        long timeIn,
        long timeOut
) {}
