package archipelago.patches;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import archipelago.ArchipelagoRuntime;
import javassist.CtClass;
import javassist.CtMethod;
import rtrmodloader.api.ModPatch;

/**
 * Patches rtr.gui.buttons.GUIPanelButton.render() to grey out building
 * panel buttons for buildings that are locked by the AP mod.
 *
 * PANEL_TO_ITEM maps each building's GUIPanelButton name (e.g. "panelFarm")
 * to its AP item name (e.g. "FARM"). The 7-arg render overload is patched
 * via insertBefore to OR the AP lock check into the disabled ($6) flag.
 * The 6-arg overload delegates to the 7-arg with count=-1, so both call
 * paths are covered.
 *
 * Greying out the button is sufficient: a disabled button cannot be clicked,
 * so the player cannot select the building type at all.
 */
public class BuildingGatePatch implements ModPatch {
    
    /** Maps GUIPanelButton name → AP item name for every building in the item pool. */
    public static final Map<String, String> PANEL_TO_ITEM;
    static {
        Map<String, String> m = new HashMap<>();
        // Storage
        m.put("panelWoodStorage",           "WOOD_STORAGE");
        m.put("panelRockStorage",           "ROCK_STORAGE");
        m.put("panelFoodStorage",           "FOOD_STORAGE");
        m.put("panelMineralStorage",        "MINERAL_STORAGE");
        m.put("panelCrystalStorage",        "CRYSTAL_STORAGE");
        m.put("panelEquipmentStorage",      "EQUIPMENT_STORAGE");
        m.put("panelGoldStorage",           "GOLD_STORAGE");
        m.put("panelMiscellaneousStorage",  "MISCELLANEOUS_STORAGE");
        m.put("panelAmmoStorage",           "AMMO_STORAGE");
        // Housing
        m.put("panelHousing",               "HOUSING");
        m.put("panelDoggoHouse",            "DOGGO_HOUSE");
        m.put("panelAncillary",             "ANCILLARY");
        // Food & water
        m.put("panelFarm",                  "FARM");
        m.put("panelKitchen",               "KITCHEN");
        m.put("panelCluckerCoop",           "CLUCKER_COOP");
        m.put("panelAnimalPen",             "ANIMAL_PEN");
        m.put("panelBottler",               "BOTTLER");
        // Resource production
        m.put("panelMiningFacility",        "MINING_FACILITY");
        m.put("panelCrystalHarvestry",      "CRYSTAL_HARVESTRY");
        m.put("panelForge",                 "FORGE");
        m.put("panelStoneCuttery",          "STONE_CUTTERY");
        m.put("panelRockTumbler",           "ROCK_TUMBLER");
        m.put("panelProcessor",             "PROCESSOR");
        m.put("panelCrystillery",           "CRYSTILLERY");
        m.put("panelRainCatcher",           "RAIN_CATCHER");
        m.put("panelWaterPurifier",         "WATER_PURIFIER");
        m.put("panelEssenceCollector",      "ESSENCE_COLLECTOR");
        m.put("panelEssenceAltar",          "ESSENCE_ALTAR");
        m.put("panelLumberShack",           "LUMBER_SHACK");
        m.put("panelLumberMill",            "LUMBER_MILL");
        // Crafting & trash
        m.put("panelToolsmithy",            "TOOLSMITHY");
        m.put("panelArmorsmithy",           "ARMORSMITHY");
        m.put("panelBowyer",                "BOWYER");
        m.put("panelBurner",                "BURNER");
        m.put("panelTrashCan",              "TRASH_CAN");
        m.put("panelLandfill",              "LANDFILL");
        m.put("panelTrashyCubePile",        "TRASHY_CUBE_PILE");
        // Defense towers
        m.put("panelBowTower",              "BOW_TOWER");
        m.put("panelBallistaTower",         "BALLISTA_TOWER");
        m.put("panelSlingTower",            "SLING_TOWER");
        m.put("panelBulletTower",           "BULLET_TOWER");
        m.put("panelSprayTower",            "SPRAY_TOWER");
        m.put("panelStaticTower",           "STATIC_TOWER");
        m.put("panelPhantomDartTower",      "PHANTOM_DART_TOWER");
        m.put("panelElementalBoltTower",    "ELEMENTAL_BOLT_TOWER");
        m.put("panelAttractTower",          "ATTRACT_TOWER");
        m.put("panelBanishTower",           "BANISH_TOWER");
        m.put("panelRecombobulatorTower",   "RECOMBOBULATOR_TOWER");
        m.put("panelLightningRod",          "LIGHTNING_ROD");
        // Golem combobulators
        m.put("panelWoodGolemCombobulator",    "WOOD_GOLEM_COMBOBULATOR");
        m.put("panelStoneGolemCombobulator",   "STONE_GOLEM_COMBOBULATOR");
        m.put("panelCrystalGolemCombobulator", "CRYSTAL_GOLEM_COMBOBULATOR");
        m.put("panelCubeEGolemCombobulator",   "CUBE_E_GOLEM_COMBOBULATOR");
        // Utility / infrastructure
        m.put("panelClinic",                "CLINIC");
        m.put("panelMaintenanceBuilding",   "MAINTENANCE_BUILDING");
        m.put("panelCourierStation",        "COURIER_STATION");
        m.put("panelOutpost",               "OUTPOST");
        m.put("panelMigrationWayStation",   "MIGRATION_WAY_STATION");
        m.put("panelWayMakerShack",         "WAY_MAKER_SHACK");
        m.put("panelRangerLodge",           "RANGER_LODGE");
        m.put("panelMarketplace",           "MARKETPLACE");
        m.put("panelReliquary",             "RELIQUARY");
        m.put("panelKeyShack",              "KEY_SHACK");
        m.put("panelSmallFountain",         "SMALL_FOUNTAIN");
        m.put("panelLargeFountain",         "LARGE_FOUNTAIN");
        m.put("panelCrystalMotivator",      "CRYSTAL_MOTIVATOR");
        m.put("panelWell",                  "WELL");
        // Walls
        m.put("panelWoodFence",             "WOOD_FENCE");
        m.put("panelStoneWall",             "STONE_WALL");
        m.put("panelCurtainWall",           "CURTAIN_WALL");
        m.put("panelCrylithiumCurtainWall", "CRYLITHIUM_CURTAIN_WALL");
        m.put("panelTrashyCubeWall",        "TRASHY_CUBE_WALL");
        m.put("panelCrylithiumWall",        "CRYLITHIUM_WALL");
        m.put("panelCrylithiumWallGateNS",  "CRYLITHIUM_WALL_GATE_NS");
        m.put("panelCrylithiumWallGateWE",  "CRYLITHIUM_WALL_GATE_WE");
        m.put("panelStoneWallGateNS",       "STONE_WALL_GATE_NS");
        m.put("panelStoneWallGateWE",       "STONE_WALL_GATE_WE");
        m.put("panelWoodFenceGateNS",       "WOOD_FENCE_GATE_NS");
        m.put("panelWoodFenceGateWE",       "WOOD_FENCE_GATE_WE");
        // Lighting
        m.put("panelFirePit",               "FIRE_PIT");
        m.put("panelLargeFirePit",          "LARGE_FIRE_PIT");
        m.put("panelCrylithiumFirePit",     "CRYLITHIUM_FIRE_PIT");
        m.put("panelLargeCrylithiumFirePit","LARGE_CRYLITHIUM_FIRE_PIT");
        // Special
        m.put("panelCullisGate",            "CULLIS_GATE");
        PANEL_TO_ITEM = Collections.unmodifiableMap(m);
    }

    /** Returns true if the named building has been received as an AP item, or if not connected. */
    public static boolean isUnlocked(String name) {
        ArchipelagoRuntime ap = ArchipelagoRuntime.getInstance();
        if (ap == null) return true;
        return ap.isUnlocked(name);
    }

    @Override
    public void apply(CtClass cc, ClassLoader loader) throws Exception {
        CtMethod render7 = null;
        for (CtMethod m : cc.getDeclaredMethods("render")) {
            if (m.getParameterTypes().length == 7) {
                render7 = m;
                break;
            }
        }
        if (render7 == null) throw new NoSuchMethodException("GUIPanelButton: no 7-arg render");

        render7.insertBefore(
            "{ String _item = (String) archipelago.patches.BuildingGatePatch.PANEL_TO_ITEM.get($0.buttonName);" +
            "  if (_item != null) $6 = $6 | !archipelago.patches.BuildingGatePatch.isUnlocked(_item); }"
        );
    }
}
