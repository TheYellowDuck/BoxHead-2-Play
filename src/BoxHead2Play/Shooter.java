package BoxHead2Play;

import java.util.List;

// Ranged enemy — keeps distance and fires projectiles at the player.
// Gun is added by Sketch at spawn time so it can reference sketch.bullets.
public class Shooter extends Entity {

	static final float IDEAL_DIST = 230f;
	static final float MARGIN     =  40f;

	public Shooter(float x, float y, int wave) {
		super(x, y,
			(int)(550 * (1 + (wave - 1) * 0.18f)),
			11.5f, 55, 110, 185,
			1.1f + (wave - 1) * 0.03f,
			3f, -4);
		xpValue = 50;
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

		// Shadow
		s.noStroke(); s.fill(0, 0, 0, 55);
		s.rect(x - sz + 3, y - sz + 4, sz * 2, sz * 2, 5);

		// Body — dark teal
		s.stroke(30, 70, 110); s.strokeWeight(2);
		s.fill(55, 110, 185);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 5);
		s.strokeWeight(1); s.noStroke();

		// Top highlight
		s.fill(90, 155, 225, 140);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.5f, 3);

		// Glowing cyan eyes
		s.fill(80, 240, 235);
		s.rect(x - 7, y - 3, 5, 5, 1);
		s.rect(x + 2, y - 3, 5, 5, 1);
		s.fill(200, 255, 255);
		s.rect(x - 6, y - 2, 3, 3);
		s.rect(x + 3, y - 2, 3, 3);

		// Frown
		s.fill(30, 65, 110);
		s.rect(x - 4, y + 4, 8, 2, 1);

		// Aim indicator — small dot on a short line toward gun target
		if (!guns.isEmpty()) {
			Gun g = guns.get(0);
			float gl = (float) Math.sqrt(g.xd * g.xd + g.yd * g.yd);
			if (gl > 0) {
				float nx = g.xd / gl, ny = g.yd / gl;
				boolean ready = !g.isReloading && g.autoFireCooldown < g.autoFireRate / 2;
				s.stroke(80, 230, 255, ready ? 220 : 80); s.strokeWeight(1.5f);
				s.line(x, y, x + nx * 22, y + ny * 22);
				s.noStroke();
				s.fill(80, 230, 255, ready ? 230 : 90);
				s.ellipse(x + nx * 25, y + ny * 25, 7, 7);
			}
		}
		s.noStroke(); s.strokeWeight(1);
	}
}
