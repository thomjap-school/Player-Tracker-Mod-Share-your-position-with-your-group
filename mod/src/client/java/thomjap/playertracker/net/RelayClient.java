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
 * Client WebSocket vers le serveur relais.
 *
 * <p>Utilise {@link java.net.http.WebSocket} (inclus dans le JDK, aucune
 * dépendance supplémentaire). Se reconnecte automatiquement en cas de coupure.
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

	/** Positions reçues des autres joueurs, indexées par pseudo. Thread-safe. */
	public final ConcurrentHashMap<String, TrackedPlayer> players = new ConcurrentHashMap<>();
	/** Waypoints partagés par les autres membres du salon (snapshot immuable). */
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
							PlayerTrackerClient.LOGGER.warn("Connexion au relais échouée : {}", err.toString());
							scheduleReconnect();
						} else {
							this.webSocket = ws;
							PlayerTrackerClient.LOGGER.info("Connecté au relais {}", config.serverUrl);
						}
					});
		} catch (Exception e) {
			connecting.set(false);
			PlayerTrackerClient.LOGGER.warn("Erreur de connexion au relais : {}", e.toString());
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (shouldRun) {
			scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
		}
	}

	/** Message JSON de base (type + salon), commun à tous les envois. */
	private JsonObject baseMessage(String type) {
		JsonObject msg = new JsonObject();
		msg.addProperty("type", type);
		msg.addProperty("room", config.room);
		return msg;
	}

	/** Envoie un message JSON sur la connexion (silencieux si fermée/erreur). */
	private void send(WebSocket ws, JsonObject msg) {
		if (ws == null) {
			return;
		}
		try {
			ws.sendText(GSON.toJson(msg), true);
		} catch (Exception e) {
			PlayerTrackerClient.LOGGER.debug("Envoi WebSocket échoué", e);
		}
	}

	/**
	 * S'abonne au salon sans publier de position (mode furtif) : demande au
	 * serveur de nous retirer immédiatement du radar des autres.
	 */
	public void sendSubscribe() {
		WebSocket ws = this.webSocket;
		if (ws == null || !isConnected()) {
			return;
		}
		send(ws, baseMessage("subscribe"));
	}

	/** Construit le message des waypoints du joueur (pour la carte web). */
	private JsonObject waypointsMessage() {
		JsonObject msg = baseMessage("waypoints");
		msg.addProperty("owner", PlayerTrackerClient.selfName());
		JsonArray arr = new JsonArray();
		for (Waypoint w : config.waypoints) {
			if (!w.shared) {
				continue; // privé : jamais envoyé
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

	/** Envoie (ou met à jour) la liste des waypoints au relais. */
	public void sendWaypoints() {
		WebSocket ws = this.webSocket;
		if (ws == null || !isConnected()) {
			return;
		}
		send(ws, waypointsMessage());
	}

	/** Envoie la position du joueur local au relais. */
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
					continue; // on ne se suit pas soi-même
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

			// Waypoints partagés par les autres membres du salon.
			List<Waypoint> shared = new ArrayList<>();
			if (obj.has("waypoints") && obj.get("waypoints").isJsonArray()) {
				for (var el : obj.getAsJsonArray("waypoints")) {
					JsonObject o = el.getAsJsonObject();
					String owner = o.has("owner") && !o.get("owner").isJsonNull()
							? o.get("owner").getAsString() : null;
					if (owner != null && owner.equals(self)) {
						continue; // les miens sont déjà affichés localement
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
			PlayerTrackerClient.LOGGER.debug("Message invalide : {}", text);
		}
	}

	/** Listener du WebSocket : réassemble les trames et gère les événements. */
	private class Listener implements WebSocket.Listener {
		private final StringBuilder buffer = new StringBuilder();

		@Override
		public void onOpen(WebSocket webSocket) {
			// S'abonner au salon : on reçoit les autres même sans partager sa position.
			send(webSocket, baseMessage("subscribe"));
			send(webSocket, waypointsMessage()); // partager ses waypoints (carte web)
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
			PlayerTrackerClient.LOGGER.info("Relais fermé : {} {}", statusCode, reason);
			RelayClient.this.webSocket = null;
			players.clear();
			sharedWaypoints = List.of();
			scheduleReconnect();
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			PlayerTrackerClient.LOGGER.warn("Erreur du relais : {}", error.toString());
			RelayClient.this.webSocket = null;
			scheduleReconnect();
		}
	}
}
