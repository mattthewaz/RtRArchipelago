package archipelago;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists AP mod state to ap_data/<slot>/state.json.
 *
 * Saved eagerly after each state change — never rolled back with the game save.
 * Loaded once after the AP server connection is established.
 */
public class ApState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TIER_MAP_TYPE = new TypeToken<Map<String, Integer>>(){}.getType();

    public int lastProcessedIndex = -1;
    public Map<String, Integer> claimedTierCounts = new HashMap<>();
    public Set<String> unlockedItems = new HashSet<>();
    public int pureEssenceCount = 0;

    // -------------------------------------------------------------------------
    // Load / save
    // -------------------------------------------------------------------------

    public static ApState load(File stateFile) {
        if (!stateFile.exists()) {
            System.out.println("[Archipelago] No state file found — starting fresh");
            return new ApState();
        }
        try (Reader r = new FileReader(stateFile)) {
            ApState state = GSON.fromJson(r, ApState.class);
            if (state == null) state = new ApState();
            if (state.claimedTierCounts == null) state.claimedTierCounts = new HashMap<>();
            if (state.unlockedItems == null) state.unlockedItems = new HashSet<>();
            System.out.println("[Archipelago] Loaded state: lastIndex=" + state.lastProcessedIndex
                + " tiers=" + state.claimedTierCounts);
            return state;
        } catch (Exception e) {
            System.err.println("[Archipelago] Failed to load state: " + e.getMessage());
            return new ApState();
        }
    }

    public void save(File stateFile) {
        try {
            stateFile.getParentFile().mkdirs();
            try (Writer w = new FileWriter(stateFile)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            System.err.println("[Archipelago] Failed to save state: " + e.getMessage());
        }
    }
}
