package thomjap.playerbeacon.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import thomjap.playerbeacon.PlayerBeaconClient;
import thomjap.playerbeacon.config.BeaconConfig;
import thomjap.playerbeacon.model.Waypoint;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EMITTER-ONLY WebSocket client: sends its position to the relay and ignores
 * everything incoming (no display of others). Reconnects automatically.
 */
public class BeaconRelay {
	private static final Gson GSON = new Gson();

	private final BeaconConfig config;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ScheduledExecutorService scheduler =
			Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "PlayerBeacon-Relay");
				t.setDaemon(true);
				return t;
			});

	private final AtomicBoolean connecting = new AtomicBoolean(false);
	private volatile boolean shouldRun = false;
	private volatile WebSocket webSocket;

	public BeaconRelay(BeaconConfig config) {
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
							PlayerBeaconClient.LOGGER.warn("Failed to connect to the relay: {}", err.toString());
							scheduleReconnect();
						} else {
							this.webSocket = ws;
							PlayerBeaconClient.LOGGER.info("Beacon connected to the relay {}", config.serverUrl);
						}
					});
		} catch (Exception e) {
			connecting.set(false);
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (shouldRun) {
			scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
		}
	}

	/** Broadcast waypoints message (all shared). */
	private JsonObject waypointsMessage() {
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "waypoints");
		msg.addProperty("room", config.room);
		msg.addProperty("owner", PlayerBeaconClient.selfName());
		JsonArray arr = new JsonArray();
		for (Waypoint w : config.waypoints) {
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
		try {
			ws.sendText(GSON.toJson(waypointsMessage()), true);
		} catch (Exception e) {
			PlayerBeaconClient.LOGGER.debug("Failed to send waypoints", e);
		}
	}

	/** Sends the local player position (nothing is received back). */
	public void sendPosition(String name, double x, double y, double z, String dim) {
		WebSocket ws = this.webSocket;
		if (ws == null || !isConnected()) {
			return;
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "update");
		msg.addProperty("room", config.room);
		msg.addProperty("name", name);
		msg.addProperty("x", x);
		msg.addProperty("y", y);
		msg.addProperty("z", z);
		msg.addProperty("dim", dim);
		try {
			ws.sendText(GSON.toJson(msg), true);
		} catch (Exception e) {
			PlayerBeaconClient.LOGGER.debug("Failed to send position", e);
		}
	}

	/** Listener: we consume incoming frames but IGNORE them entirely. */
	private class Listener implements WebSocket.Listener {
		@Override
		public void onOpen(WebSocket ws) {
			try {
				ws.sendText(GSON.toJson(waypointsMessage()), true);
			} catch (Exception ignored) {
			}
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			ws.request(1); // we read to avoid blocking, but do nothing with the content
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			BeaconRelay.this.webSocket = null;
			scheduleReconnect();
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			PlayerBeaconClient.LOGGER.warn("Relay error: {}", error.toString());
			BeaconRelay.this.webSocket = null;
			scheduleReconnect();
		}
	}
}
