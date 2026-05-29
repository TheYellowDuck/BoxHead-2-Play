package BoxHead2Play;

import java.util.List;

public class Gun {

	Entity  shooter;
	Sketch  sketch;
	String  name;

	float   damage;
	int     maxbull, currbull;
	int     reloadTime;
	int     reloadTimer;
	boolean isReloading;

	int     autoFireRate;
	int     autoFireCooldown;

	float   bulletSpeed;
	float   maxRange;
	int     pellets;
	float   spread;
	boolean penetrating;
	float   aoeRadius = 0;
	float   aoeDamage = 0;

	// Per-gun levelling (base values frozen at construction for flat-bonus scaling)
	int   level        = 1;
	float baseDamage;
	int   baseFireRate;
	int   baseBull;
	int   baseReloadTime;

	// Current aim direction — updated every frame toward nearest visible target.
	float xd = 0, yd = -1;

	// Bullet modifier flags — applied to every bullet this gun fires
	int     bulletBounces    = 0;     // ricochet wall bounces
	int     bulletChains     = 0;     // chain lightning arcs
	boolean explosiveRounds  = false; // small AoE splash on each hit
	float   explosiveAoeR    = 35f;
	int     bulletSlowFrames = 0;     // slow duration to inflict on hit
	int     bulletBurnFrames = 0;     // burn duration to inflict on hit
	float   bulletBurnDmg    = 0f;    // burn damage per frame

	int bulletR, bulletG, bulletB;

	public Gun(Entity shooter, Sketch sketch, String name,
	           float damage, int maxbull, int reloadTime, int autoFireRate,
	           float bulletSpeed, float maxRange,
	           int pellets, float spread, boolean penetrating,
	           int r, int g, int b) {
		this.shooter      = shooter; this.sketch = sketch; this.name = name;
		this.damage       = damage;
		this.maxbull      = maxbull; this.currbull = maxbull;
		this.reloadTime   = reloadTime;
		this.autoFireRate = autoFireRate;
		this.bulletSpeed  = bulletSpeed; this.maxRange = maxRange;
		this.pellets      = pellets; this.spread = spread;
		this.penetrating  = penetrating;
		this.bulletR = r; this.bulletG = g; this.bulletB = b;
		// Freeze original values for flat per-level bonuses
		this.baseDamage    = damage;
		this.baseFireRate  = autoFireRate;
		this.baseBull      = maxbull;
		this.baseReloadTime = reloadTime;
	}

	// ── Called each frame from Sketch ──────────────────────────────────────

	public void update(List<Entity> entities) {
		if (isReloading) {
			reloadTimer--;
			if (reloadTimer <= 0) { currbull = maxbull; isReloading = false; }
			return;
		}
		if (autoFireCooldown > 0) autoFireCooldown--;

		// Priority 1: nearest visible enemy.  Priority 2: crate fallback.
		Entity target = findNearest(entities);
		if (target == null) target = findNearestCrate(entities);

		if (target != null) {
			xd = target.x - shooter.x;
			yd = target.y - shooter.y;
			if (autoFireCooldown <= 0 && currbull > 0)
				fireBullets(xd, yd);
		}
	}

	// Manual fire toward world-space cursor (ignores LOS; bullet will hit wall naturally).
	public void manualFire(float worldAimX, float worldAimY) {
		if (isReloading || currbull <= 0) return;
		fireBullets(worldAimX - shooter.x, worldAimY - shooter.y);
	}

	public void startReload() {
		if (!isReloading && currbull < maxbull) { isReloading = true; reloadTimer = reloadTime; }
	}

	// ── Auto-aim helpers ───────────────────────────────────────────────────

	// Max auto-aim range: keep targets visibly on a 600×600 screen (half-width = 300 px).
	private static final float AUTO_AIM_RANGE = 260f;

	private Entity findNearest(List<Entity> entities) {
		Entity nearest   = null;
		float  nearestSq = Float.MAX_VALUE;
		float  rangeSq   = Math.min(AUTO_AIM_RANGE, maxRange);
		       rangeSq  *= rangeSq;

		for (Entity e : entities) {
			if (e.die == 1) continue;
			if (e.enemy == 0 || e.enemy == 2) continue;           // skip neutrals and pickups
			if ((e.enemy > 0) == (shooter.enemy > 0)) continue;   // skip same side
			float dx = e.x - shooter.x, dy = e.y - shooter.y;
			float dSq = dx * dx + dy * dy;
			if (dSq > rangeSq || dSq >= nearestSq) continue;
			if (!hasLOS(e.x, e.y)) continue;
			nearestSq = dSq;
			nearest   = e;
		}
		return nearest;
	}

	/** Fallback: target the nearest crate when no enemies are in range. */
	private Entity findNearestCrate(List<Entity> entities) {
		Entity nearest   = null;
		float  nearestSq = Float.MAX_VALUE;
		float  rangeSq   = Math.min(AUTO_AIM_RANGE, maxRange);
		       rangeSq  *= rangeSq;

		for (Entity e : entities) {
			if (e.die == 1 || e.enemy != 0) continue;          // only neutrals
			if (!(e instanceof Crate)) continue;                // only actual crates
			float dx = e.x - shooter.x, dy = e.y - shooter.y;
			float dSq = dx * dx + dy * dy;
			if (dSq > rangeSq || dSq >= nearestSq) continue;
			if (!hasLOS(e.x, e.y)) continue;
			nearestSq = dSq;
			nearest   = e;
		}
		return nearest;
	}

	/** Step along the line from shooter to (tx,ty); returns false if any tile is a wall. */
	private boolean hasLOS(float tx, float ty) {
		float dx  = tx - shooter.x, dy = ty - shooter.y;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1) return true;
		float nx = dx / len, ny = dy / len;
		float step = WorldMap.TS * 0.45f;
		for (float d = step; d < len - step; d += step)
			if (WorldMap.isWallAt(shooter.x + nx * d, shooter.y + ny * d)) return false;
		return true;
	}

	// ── Bullet creation ────────────────────────────────────────────────────

	private void fireBullets(float dx, float dy) {
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len == 0) return;
		float nx = dx / len, ny = dy / len;

		for (int i = 0; i < pellets; i++) {
			float angle;
			if (pellets == 1)
				angle = (spread > 0) ? sketch.random(-spread, spread) : 0f;
			else
				angle = spread * (i - (pellets - 1) / 2f);

			float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
			float bvx = (nx * cos - ny * sin) * bulletSpeed;
			float bvy = (nx * sin + ny * cos) * bulletSpeed;
			Bullet b = new Bullet(
				shooter.x, shooter.y, bvx, bvy,
				damage, shooter.enemy, maxRange,
				penetrating, bulletR, bulletG, bulletB);
			b.aoeRadius       = aoeRadius;
			b.aoeDamage       = aoeDamage;
			b.maxBounces  = bulletBounces;
			b.maxChains   = bulletChains;
			b.explosiveOnHit = explosiveRounds;
			b.explosiveAoeR  = explosiveAoeR;
			b.slowFrames  = bulletSlowFrames;
			b.burnFrames  = bulletBurnFrames;
			b.burnDmg     = bulletBurnDmg;
			sketch.bullets.add(b);
		}

		currbull--;
		autoFireCooldown = autoFireRate;
		if (currbull <= 0) startReload();
	}

	public float reloadProgress() {
		if (!isReloading) return 1f;
		return 1f - reloadTimer / (float) reloadTime;
	}

	public void display(Sketch s) { }  // aim line removed per request
}
