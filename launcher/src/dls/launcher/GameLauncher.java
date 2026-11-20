package dls.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the java command for an installed game and starts it (offline nick, chosen RAM).
 */
public final class GameLauncher {
	private static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z_]+)}");

	private GameLauncher() {
	}

	/** Java to run the game with: the path from the settings, otherwise the Java running the launcher. */
	public static Path javaExecutable(LauncherConfig config) {
		if (config.javaPath != null && !config.javaPath.isBlank()) {
			return Path.of(config.javaPath);
		}

		Path bin = Path.of(System.getProperty("java.home"), "bin");
		Path javaw = bin.resolve("javaw.exe");
		return Files.exists(javaw) ? javaw : bin.resolve(File.separatorChar == '\\' ? "java.exe" : "java");
	}

	public static Process start(LauncherConfig config, GameInstaller.Installed game) throws IOException {
		if ((config.javaPath == null || config.javaPath.isBlank()) && Runtime.version().feature() < game.javaMajor()) {
			throw new IOException("Для Minecraft " + GameInstaller.MC + " нужна Java " + game.javaMajor()
					+ " (сейчас " + Runtime.version().feature() + "). Укажи путь к Java в настройках.");
		}

		String nick = config.nick;
		String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
		String sep = File.pathSeparator;
		StringBuilder cp = new StringBuilder();

		for (Path p : game.classpath()) {
			if (cp.length() > 0) {
				cp.append(sep);
			}

			cp.append(p.toAbsolutePath());
		}

		Map<String, String> vars = new HashMap<>();
		vars.put("auth_player_name", nick);
		vars.put("version_name", game.versionId());
		vars.put("game_directory", game.gameDir().toAbsolutePath().toString());
		vars.put("assets_root", game.gameDir().resolve("assets").toAbsolutePath().toString());
		vars.put("assets_index_name", game.assetIndex());
		vars.put("auth_uuid", uuid);
		vars.put("auth_access_token", "0");
		vars.put("auth_session", "0");
		vars.put("clientid", "0");
		vars.put("auth_xuid", "0");
		vars.put("user_type", "legacy");
		vars.put("user_properties", "{}");
		vars.put("version_type", "release");
		vars.put("natives_directory", game.nativesDir().toAbsolutePath().toString());
		vars.put("launcher_name", "dayener-dls");
		vars.put("launcher_version", "1.0");
		vars.put("classpath", cp.toString());
		vars.put("classpath_separator", sep);
		vars.put("library_directory", game.gameDir().resolve("libraries").toAbsolutePath().toString());

		List<String> cmd = new ArrayList<>();
		cmd.add(javaExecutable(config).toString());
		cmd.add("-Xmx" + config.ramMb + "M");
		cmd.add("-Xms" + Math.min(1024, config.ramMb) + "M");
		cmd.add("-XX:+UseG1GC");
		addArgs(cmd, Json.obj(game.vanilla().get("arguments")).get("jvm"), vars);
		addArgs(cmd, Json.obj(game.fabric().get("arguments")).get("jvm"), vars);

		// Older version files have no jvm arguments: make sure the essentials are there.
		if (!cmd.contains("-cp") && !cmd.contains("-classpath")) {
			cmd.add("-Djava.library.path=" + vars.get("natives_directory"));
			cmd.add("-cp");
			cmd.add(cp.toString());
		}

		cmd.add(game.mainClass());
		addArgs(cmd, Json.obj(game.vanilla().get("arguments")).get("game"), vars);
		addArgs(cmd, Json.obj(game.fabric().get("arguments")).get("game"), vars);

		Path logs = game.gameDir().resolve("logs");
		Files.createDirectories(logs);

		ProcessBuilder builder = new ProcessBuilder(cmd)
				.directory(game.gameDir().toFile())
				.redirectErrorStream(true)
				.redirectOutput(logs.resolve("launcher-output.log").toFile());
		return builder.start();
	}

	/** Adds Mojang-style arguments: plain strings, or {rules, value} objects allowed for this OS. */
	private static void addArgs(List<String> cmd, Object list, Map<String, String> vars) {
		for (Object o : Json.arr(list)) {
			if (o instanceof String s) {
				cmd.add(fill(s, vars));
				continue;
			}

			Map<String, Object> entry = Json.obj(o);

			if (!GameInstaller.Rules.allowed(Json.arr(entry.get("rules")))) {
				continue;
			}

			Object value = entry.get("value");

			if (value instanceof String s) {
				cmd.add(fill(s, vars));
			} else {
				for (Object v : Json.arr(value)) {
					cmd.add(fill(String.valueOf(v), vars));
				}
			}
		}
	}

	private static String fill(String s, Map<String, String> vars) {
		Matcher m = VAR.matcher(s);
		StringBuilder out = new StringBuilder();

		while (m.find()) {
			String value = vars.getOrDefault(m.group(1), "");
			m.appendReplacement(out, Matcher.quoteReplacement(value));
		}

		m.appendTail(out);
		return out.toString();
	}
}
