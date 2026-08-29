package net.steampn.createhorsepower.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.CreateHorsePower;
import net.steampn.createhorsepower.blocks.horse_crank.HorseCrankBlock;
import net.steampn.createhorsepower.blocks.horse_crank.HorseCrankTileEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum HorseCrankJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = CreateHorsePower.asResource("horse_crank_info");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof HorseCrankTileEntity crank)) return;
        boolean hasWorker = accessor.getBlockState().getValue(HorseCrankBlock.HAS_WORKER);
        data.putBoolean("HasWorker", hasWorker);
        data.putBoolean("StoppedRedstone", crank.isStoppedByRedstone());
        data.putBoolean("ValidPath", crank.hasValidWorkingBlocks);
        data.putString("WorkerName", crank.getCachedWorkerName());
        data.putFloat("SpeedBonus", crank.getSpeedBonusPercent());
        data.putFloat("HealthBonus", crank.getHealthBonusPercent());
        data.putInt("Efficiency", (int) crank.getEfficiencyPercent());
        data.putInt("InvalidBlocks", crank.getInvalidBlockCount());
        data.putString("RedstoneMode", crank.getRedstoneMode().getDisplayName().getString());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("HasWorker")) return;

        boolean hasWorker = data.getBoolean("HasWorker");
        if (!hasWorker) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.no_worker").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (data.getBoolean("StoppedRedstone")) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.stopped_redstone").withStyle(ChatFormatting.RED));
        } else if (!data.getBoolean("ValidPath")) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.invalid_path", data.getInt("InvalidBlocks")).withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.status.working").withStyle(ChatFormatting.GREEN));
        }

        String workerName = data.getString("WorkerName");
        if (!workerName.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.worker", workerName).withStyle(ChatFormatting.WHITE));
        }

        int eff = data.getInt("Efficiency");
        tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.path_efficiency", eff + "%").withStyle(ChatFormatting.GRAY));

        float speedBonus = data.getFloat("SpeedBonus");
        if (speedBonus != 0) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.speed_bonus", String.format("%+.1f%%", speedBonus)).withStyle(ChatFormatting.AQUA));
        }
        float healthBonus = data.getFloat("HealthBonus");
        if (healthBonus != 0) {
            tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.health_bonus", String.format("%+.1f%%", healthBonus)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        tooltip.add(Component.translatable("tooltip.createhorsepower.goggles.redstone_mode", data.getString("RedstoneMode")).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
