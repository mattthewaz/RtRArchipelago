package archipelago.patches;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.states.MainMenuState for AP mod hooks:
 *
 *   render(GameContainer, StateBasedGame, Graphics) — insertAfter
 *     Draws the AP Settings dialog on top of everything.
 *
 *   controlsMousePressedMainMenuPanel() — insertAfter
 *     Routes clicks to ApSettingsDialog: show/hide/handleClick depending on
 *     dialog visibility and whether the AP Settings button was clicked.
 *
 *   controlsMousePressedNewProfile() — insertBefore
 *     Forwards the click position to ApNewProfileFields.handleClick() so the
 *     server/slot/password fields can acquire focus when clicked.
 */
public class MainMenuStatePatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        ClassPool pool = cc.getClassPool();
        cc.getDeclaredMethod("render",
                new CtClass[]{
                    pool.get("org.newdawn.slick.GameContainer"),
                    pool.get("org.newdawn.slick.state.StateBasedGame"),
                    pool.get("org.newdawn.slick.Graphics")
                })
          .insertAfter("{ archipelago.ApSettingsDialog.renderOn($3, $0.mouse); }");

        cc.getDeclaredMethod("controlsMousePressedMainMenuPanel")
          .insertAfter(
              "{ boolean _apVisible = archipelago.ApSettingsDialog.isVisible();" +
              "  boolean _apClicked = $0.gui.intersects(" +
              "      rtr.gui.states.GUIControllerBase.GUIPanel.MAIN_MENU_MAIN_MENU_PANEL," +
              "      \"apSettings\");" +
              "  if (_apVisible && _apClicked) {" +
              "    archipelago.ApSettingsDialog.hide();" +
              "  } else if (_apVisible) {" +
              "    archipelago.ApSettingsDialog.handleClick($0.mouseX, $0.mouseY);" +
              "  } else if (_apClicked) {" +
              "    archipelago.ApSettingsDialog.show();" +
              "  }" +
              "}"
          );

        cc.getDeclaredMethod("controlsMousePressedNewProfile")
          .insertBefore(
              "archipelago.ApNewProfileFields.handleClick($0.mouseX, $0.mouseY);"
          );
    }
}
