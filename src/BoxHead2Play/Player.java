package BoxHead2Play;

import java.util.ArrayList;
import java.util.List;

public class Player extends Entity {

	int     level         = 1;
	float   xp            = 0;
	float   xpToNextLevel = 150;
	boolean leveledUp     = false;
	int     currgun       = 0;

	// ── Shield ────────────────────────────────────────────────────────────
	float shieldHP      = 0;
	float shieldMax     = 0;
	int   shieldCooldown = 0;
	int   shieldHitDelay = 240; // frames before recharge; reducible
	int   shieldLevel   = 0;

	// ── Knives ────────────────────────────────────────────────────────────
	int   knifeCount       = 0;
	float knifeAngle       = 0;
	float knifeDamage      = 4f;
	float knifeOrbitRadius = 58f; // upgradeable
	float knifeRotSpeed    = 0.05f; // upgradeable
	int   knifeLevel       = 0;

	// ── Drones ────────────────────────────────────────────────────────────
	List<Drone> drones = new ArrayList<>();
	int   droneLevel   = 0;

	// ── Aura (continuous damage ring) ─────────────────────────────────────
	int   auraLevel  = 0;
	float auraDamage = 0f;
	float auraRadius = 0f;

	// ── Pulse (periodic shockwave) ────────────────────────────────────────
	int   pulseLevel       = 0;
	float pulseDamage      = 0f;
	float pulseMaxRadius   = 0f;
	int   pulseMaxCooldown = 0;
	int   pulseCooldown    = 0;
	float pulseAnimRadius  = 0f; // expanding visual
	int   pulseAnimTimer   = 0;

	// ── Magnet (attract pickups) ──────────────────────────────────────────
	int   magnetLevel  = 0;
	float magnetRadius = 0f;

	// ── Passive combat traits ─────────────────────────────────────────────
	float vampirism   = 0f;  // fraction of bullet damage healed per hit
	float thorns      = 0f;  // fraction of attacker's damage reflected back
	int   turretLevel = 0;   // number of turrets allowed (0 = none)

	public Player(float x, float y) {
		super(x, y, 1500, 12.5f, 230, 185, 130, 2.5f, 5, 1);
	}

	public void gainXP(float amount) {
		xp += amount;
		if (xp >= xpToNextLevel) {
			xp -= xpToNextLevel;
			level++;
			xpToNextLevel = level * 150;
			leveledUp = true;
		}
	}

	@Override
	public void display(Sketch s) {
		float sz = radius;

		// ── Magnet range ring (faint teal, outermost) ─────────────────────
		if (magnetRadius > 0) {
			s.noFill();
			s.stroke(80, 230, 220, 35 + 20f * (float) Math.sin(s.frameCount * 0.05f));
			s.strokeWeight(1.5f);
			s.ellipse(x, y, magnetRadius * 2, magnetRadius * 2);
		}

		// ── Aura ring (orange, breathes) ───────────────────────────────────
		if (auraRadius > 0) {
			float breathe = 0.88f + 0.12f * (float) Math.sin(s.frameCount * 0.08f);
			s.noFill();
			s.stroke(255, 165, 30, 50 + 30f * (float) Math.sin(s.frameCount * 0.08f));
			s.strokeWeight(8f); // soft glow
			s.ellipse(x, y, auraRadius * 2 * breathe, auraRadius * 2 * breathe);
			s.stroke(255, 200, 80, 130);
			s.strokeWeight(2f);
			s.ellipse(x, y, auraRadius * 2 * breathe, auraRadius * 2 * breathe);
		}

		// ── Pulse expanding ring ───────────────────────────────────────────
		if (pulseAnimTimer > 0) {
			float t = pulseAnimTimer / 25f;
			s.noFill();
			s.stroke(200, 100, 255, t * 240f);
			s.strokeWeight(4f * t + 1f);
			s.ellipse(x, y, pulseAnimRadius * 2, pulseAnimRadius * 2);
			s.stroke(255, 180, 255, t * 100f);
			s.strokeWeight(1.5f);
			s.ellipse(x, y, pulseAnimRadius * 1.6f, pulseAnimRadius * 1.6f);
		}

		// ── Shield ring (blue, behind player body) ─────────────────────────
		if (shieldMax > 0 && shieldHP > 0) {
			float f = shieldHP / shieldMax;
			s.noFill();
			s.stroke(80, 150, 255, f * 210f);
			s.strokeWeight(4f + f * 2.5f);
			s.ellipse(x, y, (sz + 18) * 2, (sz + 18) * 2);
			s.stroke(140, 200, 255, f * 95f);
			s.strokeWeight(1.5f);
			s.ellipse(x, y, (sz + 11) * 2, (sz + 11) * 2);
		}

		// ── Rotating knives ────────────────────────────────────────────────
		if (knifeCount > 0) {
			for (int k = 0; k < knifeCount; k++) {
				float ka = knifeAngle + k * ((float) Math.PI * 2 / knifeCount);
				float kx = x + (float) Math.cos(ka) * knifeOrbitRadius;
				float ky = y + (float) Math.sin(ka) * knifeOrbitRadius;
				s.pushMatrix();
				s.translate(kx, ky);
				s.rotate(ka + s.frameCount * 0.12f);
				s.noStroke();
				s.fill(255, 240, 80, 120);
				s.rect(-8, -3, 16, 6, 2);
				s.fill(220, 215, 100);
				s.rect(-8, -2, 16, 4, 1);
				s.fill(255, 255, 200, 200);
				s.rect(-6, -1, 5, 2, 1);
				s.popMatrix();
			}
		}

		// ── Player body ────────────────────────────────────────────────────
		s.noStroke(); s.fill(0, 0, 0, 55);
		s.rect(x - sz + 3, y - sz + 4, sz * 2, sz * 2, 4);
		s.stroke(160, 110, 65); s.strokeWeight(2);
		s.fill(230, 185, 130);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		s.strokeWeight(1); s.noStroke();
		s.fill(70, 45, 20);
		s.rect(x - sz, y - sz, sz * 2, sz * 0.55f, 3);
		s.fill(240, 240, 240);
		s.rect(x - 7, y - 3, 5, 5, 1);
		s.rect(x + 2, y - 3, 5, 5, 1);
		s.fill(30, 25, 20);
		s.rect(x - 6, y - 2, 3, 3);
		s.rect(x + 3, y - 2, 3, 3);
		s.fill(160, 95, 55);
		s.rect(x - 4, y + 4, 8, 2, 1);
		if (currgun < guns.size()) guns.get(currgun).display(s);
		s.noStroke(); s.strokeWeight(1);
	}
}
