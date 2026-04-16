package archipelago;

import rtr.ModuleBase.ModuleType;
import rtr.PerkModule;
import rtr.map.MapData;
import rtr.objects.ObjectBase;
import rtr.objects.ObjectModule;
import rtr.resources.ResourceModule;
import rtr.states.StateBase;
import rtr.utilities.Utilities;

import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime singleton for the Archipelago mod. Created on the game thread via
 * ArchipelagoInitPatch so it shares the game's classloader — allowing direct
 * casts from patch helper static methods without reflection.
 *
 * Registered in ModRegistry under "archipelago" by its constructor.
 */
public class ArchipelagoRuntime {

    /** Filler item names that are received but have no distinct game effect (banner only). */
    private static final Set<String> FILLER_ITEMS = new HashSet<>();

    public static ArchipelagoRuntime getInstance() {
        return (ArchipelagoRuntime) rtrmodloader.api.ModRegistry.get("archipelago");
    }

    private ApClient client;
    private SlotData slotData;
    private ApState state;
    private File stateFile;
    private ApConfig pendingConfig;  // loaded on profile select, consumed on World Map entry

    // All unlocked item names (buildings + spells). Written from WebSocket thread,
    // read from game thread — ConcurrentHashMap.newKeySet() is safe for both.
    private final Set<String> unlockedItems = ConcurrentHashMap.newKeySet();

    // Pure Essence collected so far.
    private final AtomicInteger pureEssenceCount = new AtomicInteger(0);

    // Perk names queued for game-thread application.
    private final ConcurrentLinkedQueue<String> pendingPerkNames = new ConcurrentLinkedQueue<>();

    // Number of Ancient Relics received, drained on the game thread as random perks.
    private final AtomicInteger pendingAncientRelics = new AtomicInteger(0);

    // Number of Divine Sparks received, each grants 500 god XP on the game thread.
    private final AtomicInteger pendingDivineSparks = new AtomicInteger(0);

    // Item names queued for on-screen banner display (game thread only).
    private final ConcurrentLinkedQueue<String> pendingBanners = new ConcurrentLinkedQueue<>();

    // Original goalAmountRequired values, captured before we divide them for multi-tier goals.
    // ConcurrentHashMap because addAmount() (game thread) and tearDown() (game thread) both touch it.
    private final Map<String, Integer> originalGoalAmounts = new ConcurrentHashMap<>();

    // Number of Cache of Supplies items waiting to be spawned in a play-state map.
    private final AtomicInteger pendingCacheOfSupplies = new AtomicInteger(0);

    /**
     * Tracks one active "supply drop" that is dripping resources over many ticks.
     * Mirrors the loot box drip mechanic: one item spawned per tick (every 5 ticks),
     * stopped after resourcesLeft reaches zero.
     */
    private static class SupplyDrop {
        final int centerX;
        final int centerY;
        final ResourceModule.ResourceCategory category;
        int resourcesLeft;
        int tickAccum = 0;

        SupplyDrop(int centerX, int centerY, ResourceModule.ResourceCategory category, int count) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.category = category;
            this.resourcesLeft = count;
        }
    }

    // Active supply drops; drained one item at a time on the game thread.
    private final java.util.ArrayDeque<SupplyDrop> activeSupplyDrops = new java.util.ArrayDeque<>();

    // Set to true after granting perks so WorldMapState can refresh its perk list UI.
    private volatile boolean perkListRefreshRequested = false;

    public void requestPerkListRefresh() { perkListRefreshRequested = true; }
    /** Returns true (and clears the flag) if a perk list refresh is pending. */
    public boolean consumePerkListRefresh() {
        if (!perkListRefreshRequested) return false;
        perkListRefreshRequested = false;
        return true;
    }

    public ArchipelagoRuntime() {
        rtrmodloader.api.ModRegistry.register("archipelago", this);
        System.out.println("[Archipelago] Runtime initialized");
    }

    // -------------------------------------------------------------------------
    // Profile lifecycle — called from ProfileLoadPatch (game thread)
    // -------------------------------------------------------------------------

    /**
     * Called when an EXISTING profile is loaded/selected (loadProfile hook).
     * Reads ap_config.json and caches it; does NOT connect yet.
     * Connection happens in onWorldMapEntered() when the player clicks World Map.
     */
    public void onProfileLoaded(int profileSlot) {
        tearDown();
        File profileDir = new File(System.getProperty("user.dir"),
            "ap_profiles/profile" + profileSlot);
        ApConfig config = ApConfig.load(profileDir);
        if (config.slot == null || config.slot.isEmpty()) {
            System.out.println("[Archipelago] Profile " + profileSlot
                + " has no AP config — skipping");
            return;
        }
        stateFile = new File(profileDir, "ap_state.json");
        pendingConfig = config;
        System.out.println("[Archipelago] Profile " + profileSlot
            + " has AP config — will connect on World Map entry");
    }

    /**
     * Called when a NEWLY created profile's createAndLoadProfile hook fires.
     * ap_config.json has already been written by ApNewProfileFields.saveAndConnectForSlot().
     * Caches the config; connection happens in onWorldMapEntered().
     */
    public void onProfileCreated(int profileSlot) {
        tearDown();
        File profileDir = new File(System.getProperty("user.dir"),
            "ap_profiles/profile" + profileSlot);
        ApConfig config = ApConfig.load(profileDir);
        if (config.slot == null || config.slot.isEmpty()) {
            System.out.println("[Archipelago] Profile " + profileSlot
                + " has no AP config — skipping");
            return;
        }
        stateFile = new File(profileDir, "ap_state.json");
        pendingConfig = config;
        System.out.println("[Archipelago] Profile " + profileSlot
            + " has AP config — will connect on World Map entry");
    }

    /**
     * Called when WorldMapState.loadState() fires (player clicked World Map).
     * Connects on first entry; reconnects on re-entry if the client dropped.
     */
    public void onWorldMapEntered() {
        if (pendingConfig == null) return;
        if (client != null && client.isOpen()) return;
        startClient(pendingConfig);
    }

    // -------------------------------------------------------------------------
    // Client startup
    // -------------------------------------------------------------------------

    void startClient(ApConfig config) {
        try {
            String uri = config.server.startsWith("ws") ? config.server : "ws://" + config.server;
            client = new ApClient(new URI(uri), config.slot, config.password);

            client.setConnectionListener(new ApClient.ConnectionListener() {
                public void onConnected(String slotName, int slot, SlotData data, List<Long> checkedLocations) {
                    slotData = data;
                    state = ApState.load(stateFile);
                    client.setLastProcessedIndex(state.lastProcessedIndex);
                    unlockedItems.addAll(state.unlockedItems);
                    pureEssenceCount.set(state.pureEssenceCount);
                    claimedTierCounts().putAll(state.claimedTierCounts);

                    // Reconcile with the server's checked_locations so that goals claimed by
                    // a co-op partner (same slot, different machine) are reflected locally.
                    // Build reverse map: locationId → (goalType, tierIndex)
                    Map<Long, String[]> locToGoal = new HashMap<>();
                    for (Map.Entry<String, List<Long>> entry : data.goalLocationMap.entrySet()) {
                        List<Long> ids = entry.getValue();
                        for (int i = 0; i < ids.size(); i++) {
                            locToGoal.put(ids.get(i), new String[]{entry.getKey(), String.valueOf(i)});
                        }
                    }
                    boolean tierCountsChanged = false;
                    for (Long locId : checkedLocations) {
                        String[] info = locToGoal.get(locId);
                        if (info == null) continue;
                        String goalType = info[0];
                        int serverTierCount = Integer.parseInt(info[1]) + 1; // tierIndex + 1
                        Integer existing = claimedTierCounts().get(goalType);
                        if (existing == null || existing < serverTierCount) {
                            claimedTierCounts().put(goalType, serverTierCount);
                            tierCountsChanged = true;
                        }
                    }
                    if (tierCountsChanged) {
                        state.claimedTierCounts = new HashMap<>(claimedTierCounts());
                        state.save(stateFile);
                    }

                    System.out.println("[Archipelago] Connected — need " + data.pureEssenceRequired
                        + " / " + data.pureEssenceTotal + " Pure Essence to win");
                }
                public void onRefused(List<String> errors) {
                    System.out.println("[Archipelago] Connection refused: " + errors);
                }
                public void onDisconnected() {
                    System.out.println("[Archipelago] Disconnected");
                }
            });

            client.setItemReceivedListener(new ApClient.ItemReceivedListener() {
                public void onItemReceived(long itemId, int index) {
                    applyItem(itemId, index);
                }
            });

            client.connectBlocking();
        } catch (Exception e) {
            System.out.println("[Archipelago] Failed to start client: " + e.getMessage());
        }
    }

    private void tearDown() {
        if (client != null) {
            try { client.closeBlocking(); } catch (Exception ignored) {}
            client = null;
        }
        slotData = null;
        state = null;
        unlockedItems.clear();
        pureEssenceCount.set(0);
        pendingPerkNames.clear();
        pendingAncientRelics.set(0);
        pendingDivineSparks.set(0);
        pendingCacheOfSupplies.set(0);
        originalGoalAmounts.clear();
        activeSupplyDrops.clear();
        pendingBanners.clear();
        _claimedTierCounts.clear();
    }

    // -------------------------------------------------------------------------
    // Item application (called from WebSocket thread)
    // -------------------------------------------------------------------------

    public void applyItem(long itemId, int index) {
        if (slotData == null) return;

        String itemName = slotData.itemIdToName.get(String.valueOf(itemId));
        if (itemName == null) {
            System.out.println("[Archipelago] Unknown item id: " + itemId);
            return;
        }

        if (slotData.perkBundles.containsKey(itemName)) {
            pendingPerkNames.addAll(slotData.perkBundles.get(itemName));
            pendingBanners.add(itemName);
            System.out.println("[Archipelago] Queued perk bundle: " + itemName);

        } else if ("Pure Essence".equals(itemName)) {
            int count = pureEssenceCount.incrementAndGet();
            pendingBanners.add("Pure Essence (" + count + " / " + slotData.pureEssenceRequired + ")");
            System.out.println("[Archipelago] Pure Essence: " + count + " / " + slotData.pureEssenceRequired);
            if (count >= slotData.pureEssenceRequired) {
                System.out.println("[Archipelago] You have enough Pure Essence — goal complete!");
                if (client != null) client.sendGoalComplete();
            }

        } else if ("Divine Spark".equals(itemName)) {
            pendingDivineSparks.incrementAndGet();
            pendingBanners.add(itemName);
            System.out.println("[Archipelago] Received Divine Spark — queued for god XP");

        } else if ("Cache of Supplies".equals(itemName)) {
            pendingCacheOfSupplies.incrementAndGet();
            pendingBanners.add(itemName);
            System.out.println("[Archipelago] Received Cache of Supplies — queued for play state");

        } else if ("Ancient Relic".equals(itemName)) {
            pendingAncientRelics.incrementAndGet();
            pendingBanners.add(itemName);
            System.out.println("[Archipelago] Received Ancient Relic — queued for random perk");

        } else if (FILLER_ITEMS.contains(itemName)) {
            pendingBanners.add(itemName);
            System.out.println("[Archipelago] Received filler: " + itemName);

        } else {
            unlockedItems.add(itemName);
            pendingBanners.add(itemName);
            System.out.println("[Archipelago] Unlocked: " + itemName);
        }

        if (state != null && index > state.lastProcessedIndex) {
            state.lastProcessedIndex = index;
            state.unlockedItems = new java.util.HashSet<>(unlockedItems);
            state.pureEssenceCount = pureEssenceCount.get();
            state.save(stateFile);
        }
    }

    // -------------------------------------------------------------------------
    // Goal completion hook (called from patched Goal.setClaimed(), game thread)
    // -------------------------------------------------------------------------

    /**
     * Fires the AP location check for the next unclaimed tier of this goal.
     * Returns true if more tiers remain after this claim (the caller should
     * reset the goal so the player can re-complete it), or false if this was
     * the final tier or the goal is not in the AP location pool.
     */
    public boolean onGoalClaimed(String goalTypeName) {
        if (slotData == null) return false;

        List<Long> locationIds = slotData.goalLocationMap.get(goalTypeName);
        if (locationIds == null || locationIds.isEmpty()) return false;

        int tierIndex = claimedTierCounts().merge(goalTypeName, 1, Integer::sum) - 1;
        if (tierIndex >= locationIds.size()) return false;

        long locationId = locationIds.get(tierIndex);
        System.out.println("[Archipelago] Check: " + goalTypeName
            + " tier " + (tierIndex + 1) + " → id " + locationId);
        if (client != null) client.sendLocationCheck(locationId);

        if (state != null) {
            state.claimedTierCounts = new HashMap<>(claimedTierCounts());
            state.save(stateFile);
        }

        return tierIndex + 1 < locationIds.size();
    }

    // -------------------------------------------------------------------------
    // Game-thread drain (called from patched StateBase.update(), game thread)
    // Applies all effects queued by the WebSocket thread.
    // -------------------------------------------------------------------------

    public void drainPendingChanges() {
        drainBanners();
        drainDivineSparks();
        drainPerkQueue();
        drainCacheOfSupplies();
    }

    private void drainDivineSparks() {
        int sparks = pendingDivineSparks.getAndSet(0);
        if (sparks == 0) return;
        if (!rtr.system.Game.getCS().isModulesLoaded()) {
            pendingDivineSparks.addAndGet(sparks);  // put back — retry next tick
            return;
        }
        rtr.GodModule godModule = (rtr.GodModule) StateBase.getModule(ModuleType.GOD);
        if (godModule == null) {
            pendingDivineSparks.addAndGet(sparks);
            return;
        }
        godModule.increaseGodXP(500.0 * sparks, rtr.GodModule.GodXPType.UNLOCKED_GOAL);
        System.out.println("[Archipelago] Divine Spark: granted " + (500 * sparks) + " god XP");
    }

    private void drainBanners() {
        String bannerText;
        while ((bannerText = pendingBanners.poll()) != null) {
            rtr.console.Console.newBanner("Item Received", bannerText);
        }
    }

    private void drainPerkQueue() {
        if (!rtr.system.Game.getCS().isModulesLoaded()) return;  // state still loading — retry next tick

        PerkModule perkModule = (PerkModule) StateBase.getModule(ModuleType.PERK);
        if (perkModule == null) return;

        rtr.save.SaveModule saveModule = (rtr.save.SaveModule) StateBase.getModule(ModuleType.SAVE);
        rtr.save.WorldSavedGame worldSave = saveModule != null ? saveModule.getActiveWorldSave() : null;
        if (worldSave == null) return;  // no world save active yet — retry next tick

        int relics = pendingAncientRelics.getAndSet(0);
        if (relics > 0) {
            try {
                java.util.ArrayList<PerkModule.Perk> granted = perkModule.generateAndGiveNewRandomPerks(relics);
                worldSave.fullSaveSingleThreaded();
                requestPerkListRefresh();
                System.out.println("[Archipelago] Granted " + relics + " random perk(s) from Ancient Relic(s): " + granted.toString());
            } catch (Exception e) {
                System.out.println("[Archipelago] Failed to grant random perk(s): " + e.getMessage());
                e.printStackTrace();
            }
        }
        String perkName;
        while ((perkName = pendingPerkNames.poll()) != null) {
            try {
                PerkModule.Perk perk = PerkModule.Perk.valueOf(perkName);
                perkModule.addSinglePerk(perk);
                System.out.println("[Archipelago] Granted perk: " + perkName);
            } catch (Exception e) {
                System.out.println("[Archipelago] Failed to grant perk " + perkName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Drains Cache of Supplies items. Each item becomes a SupplyDrop that spawns
     * resources near the village castle over many ticks, mirroring loot box behaviour.
     * Guard: MapModule.isMapStarted() — only runs while a village map is active.
     */
    private void drainCacheOfSupplies() {
        rtr.map.MapModule mapModule = (rtr.map.MapModule) StateBase.getModule(ModuleType.MAP);
        if (mapModule == null || !mapModule.isMapStarted()) return;

        ObjectModule objectModule = (ObjectModule) StateBase.getModule(ModuleType.OBJECT);
        ResourceModule resourceModule = (ResourceModule) StateBase.getModule(ModuleType.RESOURCE);
        if (objectModule == null || resourceModule == null) return;

        // Enqueue any newly pending drops
        int pending = pendingCacheOfSupplies.getAndSet(0);
        for (int i = 0; i < pending; i++) {
            ObjectBase castle = objectModule.getFirstBaseTypeFound(rtr.map.MapTilesLoader.TileSet.CASTLE_1);
            int cx = castle != null ? castle.getCenterCoordinateX() : 128;
            int cy = castle != null ? castle.getCenterCoordinateY() : 128;

            // Pick a random category — not MISCELLANEOUS or TRASH
            ResourceModule.ResourceCategory[] cats = ResourceModule.ResourceCategory.values();
            ResourceModule.ResourceCategory cat;
            do {
                cat = cats[Utilities.randomInt(cats.length)];
            } while (cat == ResourceModule.ResourceCategory.MISCELLANEOUS
                  || cat == ResourceModule.ResourceCategory.TRASH);

            activeSupplyDrops.add(new SupplyDrop(cx, cy, cat, 30));
            System.out.println("[Archipelago] Cache of Supplies: starting drop at (" + cx + "," + cy
                + ") category=" + cat);
        }

        // Advance all active drops: spawn one item every 5 ticks (matching loot box pacing)
        for (SupplyDrop drop : activeSupplyDrops) {
            if (drop.resourcesLeft <= 0) continue;
            drop.tickAccum++;
            if (drop.tickAccum < 5) continue;
            drop.tickAccum = 0;

            // Pick a random ResourceType in this category
            ResourceModule.ResourceType[] allTypes = ResourceModule.ResourceType.values();
            ResourceModule.ResourceType typeOut = allTypes[Utilities.randomInt(allTypes.length)];
            int attempts = 0;
            while (typeOut.getTemplate().getResourceCategory() != drop.category && attempts++ < 200) {
                typeOut = allTypes[Utilities.randomInt(allTypes.length)];
            }
            if (typeOut.getTemplate().getResourceCategory() != drop.category) continue;

            int rx = drop.centerX + Utilities.randomInt(-6, 6);
            int ry = drop.centerY + Utilities.randomInt(-6, 6);
            try {
                rtr.utilities.OrderedPair o = resourceModule.findOpenSpace(rx, ry, MapData.BlockMapGroup.STANDARD);
                resourceModule.createResourceOnGround(typeOut, ResourceModule.ResourceColorSet.DEFAULT, o.getX(), o.getY());
                drop.resourcesLeft--;
            } catch (Exception e) {
                drop.resourcesLeft--;  // don't get stuck if placement fails
            }
        }

        // Remove finished drops
        activeSupplyDrops.removeIf(d -> d.resourcesLeft <= 0);
    }

    // -------------------------------------------------------------------------
    // Accessors for gate patches
    // -------------------------------------------------------------------------

    /**
     * Returns the per-tier goalAmountRequired for a multi-tier AP goal: originalAmount / tierCount.
     * For goals not in the AP pool or with only 1 tier, returns currentAmount unchanged.
     *
     * Called from the GoalCompletePatch on Goal.addAmount() (and Goal.load()) so that each tier
     * only requires a fraction of the normal completion threshold. The first call per goal type
     * captures the original (constructor-set) amount via putIfAbsent before any division occurs.
     */
    public int getAdjustedAmountRequired(String goalType, int currentAmount) {
        if (slotData == null) return currentAmount;
        List<Long> locationIds = slotData.goalLocationMap.get(goalType);
        if (locationIds == null || locationIds.size() <= 1) return currentAmount;

        // Capture the original amount the first time we see this goal (putIfAbsent is a no-op
        // on subsequent calls, so the stored value is always the pre-division original).
        originalGoalAmounts.putIfAbsent(goalType, currentAmount);
        int original = originalGoalAmounts.get(goalType);
        return Math.max(1, original / locationIds.size());
    }

    /** Returns true if the building/spell with this name has been received, or if not connected. */
    public boolean isUnlocked(String itemName) {
        return slotData == null || unlockedItems.contains(itemName);
    }

    /**
     * Returns true if this goal type has been claimed at least once in the current AP session.
     * Used by GoalCompletePatch to restore completedPreviously after a save/reload, since
     * completedPreviously is not persisted in GoalModule's save data.
     */
    public boolean wasGoalEverClaimed(String goalType) {
        return _claimedTierCounts.getOrDefault(goalType, 0) >= 1;
    }

    /**
     * Returns a tier progress suffix like " [1/4]" for multi-tier goals, or "" for
     * single-tier goals or goals not in the location map. Claimed count is how many
     * tiers have been sent as location checks so far.
     */
    public String getGoalTierSuffix(String goalType) {
        if (slotData == null) return "";
        List<Long> locationIds = slotData.goalLocationMap.get(goalType);
        if (locationIds == null || locationIds.size() <= 1) return "";
        int claimed = _claimedTierCounts.getOrDefault(goalType, 0);
        return " [" + claimed + "/" + locationIds.size() + "]";
    }

    public ApClient getClient() { return client; }
    public SlotData getSlotData() { return slotData; }
    public int getPureEssenceCount() { return pureEssenceCount.get(); }

    private final Map<String, Integer> _claimedTierCounts = new ConcurrentHashMap<>();
    private Map<String, Integer> claimedTierCounts() { return _claimedTierCounts; }
}
