package remoteio.client.helper;

import java.lang.reflect.Field;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

/**
 * @author dmillerw
 */
public class IORenderHelper {

    // "isDrawing" is the MCP-deobfuscated field name used at runtime in the GTNH modpack.
    private static volatile Field tessellatorIsDrawingField;

    /**
     * Returns {@code true} if the global {@link Tessellator} is currently in a drawing session.
     * <p>
     * Some rendering pipelines (e.g. Angelica / Embeddium's CeleritasWorldRenderer) call TESRs while the tessellator is
     * already active. Callers that need to start their own batch must check this first and flush the active batch
     * before calling {@link Tessellator#startDrawingQuads()}.
     */
    public static boolean isTessellatorDrawing() {
        // Double-checked locking: read into a local first to avoid repeated volatile reads.
        Field f = tessellatorIsDrawingField;
        if (f == null) {
            synchronized (IORenderHelper.class) {
                f = tessellatorIsDrawingField;
                if (f == null) {
                    try {
                        f = Tessellator.class.getDeclaredField("isDrawing");
                        f.setAccessible(true);
                    } catch (Exception ignored) {
                        return false;
                    }
                    tessellatorIsDrawingField = f;
                }
            }
        }
        try {
            return (boolean) f.get(Tessellator.instance);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void renderCube(IIcon icon) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        // TOP
        tessellator.addVertexWithUV(0, 1.001, 0, icon.getMinU(), icon.getMinV());
        tessellator.addVertexWithUV(0, 1.001, 1, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(1, 1.001, 1, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(1, 1.001, 0, icon.getMaxU(), icon.getMinV());

        // BOTTOM
        tessellator.addVertexWithUV(1, -0.001, 0, icon.getMaxU(), icon.getMinV());
        tessellator.addVertexWithUV(1, -0.001, 1, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(0, -0.001, 1, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(0, -0.001, 0, icon.getMinU(), icon.getMinV());

        // NORTH
        tessellator.addVertexWithUV(0, 1, -0.001, icon.getMaxU(), icon.getMinV());
        tessellator.addVertexWithUV(1, 1, -0.001, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(1, 0, -0.001, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(0, 0, -0.001, icon.getMinU(), icon.getMinV());

        // SOUTH
        tessellator.addVertexWithUV(0, 0, 1.001, icon.getMinU(), icon.getMinV());
        tessellator.addVertexWithUV(1, 0, 1.001, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(1, 1, 1.001, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(0, 1, 1.001, icon.getMaxU(), icon.getMinV());

        // EAST
        tessellator.addVertexWithUV(1.001, 1, 0, icon.getMaxU(), icon.getMinV());
        tessellator.addVertexWithUV(1.001, 1, 1, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(1.001, 0, 1, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(1.001, 0, 0, icon.getMinU(), icon.getMinV());

        // WEST
        tessellator.addVertexWithUV(-0.001, 0, 0, icon.getMinU(), icon.getMinV());
        tessellator.addVertexWithUV(-0.001, 0, 1, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(-0.001, 1, 1, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(-0.001, 1, 0, icon.getMaxU(), icon.getMinV());

        tessellator.draw();
    }
}
