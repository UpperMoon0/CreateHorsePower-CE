package net.steampn.createhorsepower.blocks.horse_crank;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.compat.OptionalIntegrations;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import net.steampn.createhorsepower.utils.CHPUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

import static net.steampn.createhorsepower.blocks.horse_crank.HorseCrankBlock.*;

public class HorseCrankTileEntity extends GeneratingKineticBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos[] OFFSETS = generateOffsets();

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

    public HorseCrankTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.redstoneMode = Config.DEFAULT_REDSTONE_MODE.get();
    }

    private static BlockPos[] generateOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int z = -3; z <= 3; z++) {
            for (int x = -3; x <= 3; x++) {
                double distSq = x * x + z * z;
                if (distSq >= 4.0 && distSq <= 11.0) {
                    offsets.add(new BlockPos(x, -1, z));
                }
            }
        }
        return offsets.toArray(new BlockPos[0]);
    }

    public boolean isStoppedByRedstone() {
        if (level == null) return false;
        boolean signal = level.hasNeighborSignal(worldPosition);
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
        if (level != null && !level.isClientSide()) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    public RedstoneMode cycleRedstoneMode() {
        RedstoneMode next = redstoneMode.next();
        setRedstoneMode(next);
        return next;
    }

    public boolean isWorkerEligible() {
        return workerEligible;
    }

    public boolean isWorking() {
        return isWorking;
    }

    public boolean canPhysicallyWork() {
        return getBlockState().getValue(HAS_WORKER)
                && workerResolved
                && workerEligible
                && hasValidWorkingBlocks
                && !isStoppedByRedstone()
                && !suppressGeneration
                && (effectiveBaseRpm * rpmModifier > 0);
    }

    @Override
    public float getGeneratedSpeed() {
        if (!isWorking || !canPhysicallyWork()) {
            return 0.0F;
        }
        return effectiveBaseRpm * rpmModifier * generationDirection;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!isWorking || !canPhysicallyWork()) return 0;

        float speed = getGeneratedSpeed();
        if (speed == 0) return 0;

        float capacity = effectiveBaseStress * pathStressModifier;
        capacity = Math.abs(capacity / Math.abs(speed));
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.header").withStyle(ChatFormatting.GOLD));

        if (!getBlockState().getValue(HAS_WORKER)) {
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
        } else {
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

        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(this.getBlockPos()).inflate(4.0D);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("RpmModifier", rpmModifier);
        compound.putFloat("PathStressModifier", pathStressModifier);
        compound.putBoolean("HasValidWorkingBlocks", hasValidWorkingBlocks);
        compound.putFloat("GenerationDirection", generationDirection);
        compound.putString("RedstoneMode", redstoneMode.getSerializedName());
        compound.putFloat("EffectiveBaseRpm", effectiveBaseRpm);
        compound.putFloat("EffectiveBaseStress", effectiveBaseStress);

        if (workerUuid != null) {
            compound.putUUID("WorkerUUID", workerUuid);
        }
        if (lastKnownWorkerPos != null) {
            compound.putLong("WorkerPos", lastKnownWorkerPos.asLong());
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

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("RpmModifier")) rpmModifier = compound.getFloat("RpmModifier");
        if (compound.contains("PathStressModifier")) pathStressModifier = compound.getFloat("PathStressModifier");
        if (compound.contains("HasValidWorkingBlocks")) hasValidWorkingBlocks = compound.getBoolean("HasValidWorkingBlocks");
        if (compound.contains("EffectiveBaseRpm")) effectiveBaseRpm = compound.getFloat("EffectiveBaseRpm");
        if (compound.contains("EffectiveBaseStress")) effectiveBaseStress = compound.getFloat("EffectiveBaseStress");

        if (compound.contains("RedstoneMode")) {
            String modeStr = compound.getString("RedstoneMode");
            for (RedstoneMode mode : RedstoneMode.values()) {
                if (mode.getSerializedName().equalsIgnoreCase(modeStr)) {
                    redstoneMode = mode;
                    break;
                }
            }
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

    public void attachWorker(Mob worker, WorkerResolver.ResolvedWorker profile) {
        this.cachedWorkerMob = worker;
        this.workerUuid = worker.getUUID();
        this.lastKnownWorkerPos = worker.blockPosition();
        this.missingWorkerTicks = 0;
        this.workerResolved = true;
        this.workerEligible = profile.isValid();

        applyProfile(worker, profile);

        float existingSpeed = getTheoreticalSpeed();
        if (existingSpeed != 0) {
            this.generationDirection = Math.signum(existingSpeed);
        } else {
            this.generationDirection = 1.0f;
        }

        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.setBlock(worldPosition, state.setValue(HAS_WORKER, true), 3);
            checkPathBlocks();
            updateGeneratedRotation();
            notifyUpdate();
            OptionalIntegrations.fireWorkerAttached(worker, worldPosition, level, profile);
        }
    }

    private void applyProfile(Mob worker, WorkerResolver.ResolvedWorker profile) {
        if (!profile.isValid()) {
            this.workerEligible = false;
            this.effectiveBaseRpm = 0.0f;
            this.effectiveBaseStress = 0.0f;
            this.speedBonusPercent = 0.0f;
            this.healthBonusPercent = 0.0f;
            this.cachedWorkerName = worker.getName().getString();
            return;
        }

        this.workerEligible = true;
        this.effectiveBaseRpm = profile.effectiveRpm();
        this.effectiveBaseStress = profile.effectiveStressCapacity();
        this.speedBonusPercent = profile.speedBonusPercent();
        this.healthBonusPercent = profile.healthBonusPercent();
        this.cachedWorkerName = worker.getName().getString();

        if (level != null && !level.isClientSide()) {
            float[] scriptModifiers = OptionalIntegrations.fireOutputCalculated(worker, worldPosition, level, effectiveBaseRpm, effectiveBaseStress);
            this.effectiveBaseRpm *= scriptModifiers[0];
            this.effectiveBaseStress *= scriptModifiers[1];
        }
    }

    public void detachWorker(boolean dropLead) {
        Mob worker = cachedWorkerMob;
        clearWorkerReferences();

        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            if (state.getValue(HAS_WORKER)) {
                level.setBlock(worldPosition, state.setValue(HAS_WORKER, false), 3);
            }
            CHPUtils.cleanUpLeash(level, worldPosition, dropLead);
            updateGeneratedRotation();
            notifyUpdate();
            OptionalIntegrations.fireWorkerDetached(worker, worldPosition, level);
        }
    }

    public void onCrankRemoved() {
        if (level != null && !level.isClientSide()) {
            CHPUtils.cleanUpLeash(level, worldPosition, true);
        }
        clearWorkerReferences();
    }

    private void clearWorkerReferences() {
        this.cachedWorkerMob = null;
        this.workerUuid = null;
        this.lastKnownWorkerPos = null;
        this.missingWorkerTicks = 0;
        this.workerResolved = false;
        this.workerEligible = false;
        this.isWorking = false;
        this.scriptVetoed = false;
        this.cachedWorkerName = "";
        this.speedBonusPercent = 0.0f;
        this.healthBonusPercent = 0.0f;
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide()) {
            if (needsLegacyDirectionResolution) {
                needsLegacyDirectionResolution = false;
                resolvingLegacyDirection = true;
                suppressGeneration = true;
                clearKineticInformation();
                updateSpeed = true;
            }

            // Check redstone transition
            boolean currentRedstone = level.hasNeighborSignal(worldPosition);
            if (currentRedstone != lastRedstoneState) {
                lastRedstoneState = currentRedstone;
                updateGeneratedRotation();
                notifyUpdate();
            }
        }

        super.tick();

        if (level == null || level.isClientSide()) {
            return;
        }

        if (resolvingLegacyDirection) {
            resolvingLegacyDirection = false;
            float networkSpeed = getTheoreticalSpeed();
            generationDirection = (networkSpeed == 0) ? 1.0f : Math.signum(networkSpeed);
            suppressGeneration = false;
            updateGeneratedRotation();
        }

        // 1. Reconcile attachment lifecycle
        reconcileWorker();

        // 2. Update path state on interval
        int interval = Config.CHECK_INTERVAL_TICKS.get();
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
                    if (cachedWorkerMob != null && !OptionalIntegrations.fireBeforeWorkStart(cachedWorkerMob, worldPosition, level)) {
                        this.scriptVetoed = true;
                        this.isWorking = false;
                        this.nextWorkStartRetryTick = time + 20;
                    } else {
                        this.scriptVetoed = false;
                        this.isWorking = true;
                        OptionalIntegrations.fireWorkStarted(cachedWorkerMob, worldPosition, level);
                    }
                }
            }
        } else {
            this.scriptVetoed = false;
            if (wasWorking) {
                this.isWorking = false;
                OptionalIntegrations.fireWorkStopped(worldPosition, level);
            }
        }

        if (wasWorking != this.isWorking) {
            updateGeneratedRotation();
            notifyUpdate();
        }

        // 4. Move animal along track if active
        if (isWorking && cachedWorkerMob != null) {
            moveWorkerTo(cachedWorkerMob);
        }
    }

    private boolean isWorkerAttachedToThisCrank(Mob mob) {
        if (mob == null || !mob.isAlive() || !mob.isLeashed()) {
            return false;
        }
        Entity holder = mob.getLeashHolder();
        if (holder instanceof LeashFenceKnotEntity knot) {
            return knot.blockPosition().equals(this.worldPosition);
        }
        return false;
    }

    private Mob resolveWorker() {
        if (cachedWorkerMob != null && isWorkerAttachedToThisCrank(cachedWorkerMob)) {
            return cachedWorkerMob;
        }

        if (workerUuid != null && level instanceof ServerLevel serverLevel) {
            Entity ent = serverLevel.getEntity(workerUuid);
            if (ent instanceof Mob mob && isWorkerAttachedToThisCrank(mob)) {
                cachedWorkerMob = mob;
                lastKnownWorkerPos = mob.blockPosition();
                return mob;
            }
        }

        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(8.0D), this::isWorkerAttachedToThisCrank);
        if (!nearby.isEmpty()) {
            Mob mob = nearby.getFirst();
            cachedWorkerMob = mob;
            workerUuid = mob.getUUID();
            lastKnownWorkerPos = mob.blockPosition();
            return mob;
        }

        return null;
    }

    private void reconcileWorker() {
        BlockState state = getBlockState();
        if (!state.getValue(HAS_WORKER)) {
            clearWorkerReferences();
            return;
        }

        Mob worker = resolveWorker();
        boolean wasResolved = this.workerResolved;
        boolean wasEligible = this.workerEligible;

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
                applyProfile(worker, profile);
            }

            if (!wasResolved) {
                float networkSpeed = getTheoreticalSpeed();
                if (networkSpeed != 0) {
                    generationDirection = Math.signum(networkSpeed);
                }
            }
        } else {
            this.workerResolved = false;
            this.workerEligible = false;
            BlockPos checkPos = lastKnownWorkerPos != null ? lastKnownWorkerPos : worldPosition;
            if (level.hasChunkAt(checkPos)) {
                missingWorkerTicks++;
                if (missingWorkerTicks > 80) {
                    detachWorker(true);
                    return;
                }
            }
        }

        if (wasResolved != this.workerResolved || wasEligible != this.workerEligible) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    private void checkPathBlocks() {
        if (level == null) return;

        PathEvaluator.Result evalResult = PathEvaluator.evaluate(level, worldPosition, OFFSETS);
        float speedMult = evalResult.speedMultiplier();
        float stressMult = evalResult.stressMultiplier();

        float[] scriptMods = OptionalIntegrations.firePathEvaluated(worldPosition, level, evalResult);
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
                float networkSpeed = getTheoreticalSpeed();
                if (networkSpeed != 0) {
                    this.generationDirection = Math.signum(networkSpeed);
                }
            }

            updateGeneratedRotation();
            notifyUpdate();
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
        if (level == null || mob == null) return;

        float speed = getGeneratedSpeed();
        if (speed == 0.0f) return;

        double centerX = worldPosition.getX() + 0.5;
        double centerZ = worldPosition.getZ() + 0.5;

        double dx = mob.getX() - centerX;
        double dz = mob.getZ() - centerZ;

        double currentAngle = Math.atan2(dz, dx);

        float radius = 2.5f;
        Optional<WorkerStats> stats = WorkerResolver.getBaseStats(mob.getType());
        if (stats.isPresent()) {
            radius = stats.get().movementRadius();
        }

        double direction = Math.signum(speed);
        double angularVelocity = Math.toRadians(Math.abs(speed) * 6.0); // 6 deg/sec per RPM
        double angularDelta = (angularVelocity * direction) / 20.0;

        double newAngle = currentAngle + angularDelta;

        double targetX = centerX + radius * Math.cos(newAngle);
        double targetZ = centerZ + radius * Math.sin(newAngle);

        mob.setPos(targetX, mob.getY(), targetZ);

        double tangentAngle = newAngle + (Math.PI / 2.0) * direction;
        float yaw = (float) Math.toDegrees(tangentAngle);

        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);

        WalkAnimationState walkState = mob.walkAnimation;
        walkState.setSpeed(1.0f);
        walkState.update(1.0f, 0.2f);
    }
}
