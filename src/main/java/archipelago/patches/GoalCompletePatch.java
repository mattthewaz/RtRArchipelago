package archipelago.patches;

import archipelago.ArchipelagoRuntime;
import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.goal.Goal to support AP multi-tier claiming.
 *
 * setClaimed() patch: fires an AP location check for the completed tier.
 * For multi-tier goals, resets the goal (goalAmount=0, completed=false,
 * completedPreviously=true) and returns early so the player must re-complete
 * it. The final tier falls through to normal setClaimed() behaviour.
 * Setting completedPreviously=true is critical: addAmount() never sets it
 * (only load() does), so without it, isLocked() on downstream goals would
 * re-lock after the reset.
 *
 * finalizeGoal() patch: divides goalAmountRequired by tier count immediately
 * at goal setup time, before any UI is displayed. Also captures the original
 * value so getDescription() can replace the baked-in text. Running here (not
 * in incrementGoal) ensures originalGoalAmounts is always populated even when
 * the player has made no progress yet.
 *
 * load() patch: restores completedPreviously from AP state after a save/
 * reload. GoalModule does NOT persist completedPreviously — it only saves
 * goalAmount and claimed. After a tier reset (goalAmount=0 written to disk),
 * load(0, false) would leave completedPreviously=false, re-locking downstream
 * goals. The patch asks ArchipelagoRuntime whether this goal was ever claimed
 * and sets completedPreviously=true if so.
 *
 * isLocked() patch: uses isCompletedPreviously() instead of isCompleted()
 * so downstream goals stay unlocked through tier resets.
 */
public class GoalCompletePatch implements ModPatch {

    /**
     * Fires the AP location check for the claimed tier.
     * Returns true if more tiers remain (goal should reset), false if this was
     * the last tier or AP is not active (goal should proceed to normal claim).
     */
    public static boolean onGoalClaimed(String goalType) {
        ArchipelagoRuntime ap = ArchipelagoRuntime.getInstance();
        if (ap == null) return false;
        return ap.onGoalClaimed(goalType);
    }

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod setClaimed = cc.getDeclaredMethod("setClaimed");
        setClaimed.insertBefore(
            "{ if (archipelago.patches.GoalCompletePatch.onGoalClaimed($0.getGoalType().name())) {" +
            "      $0.goalAmount = 0;" +
            "      $0.completed = false;" +
            "      $0.completedPreviously = true;" +
            "      return;" +
            "  } }"
        );

        // Divide goalAmountRequired at goal setup time so the threshold is correct before the
        // player makes any progress. insertAfter runs after finalizeGoal() bakes the description
        // text with the original amount — getDescription()'s patch handles the display replacement.
        // originalGoalAmounts is populated here so getOriginalAmountRequired() always works.
        CtMethod finalizeGoal = cc.getDeclaredMethod("finalizeGoal");
        finalizeGoal.insertAfter(
            "{ archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "  if (_ap != null) {" +
            "      $0.goalAmountRequired = _ap.getAdjustedAmountRequired($0.getGoalType().name(), $0.goalAmountRequired);" +
            "  } }"
        );

        // Restore completedPreviously after save/reload: GoalModule only persists goalAmount
        // and claimed, not completedPreviously. After a tier reset (goalAmount=0 saved), load()
        // would leave completedPreviously=false and re-lock downstream goals. Ask AP state
        // whether this goal was ever claimed and set it true if so.
        CtMethod load = cc.getDeclaredMethod("load");
        load.insertAfter(
            "{ archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "  if (_ap != null && _ap.wasGoalEverClaimed($0.getGoalType().name())) {" +
            "      $0.completedPreviously = true;" +
            "  } }"
        );

        // Patch getDescription() to replace the formatted original amount with the adjusted
        // (divided) amount in the description string. The description is baked at construction
        // time via Text.setVariableText("amount", ...) so we can't fix it at the source — instead
        // we swap the formatted number in the returned string. No timing risk: reads goalAmountRequired
        // from the field directly, which is always available.
        CtMethod getDescription = cc.getDeclaredMethod("getDescription");
        getDescription.insertAfter(
            "{ archipelago.ArchipelagoRuntime _ap = archipelago.ArchipelagoRuntime.getInstance();" +
            "  if (_ap != null) {" +
            "      int _original = _ap.getOriginalAmountRequired($0.getGoalType().name());" +
            "      if (_original > 0 && _original != $0.goalAmountRequired) {" +
            "          java.text.DecimalFormat _df = new java.text.DecimalFormat(\"#,###\");" +
            "          $_ = $_.replace(_df.format((long)_original), _df.format((long)$0.goalAmountRequired));" +
            "      }" +
            "      $_ = $_ + _ap.getGoalTierSuffix($0.getGoalType().name());" +
            "  } }"
        );

        // Replace the prerequisite check so that a tier-reset goal (completed=false,
        // completedPreviously=true) still unlocks downstream goals. Plain isCompleted()
        // is false after a reset; isCompletedPreviously() is false for vanilla goals
        // that were completed in the current session without a save/reload. Combining
        // both handles all cases.
        CtMethod isLocked = cc.getDeclaredMethod("isLocked");
        isLocked.instrument(new javassist.expr.ExprEditor() {
            public void edit(javassist.expr.MethodCall m) throws javassist.CannotCompileException {
                if (m.getMethodName().equals("isCompleted")) {
                    m.replace("{ $_ = $0.isCompleted() || $0.isCompletedPreviously(); }");
                }
            }
        });
    }
}
