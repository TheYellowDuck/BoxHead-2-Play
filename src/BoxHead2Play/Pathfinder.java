package BoxHead2Play;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Dual BFS flow field centred on the player:
 *
 *   flowField      — standard 8-direction, for entities whose radius ≤ TS/2 (≤15 px).
 *   flowFieldLarge — 4-direction only, walls inflated by 1 tile, for large entities
 *                    (Brute r=19, Devil r=15) that cannot fit through single-tile gaps.
 *
 * Both are rebuilt only when the player steps into a new tile (cheap: ~2 k HashMap puts).
 */
public class Pathfinder {

	private static final int RADIUS = 22; // tile radius to compute around player

	private static final HashMap<Long, float[]> flowField      = new HashMap<>();
	private static final HashMap<Long, float[]> flowFieldLarge = new HashMap<>();

	private static int lastPx = Integer.MAX_VALUE;
	private static int lastPy = Integer.MAX_VALUE;

	// 4 cardinal directions first, then 4 diagonals
	private static final int[] DDX = { 0, 0, 1, -1,  1,  1, -1, -1 };
	private static final int[] DDY = { 1, -1, 0,  0,  1, -1,  1, -1 };

	private static long key(int tx, int ty) {
		return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
	}

	/**
	 * A tile is safe for a large entity (radius > TS/2) only when it AND all 4
	 * cardinal neighbours are open floor — guaranteeing ≥ 1 full tile of clearance
	 * from every wall edge.
	 */
	private static boolean isLargePassable(int tx, int ty) {
		return !WorldMap.isWall(tx,     ty)
		    && !WorldMap.isWall(tx + 1, ty) && !WorldMap.isWall(tx - 1, ty)
		    && !WorldMap.isWall(tx,     ty + 1) && !WorldMap.isWall(tx, ty - 1);
	}

	// ── Public API ─────────────────────────────────────────────────────────

	/** Call from Sketch.initGame() so a new map seed always gets a fresh field. */
	public static void reset() {
		flowField.clear();
		flowFieldLarge.clear();
		lastPx = Integer.MAX_VALUE;
		lastPy = Integer.MAX_VALUE;
	}

	/** Call once per frame from Sketch before entity updates. */
	public static void update(float playerX, float playerY) {
		int px = (int) Math.floor(playerX / WorldMap.TS);
		int py = (int) Math.floor(playerY / WorldMap.TS);
		if (px == lastPx && py == lastPy) return;
		lastPx = px; lastPy = py;
		rebuild(px, py);
	}

	/**
	 * Returns a steering direction from (wx,wy) aimed at the **world-space centre**
	 * of the next tile in the flow field path, rather than the raw grid-quantised
	 * direction vector.  This keeps entities centred in corridors and avoids them
	 * wedging into wall corners when their in-tile offset puts the raw direction
	 * straight into a wall.
	 *
	 * Returns null when outside the computed radius (caller falls back to direct).
	 * Returns {0,0} when already at the player's tile.
	 */
	public static float[] getSteerDirection(float wx, float wy) {
		int tx = (int) Math.floor(wx / WorldMap.TS);
		int ty = (int) Math.floor(wy / WorldMap.TS);
		float[] dir = flowField.get(key(tx, ty));
		return steerToward(dir, tx, ty, wx, wy);
	}

	/** Clearance-inflated steer direction for large enemies (Brute, Devil). */
	public static float[] getLargeSteerDirection(float wx, float wy) {
		int tx = (int) Math.floor(wx / WorldMap.TS);
		int ty = (int) Math.floor(wy / WorldMap.TS);
		float[] dir = flowFieldLarge.get(key(tx, ty));
		if (dir == null) dir = flowField.get(key(tx, ty)); // fallback to standard
		return steerToward(dir, tx, ty, wx, wy);
	}

	/**
	 * Core steering helper: given the raw flow-field direction at (tx,ty),
	 * compute a unit vector from (wx,wy) toward the centre of the next tile.
	 */
	private static float[] steerToward(float[] dir, int tx, int ty, float wx, float wy) {
		if (dir == null) return null;
		if (dir[0] == 0f && dir[1] == 0f) return dir; // at player tile — no movement

		int ntx = tx + Math.round(dir[0]);
		int nty = ty + Math.round(dir[1]);
		if (WorldMap.isWall(ntx, nty)) return dir; // safety: raw direction is fine

		float gx = ntx * WorldMap.TS + WorldMap.TS * 0.5f - wx;
		float gy = nty * WorldMap.TS + WorldMap.TS * 0.5f - wy;
		float len = (float) Math.sqrt(gx * gx + gy * gy);
		if (len < 1f) return dir; // already near next tile centre
		return new float[]{ gx / len, gy / len };
	}

	// ── BFS rebuild ────────────────────────────────────────────────────────

	private static void rebuild(int px, int py) {
		buildStandard(px, py);
		fixEnclosures(px, py);
		buildLarge(px, py);
	}

	/**
	 * After buildStandard, any floor tile in the radius that isn't in the flow
	 * field is enclosed inside a wall ring (a "donut hole"). Break one adjacent
	 * wall to connect each pocket, then rebuild — repeat until none remain.
	 */
	static void fixEnclosures(int px, int py) {
		boolean changed;
		do {
			changed = false;
			for (int dy = -RADIUS; dy < RADIUS; dy++) {
				for (int dx = -RADIUS; dx < RADIUS; dx++) {
					int tx = px + dx, ty = py + dy;
					if (WorldMap.isWall(tx, ty)) continue;            // skip walls
					if (flowField.containsKey(key(tx, ty))) continue; // already reachable
					// Unreachable floor tile — open one adjacent wall to connect it
					for (int d = 0; d < 4; d++) {
						int nx = tx + DDX[d], ny = ty + DDY[d];
						if (WorldMap.isWall(nx, ny)) {
							WorldMap.openWall(nx, ny);
							changed = true;
							break;
						}
					}
				}
			}
			if (changed) buildStandard(px, py); // recheck with updated walls
		} while (changed);
	}

	/** 8-directional BFS, standard wall passability. */
	private static void buildStandard(int px, int py) {
		flowField.clear();
		Queue<int[]> queue = new LinkedList<>();
		queue.add(new int[]{ px, py });
		flowField.put(key(px, py), new float[]{ 0f, 0f });

		while (!queue.isEmpty()) {
			int[] cur = queue.poll();
			int tx = cur[0], ty = cur[1];
			if (Math.abs(tx - px) >= RADIUS || Math.abs(ty - py) >= RADIUS) continue;

			for (int d = 0; d < 8; d++) {
				int nx = tx + DDX[d], ny = ty + DDY[d];
				long nk = key(nx, ny);
				if (flowField.containsKey(nk)) continue;
				if (WorldMap.isWall(nx, ny)) continue;
				// No corner-cutting for diagonals
				if (d >= 4 && (WorldMap.isWall(tx + DDX[d], ty) || WorldMap.isWall(tx, ty + DDY[d]))) continue;

				float dx = tx - nx, dy = ty - ny;
				float len = (float) Math.sqrt(dx * dx + dy * dy);
				flowField.put(nk, new float[]{ dx / len, dy / len });
				queue.add(new int[]{ nx, ny });
			}
		}
	}

	/**
	 * 4-directional BFS with inflated walls — skips tiles adjacent to any wall.
	 * The player's own tile is always seeded so large enemies eventually reach it
	 * even if the player stands next to a wall.
	 */
	private static void buildLarge(int px, int py) {
		flowFieldLarge.clear();
		Queue<int[]> queue = new LinkedList<>();
		queue.add(new int[]{ px, py });
		flowFieldLarge.put(key(px, py), new float[]{ 0f, 0f });

		while (!queue.isEmpty()) {
			int[] cur = queue.poll();
			int tx = cur[0], ty = cur[1];
			if (Math.abs(tx - px) >= RADIUS || Math.abs(ty - py) >= RADIUS) continue;

			for (int d = 0; d < 8; d++) {
				int nx = tx + DDX[d], ny = ty + DDY[d];
				long nk = key(nx, ny);
				if (flowFieldLarge.containsKey(nk)) continue;
				if (!isLargePassable(nx, ny)) continue;
				// Diagonal: both cardinal intermediates must also have full clearance
				if (d >= 4 && (!isLargePassable(tx + DDX[d], ty) || !isLargePassable(tx, ty + DDY[d]))) continue;

				float dx = tx - nx, dy = ty - ny;
				float len = (float) Math.sqrt(dx * dx + dy * dy);
				flowFieldLarge.put(nk, new float[]{ dx / len, dy / len });
				queue.add(new int[]{ nx, ny });
			}
		}
	}
}
