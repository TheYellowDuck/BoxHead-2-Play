package BoxHead2Play;

public class Upgrade {

	public enum Type {
		// Core stat upgrades
		DAMAGE_UP, FIRE_RATE_UP, RELOAD_SPEED, SPEED_UP, MAX_HP_UP, HEAL, MULTISHOT,
		// Weapon unlocks / level-ups
		GET_SHOTGUN, GET_SNIPER, GET_MINIGUN, GET_SMG, GET_ROCKET,
		UPGRADE_GUN,
		// Bullet modifiers
		RICOCHET, RICOCHET_UP,
		CHAIN, CHAIN_UP,
		EXPLOSIVE_ROUNDS,
		SLOW_ROUNDS,
		INCENDIARY,
		// Passive combat traits
		VAMPIRISM,
		THORNS,
		// Turret
		TURRET, UPGRADE_TURRET,
		// Defensive unlocks
		SHIELD, DRONE, KNIVES,
		AURA, PULSE, MAGNET,
		// Defensive level-ups (primary)
		UPGRADE_SHIELD, UPGRADE_DRONE, UPGRADE_KNIVES,
		UPGRADE_AURA, UPGRADE_PULSE, UPGRADE_MAGNET,
		// Defensive level-ups (secondary)
		UPGRADE_SHIELD_RECHARGE,
		UPGRADE_DRONE_POWER,
		UPGRADE_KNIVES_ORBIT
	}

	public Type   type;
	public String name, desc;
	public int    cr, cg, cb;
	public int    targetIndex = -1; // UPGRADE_GUN: index in player.guns

	public Upgrade(Type type, String name, String desc, int r, int g, int b) {
		this.type = type; this.name = name; this.desc = desc;
		this.cr = r; this.cg = g; this.cb = b;
	}

	// ── Core stat upgrades — always in pool ────────────────────────────────
	public static final Upgrade[] STAT_UPGRADES = {
		new Upgrade(Type.DAMAGE_UP,    "Power Up",      "All guns +20% damage",          210,  80,  35),
		new Upgrade(Type.FIRE_RATE_UP, "Rapid Fire",    "All guns +25% attack speed",     60, 155, 235),
		new Upgrade(Type.RELOAD_SPEED, "Quick Loader",  "All guns -25% reload time",     180, 180,  55),
		new Upgrade(Type.MULTISHOT,    "Extra Barrel",  "All guns +50% pellets",         175, 110, 225),
		new Upgrade(Type.SPEED_UP,     "Swift Boots",   "+20% move speed",                60, 195,  90),
		new Upgrade(Type.MAX_HP_UP,    "Iron Heart",    "+300 max HP",                   200,  55,  55),
		new Upgrade(Type.HEAL,         "First Aid",     "Restore 500 HP",                 50, 185,  80),
	};

	// ── Bullet modifier upgrades ───────────────────────────────────────────
	public static final Upgrade RICOCHET_UNLOCK =
		new Upgrade(Type.RICOCHET,       "Ricochet",      "Bullets bounce off walls once",       255, 195,  60);
	public static final Upgrade RICOCHET_UP_OPT =
		new Upgrade(Type.RICOCHET_UP,    "Ricochet +",    "+1 more wall bounce (max 3)",         255, 215,  90);
	public static final Upgrade CHAIN_UNLOCK =
		new Upgrade(Type.CHAIN,          "Chain Strike",  "Bullets arc to 1 nearby enemy",        80, 200, 255);
	public static final Upgrade CHAIN_UP_OPT =
		new Upgrade(Type.CHAIN_UP,       "Chain Strike+", "Arc hits 1 more enemy",              120, 220, 255);
	public static final Upgrade EXPLOSIVE_ROUNDS_OPT =
		new Upgrade(Type.EXPLOSIVE_ROUNDS,"Frag Rounds",  "All guns: AoE splash on every hit",  255, 120,  50);
	public static final Upgrade SLOW_ROUNDS_OPT =
		new Upgrade(Type.SLOW_ROUNDS,    "Cryo Rounds",   "All guns: slow enemies for 2 s",      80, 200, 255);
	public static final Upgrade INCENDIARY_OPT =
		new Upgrade(Type.INCENDIARY,     "Incendiary",    "All guns: ignite enemies (DoT)",     255, 130,  30);

	// ── Passive trait upgrades ─────────────────────────────────────────────
	public static final Upgrade VAMPIRISM_OPT =
		new Upgrade(Type.VAMPIRISM,  "Vampirism",  "Heal 8 % of bullet damage dealt",   180,  40, 100);
	public static final Upgrade THORNS_OPT =
		new Upgrade(Type.THORNS,     "Thorns",     "Reflect 40 % of contact damage",    160, 220,  80);

	// ── Turret upgrades ────────────────────────────────────────────────────
	public static final Upgrade TURRET_UNLOCK =
		new Upgrade(Type.TURRET,         "Auto Turret",   "Place a turret at your position",    200, 190,  80);
	public static final Upgrade TURRET_UP_OPT =
		new Upgrade(Type.UPGRADE_TURRET, "Turret Mk.II",  "+1 turret slot · +30% turret dmg",  220, 210, 100);

	// ── Gun unlock options ──────────────────────────────────────────────────
	public static final Upgrade GET_SHOTGUN_OPT =
		new Upgrade(Type.GET_SHOTGUN, "Shotgun",      "5-pellet close-range blast",     255, 155,  55);
	public static final Upgrade GET_SNIPER_OPT  =
		new Upgrade(Type.GET_SNIPER,  "Sniper Rifle", "Long range, penetrates all",     100, 220, 255);
	public static final Upgrade GET_MINIGUN_OPT =
		new Upgrade(Type.GET_MINIGUN, "Minigun",      "80-round rapid-fire drum",       255,  90,  90);
	public static final Upgrade GET_SMG_OPT     =
		new Upgrade(Type.GET_SMG,     "SMG",          "Fast 30-round spray gun",        150, 255, 150);
	public static final Upgrade GET_ROCKET_OPT  =
		new Upgrade(Type.GET_ROCKET,  "Rocket",       "Explosive area-of-effect",       255, 120,  30);

	// ── Defensive unlock options ────────────────────────────────────────────
	public static final Upgrade SHIELD_UNLOCK =
		new Upgrade(Type.SHIELD, "Force Field",  "300 HP shield, auto-recharges",       55, 130, 255);
	public static final Upgrade DRONE_UNLOCK  =
		new Upgrade(Type.DRONE,  "Combat Drone", "Orbiting drone that auto-fires",      40, 190, 185);
	public static final Upgrade KNIVES_UNLOCK =
		new Upgrade(Type.KNIVES, "Blade Spin",   "2 rotating blades deal contact dmg", 230, 215,  60);
	public static final Upgrade AURA_UNLOCK   =
		new Upgrade(Type.AURA,   "Damage Aura",  "Continuous ring of damage around you", 255, 165, 30);
	public static final Upgrade PULSE_UNLOCK  =
		new Upgrade(Type.PULSE,  "Shockwave",    "Periodic blast wave damages all nearby", 200, 100, 255);
	public static final Upgrade MAGNET_UNLOCK =
		new Upgrade(Type.MAGNET, "Magnet",       "Auto-pulls health packs & gun drops",  80, 230, 215);
}
