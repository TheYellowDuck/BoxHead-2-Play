package BoxHead2Play;

import java.util.ArrayList;
import java.util.List;

public class Entity {

	List<Gun> guns = new ArrayList<>();

	static final float SQRT_TWO_OVER_TWO = (float) (Math.sqrt(2) / 2);

	float die = 0;
	float x, y;
	int   maxhp;
	float currhp;
	int   xpValue = 0;
	float damage;
	float radius;
	float r, g, b;
	float speed;
	float xd, yd;
	float enemy;

	// ── Status & visual flags ──────────────────────────────────────────────
	boolean isElite       = false;
	int     hitFlashTimer = 0;   // frames of red flash when taking damage
	float   lastHp        = -1f; // sentinel; set on first update
	int     slowTimer     = 0;   // frames of 50 % speed reduction
	int     burnTimer     = 0;   // frames of burn DoT remaining
	float   burnDamage    = 0f;  // damage per frame while burning

	public Entity(float x, float y, int maxhp, float radius,
	              float r, float g, float b,
	              float speed, float damage, float enemy) {
		this.x = x; this.y = y;
		this.maxhp = maxhp; this.currhp = maxhp;
		this.radius = radius;
		this.r = r; this.g = g; this.b = b;
		this.speed = speed; this.damage = damage; this.enemy = enemy;
	}

	public void update(List<Entity> entities) {
		// ── Hit-flash detection (HP change since last frame) ───────────────
		if (lastHp < 0f) {
			lastHp = currhp;
		} else {
			if (currhp < lastHp && die == 0) hitFlashTimer = 7;
			lastHp = currhp;
		}
		if (hitFlashTimer > 0) hitFlashTimer--;

		// ── Burn DoT ───────────────────────────────────────────────────────
		if (burnTimer > 0) {
			burnTimer--;
			currhp -= burnDamage;
			if (currhp <= 0) die = 1;
		}

		// ── Slow modifier ─────────────────────────────────────────────────
		float effectiveSpeed = (slowTimer > 0) ? speed * 0.45f : speed;
		if (slowTimer > 0) slowTimer--;

		// ── Movement (diagonal-input normalisation) ────────────────────────
		float coef = (Math.abs(xd) + Math.abs(yd) > 1.9f) ? SQRT_TWO_OVER_TWO : 1f;
		x += xd * coef * effectiveSpeed;
		y += yd * coef * effectiveSpeed;

		// ── Entity-entity collision & contact damage ───────────────────────
		for (Entity e : entities) {
			if (this == e) continue;
			float dx = e.x - x, dy = e.y - y;
			float distSq = dx * dx + dy * dy;
			float minDist = e.radius + radius;
			if (distSq < minDist * minDist) {
				if ((e.enemy < 0 && enemy > 0) || (e.enemy > 0 && enemy < 0)) {
					e.currhp -= damage;
					if (e.currhp <= 0) e.die = 1;
					else {
						currhp -= e.damage;
						if (currhp <= 0) die = 1;
					}
				}
				if (distSq == 0) { x -= 0.5f; e.x += 0.5f; }
				else {
					float dist = (float) Math.sqrt(distSq);
					float f = (dist - e.radius + (minDist - dist) * radius / minDist) / dist;
					float midX = x + dx * f, midY = y + dy * f;
					x = midX + (x - midX) * (0.1f + radius) / (f * dist);
					y = midY + (y - midY) * (0.1f + radius) / (f * dist);
					e.x = midX + (e.x - midX) * (0.1f + e.radius) / ((1 - f) * dist);
					e.y = midY + (e.y - midY) * (0.1f + e.radius) / ((1 - f) * dist);
				}
			}
		}

		// ── Wall collision with infinite WorldMap ─────────────────────────
		int tx = (int) Math.floor(x / WorldMap.TS);
		int ty = (int) Math.floor(y / WorldMap.TS);
		for (int i = ty - 1; i <= ty + 1; i++) {
			for (int j = tx - 1; j <= tx + 1; j++) {
				if (!WorldMap.isWall(j, i)) continue;
				float wx0 = j * WorldMap.TS, wy0 = i * WorldMap.TS;
				float clX = Math.max(wx0, Math.min(wx0 + WorldMap.TS, x));
				float clY = Math.max(wy0, Math.min(wy0 + WorldMap.TS, y));
				float ddx = clX - x, ddy = clY - y;
				float dSq = ddx * ddx + ddy * ddy;
				if (dSq > 0 && dSq < radius * radius) {
					float d = (float) Math.sqrt(dSq);
					x = clX + (x - clX) * radius / d;
					y = clY + (y - clY) * radius / d;
				}
			}
		}
	}

	public void display(Sketch s) {
		float sz = radius;
		s.noStroke();
		s.fill(0, 0, 0, 55);
		s.rect(x - sz + 3, y - sz + 4, sz * 2, sz * 2, 4);
		s.stroke(Math.max(r - 70, 0), Math.max(g - 70, 0), Math.max(b - 70, 0));
		s.strokeWeight(2);
		s.fill(r, g, b);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		s.strokeWeight(1); s.noStroke();
		s.fill(Math.min(r + 45, 255), Math.min(g + 45, 255), Math.min(b + 45, 255), 140);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.55f, 2);
		for (Gun gun : guns) gun.display(s);
	}

	public void displayhp(Sketch s) {
		if (currhp <= 0) return;
		float barW = radius * 2.8f, barH = 4;
		float barX = x - barW / 2, barY = y - radius - 9;
		float hpFrac = Math.max(0, Math.min(1, currhp / maxhp));
		s.noStroke();
		s.fill(20, 20, 20, 200);
		s.rect(barX - 1, barY - 1, barW + 2, barH + 2, 2);
		if (hpFrac > 0.6f)      s.fill(55, 200, 75);
		else if (hpFrac > 0.3f) s.fill(220, 185, 35);
		else                    s.fill(215, 50, 35);
		s.rect(barX, barY, barW * hpFrac, barH, 1);
	}
}
