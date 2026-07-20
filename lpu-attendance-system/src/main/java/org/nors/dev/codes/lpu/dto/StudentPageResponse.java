package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record StudentPageResponse(
        List<StudentResponse> items,
        long total
) {
}
