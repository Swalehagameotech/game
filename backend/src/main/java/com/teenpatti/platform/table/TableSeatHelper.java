package com.teenpatti.platform.table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for maintaining {@link Table#getSeatMap()} in sync with {@link Table#getSeatedPlayerIds()}.
 */
public final class TableSeatHelper {

    private TableSeatHelper() {
    }

    public static List<TableSeat> buildSeatMap(List<String> seatedPlayerIds) {
        List<TableSeat> seats = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < seatedPlayerIds.size(); i++) {
            seats.add(TableSeat.builder()
                    .seatIndex(i)
                    .userId(seatedPlayerIds.get(i))
                    .joinedAt(now)
                    .build());
        }
        return seats;
    }

    public static void assignSeat(Table table, String userId) {
        if (table.getSeatedPlayerIds() == null) {
            table.setSeatedPlayerIds(new ArrayList<>());
        }
        if (table.getSeatMap() == null) {
            table.setSeatMap(new ArrayList<>());
        }
        if (table.getSeatedPlayerIds().contains(userId)) {
            return;
        }
        int seatIndex = table.getSeatedPlayerIds().size();
        table.getSeatedPlayerIds().add(userId);
        table.getSeatMap().add(TableSeat.builder()
                .seatIndex(seatIndex)
                .userId(userId)
                .joinedAt(Instant.now())
                .build());
    }

    public static void removeSeat(Table table, String userId) {
        if (table.getSeatedPlayerIds() != null) {
            table.getSeatedPlayerIds().remove(userId);
        }
        if (table.getSeatMap() != null) {
            table.getSeatMap().removeIf(seat -> userId.equals(seat.getUserId()));
            reindexSeats(table.getSeatMap());
        }
    }

    private static void reindexSeats(List<TableSeat> seats) {
        for (int i = 0; i < seats.size(); i++) {
            seats.get(i).setSeatIndex(i);
        }
    }
}
