package dls.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Downloads with SHA-1 check, retries and a temp file (never leaves half-written files behind).
 */
public final class Http {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	private static final String AGENT = "dayener-dls-launcher/1.0";

	private Http() {
	}

	public static String getString(String url) throws IOException {
		for (int attempt = 1; ; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", AGENT)
						.timeout(Duration.ofSeconds(30)).build();
				HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				if (response.statusCode() / 100 != 2) {
					throw new IOException("HTTP " + response.statusCode() + " " + url);
				}

				return response.body();
			} catch (IOException | InterruptedException e) {
				if (attempt >= 3) {
					throw e instanceof IOException io ? io : new IOException(e);
				}
			}
		}
	}

	/** Downloads {@code url} to {@code target} unless it already exists with the right SHA-1. */
	public static void download(String url, Path target, String sha1) throws IOException {
		if (Files.exists(target) && (sha1 == null || sha1.equalsIgnoreCase(sha1(target)))) {
			return;
		}

		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName() + ".part");

		for (int attempt = 1; ; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", AGENT)
						.timeout(Duration.ofMinutes(5)).build();
				HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

				if (response.statusCode() / 100 != 2) {
					response.body().close();
					throw new IOException("HTTP " + response.statusCode() + " " + url);
				}

				try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
					in.transferTo(out);
				}

				if (sha1 != null && !sha1.equalsIgnoreCase(sha1(tmp))) {
					throw new IOException("Файл повреждён при скачивании: " + target.getFileName());
				}

				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException | InterruptedException e) {
				Files.deleteIfExists(tmp);

				if (attempt >= 3) {
					throw e instanceof IOException io ? io : new IOException(e);
				}
			}
		}
	}

	public static String sha1(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] buf = new byte[65536];
			int n;

			while ((n = in.read(buf)) > 0) {
				md.update(buf, 0, n);
			}

			return HexFormat.of().formatHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}
}
