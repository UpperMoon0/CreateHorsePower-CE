package net.steampn.createhorsepower.test;

import net.steampn.createhorsepower.content.crank.RedstoneMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RedstoneModeTest {

    @Test
    @DisplayName("RedstoneMode cycles in expected sequence: HIGH_STOPS -> HIGH_RUNS -> IGNORE -> HIGH_STOPS")
    void testRedstoneModeCycling() {
        RedstoneMode mode = RedstoneMode.HIGH_STOPS;
        mode = mode.next();
        assertEquals(RedstoneMode.HIGH_RUNS, mode);

        mode = mode.next();
        assertEquals(RedstoneMode.IGNORE, mode);

        mode = mode.next();
        assertEquals(RedstoneMode.HIGH_STOPS, mode);
    }
}
