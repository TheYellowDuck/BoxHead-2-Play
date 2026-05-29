package BoxHead2Play;

public class SMG extends Gun {
	// 90 dmg · 30-round mag · 1 s reload · 5 shots/s auto · slight spread · 350 px range
	public SMG(Entity e, Sketch s) {
		super(e, s, "SMG", 90, 30, 60, 12, 11f, 350f, 1, 0.05f, false, 150, 255, 150);
	}
}
