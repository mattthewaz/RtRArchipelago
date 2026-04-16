package archipelago.patches;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.gui.states.mainmenu.NewProfilePanel to embed AP config fields.
 *
 *   Constructor insertAfter → ApNewProfileFields.init(gc)
 *     Initializes the three AP input fields (server, slot, password) once,
 *     using the GameContainer ($4) passed to the constructor.
 *
 *   render(boolean) insertAfter → ApNewProfileFields.renderOn(g, mouse, x, y)
 *     Draws labels and input fields below the existing name/twitch content.
 *     The panel PNG must be tall enough to fit the extra content.
 */
public class NewProfilePanelPatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        ClassPool pool = cc.getClassPool();

        // Constructor: NewProfilePanel(MainMenuGUIController, MainMenuGUIData,
        //              Graphics, GameContainer, Rectangle)
        // $4 = GameContainer
        CtConstructor ctor = cc.getDeclaredConstructor(new CtClass[]{
            pool.get("rtr.gui.states.MainMenuGUIController"),
            pool.get("rtr.gui.states.MainMenuGUIData"),
            pool.get("org.newdawn.slick.Graphics"),
            pool.get("org.newdawn.slick.GameContainer"),
            pool.get("org.newdawn.slick.geom.Rectangle"),
        });
        ctor.insertAfter("archipelago.ApNewProfileFields.init($4);");

        // render(boolean): draw AP fields after existing content.
        // $0.g, $0.mouse, $0.x, $0.y are protected fields from GUIPanelBase.
        CtMethod render = cc.getDeclaredMethod("render",
            new CtClass[]{ CtClass.booleanType });
        render.insertAfter(
            "archipelago.ApNewProfileFields.renderOn($0.g, $0.mouse, $0.x, $0.y);"
        );
    }
}
