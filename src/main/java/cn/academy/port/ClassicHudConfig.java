package cn.academy.port;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common-safe config holder: does not reference or initialize Minecraft/client renderer classes. */
public final class ClassicHudConfig {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    static {BUILDER.push("gui");}
    public static final ModConfigSpec.DoubleValue CP_X=BUILDER.comment("Classic gui.cpbar X, right aligned, GUI-scaled pixels.").defineInRange("cpbarX",-12d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue CP_Y=BUILDER.comment("Classic gui.cpbar Y, top aligned, GUI-scaled pixels.").defineInRange("cpbarY",12d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue KEY_X=BUILDER.comment("Classic gui.keyhint X, right aligned, GUI-scaled pixels.").defineInRange("keyhintX",0d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue KEY_Y=BUILDER.comment("Classic gui.keyhint Y, vertically centered, GUI-scaled pixels.").defineInRange("keyhintY",30d,-100000d,100000d);
    public static final ModConfigSpec.ConfigValue<String> FONT=BUILDER.comment("Classic system font preference; original CJK fallback sequence is retained. No proprietary font is bundled.").define("font","Microsoft YaHei");
    public static final ModConfigSpec.DoubleValue NOTIFICATION_X=BUILDER.comment("Classic gui.notification X, left aligned.").defineInRange("notificationX",0d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue NOTIFICATION_Y=BUILDER.comment("Classic gui.notification Y, top aligned.").defineInRange("notificationY",15d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue MEDIA_X=BUILDER.comment("Classic gui.media X, right aligned offset.").defineInRange("mediaX",-6d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue MEDIA_Y=BUILDER.comment("Classic gui.media Y, bottom aligned offset.").defineInRange("mediaY",-6d,-100000d,100000d);
    static {BUILDER.pop();BUILDER.push("generic");}
    public static final ModConfigSpec.BooleanValue HEADS_OR_TAILS=BUILDER.comment("Classic client generic.headsOrTails: show a random result after a coin returns naturally.").define("headsOrTails",false);
    static {BUILDER.pop();}
    public static final ModConfigSpec SPEC=BUILDER.build();
    private ClassicHudConfig(){}
}
