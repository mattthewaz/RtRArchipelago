package archipelago.patches;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;
import rtrmodloader.api.ModPatch;

/**
 * Redirects all File(String) constructions in the save/profile classes from
 * "profiles/..." to "ap_profiles/..." so that AP game data is kept completely
 * separate from vanilla Rise to Ruins saves.
 */
public class ProfileDirPatch implements ModPatch {

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        cc.instrument(new ExprEditor() {
            @Override
            public void edit(NewExpr e) throws CannotCompileException {
                if ("(Ljava/lang/String;)V".equals(e.getSignature())) {
                    if ("java.io.File".equals(e.getClassName())) {
                        e.replace(
                            "{" +
                            "  String _p = $1;" +
                            "  if (_p != null && _p.startsWith(\"profiles/\")) {" +
                            "    _p = \"ap_profiles/\" + _p.substring(9);" +
                            "  }" +
                            "  $_ = new java.io.File(_p);" +
                            "}"
                        );
                    } else if ("java.io.FileOutputStream".equals(e.getClassName())) {
                        e.replace(
                            "{" +
                            "  String _p = $1;" +
                            "  if (_p != null && _p.startsWith(\"profiles/\")) {" +
                            "    _p = \"ap_profiles/\" + _p.substring(9);" +
                            "  }" +
                            "  $_ = new java.io.FileOutputStream(_p);" +
                            "}"
                        );
                    } else if ("java.io.FileInputStream".equals(e.getClassName())) {
                        e.replace(
                            "{" +
                            "  String _p = $1;" +
                            "  if (_p != null && _p.startsWith(\"profiles/\")) {" +
                            "    _p = \"ap_profiles/\" + _p.substring(9);" +
                            "  }" +
                            "  $_ = new java.io.FileInputStream(_p);" +
                            "}"
                        );
                    }
                }
            }
        });
    }
}
