package cn.zhonjc.quickwriter.client;

import cn.zhonjc.quickwriter.QuickWriter;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;

public class QuickWriterClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(QuickWriter.id("category"));
    static KeyMapping delete;

    @Override
    public void onInitializeClient() {
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.quickwriter.toggle", InputConstants.KEY_G, CATEGORY));
        delete = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.quickwriter.delete", InputConstants.KEY_BACKSPACE, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.consumeClick()) EditController.toggle(mc);
            EditController.tick(mc);
            while (delete.consumeClick()) EditController.deleteHovered(mc);
        });

        HudElementRegistry.addLast(QuickWriter.id("hud"), EditHud::extract);
    }
}
