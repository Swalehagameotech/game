package com.teenpatti.platform.admin.betting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "betting_configuration")
@CompoundIndex(name = "active_unique_idx", def = "{'active':1}", unique = true, partialFilter = "{ 'active': true }")
public class BettingConfiguration {

    @Id
    private String id;

    private boolean active;
    private long bootAmount;
    private List<Long> bootAmountOptions;
    private int minimumPlayers;
    private int maximumPlayers;
    private int turnTimer;

    private long blindBetAmount;
    private List<Long> blindRaiseOptions;

    private long seenChaalAmount;
    private List<Long> seenRaiseOptions;

    private long showCost;
    private long sideShowCost;

    private boolean sideShowEnabled;
    private boolean showEnabled;

    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
