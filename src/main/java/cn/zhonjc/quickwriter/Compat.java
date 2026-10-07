package cn.zhonjc.quickwriter;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;

/** Common-side API differences between Minecraft versions (Stonecutter conditions). */
public final class Compat {
    private Compat() {}

    public static EntityType<Display.TextDisplay> textDisplayType() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY;
        //?} else {
        /*return EntityType.TEXT_DISPLAY;
        *///?}
    }
}
