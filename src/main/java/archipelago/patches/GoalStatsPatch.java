package archipelago.patches;

import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.gui.buttons.GUIButtonGoalStats to keep the goal description
 * current during an AP session.
 *
 * The button caches goal.getDescription() once at construction. Since our
 * GoalCompletePatch dynamically appends the tier suffix and replaces the
 * requirement amount in getDescription(), the cached string goes stale as
 * tiers are claimed. Refreshing it at the top of render() costs nothing
 * (pure string work) and ensures the popup always shows the live value.
 */
public class GoalStatsPatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod render = cc.getDeclaredMethod("render");
        render.insertBefore(
            "{ archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "  if (_ap != null) {" +
            "      $0.goalDescription = $0.thisGoal.getDescription();" +
            "  } }"
        );
    }
}
