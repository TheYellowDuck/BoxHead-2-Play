// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Required Notice: Copyright (c) 2026 George Zhang — https://github.com/TheYellowDuck

package BoxHead2Play;

public class Rocket extends Gun {
	// 350 direct dmg · 4-round mag · 2.5 s reload · 0.5 shots/s auto · slow bullet · AoE 110 px / 220 dmg
	public Rocket(Entity e, Sketch s) {
		super(e, s, "Rocket", 350, 4, 150, 120, 5f, 700f, 1, 0f, false, 255, 120, 30);
		aoeRadius = 110f;
		aoeDamage = 220f;
	}
}
