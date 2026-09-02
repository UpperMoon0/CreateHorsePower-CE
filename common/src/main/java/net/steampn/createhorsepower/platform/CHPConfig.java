package net.steampn.createhorsepower.platform;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.path.PathEvaluationMode;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;

/**
 * Loader-agnostic view over config, data lookups and script hooks.
 * Each platform installs its implementation during mod construction.
 */
public interface CHPConfig {
    int baseCreatureRpm();

    int smallCreatureStress();

    int mediumCreatureStress();

    int largeCreatureStress();

    double poorMultiplier();

    double normalMultiplier();

    double greatMultiplier();

    List<? extends String> poorPath();

    List<? extends String> normalPath();

    List<? extends String> greatPath();

    List<? extends String> smallCreatures();

    List<? extends String> mediumCreatures();

    List<? extends String> largeCreatures();

    double globalRpmMultiplier();

    double globalStressMultiplier();

    boolean enableIndividualAnimalStats();

    double minSpeedScalingClamp();

    double maxSpeedScalingClamp();

    double minHealthScalingClamp();

    double maxHealthScalingClamp();

    boolean allowBabies();

    boolean requireTamedHorse();

    boolean allowUndeadWorkers();

    double workerGroundSpeedScale();

    double minWorkerGroundSpeed();

    double maxWorkerGroundSpeed();

    boolean debugLogging();

    PathEvaluationMode pathEvaluationMode();

    double minimumPathCoverage();

    int checkIntervalTicks();

    RedstoneMode defaultRedstoneMode();

    Optional<WorkerStats> lookupWorkerStats(EntityType<?> type);

    Optional<PathStats> lookupPathStats(Block block);
}
