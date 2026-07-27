package com.teenpatti.platform.game.winner;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ShowdownParticipantView {

    String userId;
    String displayName;
    String handRank;
    String handDescription;
    boolean winner;
    List<String> cards;
}
