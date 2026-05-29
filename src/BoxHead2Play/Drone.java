package BoxHead2Play;

import java.util.List;

// Orbiting combat drone — stored in Player.drones, updated/drawn by Sketch separately.
// Never added to the main entities list.
public class Drone {

	static final float ORBIT_RADIUS = 75f;
	static final float ORBIT_SPEED  = 0.038f; // radians per frame

	float  x, y;
	float  orbitAngle;
	Entity owner;
	Gun    gun;

	// Lightweight proxy entity used only so Gun has a valid shooter reference.
	private final Entity proxy;

	public Drone(Entity owner, float startAngle, Sketch sketch) {
		this.owner       = owner;
		this.orbitAngle  = startAngle;
		this.x           = owner.x + (float) Math.cos(startAngle) * ORBIT_RADIUS;
		this.y           = owner.y + (float) Math.sin(startAngle) * ORBIT_RADIUS;

		// Proxy: HP / size don't matter — it's only used for x, y, enemy fields.
		proxy = new Entity(x, y, 1, 0, 0, 0, 0, 0, 0, 1); // enemy = 1 (player side)
		gun   = new Gun(proxy, sketch, "Drone",
		                160, 9999, 1, 48, 11f, 380f, 1, 0f, false, 100, 255, 200);
	}

	public void update(List<Entity> entities) {
		orbitAngle += ORBIT_SPEED;
		x = owner.x + (float) Math.cos(orbitAngle) * ORBIT_RADIUS;
		y = owner.y + (float) Math.sin(orbitAngle) * ORBIT_RADIUS;
		proxy.x = x;
		proxy.y = y;
		gun.update(entities);
	}

	public void display(Sketch s) {
		// Body
		s.noStroke(); s.fill(0, 0, 0, 60);
		s.rect(x - 7, y - 7, 14, 14, 3);
		s.fill(35, 145, 155);
		s.rect(x - 7, y - 7, 14, 14, 3);
		// Top highlight
		s.fill(90, 220, 215, 180);
		s.rect(x - 6, y - 6, 12, 4, 2);
		// Eye glow
		s.fill(160, 255, 240);
		s.ellipse(x, y + 1, 5, 5);

		// Aim indicator
		float gl = (float) Math.sqrt(gun.xd * gun.xd + gun.yd * gun.yd);
		if (gl > 0) {
			float nx = gun.xd / gl, ny = gun.yd / gl;
			s.stroke(100, 255, 210, 160); s.strokeWeight(1f);
			s.line(x, y, x + nx * 11, y + ny * 11);
			s.noStroke();
		}
		s.noStroke(); s.strokeWeight(1);
	}
}
