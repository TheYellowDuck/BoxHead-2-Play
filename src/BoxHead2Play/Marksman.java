package BoxHead2Play;

import java.util.List;

// Long-range sniper enemy — slow, fires powerful penetrating shots from far away.
// Gun is added by Sketch at spawn time.
public class Marksman extends Entity {

	static final float IDEAL_DIST = 340f;
	static final float MARGIN     =  50f;

	public Marksman(float x, float y, int wave) {
		super(x, y,
			(int)(480 * (1 + (wave - 1) * 0.20f)),
			10.5f, 140, 140, 165,
			0.85f + (wave - 1) * 0.025f,
			2f, -6);
		xpValue = 65;
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
				xd *= 0.80f; yd *= 0.80f;
			}
		}
		super.update(entities);
	}

	@Override
	public void display(Sketch s) {
		float sz = radius;

		s.noStroke(); s.fill(0, 0, 0, 55);
		s.rect(x - sz + 3, y - sz + 4, sz * 2, sz * 2, 4);

		// Steel-grey body
		s.stroke(80, 80, 95); s.strokeWeight(2);
		s.fill(140, 140, 165);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		s.strokeWeight(1); s.noStroke();

		s.fill(190, 190, 210, 140);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.5f, 2);

		// Narrow, focused eyes — glowing red targeting reticle look
		s.fill(230, 30, 30);
		s.rect(x - 7, y - 2, 14, 3, 1);   // single narrow band
		s.fill(255, 80, 80);
		s.rect(x - 6, y - 1, 4, 1);
		s.rect(x + 2, y - 1, 4, 1);

		// Stoic mouth
		s.fill(90, 90, 110);
		s.rect(x - 4, y + 4, 8, 2);

		// Aim indicator — longer line for the sniper feel
		if (!guns.isEmpty()) {
			Gun g = guns.get(0);
			float gl = (float) Math.sqrt(g.xd * g.xd + g.yd * g.yd);
			if (gl > 0) {
				float nx = g.xd / gl, ny = g.yd / gl;
				boolean ready = !g.isReloading && g.autoFireCooldown < g.autoFireRate / 2;
				// Long dim laser sight
				s.stroke(255, 30, 30, ready ? 100 : 30); s.strokeWeight(1f);
				s.line(x, y, x + nx * 60, y + ny * 60);
				// Bright tip dot
				s.noStroke(); s.fill(255, 60, 60, ready ? 230 : 80);
				s.ellipse(x + nx * 28, y + ny * 28, 5, 5);
			}
		}
		s.noStroke(); s.strokeWeight(1);
	}
}
