package cn.zhonjc.quickwriter.network;

import cn.zhonjc.quickwriter.QuickWriter;
import cn.zhonjc.quickwriter.TextStyle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public final class Payloads {
    private Payloads() {}

    /** Creates a text display ({@code entityId == -1}) or rewrites an existing one. */
    public record Upsert(int entityId, Vec3 pos, float yaw, float pitch, TextStyle style) implements CustomPacketPayload {
        public static final Type<Upsert> TYPE = new Type<>(QuickWriter.id("upsert"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Upsert> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Upsert::entityId,
                Vec3.STREAM_CODEC, Upsert::pos,
                ByteBufCodecs.FLOAT, Upsert::yaw,
                ByteBufCodecs.FLOAT, Upsert::pitch,
                TextStyle.STREAM_CODEC, Upsert::style,
                Upsert::new);

        @Override public Type<Upsert> type() { return TYPE; }
    }

    /** Moves an existing text display (used when a drag is released). */
    public record Move(int entityId, Vec3 pos) implements CustomPacketPayload {
        public static final Type<Move> TYPE = new Type<>(QuickWriter.id("move"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Move> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Move::entityId,
                Vec3.STREAM_CODEC, Move::pos,
                Move::new);

        @Override public Type<Move> type() { return TYPE; }
    }

    public record Delete(int entityId) implements CustomPacketPayload {
        public static final Type<Delete> TYPE = new Type<>(QuickWriter.id("delete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Delete::entityId,
                Delete::new);

        @Override public Type<Delete> type() { return TYPE; }
    }
}
