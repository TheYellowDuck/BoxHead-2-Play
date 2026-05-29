package BoxHead2Play;

import java.util.List;

public class Devil extends Entity {

	public Devil(float x, float y, int wave) {
		super(x, y,
			(int)(7500 * (1 + (wave - 1) * 0.25f)),
			15f, 195, 45, 35,
			1.8f + (wave - 1) * 0.04f,
			12f, -2);
		xpValue = 200;
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
		s.noStroke();
		s.fill(0, 0, 0, 70);
		s.rect(x - sz + 4, y - sz + 5, sz * 2, sz * 2, 4);
		s.fill(130, 25, 20);
		s.triangle(x - sz + 2,  y - sz + 1, x - sz + 7,  y - sz - 10, x - sz + 12, y - sz + 1);
		s.triangle(x + sz - 12, y - sz + 1, x + sz - 7,  y - sz - 10, x + sz - 2,  y - sz + 1);
		s.stroke(130, 25, 20);
		s.strokeWeight(2);
		s.fill(195, 45, 35);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		s.strokeWeight(1); s.noStroke();
		s.fill(235, 90, 75, 130);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.5f, 2);
		s.fill(255, 230, 0);
		s.rect(x - 8, y - 4, 6, 6, 1);
		s.rect(x + 2, y - 4, 6, 6, 1);
		s.fill(40, 10, 5);
		s.rect(x - 6, y - 2, 2, 4);
		s.rect(x + 4, y - 2, 2, 4);
		s.fill(100, 15, 10);
		s.rect(x - 7, y + 4, 14, 4, 1);
		s.fill(235, 225, 210);
		s.triangle(x - 5, y + 4, x - 3, y + 4, x - 4, y + 9);
		s.triangle(x + 2, y + 4, x + 4, y + 4, x + 3, y + 9);
	}
}
