package net.steampn.createhorsepower.blocks.horse_crank;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.config.Config;
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
    private float generationDirection = 1.0f;
    private boolean needsLegacyDirectionResolution = false;

    @Nullable
    private Mob cachedWorkerMob;
    @Nullable
    private UUID workerUuid;
    @Nullable
    private BlockPos lastKnownWorkerPos;
    private int missingWorkerTicks = 0;
    private long lastPathCheckTick = -1;

    public HorseCrankTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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

    @Override
    public float getGeneratedSpeed() {
        BlockState state = getBlockState();
        if (!state.getValue(HAS_WORKER) || !hasValidWorkingBlocks || rpmModifier <= 0.0f) {
            return 0.0F;
        }
        float magnitude = (float) Config.BASE_CREATURE_RPM.getAsInt() * rpmModifier;
        return magnitude * generationDirection;
    }

    @Override
    public float calculateAddedStressCapacity() {
        BlockState state = getBlockState();
        float speed = getGeneratedSpeed();
        if (speed == 0 || !state.getValue(HAS_WORKER)) return 0;

        float capacity = 0;
        if (state.getValue(SMALL_WORKER_STATE)) capacity = Config.SMALL_CREATURE_STRESS.getAsInt();
        else if (state.getValue(MEDIUM_WORKER_STATE)) capacity = Config.MEDIUM_CREATURE_STRESS.getAsInt();
        else if (state.getValue(LARGE_WORKER_STATE)) capacity = Config.LARGE_CREATURE_STRESS.getAsInt();

        capacity = Math.abs(capacity / Math.abs(speed));
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(this.getBlockPos()).inflate(4.0D);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("RpmModifier", rpmModifier);
        compound.putBoolean("HasValidWorkingBlocks", hasValidWorkingBlocks);
        compound.putFloat("GenerationDirection", generationDirection);
        if (workerUuid != null) {
            compound.putUUID("WorkerUUID", workerUuid);
        }
        if (lastKnownWorkerPos != null) {
            compound.putLong("WorkerPos", lastKnownWorkerPos.asLong());
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("RpmModifier")) rpmModifier = compound.getFloat("RpmModifier");
        if (compound.contains("HasValidWorkingBlocks")) hasValidWorkingBlocks = compound.getBoolean("HasValidWorkingBlocks");
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
    }

    public void attachWorker(Mob worker, CHPUtils.WorkerTier tier) {
        this.cachedWorkerMob = worker;
        this.workerUuid = worker.getUUID();
        this.lastKnownWorkerPos = worker.blockPosition();
        this.missingWorkerTicks = 0;

        float existingSpeed = getTheoreticalSpeed();
        if (existingSpeed != 0) {
            this.generationDirection = Math.signum(existingSpeed);
        } else {
            this.generationDirection = 1.0f;
        }

        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            boolean small = tier == CHPUtils.WorkerTier.SMALL;
            boolean medium = tier == CHPUtils.WorkerTier.MEDIUM;
            boolean large = tier == CHPUtils.WorkerTier.LARGE;
            level.setBlock(worldPosition, state.setValue(HAS_WORKER, true)
                    .setValue(SMALL_WORKER_STATE, small)
                    .setValue(MEDIUM_WORKER_STATE, medium)
                    .setValue(LARGE_WORKER_STATE, large), 3);
            checkPathBlocks();
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    public void detachWorker(boolean dropLead) {
        this.cachedWorkerMob = null;
        this.workerUuid = null;
        this.lastKnownWorkerPos = null;
        this.missingWorkerTicks = 0;

        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            if (state.getValue(HAS_WORKER)) {
                level.setBlock(worldPosition, state.setValue(HAS_WORKER, false)
                        .setValue(SMALL_WORKER_STATE, false)
                        .setValue(MEDIUM_WORKER_STATE, false)
                        .setValue(LARGE_WORKER_STATE, false), 3);
            }
            CHPUtils.cleanUpLeash(level, worldPosition, dropLead);
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    public void onCrankRemoved() {
        detachWorker(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (needsLegacyDirectionResolution) {
            needsLegacyDirectionResolution = false;
            float existingSpeed = getTheoreticalSpeed();
            if (existingSpeed != 0) {
                generationDirection = Math.signum(existingSpeed);
            } else {
                generationDirection = 1.0f;
            }
            updateGeneratedRotation();
        }

        // 1. Reconcile attachment lifecycle
        reconcileWorker();

        // 2. Update path state on interval
        if (level.getGameTime() - lastPathCheckTick >= 20 || lastPathCheckTick < 0) {
            lastPathCheckTick = level.getGameTime();
            checkPathBlocks();
        }

        // 3. Move animal along track if active
        if (getBlockState().getValue(HAS_WORKER) && cachedWorkerMob != null && hasValidWorkingBlocks) {
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
            cachedWorkerMob = null;
            workerUuid = null;
            lastKnownWorkerPos = null;
            missingWorkerTicks = 0;
            return;
        }

        Mob worker = resolveWorker();
        if (worker != null) {
            missingWorkerTicks = 0;
            lastKnownWorkerPos = worker.blockPosition();
            return;
        }

        BlockPos checkPos = lastKnownWorkerPos != null ? lastKnownWorkerPos : worldPosition;
        if (level.hasChunkAt(checkPos)) {
            missingWorkerTicks++;
            if (missingWorkerTicks > 80) {
                detachWorker(true);
            }
        }
    }

    private void checkPathBlocks() {
        if (level == null) return;
        boolean allValid = true;
        int greatCount = 0;
        int normalCount = 0;
        int poorCount = 0;
        int total = OFFSETS.length;

        Set<String> poorConfig = new HashSet<>(Config.POOR_PATH.get());
        Set<String> normalConfig = new HashSet<>(Config.NORMAL_PATH.get());
        Set<String> greatConfig = new HashSet<>(Config.GREAT_PATH.get());

        for (BlockPos offset : OFFSETS) {
            BlockPos checkPos = worldPosition.offset(offset);
            BlockState blockState = level.getBlockState(checkPos);
            if (blockState.isAir()) {
                allValid = false;
                break;
            }
            String blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
            if (greatConfig.contains(blockId)) {
                greatCount++;
            } else if (normalConfig.contains(blockId)) {
                normalCount++;
            } else if (poorConfig.contains(blockId)) {
                poorCount++;
            } else {
                poorCount++;
            }
        }

        float oldModifier = this.rpmModifier;
        boolean oldValid = this.hasValidWorkingBlocks;

        this.hasValidWorkingBlocks = allValid;
        if (!allValid) {
            this.rpmModifier = 0.0f;
        } else if (poorCount > 0) {
            this.rpmModifier = (float) Config.POOR_MULTIPLIER.getAsDouble();
        } else if (greatCount == total) {
            this.rpmModifier = (float) Config.GREAT_MULTIPLIER.getAsDouble();
        } else {
            this.rpmModifier = (float) Config.NORMAL_MULTIPLIER.getAsDouble();
        }

        if (oldModifier != this.rpmModifier || oldValid != this.hasValidWorkingBlocks) {
            if (!level.isClientSide()) {
                updateGeneratedRotation();
                notifyUpdate();
            }
        }
    }

    private void moveWorkerTo(Mob worker) {
        if (worker == null || level == null || level.isClientSide()) {
            return;
        }

        if (worker instanceof Horse horse && horse.isEating()) {
            horse.setEating(false);
        }

        double baseRadius = 3.0;
        double sizeFactor = Math.max(0.8, 1.0 - (worker.getBbWidth() - 0.5));
        double radius = baseRadius * sizeFactor;

        int ticksPerRotation = (int) (20 * 10 * getTickSpeedModifier());

        BlockPos pos = this.worldPosition;
        double bx = pos.getX() + 0.5D;
        double by = pos.getY();
        double bz = pos.getZ() + 0.5D;

        double distanceToWorker = worker.distanceToSqr(pos.getCenter());

        if (distanceToWorker <= (radius * radius) + 20.5) {
            double progress;
            if (ticksPerRotation == 0) {
                progress = 0;
            } else {
                progress = (worker.level().getGameTime() % ticksPerRotation) / (double) ticksPerRotation;
            }

            double direction = Math.signum(getGeneratedSpeed() != 0 ? getGeneratedSpeed() : generationDirection);
            if (direction == 0) direction = 1.0;

            double angle = 2 * Math.PI * progress * direction;
            double xOffset = radius * Math.sin(angle);
            double zOffset = radius * Math.cos(angle);
            double targetX = bx + xOffset;
            double targetZ = bz + zOffset;

            double nextAngle = 2 * Math.PI * (progress + 0.01) * direction;
            double nextX = bx + radius * Math.sin(nextAngle);
            double nextZ = bz + radius * Math.cos(nextAngle);

            worker.teleportTo(targetX, by, targetZ);
            WalkAnimationState animation = worker.walkAnimation;
            animation.update(-animation.position(), 1);
            float movementYaw = calculateYaw(targetX, targetZ, nextX, nextZ);
            worker.setYRot(movementYaw);
            worker.setYHeadRot(movementYaw);
        }
    }

    private float calculateYaw(double currentX, double currentZ, double targetX, double targetZ) {
        double deltaX = targetX - currentX;
        double deltaZ = targetZ - currentZ;
        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX));
        return yaw - 90.0F;
    }

    private double getTickSpeedModifier() {
        return rpmModifier > 0 ? (Math.PI - .14d) / (2 * rpmModifier) : 0;
    }
}
