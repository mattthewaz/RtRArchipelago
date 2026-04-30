package archipelago.patches;

import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.goal.Goal.isLocked() to bypass the goal-count gate when AP is
 * active. In vanilla, goals with no specific prerequisite (requiredGoal == null)
 * are locked until a global count of completed goals reaches goalCountRequired.
 * In an AP session the goal web topology is enforced by AP logic instead, so
 * the count gate is unnecessary and actively harmful — it withholds goals the
 * player has already logically unlocked via AP item routing.
 *
 * Goals with a specific requiredGoal are unaffected; their prerequisite check
 * still runs normally (via the isCompletedPreviously substitution in
 * GoalCompletePatch).
 */
public class GoalLockPatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod isLocked = cc.getDeclaredMethod("isLocked");
        isLocked.insertBefore(
            "{ if ($0.requiredGoal == null) {" +
            "      archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "      if (_ap != null) return false;" +
            "  } }"
        );
    }
}
