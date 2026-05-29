package BoxHead2Play;

public class Sniper extends Gun {
	// 850 dmg · 5-round mag · 2.5 s reload · 0.5 shots/s auto · 18 px/f speed · 1100 px range · penetrating
	public Sniper(Entity e, Sketch s) {
		super(e, s, "Sniper", 850, 5, 150, 120, 18f, 1100f, 1, 0f, true, 100, 220, 255);
	}
}
