package BoxHead2Play;

public class Minigun extends Gun {
	// 70 dmg · 80-round drum · 3 s reload · 7 shots/s auto · slight spread · 380 px range
	public Minigun(Entity e, Sketch s) {
		super(e, s, "Minigun", 70, 80, 180, 9, 13f, 380f, 1, 0.06f, false, 255, 90, 90);
	}
}
