package dls.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs everything the game needs into the game folder: Minecraft {@value #MC} (client, libraries,
 * assets), the newest stable Fabric loader, Fabric API and the dayener dls mod that ships inside the launcher.
 * Files that are already there with the right checksum are skipped, so later starts are fast.
 */
public final class GameInstaller {
	public static final String MC = "26.2";
	public static final String MOD_FILE = "dayener-dls.jar";

	private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/";
	private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";
	private static final String ASSETS_URL = "https://resources.download.minecraft.net/";
	private static final String FABRIC_API_ID = "P7dR8mSH";

	/** Progress: a short text and 0..1. */
	public interface Progress {
		void update(String stage, double fraction);
	}

	/** Everything the launch step needs. */
	public record Installed(Path gameDir, String versionId, String mainClass, List<Path> classpath, Path nativesDir,
			String assetIndex, Map<String, Object> vanilla, Map<String, Object> fabric, int javaMajor) {
	}

	private GameInstaller() {
	}

	public static Installed install(Path gameDir, boolean viaFabric, Progress progress) throws IOException {
		Path versions = gameDir.resolve("versions");
		Path libraries = gameDir.resolve("libraries");
		Path assets = gameDir.resolve("assets");

		// 1. Minecraft version json + client jar.
		progress.update("Получаю список версий…", 0.01);
		Map<String, Object> manifest = Json.obj(Json.parse(Http.getString(MANIFEST)));
		String versionUrl = null;

		for (Object v : Json.arr(manifest.get("versions"))) {
			Map<String, Object> version = Json.obj(v);

			if (MC.equals(version.get("id"))) {
				versionUrl = Json.str(version.get("url"));
			}
		}

		if (versionUrl == null) {
			throw new IOException("Версия " + MC + " не найдена у Mojang");
		}

		Path versionJson = versions.resolve(MC).resolve(MC + ".json");
		Http.download(versionUrl, versionJson, null);
		Map<String, Object> vanilla = Json.obj(Json.parse(Files.readString(versionJson, StandardCharsets.UTF_8)));

		Map<String, Object> client = Json.obj(Json.obj(vanilla.get("downloads")).get("client"));
		Path clientJar = versions.resolve(MC).resolve(MC + ".jar");
		progress.update("Скачиваю Minecraft " + MC + "…", 0.04);
		Http.download(Json.str(client.get("url")), clientJar, Json.str(client.get("sha1")));

		// 2. Fabric loader profile.
		progress.update("Получаю Fabric…", 0.08);
		String loader = null;

		for (Object o : Json.arr(Json.parse(Http.getString(FABRIC_META + MC)))) {
			Map<String, Object> entry = Json.obj(Json.obj(o).get("loader"));

			if (Boolean.TRUE.equals(entry.get("stable")) || loader == null) {
				loader = Json.str(entry.get("version"));

				if (Boolean.TRUE.equals(entry.get("stable"))) {
					break;
				}
			}
		}

		if (loader == null) {
			throw new IOException("Fabric для " + MC + " не найден");
		}

		Map<String, Object> fabric = Json.obj(Json.parse(Http.getString(FABRIC_META + MC + "/" + loader + "/profile/json")));
		String versionId = Json.str(fabric.get("id"));
		Files.createDirectories(versions.resolve(versionId));
		Files.writeString(versions.resolve(versionId).resolve(versionId + ".json"), Json.write(fabric), StandardCharsets.UTF_8);

		// 3. Libraries (vanilla + Fabric; Fabric wins on duplicates).
		Path nativesDir = versions.resolve(MC).resolve("natives");
		Files.createDirectories(nativesDir);
		Map<String, Path> classpath = new LinkedHashMap<>();
		List<Job> jobs = new ArrayList<>();
		List<Path> nativeJars = new ArrayList<>();

		for (Object o : Json.arr(vanilla.get("libraries"))) {
			Map<String, Object> lib = Json.obj(o);

			if (!Rules.allowed(Json.arr(lib.get("rules")))) {
				continue;
			}

			Map<String, Object> downloads = Json.obj(lib.get("downloads"));
			Map<String, Object> artifact = Json.obj(downloads.get("artifact"));

			if (!artifact.isEmpty()) {
				Path path = libraries.resolve(Json.str(artifact.get("path")));
				jobs.add(new Job(Json.str(artifact.get("url")), path, Json.str(artifact.get("sha1"))));
				classpath.put(libKey(Json.str(lib.get("name"))), path);
			}

			// Old-style natives (separate classifier jars that must be unpacked).
			String nativeKey = Json.str(Json.obj(lib.get("natives")).get("windows"));

			if (nativeKey != null) {
				Map<String, Object> nat = Json.obj(Json.obj(downloads.get("classifiers")).get(nativeKey.replace("${arch}", "64")));

				if (!nat.isEmpty()) {
					Path path = libraries.resolve(Json.str(nat.get("path")));
					jobs.add(new Job(Json.str(nat.get("url")), path, Json.str(nat.get("sha1"))));
					nativeJars.add(path);
				}
			}
		}

		for (Object o : Json.arr(fabric.get("libraries"))) {
			Map<String, Object> lib = Json.obj(o);
			String name = Json.str(lib.get("name"));
			String base = lib.get("url") != null ? Json.str(lib.get("url")) : FABRIC_MAVEN;
			String rel = mavenPath(name);
			Path path = libraries.resolve(rel);
			jobs.add(new Job((base.endsWith("/") ? base : base + "/") + rel, path, Json.str(lib.get("sha1"))));
			classpath.put(libKey(name), path);
		}

		runJobs(jobs, progress, "Библиотеки", 0.10, 0.35);

		for (Path jar : nativeJars) {
			unpackNatives(jar, nativesDir);
		}

		// 4. Assets.
		Map<String, Object> indexInfo = Json.obj(vanilla.get("assetIndex"));
		String assetIndex = Json.str(indexInfo.get("id"));
		Path indexFile = assets.resolve("indexes").resolve(assetIndex + ".json");
		Http.download(Json.str(indexInfo.get("url")), indexFile, Json.str(indexInfo.get("sha1")));
		Map<String, Object> objects = Json.obj(Json.obj(Json.parse(Files.readString(indexFile, StandardCharsets.UTF_8))).get("objects"));
		List<Job> assetJobs = new ArrayList<>();

		for (Object o : objects.values()) {
			String hash = Json.str(Json.obj(o).get("hash"));
			String rel = hash.substring(0, 2) + "/" + hash;
			assetJobs.add(new Job(ASSETS_URL + rel, assets.resolve("objects").resolve(rel), hash));
		}

		runJobs(assetJobs, progress, "Ресурсы игры", 0.35, 0.90);

		// 5. Mods: Fabric API + dayener dls.
		installMods(gameDir.resolve("mods"), viaFabric, progress);

		List<Path> cp = new ArrayList<>(classpath.values());
		cp.add(clientJar);
		int javaMajor = (int) Json.num(Json.obj(vanilla.get("javaVersion")).get("majorVersion"));
		progress.update("Готово", 1.0);

		return new Installed(gameDir, versionId, Json.str(fabric.get("mainClass")), cp, nativesDir, assetIndex,
				vanilla, fabric, javaMajor);
	}

	/** ViaFabric: join servers of other versions (ViaVersion is built in). */
	private static final String VIAFABRIC_ID = "YlKdE5VK";
	/** Mod Menu: the "Моды" button with the mod list and mod settings (ViaVersion / ViaFabric too). */
	private static final String MODMENU_ID = "mOgUt4GM";

	/**
	 * ViaFabric's defaults hide its VIA button in "Сетевая игра" and keep translation off, so it looks like
	 * it is not there. Once per game folder: show the button and switch translation on. The chosen server
	 * version and everything else is left alone; later changes made in the game are kept.
	 */
	private static void setupViaFabric(Path gameDir) throws IOException {
		Path dir = gameDir.resolve("config").resolve("ViaFabric");
		Path marker = dir.resolve(".dayener-dls-setup");

		if (Files.exists(marker)) {
			return;
		}

		Files.createDirectories(dir);
		Path yml = dir.resolve("viafabric.yml");
		List<String> lines = Files.exists(yml) ? new ArrayList<>(Files.readAllLines(yml, StandardCharsets.UTF_8)) : new ArrayList<>();
		setYaml(lines, "enable-client-side", "true");
		setYaml(lines, "hide-button", "false");
		Files.write(yml, lines, StandardCharsets.UTF_8);
		Files.writeString(marker, "ViaFabric button and client-side translation enabled by the dayener dls launcher\n");
	}

	/** Sets a top-level "key: value" line, adding it when missing. */
	private static void setYaml(List<String> lines, String key, String value) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(key + ":")) {
				lines.set(i, key + ": " + value);
				return;
			}
		}

		lines.add(key + ": " + value);
	}

	/** A mod from Modrinth that the launcher keeps up to date: project id, display name, file-name prefix. */
	private record ModrinthMod(String id, String name, String prefix) {
	}

	private static void installMods(Path mods, boolean viaFabric, Progress progress) throws IOException {
		Files.createDirectories(mods);

		List<ModrinthMod> wanted = new ArrayList<>();
		wanted.add(new ModrinthMod(FABRIC_API_ID, "Fabric API", "fabric-api-"));
		wanted.add(new ModrinthMod(MODMENU_ID, "Mod Menu", "modmenu-"));
		List<String> keep = new ArrayList<>();

		if (viaFabric) {
			wanted.add(new ModrinthMod(VIAFABRIC_ID, "ViaFabric", "viafabric-"));
			setupViaFabric(mods.getParent());
		}

		double at = 0.91;

		for (ModrinthMod mod : wanted) {
			progress.update("Скачиваю " + mod.name() + "…", at);
			at += 0.03;
			keep.add(installModrinth(mods, mod));
		}

		// Remove old versions of these mods, ViaFabric when it was switched off, and old copies of our mod.
		try (var list = Files.list(mods)) {
			for (Path p : list.toList()) {
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				boolean oldManaged = (n.startsWith("fabric-api-") || n.startsWith("viafabric-") || n.startsWith("modmenu-"))
						&& !keep.contains(n);
				boolean oldOurs = n.startsWith("triggerbot") || (n.startsWith("dayener-dls") && !n.equals(MOD_FILE));

				if (oldManaged || oldOurs) {
					Files.deleteIfExists(p);
				}
			}
		}

		// Our mod ships inside the launcher jar.
		progress.update("Устанавливаю dayener dls…", 0.97);

		try (InputStream in = GameInstaller.class.getResourceAsStream("/mod/" + MOD_FILE)) {
			if (in == null) {
				throw new IOException("В лаунчере нет файла мода — пересобери лаунчер (build.ps1)");
			}

			Files.copy(in, mods.resolve(MOD_FILE), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Downloads the newest Fabric build of {@code mod} for {@value #MC}; returns its file name (lower case). */
	private static String installModrinth(Path mods, ModrinthMod mod) throws IOException {
		String query = "loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
				+ "&game_versions=" + URLEncoder.encode("[\"" + MC + "\"]", StandardCharsets.UTF_8);
		List<Object> versions = Json.arr(Json.parse(Http.getString("https://api.modrinth.com/v2/project/" + mod.id() + "/version?" + query)));

		if (versions.isEmpty()) {
			throw new IOException(mod.name() + " для " + MC + " не найден на Modrinth");
		}

		// Prefer a release build; otherwise the newest of any kind.
		Map<String, Object> chosen = Json.obj(versions.get(0));

		for (Object v : versions) {
			if ("release".equals(Json.obj(v).get("version_type"))) {
				chosen = Json.obj(v);
				break;
			}
		}

		Map<String, Object> file = null;

		for (Object f : Json.arr(chosen.get("files"))) {
			if (file == null || Boolean.TRUE.equals(Json.obj(f).get("primary"))) {
				file = Json.obj(f);
			}
		}

		String fileName = Json.str(file.get("filename"));
		Http.download(Json.str(file.get("url")), mods.resolve(fileName), Json.str(Json.obj(file.get("hashes")).get("sha1")));
		return fileName.toLowerCase(Locale.ROOT);
	}

	// ---- helpers ------------------------------------------------------------------------------------

	private record Job(String url, Path path, String sha1) {
	}

	/** Downloads in parallel (16 at once) and reports progress between {@code from} and {@code to}. */
	private static void runJobs(List<Job> jobs, Progress progress, String label, double from, double to) throws IOException {
		if (jobs.isEmpty()) {
			return;
		}

		ExecutorService pool = Executors.newFixedThreadPool(16);
		AtomicInteger done = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();

		for (Job job : jobs) {
			futures.add(pool.submit(() -> {
				Http.download(job.url(), job.path(), job.sha1());
				int n = done.incrementAndGet();

				if (n % 25 == 0 || n == jobs.size()) {
					progress.update(label + ": " + n + " / " + jobs.size(), from + (to - from) * n / jobs.size());
				}

				return null;
			}));
		}

		try {
			for (Future<?> f : futures) {
				f.get();
			}
		} catch (Exception e) {
			pool.shutdownNow();
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			throw cause instanceof IOException io ? io : new IOException(cause.getMessage(), cause);
		}

		pool.shutdown();
	}

	/** group:artifact:version[:classifier] → group/path/artifact/version/artifact-version[-classifier].jar */
	static String mavenPath(String name) {
		String[] p = name.split(":");
		String file = p[1] + "-" + p[2] + (p.length > 3 ? "-" + p[3] : "") + ".jar";
		return p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + file;
	}

	/** Duplicate key: group:artifact[:classifier], without the version. */
	private static String libKey(String name) {
		String[] p = name.split(":");
		return p[0] + ":" + p[1] + (p.length > 3 ? ":" + p[3] : "");
	}

	private static void unpackNatives(Path jar, Path dir) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
			ZipEntry entry;

			while ((entry = zip.getNextEntry()) != null) {
				String n = entry.getName();

				if (entry.isDirectory() || n.startsWith("META-INF") || n.contains("..")) {
					continue;
				}

				Path out = dir.resolve(Path.of(n).getFileName().toString());

				if (!Files.exists(out)) {
					Files.copy(zip, out);
				}
			}
		}
	}

	/** Mojang library / argument rules (only the OS part; feature-gated entries are skipped by the caller). */
	static final class Rules {
		private Rules() {
		}

		static boolean allowed(List<Object> rules) {
			if (rules.isEmpty()) {
				return true;
			}

			boolean allow = false;

			for (Object o : rules) {
				Map<String, Object> rule = Json.obj(o);

				if (rule.containsKey("features")) {
					return false; // demo mode, custom resolution, quick play… — never used
				}

				Map<String, Object> os = Json.obj(rule.get("os"));
				boolean matches = os.isEmpty() || matchesOs(os);

				if (matches) {
					allow = "allow".equals(rule.get("action"));
				}
			}

			return allow;
		}

		private static boolean matchesOs(Map<String, Object> os) {
			String name = Json.str(os.get("name"));
			String arch = Json.str(os.get("arch"));
			String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
			String current = osName.contains("win") ? "windows" : osName.contains("mac") ? "osx" : "linux";

			if (name != null && !name.equals(current)) {
				return false;
			}

			return arch == null || (arch.equals("x86") == !System.getProperty("os.arch").contains("64"));
		}
	}
}
