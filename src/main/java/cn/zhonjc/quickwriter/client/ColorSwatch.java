package cn.zhonjc.quickwriter.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** A small clickable colour square; transparent colours are drawn over a checkerboard. */
public class ColorSwatch extends AbstractButton {
    private final int argb;
    private final IntConsumer onPick;
    private final IntSupplier current;

    public ColorSwatch(int x, int y, int size, int argb, IntSupplier current, IntConsumer onPick) {
        super(x, y, size, size, Component.literal(String.format("#%08X", argb)));
        this.argb = argb;
        this.current = current;
        this.onPick = onPick;
    }

    @Override public void onPress(InputWithModifiers input) { onPick.accept(argb); }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), s = getWidth();
        boolean selected = current.getAsInt() == argb;
        g.fill(x, y, x + s, y + s, selected ? 0xFFFFFFFF : isHoveredOrFocused() ? 0xFFA0A0A0 : 0xFF000000);
        int h = s / 2;
        g.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + h, y + h, 0xFFBFBFBF);
        g.fill(x + h, y + h, x + s - 1, y + s - 1, 0xFFBFBFBF);
        g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
