package cn.zhonjc.quickwriter.client;

import cn.zhonjc.quickwriter.Compat;
import cn.zhonjc.quickwriter.TextDisplays;
import cn.zhonjc.quickwriter.TextStyle;
import cn.zhonjc.quickwriter.network.Payloads;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Editor panel on the left of the screen. Every change is applied to the (client-side) entity immediately, so the
 * world behind the panel is a live preview. Nothing reaches the server until "Done"; closing any other way reverts.
 */
public class TextEditorScreen extends Screen {
    private static final int[] TEXT_COLORS = {
            0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000, 0xFF5555, 0xAA0000, 0xFFAA00, 0xFFFF55,
            0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA, 0x5555FF, 0x0000AA, 0xFF55FF, 0xAA00AA};
    private static final int[] BACKGROUNDS = {
            0x00000000, 0x40000000, 0x80000000, 0xFF000000, 0x80FFFFFF, 0xFFFFFFFF, 0x80AA0000, 0x800000AA};
    private static final int PANEL_W = 220, PANEL_H = 226, PAD = 6, IW = PANEL_W - 2 * PAD;
    private static int nextPreviewId = -0x51A7E;

    private enum Anchor { CLICK, FACE_CENTER }

    /** Exact vanilla state of an existing entity, for reverting a cancelled edit. */
    private record Snapshot(Component text, int lineWidth, int background, byte flags, @Nullable Brightness brightness,
                            Display.BillboardConstraints billboard, Transformation transformation,
                            Vec3 pos, float yaw, float pitch) {
        static Snapshot of(Display.TextDisplay e) {
            return new Snapshot(e.getText(), e.getLineWidth(), e.getBackgroundColor(), e.getFlags(),
                    e.getBrightnessOverride(), e.getBillboardConstraints(), TextDisplays.transformation(e),
                    e.position(), e.getYRot(), e.getXRot());
        }

        void restore(Display.TextDisplay e) {
            e.setText(text);
            e.setLineWidth(lineWidth);
            e.setBackgroundColor(background);
            e.setFlags(flags);
            e.setBrightnessOverride(brightness);
            e.setBillboardConstraints(billboard);
            e.setTransformation(transformation);
            e.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
            e.setOldPosAndRot();
        }
    }

    private final Display.@Nullable TextDisplay existing;
    private final @Nullable Snapshot snapshot;
    private Display.@Nullable TextDisplay preview;
    private final Vec3 clickAnchor, faceCenterAnchor;
    private Anchor anchor = Anchor.CLICK;
    private Vec3 center;
    private float yaw;
    private final float pitch;
    private boolean finished;

    // editable state (kept across re-init on window resize)
    private String text;
    private int color, background, lineWidth, format;
    private float scale;
    private boolean shadow, seeThrough, fullBright;
    private TextStyle.Align align;

    private @Nullable EditBox colorBox, backgroundBox;

    private TextEditorScreen(Component title, Display.@Nullable TextDisplay existing, TextStyle style,
                             Vec3 center, Vec3 faceCenter, float yaw, float pitch) {
        super(title);
        this.existing = existing;
        this.snapshot = existing == null ? null : Snapshot.of(existing);
        this.clickAnchor = center;
        this.faceCenterAnchor = faceCenter;
        this.center = center;
        this.yaw = yaw;
        this.pitch = pitch;
        text = style.text();
        color = style.color();
        background = style.background();
        lineWidth = style.lineWidth();
        format = style.format();
        scale = style.scale();
        shadow = style.shadow();
        seeThrough = style.seeThrough();
        fullBright = style.fullBright();
        align = style.align();
    }

    public static TextEditorScreen create(Vec3 click, Vec3 faceCenter, float yaw, float pitch) {
        return new TextEditorScreen(Component.translatable("screen.quickwriter.create"), null,
                EditController.lastStyle(), click, faceCenter, yaw, pitch);
    }

    public static TextEditorScreen edit(Display.TextDisplay e) {
        Vec3 center = TextGeometry.center(e.position(), e.getYRot(), e.getXRot(), TextDisplays.transformation(e),
                e.getText(), e.getLineWidth());
        return new TextEditorScreen(Component.translatable("screen.quickwriter.edit"), e, TextDisplays.read(e),
                center, center, e.getYRot(), e.getXRot());
    }

    private TextStyle style() {
        return new TextStyle(text, color, background, scale, lineWidth, shadow, seeThrough, fullBright, align, format)
                .sanitized();
    }

    private boolean flat() { return Math.abs(pitch) > 89; }

    // ---- layout ------------------------------------------------------------------------------------------------

    private int left() { return PAD; }
    private int top() { return Math.max(PAD, (height - PANEL_H) / 2); }

    @Override
    protected void init() {
        if (existing == null && preview == null && minecraft.level != null) {
            preview = new Display.TextDisplay(Compat.textDisplayType(), minecraft.level);
            preview.setId(nextPreviewId--);
            minecraft.level.addEntity(preview);
        }
        int x = left() + PAD, y = top() + PAD, half = (IW - 4) / 2, third = (IW - 4) / 3;

        MultiLineEditBox textBox = MultiLineEditBox.builder().setX(x).setY(y + 12)
                .setPlaceholder(Component.translatable("screen.quickwriter.text_hint"))
                .build(font, IW, 44, Component.translatable("screen.quickwriter.text"));
        textBox.setValue(text);
        textBox.setValueListener(v -> { text = v; changed(); });
        addRenderableWidget(textBox);

        colorBox = addRenderableWidget(new EditBox(font, x, y + 60, 64, 16, Component.translatable("screen.quickwriter.color")));
        colorBox.setMaxLength(7);
        colorBox.setValue(String.format("#%06X", color));
        colorBox.setTooltip(Tooltip.create(Component.translatable("screen.quickwriter.color")));
        colorBox.setResponder(v -> { Integer c = parseHex(v, 6); if (c != null) { color = c; changed(); } });
        for (int i = 0; i < TEXT_COLORS.length; i++) {
            int rgb = TEXT_COLORS[i];
            addRenderableWidget(new ColorSwatch(x + 68 + (i % 8) * 14, y + 60 + (i / 8) * 14, 12, 0xFF000000 | rgb,
                    () -> 0xFF000000 | color, argb -> colorBox.setValue(String.format("#%06X", argb & 0xFFFFFF))));
        }

        backgroundBox = addRenderableWidget(new EditBox(font, x, y + 90, 64, 16, Component.translatable("screen.quickwriter.background")));
        backgroundBox.setMaxLength(9);
        backgroundBox.setValue(String.format("#%08X", background));
        backgroundBox.setTooltip(Tooltip.create(Component.translatable("screen.quickwriter.background_tip")));
        backgroundBox.setResponder(v -> { Integer c = parseHex(v, 8); if (c != null) { background = c; changed(); } });
        for (int i = 0; i < BACKGROUNDS.length; i++) {
            addRenderableWidget(new ColorSwatch(x + 68 + i * 14, y + 92, 12, BACKGROUNDS[i],
                    () -> background, argb -> backgroundBox.setValue(String.format("#%08X", argb))));
        }

        // scale: 0.1 … 10 on a log scale
        addRenderableWidget(new ValueSlider(x, y + 110, half, 20, Math.clamp(Math.log(scale / 0.1) / Math.log(100), 0, 1),
                v -> Math.round(0.1 * Math.pow(100, v) * 100) / 100.0,
                v -> Component.translatable("screen.quickwriter.scale", String.format(Locale.ROOT, "%.2f", v)),
                v -> { scale = (float) v; changed(); }));
        addRenderableWidget(new ValueSlider(x + half + 4, y + 110, half, 20, Math.clamp((lineWidth - 20) / 580.0, 0, 1),
                v -> 20 + Math.round(v * 58) * 10,
                v -> Component.translatable("screen.quickwriter.line_width", (int) v),
                v -> { lineWidth = (int) v; changed(); }));

        addRenderableWidget(CycleButton.<TextStyle.Align>builder(
                        a -> Component.translatable("screen.quickwriter.align." + a.name().toLowerCase(Locale.ROOT)), align)
                .withValues(TextStyle.Align.values())
                .create(x, y + 132, half, 20, Component.translatable("screen.quickwriter.align"),
                        (b, v) -> { align = v; changed(); }));
        Button rotate = addRenderableWidget(Button.builder(Component.translatable("screen.quickwriter.rotate"),
                b -> { yaw = TextDisplays.snap90(yaw + 90); changed(); }).bounds(x + half + 4, y + 132, half, 20).build());
        rotate.active = flat();
        rotate.setTooltip(Tooltip.create(Component.translatable(flat()
                ? "screen.quickwriter.rotate_tip" : "screen.quickwriter.rotate_disabled")));

        addToggle(x, y + 154, third, "shadow", shadow, v -> shadow = v);
        addToggle(x + third + 2, y + 154, third, "full_bright", fullBright, v -> fullBright = v);
        addToggle(x + 2 * (third + 2), y + 154, third, "see_through", seeThrough, v -> seeThrough = v);

        String[] letters = {"B", "I", "U", "S"};
        ChatFormatting[] styles = {ChatFormatting.BOLD, ChatFormatting.ITALIC, ChatFormatting.UNDERLINE, ChatFormatting.STRIKETHROUGH};
        String[] keys = {"bold", "italic", "underlined", "strikethrough"};
        for (int i = 0; i < 4; i++) {
            int flag = 1 << i;
            CycleButton<Boolean> b = addRenderableWidget(CycleButton.booleanBuilder(
                            Component.literal(letters[i]).withStyle(styles[i], ChatFormatting.YELLOW),
                            Component.literal(letters[i]).withStyle(styles[i], ChatFormatting.DARK_GRAY), (format & flag) != 0)
                    .displayOnlyValue()
                    .create(x + i * 24, y + 176, 22, 20, Component.translatable("screen.quickwriter." + keys[i]),
                            (btn, v) -> { format = v ? format | flag : format & ~flag; changed(); }));
            b.setTooltip(Tooltip.create(Component.translatable("screen.quickwriter." + keys[i])));
        }
        if (existing == null) {
            addRenderableWidget(CycleButton.<Anchor>builder(
                            a -> Component.translatable("screen.quickwriter.anchor." + a.name().toLowerCase(Locale.ROOT)), anchor)
                    .withValues(Anchor.values())
                    .create(x + 98, y + 176, IW - 98, 20, Component.translatable("screen.quickwriter.anchor"), (b, v) -> {
                        anchor = v;
                        center = v == Anchor.CLICK ? clickAnchor : faceCenterAnchor;
                        changed();
                    }));
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.quickwriter.done"), b -> save())
                .bounds(x, y + 200, third, 20).build());
        Button delete = addRenderableWidget(Button.builder(
                        Component.translatable("screen.quickwriter.delete").withStyle(ChatFormatting.RED), b -> delete())
                .bounds(x + third + 2, y + 200, third, 20).build());
        delete.active = existing != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + 2 * (third + 2), y + 200, third, 20).build());

        setInitialFocus(textBox);
        changed();
    }

    private void addToggle(int x, int y, int w, String key, boolean value, java.util.function.Consumer<Boolean> set) {
        CycleButton<Boolean> b = addRenderableWidget(CycleButton.onOffBuilder(value)
                .create(x, y, w, 20, Component.translatable("screen.quickwriter." + key), (btn, v) -> { set.accept(v); changed(); }));
        b.setTooltip(Tooltip.create(Component.translatable("screen.quickwriter." + key + "_tip")));
    }

    private static @Nullable Integer parseHex(String value, int digits) {
        String v = value.startsWith("#") ? value.substring(1) : value;
        if (v.length() != digits) return null;
        try {
            return (int) Long.parseLong(v, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- live preview / commit ---------------------------------------------------------------------------------

    private Display.@Nullable TextDisplay target() { return existing != null ? existing : preview; }

    private Vec3 origin(TextStyle style) { return TextGeometry.originForCenter(center, yaw, pitch, style); }

    private void changed() {
        Display.TextDisplay e = target();
        if (e == null) return;
        TextStyle style = style();
        TextDisplays.apply(e, style);
        Vec3 o = origin(style);
        e.snapTo(o.x, o.y, o.z, yaw, pitch);
        e.setOldPosAndRot();
    }

    private void save() {
        TextStyle style = style();
        if (existing != null || !style.text().isBlank()) {
            int id = existing != null ? existing.getId() : -1;
            ClientPlayNetworking.send(new Payloads.Upsert(id, origin(style), yaw, pitch, style));
            EditController.rememberStyle(style);
        }
        finished = true;
        removePreview();
        onClose();
    }

    private void delete() {
        if (existing == null) return;
        snapshot.restore(existing); // in case the server refuses
        ClientPlayNetworking.send(new Payloads.Delete(existing.getId()));
        finished = true;
        onClose();
    }

    private void removePreview() {
        if (preview != null && minecraft.level != null) minecraft.level.removeEntity(preview.getId(), Entity.RemovalReason.DISCARDED);
        preview = null;
    }

    @Override
    public void removed() {
        if (!finished) {
            if (existing != null && !existing.isRemoved()) snapshot.restore(existing);
            removePreview();
            finished = true;
        }
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = left(), y = top();
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xC0101018);
        g.outline(x, y, PANEL_W, PANEL_H, 0xFF505060);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.text(font, title, left() + PAD, top() + PAD + 1, 0xFFFFFFFF);
        super.extractRenderState(g, mouseX, mouseY, a);
    }
}
