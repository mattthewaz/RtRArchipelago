package archipelago.patches;

import archipelago.ArchipelagoRuntime;
import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.influence.SpellBase.canCast().
 *
 * If the spell type has not been received as an AP item, returns false
 * so the game prevents casting. The spell unlocks when the AP item arrives.
 *
 * getSpellType().name() returns the SpellType enum name (e.g. "METEOR", "RECALL")
 * which matches our AP spell item names directly.
 */
public class SpellGatePatch implements ModPatch {

    /** Called from the patched method. Returns false if the AP mod says the spell is locked. */
    public static boolean isUnlocked(String name) {
        ArchipelagoRuntime ap = ArchipelagoRuntime.getInstance();
        if (ap == null) return true;
        return ap.isUnlocked(name);
    }

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod canCast = cc.getDeclaredMethod("canCast");
        canCast.insertBefore(
            "{ if (!archipelago.patches.SpellGatePatch.isUnlocked($0.getSpellType().name())) return false; }"
        );

        // Return a cost larger than any possible max influence so the UI grey-out
        // logic (percentCast > 100) fires for AP-locked spells.
        CtMethod getCastCost = cc.getDeclaredMethod("getCastCost");
        getCastCost.insertBefore(
            "{ if (!archipelago.patches.SpellGatePatch.isUnlocked($0.getSpellType().name())) return 999999; }"
        );
    }
}
