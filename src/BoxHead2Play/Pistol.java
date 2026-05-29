package BoxHead2Play;

public class Pistol extends Gun {
	// 250 dmg · 12-round mag · 1.5 s reload · 0.67 shots/s auto · 10 px/f · 500 px range
	public Pistol(Entity e, Sketch s) {
		super(e, s, "Pistol", 250, 12, 90, 40, 10f, 500f, 1, 0f, false, 255, 240, 100);
	}
}
