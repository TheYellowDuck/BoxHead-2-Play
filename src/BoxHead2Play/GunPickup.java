package BoxHead2Play;

import java.util.List;

// Dropped by crates. Player walks over it to acquire the gun.
// enemy = 2 so player bullets skip it (same positive side).
public class GunPickup extends Entity {

	String gunName;  // must match the `name` field set in each Gun subclass
	String abbrev;
	int pr, pg, pb;  // display colour (from gun's bullet colour)

	public GunPickup(float x, float y, String gunName) {
		super(x, y, 99999, 11f, 200, 200, 200, 0, 0, 2);
		this.gunName = gunName;
		xpValue      = 0;
		int[] col    = color(gunName);
		pr = col[0]; pg = col[1]; pb = col[2];
		abbrev       = abbrev(gunName);
	}

	@Override public void update(List<Entity> entities) { /* static item */ }

	@Override
	public void display(Sketch s) {
		float sz    = radius;
		float pulse = 0.88f + 0.12f * (float) Math.sin(s.frameCount * 0.10f);

		// Outer glow
		s.noStroke(); s.fill(pr, pg, pb, 65);
		s.ellipse(x, y, (sz + 8) * 2 * pulse, (sz + 8) * 2 * pulse);

		// Box
		s.fill(Math.max(pr - 60, 0), Math.max(pg - 60, 0), Math.max(pb - 60, 0));
		s.rect(x - sz, y - sz, sz * 2, sz * 2, 4);
		// Top highlight
		s.fill(pr, pg, pb, 180);
		s.rect(x - sz + 2, y - sz + 2, sz * 2 - 4, sz * 0.5f, 2);

		// Abbreviation label
		s.fill(255); s.textSize(8); s.textAlign(processing.core.PConstants.CENTER, processing.core.PConstants.CENTER);
		s.text(abbrev, x, y + 1);

		s.noStroke(); s.strokeWeight(1);
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	public static int[] color(String name) {
		switch (name) {
		case "Shotgun": return new int[]{ 255, 155,  55 };
		case "Sniper":  return new int[]{ 100, 220, 255 };
		case "Minigun": return new int[]{ 255,  90,  90 };
		case "SMG":     return new int[]{ 150, 255, 150 };
		case "Rocket":  return new int[]{ 255, 120,  30 };
		default:        return new int[]{ 200, 200, 200 };
		}
	}

	private static String abbrev(String name) {
		switch (name) {
		case "Shotgun": return "SHG";
		case "Sniper":  return "SNP";
		case "Minigun": return "MGN";
		case "SMG":     return "SMG";
		case "Rocket":  return "RKT";
		default:        return name.substring(0, Math.min(3, name.length())).toUpperCase();
		}
	}
}
