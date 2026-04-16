package archipelago;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;

/**
 * Per-profile AP connection config.
 *
 * Stored at ap_profiles/profile<N>/ap_config.json so each game slot has its
 * own server URL, AP slot name, and password.
 *
 * Example ap_config.json:
 * {
 *   "server":   "archipelago.gg:38281",
 *   "slot":     "YourName",
 *   "password": ""
 * }
 */
public class ApConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String server   = "localhost:38281";
    public String slot     = "";
    public String password = "";

    /** Load config from ap_profiles/profile<profileSlot>/ap_config.json. */
    public static ApConfig load(File profileDir) {
        File configFile = new File(profileDir, "ap_config.json");
        if (!configFile.exists()) {
            System.out.println("[Archipelago] No ap_config.json in " + profileDir + " — AP not configured");
            return new ApConfig();
        }
        try (FileReader reader = new FileReader(configFile)) {
            ApConfig config = GSON.fromJson(reader, ApConfig.class);
            if (config == null) config = new ApConfig();
            System.out.println("[Archipelago] Loaded config: server=" + config.server + " slot=" + config.slot);
            return config;
        } catch (Exception e) {
            System.err.println("[Archipelago] Failed to read ap_config.json: " + e.getMessage());
            return new ApConfig();
        }
    }

    /** Write this config to ap_profiles/profile<profileSlot>/ap_config.json. */
    public void save(File profileDir) {
        try {
            profileDir.mkdirs();
            try (Writer w = new FileWriter(new File(profileDir, "ap_config.json"))) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            System.err.println("[Archipelago] Failed to save ap_config.json: " + e.getMessage());
        }
    }
}
