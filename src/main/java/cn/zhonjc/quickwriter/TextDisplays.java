package cn.zhonjc.quickwriter;

import cn.zhonjc.quickwriter.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reading/writing {@link Display.TextDisplay} state, plus the geometry the vanilla renderer uses.
 *
 * <p>Renderer facts (DisplayRenderer.TextDisplayRenderer, billboard FIXED):
 * orientation = rotationYXZ(-yaw, pitch, 0), then the entity transformation; the text's readable side faces
 * local +Z, local +X is the reader's right, and the background quad spans
 * x ∈ [-w/2, w/2 + 1] · 0.025, y ∈ [0, h + 1] · 0.025 where w = widest line (px) and h = lines · 10 − 1.
 */
public final class TextDisplays {
    public static final String TAG = "quickwriter";
    /** Distance between the block surface and the text, to avoid z-fighting. */
    public static final double SURFACE_OFFSET = 0.01;
    public static final float PIXEL = 0.025f;
    public static final int LINE_HEIGHT = 10;

    private TextDisplays() {}

    // ---- style -------------------------------------------------------------------------------------------------

    public static Component toComponent(TextStyle s) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(s.color()));
        if (s.has(TextStyle.BOLD)) style = style.withBold(true);
        if (s.has(TextStyle.ITALIC)) style = style.withItalic(true);
        if (s.has(TextStyle.UNDERLINED)) style = style.withUnderlined(true);
        if (s.has(TextStyle.STRIKETHROUGH)) style = style.withStrikethrough(true);
        return Component.literal(s.text()).withStyle(style);
    }

    public static void apply(Display.TextDisplay e, TextStyle s) {
        e.setText(toComponent(s));
        e.setLineWidth(s.lineWidth());
        e.setBackgroundColor(s.background());
        byte flags = 0;
        if (s.shadow()) flags |= Display.TextDisplay.FLAG_SHADOW;
        if (s.seeThrough()) flags |= Display.TextDisplay.FLAG_SEE_THROUGH;
        if (s.align() == TextStyle.Align.LEFT) flags |= Display.TextDisplay.FLAG_ALIGN_LEFT;
        if (s.align() == TextStyle.Align.RIGHT) flags |= Display.TextDisplay.FLAG_ALIGN_RIGHT;
        e.setFlags(flags);
        e.setBrightnessOverride(s.fullBright() ? Brightness.FULL_BRIGHT : null);
        e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        Transformation old = transformation(e);
        e.setTransformation(new Transformation(old.translation(), old.leftRotation(),
                new Vector3f(s.scale(), s.scale(), s.scale()), old.rightRotation()));
    }

    public static TextStyle read(Display.TextDisplay e) {
        Component text = e.getText();
        Style style = text.getStyle();
        TextColor color = style.getColor();
        int format = (style.isBold() ? TextStyle.BOLD : 0) | (style.isItalic() ? TextStyle.ITALIC : 0)
                | (style.isUnderlined() ? TextStyle.UNDERLINED : 0)
                | (style.isStrikethrough() ? TextStyle.STRIKETHROUGH : 0);
        byte flags = e.getFlags();
        int background = (flags & Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND) != 0
                ? Display.TextDisplay.INITIAL_BACKGROUND : e.getBackgroundColor();
        TextStyle.Align align = switch (Display.TextDisplay.getAlign(flags)) {
            case LEFT -> TextStyle.Align.LEFT;
            case RIGHT -> TextStyle.Align.RIGHT;
            case CENTER -> TextStyle.Align.CENTER;
        };
        return new TextStyle(text.getString(), color == null ? 0xFFFFFF : color.getValue(), background,
                transformation(e).scale().y(), e.getLineWidth(),
                (flags & Display.TextDisplay.FLAG_SHADOW) != 0, (flags & Display.TextDisplay.FLAG_SEE_THROUGH) != 0,
                e.getBrightnessOverride() != null, align, format);
    }

    public static Transformation transformation(Display e) {
        return DisplayAccessor.quickwriter$createTransformation(e.getEntityData());
    }

    // ---- geometry ----------------------------------------------------------------------------------------------

    /** Model matrix from text-display local space (blocks, before the 0.025 px scale) to entity-relative world space. */
    public static Matrix4f localToWorld(float yaw, float pitch, Transformation t) {
        return new Matrix4f()
                .rotate(new Quaternionf().rotationYXZ(-yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD, 0))
                .mul(t.getMatrix());
    }

    /** Yaw/pitch that makes the text lie flat on {@code face}, readable from outside the block. */
    public static float[] rotationFor(Direction face, float playerYaw) {
        return switch (face) {
            case SOUTH -> new float[]{0, 0};
            case WEST -> new float[]{90, 0};
            case NORTH -> new float[]{180, 0};
            case EAST -> new float[]{-90, 0};
            // Floors: the text's top points away from the player (the top of the view when looking down).
            // Ceilings: it points back toward the player (the top of the view when tilting the head back).
            case UP, DOWN -> new float[]{snap90(playerYaw + 180), face == Direction.UP ? -90 : 90};
        };
    }

    public static float snap90(float yaw) {
        return Mth.wrapDegrees(Math.round(yaw / 90f) * 90f);
    }

    /** World-space vector of the text's local +Y (its "up"), including scale. */
    public static Vec3 up(float yaw, float pitch, Transformation t) {
        Vector3f v = localToWorld(yaw, pitch, t).transformDirection(new Vector3f(0, 1, 0));
        return new Vec3(v.x, v.y, v.z);
    }
}
