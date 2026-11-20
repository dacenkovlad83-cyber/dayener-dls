package com.triger.triggerbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All user settings. Persisted as JSON in {@code config/triggerbot.json}.
 */
public final class TriggerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch, restored on startup. */
	public boolean enabled = false;

	public boolean attackPlayers = true;
	public boolean attackHostile = true;
	public boolean attackPassive = false;

	/** Maximum distance to the target, in blocks. */
	public float range = 3.0f;

	/** Attacks per second, used to throttle the automatic swings. */
	public int minCps = 8;
	public int maxCps = 12;

	/** OFF = hit whenever, ONLY_CRITS = wait for a crit, SMART = crit only while airborne. */
	public CritMode critMode = CritMode.SMART;

	public boolean randomDelay = true;

	/** Combat → AimAssist. aimMode: 0 свободная, 1 горизонталь, 2 резкая. aimPriority: 0 угол, 1 дистанция, 2 здоровье. */
	public boolean aimAssist = false;
	public int aimMode = 0;
	public float aimSpeed = 15.0f;
	public float aimCorrection = 0.5f;
	public float aimFreeDown = 0.0f;
	public boolean aimSmoothEntry = true;
	public boolean aimUseSens = true;
	public boolean aimSlowdown = true;
	public boolean aimOnlyMouse = false;
	public float aimSlowAngle = 6.0f;
	public int aimPriority = 0;
	public float aimRange = 4.5f;
	public float aimFov = 360.0f;
	public float aimHold = 5.0f;
	public boolean aimLastHitOnly = false;
	public boolean aimPlayers = true;
	public boolean aimHostile = false;
	public boolean aimPassive = false;
	public boolean aimOnlyWeapon = true;
	public boolean aimOnlyLmb = false;
	public boolean aimVisible = true;
	/** Наводиться только для сервера: камера не двигается. */
	public boolean aimSilent = false;

	/** Movement → NoPush: от чего игрока не отталкивает. */
	public boolean noPush = false;
	public boolean noPushEntities = true;
	public boolean noPushBlocks = true;
	public boolean noPushWater = false;
	public boolean noPushRod = true;

	/** Helper → HUD: плашки с биндами хелпера. */
	public boolean helperHud = false;
	public float hhScale = 1.0f;
	public int hhX = -1;
	public int hhY = -1;

	/** TriggerBot: бить сквозь блоки (луч к цели игнорирует стены). */
	public boolean throughWalls = false;

	/** TriggerBot: булавой в падении бить сразу, не дожидаясь полной зарядки. */
	public boolean maceNoCharge = true;

	/** TriggerBot: бить сразу как оружие готово, каждый раз в чуть разный момент (CPS не учитывается). */
	public boolean fastRandom = true;
	/** Максимальная случайная задержка после зарядки удара, мс. */
	public int fastRandomSpread = 25;
	public boolean requireLineOfSight = true;

	public boolean showHud = true;
	public boolean showHighlight = true;

	public List<Friend> friends = new ArrayList<>();

	/** Visuals → Fullbright: максимальная яркость без зелья ночного зрения. */
	public boolean fullbright = false;

	/** Visuals → NoRender: общий переключатель модуля и его опции. */
	public boolean noRender = false;
	public boolean noRenderFire = true;
	public boolean noRenderHurtShake = true;
	public boolean noRenderHandShake = false;
	public boolean noRenderBadEffects = true;
	public boolean noRenderInBlock = true;
	public boolean noRenderTotem = true;
	public boolean noRenderFireworks = true;
	/** NoRender: частицы крита и зачарованного удара. */
	public boolean noRenderCrits = false;
	public boolean noRenderWaterSplash = false;
	public boolean noRenderLavaSplash = false;
	public boolean noRenderRain = true;
	public boolean noRenderSnow = true;
	public boolean noRenderFog = false;

	/** Visuals → NameTags: свои таблички над игроками. */
	public boolean nameTags = false;
	public boolean nameTagsArmor = true;
	public boolean nameTagsItems = true;
	public boolean nameTagsHealth = true;
	/** Брать имя из таба/команды сервера — там донаты и префиксы (символы из серверного ресурспака). */
	public boolean nameTagsPrefixes = true;
	public int nameTagsRange = 64;
	public float nameTagsScale = 1.0f;

	/** Utility → AutoEat. */
	public boolean autoEat = false;
	/** Есть, когда голод (0–20) опускается до этого значения. */
	public int autoEatHunger = 14;
	/** Перестать копать, чтобы начать есть. */
	public boolean autoEatStopMining = true;

	/** Utility → AutoLeave. */
	public boolean autoLeave = false;
	/** Выйти, когда здоровья (в половинках сердец) осталось столько или меньше. */
	public float autoLeaveHealth = 6.0f;
	/** Выйти, когда чужой игрок ближе этой дистанции. 0 = не проверять. */
	public int autoLeaveDistance = 0;

	/** Combat → AutoTotem. */
	public boolean autoTotem = false;
	/** Брать тотем, когда HP (с поглощением) столько или меньше. */
	public float autoTotemHealth = 10.0f;
	/** Сначала брать обычные тотемы (без своего названия), именные — только если обычных нет. */
	public boolean autoTotemPlainFirst = true;
	/** Учитывать золотые сердца (поглощение) в HP. */
	public boolean autoTotemAbsorption = true;
	/** Брать тотем, когда рядом враг с булавой. */
	public boolean autoTotemMace = true;
	/** Брать тотем, когда рядом кристалл края. */
	public boolean autoTotemCrystals = true;
	/** Дальность проверки булавы и кристаллов, в блоках. */
	public int autoTotemRange = 6;

	/** Movement. Freecam и AirStuck не сохраняются — после перезахода они выключены. */
	public boolean autoSprint = false;
	public boolean invMove = false;
	public transient boolean freecam = false;
	public float freecamSpeedXZ = 1.0f;
	public float freecamSpeedY = 0.6f;
	public transient boolean airStuck = false;

	/** Visuals → TargetESP: крутящийся маркер на цели. */
	public boolean targetEsp = false;
	public float targetEspSize = 1.0f;
	public float targetEspSpeed = 1.0f;
	/** false = белый, true = цвет меню. */
	public boolean targetEspAccent = false;
	/** 0 = Маркер, 1 = Призраки. */
	public int targetEspMode = 0;
	/** Призраки: ширина орбиты (множитель). */
	public float targetEspWidth = 1.0f;

	/** Visuals → CustomHands: какие руки видны и их положение (View Model). 0 = обе, 1 = правая, 2 = левая. */
	public boolean customHands = false;
	public int handsMode = 0;
	public float rightHandX = 0.0f;
	public float rightHandY = 0.0f;
	public float rightHandZ = 0.0f;
	public float leftHandX = 0.0f;
	public float leftHandY = 0.0f;
	public float leftHandZ = 0.0f;
	/** View Model: размер рук (1.0 = обычный). */
	public float rightHandScale = 1.0f;
	public float leftHandScale = 1.0f;

	/** Player → ItemScroller и NoJumpDelay. */
	public boolean itemScroller = false;
	public int itemScrollerDelay = 10;
	public boolean noJumpDelay = false;

	/** Interface → Watermark. */
	public boolean watermark = false;
	public boolean wmNick = true;
	public String wmNickText = "";
	public boolean wmFps = true;
	public boolean wmTime = true;
	public boolean wmCoords = true;
	public boolean wmPing = true;
	public boolean wmTps = true;
	public boolean wmBps = true;
	public int wmX = 6;
	public int wmY = 6;

	/** Movement → ElytraSwap: клавиши смены элитр и фейерверка. */
	public boolean elytraSwap = false;
	public String elytraKey = "";
	public String fireworkKey = "";

	/** Alt Manager: офлайн-ники и активный ник ("" = основной аккаунт лаунчера). */
	public List<String> alts = new ArrayList<>();
	public String activeAlt = "";

	/** Interface → ArmorHUD: своя броня с прочностью. ahX/ahY = -1 — над хотбаром по центру. */
	public boolean armorHud = false;
	public boolean ahVertical = false;
	public boolean ahPercent = true;
	public boolean ahHands = false;
	public boolean ahBackground = true;
	public float ahScale = 1.0f;
	public int ahX = -1;
	public int ahY = -1;

	/** Interface → TargetHUD: инфо о цели. thX/thY = -1 — возле прицела. */
	public boolean targetHud = false;
	public boolean thItems = false;
	public float thScale = 1.0f;
	public int thX = -1;
	public int thY = -1;

	/** Interface → Effects: активные эффекты. */
	public boolean effectsHud = false;
	public boolean efHidden = false;
	public float efScale = 1.0f;
	public int efX = -1;
	public int efY = -1;

	/** Interface → Keybinds: список биндов на экране. */
	public boolean keybindsHud = false;
	public float kbScale = 1.0f;
	public int kbX = -1;
	public int kbY = -1;

	/** Interface → HUD → Cooldowns: предметы на задержке и сколько секунд осталось. */
	public boolean cooldownHud = false;
	public float cdScale = 1.0f;
	public int cdX = -1;
	public int cdY = -1;

	/** Combat → AutoSwap: по клавише меняет предмет во второй руке между двумя выбранными. */
	public boolean autoSwap = false;
	public String autoSwapKey = "";
	public int autoSwapFirst = 0;
	public int autoSwapSecond = 1;

	/** Visuals → FreeLook: камера крутится отдельно от игрока. Пустая клавиша — пока модуль включён. */
	public boolean freeLook = false;
	public String freeLookKey = "";
	public boolean freeLookThirdPerson = true;

	/** Combat → SpearSwap: после удара свап на копьё. spearSwapBack = через сколько тиков вернуть (0 — не возвращать). */
	public boolean spearSwap = false;
	public boolean spearSwapPlayersOnly = false;
	public String spearSwapKey = "";
	/** Через сколько тиков после ЛКМ взять копьё. */
	public int spearSwapDelay = 2;
	public int spearSwapBack = 0;

	/** Player → ClickPearl: эндер-жемчуг по клавише. */
	public boolean clickPearl = false;
	public String clickPearlKey = "";

	/** Player → AutoTool: лучший инструмент из хотбара при копании. */
	public boolean autoTool = false;
	public boolean autoToolStop = false;
	public boolean autoToolBack = true;

	/** Player → LockSlot: битовая маска заблокированных слотов хотбара (бит 0 = слот 1). */
	public boolean lockSlot = false;
	public boolean lockHotbar = false;
	public int lockedSlots = 0;
	public String lockSlotKey = "";

	/** Player → Helper: клавиши серверных предметов (id из Helper.ENTRIES → клавиша). */
	public boolean helper = false;
	public Map<String, String> helperKeys = new HashMap<>();

	/** Player → FastExp: бутылочки опыта без задержки. */
	public boolean fastExp = false;
	public int fastExpDelay = 0;

	/** Visuals → ShulkerPreview: содержимое шалкера при зажатом Shift. */
	public boolean shulkerPreview = false;

	/** Visuals → TotemCounter: сколько тотемов осталось. 0 = возле прицела, 1 = над хотбаром. */
	public boolean totemCounter = false;
	public int tcPosition = 0;
	public float tcScale = 1.0f;

	/** Watermark → размер. */
	public float wmScale = 1.0f;

	/** Бинды модулей: имя модуля → клавиша (например "key.keyboard.f"). */
	public Map<String, String> moduleBinds = new HashMap<>();

	/** Interface → цвет меню (оттенок 0–360). 94 — исходный зелёный. */
	public float guiHue = 94.0f;

	/** Interface → непрозрачность меню, 0.3–1.0. */
	public float guiOpacity = 0.75f;

	/** Interface → непрозрачность плашек HUD на экране, 0.2–1.0. */
	public float hudOpacity = 0.9f;

	/** Interface → цвет меню: насыщенность и яркость (оттенок — guiHue). */
	public float guiSat = 0.56f;
	public float guiVal = 0.90f;

	/** Interface → HUD → размер метки GPS. */
	public float gpsScale = 1.0f;

	/** Set once after the menu key moved from J to Right Shift. */
	public boolean menuKeyMigrated = false;

	public enum CritMode {
		OFF("Выкл"),
		ONLY_CRITS("Только криты"),
		SMART("Смарт");

		private final String label;

		CritMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public CritMode next() {
			CritMode[] values = values();
			return values[(ordinal() + 1) % values.length];
		}

		public CritMode previous() {
			CritMode[] values = values();
			return values[(ordinal() + values.length - 1) % values.length];
		}
	}

	public static final class Friend {
		public String uuid = "";
		public String name = "";

		public Friend() {
		}

		public Friend(String uuid, String name) {
			this.uuid = uuid;
			this.name = name;
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("triggerbot.json");
	}

	public static TriggerConfig load() {
		TriggerConfig config = new TriggerConfig();
		Path path = file();

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				TriggerConfig loaded = GSON.fromJson(reader, TriggerConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} catch (Exception exception) {
				TriggerBotClient.LOGGER.warn("Failed to read triggerbot.json, using defaults", exception);
			}
		}

		config.sanitize();
		config.save();
		return config;
	}

	public void save() {
		sanitize();

		try {
			Files.createDirectories(file().getParent());

			try (Writer writer = Files.newBufferedWriter(file())) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			TriggerBotClient.LOGGER.warn("Failed to save triggerbot.json", exception);
		}
	}

	private void sanitize() {
		range = Math.clamp(range, 1.0f, 6.0f);
		minCps = Math.clamp(minCps, 1, 20);
		maxCps = Math.clamp(maxCps, minCps, 20);
		guiOpacity = Math.clamp(guiOpacity, 0.3f, 1.0f);
		hudOpacity = hudOpacity <= 0.0f ? 0.9f : Math.clamp(hudOpacity, 0.2f, 1.0f);
		fastRandomSpread = Math.clamp(fastRandomSpread, 0, 100);
		aimMode = Math.clamp(aimMode, 0, 2);
		aimPriority = Math.clamp(aimPriority, 0, 2);
		aimSpeed = aimSpeed <= 0.0f ? 15.0f : Math.clamp(aimSpeed, 1.0f, 30.0f);
		aimCorrection = Math.clamp(aimCorrection, 0.05f, 1.0f);
		aimFreeDown = Math.clamp(aimFreeDown, -1.0f, 1.0f);
		aimSlowAngle = Math.clamp(aimSlowAngle, 0.5f, 30.0f);
		aimRange = aimRange <= 0.0f ? 4.5f : Math.clamp(aimRange, 1.0f, 8.0f);
		aimFov = aimFov <= 0.0f ? 360.0f : Math.clamp(aimFov, 10.0f, 360.0f);
		aimHold = Math.clamp(aimHold, 0.0f, 15.0f);
		hhScale = hhScale <= 0.0f ? 1.0f : Math.clamp(hhScale, 0.5f, 2.0f);
		guiSat = Math.clamp(guiSat, 0.0f, 1.0f);
		guiVal = Math.clamp(guiVal, 0.0f, 1.0f);
		gpsScale = gpsScale <= 0.0f ? 1.0f : Math.clamp(gpsScale, 0.5f, 1.5f);
		nameTagsRange = Math.clamp(nameTagsRange, 8, 256);
		nameTagsScale = Math.clamp(nameTagsScale, 0.5f, 2.0f);
		autoEatHunger = Math.clamp(autoEatHunger, 1, 19);
		autoLeaveHealth = Math.clamp(autoLeaveHealth, 1.0f, 19.0f);
		autoLeaveDistance = Math.clamp(autoLeaveDistance, 0, 64);
		autoTotemHealth = Math.clamp(autoTotemHealth, 1.0f, 20.0f);
		autoTotemRange = Math.clamp(autoTotemRange, 1, 12);
		freecamSpeedXZ = Math.clamp(freecamSpeedXZ, 0.1f, 5.0f);
		freecamSpeedY = Math.clamp(freecamSpeedY, 0.1f, 5.0f);
		guiHue = ((guiHue % 360.0f) + 360.0f) % 360.0f;
		targetEspSize = Math.clamp(targetEspSize, 0.5f, 2.0f);
		targetEspSpeed = Math.clamp(targetEspSpeed, 0.0f, 5.0f);
		handsMode = Math.clamp(handsMode, 0, 2);
		rightHandScale = rightHandScale <= 0.0f ? 1.0f : Math.clamp(rightHandScale, 0.2f, 2.0f);
		leftHandScale = leftHandScale <= 0.0f ? 1.0f : Math.clamp(leftHandScale, 0.2f, 2.0f);
		itemScrollerDelay = Math.clamp(itemScrollerDelay, 1, 50);

		wmScale = Math.clamp(wmScale, 0.5f, 2.0f);
		ahScale = Math.clamp(ahScale, 0.5f, 2.0f);
		thScale = Math.clamp(thScale, 0.5f, 2.0f);
		efScale = Math.clamp(efScale, 0.5f, 2.0f);
		kbScale = Math.clamp(kbScale, 0.5f, 2.0f);
		tcScale = Math.clamp(tcScale, 0.5f, 2.0f);
		cdScale = cdScale <= 0.0f ? 1.0f : Math.clamp(cdScale, 0.5f, 2.0f);
		tcPosition = Math.clamp(tcPosition, 0, 1);
		fastExpDelay = Math.clamp(fastExpDelay, 0, 3);
		spearSwapBack = spearSwapBack <= 0 ? 2 : Math.clamp(spearSwapBack, 1, 20);
		spearSwapDelay = Math.clamp(spearSwapDelay, 1, 5);
		targetEspMode = Math.clamp(targetEspMode, 0, 1);
		targetEspWidth = targetEspWidth <= 0.0f ? 1.0f : Math.clamp(targetEspWidth, 0.3f, 3.0f);
		autoSwapFirst = Math.clamp(autoSwapFirst, 0, AutoSwap.ITEMS.length - 1);
		autoSwapSecond = Math.clamp(autoSwapSecond, 0, AutoSwap.ITEMS.length - 1);

		if (autoSwapKey == null) {
			autoSwapKey = "";
		}

		lockedSlots &= 0x1FF;

		if (clickPearlKey == null) {
			clickPearlKey = "";
		}

		if (lockSlotKey == null) {
			lockSlotKey = "";
		}

		if (spearSwapKey == null) {
			spearSwapKey = "";
		}

		if (helperKeys == null) {
			helperKeys = new HashMap<>();
		}

		if (freeLookKey == null) {
			freeLookKey = "";
		}

		if (alts == null) {
			alts = new ArrayList<>();
		}

		if (activeAlt == null) {
			activeAlt = "";
		}

		if (elytraKey == null) {
			elytraKey = "";
		}

		if (fireworkKey == null) {
			fireworkKey = "";
		}

		if (wmNickText == null) {
			wmNickText = "";
		}

		if (moduleBinds == null) {
			moduleBinds = new HashMap<>();
		}

		// "HUD" is now the hub in Interface; the TriggerBot plate's bind moved to "TriggerBotHUD".
		if (moduleBinds.containsKey("HUD")) {
			moduleBinds.putIfAbsent("TriggerBotHUD", moduleBinds.get("HUD"));
			moduleBinds.remove("HUD");
		}

		if (friends == null) {
			friends = new ArrayList<>();
		}

		if (critMode == null) {
			critMode = CritMode.SMART;
		}
	}

	public boolean isFriend(UUID uuid) {
		String id = uuid.toString();

		for (Friend friend : friends) {
			if (id.equals(friend.uuid)) {
				return true;
			}
		}

		return false;
	}

	public boolean addFriend(UUID uuid, String name) {
		if (isFriend(uuid)) {
			return false;
		}

		friends.add(new Friend(uuid.toString(), name));
		return true;
	}

	public boolean removeFriend(UUID uuid) {
		String id = uuid.toString();
		return friends.removeIf(friend -> id.equals(friend.uuid));
	}
}
