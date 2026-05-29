package BoxHead2Play;

import java.util.List;

/**
 * Stationary auto-firing turret placed at the player's position.
 * Lives in the main entities list (enemy = 1, player side) so enemy bullets
 * can destroy it and it acts as a physical obstacle.
 * Its Gun is updated by Sketch's existing gun-update loop.
 */
public class Turret extends Entity {

	public Turret(float x, float y, Sketch sketch) {
		super(x, y, 2800, 11f, 150, 140, 90, 0f, 0f, 1);
		guns.add(new Gun(this, sketch, "Turret",
		         130, 9999, 1, 22, 11f, 350f, 1, 0f, false, 220, 210, 100));
	}

	@Override
	public void update(List<Entity> entities) {
		// No movement — just run wall-collision to stay physical, skip entity push
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
		// Hit-flash tracking
		if (lastHp < 0f) lastHp = currhp;
		else if (currhp < lastHp && die == 0) hitFlashTimer = 7;
		lastHp = currhp;
		if (hitFlashTimer > 0) hitFlashTimer--;
	}

	@Override
	public void display(Sketch s) {
		// Base plate shadow
		s.noStroke(); s.fill(0, 0, 0, 70);
		s.ellipse(x + 3, y + 4, 28, 28);

		// Base plate
		s.stroke(90, 80, 60); s.strokeWeight(2f);
		s.fill(110, 100, 75);
		s.ellipse(x, y, 28, 28);
		s.noStroke();

		// Barrel (toward gun aim direction)
		Gun g = guns.isEmpty() ? null : guns.get(0);
		float bx = 0, by = -1;
		if (g != null) {
			float gl = (float) Math.sqrt(g.xd * g.xd + g.yd * g.yd);
			if (gl > 0) { bx = g.xd / gl; by = g.yd / gl; }
		}
		s.stroke(70, 65, 50); s.strokeWeight(6f);
		s.line(x, y, x + bx * 16, y + by * 16);
		s.stroke(140, 130, 100); s.strokeWeight(4f);
		s.line(x, y, x + bx * 14, y + by * 14);
		s.noStroke();

		// Turret head
		s.fill(160, 148, 110);
		s.ellipse(x, y, 18, 18);
		s.fill(200, 190, 140, 160);
		s.ellipse(x - 2, y - 2, 10, 10);

		// HP bar
		float frac = Math.max(0, Math.min(1, currhp / (float) maxhp));
		s.fill(20, 20, 20, 180); s.rect(x - 12, y - 20, 24, 5, 2);
		s.fill(frac > 0.5f ? s.color(55, 200, 80) : s.color(215, 50, 35));
		if (frac > 0) s.rect(x - 12, y - 20, 24 * frac, 5, 2);
	}
}
