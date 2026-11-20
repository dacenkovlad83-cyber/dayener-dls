package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Translucent ClickGUI with fully rounded elements and small animations.
 *
 * <ul>
 *   <li>Right click on a category opens its modules in the middle.</li>
 *   <li>Left click on a module toggles it; right click slides its settings open (hidden until then).</li>
 *   <li>Every module can be bound to a key in its settings.</li>
 *   <li>Search in the top right corner finds modules in every category; just start typing.</li>
 * </ul>
 */
public final class TriggerBotScreen extends Screen {
	private static final int DESIGN_W = 499;
	private static final int DESIGN_H = 282;
	private static final float MAX_SCALE = 0.85f;

	private static final int SIDEBAR_W = 134;
	private static final int PAD = 16;
	private static final int CONTENT_X = SIDEBAR_W + PAD;
	private static final int CONTENT_W = DESIGN_W - CONTENT_X - PAD;
	private static final int CONTENT_TOP = 36;
	private static final int CONTENT_BOTTOM = 252;
	private static final int HINT_Y = 262;

	private static final int WINDOW_RADIUS = 24;
	private static final int SIDEBAR_RADIUS = 18;

	private static final int CAT_X = 14;
	private static final int CAT_Y = 54;
	private static final int CAT_W = SIDEBAR_W - 22;
	private static final int CAT_H = 18;
	private static final int CAT_STEP = 22;

	private static final int SEARCH_W = 120;
	private static final int SEARCH_H = 16;
	private static final int SEARCH_X = DESIGN_W - PAD - SEARCH_W;
	private static final int SEARCH_Y = 10;

	private static final int MODULE_H = 18;
	private static final int MODULE_GAP = 4;
	private static final int SETTING_PAD = 4;
	private static final int SETTING_INDENT = 8;
	private static final int SWITCH_W = 24;
	private static final int SWITCH_H = 12;

	private static final long OPEN_ANIM_NS = 220_000_000L;

	private static final int COL_BACKDROP = 0x30000000;
	private static final int COL_WINDOW = 0xD00A0D09;
	private static final int COL_SIDEBAR = 0xB8070907;
	private static final int COL_ROW = 0xA0151A12;
	private static final int COL_PANEL = 0x700C100A;
	private static final int COL_TEXT = 0xFFE9EDE4;
	private static final int COL_TEXT_DIM = 0xFF93A08F;
	private static final int COL_TEXT_DARK = 0xFF0A0D09;
	private static final int COL_TRACK = 0xB3242E1E;
	private static final int COL_TRACK_HOVER = 0xE02E3B26;
	private static final int COL_OFF = 0xE05B6557;
	private static final int COL_ERROR = 0xFFE0605A;

	private static final int KIND_CATEGORY = 1;
	private static final int KIND_SEARCH = 2;
	private static final int KIND_MODULE = 3;
	private static final int KIND_SETTING = 4;

	// ------------------------------------------------------------------------------------------------
	// Model
	// ------------------------------------------------------------------------------------------------

	private enum Category {
		COMBAT("Combat"),
		PLAYER("Player"),
		VISUALS("Visuals"),
		MOVEMENT("Movement"),
		CONFIGS("Configs"),
		ALTS("Alt Manager"),
		INTERFACE("Interface");

		final String name;

		Category(String name) {
			this.name = name;
		}
	}

	private abstract static class Setting {
		final String name;

		Setting(String name) {
			this.name = name;
		}

		int height() {
			return 13;
		}
	}

	private static final class Toggle extends Setting {
		final BooleanSupplier get;
		final Consumer<Boolean> set;

		Toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
			super(name);
			this.get = get;
			this.set = set;
		}
	}

	private static final class Slider extends Setting {
		final double min;
		final double max;
		final double step;
		final DoubleSupplier get;
		final DoubleConsumer set;
		final DoubleFunction<String> format;
		boolean hueTrack;

		Slider(String name, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format) {
			super(name);
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.format = format;
		}

		@Override
		int height() {
			return 19;
		}

		double progress() {
			return (this.get.getAsDouble() - this.min) / (this.max - this.min);
		}

		void setProgress(double progress) {
			double raw = this.min + Math.clamp(progress, 0.0, 1.0) * (this.max - this.min);
			this.set.accept(Math.round(raw / this.step) * this.step);
		}
	}

	private static final class Mode extends Setting {
		final Supplier<String> value;
		final Runnable next;
		final Runnable previous;

		Mode(String name, Supplier<String> value, Runnable next, Runnable previous) {
			super(name);
			this.value = value;
			this.next = next;
			this.previous = previous;
		}
	}

	/** Bind stored in a vanilla key mapping (TriggerBot, menu key) — also visible in Controls. */
	private static final class Bind extends Setting {
		final Supplier<KeyMapping> key;

		Bind(String name, Supplier<KeyMapping> key) {
			super(name);
			this.key = key;
		}
	}

	/** A key stored as a string in the config ("" / null = none). */
	private static class KeySetting extends Setting {
		final Supplier<String> get;
		final Consumer<String> set;
		/** Optional item icon shown before the label (Helper). */
		net.minecraft.world.item.Item icon;
		private net.minecraft.world.item.ItemStack iconStack;

		KeySetting(String name, Supplier<String> get, Consumer<String> set) {
			super(name);
			this.get = get;
			this.set = set;
		}

		KeySetting icon(net.minecraft.world.item.Item item) {
			this.icon = item;
			return this;
		}

		net.minecraft.world.item.ItemStack iconStack() {
			if (this.iconStack == null && this.icon != null) {
				this.iconStack = new net.minecraft.world.item.ItemStack(this.icon);
			}

			return this.iconStack;
		}
	}

	/** Bind for any other module, stored in the config. */
	private final class ModuleBind extends KeySetting {
		ModuleBind(String module) {
			super("Бинд", () -> TriggerBotScreen.this.config.moduleBinds.get(module), v -> {
				if (v == null) {
					TriggerBotScreen.this.config.moduleBinds.remove(module);
				} else {
					TriggerBotScreen.this.config.moduleBinds.put(module, v);
				}
			});
		}
	}

	private static final class Action extends Setting {
		final Supplier<String> label;
		final String button;
		final Runnable run;

		Action(Supplier<String> label, String button, Runnable run) {
			super("");
			this.label = label;
			this.button = button;
			this.run = run;
		}
	}

	/** Single-line text input (e.g. the watermark nick). */
	private static final class TextField extends Setting {
		final Supplier<String> get;
		final Consumer<String> set;
		final String placeholder;
		final int maxLength;

		TextField(String name, Supplier<String> get, Consumer<String> set, String placeholder, int maxLength) {
			super(name);
			this.get = get;
			this.set = set;
			this.placeholder = placeholder;
			this.maxLength = maxLength;
		}
	}

	private static final class Note extends Setting {
		Note(String text) {
			super(text);
		}

		@Override
		int height() {
			return 11;
		}
	}

	/** Section title inside a module (used by the HUD hub). */
	private static final class Header extends Setting {
		Header(String text) {
			super(text);
		}

		@Override
		int height() {
			return 16;
		}
	}

	/** Saturation / brightness square + hue bar (Interface → ClickGUI → «Кастомный цвет»). */
	private static final class ColorPicker extends Setting {
		static final int TITLE = 13;
		static final int SV_H = 54;
		static final int GAP = 6;
		static final int HUE_H = 6;

		ColorPicker(String name) {
			super(name);
		}

		@Override
		int height() {
			return TITLE + SV_H + GAP + HUE_H + 6;
		}
	}

	private static final class Module {
		final String name;
		final String description;
		final Category category;
		final boolean hasSwitch;
		final List<Setting> settings = new ArrayList<>();
		boolean alwaysOpen;

		Module(String name, String description, Category category, boolean hasSwitch) {
			this.name = name;
			this.description = description;
			this.category = category;
			this.hasSwitch = hasSwitch;
		}

		Module add(Setting setting) {
			this.settings.add(setting);
			return this;
		}

		boolean isOn() {
			return this.hasSwitch && Modules.isOn(this.name);
		}

		boolean matches(String query) {
			if (this.name.toLowerCase(Locale.ROOT).contains(query)
					|| this.description.toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}

			for (Setting setting : this.settings) {
				if (setting.name.toLowerCase(Locale.ROOT).contains(query)) {
					return true;
				}
			}

			return false;
		}
	}

	private record Hotspot(int kind, int x, int y, int w, int h, Object ref) {
		boolean contains(double px, double py) {
			return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
		}
	}

	/** Remembered while the game is running. */
	private static Category lastCategory;
	private static final Set<String> EXPANDED = new HashSet<>();

	private final TriggerConfig config = TriggerBotClient.config();
	private final List<Module> modules = new ArrayList<>();
	private final List<Hotspot> hotspots = new ArrayList<>();

	/** 0 → 1 per module: how far its settings are slid open. */
	private final Map<String, Float> expandAnim = new HashMap<>();
	/** 0 → 1 per module: switch knob position. */
	private final Map<String, Float> knobAnim = new HashMap<>();

	private float ox;
	private float oy;
	/** 0.85 normally; smaller on tiny windows so the menu always fits. */
	private float scale = MAX_SCALE;

	private final long openedAt = System.nanoTime();
	private long lastFrame = System.nanoTime();
	private float openProgress;

	private Category category = lastCategory;
	private String search = "";
	private boolean searchFocused;
	private int scroll;
	private int maxScroll;

	private TextField textFocus;
	private KeyMapping bindTarget;
	private KeySetting bindKey;
	/** Alt Manager input. */
	private String altInput = "";
	private String altMessage = "";
	private final TextField altField = new TextField("", () -> this.altInput, v -> this.altInput = v, "Ник (3–16, латиница, цифры, _)", 200);
	/** Configs tab input. */
	private String cfgInput = "";
	private String cfgMessage = "";
	private final TextField cfgField = new TextField("", () -> this.cfgInput, v -> this.cfgInput = v, "Название конфига", 32);
	private boolean bindError;
	private Slider dragging;
	/** Colour picker drag: 0 = none, 1 = saturation/brightness square, 2 = hue bar. */
	private int colorDrag;
	private int colorX;
	private int colorY;
	private int colorW;
	private int dragX;
	private int dragW;
	private double designMouseX;
	private double designMouseY;

	public TriggerBotScreen() {
		super(Component.literal("dayener dls"));
		buildModules();

		// Already open modules start fully open, without replaying the animation.
		for (Module module : this.modules) {
			this.expandAnim.put(module.name, isOpen(module) ? 1.0f : 0.0f);
			this.knobAnim.put(module.name, module.isOn() ? 1.0f : 0.0f);
		}
	}

	/** True while the user types into the search field or presses a key for a bind (InvMove pauses). */
	public boolean isTyping() {
		return this.searchFocused || this.textFocus != null || this.bindTarget != null || this.bindKey != null;
	}

	private void buildModules() {
		TriggerConfig c = this.config;

		// Combat
		this.modules.add(new Module("TriggerBot", "Бьёт цель под прицелом", Category.COMBAT, true)
				.add(new Bind("Бинд", TriggerBotClient::toggleKey))
				.add(new Mode("Режим атаки", () -> c.critMode.label(),
						() -> c.critMode = c.critMode.next(), () -> c.critMode = c.critMode.previous()))
				.add(new Toggle("Игроки", () -> c.attackPlayers, v -> c.attackPlayers = v))
				.add(new Toggle("Враждебные мобы", () -> c.attackHostile, v -> c.attackHostile = v))
				.add(new Toggle("Мирные мобы", () -> c.attackPassive, v -> c.attackPassive = v))
				.add(new Slider("Дистанция", 1, 6, 0.1, () -> c.range, v -> c.range = (float) v, v -> String.format("%.1f", v)))
				.add(new Slider("Разброс тайминга", 0, 100, 5, () -> c.fastRandomSpread, v -> c.fastRandomSpread = (int) v,
						v -> (int) v == 0 ? "нет" : "до " + (int) v + " мс"))
				.add(new Note("Бьёт сразу как оружие заряжено, каждый раз в разный момент"))
				.add(new Toggle("Бить через стены", () -> c.throughWalls, v -> c.throughWalls = v))
				.add(new Toggle("Булава: бить в падении без зарядки", () -> c.maceNoCharge, v -> c.maceNoCharge = v))
				.add(new Toggle("Подсветка цели", () -> c.showHighlight, v -> c.showHighlight = v))
				.add(new Action(() -> "Вайтлист: " + c.friends.size(), "Очистить", c.friends::clear)));

		this.modules.add(new Module("AutoTotem", "Тотем во второй руке при опасности", Category.COMBAT, true)
				.add(new ModuleBind("AutoTotem"))
				.add(new Slider("Порог HP", 1, 20, 0.5, () -> c.autoTotemHealth, v -> c.autoTotemHealth = (float) v,
						v -> String.format("%.1f (%.1f серд.)", v, v / 2.0)))
				.add(new Toggle("Учитывать золотые сердца", () -> c.autoTotemAbsorption, v -> c.autoTotemAbsorption = v))
				.add(new Toggle("Сначала обычные тотемы", () -> c.autoTotemPlainFirst, v -> c.autoTotemPlainFirst = v))
				.add(new Toggle("Враг с булавой рядом", () -> c.autoTotemMace, v -> c.autoTotemMace = v))
				.add(new Toggle("Кристаллы рядом", () -> c.autoTotemCrystals, v -> c.autoTotemCrystals = v))
				.add(new Slider("Дальность", 1, 12, 1, () -> c.autoTotemRange, v -> c.autoTotemRange = (int) v,
						v -> (int) v + " бл."))
				.add(new Note("Тотемы в КД не берёт · на бегу — стоп 1 тик и свап")));

		DoubleFunction<String> oneDec = v -> String.format("%.1f", v);
		this.modules.add(new Module("AimAssist", "Плавно наводит прицел на цель", Category.COMBAT, true)
				.add(new ModuleBind("AimAssist"))
				.add(new Toggle("Silent (камера не двигается)", () -> c.aimSilent, v -> c.aimSilent = v))
				.add(new Header("Ротация"))
				.add(new Mode("Режим ротации", () -> AimAssist.MODES[c.aimMode],
						() -> c.aimMode = (c.aimMode + 1) % 3, () -> c.aimMode = (c.aimMode + 2) % 3))
				.add(new Slider("Скорость", 1, 30, 0.5, () -> c.aimSpeed, v -> c.aimSpeed = (float) v, oneDec))
				.add(new Slider("Сила коррекции", 0.05, 1, 0.05, () -> c.aimCorrection, v -> c.aimCorrection = (float) v,
						v -> String.format("%.2f", v)))
				.add(new Slider("Свобода вниз", -1, 1, 0.05, () -> c.aimFreeDown, v -> c.aimFreeDown = (float) v,
						v -> String.format("%.2f", v)))
				.add(new Header("Поведение"))
				.add(new Toggle("Плавный вход", () -> c.aimSmoothEntry, v -> c.aimSmoothEntry = v))
				.add(new Toggle("Учитывать сенсу", () -> c.aimUseSens, v -> c.aimUseSens = v))
				.add(new Toggle("Замедление у цели", () -> c.aimSlowdown, v -> c.aimSlowdown = v))
				.add(new Toggle("Только при движении мыши", () -> c.aimOnlyMouse, v -> c.aimOnlyMouse = v))
				.add(new Slider("Порог замедл.", 0.5, 30, 0.5, () -> c.aimSlowAngle, v -> c.aimSlowAngle = (float) v, oneDec))
				.add(new Header("Цель"))
				.add(new Mode("Приоритет", () -> AimAssist.PRIORITIES[c.aimPriority],
						() -> c.aimPriority = (c.aimPriority + 1) % 3, () -> c.aimPriority = (c.aimPriority + 2) % 3))
				.add(new Slider("Дистанция", 1, 8, 0.1, () -> c.aimRange, v -> c.aimRange = (float) v, oneDec))
				.add(new Slider("Фов", 10, 360, 5, () -> c.aimFov, v -> c.aimFov = (float) v, v -> String.valueOf((int) v)))
				.add(new Slider("Удержание после удара", 0, 15, 0.5, () -> c.aimHold, v -> c.aimHold = (float) v,
						v -> v <= 0 ? "выкл" : String.format("%.1fс", v)))
				.add(new Toggle("Только ластхит", () -> c.aimLastHitOnly, v -> c.aimLastHitOnly = v))
				.add(new Toggle("Игроки", () -> c.aimPlayers, v -> c.aimPlayers = v))
				.add(new Toggle("Враждебные мобы", () -> c.aimHostile, v -> c.aimHostile = v))
				.add(new Toggle("Мирные мобы", () -> c.aimPassive, v -> c.aimPassive = v))
				.add(new Header("Условия"))
				.add(new Toggle("Только с оружием", () -> c.aimOnlyWeapon, v -> c.aimOnlyWeapon = v))
				.add(new Toggle("Только с зажатой ЛКМ", () -> c.aimOnlyLmb, v -> c.aimOnlyLmb = v))
				.add(new Toggle("Только видимые (не сквозь стены)", () -> c.aimVisible, v -> c.aimVisible = v))
				.add(new Note("В инвентаре и меню не наводит · друзей не трогает")));

		this.modules.add(new Module("SpearSwap", "Удар, сразу копьё, затем слот обратно", Category.COMBAT, true)
				.add(new ModuleBind("SpearSwap"))
				.add(new KeySetting("Клавиша", () -> c.spearSwapKey, v -> c.spearSwapKey = v == null ? "" : v))

				.add(new Slider("Слот обратно через", 1, 20, 1, () -> c.spearSwapBack, v -> c.spearSwapBack = (int) v,
						v -> (int) v + " тик."))
				.add(new Note("Копьё должно лежать в хотбаре")));

		int items = AutoSwap.NAMES.length;
		this.modules.add(new Module("AutoSwap", "Свап второй руки между двумя предметами", Category.COMBAT, true)
				.add(new ModuleBind("AutoSwap"))
				.add(new KeySetting("Клавиша свапа", () -> c.autoSwapKey, v -> c.autoSwapKey = v == null ? "" : v))
				.add(new Mode("Предмет 1", () -> AutoSwap.NAMES[c.autoSwapFirst],
						() -> c.autoSwapFirst = (c.autoSwapFirst + 1) % items,
						() -> c.autoSwapFirst = (c.autoSwapFirst + items - 1) % items))
				.add(new Mode("Предмет 2", () -> AutoSwap.NAMES[c.autoSwapSecond],
						() -> c.autoSwapSecond = (c.autoSwapSecond + 1) % items,
						() -> c.autoSwapSecond = (c.autoSwapSecond + items - 1) % items))
				.add(new Note("Во 2 руке предмет 1 — ставит предмет 2, иначе 1")));

		// Utility
		this.modules.add(new Module("FastExp", "Бутылочки опыта без задержки", Category.PLAYER, true)
				.add(new ModuleBind("FastExp"))
				.add(new Slider("Задержка", 0, 3, 1, () -> c.fastExpDelay, v -> c.fastExpDelay = (int) v,
						v -> (int) v == 0 ? "нет" : (int) v + " тик.")));

		Module helper = new Module("Helper", "Серверные предметы по клавише", Category.PLAYER, true)
				.add(new ModuleBind("Helper"));

		for (Helper.Entry entry : Helper.allEntries()) {
			helper.add(new KeySetting(entry.label(), () -> c.helperKeys.get(entry.id()), v -> {
				if (v == null) {
					c.helperKeys.remove(entry.id());
				} else {
					c.helperKeys.put(entry.id(), v);
				}
			}).icon(entry.item()));
		}

		helper.add(new Header("HUD с биндами"))
				.add(new Toggle("Показывать", () -> c.helperHud, v -> c.helperHud = v))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.hhScale, v -> c.hhScale = (float) v,
						v -> Math.round(v * 100) + "%"))
				.add(new Action(() -> "Позиция", "Сбросить", HelperHud::resetPosition));

		this.modules.add(helper
				.add(new Note("Сам найдёт предмет, остановит, применит и вернёт слот"))
				.add(new Note("Хорус: держишь — ест, отпустил — слот обратно"))
				.add(new Note("Заряд ветра под ноги + прыжок, затем булава в руку")));

		this.modules.add(new Module("ClickPearl", "Эндер-жемчуг по клавише", Category.PLAYER, true)
				.add(new ModuleBind("ClickPearl"))
				.add(new KeySetting("Клавиша броска", () -> c.clickPearlKey, v -> c.clickPearlKey = v == null ? "" : v))
				.add(new Note("Перка из любого слота, слот возвращается")));

		this.modules.add(new Module("AutoTool", "Лучший инструмент при копании", Category.PLAYER, true)
				.add(new ModuleBind("AutoTool"))
				.add(new Toggle("Останавливаться при свапе", () -> c.autoToolStop, v -> c.autoToolStop = v))
				.add(new Toggle("Вернуть слот после", () -> c.autoToolBack, v -> c.autoToolBack = v)));

		Module lock = new Module("LockSlot", "Блокировка слотов хотбара", Category.PLAYER, true)
				.add(new ModuleBind("LockSlot"))
				.add(new KeySetting("Блок. текущий слот", () -> c.lockSlotKey, v -> c.lockSlotKey = v == null ? "" : v))
				.add(new Toggle("Весь хотбар", () -> c.lockHotbar, v -> c.lockHotbar = v));

		for (int i = 0; i < 9; i++) {
			int bit = 1 << i;
			lock.add(new Toggle("Слот " + (i + 1), () -> (c.lockedSlots & bit) != 0, v -> c.lockedSlots = v ? c.lockedSlots | bit : c.lockedSlots & ~bit));
		}

		this.modules.add(lock.add(new Note("Нельзя выкинуть (Q), кликнуть или свапнуть")));

		this.modules.add(new Module("AutoEat", "Сам ест, когда хочется есть", Category.PLAYER, true)
				.add(new ModuleBind("AutoEat"))
				.add(new Slider("Есть, когда голод до", 1, 19, 1, () -> c.autoEatHunger, v -> c.autoEatHunger = (int) v,
						v -> (int) v + " / 20"))
				.add(new Toggle("Перестать копать, чтобы поесть", () -> c.autoEatStopMining, v -> c.autoEatStopMining = v)));

		this.modules.add(new Module("AutoLeave", "Выходит с сервера при опасности", Category.PLAYER, true)
				.add(new ModuleBind("AutoLeave"))
				.add(new Slider("Мин. HP для выхода", 1, 19, 1, () -> c.autoLeaveHealth, v -> c.autoLeaveHealth = (float) v,
						v -> String.format("%.0f (%.1f серд.)", v, v / 2.0)))
				.add(new Slider("Дистанция до игрока", 0, 64, 1, () -> c.autoLeaveDistance, v -> c.autoLeaveDistance = (int) v,
						v -> v <= 0 ? "выкл" : (int) v + " бл.")));

		this.modules.add(new Module("ItemScroller", "Быстро перекладывает предметы", Category.PLAYER, true)
				.add(new ModuleBind("ItemScroller"))
				.add(new Slider("Задержка", 1, 50, 1, () -> c.itemScrollerDelay, v -> c.itemScrollerDelay = (int) v,
						v -> (int) v + " мс"))
				.add(new Note("Shift + ЛКМ по слотам или колесо над слотом")));

		this.modules.add(new Module("NoJumpDelay", "Без задержки между прыжками", Category.PLAYER, true)
				.add(new ModuleBind("NoJumpDelay")));

		// Visuals
		this.modules.add(new Module("Fullbright", "Максимальная яркость везде", Category.VISUALS, true)
				.add(new ModuleBind("Fullbright")));

		this.modules.add(new Module("NameTags", "Таблички над игроками с донатом", Category.VISUALS, true)
				.add(new ModuleBind("NameTags"))
				.add(new Toggle("Броня", () -> c.nameTagsArmor, v -> c.nameTagsArmor = v))
				.add(new Toggle("Предметы в руках", () -> c.nameTagsItems, v -> c.nameTagsItems = v))
				.add(new Toggle("Здоровье", () -> c.nameTagsHealth, v -> c.nameTagsHealth = v))
				.add(new Toggle("Донат и префиксы с сервера", () -> c.nameTagsPrefixes, v -> c.nameTagsPrefixes = v))
				.add(new Slider("Дистанция", 8, 256, 8, () -> c.nameTagsRange, v -> c.nameTagsRange = (int) v,
						v -> (int) v + " бл."))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.nameTagsScale, v -> c.nameTagsScale = (float) v,
						v -> Math.round(v * 100) + "%")));

		this.modules.add(new Module("NoRender", "Убирает мешающие эффекты", Category.VISUALS, true)
				.add(new ModuleBind("NoRender"))
				.add(new Toggle("Огонь", () -> c.noRenderFire, v -> c.noRenderFire = v))
				.add(new Toggle("Тряска экрана", () -> c.noRenderHurtShake, v -> c.noRenderHurtShake = v))
				.add(new Toggle("Тряска рук", () -> c.noRenderHandShake, v -> c.noRenderHandShake = v))
				.add(new Toggle("Плохие эффекты", () -> c.noRenderBadEffects, v -> c.noRenderBadEffects = v))
				.add(new Toggle("Рендер в блоках", () -> c.noRenderInBlock, v -> c.noRenderInBlock = v))
				.add(new Toggle("Снос тотема", () -> c.noRenderTotem, v -> c.noRenderTotem = v))
				.add(new Toggle("Эффекты фейерверка", () -> c.noRenderFireworks, v -> c.noRenderFireworks = v))
				.add(new Toggle("Эффекты крита", () -> c.noRenderCrits, v -> c.noRenderCrits = v))
				.add(new Toggle("Всплески воды", () -> c.noRenderWaterSplash, v -> c.noRenderWaterSplash = v))
				.add(new Toggle("Всплески лавы", () -> c.noRenderLavaSplash, v -> c.noRenderLavaSplash = v))
				.add(new Toggle("Дождь", () -> c.noRenderRain, v -> c.noRenderRain = v))
				.add(new Toggle("Снег", () -> c.noRenderSnow, v -> c.noRenderSnow = v))
				.add(new Toggle("Туман", () -> c.noRenderFog, v -> c.noRenderFog = v)));

		this.modules.add(new Module("TargetESP", "Маркер или призраки на цели", Category.VISUALS, true)
				.add(new ModuleBind("TargetESP"))
				.add(new Mode("Режим", () -> TargetEsp.MODES[c.targetEspMode],
						() -> c.targetEspMode = 1 - c.targetEspMode, () -> c.targetEspMode = 1 - c.targetEspMode))
				.add(new Slider("Ширина (призраки)", 0.3, 3.0, 0.05, () -> c.targetEspWidth, v -> c.targetEspWidth = (float) v,
						v -> Math.round(v * 100) + "%"))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.targetEspSize, v -> c.targetEspSize = (float) v,
						v -> Math.round(v * 100) + "%"))
				.add(new Slider("Скорость вращения", 0, 5, 0.1, () -> c.targetEspSpeed, v -> c.targetEspSpeed = (float) v,
						v -> String.format("%.1f", v)))
				.add(new Toggle("Цвет меню (иначе белый)", () -> c.targetEspAccent, v -> c.targetEspAccent = v)));

		this.modules.add(new Module("FreeLook", "Осматриваться, не поворачивая игрока", Category.VISUALS, true)
				.add(new ModuleBind("FreeLook"))
				.add(new KeySetting("Клавиша (удерживать)", () -> c.freeLookKey, v -> c.freeLookKey = v == null ? "" : v))
				.add(new Toggle("Вид от 3-го лица", () -> c.freeLookThirdPerson, v -> c.freeLookThirdPerson = v))
				.add(new Note("Без клавиши — работает, пока модуль включён")));

		this.modules.add(new Module("ShulkerPreview", "Shift над шалкером — что внутри", Category.VISUALS, true)
				.add(new ModuleBind("ShulkerPreview"))
				.add(new Note("Работает в любом инвентаре и сундуке")));

		this.modules.add(new Module("TotemCounter", "Сколько тотемов осталось", Category.VISUALS, true)
				.add(new ModuleBind("TotemCounter"))
				.add(new Mode("Позиция", () -> TotemCounter.POSITIONS[c.tcPosition],
						() -> c.tcPosition = 1 - c.tcPosition, () -> c.tcPosition = 1 - c.tcPosition))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.tcScale, v -> c.tcScale = (float) v,
						v -> Math.round(v * 100) + "%")));

		String[] handModes = {"Обе", "Правая", "Левая"};
		DoubleFunction<String> offset = v -> String.format("%+.2f", v);
		this.modules.add(new Module("CustomHands", "Какие руки видно и их положение", Category.VISUALS, true)
				.add(new ModuleBind("CustomHands"))
				.add(new Mode("Режим рук", () -> handModes[c.handsMode],
						() -> c.handsMode = (c.handsMode + 1) % 3, () -> c.handsMode = (c.handsMode + 2) % 3))
				.add(new Note("View Model — правая рука"))
				.add(new Slider("Правая X", -1, 1, 0.05, () -> c.rightHandX, v -> c.rightHandX = (float) v, offset))
				.add(new Slider("Правая Y", -1, 1, 0.05, () -> c.rightHandY, v -> c.rightHandY = (float) v, offset))
				.add(new Slider("Правая Z", -1, 1, 0.05, () -> c.rightHandZ, v -> c.rightHandZ = (float) v, offset))
				.add(new Slider("Правая — размер", 0.2, 2.0, 0.05, () -> c.rightHandScale, v -> c.rightHandScale = (float) v,
						v -> Math.round(v * 100) + "%"))
				.add(new Note("View Model — левая рука"))
				.add(new Slider("Левая X", -1, 1, 0.05, () -> c.leftHandX, v -> c.leftHandX = (float) v, offset))
				.add(new Slider("Левая Y", -1, 1, 0.05, () -> c.leftHandY, v -> c.leftHandY = (float) v, offset))
				.add(new Slider("Левая Z", -1, 1, 0.05, () -> c.leftHandZ, v -> c.leftHandZ = (float) v, offset))
				.add(new Slider("Левая — размер", 0.2, 2.0, 0.05, () -> c.leftHandScale, v -> c.leftHandScale = (float) v,
						v -> Math.round(v * 100) + "%"))
				.add(new Action(() -> "Положение и размер рук", "Сбросить", () -> {
					c.rightHandX = c.rightHandY = c.rightHandZ = 0.0f;
					c.leftHandX = c.leftHandY = c.leftHandZ = 0.0f;
					c.rightHandScale = c.leftHandScale = 1.0f;
				})));

		// Movement
		this.modules.add(new Module("AutoSprint", "Всегда бежать при движении вперёд", Category.MOVEMENT, true)
				.add(new ModuleBind("AutoSprint")));

		this.modules.add(new Module("InvMove", "Ходить с открытым инвентарём", Category.MOVEMENT, true)
				.add(new ModuleBind("InvMove"))
				.add(new Note("Работает в инвентарях, сундуках и в этом меню")));

		this.modules.add(new Module("Freecam", "Свободная камера, игрок стоит на месте", Category.MOVEMENT, true)
				.add(new ModuleBind("Freecam"))
				.add(new Slider("Скорость X/Z", 0.1, 5.0, 0.1, () -> c.freecamSpeedXZ, v -> c.freecamSpeedXZ = (float) v,
						v -> String.format("%.1f", v)))
				.add(new Slider("Скорость Y", 0.1, 5.0, 0.1, () -> c.freecamSpeedY, v -> c.freecamSpeedY = (float) v,
						v -> String.format("%.1f", v)))
				.add(new Note("Пробел / Shift — вверх / вниз, Ctrl — быстрее")));

		this.modules.add(new Module("AirStuck", "Зависнуть в воздухе", Category.MOVEMENT, true)
				.add(new ModuleBind("AirStuck")));

		this.modules.add(new Module("NoPush", "Тебя не отталкивает", Category.MOVEMENT, true)
				.add(new ModuleBind("NoPush"))
				.add(new Toggle("Игроки и мобы", () -> c.noPushEntities, v -> c.noPushEntities = v))
				.add(new Toggle("Блоки", () -> c.noPushBlocks, v -> c.noPushBlocks = v))
				.add(new Toggle("Удочка", () -> c.noPushRod, v -> c.noPushRod = v))
				.add(new Toggle("Течение воды", () -> c.noPushWater, v -> c.noPushWater = v)));

		this.modules.add(new Module("ElytraSwap", "Свап элитр и фейерверк по кнопке", Category.MOVEMENT, true)
				.add(new ModuleBind("ElytraSwap"))
				.add(new KeySetting("Клавиша элитр", () -> c.elytraKey, v -> c.elytraKey = v == null ? "" : v))
				.add(new KeySetting("Клавиша фейерверка", () -> c.fireworkKey, v -> c.fireworkKey = v == null ? "" : v))
				.add(new Note("Задержка 75 мс, пауза 1 тик"))
				.add(new Note("Нет места — в слот справа/слева, затем во 2 руку")));

		// Interface: menu look (ПКМ — свернуть) and one "HUD" entry with every on-screen element inside.
		this.modules.add(new Module("ClickGUI", "Это меню · ПКМ — свернуть", Category.INTERFACE, false)
				.add(new Bind("Клавиша меню", TriggerBotClient::screenKey))
				.add(new ColorPicker("Кастомный цвет"))
				.add(new Action(() -> "Стандартный цвет", "Сбросить", () -> {
					c.guiHue = 94.0f;
					c.guiSat = 0.56f;
					c.guiVal = 0.90f;
				}))
				.add(new Slider("Непрозрачность", 0.3, 1.0, 0.05, () -> c.guiOpacity, v -> c.guiOpacity = (float) v,
						v -> Math.round(v * 100) + "%")));

		DoubleFunction<String> percent = v -> Math.round(v * 100) + "%";
		this.modules.add(new Module("HUD", "Всё на экране: эффекты, бинды, броня... · ПКМ — открыть", Category.INTERFACE, false)
				.add(new Slider("Непрозрачность HUD", 0.2, 1.0, 0.05, () -> c.hudOpacity, v -> c.hudOpacity = (float) v, percent))
				.add(new Slider("Размер GPS", 0.5, 1.5, 0.05, () -> c.gpsScale, v -> c.gpsScale = (float) v, percent))
				.add(new Note("Открой чат и перетаскивай плашки мышкой"))

				.add(new Header("Watermark"))
				.add(new Toggle("Включить", () -> c.watermark, v -> c.watermark = v))
				.add(new ModuleBind("Watermark"))
				.add(new Toggle("Ник", () -> c.wmNick, v -> c.wmNick = v))
				.add(new TextField("Свой ник", () -> c.wmNickText, v -> c.wmNickText = v, "как в игре", 16))
				.add(new Toggle("FPS", () -> c.wmFps, v -> c.wmFps = v))
				.add(new Toggle("Время сессии", () -> c.wmTime, v -> c.wmTime = v))
				.add(new Toggle("Координаты", () -> c.wmCoords, v -> c.wmCoords = v))
				.add(new Toggle("Пинг", () -> c.wmPing, v -> c.wmPing = v))
				.add(new Toggle("TPS", () -> c.wmTps, v -> c.wmTps = v))
				.add(new Toggle("Скорость (BPS)", () -> c.wmBps, v -> c.wmBps = v))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.wmScale, v -> c.wmScale = (float) v, percent))

				.add(new Header("ArmorHUD"))
				.add(new Toggle("Включить", () -> c.armorHud, v -> c.armorHud = v))
				.add(new ModuleBind("ArmorHUD"))
				.add(new Toggle("Вертикально", () -> c.ahVertical, v -> c.ahVertical = v))
				.add(new Toggle("В процентах", () -> c.ahPercent, v -> c.ahPercent = v))
				.add(new Toggle("Предметы в руках", () -> c.ahHands, v -> c.ahHands = v))
				.add(new Toggle("Фон", () -> c.ahBackground, v -> c.ahBackground = v))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.ahScale, v -> c.ahScale = (float) v, percent))
				.add(new Action(() -> "Позиция", "Сбросить", () -> ArmorHud.resetPosition(c)))

				.add(new Header("TargetHUD"))
				.add(new Toggle("Включить", () -> c.targetHud, v -> c.targetHud = v))
				.add(new ModuleBind("TargetHUD"))
				.add(new Toggle("Броня и предметы", () -> c.thItems, v -> c.thItems = v))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.thScale, v -> c.thScale = (float) v, percent))
				.add(new Action(() -> "Позиция", "Сбросить", TargetInfoHud::resetPosition))

				.add(new Header("Effects"))
				.add(new Toggle("Включить", () -> c.effectsHud, v -> c.effectsHud = v))
				.add(new ModuleBind("Effects"))
				.add(new Toggle("Скрытые эффекты", () -> c.efHidden, v -> c.efHidden = v))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.efScale, v -> c.efScale = (float) v, percent))
				.add(new Action(() -> "Позиция", "Сбросить", EffectsHud::resetPosition))

				.add(new Header("Cooldowns"))
				.add(new Toggle("Включить", () -> c.cooldownHud, v -> c.cooldownHud = v))
				.add(new ModuleBind("Cooldowns"))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.cdScale, v -> c.cdScale = (float) v, percent))
				.add(new Action(() -> "Позиция", "Сбросить", CooldownHud::resetPosition))

				.add(new Header("Keybinds"))
				.add(new Toggle("Включить", () -> c.keybindsHud, v -> c.keybindsHud = v))
				.add(new ModuleBind("Keybinds"))
				.add(new Slider("Размер", 0.5, 2.0, 0.05, () -> c.kbScale, v -> c.kbScale = (float) v, percent))
				.add(new Action(() -> "Позиция", "Сбросить", KeybindsHud::resetPosition))
				.add(new Note("В чате: .keybind info — список биндов"))

				.add(new Header("Плашка TriggerBot"))
				.add(new Toggle("Включить", () -> c.showHud, v -> c.showHud = v))
				.add(new ModuleBind("TriggerBotHUD")));
	}

	@Override
	protected void init() {
		this.scale = Math.min(MAX_SCALE, Math.min((this.width - 8) / (float) DESIGN_W, (this.height - 8) / (float) DESIGN_H));
		this.ox = (this.width - DESIGN_W * this.scale) / 2.0f;
		this.oy = (this.height - DESIGN_H * this.scale) / 2.0f;
	}

	// ------------------------------------------------------------------------------------------------
	// Animation helpers
	// ------------------------------------------------------------------------------------------------

	private static float easeOut(float t) {
		float inv = 1.0f - Math.clamp(t, 0.0f, 1.0f);
		return 1.0f - inv * inv * inv;
	}

	private static float approach(float value, float target, float dt, float speed) {
		float next = value + (target - value) * Math.min(1.0f, dt * speed);
		return Math.abs(target - next) < 0.003f ? target : next;
	}

	/** Applies the opening fade to any colour. */
	private int fade(int color) {
		if (this.openProgress >= 1.0f) {
			return color;
		}

		int alpha = Math.round((color >>> 24) * this.openProgress);
		return (Math.max(alpha, 0) << 24) | (color & 0x00FFFFFF);
	}

	private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
		int c = fade(color);

		if ((c >>> 24) > 8) {
			graphics.text(this.font, Component.literal(text), x, y, c);
		}
	}

	private void centered(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
		int c = fade(color);

		if ((c >>> 24) > 8) {
			graphics.centeredText(this.font, Component.literal(text), x, y, c);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - this.lastFrame) / 1_000_000_000f);
		this.lastFrame = now;
		this.openProgress = easeOut((now - this.openedAt) / (float) OPEN_ANIM_NS);

		for (Module module : this.modules) {
			this.expandAnim.merge(module.name, 0.0f, (old, ignored) -> approach(old, isOpen(module) ? 1.0f : 0.0f, dt, 14.0f));
			this.knobAnim.merge(module.name, 0.0f, (old, ignored) -> approach(old, module.isOn() ? 1.0f : 0.0f, dt, 18.0f));
		}

		this.designMouseX = (mouseX - this.ox) / this.scale;
		this.designMouseY = (mouseY - this.oy) / this.scale;
		this.hotspots.clear();

		graphics.fill(0, 0, this.width, this.height, fade(COL_BACKDROP));

		// Opening animation: grows from 92% to 100% around the window centre while fading in.
		float drawScale = this.scale * (0.92f + 0.08f * this.openProgress);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(this.width / 2.0f, this.height / 2.0f);
		pose.scale(drawScale);
		pose.translate(-DESIGN_W / 2.0f, -DESIGN_H / 2.0f);

		UiDraw.setOpacity(this.config.guiOpacity * this.openProgress);

		try {
			UiDraw.box(graphics, 0, 0, DESIGN_W, DESIGN_H, WINDOW_RADIUS, COL_WINDOW, Theme.border());
			UiDraw.fill(graphics, 6, 6, SIDEBAR_W - 6, DESIGN_H - 12, SIDEBAR_RADIUS, COL_SIDEBAR);

			drawSidebar(graphics);
			drawSearch(graphics);
			drawContent(graphics);
			drawHint(graphics);
		} finally {
			UiDraw.resetOpacity();
			pose.popMatrix();
		}
	}

	private void drawSidebar(GuiGraphicsExtractor graphics) {
		UiDraw.fill(graphics, 18, 18, 9, 9, 5, Theme.accent());
		text(graphics, "dayener dls", 31, 19, COL_TEXT);
		text(graphics, "КАТЕГОРИИ", 18, 40, COL_TEXT_DIM);

		for (Category cat : Category.values()) {
			// Alt Manager and Interface sit at the very bottom of the sidebar.
			int y = switch (cat) {
				case INTERFACE -> DESIGN_H - 30 - CAT_H;
				case ALTS -> DESIGN_H - 30 - CAT_H - CAT_STEP;
				case CONFIGS -> DESIGN_H - 30 - CAT_H - 2 * CAT_STEP;
				default -> CAT_Y + cat.ordinal() * CAT_STEP;
			};
			boolean selected = cat == this.category && this.search.isEmpty();
			Hotspot spot = addHotspot(KIND_CATEGORY, CAT_X, y, CAT_W, CAT_H, cat);
			boolean hovered = spot.contains(this.designMouseX, this.designMouseY);

			UiDraw.box(graphics, CAT_X, y, CAT_W, CAT_H, CAT_H / 2,
					selected ? Theme.rowActive() : (hovered ? Theme.rowHover() : COL_ROW),
					selected ? Theme.accent() : Theme.borderSoft());
			boolean lit = categoryActive(cat) || selected;
			icon(graphics, cat, CAT_X + 8, y + 4, fade(lit ? Theme.accent() : (hovered ? COL_TEXT : COL_TEXT_DIM)));
			text(graphics, cat.name, CAT_X + 21, y + 5, selected || hovered ? COL_TEXT : COL_TEXT_DIM);

			int count = moduleCount(cat);

			if (count > 0 && cat != Category.INTERFACE && cat != Category.ALTS && cat != Category.CONFIGS) {
				int badgeX = CAT_X + CAT_W - 20;
				UiDraw.fill(graphics, badgeX, y + 4, 14, 10, 5, selected ? Theme.accent() : Theme.accentDim());
				centered(graphics, String.valueOf(count), badgeX + 7, y + 5, selected ? COL_TEXT_DARK : COL_TEXT);
			}
		}
	}

	private static final String[] ICON_SWORD = {
			"......###", ".....####", "....####.", "#..####..", ".#####...", "..###....", ".##.#....", "##...#...", "#........"};
	private static final String[] ICON_PLAYER = {
			"...###...", "...###...", "...###...", ".........", "..#####..", ".#.###.#.", "...###...", "...#.#...", "...#.#..."};
	private static final String[] ICON_STAR = {
			"....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.", ".#.....#."};
	private static final String[] ICON_LEG = {
			"..###....", "..###....", "..###....", "..###....", "..###....", "..###....", "..######.", "..#######", "..#######"};
	private static final String[] ICON_ALTS = {
			"..###....", "..###..#.", "..###.###", ".......#.", ".#####...", "#######..", "#######..", ".........", "........."};
	private static final String[] ICON_FOLDER = {
			"#####....", "#....#...", "#########", "#.......#", "#.......#", "#.......#", "#.......#", "#########", "........."};
	private static final String[] ICON_WINDOW = {
			"#########", "#.......#", "#########", "#.......#", "#.......#", "#.......#", "#.......#", "#########", "........."};

	/** 9x9 pixel icons next to category names: sword, player, star, leg, people, window. */
	private static void icon(GuiGraphicsExtractor graphics, Category cat, int x, int y, int color) {
		String[] rows = switch (cat) {
			case COMBAT -> ICON_SWORD;
			case PLAYER -> ICON_PLAYER;
			case VISUALS -> ICON_STAR;
			case MOVEMENT -> ICON_LEG;
			case ALTS -> ICON_ALTS;
			case CONFIGS -> ICON_FOLDER;
			case INTERFACE -> ICON_WINDOW;
		};

		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];
			int start = -1;

			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == '#';

				if (on && start < 0) {
					start = col;
				} else if (!on && start >= 0) {
					graphics.fill(x + start, y + row, x + col, y + row + 1, color);
					start = -1;
				}
			}
		}
	}

	private void drawSearch(GuiGraphicsExtractor graphics) {
		if (!this.search.isEmpty()) {
			text(graphics, "Поиск", CONTENT_X, 14, Theme.accent());
		} else if (this.category != null) {
			text(graphics, this.category.name, CONTENT_X, 14, Theme.accent());
		}

		Hotspot spot = addHotspot(KIND_SEARCH, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H, null);
		boolean hovered = spot.contains(this.designMouseX, this.designMouseY);

		UiDraw.box(graphics, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H, SEARCH_H / 2,
				this.searchFocused ? Theme.rowActive() : (hovered ? Theme.rowHover() : COL_ROW),
				this.searchFocused ? Theme.accent() : Theme.borderSoft());

		// Magnifier: ring + handle.
		UiDraw.box(graphics, SEARCH_X + 7, SEARCH_Y + 4, 7, 7, 3, 0x00000000, COL_TEXT_DIM);
		UiDraw.fill(graphics, SEARCH_X + 13, SEARCH_Y + 10, 2, 2, 1, COL_TEXT_DIM);

		int textX = SEARCH_X + 19;
		int maxW = SEARCH_W - 26;

		if (this.search.isEmpty() && !this.searchFocused) {
			text(graphics, "Поиск...", textX, SEARCH_Y + 4, COL_TEXT_DIM);
		} else {
			String shown = this.search;

			while (this.font.width(shown) > maxW && !shown.isEmpty()) {
				shown = shown.substring(1);
			}

			text(graphics, shown, textX, SEARCH_Y + 4, COL_TEXT);

			if (this.searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = textX + this.font.width(shown) + 1;
				graphics.fill(cx, SEARCH_Y + 3, cx + 1, SEARCH_Y + 13, fade(Theme.accent()));
			}
		}
	}

	private List<Module> visibleModules() {
		List<Module> list = new ArrayList<>();

		if (!this.search.isEmpty()) {
			String query = this.search.toLowerCase(Locale.ROOT);

			for (Module module : this.modules) {
				if (module.matches(query)) {
					list.add(module);
				}
			}

			return list;
		}

		if (this.category != null) {
			for (Module module : this.modules) {
				if (module.category == this.category) {
					list.add(module);
				}
			}
		}

		return list;
	}

	private boolean isOpen(Module module) {
		if (module.settings.isEmpty()) {
			return false;
		}

		return module.alwaysOpen || EXPANDED.contains(module.name);
	}

	private int settingsHeight(Module module) {
		int h = SETTING_PAD * 2;

		for (Setting setting : module.settings) {
			h += setting.height();
		}

		return h;
	}

	/** Current (animated) height of the settings panel. */
	private int animatedSettingsHeight(Module module) {
		float t = this.expandAnim.getOrDefault(module.name, 0.0f);

		if (t <= 0.0f) {
			return 0;
		}

		return Math.round((settingsHeight(module) + 2) * easeOut(t));
	}

	private void drawContent(GuiGraphicsExtractor graphics) {
		if (this.category == Category.CONFIGS && this.search.isEmpty()) {
			drawConfigs(graphics);
			return;
		}

		if (this.category == Category.ALTS && this.search.isEmpty()) {
			drawAlts(graphics);
			return;
		}

		List<Module> list = visibleModules();

		if (list.isEmpty()) {
			drawEmpty(graphics);
			this.maxScroll = 0;
			return;
		}

		int total = 0;

		for (Module module : list) {
			total += MODULE_H + MODULE_GAP + animatedSettingsHeight(module);
		}

		this.maxScroll = Math.max(0, total - (CONTENT_BOTTOM - CONTENT_TOP));
		this.scroll = Math.clamp(this.scroll, 0, this.maxScroll);

		graphics.enableScissor(CONTENT_X - 2, CONTENT_TOP, CONTENT_X + CONTENT_W + 2, CONTENT_BOTTOM);

		int y = CONTENT_TOP - this.scroll;

		for (Module module : list) {
			drawModule(graphics, module, y);
			y += MODULE_H;

			int animated = animatedSettingsHeight(module);

			if (animated > 0) {
				drawSettings(graphics, module, y, animated);
				y += animated;
			}

			y += MODULE_GAP;
		}

		graphics.disableScissor();

		if (this.maxScroll > 0) {
			int trackH = CONTENT_BOTTOM - CONTENT_TOP;
			int barH = Math.max(16, trackH * trackH / (trackH + this.maxScroll));
			int barY = CONTENT_TOP + (int) ((trackH - barH) * (this.scroll / (double) this.maxScroll));
			UiDraw.fill(graphics, DESIGN_W - 8, barY, 3, barH, 1, Theme.borderSoft());
		}
	}

	// ---- Configs -------------------------------------------------------------------------------------

	private static final int KIND_CFG_SAVE = 13;
	private static final int KIND_CFG_LOAD = 14;
	private static final int KIND_CFG_DELETE = 15;
	private static final int KIND_CFG_DIR = 16;

	private void drawConfigs(GuiGraphicsExtractor graphics) {
		int x = CONTENT_X;
		int w = CONTENT_W;
		int y = CONTENT_TOP;

		// Name input + "Сохранить".
		int buttonW = 74;
		int inputW = w - buttonW - 6;
		Hotspot input = addHotspot(KIND_SETTING, x, y, inputW, 18, this.cfgField);
		boolean focused = this.textFocus == this.cfgField;
		UiDraw.box(graphics, x, y, inputW, 18, 9,
				focused ? Theme.rowActive() : (input.contains(this.designMouseX, this.designMouseY) ? Theme.rowHover() : COL_ROW),
				focused ? Theme.accent() : Theme.borderSoft());

		if (this.cfgInput.isEmpty() && !focused) {
			text(graphics, this.cfgField.placeholder, x + 10, y + 5, 0xFF6F7A6B);
		} else {
			text(graphics, this.cfgInput, x + 10, y + 5, COL_TEXT);

			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = x + 10 + this.font.width(this.cfgInput) + 1;
				graphics.fill(cx, y + 4, cx + 1, y + 14, fade(Theme.accent()));
			}
		}

		Hotspot save = addHotspot(KIND_CFG_SAVE, x + inputW + 6, y, buttonW, 18, null);
		boolean saveHover = save.contains(this.designMouseX, this.designMouseY);
		UiDraw.box(graphics, x + inputW + 6, y, buttonW, 18, 9, saveHover ? Theme.accent() : Theme.accentDim(), Theme.accent());
		centered(graphics, "Сохранить", x + inputW + 6 + buttonW / 2, y + 5, saveHover ? COL_TEXT_DARK : COL_TEXT);

		y += 22;

		// "Открыть папку" + message.
		String dirLabel = "Открыть папку";
		int dirW = this.font.width(dirLabel) + 20;
		Hotspot dir = addHotspot(KIND_CFG_DIR, x, y, dirW, 16, null);
		boolean dirHover = dir.contains(this.designMouseX, this.designMouseY);
		UiDraw.box(graphics, x, y, dirW, 16, 8, dirHover ? Theme.rowHover() : COL_ROW, dirHover ? Theme.accent() : Theme.borderSoft());
		centered(graphics, dirLabel, x + dirW / 2, y + 4, COL_TEXT);

		String note = this.cfgMessage.isEmpty() ? "Клик по конфигу — загрузить · файлы можно скинуть другу" : this.cfgMessage;
		text(graphics, note, x + dirW + 8, y + 4, this.cfgMessage.startsWith("!") ? COL_ERROR : COL_TEXT_DIM);
		y += 22;

		List<String> names = Configs.list();

		if (names.isEmpty()) {
			text(graphics, "Конфигов пока нет. Введи название и нажми «Сохранить».", x + 2, y + 2, COL_TEXT_DIM);
		}

		for (int i = 0; i < names.size(); i++) {
			if (y > CONTENT_BOTTOM - 20) {
				text(graphics, "… ещё " + (names.size() - i) + " (.cfg list)", x + 2, y + 2, COL_TEXT_DIM);
				break;
			}

			String name = names.get(i);
			int deleteW = 18;
			int rowW = w - deleteW - 4;
			Hotspot row = addHotspot(KIND_CFG_LOAD, x, y, rowW, 18, name);
			boolean hovered = row.contains(this.designMouseX, this.designMouseY);
			UiDraw.box(graphics, x, y, rowW, 18, 9, hovered ? Theme.rowHover() : COL_ROW, hovered ? Theme.border() : Theme.borderSoft());
			UiDraw.fill(graphics, x + 8, y + 6, 6, 6, 3, hovered ? Theme.accent() : COL_OFF);
			text(graphics, name, x + 20, y + 5, hovered ? COL_TEXT : COL_TEXT_DIM);
			String tag = "загрузить";
			text(graphics, tag, x + rowW - 10 - this.font.width(tag), y + 5, hovered ? Theme.accent() : 0xFF6F7A6B);

			int dx = x + w - deleteW;
			Hotspot del = addHotspot(KIND_CFG_DELETE, dx, y, deleteW, 18, name);
			boolean delHover = del.contains(this.designMouseX, this.designMouseY);
			UiDraw.box(graphics, dx, y, deleteW, 18, 9, delHover ? 0xC0402020 : COL_ROW, delHover ? COL_ERROR : Theme.borderSoft());
			centered(graphics, "×", dx + deleteW / 2, y + 5, delHover ? COL_ERROR : COL_TEXT_DIM);
			y += 22;
		}

		this.maxScroll = 0;
	}

	private void clickConfig(int kind, Object ref) {
		switch (kind) {
			case KIND_CFG_SAVE -> {
				String name = Configs.clean(this.cfgInput);
				String error = Configs.save(this.cfgInput);
				this.cfgMessage = error == null ? "Сохранено: " + name : "! " + error;

				if (error == null) {
					this.cfgInput = "";
				}
			}
			case KIND_CFG_LOAD -> {
				String error = Configs.load((String) ref);
				this.cfgMessage = error == null ? "Загружен: " + ref : "! " + error;
			}
			case KIND_CFG_DELETE -> this.cfgMessage = Configs.delete((String) ref) ? "Удалён: " + ref : "! Не удалось удалить";
			case KIND_CFG_DIR -> Configs.openFolder();
			default -> {
			}
		}
	}

	// ---- Alt Manager ---------------------------------------------------------------------------------

	private static final int KIND_ALT_ADD = 10;
	private static final int KIND_ALT_USE = 11;
	private static final int KIND_ALT_DELETE = 12;
	private static final String MAIN_ACCOUNT = "#main";

	private void drawAlts(GuiGraphicsExtractor graphics) {
		int x = CONTENT_X;
		int w = CONTENT_W;
		int y = CONTENT_TOP;

		// Input + "Добавить".
		int buttonW = 74;
		int inputW = w - buttonW - 6;
		Hotspot input = addHotspot(KIND_SETTING, x, y, inputW, 18, this.altField);
		boolean focused = this.textFocus == this.altField;
		UiDraw.box(graphics, x, y, inputW, 18, 9,
				focused ? Theme.rowActive() : (input.contains(this.designMouseX, this.designMouseY) ? Theme.rowHover() : COL_ROW),
				focused ? Theme.accent() : Theme.borderSoft());

		if (this.altInput.isEmpty() && !focused) {
			text(graphics, this.altField.placeholder, x + 10, y + 5, 0xFF6F7A6B);
		} else {
			String shown = this.altInput;

			while (this.font.width(shown) > inputW - 22 && !shown.isEmpty()) {
				shown = shown.substring(1);
			}

			text(graphics, shown, x + 10, y + 5, COL_TEXT);

			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = x + 10 + this.font.width(shown) + 1;
				graphics.fill(cx, y + 4, cx + 1, y + 14, fade(Theme.accent()));
			}
		}

		Hotspot add = addHotspot(KIND_ALT_ADD, x + inputW + 6, y, buttonW, 18, null);
		boolean addHover = add.contains(this.designMouseX, this.designMouseY);
		UiDraw.box(graphics, x + inputW + 6, y, buttonW, 18, 9, addHover ? Theme.accent() : Theme.accentDim(), Theme.accent());
		centered(graphics, "Добавить", x + inputW + 6 + buttonW / 2, y + 5, addHover ? COL_TEXT_DARK : COL_TEXT);

		y += 22;
		String note = this.altMessage.isEmpty()
				? "Несколько ников — через пробел или запятую. Клик — войти."
				: this.altMessage;
		text(graphics, note, x + 2, y, this.altMessage.startsWith("!") ? COL_ERROR : COL_TEXT_DIM);
		y += 14;

		String active = this.config.activeAlt;

		// The launcher (Microsoft) account is always first.
		y = altRow(graphics, x, y, w, "Основной аккаунт · " + AltManager.launcherName(), "Microsoft", MAIN_ACCOUNT,
				active.isEmpty(), false);

		for (String alt : this.config.alts) {
			if (y > CONTENT_BOTTOM - 20) {
				text(graphics, "… ещё " + (this.config.alts.size() - this.config.alts.indexOf(alt)), x + 2, y + 2, COL_TEXT_DIM);
				break;
			}

			y = altRow(graphics, x, y, w, alt, "офлайн", alt, alt.equals(active), true);
		}

		this.maxScroll = 0;
	}

	private int altRow(GuiGraphicsExtractor graphics, int x, int y, int w, String name, String tag, String ref,
			boolean active, boolean deletable) {
		int deleteW = deletable ? 18 : 0;
		Hotspot row = addHotspot(KIND_ALT_USE, x, y, w - deleteW - (deletable ? 4 : 0), 18, ref);
		boolean hovered = row.contains(this.designMouseX, this.designMouseY);

		UiDraw.box(graphics, x, y, w - deleteW - (deletable ? 4 : 0), 18, 9,
				active ? Theme.rowActive() : (hovered ? Theme.rowHover() : COL_ROW),
				active ? Theme.accent() : (hovered ? Theme.border() : Theme.borderSoft()));
		UiDraw.fill(graphics, x + 8, y + 6, 6, 6, 3, active ? Theme.accent() : COL_OFF);
		text(graphics, name, x + 20, y + 5, active ? COL_TEXT : COL_TEXT_DIM);

		String right = active ? "активен" : tag;
		text(graphics, right, x + w - deleteW - (deletable ? 4 : 0) - 10 - this.font.width(right), y + 5,
				active ? Theme.accent() : 0xFF6F7A6B);

		if (deletable) {
			int dx = x + w - deleteW;
			Hotspot del = addHotspot(KIND_ALT_DELETE, dx, y, deleteW, 18, ref);
			boolean delHover = del.contains(this.designMouseX, this.designMouseY);
			UiDraw.box(graphics, dx, y, deleteW, 18, 9, delHover ? 0xC0402020 : COL_ROW, delHover ? COL_ERROR : Theme.borderSoft());
			centered(graphics, "×", dx + deleteW / 2, y + 5, delHover ? COL_ERROR : COL_TEXT_DIM);
		}

		return y + 22;
	}

	private void addAlts() {
		String[] names = this.altInput.trim().split("[\\s,;]+");
		int added = 0;
		String bad = null;

		for (String name : names) {
			if (name.isEmpty()) {
				continue;
			}

			if (AltManager.add(name)) {
				added++;
			} else if (!AltManager.isValid(name)) {
				bad = name;
			}
		}

		if (bad != null) {
			this.altMessage = "! Неверный ник: " + bad + " (3–16 символов: A–Z, 0–9, _)";
		} else if (added == 0) {
			this.altMessage = "! Такой ник уже есть";
		} else {
			this.altMessage = "Добавлено: " + added;
			this.altInput = "";
		}
	}

	private void drawEmpty(GuiGraphicsExtractor graphics) {
		int cx = CONTENT_X + CONTENT_W / 2;
		int cy = DESIGN_H / 2 - 14;

		if (!this.search.isEmpty()) {
			centered(graphics, "Ничего не найдено", cx, cy, COL_TEXT_DIM);
			return;
		}

		UiDraw.fill(graphics, cx - 6, cy - 22, 12, 12, 6, Theme.accent());
		centered(graphics, "dayener dls", cx, cy, COL_TEXT);
		centered(graphics, "Нажми ПКМ по категории слева", cx, cy + 12, COL_TEXT_DIM);
	}

	private void drawModule(GuiGraphicsExtractor graphics, Module module, int y) {
		Hotspot spot = addClippedHotspot(KIND_MODULE, CONTENT_X, y, CONTENT_W, MODULE_H, module);
		boolean hovered = spot != null && spot.contains(this.designMouseX, this.designMouseY);
		boolean on = module.isOn();

		UiDraw.box(graphics, CONTENT_X, y, CONTENT_W, MODULE_H, MODULE_H / 2,
				on ? Theme.rowActive() : (hovered ? Theme.rowHover() : COL_ROW),
				on ? Theme.accent() : (hovered ? Theme.border() : Theme.borderSoft()));

		int textX = CONTENT_X + 10;

		if (!module.settings.isEmpty() && !module.alwaysOpen) {
			float t = easeOut(this.expandAnim.getOrDefault(module.name, 0.0f));
			chevron(graphics, textX, y + 6, t, fade(t > 0.5f ? Theme.accent() : COL_TEXT_DIM));
			textX += 10;
		}

		text(graphics, module.name, textX, y + 5, on || !module.hasSwitch ? COL_TEXT : COL_TEXT_DIM);

		String bind = this.config.moduleBinds.get(module.name);

		if (bind != null) {
			text(graphics, "[" + InputConstants.getKey(bind).getDisplayName().getString() + "]",
					textX + this.font.width(module.name) + 6, y + 5, COL_TEXT_DIM);
		}

		int right = CONTENT_X + CONTENT_W - 8;

		if (module.hasSwitch) {
			right -= SWITCH_W;
			toggleSwitch(graphics, right, y + (MODULE_H - SWITCH_H) / 2, this.knobAnim.getOrDefault(module.name, 0.0f), hovered);
			right -= 8;
		}

		if (!this.search.isEmpty()) {
			String tag = module.category.name;
			text(graphics, tag, right - this.font.width(tag), y + 5, COL_TEXT_DIM);
		}
	}

	private void drawSettings(GuiGraphicsExtractor graphics, Module module, int y, int visibleHeight) {
		int x = CONTENT_X + SETTING_INDENT;
		int w = CONTENT_W - SETTING_INDENT * 2;
		int fullH = settingsHeight(module);
		boolean animating = visibleHeight < fullH + 2;

		// Slide effect: the panel moves down from under its module while it opens.
		int panelY = y + 2 - (fullH + 2 - visibleHeight);

		if (animating) {
			graphics.enableScissor(CONTENT_X - 2, y, CONTENT_X + CONTENT_W + 2, y + visibleHeight);
		}

		UiDraw.box(graphics, x - 4, panelY, w + 8, fullH, 9, COL_PANEL, Theme.borderSoft());

		int rowY = panelY + SETTING_PAD;

		for (Setting setting : module.settings) {
			drawSetting(graphics, setting, x + 4, rowY, w - 8, !animating);
			rowY += setting.height();
		}

		if (animating) {
			graphics.disableScissor();
		}
	}

	private void drawSetting(GuiGraphicsExtractor graphics, Setting setting, int x, int y, int w, boolean interactive) {
		Hotspot spot = interactive ? addClippedHotspot(KIND_SETTING, x, y, w, setting.height(), setting) : null;
		boolean hovered = spot != null && spot.contains(this.designMouseX, this.designMouseY);
		int labelColor = hovered ? COL_TEXT : COL_TEXT_DIM;

		switch (setting) {
			case Toggle toggle -> {
				boolean checked = toggle.get.getAsBoolean();
				UiDraw.box(graphics, x, y + 2, 8, 8, 4,
						checked ? Theme.accent() : COL_TRACK,
						checked ? Theme.accent() : (hovered ? Theme.border() : Theme.borderSoft()));
				text(graphics, toggle.name, x + 13, y + 2, checked ? COL_TEXT : labelColor);
			}
			case Slider slider -> {
				String value = slider.format.apply(slider.get.getAsDouble());
				text(graphics, slider.name, x, y + 1, labelColor);
				text(graphics, value, x + w - this.font.width(value), y + 1, COL_TEXT);

				int trackY = y + 12;
				int trackH = 4;
				int filled = (int) Math.round(w * Math.clamp(slider.progress(), 0.0, 1.0));

				if (slider.hueTrack) {
					for (int i = 0; i < w; i += 2) {
						int c = Mth.hsvToArgb(i / (float) w, 0.56f, 0.9f, 0xFF);
						UiDraw.fill(graphics, x + i, trackY, Math.min(2, w - i), trackH, 0, c);
					}
				} else {
					UiDraw.fill(graphics, x, trackY, w, trackH, 2, hovered ? COL_TRACK_HOVER : COL_TRACK);

					if (filled > 0) {
						UiDraw.fill(graphics, x, trackY, Math.max(trackH, filled), trackH, 2, Theme.accent());
					}
				}

				int knob = 8;
				int knobX = Math.max(x, Math.min(x + w - knob, x + filled - knob / 2));
				UiDraw.box(graphics, knobX, trackY - 2, knob, knob, 4, Theme.accent(), slider.hueTrack ? 0xFFFFFFFF : Theme.accent());
			}
			case Mode mode -> {
				String value = "‹ " + mode.value.get() + " ›";
				text(graphics, mode.name, x, y + 2, labelColor);
				text(graphics, value, x + w - this.font.width(value), y + 2, Theme.accent());
			}
			case Bind bind -> {
				KeyMapping key = bind.key.get();
				boolean binding = this.bindTarget == key;
				bindChip(graphics, bind.name, binding ? "..." : key.getTranslatedKeyMessage().getString(), binding, hovered, x, y, w,
						labelColor);
			}
			case KeySetting bind -> {
				boolean binding = bind == this.bindKey;
				String stored = bind.get.get();
				String keyText = binding ? "..." : stored == null || stored.isEmpty() ? "нет"
						: InputConstants.getKey(stored).getDisplayName().getString();
				net.minecraft.world.item.ItemStack icon = bind.iconStack();

				if (icon != null) {
					var pose = graphics.pose();
					pose.pushMatrix();
					pose.translate(x, y - 1);
					pose.scale(0.75f);
					graphics.item(icon, 0, 0);
					pose.popMatrix();
					bindChip(graphics, bind.name, keyText, binding, hovered, x + 15, y, w - 15, labelColor);
				} else {
					bindChip(graphics, bind.name, keyText, binding, hovered, x, y, w, labelColor);
				}
			}
			case Action action -> {
				int buttonW = this.font.width(action.button) + 14;
				text(graphics, action.label.get(), x, y + 2, COL_TEXT_DIM);
				UiDraw.box(graphics, x + w - buttonW, y, buttonW, 12, 6,
						hovered ? Theme.rowHover() : COL_ROW, hovered ? Theme.accent() : Theme.borderSoft());
				centered(graphics, action.button, x + w - buttonW / 2, y + 2, COL_TEXT);
			}
			case TextField field -> {
				boolean focused = this.textFocus == field;
				String value = field.get.get();
				int boxW = 120;
				int boxX = x + w - boxW;
				text(graphics, field.name, x, y + 2, labelColor);
				UiDraw.box(graphics, boxX, y, boxW, 12, 6,
						focused ? Theme.rowActive() : (hovered ? Theme.rowHover() : COL_ROW),
						focused ? Theme.accent() : Theme.borderSoft());

				if (value.isEmpty() && !focused) {
					text(graphics, field.placeholder, boxX + 6, y + 2, 0xFF6F7A6B);
				} else {
					text(graphics, value, boxX + 6, y + 2, COL_TEXT);

					if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
						int cx = boxX + 6 + this.font.width(value) + 1;
						graphics.fill(cx, y + 2, cx + 1, y + 10, fade(Theme.accent()));
					}
				}
			}
			case Note note -> text(graphics, note.name, x, y + 2, 0xFF6F7A6B);
			case Header header -> {
				text(graphics, header.name, x, y + 5, Theme.accent());
				int lineX = x + this.font.width(header.name) + 6;
				UiDraw.fill(graphics, lineX, y + 9, Math.max(0, x + w - lineX), 1, 0, Theme.borderSoft());
			}
			case ColorPicker picker -> drawColorPicker(graphics, picker, x, y, w);
			default -> {
			}
		}
	}

	/** Like the screenshot: title, a rounded saturation/brightness square and a rounded rainbow hue bar. */
	private void drawColorPicker(GuiGraphicsExtractor graphics, ColorPicker picker, int x, int y, int w) {
		text(graphics, picker.name, x, y + 1, COL_TEXT);

		float hue = this.config.guiHue / 360.0f;
		int svY = y + ColorPicker.TITLE;
		int svH = ColorPicker.SV_H;
		int radius = 7;

		// Columns: white → pure hue horizontally, each column fading to black vertically.
		// 1-px columns only where the corners curve, 3-px columns in between (far fewer draw calls).
		for (int i = 0; i < w; ) {
			int step = i < radius || i >= w - radius ? 1 : Math.min(3, w - radius - i);
			int inset = Math.round(roundInset(radius, w, i));
			int top = Mth.hsvToArgb(hue, (i + step / 2.0f) / (w - 1), 1.0f, 0xFF);
			graphics.fillGradient(x + i, svY + inset, x + i + step, svY + svH - inset, fade(top), fade(0xFF000000));
			i += step;
		}

		int knobX = x + Math.round(this.config.guiSat * (w - 1));
		int knobY = svY + Math.round((1.0f - this.config.guiVal) * (svH - 1));
		UiDraw.fill(graphics, knobX - 5, knobY - 5, 10, 10, 5, 0xFFFFFFFF);
		UiDraw.fill(graphics, knobX - 3, knobY - 3, 6, 6, 3, Theme.accent());

		int hueY = svY + svH + ColorPicker.GAP;
		int hueH = ColorPicker.HUE_H;

		for (int i = 0; i < w; ) {
			int step = i < hueH / 2 || i >= w - hueH / 2 ? 1 : Math.min(3, w - hueH / 2 - i);
			int inset = Math.round(roundInset(hueH / 2, w, i));
			graphics.fill(x + i, hueY + inset, x + i + step, hueY + hueH - inset,
					fade(Mth.hsvToArgb((i + step / 2.0f) / w, 1.0f, 1.0f, 0xFF)));
			i += step;
		}

		int hx = x + Math.round(hue * (w - 1));
		UiDraw.fill(graphics, hx - 4, hueY - 2, 8, hueH + 4, 4, 0xFFFFFFFF);
	}

	/** Vertical inset of a rounded rectangle's left/right edge for column {@code i}. */
	private static float roundInset(int r, int w, int i) {
		int edge = Math.min(i, w - 1 - i);

		if (edge >= r) {
			return 0.0f;
		}

		double dx = r - edge - 0.5;
		return (float) (r - Math.sqrt(Math.max(0.0, (double) r * r - dx * dx)));
	}

	private void bindChip(GuiGraphicsExtractor graphics, String label, String keyText, boolean binding, boolean hovered,
			int x, int y, int w, int labelColor) {
		int chipW = Math.max(28, this.font.width(keyText) + 12);
		text(graphics, label, x, y + 2, labelColor);
		UiDraw.box(graphics, x + w - chipW, y, chipW, 12, 6,
				binding ? Theme.accentDim() : (hovered ? Theme.rowHover() : Theme.rowActive()),
				binding || hovered ? Theme.accent() : Theme.borderSoft());
		centered(graphics, keyText, x + w - chipW / 2, y + 2, binding ? Theme.accent() : COL_TEXT);
	}

	/** Small arrow that rotates from "right" (closed) to "down" (open) as {@code t} goes 0 → 1. */
	private static void chevron(GuiGraphicsExtractor graphics, int x, int y, float t, int color) {
		if (t < 0.5f) {
			for (int i = 0; i < 3; i++) {
				graphics.fill(x + 1 + i, y + i, x + 2 + i, y + 6 - i, color);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				graphics.fill(x + i, y + 1 + i, x + 6 - i, y + 2 + i, color);
			}
		}
	}

	private void toggleSwitch(GuiGraphicsExtractor graphics, int x, int y, float t, boolean hovered) {
		float e = easeOut(t);
		boolean lit = t > 0.5f;
		UiDraw.box(graphics, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2,
				lit ? Theme.accentDim() : COL_ROW,
				hovered || lit ? Theme.accent() : Theme.borderSoft());
		int knobX = x + 2 + Math.round((SWITCH_W - 12) * e);
		UiDraw.fill(graphics, knobX, y + 2, 8, 8, 4, lit ? Theme.accent() : COL_OFF);
	}

	private void drawHint(GuiGraphicsExtractor graphics) {
		String hint;
		int color = COL_TEXT_DIM;
		Module hoveredModule = hoveredModule();

		if (this.bindTarget != null) {
			hint = "Нажми клавишу или кнопку мыши · ESC — отмена";
		} else if (this.bindKey != null) {
			hint = "Нажми клавишу · Backspace — убрать бинд · ESC — отмена";
		} else if (this.bindError) {
			hint = "Эта кнопка уже занята — выбери другую";
			color = COL_ERROR;
		} else if (hoveredModule != null) {
			hint = hoveredModule.description;

			if (!hoveredModule.settings.isEmpty() && !hoveredModule.alwaysOpen) {
				hint += " · ПКМ — настройки и бинд";
			}
		} else if (this.category == Category.CONFIGS && this.search.isEmpty()) {
			hint = "Чат: .cfg save <имя> · .cfg load <имя> · .cfg dir · × — удалить";
		} else if (this.category == Category.ALTS && this.search.isEmpty()) {
			hint = "Офлайн-ники работают на серверах без лицензии · × — удалить";
		} else if (this.category == null && this.search.isEmpty()) {
			hint = "ПКМ по категории — открыть · начни печатать для поиска";
		} else {
			hint = "ЛКМ — вкл/выкл · ПКМ — настройки модуля";
		}

		text(graphics, hint, CONTENT_X, HINT_Y, color);
	}

	private Module hoveredModule() {
		for (Hotspot spot : this.hotspots) {
			if (spot.kind() == KIND_MODULE && spot.contains(this.designMouseX, this.designMouseY)) {
				return (Module) spot.ref();
			}
		}

		return null;
	}

	private Hotspot addHotspot(int kind, int x, int y, int w, int h, Object ref) {
		Hotspot spot = new Hotspot(kind, x, y, w, h, ref);
		this.hotspots.add(spot);
		return spot;
	}

	/** Adds a hotspot only for the part that is visible inside the scrolling content area. */
	private Hotspot addClippedHotspot(int kind, int x, int y, int w, int h, Object ref) {
		int top = Math.max(y, CONTENT_TOP);
		int bottom = Math.min(y + h, CONTENT_BOTTOM);

		if (bottom <= top) {
			return null;
		}

		return addHotspot(kind, x, top, w, bottom - top, ref);
	}

	private int moduleCount(Category cat) {
		int count = 0;

		for (Module module : this.modules) {
			if (module.category == cat) {
				count++;
			}
		}

		return count;
	}

	private boolean categoryActive(Category cat) {
		for (Module module : this.modules) {
			if (module.category == cat && module.isOn()) {
				return true;
			}
		}

		return false;
	}

	// ------------------------------------------------------------------------------------------------
	// Input
	// ------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int button = event.button();
		double mouseX = (event.x() - this.ox) / this.scale;
		double mouseY = (event.y() - this.oy) / this.scale;

		if (this.bindTarget != null) {
			if (button >= 0 && button <= 7) {
				applyBinding(InputConstants.Type.MOUSE.getOrCreate(button));
			}

			return true;
		}

		if (this.bindKey != null) {
			// Left / right click cancel; other mouse buttons (wheel, side buttons) can be bound.
			if (button >= 2 && button <= 7) {
				this.bindKey.set.accept(InputConstants.Type.MOUSE.getOrCreate(button).getName());
				this.config.save();
			}

			this.bindKey = null;
			return true;
		}

		Hotspot hit = null;

		for (Hotspot spot : this.hotspots) {
			if (spot.contains(mouseX, mouseY)) {
				hit = spot; // later hotspots (settings) are more specific
			}
		}

		this.searchFocused = hit != null && hit.kind() == KIND_SEARCH;

		if (hit == null || hit.ref() != this.textFocus) {
			this.textFocus = null;
		}

		if (hit == null) {
			return super.mouseClicked(event, doubleClick);
		}

		switch (hit.kind()) {
			case KIND_CATEGORY -> openCategory((Category) hit.ref());
			case KIND_SEARCH -> {
				if (button == 1) {
					this.search = "";
					this.scroll = 0;
				}
			}
			case KIND_MODULE -> clickModule((Module) hit.ref(), button);
			case KIND_SETTING -> clickSetting((Setting) hit.ref(), hit, button, mouseX);
			case KIND_ALT_ADD -> addAlts();
			case KIND_ALT_USE -> {
				String ref = (String) hit.ref();
				AltManager.use(MAIN_ACCOUNT.equals(ref) ? null : ref);
				this.altMessage = "Вход под ником: " + (MAIN_ACCOUNT.equals(ref) ? AltManager.launcherName() : ref)
						+ " — перезайди на сервер";
			}
			case KIND_ALT_DELETE -> AltManager.remove((String) hit.ref());
			case KIND_CFG_SAVE, KIND_CFG_LOAD, KIND_CFG_DELETE, KIND_CFG_DIR -> clickConfig(hit.kind(), hit.ref());
			default -> {
			}
		}

		return true;
	}

	private void clickModule(Module module, int button) {
		boolean canExpand = !module.settings.isEmpty() && !module.alwaysOpen;

		if (button == 1 || !module.hasSwitch) {
			if (canExpand && !EXPANDED.remove(module.name)) {
				EXPANDED.add(module.name);
			}

			return;
		}

		if (button == 0) {
			Modules.toggle(module.name);
		}
	}

	private void clickSetting(Setting setting, Hotspot spot, int button, double mouseX) {
		switch (setting) {
			case Toggle toggle -> toggle.set.accept(!toggle.get.getAsBoolean());
			case Slider slider -> {
				this.dragging = slider;
				this.dragX = spot.x();
				this.dragW = spot.w();
				slider.setProgress((mouseX - this.dragX) / this.dragW);
				return;
			}
			case Mode mode -> {
				if (button == 1) {
					mode.previous.run();
				} else {
					mode.next.run();
				}
			}
			case Bind bind -> {
				this.bindTarget = bind.key.get();
				this.bindError = false;
				return;
			}
			case KeySetting bind -> {
				if (button == 1) {
					bind.set.accept(null); // RMB on the chip clears the bind
				} else {
					this.bindKey = bind;
					this.bindError = false;
					return;
				}
			}
			case Action action -> action.run.run();
			case TextField field -> {
				this.textFocus = field;
				return;
			}
			case ColorPicker picker -> {
				double mouseY = this.designMouseY;
				int svTop = spot.y() + ColorPicker.TITLE;
				int hueTop = svTop + ColorPicker.SV_H + ColorPicker.GAP - 2;

				if (mouseY >= hueTop) {
					this.colorDrag = 2;
				} else if (mouseY >= svTop - 2) {
					this.colorDrag = 1;
				} else {
					return;
				}

				this.colorX = spot.x();
				this.colorY = svTop;
				this.colorW = spot.w();
				updateColor(mouseX, mouseY);
				return;
			}
			default -> {
			}
		}

		this.config.save();
	}

	private void updateColor(double mouseX, double mouseY) {
		float fx = (float) Math.clamp((mouseX - this.colorX) / Math.max(1, this.colorW - 1), 0.0, 1.0);

		if (this.colorDrag == 2) {
			this.config.guiHue = Math.min(359.0f, fx * 360.0f);
		} else if (this.colorDrag == 1) {
			this.config.guiSat = fx;
			this.config.guiVal = 1.0f - (float) Math.clamp((mouseY - this.colorY) / (ColorPicker.SV_H - 1), 0.0, 1.0);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging != null || this.colorDrag != 0) {
			this.dragging = null;
			this.colorDrag = 0;
			this.config.save();
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double mouseX = (event.x() - this.ox) / this.scale;

		if (this.colorDrag != 0) {
			updateColor(mouseX, (event.y() - this.oy) / this.scale);
			return true;
		}

		if (this.dragging != null) {
			this.dragging.setProgress((mouseX - this.dragX) / this.dragW);
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		this.scroll = Math.clamp(this.scroll - (int) Math.round(scrollY * 16), 0, this.maxScroll);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.bindTarget != null) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.bindTarget = null;
				return true;
			}

			applyBinding(InputConstants.getKey(event));
			return true;
		}

		if (this.bindKey != null) {
			switch (event.key()) {
				case GLFW.GLFW_KEY_ESCAPE -> {
				}
				case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> this.bindKey.set.accept(null);
				default -> this.bindKey.set.accept(InputConstants.getKey(event).getName());
			}

			this.config.save();
			this.bindKey = null;
			return true;
		}

		if (this.textFocus != null) {
			String value = this.textFocus.get.get();

			switch (event.key()) {
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!value.isEmpty()) {
						this.textFocus.set.accept(value.substring(0, value.length() - 1));
					}
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					if (this.textFocus == this.altField) {
						addAlts();
					} else {
						this.textFocus = null;
						this.config.save();
					}
				}
				case GLFW.GLFW_KEY_ESCAPE -> {
					this.textFocus = null;
					this.config.save();
				}
				default -> {
				}
			}

			return true;
		}

		if (this.searchFocused) {
			switch (event.key()) {
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!this.search.isEmpty()) {
						this.search = this.search.substring(0, this.search.length() - 1);
						this.scroll = 0;
					}
				}
				case GLFW.GLFW_KEY_ESCAPE -> {
					if (this.search.isEmpty()) {
						this.searchFocused = false;
					} else {
						this.search = "";
						this.scroll = 0;
					}
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> this.searchFocused = false;
				default -> {
				}
			}

			return true;
		}

		// The menu key closes the menu again.
		if (TriggerBotClient.screenKey().matches(event)) {
			this.onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.bindTarget != null || this.bindKey != null || !event.isAllowedChatCharacter()) {
			return false;
		}

		if (this.textFocus != null) {
			String value = this.textFocus.get.get();

			if (value.length() < this.textFocus.maxLength) {
				this.textFocus.set.accept(value + event.codepointAsString());
			}

			return true;
		}

		// With InvMove on, WASD keep walking instead of starting a search.
		if (!this.searchFocused && this.config.invMove) {
			return false;
		}

		// Typing anywhere starts a search.
		this.searchFocused = true;

		if (this.search.length() < 24) {
			this.search += event.codepointAsString();
			this.scroll = 0;
		}

		return true;
	}

	@Override
	public void onClose() {
		this.config.save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void openCategory(Category cat) {
		this.category = cat;
		lastCategory = cat;
		this.search = "";
		this.searchFocused = false;
		this.scroll = 0;
		this.bindTarget = null;
		this.bindKey = null;
		this.textFocus = null;
		this.bindError = false;
	}

	private void applyBinding(InputConstants.Key key) {
		KeyMapping target = this.bindTarget;
		this.bindTarget = null;
		this.bindError = !TriggerBotClient.rebind(target, key);
	}
}
