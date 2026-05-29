package BoxHead2Play;

import java.util.List;

// Slow, massive tank — appears from wave 6.
public class Brute extends Entity {

	public Brute(float x, float y, int wave) {
		super(x, y,
			(int)(4500 * (1 + (wave - 1) * 0.22f)),
			19f, 80, 60, 100,
			0.9f + (wave - 1) * 0.03f,
			22f, -3);
		xpValue = 100;
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
				float[] pf = Pathfinder.getLargeSteerDirection(x, y);
				if (pf != null && (pf[0] != 0 || pf[1] != 0)) { xd = pf[0]; yd = pf[1]; }
				else { xd = dx / len; yd = dy / len; }
			}
		}
		super.update(entities);
	}

	@Override
	public void display(Sketch s) {
		float sz = radius;

		// Heavy shadow
		s.noStroke(); s.fill(0, 0, 0, 80);
		s.rect(x - sz + 5, y - sz + 6, sz * 2, sz * 2, 5);

		// Armour plates — dark purple-grey
		s.stroke(45, 35, 60); s.strokeWeight(2.5f);
		s.fill(80, 60, 100);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 5);
		s.strokeWeight(1); s.noStroke();

		// Plate rivets / highlight
		s.fill(115, 95, 140, 160);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.4f, 3);

		// Glowing purple eyes (angry)
		s.fill(190, 50, 230);
		s.rect(x - 9, y - 4, 7, 7, 2);
		s.rect(x + 2, y - 4, 7, 7, 2);

		// Pupil
		s.fill(255, 200, 255);
		s.rect(x - 7, y - 2, 3, 4);
		s.rect(x + 4, y - 2, 3, 4);

		// Tusks / teeth
		s.fill(200, 190, 165);
		s.rect(x - 8, y + 5, 16, 4, 1);
		s.fill(220, 215, 200);
		s.triangle(x - 6, y + 5, x - 4, y + 5, x - 5, y + 11);
		s.triangle(x - 1, y + 5, x + 1, y + 5, x,     y + 11);
		s.triangle(x + 4, y + 5, x + 6, y + 5, x + 5, y + 11);
	}
}
