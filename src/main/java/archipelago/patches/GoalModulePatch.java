package archipelago.patches;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.goal.GoalModule to support AP multi-tier goals in the
 * count-based unlock system.
 *
 * refreshGoalData() patch: replaces isCompleted() calls with
 * isCompleted() || isCompletedPreviously() so that multi-tier AP goals count
 * toward the global goal count after the first tier is claimed, even while the
 * goal is reset and being re-completed for later tiers.
 */
public class GoalModulePatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod refreshGoalData = cc.getDeclaredMethod("refreshGoalData");
        refreshGoalData.instrument(new ExprEditor() {
            public void edit(MethodCall m) throws javassist.CannotCompileException {
                if (m.getMethodName().equals("isCompleted")) {
                    m.replace("{ $_ = $0.isCompleted() || $0.isCompletedPreviously(); }");
                }
            }
        });
    }
}
