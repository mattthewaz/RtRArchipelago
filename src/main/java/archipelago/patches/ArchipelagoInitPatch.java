package archipelago.patches;

import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.system.Game.launchGame() to instantiate ArchipelagoRuntime
 * on the game thread before any state loads. This ensures the runtime
 * instance shares the game's classloader, allowing direct casts from
 * patch helper static methods without reflection.
 */
public class ArchipelagoInitPatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod launchGame = cc.getDeclaredMethod("launchGame");
        launchGame.insertBefore(
            "{ new archipelago.ArchipelagoRuntime(); }"
        );
    }
}
