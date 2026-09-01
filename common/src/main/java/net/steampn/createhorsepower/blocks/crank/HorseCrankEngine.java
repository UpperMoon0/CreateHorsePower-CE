package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.utils.CHPUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Loader-neutral crank behaviour. All state and gameplay logic lives here;
 * the platform block entities delegate lifecycle + Create wiring to this class.
 */
public class HorseCrankEngine {
    /** Bridges the engine to the hosting block entity / world. */
    public interface Host {
        @Nullable Level level();

        BlockPos pos();

        BlockState blockState();

        boolean hasWorkerProperty();

        void setBlockState(BlockState state);

        float theoreticalSpeed();

        void refreshKinetic();

        void syncToClient();

        void clearKineticInfo();

        void requestSpeedUpdate();

        void setLastCapacityProvided(float capacity);
    }

    public static final float DEFAULT_RADIUS = 2.5f;
    public static final int MISSING_ATTACHMENT_GRACE_TICKS = 10;
    public static final int MISSING_WORKER_GRACE_TICKS = 80;

    private final Host host;

    public boolean hasValidWorkingBlocks = false;
    private float rpmModifier = 1.0f;
    private float pathStressModifier = 1.0f;
    private float generationDirection = 1.0f;
    private boolean needsLegacyDirectionResolution = false;
    private boolean suppressGeneration = false;
    private boolean resolvingLegacyDirection = false;
    private boolean workerResolved = false;
    private boolean workerEligible = false;
    private boolean isWorking = false;
    private boolean scriptVetoed = false;

    private float effectiveBaseRpm = 4.0f;
    private float effectiveBaseStress = 256.0f;
    private float workerRadius = DEFAULT_RADIUS;
    private BlockPos[] cachedOffsets;
    private float cachedOffsetsRadius = Float.NaN;
    private float speedBonusPercent = 0.0f;
    private float healthBonusPercent = 0.0f;
    private int efficiencyPercent = 100;
    private int invalidBlockCount = 0;
    private String cachedWorkerName = "";

    private RedstoneMode redstoneMode;
    private boolean lastRedstoneState = false;

    @Nullable
    private Mob cachedWorkerMob;
    @Nullable
    private UUID workerUuid;
    @Nullable
    private BlockPos lastKnownWorkerPos;
    private int missingWorkerTicks = 0;
    private long lastPathCheckTick = -1;
    private int statRefreshTimer = 0;
    private long nextWorkStartRetryTick = 0;
    private long nextFallbackWorkerSearchTick = 0;
    private double workerOrbitAngle = Double.NaN;
    private boolean ownsWorkerAiSuppression = false;
    @Nullable
    private UUID aiSuppressedWorkerUuid;

    /**
     * Real, random, persistent identifier for this crank instance. Two
     * cranks at the same coordinates in succession get distinct UUIDs, and
     * saving/reloading the world preserves the same UUID for the same crank.
     * Used to scope worker AI suppression markers.
     */
    private UUID crankInstanceUuid = UUID.randomUUID();

    public HorseCrankEngine(Host host, RedstoneMode defaultMode) {
        this.host = host;
        this.redstoneMode = defaultMode;
    }

    public static BlockPos[] generateOffsetsForRadius(float radius) {
        List<BlockPos> offsets = new java.util.ArrayList<>();
        int rInt = (int) Math.ceil(radius + 1.0f);
        double minSq = Math.max(0.5, (radius - 0.75) * (radius - 0.75));
        double maxSq = (radius + 0.75) * (radius + 0.75);
        for (int z = -rInt; z <= rInt; z++) {
            for (int x = -rInt; x <= rInt; x++) {
                double distSq = x * x + z * z;
                if (distSq >= minSq && distSq <= maxSq) {
                    offsets.add(new BlockPos(x, -1, z));
                }
            }
        }
        return offsets.toArray(new BlockPos[0]);
    }

    @Nullable
    private Level level() {
        return host.level();
    }

    public boolean isStoppedByRedstone() {
        Level level = level();
        if (level == null) return false;
        boolean signal = level.hasNeighborSignal(host.pos());
        return switch (redstoneMode) {
            case HIGH_STOPS -> signal;
            case HIGH_RUNS -> !signal;
            case IGNORE -> false;
        };
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        this.redstoneMode = mode;
        Level level = level();
        if (level != null && !level.isClientSide()) {
            host.refreshKinetic();
            host.syncToClient();
        }
    }

    public RedstoneMode cycleRedstoneMode() {
        RedstoneMode next = redstoneMode.next();
        setRedstoneMode(next);
        return next;
    }

    public boolean isWorkerResolved() {
        return workerResolved;
    }

    public boolean isWorkerEligible() {
        return workerEligible;
    }

    public boolean isScriptVetoed() {
        return scriptVetoed;
    }

    public boolean isWorking() {
        return isWorking;
    }

    public boolean canPhysicallyWork() {
        return host.hasWorkerProperty()
                && workerResolved
                && workerEligible
                && hasValidWorkingBlocks
                && !isStoppedByRedstone()
                && !suppressGeneration
                && (effectiveBaseRpm * rpmModifier > 0);
    }

    public float generatedSpeed() {
        if (!isWorking || !canPhysicallyWork()) {
            return 0.0F;
        }
        return effectiveBaseRpm * rpmModifier * generationDirection;
    }

    public float addedStressCapacity() {
        if (!isWorking || !canPhysicallyWork()) return 0;

        float speed = generatedSpeed();
        if (speed == 0) return 0;

        float capacity = effectiveBaseStress * pathStressModifier;
        capacity = Math.abs(capacity / Math.abs(speed));
        host.setLastCapacityProvided(capacity);
        return capacity;
    }

    /** Appends CE status lines to the goggle tooltip. */
    public boolean buildGoggleTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.header").withStyle(ChatFormatting.GOLD));

        if (!host.hasWorkerProperty()) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.no_worker").withStyle(ChatFormatting.GRAY));
            return true;
        }

        if (isStoppedByRedstone()) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.stopped_redstone").withStyle(ChatFormatting.RED));
        } else if (!workerResolved) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.worker_unloaded").withStyle(ChatFormatting.YELLOW));
        } else if (!workerEligible) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.worker_ineligible").withStyle(ChatFormatting.RED));
        } else if (!hasValidWorkingBlocks) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.invalid_path", invalidBlockCount).withStyle(ChatFormatting.RED));
        } else if (scriptVetoed) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.vetoed").withStyle(ChatFormatting.YELLOW));
        } else if (isWorking) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.working").withStyle(ChatFormatting.GREEN));
        }

        if (!cachedWorkerName.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.worker", cachedWorkerName).withStyle(ChatFormatting.WHITE));
        }

        tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.path_efficiency", efficiencyPercent + "%").withStyle(ChatFormatting.GRAY));

        if (speedBonusPercent != 0) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.speed_bonus", String.format("%+.1f%%", speedBonusPercent)).withStyle(ChatFormatting.AQUA));
        }
        if (healthBonusPercent != 0) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.health_bonus", String.format("%+.1f%%", healthBonusPercent)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.redstone_mode", redstoneMode.getDisplayName()).withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    public double renderBoundingBoxInflate() {
        return Math.max(4.0D, workerRadius + 2.0D);
    }

    public float workerRadius() {
        return workerRadius;
    }

    /** Persistent, real, position-independent identifier for this crank instance. */
    public UUID crankInstanceUuid() {
        return crankInstanceUuid;
    }

    /**
     * Test-only override for {@link #crankInstanceUuid}. Replaces the random
     * initial UUID with the supplied one so GameTests can assert the
     * marker-orphan / marker-foreign behaviour deterministically.
     */
    public void setCrankInstanceUuidForTesting(UUID uuid) {
        this.crankInstanceUuid = uuid;
    }

    /** {@code true} when this crank currently considers the given mob UUID its worker. */
    public boolean isAssignedWorker(UUID uuid) {
        return workerUuid != null && workerUuid.equals(uuid);
    }

    /**
     * Test-only override for the worker's UUID, so GameTests can simulate
     * a live crank that still claims a specific mob.
     */
    public void setWorkerUuidForTesting(UUID uuid) {
        this.workerUuid = uuid;
    }

    /** Test-only introspection: whether this crank currently owns worker AI suppression. */
    public boolean ownsWorkerAiSuppressionForTesting() {
        return ownsWorkerAiSuppression;
    }

    /** Test-only introspection: the worker UUID this crank currently suppresses AI for. */
    @Nullable
    public UUID aiSuppressedWorkerUuidForTesting() {
        return aiSuppressedWorkerUuid;
    }

    /**
     * Test-only override for the cached worker reference. Simulates the
     * state after a chunk unload/BE reload, where the engine still owns AI
     * suppression (restored from NBT) but holds no entity reference.
     */
    public void setCachedWorkerMobForTesting(@Nullable Mob mob) {
        this.cachedWorkerMob = mob;
    }

    /**
     * Test-only entry point that runs the same AI-control decision the
     * working tick performs ({@link #controlWorkerAi}), without requiring a
     * full kinetic network around the crank.
     */
    public void controlWorkerAiForTesting(Mob mob) {
        controlWorkerAi(mob);
    }

    // ==========================================
    // NBT (CompoundTag API is identical on 1.20.1 and 1.21.1)
    // ==========================================

    public void write(CompoundTag compound, boolean clientPacket) {
        compound.putFloat("RpmModifier", rpmModifier);
        compound.putFloat("PathStressModifier", pathStressModifier);
        compound.putBoolean("HasValidWorkingBlocks", hasValidWorkingBlocks);
        compound.putFloat("GenerationDirection", generationDirection);
        compound.putString("RedstoneMode", redstoneMode.getSerializedName());
        compound.putFloat("EffectiveBaseRpm", effectiveBaseRpm);
        compound.putFloat("EffectiveBaseStress", effectiveBaseStress);
        compound.putFloat("WorkerRadius", workerRadius);
        compound.putUUID("CrankInstanceUUID", crankInstanceUuid);

        if (workerUuid != null) {
            compound.putUUID("WorkerUUID", workerUuid);
        }
        if (lastKnownWorkerPos != null) {
            compound.putLong("WorkerPos", lastKnownWorkerPos.asLong());
        }
        if (Double.isFinite(workerOrbitAngle)) {
            compound.putDouble("WorkerOrbitAngle", workerOrbitAngle);
        } else {
            compound.remove("WorkerOrbitAngle");
        }
        if (ownsWorkerAiSuppression && aiSuppressedWorkerUuid != null) {
            compound.putBoolean("OwnsWorkerAiSuppression", true);
            compound.putUUID("AiSuppressedWorkerUUID", aiSuppressedWorkerUuid);
        } else {
            compound.remove("OwnsWorkerAiSuppression");
            compound.remove("AiSuppressedWorkerUUID");
        }

        if (clientPacket) {
            compound.putBoolean("WorkerResolved", workerResolved);
            compound.putBoolean("WorkerEligible", workerEligible);
            compound.putBoolean("IsWorking", isWorking);
            compound.putBoolean("ScriptVetoed", scriptVetoed);
            compound.putFloat("SpeedBonusPercent", speedBonusPercent);
            compound.putFloat("HealthBonusPercent", healthBonusPercent);
            compound.putInt("EfficiencyPercent", efficiencyPercent);
            compound.putInt("InvalidBlockCount", invalidBlockCount);
            compound.putString("CachedWorkerName", cachedWorkerName);
        }
    }

    public void read(CompoundTag compound, boolean clientPacket) {
        if (compound.contains("RpmModifier")) rpmModifier = compound.getFloat("RpmModifier");
        if (compound.contains("PathStressModifier")) pathStressModifier = compound.getFloat("PathStressModifier");
        if (compound.contains("HasValidWorkingBlocks")) hasValidWorkingBlocks = compound.getBoolean("HasValidWorkingBlocks");
        if (compound.contains("EffectiveBaseRpm")) effectiveBaseRpm = compound.getFloat("EffectiveBaseRpm");
        if (compound.contains("EffectiveBaseStress")) effectiveBaseStress = compound.getFloat("EffectiveBaseStress");
        if (compound.contains("WorkerRadius")) {
            float savedRadius = compound.getFloat("WorkerRadius");
            workerRadius = Float.isFinite(savedRadius)
                    ? Math.max(WorkerStats.MIN_MOVEMENT_RADIUS, Math.min(WorkerStats.MAX_MOVEMENT_RADIUS, savedRadius))
                    : WorkerStats.DEFAULT.movementRadius();
        }

        if (compound.contains("RedstoneMode")) {
            String modeStr = compound.getString("RedstoneMode");
            for (RedstoneMode mode : RedstoneMode.values()) {
                if (mode.getSerializedName().equalsIgnoreCase(modeStr)) {
                    redstoneMode = mode;
                    break;
                }
            }
        } else if (!clientPacket) {
            // Pre-1.2 crank from 1.1 save: redstone never affected it.
            redstoneMode = RedstoneMode.IGNORE;
        }

        if (compound.hasUUID("CrankInstanceUUID")) {
            crankInstanceUuid = compound.getUUID("CrankInstanceUUID");
        }

        if (compound.contains("GenerationDirection")) {
            generationDirection = compound.getFloat("GenerationDirection");
            if (generationDirection == 0) generationDirection = 1.0f;
        } else {
            needsLegacyDirectionResolution = true;
        }

        if (compound.hasUUID("WorkerUUID")) {
            workerUuid = compound.getUUID("WorkerUUID");
        }
        if (compound.contains("WorkerPos")) {
            lastKnownWorkerPos = BlockPos.of(compound.getLong("WorkerPos"));
        }
        workerOrbitAngle = compound.contains("WorkerOrbitAngle")
                ? compound.getDouble("WorkerOrbitAngle")
                : Double.NaN;
        if (!Double.isFinite(workerOrbitAngle)) {
            workerOrbitAngle = Double.NaN;
        }
        ownsWorkerAiSuppression = compound.getBoolean("OwnsWorkerAiSuppression")
                && compound.hasUUID("AiSuppressedWorkerUUID");
        aiSuppressedWorkerUuid = ownsWorkerAiSuppression
                ? compound.getUUID("AiSuppressedWorkerUUID")
                : null;

        if (clientPacket) {
            workerResolved = compound.getBoolean("WorkerResolved");
            workerEligible = compound.getBoolean("WorkerEligible");
            isWorking = compound.getBoolean("IsWorking");
            scriptVetoed = compound.getBoolean("ScriptVetoed");
            speedBonusPercent = compound.getFloat("SpeedBonusPercent");
            healthBonusPercent = compound.getFloat("HealthBonusPercent");
            efficiencyPercent = compound.getInt("EfficiencyPercent");
            invalidBlockCount = compound.getInt("InvalidBlockCount");
            cachedWorkerName = compound.getString("CachedWorkerName");
        } else {
            workerResolved = false;
            workerEligible = false;
            isWorking = false;
            scriptVetoed = false;
        }
    }

    // ==========================================
    // Worker lifecycle
    // ==========================================

    public void attachWorker(Mob worker, WorkerResolver.ResolvedWorker profile) {
        restoreWorkerAi();
        // Defensive cleanup: only a marker from a *different* crank should be
        // cleared on attach. If the marker belongs to this exact crank
        // (instance UUID), the marker is meaningful state and must be left
        // alone so the next release can still use the recorded previous
        // NoAI.
        if (WorkerActivityControl.hasForeignMarker(worker, crankInstanceUuid)) {
            WorkerActivityControl.releaseFromMarker(worker);
        }
        this.cachedWorkerMob = worker;
        this.workerUuid = worker.getUUID();
        this.lastKnownWorkerPos = worker.blockPosition();
        this.missingWorkerTicks = 0;
        this.statRefreshTimer = 0;
        this.nextWorkStartRetryTick = 0;
        this.nextFallbackWorkerSearchTick = 0;
        this.scriptVetoed = false;
        this.workerResolved = true;
        this.workerEligible = profile.isValid();
        this.workerOrbitAngle = Double.NaN;

        applyProfile(worker, profile);

        float existingSpeed = host.theoreticalSpeed();
        if (existingSpeed != 0) {
            this.generationDirection = Math.signum(existingSpeed);
        } else {
            this.generationDirection = 1.0f;
        }

        Level level = level();
        if (level != null && !level.isClientSide()) {
            host.setBlockState(host.blockState().setValue(CrankProperties.HAS_WORKER, true));
            checkPathBlocks();
            host.refreshKinetic();
            host.syncToClient();
            CHPApi.scripts().fireWorkerAttached(worker, host.pos(), level, profile);
        }
    }

    private void applyProfile(Mob worker, WorkerResolver.ResolvedWorker profile) {
        if (!profile.isValid()) {
            this.workerEligible = false;
            this.effectiveBaseRpm = 0.0f;
            this.effectiveBaseStress = 0.0f;
            this.workerRadius = DEFAULT_RADIUS;
            this.speedBonusPercent = 0.0f;
            this.healthBonusPercent = 0.0f;
            this.cachedWorkerName = worker.getName().getString();
            return;
        }

        this.workerEligible = true;
        this.effectiveBaseRpm = profile.effectiveRpm();
        this.effectiveBaseStress = profile.effectiveStressCapacity();
        this.workerRadius = profile.baseStats().movementRadius();
        this.speedBonusPercent = profile.speedBonusPercent();
        this.healthBonusPercent = profile.healthBonusPercent();
        this.cachedWorkerName = worker.getName().getString();

        Level level = level();
        if (level != null && !level.isClientSide()) {
            float[] scriptModifiers = CHPApi.scripts().fireOutputCalculated(worker, host.pos(), level, effectiveBaseRpm, effectiveBaseStress);
            this.effectiveBaseRpm *= scriptModifiers[0];
            this.effectiveBaseStress *= scriptModifiers[1];
        }
    }

    public void detachWorker(boolean dropLead) {
        Mob worker = cachedWorkerMob;
        stopWorking();
        clearWorkerReferences();

        Level level = level();
        if (level != null && !level.isClientSide()) {
            BlockState state = host.blockState();
            if (state.getValue(CrankProperties.HAS_WORKER)) {
                host.setBlockState(state.setValue(CrankProperties.HAS_WORKER, false));
            }
            CHPUtils.cleanUpLeash(level, host.pos(), dropLead);
            host.refreshKinetic();
            host.syncToClient();
            CHPApi.scripts().fireWorkerDetached(worker, host.pos(), level);
        }
    }

    public void onCrankRemoved() {
        Mob worker = cachedWorkerMob;
        stopWorking();
        Level level = level();
        if (level != null && !level.isClientSide()) {
            CHPUtils.cleanUpLeash(level, host.pos(), true);
            if (worker != null || workerUuid != null) {
                CHPApi.scripts().fireWorkerDetached(worker, host.pos(), level);
            }
        }
        clearWorkerReferences();
    }

    private void clearWorkerReferences() {
        restoreWorkerAi();

        // Permanent detach/removal: if the old worker was unavailable, its
        // persistent marker remains on that worker for entity-load orphan
        // recovery, but this BE must no longer claim ownership. Keeping a
        // stale ownership record here would make the next worker skip its
        // own acquire (controlWorkerAi would merely maintain it), leaving
        // it NoAI with no recovery marker of its own. Safe even when
        // restoreWorkerAi succeeded: those fields are already false/null.
        this.ownsWorkerAiSuppression = false;
        this.aiSuppressedWorkerUuid = null;

        // This BE is being detached/destroyed (break, invalid transition,
        // or `onCrankRemoved`), so it must drop its local ownership record
        // even if the worker is currently unloaded.
        this.cachedWorkerMob = null;
        this.workerUuid = null;
        this.lastKnownWorkerPos = null;
        this.missingWorkerTicks = 0;
        this.statRefreshTimer = 0;
        this.nextWorkStartRetryTick = 0;
        this.nextFallbackWorkerSearchTick = 0;
        this.workerResolved = false;
        this.workerEligible = false;
        this.workerRadius = DEFAULT_RADIUS;
        this.cachedWorkerName = "";
        this.speedBonusPercent = 0.0f;
        this.healthBonusPercent = 0.0f;
        this.workerOrbitAngle = Double.NaN;
    }

    // ==========================================
    // Tick
    // ==========================================

    /** Work that must happen before Create's own kinetic block-entity tick. */
    public void beforeHostTick() {
        Level level = level();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (needsLegacyDirectionResolution) {
            needsLegacyDirectionResolution = false;
            resolvingLegacyDirection = true;
            suppressGeneration = true;
            host.clearKineticInfo();
            host.requestSpeedUpdate();
        }

        // Preserve the original lifecycle ordering: kinetic/redstone refreshes
        // happen before Create evaluates this generator in its host tick.
        boolean currentRedstone = level.hasNeighborSignal(host.pos());
        if (currentRedstone != lastRedstoneState) {
            lastRedstoneState = currentRedstone;
            host.refreshKinetic();
            host.syncToClient();
        }
    }

    /** Work that historically happened after Create's own kinetic tick. */
    public void afterHostTick() {
        Level level = level();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (resolvingLegacyDirection) {
            resolvingLegacyDirection = false;
            float networkSpeed = host.theoreticalSpeed();
            generationDirection = (networkSpeed == 0) ? 1.0f : Math.signum(networkSpeed);
            suppressGeneration = false;
            host.refreshKinetic();
        }

        // 1. Reconcile attachment lifecycle
        reconcileWorker();

        // 2. Update path state on interval
        int interval = CHPApi.config().checkIntervalTicks();
        if (level.getGameTime() - lastPathCheckTick >= interval || lastPathCheckTick < 0) {
            lastPathCheckTick = level.getGameTime();
            checkPathBlocks();
        }

        // 3. Centralized working state transition with non-permanent beforeWorkStart check & cooldown
        boolean canWork = canPhysicallyWork();
        boolean wasWorking = this.isWorking;

        if (canWork) {
            if (!wasWorking) {
                long time = level.getGameTime();
                if (!scriptVetoed || time >= nextWorkStartRetryTick) {
                    if (cachedWorkerMob != null && !CHPApi.scripts().fireBeforeWorkStart(cachedWorkerMob, host.pos(), level)) {
                        this.scriptVetoed = true;
                        this.isWorking = false;
                        this.nextWorkStartRetryTick = time + 20;
                    } else {
                        this.scriptVetoed = false;
                        this.isWorking = true;
                        CHPApi.scripts().fireWorkStarted(cachedWorkerMob, host.pos(), level);
                    }
                }
            }
        } else {
            stopWorking();
        }

        if (wasWorking != this.isWorking) {
            host.refreshKinetic();
            host.syncToClient();
        }

        // 4. Move animal along track if active
        if (isWorking && cachedWorkerMob != null) {
            moveWorkerTo(cachedWorkerMob);
        }
    }

    private void stopWorking() {
        boolean wasWorking = this.isWorking;
        this.isWorking = false;
        this.scriptVetoed = false;
        this.nextWorkStartRetryTick = 0;
        this.workerOrbitAngle = Double.NaN;
        restoreWorkerAi();

        Level level = level();
        if (wasWorking && level != null && !level.isClientSide()) {
            CHPApi.scripts().fireWorkStopped(host.pos(), level);
        }
    }

    private void controlWorkerAi(Mob mob) {
        UUID mobUuid = mob.getUUID();

        if (ownsWorkerAiSuppression
                && !mobUuid.equals(aiSuppressedWorkerUuid)) {
            restoreWorkerAi();
        }

        UUID thisCrank = crankInstanceUuid;

        if (WorkerActivityControl.hasForeignMarker(mob, thisCrank)) {
            WorkerActivityControl.releaseFromMarker(mob);
        }

        if (!ownsWorkerAiSuppression) {
            ownsWorkerAiSuppression = WorkerActivityControl.acquire(
                    mob,
                    host.pos(),
                    thisCrank
            );
            aiSuppressedWorkerUuid = ownsWorkerAiSuppression ? mobUuid : null;
        } else {
            WorkerActivityControl.maintain(mob);
        }
    }

    /**
     * Restore the worker's original {@code NoAI} state and clear the crank's
     * ownership record. When the worker cannot be resolved (it is unloaded
     * or in another chunk), local ownership is preserved: temporarily
     * dropping the record is exactly what makes the next same-crank
     * {@link #controlWorkerAi} call treat a normal unload as a foreign-crank
     * reacquisition, which would corrupt the recorded {@code NoAI} baseline.
     */
    private void restoreWorkerAi() {
        if (!ownsWorkerAiSuppression || aiSuppressedWorkerUuid == null) {
            return;
        }

        Mob controlledWorker = resolveSuppressedWorker();
        if (controlledWorker == null) {
            // Worker is temporarily unavailable. KEEP ownership information.
            // The worker marker also remains intact.
            return;
        }

        WorkerActivityControl.release(controlledWorker, true);
        ownsWorkerAiSuppression = false;
        aiSuppressedWorkerUuid = null;
    }

    @Nullable
    private Mob resolveSuppressedWorker() {
        Level level = level();
        if (!(level instanceof ServerLevel serverLevel) || aiSuppressedWorkerUuid == null) {
            return null;
        }

        // The level's entity index is authoritative: a cached Mob reference
        // may point at an entity that has since been unloaded or discarded,
        // and releasing AI state against a stale object would silently do
        // nothing while the marker machinery believes restoration happened.
        Entity entity = serverLevel.getEntity(aiSuppressedWorkerUuid);
        if (entity instanceof Mob mob) {
            cachedWorkerMob = mob;
            return mob;
        }
        return null;
    }

    private boolean isWorkerAttachedToThisCrank(Mob mob) {
        if (mob == null || !mob.isAlive() || !mob.isLeashed()) {
            return false;
        }
        Entity holder = mob.getLeashHolder();
        if (holder instanceof LeashFenceKnotEntity knot) {
            return knot.blockPosition().equals(host.pos());
        }
        return false;
    }

    @Nullable
    private Mob resolveWorker() {
        Level level = level();
        if (level == null) return null;

        if (cachedWorkerMob != null && isWorkerAttachedToThisCrank(cachedWorkerMob)) {
            // A cached reference is only trustworthy while the entity is
            // still registered in the server level; an unloaded/discardable
            // entity can keep its leash data but no longer exist as far as
            // the world is concerned.
            if (level instanceof ServerLevel serverLevel) {
                if (serverLevel.getEntity(cachedWorkerMob.getUUID()) == cachedWorkerMob) {
                    return cachedWorkerMob;
                }
                cachedWorkerMob = null;
            } else {
                return cachedWorkerMob;
            }
        }

        if (workerUuid != null && level instanceof ServerLevel serverLevel) {
            Entity ent = serverLevel.getEntity(workerUuid);
            if (ent instanceof Mob mob && isWorkerAttachedToThisCrank(mob)) {
                cachedWorkerMob = mob;
                lastKnownWorkerPos = mob.blockPosition();
                return mob;
            }
        }

        long gameTime = level.getGameTime();
        if (gameTime < nextFallbackWorkerSearchTick) {
            return null;
        }
        nextFallbackWorkerSearchTick = gameTime + 20;

        double searchRadius = Math.max(8.0D, workerRadius + 4.0D);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, new AABB(host.pos()).inflate(searchRadius), this::isWorkerAttachedToThisCrank);
        if (!nearby.isEmpty()) {
            Mob mob = nearby.get(0);
            cachedWorkerMob = mob;
            workerUuid = mob.getUUID();
            lastKnownWorkerPos = mob.blockPosition();
            return mob;
        }

        return null;
    }

    private void reconcileWorker() {
        BlockState state = host.blockState();
        if (!state.getValue(CrankProperties.HAS_WORKER)) {
            clearWorkerReferences();
            return;
        }

        Level level = level();
        if (level == null) return;

        Mob worker = resolveWorker();
        boolean wasResolved = this.workerResolved;
        boolean wasEligible = this.workerEligible;
        boolean outputChanged = false;
        boolean radiusChanged = false;

        if (worker != null) {
            this.workerResolved = true;
            missingWorkerTicks = 0;
            lastKnownWorkerPos = worker.blockPosition();

            statRefreshTimer++;
            boolean needStatRefresh = !wasResolved || (statRefreshTimer >= 60);

            if (needStatRefresh) {
                statRefreshTimer = 0;
                WorkerResolver.ResolvedWorker profile = WorkerResolver.resolve(worker);
                this.workerEligible = profile.isValid();

                float oldRpm = effectiveBaseRpm;
                float oldStress = effectiveBaseStress;
                float oldRadius = workerRadius;
                applyProfile(worker, profile);

                outputChanged = Float.compare(oldRpm, effectiveBaseRpm) != 0
                        || Float.compare(oldStress, effectiveBaseStress) != 0;
                radiusChanged = Float.compare(oldRadius, workerRadius) != 0;
                if (radiusChanged) {
                    checkPathBlocks();
                }
            }

            if (!wasResolved) {
                float networkSpeed = host.theoreticalSpeed();
                if (networkSpeed != 0) {
                    generationDirection = Math.signum(networkSpeed);
                }
            }
        } else {
            this.workerResolved = false;
            this.workerEligible = false;
            BlockPos checkPos = lastKnownWorkerPos != null ? lastKnownWorkerPos : host.pos();
            if (level.hasChunkAt(checkPos)) {
                missingWorkerTicks++;
                boolean attachmentPresent = CHPUtils.hasAttachedWorker(level, host.pos());
                if (shouldDetachMissingWorker(true, attachmentPresent, missingWorkerTicks)) {
                    detachWorker(true);
                    return;
                }
            }
        }

        if (wasResolved != this.workerResolved || wasEligible != this.workerEligible || outputChanged || radiusChanged) {
            host.refreshKinetic();
            host.syncToClient();
        }
    }

    public static boolean shouldDetachMissingWorker(boolean workerLocationLoaded, boolean attachmentPresent, int missingTicks) {
        if (!workerLocationLoaded) return false;
        return attachmentPresent
                ? missingTicks > MISSING_WORKER_GRACE_TICKS
                : missingTicks > MISSING_ATTACHMENT_GRACE_TICKS;
    }

    private BlockPos[] offsetsForRadius(float radius) {
        if (cachedOffsets == null || cachedOffsetsRadius != radius) {
            cachedOffsets = generateOffsetsForRadius(radius);
            cachedOffsetsRadius = radius;
        }
        return cachedOffsets;
    }

    private void checkPathBlocks() {
        Level level = level();
        if (level == null) return;

        BlockPos[] offsets = offsetsForRadius(workerRadius);
        PathEvaluator.Result evalResult = PathEvaluator.evaluate(level, host.pos(), offsets);
        float speedMult = evalResult.speedMultiplier();
        float stressMult = evalResult.stressMultiplier();

        float[] scriptMods = CHPApi.scripts().firePathEvaluated(host.pos(), level, evalResult);
        speedMult *= scriptMods[0];
        stressMult *= scriptMods[1];

        boolean valid = evalResult.isValid();
        int invalidCount = evalResult.invalidBlocks();
        int eff = Math.round(speedMult * 100.0f);

        boolean changed = (this.hasValidWorkingBlocks != valid)
                || (this.rpmModifier != speedMult)
                || (this.pathStressModifier != stressMult)
                || (this.invalidBlockCount != invalidCount);

        if (changed) {
            boolean wasGenerating = hasValidWorkingBlocks && (rpmModifier > 0);
            this.hasValidWorkingBlocks = valid;
            this.rpmModifier = speedMult;
            this.pathStressModifier = stressMult;
            this.efficiencyPercent = eff;
            this.invalidBlockCount = invalidCount;

            boolean willGenerate = hasValidWorkingBlocks && (rpmModifier > 0);
            if (!wasGenerating && willGenerate) {
                float networkSpeed = host.theoreticalSpeed();
                if (networkSpeed != 0) {
                    this.generationDirection = Math.signum(networkSpeed);
                }
            }

            host.refreshKinetic();
            host.syncToClient();
        }
    }

    public float getEfficiencyPercent() {
        return efficiencyPercent;
    }

    public int getInvalidBlockCount() {
        return invalidBlockCount;
    }

    public float getSpeedBonusPercent() {
        return speedBonusPercent;
    }

    public float getHealthBonusPercent() {
        return healthBonusPercent;
    }

    public String getCachedWorkerName() {
        return cachedWorkerName;
    }

    public float getEffectiveBaseRpm() {
        return effectiveBaseRpm;
    }

    public float getEffectiveBaseStress() {
        return effectiveBaseStress;
    }

    private void moveWorkerTo(Mob mob) {
        Level level = level();
        if (level == null || mob == null) return;

        float speed = generatedSpeed();
        if (speed == 0.0f) return;

        BlockPos pos = host.pos();
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;

        double direction = Math.signum(speed);
        double angularVelocity = Math.toRadians(Math.abs(speed) * 6.0); // 6 deg/sec per RPM
        double angularDelta = (angularVelocity * direction) / 20.0;
        controlWorkerAi(mob);
        if (!Double.isFinite(workerOrbitAngle)) {
            workerOrbitAngle = WorkerOrbitMovement.angleFromPosition(
                    mob.getX(), mob.getZ(), centerX, centerZ);
        }
        double currentAngle = workerOrbitAngle;
        workerOrbitAngle = WorkerOrbitMovement.normalizeAngle(workerOrbitAngle + angularDelta);
        WorkerOrbitMovement.Snapshot movement = WorkerOrbitMovement.moveToAngle(
                mob, centerX, centerZ, workerRadius, currentAngle, workerOrbitAngle);
        WorkerActivityControl.clearHorizontalVelocity(mob);
    }
}
