package cn.zhonjc.quickwriter;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything the editor can change on a text display, independent of where it sits. */
public record TextStyle(String text, int color, int background, float scale, int lineWidth,
                        boolean shadow, boolean seeThrough, boolean fullBright, Align align, int format) {
    public static final int MAX_TEXT_LENGTH = 1024;
    public static final float MIN_SCALE = 0.05f, MAX_SCALE = 16f;

    public static final int BOLD = 1, ITALIC = 2, UNDERLINED = 4, STRIKETHROUGH = 8;

    public static final TextStyle DEFAULT = new TextStyle("", 0xFFFFFF, 0, 1f, 200,
            true, false, true, Align.CENTER, 0);

    public enum Align { CENTER, LEFT, RIGHT }

    public static final StreamCodec<RegistryFriendlyByteBuf, TextStyle> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeUtf(s.text, MAX_TEXT_LENGTH * 4);
                buf.writeInt(s.color);
                buf.writeInt(s.background);
                buf.writeFloat(s.scale);
                buf.writeVarInt(s.lineWidth);
                buf.writeBoolean(s.shadow);
                buf.writeBoolean(s.seeThrough);
                buf.writeBoolean(s.fullBright);
                buf.writeEnum(s.align);
                buf.writeVarInt(s.format);
            },
            buf -> new TextStyle(buf.readUtf(MAX_TEXT_LENGTH * 4), buf.readInt(), buf.readInt(), buf.readFloat(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readEnum(Align.class), buf.readVarInt()));

    public boolean has(int flag) { return (format & flag) != 0; }

    public TextStyle withText(String value) {
        return new TextStyle(value, color, background, scale, lineWidth, shadow, seeThrough, fullBright, align, format);
    }

    /** Clamps values coming from the network or user input into a safe range. */
    public TextStyle sanitized() {
        String t = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
        float s = Float.isFinite(scale) ? Math.clamp(scale, MIN_SCALE, MAX_SCALE) : 1f;
        return new TextStyle(t, color & 0xFFFFFF, background, s, Math.clamp(lineWidth, 10, 2000),
                shadow, seeThrough, fullBright, align, format & 0xF);
    }
}
