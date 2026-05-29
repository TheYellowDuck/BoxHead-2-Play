package BoxHead2Play;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Bullet {

	float x, y, prevX, prevY;
	float vx, vy;
	float damage;
	float ownerSide;
	float maxRange;
	float traveled;
	boolean penetrating;
	boolean dead;
	boolean didHit;

	// AoE on death (Rocket Launcher)
	float aoeRadius = 0;
	float aoeDamage = 0;

	// Ricochet — wall bouncing
	int maxBounces  = 0;
	int bounceCount = 0;

	// Chain lightning — arcs to nearby enemies on hit
	int             maxChains  = 0;
	int             chainCount = 0;
	HashSet<Entity> chainHit   = new HashSet<>();
	static final float CHAIN_RANGE = 130f;

	// Explosive rounds — small AoE splash on every entity hit
	boolean explosiveOnHit = false;
	float   explosiveAoeR  = 35f;

	// Status effects to apply on hit
	int   slowFrames  = 0;    // slow duration to inflict
	int   burnFrames  = 0;    // burn duration to inflict
	float burnDmg     = 0f;   // burn damage per frame

	// Vampirism accumulator — harvested by Sketch for player heal
	float pendingHeal = 0f;

	// Side-effects queued for Sketch to process after update()
	List<float[]> pendingArcs       = new ArrayList<>(); // {x1,y1,x2,y2}  → lightning arcs
	List<float[]> pendingExplosions = new ArrayList<>(); // {x,y,r,dmg}    → small explosions
	List<float[]> pendingFloaters   = new ArrayList<>(); // {x,y,dmg}      → damage numbers

	int r, g, b;

	public Bullet(float x, float y, float vx, float vy,
	              float damage, float ownerSide, float maxRange,
	              boolean penetrating, int r, int g, int b) {
		this.x = x; this.y = y; this.prevX = x; this.prevY = y;
		this.vx = vx; this.vy = vy;
		this.damage = damage; this.ownerSide = ownerSide;
		this.maxRange = maxRange; this.penetrating = penetrating;
		this.r = r; this.g = g; this.b = b;
	}

	public void update(List<Entity> entities) {
		if (dead) return;
		prevX = x; prevY = y;
		x += vx; y += vy;
		traveled += (float) Math.sqrt(vx * vx + vy * vy);

		if (traveled >= maxRange) { dead = true; return; }

		if (WorldMap.isWallAt(x, y)) {
			if (bounceCount < maxBounces) {
				bounceCount++;
				// Determine which axis caused the wall entry
				boolean xHit = WorldMap.isWallAt(x, prevY);
				boolean yHit = WorldMap.isWallAt(prevX, y);
				if (xHit) vx = -vx;
				if (yHit) vy = -vy;
				if (!xHit && !yHit) { vx = -vx; vy = -vy; } // corner
				x = prevX; y = prevY;
				return;
			}
			dead = true; didHit = true; return;
		}

		for (Entity e : entities) {
			if (e.die == 1) continue;
			if (e.enemy == 2) continue;                  // never hit pickups
			if (ownerSide > 0 && e.enemy > 0) continue; // friendly fire
			if (ownerSide < 0 && e.enemy < 0) continue;
			if (segCircle(prevX, prevY, x, y, e.x, e.y, e.radius)) {
				e.currhp -= damage;
				if (e.currhp <= 0) e.die = 1;
				didHit = true;
				pendingFloaters.add(new float[]{ e.x, e.y - e.radius - 4f, damage });

				// Status effects & vampirism (player bullets vs enemies only)
				if (ownerSide > 0 && e.enemy < 0) {
					if (slowFrames > 0) e.slowTimer = Math.max(e.slowTimer, slowFrames);
					if (burnFrames > 0) {
						e.burnTimer  = Math.max(e.burnTimer, burnFrames);
						e.burnDamage = Math.max(e.burnDamage, burnDmg);
					}
					pendingHeal += damage; // vampirism: collected by Sketch
				}

				// Chain lightning — only from actual enemies/player, not neutral
				if (e.enemy != 0 && chainCount < maxChains) {
					chainHit.add(e);
					Entity from = e;
					while (chainCount < maxChains) {
						Entity to = findChainTarget(entities, from);
						if (to == null) break;
						float decayed = damage * (float) Math.pow(0.65, chainCount + 1);
						to.currhp -= decayed;
						if (to.currhp <= 0) to.die = 1;
						chainHit.add(to);
						pendingArcs.add(new float[]{ from.x, from.y, to.x, to.y, r, g, b });
						pendingFloaters.add(new float[]{ to.x, to.y - to.radius - 4f, decayed });
						from = to;
						chainCount++;
					}
				} else {
					chainHit.add(e);
				}

				// Explosive rounds — queue small splash AoE
				if (explosiveOnHit) {
					pendingExplosions.add(new float[]{ e.x, e.y, explosiveAoeR, damage * 0.4f });
				}

				if (!penetrating) { dead = true; return; }
			}
		}
	}

	private Entity findChainTarget(List<Entity> entities, Entity from) {
		Entity best    = null;
		float  bestSq  = CHAIN_RANGE * CHAIN_RANGE;
		for (Entity t : entities) {
			if (t.die == 1 || t.enemy == 0 || chainHit.contains(t)) continue;
			if (ownerSide > 0 && t.enemy > 0) continue;
			if (ownerSide < 0 && t.enemy < 0) continue;
			float dx = t.x - from.x, dy = t.y - from.y;
			float dSq = dx * dx + dy * dy;
			if (dSq < bestSq) { bestSq = dSq; best = t; }
		}
		return best;
	}

	private boolean segCircle(float px, float py, float qx, float qy,
	                           float cx, float cy, float r) {
		float dx = qx - px, dy = qy - py;
		float lenSq = dx * dx + dy * dy;
		if (lenSq == 0) return false;
		float t  = Math.max(0f, Math.min(1f, ((cx - px) * dx + (cy - py) * dy) / lenSq));
		float nx = px + t * dx - cx, ny = py + t * dy - cy;
		return nx * nx + ny * ny < r * r;
	}

	public void display(Sketch s) {
		// Bounced bullets glow brighter
		int dr = bounceCount > 0 ? Math.min(255, r + 80) : r;
		int dg = bounceCount > 0 ? Math.min(255, g + 50) : g;
		int db = b;
		s.stroke(dr, dg, db, 210);
		s.strokeWeight(bounceCount > 0 ? 3.2f : 2.5f);
		s.line(prevX, prevY, x, y);
		s.noStroke();
		s.fill(255, 255, 215, 235);
		s.ellipse(x, y, 4f, 4f);
	}
}
