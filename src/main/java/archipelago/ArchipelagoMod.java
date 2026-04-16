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
            Collections.singletonList(new GoalCompletePatch()));
        patches.put("rtr/influence/SpellBase",
            Collections.singletonList(new SpellGatePatch()));
        patches.put("rtr/gui/buttons/GUIPanelButton",
            Collections.singletonList(new BuildingGatePatch()));
        patches.put("rtr/states/WorldMapState",
            Collections.singletonList(new WorldMapStatePatch()));
        patches.put("rtr/states/StateBase",
            Collections.singletonList(new StateBasePatch()));

        ProfileDirPatch profileDirPatch = new ProfileDirPatch();
        String[] profileDirClasses = {
            "rtr/ProfileModule",
            "rtr/SettingsParser",
            "rtr/gui/states/WorldMapGUIData",
            "rtr/gui/states/mainmenu/SelectProfilePanel",
            "rtr/gui/states/shared/SettingsPanel",
            "rtr/gui/states/worldmap/GameModeConfigPanel",
            "rtr/help/HelpModule",
            "rtr/save/RegionalSavedGame",
            "rtr/save/SaveModule",
            "rtr/save/SavedGamesHandler",
            "rtr/save/WorldSavedGame",
            "rtr/states/MapEditorState",
            "rtr/states/MainMenuState",
            "rtr/states/PlayState",
            "rtr/system/gamemodetemplates/GameModeTemplateBase",
            "rtr/utilities/Utilities",
        };
        for (String cls : profileDirClasses) {
            patches.put(cls, new ArrayList<>(Collections.singletonList(profileDirPatch)));
        }
        patches.get("rtr/ProfileModule").add(new ProfileLoadPatch());
        patches.get("rtr/states/MainMenuState").add(new MainMenuStatePatch());
        patches.put("rtr/gui/states/mainmenu/NewProfilePanel",
            Collections.singletonList(new NewProfilePanelPatch()));
        patches.put("rtr/gui/states/mainmenu/MainMenuPanel",
            Collections.singletonList(new MainMenuPanelPatch()));

        return patches;
    }
}
