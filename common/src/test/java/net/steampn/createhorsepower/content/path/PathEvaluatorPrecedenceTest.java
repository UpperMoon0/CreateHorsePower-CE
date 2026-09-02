package net.steampn.createhorsepower.content.path;

import net.steampn.createhorsepower.content.stats.PathStats;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathEvaluatorPrecedenceTest {

    private static final PathStats PLATFORM = new PathStats(1.4f, 1.2f);
    private static final PathStats BUILTIN = new PathStats(1.1f, 1.0f);
    private static final PathStats LEGACY = new PathStats(0.5f, 0.9f);

    @Test
    void platformProfileBeatsBuiltinAndLegacyFallbacks() {
        assertEquals(
                Optional.of(PLATFORM),
                PathEvaluator.resolveFallbackPathStats(
                        Optional.of(PLATFORM),
                        Optional.of(BUILTIN),
                        Optional.of(LEGACY)
                )
        );
    }

    @Test
    void builtinProfileBeatsLegacyConfigFallback() {
        assertEquals(
                Optional.of(BUILTIN),
                PathEvaluator.resolveFallbackPathStats(
                        Optional.empty(),
                        Optional.of(BUILTIN),
                        Optional.of(LEGACY)
                )
        );
    }

    @Test
    void legacyConfigRemainsFallbackForOtherwiseUnresolvedBlocks() {
        assertEquals(
                Optional.of(LEGACY),
                PathEvaluator.resolveFallbackPathStats(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(LEGACY)
                )
        );
    }
}
