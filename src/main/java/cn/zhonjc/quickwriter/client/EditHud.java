package cn.zhonjc.quickwriter.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Edit-mode hints in the top-left corner, plus what the crosshair is pointing at. */
final class EditHud {
    private static final int MAX_PREVIEW = 24;

    private EditHud() {}

    static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!EditController.enabled() || mc.gui.screen() != null) return;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.quickwriter.title").withStyle(ChatFormatting.GOLD));
        if (EditController.dragging()) {
            lines.add(Component.translatable("hud.quickwriter.dragging"));
        } else {
            TextGeometry.Hit hover = EditController.hover();
            if (hover != null) {
                String s = hover.entity().getText().getString().replace('\n', ' ');
                if (s.length() > MAX_PREVIEW) s = s.substring(0, MAX_PREVIEW) + "…";
                lines.add(Component.translatable("hud.quickwriter.hover", s).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("hud.quickwriter.hint_text", QuickWriterClient.delete.getTranslatedKeyMessage()));
            } else {
                lines.add(Component.translatable("hud.quickwriter.hint_block"));
            }
        }

        int y = 4;
        for (Component line : lines) {
            int w = mc.font.width(line);
            g.fill(2, y - 2, 6 + w, y + 10, 0x90000000);
            g.text(mc.font, line, 4, y, 0xFFFFFFFF);
            y += 12;
        }
    }
}
