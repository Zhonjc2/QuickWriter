package cn.zhonjc.quickwriter.client;

import cn.zhonjc.quickwriter.TextDisplays;
import cn.zhonjc.quickwriter.TextStyle;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Client-side geometry of text displays: size of the rendered text, picking with the view ray, centering. */
public final class TextGeometry {
    private static final float PICK_PADDING = 2 * TextDisplays.PIXEL;

    private TextGeometry() {}

    /** Rendered text size in font pixels: {width, height} where height = lines · 10 − 1 (same as the renderer). */
    public static int[] pixelSize(Component text, int lineWidth) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(text, lineWidth);
        int width = 0;
        for (FormattedCharSequence line : lines) width = Math.max(width, font.width(line));
        return new int[]{width, Math.max(1, lines.size()) * TextDisplays.LINE_HEIGHT - 1};
    }

    /** Offset from the entity origin to the visual centre of the background quad, in local (unscaled) blocks. */
    private static Vector3f localCenter(int[] px) {
        return new Vector3f(0.5f * TextDisplays.PIXEL, (px[1] + 1) / 2f * TextDisplays.PIXEL, 0);
    }

    /** World-space centre of the text's background rectangle. */
    public static Vec3 center(Vec3 origin, float yaw, float pitch, Transformation t, Component text, int lineWidth) {
        Vector3f c = TextDisplays.localToWorld(yaw, pitch, t).transformDirection(localCenter(pixelSize(text, lineWidth)));
        return origin.add(c.x, c.y, c.z);
    }

    /** Entity origin that places the text's visual centre at {@code center}. */
    public static Vec3 originForCenter(Vec3 center, float yaw, float pitch, TextStyle style) {
        Transformation t = new Transformation(null, null, new Vector3f(style.scale()), null);
        Vector3f c = TextDisplays.localToWorld(yaw, pitch, t)
                .transformDirection(localCenter(pixelSize(TextDisplays.toComponent(style), style.lineWidth())));
        return center.subtract(c.x, c.y, c.z);
    }

    /** World-space normal of the text plane (the side the text is readable from). */
    public static Vec3 normal(Display.TextDisplay e) {
        Vector3f n = TextDisplays.localToWorld(e.getYRot(), e.getXRot(), TextDisplays.transformation(e))
                .transformDirection(new Vector3f(0, 0, 1)).normalize();
        return new Vec3(n.x, n.y, n.z);
    }

    public record Hit(Display.TextDisplay entity, Vec3 point, double distance) {}

    /** Closest fixed-billboard text display whose rectangle the ray hits within {@code maxDistance}. */
    public static @Nullable Hit pick(Iterable<Entity> entities, Vec3 eye, Vec3 look, double maxDistance) {
        Hit best = null;
        for (Entity entity : entities) {
            if (!(entity instanceof Display.TextDisplay e) || e.isRemoved()) continue;
            if (e.getBillboardConstraints() != Display.BillboardConstraints.FIXED) continue;
            if (e.position().distanceToSqr(eye) > (maxDistance + 16) * (maxDistance + 16)) continue;
            double d = intersect(e, eye, look);
            if (d >= 0 && d <= maxDistance && (best == null || d < best.distance)) {
                best = new Hit(e, eye.add(look.scale(d)), d);
            }
        }
        return best;
    }

    /** Ray parameter (distance along a unit {@code look}) where it hits the text's rectangle, or −1. */
    private static double intersect(Display.TextDisplay e, Vec3 eye, Vec3 look) {
        Matrix4f inv = TextDisplays.localToWorld(e.getYRot(), e.getXRot(), TextDisplays.transformation(e)).invert();
        Vec3 rel = eye.subtract(e.position());
        Vector3f o = inv.transformPosition(new Vector3f((float) rel.x, (float) rel.y, (float) rel.z));
        Vector3f d = inv.transformDirection(new Vector3f((float) look.x, (float) look.y, (float) look.z));
        if (Math.abs(d.z) < 1e-6f) return -1;
        float t = -o.z / d.z;
        if (t < 0) return -1;
        float x = o.x + d.x * t, y = o.y + d.y * t;
        int[] px = pixelSize(e.getText(), e.getLineWidth());
        float p = TextDisplays.PIXEL;
        boolean inside = x >= -px[0] / 2f * p - PICK_PADDING && x <= (px[0] / 2f + 1) * p + PICK_PADDING
                && y >= -PICK_PADDING && y <= (px[1] + 1) * p + PICK_PADDING;
        return inside ? t : -1;
    }
}
