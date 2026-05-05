package archipelago;

import archipelago.patches.*;
import rtrmodloader.api.ModPatch;
import rtrmodloader.api.RtRMod;

import java.util.*;

public class ArchipelagoMod implements RtRMod {

    @Override
    public String getId() {
        return "archipelago";
    }

    @Override
    public Map<String, List<ModPatch>> getPatches() {
        Map<String, List<ModPatch>> patches = new HashMap<>();
        patches.put("rtr/system/Game",
            Collections.singletonList(new ArchipelagoInitPatch()));
        patches.put("rtr/goal/Goal",
            Arrays.asList(new GoalCompletePatch(), new GoalLockPatch()));
        patches.put("rtr/goal/GoalModule",
            Collections.singletonList(new GoalModulePatch()));
        patches.put("rtr/influence/SpellBase",
            Collections.singletonList(new SpellGatePatch()));
        patches.put("rtr/gui/buttons/GUIPanelButton",
            Collections.singletonList(new BuildingGatePatch()));
        patches.put("rtr/gui/buttons/GUIButtonGoalStats",
            Collections.singletonList(new GoalStatsPatch()));
        patches.put("rtr/states/WorldMapState",
            Collections.singletonList(new WorldMapStatePatch()));
        patches.put("rtr/states/StateBase",
            Collections.singletonList(new StateBasePatch()));
        patches.put("rtr/ProfileModule",
            Collections.singletonList(new ProfileLoadPatch()));
        patches.put("rtr/states/MainMenuState", 
            Collections.singletonList(new MainMenuStatePatch()));
        patches.put("rtr/gui/states/mainmenu/NewProfilePanel",
            Collections.singletonList(new NewProfilePanelPatch()));
        patches.put("rtr/gui/states/mainmenu/MainMenuPanel",
            Collections.singletonList(new MainMenuPanelPatch()));

        return patches;
    }
}
