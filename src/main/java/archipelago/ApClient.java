package archipelago;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ApClient extends WebSocketClient {

    public interface ItemReceivedListener {
        void onItemReceived(long itemId, int absoluteIndex);
    }

    public interface ConnectionListener {
        void onConnected(String slotName, int slot, SlotData slotData, List<Long> checkedLocations);
        void onRefused(List<String> errors);
        void onDisconnected();
    }

    private static final String GAME_NAME = "Rise to Ruins";
    private static final Gson GSON = new Gson();

    private final String slotName;
    private final String password;
    private ItemReceivedListener itemListener;
    private ConnectionListener connectionListener;

    // Index of the last item we've already processed, so we don't re-grant on reconnect
    private int lastProcessedIndex = -1;

    public ApClient(URI serverUri, String slotName, String password) {
        super(serverUri);
        this.slotName = slotName;
        this.password = password;
    }

    public void setItemReceivedListener(ItemReceivedListener listener) {
        this.itemListener = listener;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void setLastProcessedIndex(int index) {
        this.lastProcessedIndex = index;
    }

    // -------------------------------------------------------------------------
    // WebSocketClient callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onOpen(ServerHandshake handshake) {
        // Server will send RoomInfo first; we reply with Connect
    }

    @Override
    public void onMessage(String raw) {
        JsonArray messages = GSON.fromJson(raw, JsonArray.class);
        for (JsonElement el : messages) {
            JsonObject msg = el.getAsJsonObject();
            String cmd = msg.get("cmd").getAsString();
            handleCommand(cmd, msg);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[Archipelago] Disconnected: " + reason);
        if (connectionListener != null) connectionListener.onDisconnected();
    }

    @Override
    public void onError(Exception e) {
        System.err.println("[Archipelago] WebSocket error: " + e.getMessage());
    }

    // -------------------------------------------------------------------------
    // Protocol handling
    // -------------------------------------------------------------------------

    private void handleCommand(String cmd, JsonObject msg) {
        switch (cmd) {
            case "RoomInfo":
                sendConnect();
                break;
            case "Connected":
                handleConnected(msg);
                break;
            case "ConnectionRefused":
                handleConnectionRefused(msg);
                break;
            case "ReceivedItems":
                handleReceivedItems(msg);
                break;
            default:
                // ignore PrintJSON, DataPackage, etc. for now
                break;
        }
    }

    private void sendConnect() {
        JsonObject msg = new JsonObject();
        msg.addProperty("cmd", "Connect");
        msg.addProperty("game", GAME_NAME);
        msg.addProperty("name", slotName);
        msg.addProperty("password", password != null ? password : "");
        msg.addProperty("items_handling", 7); // all items
        msg.addProperty("uuid", java.util.UUID.randomUUID().toString());
        msg.addProperty("slot_data", true);

        JsonObject version = new JsonObject();
        version.addProperty("major", 0);
        version.addProperty("minor", 5);
        version.addProperty("build", 0);
        version.addProperty("class", "Version");
        msg.add("version", version);

        msg.add("tags", new JsonArray());

        JsonArray envelope = new JsonArray();
        envelope.add(msg);
        send(GSON.toJson(envelope));
    }

    private void handleConnected(JsonObject msg) {
        int slotNum = msg.has("slot") ? msg.get("slot").getAsInt() : -1;
        SlotData slotData = msg.has("slot_data")
            ? SlotData.parse(msg.getAsJsonObject("slot_data"))
            : null;

        // checked_locations: location IDs the server already considers checked for this slot.
        // Includes checks made by co-op partners with the same slot.
        List<Long> checkedLocations = new ArrayList<>();
        if (msg.has("checked_locations")) {
            for (JsonElement el : msg.getAsJsonArray("checked_locations")) {
                checkedLocations.add(el.getAsLong());
            }
        }

        System.out.println("[Archipelago] Connected as " + slotName + " (slot " + slotNum
            + ", " + checkedLocations.size() + " locations already checked)");
        if (connectionListener != null)
            connectionListener.onConnected(slotName, slotNum, slotData, checkedLocations);
    }

    private void handleConnectionRefused(JsonObject msg) {
        List<String> errors = new ArrayList<String>();
        if (msg.has("errors")) {
            for (JsonElement e : msg.getAsJsonArray("errors")) {
                errors.add(e.getAsString());
            }
        }
        System.err.println("[Archipelago] Connection refused: " + errors);
        if (connectionListener != null) connectionListener.onRefused(errors);
    }

    private void handleReceivedItems(JsonObject msg) {
        int index = msg.get("index").getAsInt();
        JsonArray items = msg.getAsJsonArray("items");

        for (int i = 0; i < items.size(); i++) {
            int absoluteIndex = index + i;
            if (absoluteIndex <= lastProcessedIndex) continue; // already applied

            JsonObject item = items.get(i).getAsJsonObject();
            long itemId = item.get("item").getAsLong();

            if (itemListener != null) {
                itemListener.onItemReceived(itemId, absoluteIndex);
            }
            lastProcessedIndex = absoluteIndex;
        }
    }

    // -------------------------------------------------------------------------
    // Outgoing messages
    // -------------------------------------------------------------------------

    public void sendLocationCheck(long locationId) {
        JsonArray locations = new JsonArray();
        locations.add(locationId);

        JsonObject msg = new JsonObject();
        msg.addProperty("cmd", "LocationChecks");
        msg.add("locations", locations);

        JsonArray envelope = new JsonArray();
        envelope.add(msg);
        send(GSON.toJson(envelope));
    }

    public void sendGoalComplete() {
        JsonObject msg = new JsonObject();
        msg.addProperty("cmd", "StatusUpdate");
        msg.addProperty("status", 30); // 30 = goal complete

        JsonArray envelope = new JsonArray();
        envelope.add(msg);
        send(GSON.toJson(envelope));
        System.out.println("[Archipelago] Sent goal complete");
    }

    public void sendLocationChecks(List<Long> locationIds) {
        JsonArray locations = new JsonArray();
        for (Long id : locationIds) locations.add(id);

        JsonObject msg = new JsonObject();
        msg.addProperty("cmd", "LocationChecks");
        msg.add("locations", locations);

        JsonArray envelope = new JsonArray();
        envelope.add(msg);
        send(GSON.toJson(envelope));
    }
}
