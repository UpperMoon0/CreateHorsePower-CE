package net.steampn.createhorsepower.content.attachment;

public enum AttachmentMode {
    VANILLA_LEASH("vanilla_leash"),
    HARNESS("harness"),
    YOKE("yoke"),
    VIRTUAL_TETHER("virtual_tether");

    private final String serializedName;
    AttachmentMode(String serializedName) { this.serializedName = serializedName; }
    public String serializedName() { return serializedName; }

    public static AttachmentMode parse(String value) {
        for (AttachmentMode mode : values()) {
            if (mode.serializedName.equals(value)) return mode;
        }
        throw new IllegalArgumentException("Unknown attachment mode: " + value);
    }
}
