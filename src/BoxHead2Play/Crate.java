package BoxHead2Play;

import java.util.List;

// Destroyable wooden crate — neutral entity (no contact damage, bullets from any side hit it).
public class Crate extends Entity {

	int tileX, tileY; // world-map tile coordinates

	public Crate(float x, float y, int tileX, int tileY) {
		super(x, y, 500, 13f, 170, 115, 55, 0, 0, 0); // enemy = 0 → neutral
		this.tileX = tileX; this.tileY = tileY;
		xpValue = 0;
	}

	@Override
	public void update(List<Entity> entities) {
		// Crates don't move on their own, but still resolve wall/entity collisions
		// so they can be pushed and don't clip into walls.
		super.update(entities);
	}

	@Override
	public void display(Sketch s) {
		float sz = radius;

		// Shadow
		s.noStroke(); s.fill(0, 0, 0, 60);
		s.rect(x - sz + 3, y - sz + 4, sz * 2, sz * 2, 3);

		// Wood body
		s.stroke(110, 70, 25); s.strokeWeight(2);
		s.fill(170, 115, 55);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 3);
		s.strokeWeight(1); s.noStroke();

		// Top plank highlight
		s.fill(210, 155, 80, 160);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, 5, 2);

		// Cross brace
		s.fill(120, 75, 30, 200);
		s.rect(x - sz + 2, y - 1, sz * 2 - 4, 2);  // horizontal brace
		s.rect(x - 1, y - sz + 2, 2, sz * 2 - 4);  // vertical brace

		// Corner nails
		s.fill(90, 90, 90);
		s.ellipse(x - sz + 5, y - sz + 5, 4, 4);
		s.ellipse(x + sz - 5, y - sz + 5, 4, 4);
		s.ellipse(x - sz + 5, y + sz - 5, 4, 4);
		s.ellipse(x + sz - 5, y + sz - 5, 4, 4);

		// HP crack: show damage when below 50 %
		if (currhp < maxhp * 0.5f) {
			s.stroke(90, 55, 20, 200); s.strokeWeight(1.5f);
			s.line(x - 3, y - sz + 3, x + 5, y + 3);
			s.line(x + 4, y - sz + 6, x - 2, y + sz - 4);
		}
		s.noStroke(); s.strokeWeight(1);
	}
}
