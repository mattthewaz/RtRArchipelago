package archipelago.patches;

import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.states.StateBase.update() to drain pending AP changes
 * on the game thread after each tick. This is the single integration
 * point for applying effects that arrived from the WebSocket thread
 * (e.g. perk grants, future deferred effects).
 */
public class StateBasePatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod update = cc.getDeclaredMethod("update");
        update.insertAfter(
            "{ archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "  if (_ap != null) _ap.drainPendingChanges(); }"
        );
    }
}
