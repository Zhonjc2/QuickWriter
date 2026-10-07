package cn.zhonjc.quickwriter.client;

import cn.zhonjc.quickwriter.TextDisplays;
import cn.zhonjc.quickwriter.TextStyle;
import cn.zhonjc.quickwriter.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Edit-mode state machine. While edit mode is on, the mod takes over the mouse buttons:
 * right click = create/edit, left drag = slide along the surface, middle click = copy style.
 */
public final class EditController {
    /** How far (blocks) the player can reach blocks and texts while in edit mode. */
    public static final double REACH = 32;
    private static final double GRID = 1 / 16.0;

    private static boolean enabled;
    private static TextStyle lastStyle = TextStyle.DEFAULT;
    private static TextGeometry.@Nullable Hit hover;
    private static @Nullable Drag drag;

    private record Drag(Display.TextDisplay entity, Vec3 start, Vec3 grabPoint, Vec3 normal, Vec3 offset) {}

    private EditController() {}

    public static boolean enabled() { return enabled; }
    public static TextGeometry.@Nullable Hit hover() { return hover; }
    public static boolean dragging() { return drag != null; }
    public static TextStyle lastStyle() { return lastStyle; }
    public static void rememberStyle(TextStyle style) { lastStyle = style.withText(""); }

    public static void toggle(Minecraft mc) {
        cancelDrag();
        enabled = !enabled;
        hover = null;
        if (mc.player == null) return;
        if (enabled && !ClientPlayNetworking.canSend(Payloads.Upsert.TYPE)) {
            enabled = false;
            mc.player.sendOverlayMessage(Component.translatable("message.quickwriter.server_missing"));
            return;
        }
        mc.player.sendOverlayMessage(Component.translatable(
                enabled ? "message.quickwriter.enabled" : "message.quickwriter.disabled"));
    }

    // ---- per tick ----------------------------------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            enabled = false;
            hover = null;
            drag = null;
            return;
        }
        if (!enabled) return;

        if (drag != null) {
            if (drag.entity.isRemoved()) drag = null;
            else if (ClientCompat.screen(mc) != null) cancelDrag();
            else if (!mc.options.keyAttack.isDown()) finishDrag();
            else updateDrag(mc);
        }
        hover = drag != null ? null : pickText(mc);
    }

    private static Vec3 eye(Minecraft mc) { return mc.player.getEyePosition(1f); }
    private static Vec3 look(Minecraft mc) { return mc.player.getViewVector(1f); }

    private static @Nullable BlockHitResult pickBlock(Minecraft mc) {
        Vec3 eye = eye(mc);
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(look(mc).scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** Text under the crosshair, unless a block is in front of it. */
    private static TextGeometry.@Nullable Hit pickText(Minecraft mc) {
        BlockHitResult block = pickBlock(mc);
        double limit = block == null ? REACH : block.getLocation().distanceTo(eye(mc)) + 0.05;
        return TextGeometry.pick(mc.level.entitiesForRendering(), eye(mc), look(mc), limit);
    }

    // ---- mouse handlers (called from MinecraftMixin); return true to swallow the vanilla action --------------

    public static boolean onAttack(Minecraft mc) {
        if (!enabled) return false;
        TextGeometry.Hit hit = pickText(mc);
        if (hit != null) {
            Vec3 start = hit.entity().position();
            drag = new Drag(hit.entity(), start, hit.point(), TextGeometry.normal(hit.entity()), start.subtract(hit.point()));
        }
        return true; // never break blocks while in edit mode
    }

    public static boolean onContinueAttack() {
        return enabled;
    }

    public static boolean onUse(Minecraft mc) {
        if (!enabled) return false;
        if (drag != null) {
            cancelDrag();
            return true;
        }
        TextGeometry.Hit hit = pickText(mc);
        if (hit != null) {
            ClientCompat.setScreen(mc, TextEditorScreen.edit(hit.entity()));
            return true;
        }
        BlockHitResult block = pickBlock(mc);
        if (block != null) {
            Direction face = block.getDirection();
            float[] rot = TextDisplays.rotationFor(face, mc.player.getYRot());
            Vec3 n = face.getUnitVec3();
            Vec3 click = block.getLocation().add(n.scale(TextDisplays.SURFACE_OFFSET));
            // Face centre: middle of the block on the two in-plane axes, the clicked surface on the normal axis.
            Vec3 c = Vec3.atCenterOf(block.getBlockPos());
            Vec3 faceCenter = switch (face.getAxis()) {
                case X -> new Vec3(click.x, c.y, c.z);
                case Y -> new Vec3(c.x, click.y, c.z);
                case Z -> new Vec3(c.x, c.y, click.z);
            };
            ClientCompat.setScreen(mc, TextEditorScreen.create(click, faceCenter, rot[0], rot[1]));
        }
        return true;
    }

    public static boolean onPick(Minecraft mc) {
        if (!enabled) return false;
        TextGeometry.Hit hit = pickText(mc);
        if (hit != null) {
            rememberStyle(TextDisplays.read(hit.entity()));
            mc.player.sendOverlayMessage(Component.translatable("message.quickwriter.style_copied"));
        }
        return true;
    }

    public static void deleteHovered(Minecraft mc) {
        if (!enabled || hover == null) return;
        ClientPlayNetworking.send(new Payloads.Delete(hover.entity().getId()));
        hover = null;
    }

    // ---- dragging ----------------------------------------------------------------------------------------------

    private static void updateDrag(Minecraft mc) {
        Drag d = drag;
        Vec3 eye = eye(mc), look = look(mc);
        double denom = look.dot(d.normal);
        if (Math.abs(denom) < 1e-4) return;
        double t = d.grabPoint.subtract(eye).dot(d.normal) / denom;
        if (t < 0 || t > REACH * 2) return;
        Vec3 pos = eye.add(look.scale(t)).add(d.offset);
        if (mc.hasShiftDown()) pos = snap(pos, d.normal);
        d.entity.setPos(pos);
    }

    /** Rounds the in-plane coordinates to a 1/16 block grid; only for axis-aligned planes. */
    private static Vec3 snap(Vec3 pos, Vec3 normal) {
        double ax = Math.abs(normal.x), ay = Math.abs(normal.y), az = Math.abs(normal.z);
        if (Math.max(ax, Math.max(ay, az)) < 0.999) return pos;
        return new Vec3(ax > 0.5 ? pos.x : round(pos.x), ay > 0.5 ? pos.y : round(pos.y), az > 0.5 ? pos.z : round(pos.z));
    }

    private static double round(double v) { return Math.round(v / GRID) * GRID; }

    private static void finishDrag() {
        Drag d = drag;
        drag = null;
        if (d.entity.position().distanceToSqr(d.start) > 1e-8) {
            ClientPlayNetworking.send(new Payloads.Move(d.entity.getId(), d.entity.position()));
        }
    }

    private static void cancelDrag() {
        if (drag != null) drag.entity.setPos(drag.start);
        drag = null;
    }
}
