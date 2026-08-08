package thomjap.playertracker.net;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.model.TrackedPlayer;
import thomjap.playertracker.model.Waypoint;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket client to the relay server.
 *
 * <p>Uses {@link java.net.http.WebSocket} (bundled with the JDK, no extra
 * dependency). Reconnects automatically on disconnect.
 */
public class RelayClient {
	private static final Gson GSON = new Gson();

	private final TrackerConfig config;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ScheduledExecutorService scheduler =
			Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "PlayerTracker-Relay");
				t.setDaemon(true);
				return t;
			});

	private final AtomicBoolean connecting = new AtomicBoolean(false);
	private volatile boolean shouldRun = false;
	private volatile WebSocket webSocket;

	/** Positions received from other players, indexed by username. Thread-safe. */
	public final ConcurrentHashMap<String, TrackedPlayer> players = new ConcurrentHashMap<>();
	/** Waypoints shared by other room members (immutable snapshot). */
	public volatile List<Waypoint> sharedWaypoints = List.of();

	public RelayClient(TrackerConfig config) {
		this.config = config;
	}

	public void start() {
		if (shouldRun) {
			return;
		}
		shouldRun = true;
		connect();
	}

	public void stop() {
		shouldRun = false;
		WebSocket ws = this.webSocket;
		this.webSocket = null;
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "stop");
			} catch (Exception ignored) {
			}
		}
		players.clear();
		sharedWaypoints = List.of();
	}

	public boolean isConnected() {
		WebSocket ws = this.webSocket;
		return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
	}

	private void connect() {
		if (!shouldRun || !connecting.compareAndSet(false, true)) {
			return;
		}
		try {
			httpClient.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.buildAsync(URI.create(config.serverUrl), new Listener())
					.whenComplete((ws, err) -> {
						connecting.set(false);
						if (err != null) {
							PlayerTrackerClient.LOGGER.warn("Failed to connect to the relay: {}", err.toString());
							scheduleReconnect();
						} else {
							this.webSocket = ws;
							PlayerTrackerClient.LOGGER.info("Connected to the relay {}", config.serverUrl);
						}
					});
		} catch (Exception e) {
			connecting.set(false);
			PlayerTrackerClient.LOGGER.warn("Relay connection error: {}", e.toString());
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (shouldRun) {
			scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
		}
	}

	/** Base JSON message (type + room), common to all sends. */
	private JsonObject baseMessage(String type) {
		JsonObject msg = new JsonObject();
		msg.addProperty("type", type);
		msg.addProperty("room", config.room);
		return msg;
	}

	/** Sends a JSON message over the connection (silent if closed/error). */
	private void send(WebSocket ws, JsonObject msg) {
		if (ws == null) {
			return;
		}
		try {
			ws.sendText(GSON.toJson(msg), true);
		} catch (Exception e) {
			PlayerTrackerClient.LOGGER.debug("WebSocket send failed", e);
		}
	}

	/**
	 * Subscribes to the room without publishing a position (stealth mode): asks the
	 * server to remove us from others' radar immediately.
	 */
	public void sendSubscribe() {
		WebSocket ws = this.webSocket;
		if (ws == null || !isConnected()) {
			return;
		}
		send(ws, baseMessage("subscribe"));
	}

	/** Builds the player's waypoints message (for the web map). */
	private JsonObject waypointsMessage() {
		JsonObject msg = baseMessage("waypoints");
		msg.addProperty("owner", PlayerTrackerClient.selfName());
		JsonArray arr = new JsonArray();
		for (Waypoint w : config.waypoints) {
			if (!w.shared) {
				continue; // private: never sent
			}
			JsonObject o = new JsonObject();
			o.addProperty("name", w.name);
			o.addProperty("x", w.x);
			o.addProperty("y", w.y);
			o.addProperty("z", w.z);
			o.addProperty("dim", w.dim);
			o.addProperty("color", w.color);
			arr.add(o);
		}
		msg.add("waypoints", arr);
		return msg;
	}

	/** Sends (or updates) the waypoint list to the relay. */
	public void sendWaypoints() {
		WebSocket ws = this.webSocket;
		if (ws == null || !isConnected()) {
			return;
		}
		send(ws, waypointsMessage());
	}

	/** Sends the local player position to the relay. */
	public void sendPosition(String name, double x, double y, double z, String dim) {
		WebSocket ws = this.webSocket;
		if (ws == null || !isConnected()) {
			return;
		}
		JsonObject msg = baseMessage("update");
		msg.addProperty("name", name);
		msg.addProperty("x", x);
		msg.addProperty("y", y);
		msg.addProperty("z", z);
		msg.addProperty("dim", dim);
		send(ws, msg);
	}

	private void handleMessage(String text) {
		try {
			JsonObject obj = GSON.fromJson(text, JsonObject.class);
			if (obj == null || !obj.has("type") || !"players".equals(obj.get("type").getAsString())) {
				return;
			}
			JsonArray arr = obj.getAsJsonArray("players");
			String self = PlayerTrackerClient.selfName();
			Set<String> seen = new HashSet<>();
			for (var el : arr) {
				JsonObject p = el.getAsJsonObject();
				String name = p.get("name").getAsString();
				if (name.equals(self)) {
					continue; // we don't track ourselves
				}
				players.put(name, new TrackedPlayer(
						name,
						p.get("x").getAsDouble(),
						p.get("y").getAsDouble(),
						p.get("z").getAsDouble(),
						p.get("dim").getAsString()));
				seen.add(name);
			}
			players.keySet().removeIf(k -> !seen.contains(k));

			// Waypoints shared by other room members.
			List<Waypoint> shared = new ArrayList<>();
			if (obj.has("waypoints") && obj.get("waypoints").isJsonArray()) {
				for (var el : obj.getAsJsonArray("waypoints")) {
					JsonObject o = el.getAsJsonObject();
					String owner = o.has("owner") && !o.get("owner").isJsonNull()
							? o.get("owner").getAsString() : null;
					if (owner != null && owner.equals(self)) {
						continue; // mine are already shown locally
					}
					Waypoint w = new Waypoint(o.get("name").getAsString(),
							o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
							o.get("dim").getAsString(), o.get("color").getAsInt());
					w.owner = owner;
					shared.add(w);
				}
			}
			sharedWaypoints = List.copyOf(shared);
		} catch (Exception e) {
			PlayerTrackerClient.LOGGER.debug("Invalid message: {}", text);
		}
	}

	/** WebSocket listener: reassembles frames and handles events. */
	private class Listener implements WebSocket.Listener {
		private final StringBuilder buffer = new StringBuilder();

		@Override
		public void onOpen(WebSocket webSocket) {
			// Subscribe to the room: we receive others even without sharing our position.
			send(webSocket, baseMessage("subscribe"));
			send(webSocket, waypointsMessage()); // share our waypoints (web map)
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String full = buffer.toString();
				buffer.setLength(0);
				handleMessage(full);
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			PlayerTrackerClient.LOGGER.info("Relay closed: {} {}", statusCode, reason);
			RelayClient.this.webSocket = null;
			players.clear();
			sharedWaypoints = List.of();
			scheduleReconnect();
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			PlayerTrackerClient.LOGGER.warn("Relay error: {}", error.toString());
			RelayClient.this.webSocket = null;
			scheduleReconnect();
		}
	}
}
