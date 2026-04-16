package archipelago.patches;

import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.states.WorldMapState:
 *  - loadState(): triggers the AP connection on world map entry.
 *  - update(): refreshes the perk list UI whenever AP grants perks
 *    (e.g. Ancient Relic), since gui is private and only reachable from
 *    inside WorldMapState itself.
 */
public class WorldMapStatePatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod loadState = cc.getDeclaredMethod("loadState");
        loadState.insertBefore(
            "{ archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "  if (_ap != null) _ap.onWorldMapEntered(); }"
        );

        CtMethod update = cc.getDeclaredMethod("update");
        update.insertAfter(
            "{ archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "  if (_ap != null && _ap.consumePerkListRefresh()) $0.gui.getPerksList().refreshPerksList(); }"
        );
    }
}
