package cn.zhonjc.quickwriter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Client-side API differences between Minecraft versions (Stonecutter conditions). */
public final class ClientCompat {
    private ClientCompat() {}

    public static @Nullable Screen screen(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }

    public static void setScreen(Minecraft mc, @Nullable Screen screen) {
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else {
        /*mc.setScreen(screen);
        *///?}
    }
}
