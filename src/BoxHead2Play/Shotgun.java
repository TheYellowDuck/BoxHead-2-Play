package BoxHead2Play;

public class Shotgun extends Gun {
	// 110 dmg × 5 pellets · 6-round mag · 2 s reload · 1 shot/s auto · 250 px range · wide spread
	public Shotgun(Entity e, Sketch s) {
		super(e, s, "Shotgun", 110, 6, 120, 60, 9f, 250f, 5, 0.14f, false, 255, 155, 55);
	}
}
