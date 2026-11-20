package dls.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON reader / writer (no libraries needed). Objects become {@code Map<String, Object>}, arrays
 * {@code List<Object>}, numbers {@code Double}, plus String, Boolean and null.
 */
public final class Json {
	private final String s;
	private int i;

	private Json(String s) {
		this.s = s;
	}

	public static Object parse(String text) {
		Json json = new Json(text);
		json.ws();
		Object value = json.value();
		json.ws();
		return value;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> obj(Object o) {
		return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
	}

	@SuppressWarnings("unchecked")
	public static List<Object> arr(Object o) {
		return o instanceof List<?> l ? (List<Object>) l : List.of();
	}

	public static String str(Object o) {
		return o == null ? null : o.toString();
	}

	public static long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0L;
	}

	private Object value() {
		char c = this.s.charAt(this.i);

		return switch (c) {
			case '{' -> object();
			case '[' -> array();
			case '"' -> string();
			case 't' -> word("true", Boolean.TRUE);
			case 'f' -> word("false", Boolean.FALSE);
			case 'n' -> word("null", null);
			default -> number();
		};
	}

	private Map<String, Object> object() {
		Map<String, Object> map = new LinkedHashMap<>();
		this.i++;
		ws();

		if (this.s.charAt(this.i) == '}') {
			this.i++;
			return map;
		}

		while (true) {
			ws();
			String key = string();
			ws();
			expect(':');
			ws();
			map.put(key, value());
			ws();

			if (this.s.charAt(this.i) == ',') {
				this.i++;
				continue;
			}

			expect('}');
			return map;
		}
	}

	private List<Object> array() {
		List<Object> list = new ArrayList<>();
		this.i++;
		ws();

		if (this.s.charAt(this.i) == ']') {
			this.i++;
			return list;
		}

		while (true) {
			ws();
			list.add(value());
			ws();

			if (this.s.charAt(this.i) == ',') {
				this.i++;
				continue;
			}

			expect(']');
			return list;
		}
	}

	private String string() {
		expect('"');
		StringBuilder out = new StringBuilder();

		while (true) {
			char c = this.s.charAt(this.i++);

			if (c == '"') {
				return out.toString();
			}

			if (c != '\\') {
				out.append(c);
				continue;
			}

			char e = this.s.charAt(this.i++);

			switch (e) {
				case 'n' -> out.append('\n');
				case 't' -> out.append('\t');
				case 'r' -> out.append('\r');
				case 'b' -> out.append('\b');
				case 'f' -> out.append('\f');
				case 'u' -> {
					out.append((char) Integer.parseInt(this.s.substring(this.i, this.i + 4), 16));
					this.i += 4;
				}
				default -> out.append(e);
			}
		}
	}

	private Object number() {
		int start = this.i;

		while (this.i < this.s.length() && "+-0123456789.eE".indexOf(this.s.charAt(this.i)) >= 0) {
			this.i++;
		}

		return Double.parseDouble(this.s.substring(start, this.i));
	}

	private Object word(String word, Object value) {
		if (!this.s.startsWith(word, this.i)) {
			throw new IllegalArgumentException("JSON: bad token at " + this.i);
		}

		this.i += word.length();
		return value;
	}

	private void expect(char c) {
		if (this.s.charAt(this.i) != c) {
			throw new IllegalArgumentException("JSON: expected '" + c + "' at " + this.i);
		}

		this.i++;
	}

	private void ws() {
		while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
			this.i++;
		}
	}

	// ---- writing -------------------------------------------------------------------------------------

	public static String write(Object value) {
		StringBuilder out = new StringBuilder();
		write(out, value, 0);
		return out.toString();
	}

	private static void write(StringBuilder out, Object v, int indent) {
		if (v == null) {
			out.append("null");
		} else if (v instanceof String str) {
			out.append('"');

			for (char c : str.toCharArray()) {
				switch (c) {
					case '"' -> out.append("\\\"");
					case '\\' -> out.append("\\\\");
					case '\n' -> out.append("\\n");
					case '\r' -> out.append("\\r");
					case '\t' -> out.append("\\t");
					default -> {
						if (c < 0x20) {
							out.append(String.format("\\u%04x", (int) c));
						} else {
							out.append(c);
						}
					}
				}
			}

			out.append('"');
		} else if (v instanceof Number n) {
			double d = n.doubleValue();
			out.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d));
		} else if (v instanceof Boolean b) {
			out.append(b);
		} else if (v instanceof Map<?, ?> map) {
			out.append("{\n");
			int n = 0;

			for (Map.Entry<?, ?> e : map.entrySet()) {
				out.append("  ".repeat(indent + 1));
				write(out, String.valueOf(e.getKey()), 0);
				out.append(": ");
				write(out, e.getValue(), indent + 1);
				out.append(++n < map.size() ? ",\n" : "\n");
			}

			out.append("  ".repeat(indent)).append('}');
		} else if (v instanceof List<?> list) {
			out.append('[');

			for (int k = 0; k < list.size(); k++) {
				write(out, list.get(k), indent + 1);

				if (k < list.size() - 1) {
					out.append(", ");
				}
			}

			out.append(']');
		} else {
			write(out, v.toString(), indent);
		}
	}
}
