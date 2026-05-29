package BoxHead2Play;

import java.util.List;

public class Zombie extends Entity {

	public Zombie(float x, float y, int wave) {
		super(x, y,
			(int)(600 * (1 + (wave - 1) * 0.16f)),
			12.5f, 128, 168, 88,
			1.5f + (wave - 1) * 0.025f,
			5, -1);
		xpValue = 25;
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
		s.noStroke();
		s.fill(0, 0, 0, 55);
		s.rect(x - sz + 3, y - sz + 4, sz * 2, sz * 2, 3);
		s.stroke(75, 110, 45);
		s.strokeWeight(2);
		s.fill(128, 168, 88);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 3);
		s.strokeWeight(1); s.noStroke();
		s.fill(165, 205, 120, 130);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.5f, 2);
		s.fill(195, 45, 30);
		s.rect(x - 7, y - 4, 5, 5, 1);
		s.rect(x + 2, y - 4, 5, 5, 1);
		s.fill(15, 5, 5);
		s.rect(x - 6, y - 3, 3, 3);
		s.rect(x + 3, y - 3, 3, 3);
		s.fill(30, 15, 10);
		s.rect(x - 6, y + 4, 12, 4, 1);
		s.fill(210, 200, 185);
		s.rect(x - 5, y + 4, 2, 3);
		s.rect(x - 1, y + 4, 2, 3);
		s.rect(x + 3, y + 4, 2, 3);
	}
}
