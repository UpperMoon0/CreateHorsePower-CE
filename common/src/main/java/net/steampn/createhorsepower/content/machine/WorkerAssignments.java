package net.steampn.createhorsepower.content.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded, loader-neutral worker assignment state for reusable animal-power machines. */
public final class WorkerAssignments {
    public record Entry(UUID workerUuid, @Nullable BlockPos lastKnownPos) {}

    private final int maxWorkers;
    private final List<Entry> entries = new ArrayList<>();

    public WorkerAssignments(int maxWorkers) {
        if (maxWorkers < 1) throw new IllegalArgumentException("maxWorkers must be >= 1");
        this.maxWorkers = maxWorkers;
    }

    public int maxWorkers() { return maxWorkers; }
    public List<Entry> entries() { return List.copyOf(entries); }
    public boolean isFull() { return entries.size() >= maxWorkers; }
    public boolean contains(UUID uuid) { return entries.stream().anyMatch(e -> e.workerUuid().equals(uuid)); }
    @Nullable public Entry primary() { return entries.isEmpty() ? null : entries.get(0); }

    public void assign(UUID uuid, @Nullable BlockPos pos) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).workerUuid().equals(uuid)) {
                entries.set(i, new Entry(uuid, pos));
                return;
            }
        }
        if (isFull()) throw new IllegalStateException("machine already has maxWorkers=" + maxWorkers);
        entries.add(new Entry(uuid, pos));
    }

    public void clear() { entries.clear(); }

    public void write(CompoundTag parent) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", entry.workerUuid());
            if (entry.lastKnownPos() != null) tag.putLong("Pos", entry.lastKnownPos().asLong());
            list.add(tag);
        }
        parent.put("AnimalPowerWorkers", list);
    }

    public boolean read(CompoundTag parent) {
        entries.clear();
        if (!parent.contains("AnimalPowerWorkers", Tag.TAG_LIST)) return false;
        ListTag list = parent.getList("AnimalPowerWorkers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && entries.size() < maxWorkers; i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.hasUUID("UUID")) continue;
            entries.add(new Entry(tag.getUUID("UUID"), tag.contains("Pos") ? BlockPos.of(tag.getLong("Pos")) : null));
        }
        return true;
    }
}
