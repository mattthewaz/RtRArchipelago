package archipelago;

import org.newdawn.slick.Color;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.geom.Rectangle;
import rtr.gui.buttons.GUIButtonInputText;
import rtr.gui.buttons.GUIButtonMainMenu;
import rtr.system.ScaleControl;

import java.io.File;

/**
 * Overlay dialog for editing the AP connection config (server, slot, password)
 * of the currently loaded profile.
 *
 * Lifecycle:
 *   MainMenuPanelPatch constructor hook → init(gc)
 *   MainMenuStatePatch render hook      → renderOn(g, mouse)
 *   MainMenuStatePatch click hook       → handleClick(mouseXPressed, mouseYPressed)
 *   ProfileLoadPatch / ApNewProfileFields → setCurrentSlot(slot)
 *   ApMainMenuButton.handleClick        → show()
 *
 * Background image:
 *   Assign a loaded Image to backgroundImage to replace the placeholder rect.
 *   Dimensions should match DIALOG_W × DIALOG_H.
 */
public class ApSettingsDialog {

    // Set this to a loaded Image to replace the placeholder background.
    public static Image backgroundImage = null;

    // Dialog logical dimensions (interface-coordinate pixels).
    private static final int DIALOG_W = 340;
    private static final int DIALOG_H = 260;

    private static boolean initialized = false;
    private static boolean visible = false;
    private static int currentProfileSlot = -1;

    private static GUIButtonInputText serverField;
    private static GUIButtonInputText slotField;
    private static GUIButtonInputText passwordField;
    private static GUIButtonMainMenu saveButton;
    private static GUIButtonMainMenu cancelButton;

    // -------------------------------------------------------------------------

    /** Records which profile slot is active. Called from profile load/create hooks. */
    public static void setCurrentSlot(int slot) {
        currentProfileSlot = slot;
    }

    public static boolean isVisible() { return visible; }

    /** Called from MainMenuPanel constructor patch. Idempotent. */
    public static void init(GameContainer gc) {
        if (initialized) return;
        try {
            serverField   = new GUIButtonInputText(gc, "apCfgServer",   "Server (e.g. archipelago.gg:38281)", 36, 2);
            slotField     = new GUIButtonInputText(gc, "apCfgSlot",     "Slot Name",                          22, 2);
            passwordField = new GUIButtonInputText(gc, "apCfgPassword", "Password (optional)",                22, 2);
            saveButton    = new GUIButtonMainMenu("apCfgSave",   "Save",   2);
            cancelButton  = new GUIButtonMainMenu("apCfgCancel", "Cancel", 2);
            initialized = true;
            System.out.println("[Archipelago] ApSettingsDialog initialized");
        } catch (Exception e) {
            System.out.println("[Archipelago] ApSettingsDialog init failed: " + e.getMessage());
        }
    }

    /** Opens the dialog pre-filled with the saved config for the current slot. */
    public static void show() {
        System.out.println("[Archipelago] ApSettingsDialog.show() called, initialized=" + initialized + " slot=" + currentProfileSlot);
        if (!initialized || currentProfileSlot < 0) return;
        File profileDir = new File(System.getProperty("user.dir"),
            "ap_profiles/profile" + currentProfileSlot);
        ApConfig config = ApConfig.load(profileDir);
        serverField.setText(config.server   != null ? config.server   : "");
        slotField.setText(config.slot       != null ? config.slot     : "");
        passwordField.setText(config.password != null ? config.password : "");
        setFocus(null);
        visible = true;
    }

    public static void hide() {
        visible = false;
        setFocus(null);
    }

    // -------------------------------------------------------------------------

    /** Called from MainMenuState.render() insertAfter to draw the dialog on top. */
    public static void renderOn(Graphics g, Rectangle mouse) {
        if (!visible || !initialized) return;

        int cx = ScaleControl.getInterfaceCenterX();
        int cy = ScaleControl.getInterfaceCenterY();
        int x  = cx - DIALOG_W / 2;
        int y  = cy - DIALOG_H / 2;

        Color prev = g.getColor();

        if (backgroundImage != null) {
            backgroundImage.draw(x, y);
        } else {
            g.setColor(new Color(20, 20, 30, 220));
            g.fillRect(x, y, DIALOG_W, DIALOG_H);
            g.setColor(new Color(100, 100, 130, 255));
            g.drawRect(x, y, DIALOG_W - 1, DIALOG_H - 1);
        }

        g.setColor(Color.white);
        g.drawString("Archipelago Settings", x + 10, y + 10);

        g.setColor(Color.white);
        g.drawString("Server:", x + 10, y + 45);
        serverField.render(g, mouse, x + 10, y + 60, false);

        g.setColor(Color.white);
        g.drawString("Slot Name:", x + 10, y + 110);
        slotField.render(g, mouse, x + 10, y + 125, false);

        g.setColor(Color.white);
        g.drawString("Password:", x + 10, y + 160);
        passwordField.render(g, mouse, x + 10, y + 175, false);

        // Save centered left of dialog center, Cancel centered right
        saveButton.render(g, mouse, cx - 80, y + 220, false, true, false);
        cancelButton.render(g, mouse, cx + 80, y + 220, false, true, false);

        g.setColor(prev);
    }

    /** Called from controlsMousePressedMainMenuPanel() patch when the dialog is visible. */
    public static void handleClick(int mouseX, int mouseY) {
        if (!visible || !initialized) return;

        Rectangle saveBox   = saveButton.getIntersectBox();
        Rectangle cancelBox = cancelButton.getIntersectBox();
        Rectangle serverBox = serverField.getIntersectBox();
        Rectangle slotBox   = slotField.getIntersectBox();
        Rectangle passBox   = passwordField.getIntersectBox();

        if (saveBox != null && saveBox.contains(mouseX, mouseY)) {
            save();
        } else if (cancelBox != null && cancelBox.contains(mouseX, mouseY)) {
            hide();
        } else if (serverBox != null && serverBox.contains(mouseX, mouseY)) {
            setFocus(serverField);
        } else if (slotBox != null && slotBox.contains(mouseX, mouseY)) {
            setFocus(slotField);
        } else if (passBox != null && passBox.contains(mouseX, mouseY)) {
            setFocus(passwordField);
        }
    }

    // -------------------------------------------------------------------------

    private static void save() {
        if (currentProfileSlot < 0) return;

        File profileDir = new File(System.getProperty("user.dir"),
            "ap_profiles/profile" + currentProfileSlot);
        profileDir.mkdirs();

        ApConfig config  = new ApConfig();
        config.server    = serverField.getText().trim();
        config.slot      = slotField.getText().trim();
        config.password  = passwordField.getText().trim();
        config.save(profileDir);
        System.out.println("[Archipelago] AP config saved for profile " + currentProfileSlot);

        // Tell ArchipelagoMod to re-read the config so the next World Map entry reconnects.
        Object mod = rtrmodloader.api.ModRegistry.get("archipelago");
        if (mod != null) {
            try {
                mod.getClass()
                   .getMethod("onProfileLoaded", new Class[]{ Integer.TYPE })
                   .invoke(mod, new Object[]{ Integer.valueOf(currentProfileSlot) });
            } catch (Exception e) {
                System.out.println("[Archipelago] ApSettingsDialog save dispatch failed: " + e);
            }
        }
        hide();
    }

    private static void setFocus(GUIButtonInputText target) {
        if (serverField   != null) serverField.setFocus(target == serverField);
        if (slotField     != null) slotField.setFocus(target == slotField);
        if (passwordField != null) passwordField.setFocus(target == passwordField);
    }
}
