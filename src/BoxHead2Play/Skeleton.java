package BoxHead2Play;

import java.util.List;

// Fast, fragile enemy — appears from wave 3.
public class Skeleton extends Entity {

	public Skeleton(float x, float y, int wave) {
		super(x, y,
			(int)(300 * (1 + (wave - 1) * 0.18f)),
			10.5f, 210, 210, 195,
			2.3f + (wave - 1) * 0.04f,
			4, -1);
		xpValue = 15;
	}

	@Override
	public void update(List<Entity> entities) {
		Entity player = entities.get(0);
		float dx = player.x - x, dy = player.y - y;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len > 0) {
			if (WorldMap.hasLOS(x, y, player.x, player.y)) {
				xd = dx / len; yd = dy / len;
			} else {
				float[] pf = Pathfinder.getSteerDirection(x, y);
				if (pf != null && (pf[0] != 0 || pf[1] != 0)) { xd = pf[0]; yd = pf[1]; }
				else { xd = dx / len; yd = dy / len; }
			}
		}
		super.update(entities);
	}

	@Override
	public void display(Sketch s) {
		float sz = radius;

		// Shadow
		s.noStroke(); s.fill(0, 0, 0, 50);
		s.rect(x - sz + 2, y - sz + 3, sz * 2, sz * 2, 3);

		// Skull body — bone white
		s.stroke(140, 135, 125); s.strokeWeight(1.5f);
		s.fill(210, 210, 195);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		s.strokeWeight(1); s.noStroke();

		// Top crack lines
		s.fill(160, 155, 145, 180);
		s.rect(x - 2, y - sz + 1, 1, sz * 0.7f);
		s.rect(x + 3, y - sz + 2, 1, sz * 0.5f);

		// Dark hollow eyes
		s.fill(25, 20, 20);
		s.ellipse(x - 4, y - 1, 6, 6);
		s.ellipse(x + 4, y - 1, 6, 6);

		// Nose cavity
		s.fill(50, 45, 40);
		s.triangle(x - 1, y + 4, x + 1, y + 4, x, y + 6);

		// Jagged mouth
		s.fill(30, 25, 20);
		s.rect(x - 5, y + 6, 10, 3, 1);
		s.fill(210, 210, 195);
		s.rect(x - 4, y + 6, 2, 2);
		s.rect(x - 1, y + 6, 2, 2);
		s.rect(x + 2, y + 6, 2, 2);
	}
}
