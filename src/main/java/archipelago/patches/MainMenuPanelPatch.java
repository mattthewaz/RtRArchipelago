package archipelago.patches;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.gui.states.mainmenu.MainMenuPanel to inject the AP Settings button.
 *
 *   Constructor insertAfter → ApMainMenuButton.init()
 *   render(boolean, boolean) insertAfter → ApMainMenuButton.renderBelow(...)
 *     Uses changeProfile.getIntersectBox() to position the button just below
 *     the Change Profile button. Returns early when the box is null (other pages).
 */
public class MainMenuPanelPatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        // Constructor: MainMenuPanel(MainMenuGUIController, MainMenuGUIData, Graphics, GameContainer, Rectangle)
        CtConstructor ctor = cc.getDeclaredConstructors()[0];
        ctor.insertAfter(
            "{ archipelago.ApMainMenuButton.init();" +
            "  archipelago.ApSettingsDialog.init($4);" +
            "  rtr.gui.buttons.GUIButtonMainMenu _apBtn = archipelago.ApMainMenuButton.getButton();" +
            "  if (_apBtn != null) $0.guiButtons.add(_apBtn); }"
        );

        // render(boolean, boolean): $1=hidden, $2=focused
        CtClass boolType = CtClass.booleanType;
        CtMethod render = cc.getDeclaredMethod("render", new CtClass[]{ boolType, boolType });
        render.insertAfter(
            "{" +
            "  org.newdawn.slick.geom.Rectangle _cb = $0.changeProfile.getIntersectBox();" +
            "  archipelago.ApMainMenuButton.renderBelow($0.g, $0.mouse, _cb, $2);" +
            "}"
        );
    }
}
