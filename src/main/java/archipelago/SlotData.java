package archipelago;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Parsed contents of the slot_data field from the AP Connected message.
 * Produced by fill_slot_data() in the apworld and sent once on connect.
 */
public class SlotData {

    /** "2726297609" → "HOUSING" — string keys because JSON has no integer keys */
    public final Map<String, String> itemIdToName;

    /** "2726305412" → "DEFORESTATION Tier 1" */
    public final Map<String, String> locationIdToName;

    /** "Arcane Efficiency" → ["BANISH_SPELL_COST", "CHARM_SPELL_COST", ...] */
    public final Map<String, List<String>> perkBundles;

    /** "DEFORESTATION" → [id_tier1, id_tier2, id_tier3] */
    public final Map<String, List<Long>> goalLocationMap;

    public final int pureEssenceRequired;
    public final int pureEssenceTotal;

    private SlotData(Map<String, String> itemIdToName,
                     Map<String, String> locationIdToName,
                     Map<String, List<String>> perkBundles,
                     Map<String, List<Long>> goalLocationMap,
                     int pureEssenceRequired,
                     int pureEssenceTotal) {
        this.itemIdToName        = itemIdToName;
        this.locationIdToName    = locationIdToName;
        this.perkBundles         = perkBundles;
        this.goalLocationMap     = goalLocationMap;
        this.pureEssenceRequired = pureEssenceRequired;
        this.pureEssenceTotal    = pureEssenceTotal;
    }

    public static SlotData parse(JsonObject json) {
        Gson gson = new Gson();
        Type stringMap    = new TypeToken<Map<String, String>>(){}.getType();
        Type perkBundleMap = new TypeToken<Map<String, List<String>>>(){}.getType();
        Type goalLocMap   = new TypeToken<Map<String, List<Long>>>(){}.getType();

        return new SlotData(
            gson.fromJson(json.get("item_id_to_name"),       stringMap),
            gson.fromJson(json.get("location_id_to_name"),   stringMap),
            gson.fromJson(json.get("perk_bundles"),           perkBundleMap),
            gson.fromJson(json.get("goal_location_map"),      goalLocMap),
            json.get("pure_essence_required").getAsInt(),
            json.get("pure_essence_total").getAsInt()
        );
    }
}
