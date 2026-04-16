package archipelago;

import org.newdawn.slick.Graphics;
import org.newdawn.slick.geom.Rectangle;
import rtr.gui.buttons.GUIButtonMainMenu;

/**
 * Manages the "AP Settings" button rendered on the main menu's MAIN page,
 * immediately below the "Change Profile" button.
 *
 * Lifecycle:
 *   MainMenuPanelPatch constructor hook  → init()
 *   MainMenuPanelPatch render() hook     → renderBelow(g, mouse, changeProfileBox, focused)
 *   MainMenuStatePatch click hook        → handleClick(mouseX, mouseY)
 */
public class ApMainMenuButton {

    private static GUIButtonMainMenu button;

    /** Called from MainMenuPanel constructor patch. Idempotent. */
    public static void init() {
        if (button != null) return;
        try {
            button = new GUIButtonMainMenu("apSettings", "AP Settings", 2);
        } catch (Exception e) {
            System.out.println("[Archipelago] ApMainMenuButton init failed: " + e.getMessage());
        }
    }

    /**
     * Called from MainMenuPanel.render() patch after the panel is drawn.
     * changeProfileBox comes from changeProfile.getIntersectBox() — null when
     * not on the MAIN page, in which case we skip rendering.
     */
    public static void renderBelow(Graphics g, Rectangle mouse, Rectangle changeProfileBox, boolean focused) {
        if (button == null || changeProfileBox == null) return;
        // Pass the center X so the centering flag aligns us with changeProfile.
        int x = (int) (changeProfileBox.getX() + changeProfileBox.getWidth() / 2);
        int y = (int) (changeProfileBox.getY() + changeProfileBox.getHeight());
        button.render(g, mouse, x, y, false, true, focused);
    }

    public static GUIButtonMainMenu getButton() { return button; }
}
