package org.etf.evoting.model; // odnosno org.etf.evoting.model na bekendu

import java.time.LocalDateTime;
import java.util.List;

public record ElectionDTO(
        Integer id,
        String title,
        String description,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer organizerId,
        String organizerUsername,
        List<ElectionOptionDTO> options // Dodata komponenta
) {}