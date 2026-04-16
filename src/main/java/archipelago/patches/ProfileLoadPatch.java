package archipelago.patches;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.ProfileModule to notify the AP mod when a profile is loaded or created.
 *
 *   loadProfile(int slot)
 *       insertBefore → ArchipelagoRuntime.onProfileLoaded(slot)
 *       Reads ap_config.json and auto-connects if configured.
 *
 *   createAndLoadProfile(int slot, String, String, boolean)
 *       insertBefore → ApNewProfileFields.saveAndConnectForSlot(slot)
 *       Saves ap_config.json from the embedded AP fields BEFORE the game
 *       transitions away from MainMenuState, then calls onProfileCreated.
 */
public class ProfileLoadPatch implements ModPatch {

    private static final String LOAD_HOOK =
        "{ archipelago.ApSettingsDialog.setCurrentSlot($1);" +
        "  archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
        "  if (_ap != null) _ap.onProfileLoaded($1); }";

    private static final String CREATE_HOOK =
        "{ archipelago.ApNewProfileFields.saveAndConnectForSlot($1); }";

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        ClassPool pool = cc.getClassPool();
        CtClass strType = pool.get("java.lang.String");

        // loadProfile(int)
        try {
            cc.getDeclaredMethod("loadProfile", new CtClass[]{ CtClass.intType })
              .insertBefore(LOAD_HOOK);
        } catch (javassist.NotFoundException e) {
            System.err.println("[Archipelago] ProfileLoadPatch: loadProfile(int) not found: " + e.getMessage());
        }

        // createAndLoadProfile(int, String, String, boolean)
        try {
            cc.getDeclaredMethod("createAndLoadProfile",
                    new CtClass[]{ CtClass.intType, strType, strType, CtClass.booleanType })
              .insertBefore(CREATE_HOOK);
        } catch (javassist.NotFoundException e) {
            System.err.println("[Archipelago] ProfileLoadPatch: createAndLoadProfile not found: " + e.getMessage());
        }
    }
}
