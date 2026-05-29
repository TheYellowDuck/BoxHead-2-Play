package BoxHead2Play;

import java.util.List;

// Heals the player on contact. Neutral (enemy = 2) so player bullets skip it.
public class HealthPack extends Entity {

	static final int HEAL_AMOUNT = 300;

	public HealthPack(float x, float y) {
		super(x, y, 99999, 10f, 60, 180, 80, 0, 0, 2); // enemy=2: same side as player → bullets skip
		xpValue = 0;
	}

	@Override public void update(List<Entity> entities) { /* static item */ }

	@Override
	public void display(Sketch s) {
		float sz = radius;
		float pulse = 0.88f + 0.12f * (float) Math.sin(s.frameCount * 0.09f);

		// Outer glow
		s.noStroke(); s.fill(60, 200, 90, 70);
		s.ellipse(x, y, (sz + 7) * 2 * pulse, (sz + 7) * 2 * pulse);

		// Box
		s.fill(40, 150, 60);
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		s.fill(70, 210, 100, 160);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.5f, 2);

		// White cross
		s.fill(225, 245, 225);
		s.rect(x - 6, y - 2, 12, 4, 1);
		s.rect(x - 2, y - 6, 4, 12, 1);

		s.noStroke(); s.strokeWeight(1);
	}
}
