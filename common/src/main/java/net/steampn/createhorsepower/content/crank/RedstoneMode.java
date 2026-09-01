package net.steampn.createhorsepower.content.crank;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum RedstoneMode implements StringRepresentable {
    IGNORE("ignore", "tooltip.createhorsepower.redstone_mode.ignore"),
    HIGH_STOPS("high_stops", "tooltip.createhorsepower.redstone_mode.high_stops"),
    HIGH_RUNS("high_runs", "tooltip.createhorsepower.redstone_mode.high_runs");

    private final String name;
    private final String translationKey;

    RedstoneMode(String name, String translationKey) {
        this.name = name;
        this.translationKey = translationKey;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public RedstoneMode next() {
        RedstoneMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
