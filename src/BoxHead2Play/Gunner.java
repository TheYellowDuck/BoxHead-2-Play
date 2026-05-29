package BoxHead2Play;

import java.util.List;

// Rapid-fire ranged enemy — stays at medium range and sprays bullets.
// Gun is added by Sketch at spawn time.
public class Gunner extends Entity {

	static final float IDEAL_DIST = 160f;
	static final float MARGIN     =  35f;

	public Gunner(float x, float y, int wave) {
		super(x, y,
			(int)(400 * (1 + (wave - 1) * 0.18f)),
			11f, 200, 120, 40,
			1.4f + (wave - 1) * 0.03f,
			3f, -5);
		xpValue = 40;
	}

	@Override
	public void update(List<Entity> entities) {
		Entity player = entities.get(0);
		float dx = player.x - x, dy = player.y - y;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len > 0) {
			float diff = len - IDEAL_DIST;
			if (diff > MARGIN) {
				if (WorldMap.hasLOS(x, y, player.x, player.y)) {
					xd = dx / len; yd = dy / len;
				} else {
					float[] pf = Pathfinder.getSteerDirection(x, y);
					if (pf != null && (pf[0] != 0 || pf[1] != 0)) { xd = pf[0]; yd = pf[1]; }
					else { xd = dx / len; yd = dy / len; }
				}
			} else if (diff < -MARGIN) {
				xd = -dx / len; yd = -dy / len;
			} else {
				xd *= 0.85f; yd *= 0.85f;
			}
		}
		super.update(entities);
	}

	@Override
	public void display(Sketch s) {
		float sz = radius;

		s.noStroke(); s.fill(0, 0, 0, 55);
		s.rect(x - sz + 3, y - sz + 4, sz * 2, sz * 2, 4);

		// Burnt-orange body
		s.stroke(130, 65, 15); s.strokeWeight(2);
		s.fill(200, 115, 35);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		s.strokeWeight(1); s.noStroke();

		s.fill(240, 165, 80, 140);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.5f, 2);

		// Orange-yellow eyes
		s.fill(255, 200, 30);
		s.rect(x - 7, y - 3, 5, 5, 1);
		s.rect(x + 2, y - 3, 5, 5, 1);
		s.fill(180, 90, 10);
		s.rect(x - 6, y - 2, 3, 3);
		s.rect(x + 3, y - 2, 3, 3);

		// Snarl
		s.fill(120, 50, 10);
		s.rect(x - 5, y + 4, 10, 3, 1);

		// Aim indicator
		if (!guns.isEmpty()) {
			Gun g = guns.get(0);
			float gl = (float) Math.sqrt(g.xd * g.xd + g.yd * g.yd);
			if (gl > 0) {
				float nx = g.xd / gl, ny = g.yd / gl;
				boolean ready = !g.isReloading && g.autoFireCooldown < g.autoFireRate / 2;
				s.stroke(255, 200, 50, ready ? 210 : 70); s.strokeWeight(1.5f);
				s.line(x, y, x + nx * 18, y + ny * 18);
				s.noStroke(); s.fill(255, 200, 50, ready ? 220 : 80);
				s.ellipse(x + nx * 21, y + ny * 21, 6, 6);
			}
		}
		s.noStroke(); s.strokeWeight(1);
	}
}
