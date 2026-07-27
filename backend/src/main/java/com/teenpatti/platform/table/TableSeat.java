package com.teenpatti.platform.table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Embedded seat assignment on a {@link Table} document.
 * One seat index maps to at most one user for the lifetime of a seated session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSeat {

    private int seatIndex;

    private String userId;

    private Instant joinedAt;
}
