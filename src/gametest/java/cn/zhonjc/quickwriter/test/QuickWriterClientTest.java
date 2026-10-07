package cn.zhonjc.quickwriter.test;

import cn.zhonjc.quickwriter.TextDisplays;
import cn.zhonjc.quickwriter.client.EditController;
import cn.zhonjc.quickwriter.client.TextEditorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end test driving the real client: toggle edit mode, right-click faces, type, drag, edit, delete.
 * Screenshots land in build/run/clientGameTest/screenshots for visual inspection.
 */
public class QuickWriterClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            TestInput input = ctx.getInput();

            BlockPos p = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
            int x = p.getX(), y = p.getY(), z = p.getZ();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            // 3x3x3 stone cube south of the player, plus an overhang (ceiling) to the west.
            server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x - 1, y, z + 4, x + 1, y + 2, z + 6));
            server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_planks", x - 6, y + 3, z + 3, x - 3, y + 3, z + 6));
            server.runCommand(String.format("tp @a %.1f %d %.1f 0 0", x + 0.5, y, z + 0.5));
            ctx.waitTicks(20);

            // --- 1. enable edit mode with the key binding and write on the north face -----------------------------
            input.pressKey(GLFW.GLFW_KEY_G);
            ctx.waitTick();
            check(ctx.computeOnClient(mc -> EditController.enabled()), "edit mode should be on");
            input.lookAt(0, 10);
            ctx.waitTicks(2);
            ctx.takeScreenshot("01_edit_mode_hud");
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitForScreen(TextEditorScreen.class);
            input.typeChars("Hello");
            input.pressKey(GLFW.GLFW_KEY_ENTER);
            input.typeChars("QuickWriter 你好");
            ctx.waitTicks(2);
            ctx.takeScreenshot("02_editor_live_preview");
            ctx.clickScreenButton("screen.quickwriter.done");
            ctx.waitForScreen(null);
            sp.getConnection().waitForServerboundPackets();
            ctx.waitTicks(5);

            List<Display.TextDisplay> texts = texts(server);
            check(texts.size() == 1, "expected 1 text display, got " + texts.size());
            Display.TextDisplay north = texts.getFirst();
            check(north.getText().getString().equals("Hello\nQuickWriter 你好"), "text was " + north.getText().getString());
            check(Math.abs(north.getYRot() - 180) < 0.01 || Math.abs(north.getYRot() + 180) < 0.01, "north face yaw " + north.getYRot());
            check(Math.abs(north.getZ() - (z + 4 - TextDisplays.SURFACE_OFFSET)) < 1e-6, "north face z " + north.getZ());
            Vec3 before = north.position();
            ctx.takeScreenshot("03_placed_north");

            // --- 2. drag it to the side; it must stay on the face plane ------------------------------------------
            input.lookAt(0, 10);
            ctx.waitTicks(2);
            check(ctx.computeOnClient(mc -> EditController.hover() != null), "text should be hovered");
            input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(2);
            for (int i = 1; i <= 8; i++) {
                input.lookAt(i * 1.5f, 10 - i * 0.5f);
                ctx.waitTick();
            }
            ctx.takeScreenshot("04_dragging");
            input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(3);
            sp.getConnection().waitForServerboundPackets();
            ctx.waitTicks(3);
            Vec3 after = texts(server).getFirst().position();
            check(Math.abs(after.z - before.z) < 1e-6, "drag left the face plane: " + before + " -> " + after);
            check(after.x < before.x - 0.2 && after.y > before.y + 0.05, "drag did not move west/up: " + before + " -> " + after);
            check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(x, y, z + 4)).isSolid()), "block was broken");

            // --- 3. top face: written from above, top of the text points away from the player -------------------
            server.runCommand(String.format("tp @a %.1f %d %.1f 0 0", x + 0.5, y + 3, z + 4.5)); // stand on the cube
            ctx.waitTicks(5);
            input.lookAt(new BlockPos(x, y + 2, z + 5));
            ctx.waitTicks(2);
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitForScreen(TextEditorScreen.class);
            input.typeChars("TOP");
            ctx.clickScreenButton("screen.quickwriter.done");
            ctx.waitForScreen(null);
            sp.getConnection().waitForServerboundPackets();
            ctx.waitTicks(5);
            Display.TextDisplay top = byText(server, "TOP");
            check(Math.abs(top.getXRot() + 90) < 0.01, "top face pitch " + top.getXRot());
            check(Math.abs(top.getY() - (y + 3 + TextDisplays.SURFACE_OFFSET)) < 1e-6, "top face y " + top.getY());
            System.out.println("[QuickWriterTest] TOP at " + top.position() + " yaw " + top.getYRot());
            server.runCommand(String.format("tp @a %.2f %d %.2f 0 90", top.getX(), y + 6, top.getZ() - 1.2));
            ctx.waitTicks(10);
            ctx.takeScreenshot("05a_top_from_above");

            // --- 4. ceiling (bottom face) and the east face ------------------------------------------------------
            server.runCommand(String.format("tp @a %.1f %d %.1f 90 0", x - 3.5, y, z + 4.5));
            ctx.waitTicks(5);
            input.lookAt(new BlockPos(x - 5, y + 3, z + 4));
            ctx.waitTicks(2);
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitForScreen(TextEditorScreen.class);
            input.typeChars("CEILING");
            ctx.clickScreenButton("screen.quickwriter.done");
            ctx.waitForScreen(null);

            server.runCommand(String.format("tp @a %.1f %d %.1f 90 0", x + 4.5, y, z + 5.5));
            ctx.waitTicks(5);
            input.lookAt(90, 0);
            ctx.waitTicks(2);
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitForScreen(TextEditorScreen.class);
            input.typeChars("EAST");
            ctx.clickScreenButton("screen.quickwriter.done");
            ctx.waitForScreen(null);
            sp.getConnection().waitForServerboundPackets();
            ctx.waitTicks(5);
            check(Math.abs(byText(server, "CEILING").getXRot() - 90) < 0.01, "ceiling pitch");
            check(Math.abs(byText(server, "EAST").getYRot() + 90) < 0.01, "east yaw " + byText(server, "EAST").getYRot());
            ctx.takeScreenshot("05_east_face");

            // --- 5. edit the east text: change colour via swatch, toggle bold ------------------------------------
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitForScreen(TextEditorScreen.class);
            ctx.runOnClient(mc -> {
                var screen = (TextEditorScreen) mc.gui.screen();
                for (var child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.EditBox box && box.getValue().startsWith("#") && box.getValue().length() == 7) {
                        box.setValue("#FF5555");
                    }
                }
            });
            ctx.clickScreenButton("screen.quickwriter.done");
            ctx.waitForScreen(null);
            sp.getConnection().waitForServerboundPackets();
            ctx.waitTicks(5);
            int color = byText(server, "EAST").getText().getStyle().getColor().getValue();
            check(color == 0xFF5555, "colour after edit: " + Integer.toHexString(color));

            // --- 6. cancelling an edit reverts the live preview ---------------------------------------------------
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitForScreen(TextEditorScreen.class);
            input.typeChars("XYZ");
            ctx.waitTicks(2);
            input.pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitForScreen(null);
            String clientText = ctx.computeOnClient(mc -> {
                for (Entity e : mc.level.entitiesForRendering())
                    if (e instanceof Display.TextDisplay td && td.getText().getString().startsWith("EAST")) return td.getText().getString();
                return "<missing>";
            });
            check(clientText.equals("EAST"), "cancel did not revert, client shows " + clientText);

            // --- overview screenshots ------------------------------------------------------------------------------
            server.runCommand(String.format("tp @a %.1f %d %.1f -30 25", x - 3.5, y + 4, z - 0.5));
            ctx.waitTicks(10);
            ctx.takeScreenshot("06_overview_north_top");
            server.runCommand(String.format("tp @a %.1f %d %.1f 135 15", x + 5.5, y + 2, z + 1.5));
            ctx.waitTicks(10);
            ctx.takeScreenshot("07_overview_east");
            server.runCommand(String.format("tp @a %.1f %d %.1f 90 -45", x - 1.5, y, z + 4.5));
            ctx.waitTicks(10);
            ctx.takeScreenshot("08_ceiling");

            // --- 7. delete with the Backspace key ---------------------------------------------------------------------
            server.runCommand(String.format("tp @a %.1f %d %.1f 90 0", x + 4.5, y, z + 5.5));
            ctx.waitTicks(5);
            input.lookAt(90, 0);
            ctx.waitTicks(2);
            input.pressKey(GLFW.GLFW_KEY_BACKSPACE);
            sp.getConnection().waitForServerboundPackets();
            ctx.waitTicks(5);
            check(texts(server).stream().noneMatch(t -> t.getText().getString().equals("EAST")), "EAST was not deleted");
            check(texts(server).size() == 3, "expected 3 texts left, got " + texts(server).size());

            input.pressKey(GLFW.GLFW_KEY_G);
            ctx.waitTick();
            check(!ctx.computeOnClient(mc -> EditController.enabled()), "edit mode should be off");
        }
    }

    private static List<Display.TextDisplay> texts(TestServerContext server) {
        return server.computeOnServer(s -> {
            List<Display.TextDisplay> out = new ArrayList<>();
            for (Entity e : s.overworld().getAllEntities())
                if (e instanceof Display.TextDisplay td && td.entityTags().contains(TextDisplays.TAG)) out.add(td);
            return out;
        });
    }

    private static Display.TextDisplay byText(TestServerContext server, String text) {
        return texts(server).stream().filter(t -> t.getText().getString().equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("no text display with text " + text));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
