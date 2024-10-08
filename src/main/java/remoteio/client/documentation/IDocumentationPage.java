package remoteio.client.documentation;

import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * @author dmillerw
 */
public interface IDocumentationPage {

    @SideOnly(Side.CLIENT)
    public abstract void renderScreen(GuiScreen guiScreen, int mouseX, int mouseY);

    @SideOnly(Side.CLIENT)
    public abstract void updateScreen(GuiScreen guiScreen);
}
