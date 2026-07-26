package com.teenpatti.platform.lobby;

import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

class InviteCodeGeneratorTest {

    @Test
    @DisplayName("Generate unique invite code returns valid uppercase alphanumeric string of length 7")
    void generateUniqueInviteCode_ReturnsValidFormat() {
        TableRepository tableRepository = Mockito.mock(TableRepository.class);
        Mockito.when(tableRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());

        InviteCodeGenerator generator = new InviteCodeGenerator(tableRepository);
        String code = generator.generateUniqueInviteCode();

        assertNotNull(code);
        assertEquals(7, code.length());
        assertTrue(code.matches("^[A-Z0-9]{7}$"), "Invite code should be 7 uppercase alphanumeric chars");
    }

    @Test
    @DisplayName("Generate unique invite code retries on database collision")
    void generateUniqueInviteCode_RetriesOnCollision() {
        TableRepository tableRepository = Mockito.mock(TableRepository.class);
        Table dummyTable = Table.builder().id("tab_1").build();

        // First call collides, second call succeeds
        Mockito.when(tableRepository.findByInviteCode(anyString()))
                .thenReturn(Optional.of(dummyTable))
                .thenReturn(Optional.empty());

        InviteCodeGenerator generator = new InviteCodeGenerator(tableRepository);
        String code = generator.generateUniqueInviteCode();

        assertNotNull(code);
        Mockito.verify(tableRepository, Mockito.times(2)).findByInviteCode(anyString());
    }
}
