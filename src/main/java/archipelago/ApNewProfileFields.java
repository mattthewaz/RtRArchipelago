package archipelago;

import org.newdawn.slick.Color;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.geom.Rectangle;
import org.newdawn.slick.geom.Shape;
import rtr.gui.buttons.GUIButtonInputText;

import java.io.File;

/**
 * Holds the three AP config input fields (server, slot, password) that are
 * rendered inside NewProfilePanel below the existing name / Twitch fields.
 *
 * Lifecycle:
 *   NewProfilePanelPatch constructor hook  → init(gc)
 *   NewProfilePanelPatch render() hook     → renderOn(g, mouse, x, y)
 *   MainMenuStatePatch click hook          → handleClick(mouseX, mouseY)
 *   ProfileLoadPatch createAndLoad hook    → saveForSlot(profileSlot)
 */
public class ApNewProfileFields {

    // --- Y offsets relative to panel's top-left (x, y) ---
    // Negative values place the fields ABOVE the panel image.
    // Layout (top → bottom): server, slot, password, then panel begins at y+0.
    private static final int LABEL_SERVER   = -155;
    private static final int FIELD_SERVER   = -141;
    private static final int LABEL_SLOT     = -108;
    private static final int FIELD_SLOT     = -94;
    private static final int LABEL_PASSWORD = -61;
    private static final int FIELD_PASSWORD = -47;

    private static final int FIELD_X_OFFSET = 53;  // same left margin as name/twitch fields

    private static GUIButtonInputText serverField;
    private static GUIButtonInputText slotField;
    private static GUIButtonInputText passwordField;
    private static boolean initialized = false;

    /** Called from NewProfilePanel constructor patch. Idempotent. */
    public static void init(GameContainer gc) {
        if (initialized) return;
        try {
            // 5-arg: (GameContainer, id, placeholder, widthInChars, fontSize)
            serverField   = new GUIButtonInputText(gc, "apServer",   "Server (e.g. archipelago.gg:38281)", 40, 2);
            slotField     = new GUIButtonInputText(gc, "apSlot",     "Slot Name",                          24, 2);
            passwordField = new GUIButtonInputText(gc, "apPassword", "Password (optional)",                24, 2);
            initialized = true;
            System.out.println("[Archipelago] ApNewProfileFields initialized");
        } catch (Exception e) {
            System.err.println("[Archipelago] ApNewProfileFields init failed: " + e.getMessage());
        }
    }

    /**
     * Called from NewProfilePanel.render() patch.
     * Draws a divider + labels + input fields below the existing panel content.
     */
    public static void renderOn(Graphics g, Rectangle mouse, int x, int y) {
        if (!initialized) return;
        Color prev = g.getColor();

        g.setColor(Color.white);
        g.drawString("Archipelago Server:", x + FIELD_X_OFFSET, y + LABEL_SERVER);
        serverField.render(g, mouse, x + FIELD_X_OFFSET, y + FIELD_SERVER, false);

        g.setColor(Color.white);
        g.drawString("Slot Name:", x + FIELD_X_OFFSET, y + LABEL_SLOT);
        slotField.render(g, mouse, x + FIELD_X_OFFSET, y + FIELD_SLOT, false);

        g.setColor(Color.white);
        g.drawString("Password:", x + FIELD_X_OFFSET, y + LABEL_PASSWORD);
        passwordField.render(g, mouse, x + FIELD_X_OFFSET, y + FIELD_PASSWORD, false);

        g.setColor(prev);
    }

    /**
     * Called from controlsMousePressedNewProfile() patch.
     * Routes a mouse click to the appropriate field's focus state.
     */
    public static void handleClick(int mouseX, int mouseY) {
        if (!initialized) return;
        Shape serverBox   = serverField.getIntersectBox();
        Shape slotBox     = slotField.getIntersectBox();
        Shape passwordBox = passwordField.getIntersectBox();
        if (serverBox != null && serverBox.contains(mouseX, mouseY)) {
            setFocus(serverField);
        } else if (slotBox != null && slotBox.contains(mouseX, mouseY)) {
            setFocus(slotField);
        } else if (passwordBox != null && passwordBox.contains(mouseX, mouseY)) {
            setFocus(passwordField);
        }
    }

    /**
     * Called from ProfileModule.createAndLoadProfile() insertBefore patch.
     * Saves ap_config.json and starts the AP client, all without going through
     */
    public static void saveAndConnectForSlot(int profileSlot) {
        if (!initialized) return;
        String slot = slotField.getText().trim();
        if (slot.isEmpty()) {
            System.out.println("[Archipelago] No slot name entered — skipping AP config for profile " + profileSlot);
            return;
        }
        ApSettingsDialog.setCurrentSlot(profileSlot);
        File profileDir = new File(System.getProperty("user.dir"),
            "ap_profiles/profile" + profileSlot);
        profileDir.mkdirs();

        ApConfig config  = new ApConfig();
        config.server    = serverField.getText().trim();
        config.slot      = slot;
        config.password  = passwordField.getText().trim();
        config.save(profileDir);
        System.out.println("[Archipelago] Saved AP config for profile " + profileSlot + " (slot=" + slot + ")");

        ArchipelagoRuntime ap = ArchipelagoRuntime.getInstance();
        if (ap != null) ap.onProfileCreated(profileSlot);
    }

    // -------------------------------------------------------------------------

    private static void setFocus(GUIButtonInputText target) {
        serverField.setFocus(target == serverField);
        slotField.setFocus(target == slotField);
        passwordField.setFocus(target == passwordField);
    }
}
