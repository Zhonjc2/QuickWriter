package cn.zhonjc.quickwriter;

import cn.zhonjc.quickwriter.network.Payloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class QuickWriter implements ModInitializer {
    public static final String MOD_ID = "quickwriter";
    /** Max distance (blocks) between the player's eyes and a text display they create, move or edit. */
    public static final double MAX_REACH = 64;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Upsert.TYPE, Payloads.Upsert.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Move.TYPE, Payloads.Move.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Delete.TYPE, Payloads.Delete.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payloads.Upsert.TYPE, (p, ctx) -> upsert(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Move.TYPE, (p, ctx) -> move(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Delete.TYPE, (p, ctx) -> delete(ctx.player(), p));
    }

    /** Creative players, operators and the single-player host may edit text displays. */
    public static boolean mayEdit(ServerPlayer player) {
        return player.isCreative()
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || player.level().getServer().isSingleplayerOwner(player.nameAndId());
    }

    private static boolean inReach(ServerPlayer player, Vec3 pos) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && player.getEyePosition().distanceToSqr(pos) <= MAX_REACH * MAX_REACH;
    }

    private static boolean allowed(ServerPlayer player, Vec3 pos) {
        if (!mayEdit(player)) {
            player.sendOverlayMessage(Component.translatable("message.quickwriter.no_permission"));
            return false;
        }
        return inReach(player, pos);
    }

    private static Display.@Nullable TextDisplay find(ServerPlayer player, int id) {
        Entity e = player.level().getEntity(id);
        return e instanceof Display.TextDisplay td && !td.isRemoved() ? td : null;
    }

    private static void upsert(ServerPlayer player, Payloads.Upsert p) {
        if (!allowed(player, p.pos())) return;
        TextStyle style = p.style().sanitized();
        ServerLevel level = player.level();
        float yaw = Float.isFinite(p.yaw()) ? p.yaw() : 0, pitch = Float.isFinite(p.pitch()) ? Math.clamp(p.pitch(), -90, 90) : 0;
        if (p.entityId() < 0) {
            if (style.text().isBlank()) return;
            Display.TextDisplay e = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
            if (e == null) return;
            e.snapTo(p.pos().x, p.pos().y, p.pos().z, yaw, pitch);
            e.addTag(TextDisplays.TAG);
            TextDisplays.apply(e, style);
            level.addFreshEntity(e);
        } else {
            Display.TextDisplay e = find(player, p.entityId());
            if (e == null || !inReach(player, e.position())) return;
            e.snapTo(p.pos().x, p.pos().y, p.pos().z, yaw, pitch);
            TextDisplays.apply(e, style);
        }
    }

    private static void move(ServerPlayer player, Payloads.Move p) {
        if (!allowed(player, p.pos())) return;
        Display.TextDisplay e = find(player, p.entityId());
        if (e == null || !inReach(player, e.position())) return;
        e.setPos(p.pos());
    }

    private static void delete(ServerPlayer player, Payloads.Delete p) {
        Display.TextDisplay e = find(player, p.entityId());
        if (e == null || !allowed(player, e.position())) return;
        e.discard();
    }
}
