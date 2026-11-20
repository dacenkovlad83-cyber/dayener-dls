package dls.launcher;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Launcher settings, saved as {@code %APPDATA%/dayener-dls/launcher.json}.
 */
public final class LauncherConfig {
	public String nick = "";
	public int tag = ThreadLocalRandom.current().nextInt(10000, 99999);
	public List<String> accounts = new ArrayList<>();
	public int ramMb = 4096;
	public String gameDir = defaultGameDir().toString();
	public String javaPath = "";
	public boolean closeOnLaunch = false;
	/** Install ViaFabric: join servers of other Minecraft versions. */
	public boolean viaFabric = true;
	/** Last day (yyyy-MM-dd) the gift roulette was spun (old format, kept for older config files). */
	public String lastGiftDay = "";
	/** When the gift roulette was last spun (epoch millis); the next spin is 24 hours later. */
	public long lastGiftAt = 0L;
	public List<String> gifts = new ArrayList<>();
	public long playSeconds = 0;

	public static Path home() {
		String appdata = System.getenv("APPDATA");
		Path base = appdata != null ? Path.of(appdata) : Path.of(System.getProperty("user.home"));
		return base.resolve("dayener-dls");
	}

	public static Path defaultGameDir() {
		return home().resolve("game");
	}

	private static Path file() {
		return home().resolve("launcher.json");
	}

	/** Physical memory in MB (for the RAM slider). */
	public static int systemRamMb() {
		try {
			var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			return (int) (os.getTotalMemorySize() / (1024 * 1024));
		} catch (Throwable ignored) {
			return 8192;
		}
	}

	public static LauncherConfig load() {
		LauncherConfig c = new LauncherConfig();

		try {
			if (Files.exists(file())) {
				Map<String, Object> m = Json.obj(Json.parse(Files.readString(file(), StandardCharsets.UTF_8)));
				c.nick = str(m.get("nick"), "");
				c.tag = (int) (m.get("tag") instanceof Number n ? n.longValue() : c.tag);
				c.ramMb = (int) Json.num(m.getOrDefault("ramMb", 4096.0));
				c.gameDir = str(m.get("gameDir"), c.gameDir);
				c.javaPath = str(m.get("javaPath"), "");
				c.closeOnLaunch = Boolean.TRUE.equals(m.get("closeOnLaunch"));
				c.viaFabric = !Boolean.FALSE.equals(m.get("viaFabric"));
				c.lastGiftDay = str(m.get("lastGiftDay"), "");
				c.playSeconds = Json.num(m.getOrDefault("playSeconds", 0.0));
				c.lastGiftAt = Json.num(m.getOrDefault("lastGiftAt", 0.0));

				// Older files only had the day: count from the start of that day.
				if (c.lastGiftAt == 0L && !c.lastGiftDay.isEmpty()) {
					try {
						c.lastGiftAt = java.time.LocalDate.parse(c.lastGiftDay).atStartOfDay(java.time.ZoneId.systemDefault())
								.toInstant().toEpochMilli();
					} catch (Exception ignored) {
						// bad date: gift available
					}
				}

				for (Object o : Json.arr(m.get("accounts"))) {
					c.accounts.add(String.valueOf(o));
				}

				for (Object o : Json.arr(m.get("gifts"))) {
					c.gifts.add(String.valueOf(o));
				}
			}
		} catch (Exception ignored) {
			// broken file: defaults
		}

		c.ramMb = Math.max(1024, Math.min(c.ramMb, Math.max(2048, systemRamMb() - 1024)));
		return c;
	}

	private static String str(Object o, String fallback) {
		return o == null ? fallback : o.toString();
	}

	public void save() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("nick", this.nick);
		m.put("tag", this.tag);
		m.put("accounts", this.accounts);
		m.put("ramMb", this.ramMb);
		m.put("gameDir", this.gameDir);
		m.put("javaPath", this.javaPath);
		m.put("closeOnLaunch", this.closeOnLaunch);
		m.put("viaFabric", this.viaFabric);
		m.put("lastGiftDay", this.lastGiftDay);
		m.put("lastGiftAt", this.lastGiftAt);
		m.put("gifts", this.gifts);
		m.put("playSeconds", this.playSeconds);

		try {
			Files.createDirectories(home());
			Files.writeString(file(), Json.write(m), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// nothing to do
		}
	}

	public static final long GIFT_PERIOD_MS = 24L * 60 * 60 * 1000;

	/** True while the gift roulette is still on its 24-hour cooldown. */
	public boolean giftOnCooldown() {
		return giftLeftMs() > 0;
	}

	/** Milliseconds until the next spin (0 = available now). */
	public long giftLeftMs() {
		return Math.max(0L, this.lastGiftAt + GIFT_PERIOD_MS - System.currentTimeMillis());
	}

	public static String formatLeft(long ms) {
		long s = ms / 1000;
		return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}

	/** Valid offline nick: 3–16 chars, latin letters, digits, underscore. */
	public static boolean validNick(String nick) {
		return nick != null && nick.matches("[A-Za-z0-9_]{3,16}");
	}
}
