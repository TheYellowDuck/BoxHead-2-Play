package BoxHead2Play;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class WorldMap {

	static final int TS = 30;
	static int mapSeed = 12345;

	// Tile-result cache (connectivity-checked isWall results, cleared on seed change or wall open)
	private static final HashMap<Long, Boolean> wallCache = new HashMap<>();

	/**
	 * Wall tiles forcibly converted to floor by {@link Pathfinder#fixEnclosures}.
	 * These override both the noise and the connectivity check so that previously
	 * enclosed floor areas are permanently linked to the rest of the map.
	 * Package-private so Pathfinder can call {@link #openWall}.
	 */
	static final HashSet<Long> forcedFloor = new HashSet<>();

	public static void setSeed(int seed) {
		mapSeed = seed;
		wallCache.clear();
		forcedFloor.clear();
	}

	/**
	 * Open a wall tile — called by Pathfinder when it detects a floor tile that is
	 * unreachable from the player (i.e. inside an enclosed ring).
	 * Clears the wall cache so neighbours recompute their connectivity status.
	 */
	static void openWall(int tx, int ty) {
		if (forcedFloor.add(tileKey(tx, ty))) {
			wallCache.clear(); // connectivity results for neighbours may change
		}
	}

	// ── Tile queries (public API) ──────────────────────────────────────────

	/**
	 * Full connectivity-guaranteed wall check, with caching.
	 * Tiles in {@code forcedFloor} always return false.
	 */
	public static boolean isWall(int tx, int ty) {
		long k = tileKey(tx, ty);
		if (forcedFloor.contains(k)) return false;         // fast path: forced floor
		Boolean cached = wallCache.get(k);
		if (cached != null) return cached;
		boolean result = isWallNoise(tx, ty) && !wouldIsolate(tx, ty);
		wallCache.put(k, result);
		return result;
	}

	public static boolean isWallAt(float wx, float wy) {
		return isWall((int) Math.floor(wx / TS), (int) Math.floor(wy / TS));
	}

	/** True when the straight line from (x1,y1)→(x2,y2) passes through no wall tile. */
	public static boolean hasLOS(float x1, float y1, float x2, float y2) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1f) return true;
		float nx = dx / len, ny = dy / len;
		float step = TS * 0.45f;
		for (float d = step; d < len - step; d += step)
			if (isWallAt(x1 + nx * d, y1 + ny * d)) return false;
		return true;
	}

	/** Returns true if this floor tile should contain a wooden crate. */
	public static boolean isCrate(int tx, int ty) {
		if (Math.abs(tx) < 7 && Math.abs(ty) < 7) return false;
		if (isWall(tx, ty)) return false;
		int seed = mapSeed ^ 0xABCD1234;
		int h    = (tx * 374761393 ^ seed) ^ (ty * 1779033703 ^ (seed * 1000003));
		h ^= h >>> 13; h *= 1540483477; h ^= h >>> 15;
		return (h & 0xFFFF) > 64880;
	}

	// ── Internal wall checks ───────────────────────────────────────────────

	/**
	 * Pure noise wall result — NO forcedFloor, NO connectivity check.
	 * Used only when computing whether a NEW tile should be a wall.
	 */
	private static boolean isWallNoise(int tx, int ty) {
		if (Math.abs(tx) < 7 && Math.abs(ty) < 7) return false;
		float sx = (mapSeed & 0xFF)         * 0.17f;
		float sy = ((mapSeed >> 8) & 0xFF)  * 0.17f;
		float dx = ((mapSeed >> 16) & 0xFF) * 0.11f;
		float dy = ((mapSeed >> 24) & 0xFF) * 0.11f;
		float blob = vn(tx * 0.09f + sx, ty * 0.09f + sy);
		if (blob < 0.66f) return false;
		float detail = vn(tx * 0.27f + dx, ty * 0.27f + dy);
		return detail > 0.45f;
	}

	/**
	 * Noise + forcedFloor: what {@link #wouldIsolate} uses to check neighbours.
	 * Using this (not isWallNoise) means that tiles Pathfinder has already opened
	 * are treated as floor when evaluating connectivity for adjacent tiles — so the
	 * connectivity fix propagates correctly without recursion.
	 */
	static boolean isWallBase(int tx, int ty) {
		if (forcedFloor.contains(tileKey(tx, ty))) return false;
		return isWallNoise(tx, ty);
	}

	// ── Connectivity check (prevents single-tile / small enclosed areas) ───

	private static final int R    = 4;
	private static final int SIDE = 2 * R + 1; // 9×9 local BFS window

	private static final int[] DCX = {0, 0, 1, -1};
	private static final int[] DCY = {1, -1, 0, 0};

	/**
	 * Returns true if making (tx,ty) a wall would strand any cardinal floor
	 * neighbour in the 9×9 local area (catches small enclosed pockets).
	 * Larger rings are caught reactively by {@link Pathfinder#fixEnclosures}.
	 */
	private static boolean wouldIsolate(int tx, int ty) {
		for (int ci = 0; ci < 4; ci++) {
			int fx = tx + DCX[ci], fy = ty + DCY[ci];
			if (isWallBase(fx, fy)) continue;

			boolean[][] vis = new boolean[SIDE][SIDE];
			boolean reached = false;
			Queue<int[]> q  = new LinkedList<>();
			int sox = fx - tx + R, soy = fy - ty + R;
			vis[sox][soy] = true;
			q.add(new int[]{sox, soy});

			while (!q.isEmpty() && !reached) {
				int[] cur = q.poll();
				int ox = cur[0], oy = cur[1];
				if (ox == 0 || ox == SIDE - 1 || oy == 0 || oy == SIDE - 1) {
					reached = true; break;
				}
				for (int di = 0; di < 4; di++) {
					int nox = ox + DCX[di], noy = oy + DCY[di];
					if (vis[nox][noy]) continue;
					int ntx = tx + (nox - R), nty = ty + (noy - R);
					if (ntx == tx && nty == ty) continue;
					if (isWallBase(ntx, nty)) continue;
					vis[nox][noy] = true;
					q.add(new int[]{nox, noy});
				}
			}
			if (!reached) return true;
		}
		return false;
	}

	// ── Value noise ────────────────────────────────────────────────────────

	private static float vn(float x, float y) {
		int   ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		float fx = x - ix,              fy = y - iy;
		float v00 = htf(ix,     iy),     v10 = htf(ix + 1, iy);
		float v01 = htf(ix,     iy + 1), v11 = htf(ix + 1, iy + 1);
		float ux  = fx * fx * (3 - 2 * fx);
		float uy  = fy * fy * (3 - 2 * fy);
		return v00 * (1-ux)*(1-uy) + v10 * ux*(1-uy)
		     + v01 * (1-ux)*uy     + v11 * ux*uy;
	}

	private static float htf(int x, int y) {
		int h = (x * 374761393 ^ mapSeed) ^ (y * 1779033703 ^ (mapSeed * 1000003));
		h ^= h >>> 13; h *= 1540483477; h ^= h >>> 15;
		return (h & 0x7FFF) / (float) 0x8000;
	}

	private static long tileKey(int tx, int ty) {
		return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
	}

	// ── Rendering ──────────────────────────────────────────────────────────

	public static void display(Sketch s, float camX, float camY) {
		int startTx = (int) Math.floor((camX - s.width  / 2f) / TS) - 1;
		int startTy = (int) Math.floor((camY - s.height / 2f) / TS) - 1;
		int endTx   = (int) Math.ceil ((camX + s.width  / 2f) / TS) + 1;
		int endTy   = (int) Math.ceil ((camY + s.height / 2f) / TS) + 1;

		s.noStroke();
		for (int ty = startTy; ty <= endTy; ty++) {
			for (int tx = startTx; tx <= endTx; tx++) {
				float wx = tx * TS, wy = ty * TS;
				if (isWall(tx, ty)) {
					s.fill(18, 16, 24);    s.rect(wx + 4, wy + 4, TS, TS);
					s.fill(80, 74, 96);    s.rect(wx, wy, TS, TS);
					s.fill(128, 120, 152); s.rect(wx, wy, TS, 5);
					s.fill(108, 100, 130); s.rect(wx, wy + 5, 4, TS - 9);
					s.fill(50, 45, 62);    s.rect(wx, wy + TS - 4, TS, 4);
					                       s.rect(wx + TS - 4, wy, 4, TS - 4);
				} else {
					s.fill(((Math.floorMod(tx, 2)) + (Math.floorMod(ty, 2))) % 2 == 0
					       ? s.color(52, 48, 58) : s.color(47, 44, 53));
					s.rect(wx, wy, TS, TS);
				}
			}
		}
	}
}
