package BoxHead2Play;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import processing.core.PApplet;
import processing.event.MouseEvent;

public class Sketch extends PApplet {

	// ── Game data ──────────────────────────────────────────────────────────
	Player       p;
	List<Entity> entities = new ArrayList<>();
	List<Bullet>  bullets    = new ArrayList<>();
	List<float[]> explosions    = new ArrayList<>(); // {x,y,radius,timer,maxTimer}
	List<float[]> lightningArcs = new ArrayList<>(); // {x1,y1,x2,y2,timer}
	List<float[]> floatingTexts = new ArrayList<>(); // {x,y,dmg,timer}
	int           kills         = 0;

	// ── Camera (world space) ───────────────────────────────────────────────
	float camX, camY;
	float aimX, aimY; // world-space cursor position

	// ── Wave / spawn ───────────────────────────────────────────────────────
	int waveNumber = 1;
	int waveTimer  = 0;
	int spawnTimer = 150; // initial grace period before first spawn
	int spawnRate  = 150; // frames between spawn ticks (starts at 2.5 s)
	int gameTime   = 0;
	int waveNotifyTimer = 0;

	// Wave duration scales with wave number: 25 s base + 3 s per wave, capped at 2 min
	private int waveDuration() {
		return Math.min(7200, 1500 + (waveNumber - 1) * 180);
	}

	// ── Crate tracking (procedural tile-based) ────────────────────────────
	HashMap<Long, Crate> activeCrateMap  = new HashMap<>();
	HashSet<Long>        destroyedCrates = new HashSet<>();

	// ── Level-up state ─────────────────────────────────────────────────────
	Upgrade[]        currentUpgrades = new Upgrade[4];
	int              savedCurrgun    = 0;
	// Upgrade types taken this run — prevents duplicate picks. STAT types (damage, speed,
	// hp, heal, fire rate, reload, multishot) and UPGRADE_GUN are always re-pooled.
	HashSet<String>  takenUpgrades   = new HashSet<>();

	// ── Kill streak ────────────────────────────────────────────────────────
	int   killStreak      = 0;
	int   killStreakTimer = 0;
	float xpMultiplier    = 1f;

	// ── Wave events ────────────────────────────────────────────────────────
	String waveEventText  = "";
	int    waveEventTimer = 0;

	// ── Danger zones ───────────────────────────────────────────────────────
	// {worldX, worldY, maxRadius, timer, maxTimer}
	List<float[]> dangerZones = new ArrayList<>();

	// ── Character select ───────────────────────────────────────────────────
	int selectedCharacter = 0; // 0=Soldier 1=Scout 2=Engineer

	// ── High score {kills, wave, timeSecs, level} ─────────────────────────
	int[] highScore = {0, 0, 0, 0};

	// ── State machine ──────────────────────────────────────────────────────
	static final int STATE_MENU      = 0;
	static final int STATE_PLAYING   = 1;
	static final int STATE_PAUSED    = 2;
	static final int STATE_GAME_OVER = 3;
	static final int STATE_LEVELUP   = 5;
	static final int STATE_CHARACTER = 6;
	int gameState = STATE_MENU;

	// ── Input flags ────────────────────────────────────────────────────────
	boolean wPressed, aPressed, sPressed, dPressed;

	// ── Processing lifecycle ───────────────────────────────────────────────
	public void startSketch() {
		PApplet.runSketch(new String[]{ this.getClass().getName() }, this);
	}

	@Override public void settings() { size(600, 600); pixelDensity(displayDensity()); }
	@Override public void setup()    { highScore = loadHighScore(); }

	// ── Transitions ────────────────────────────────────────────────────────
	private void startGame()  { initGame(); gameState = STATE_PLAYING;  noCursor(); }
	private void pauseGame()  {
		gameState = STATE_PAUSED; cursor();
		if (p != null) { p.xd = 0; p.yd = 0; }
		wPressed = aPressed = sPressed = dPressed = false;
	}
	private void resumeGame() { gameState = STATE_PLAYING; noCursor(); }
	private void goToMenu()   {
		gameState = STATE_MENU; cursor();
		entities.clear(); bullets.clear(); explosions.clear();
		lightningArcs.clear(); floatingTexts.clear(); dangerZones.clear();
		activeCrateMap.clear(); destroyedCrates.clear(); p = null;
	}

	private void initGame() {
		kills = 0; gameTime = 0;
		waveNumber = 1; waveTimer = 0;
		spawnTimer = 150; spawnRate = 150;
		waveNotifyTimer = 0;
		entities.clear(); bullets.clear(); explosions.clear();
		lightningArcs.clear(); floatingTexts.clear(); dangerZones.clear();
		activeCrateMap.clear(); destroyedCrates.clear();
		killStreak = 0; killStreakTimer = 0; xpMultiplier = 1f;
		waveEventText = ""; waveEventTimer = 0;
		takenUpgrades.clear();
		wPressed = aPressed = sPressed = dPressed = false;

		WorldMap.setSeed((int) System.currentTimeMillis()); // fresh layout each run
		Pathfinder.reset(); // clear stale flow field from previous map seed

		p = new Player(0, 0);
		p.guns.add(new Pistol(p, this));
		entities.add(p);
		camX = 0; camY = 0;
		aimX = 0; aimY = 0;

		// Apply character bonus
		switch (selectedCharacter) {
		case 0: // Soldier — +25 % damage, bonus Shotgun
			for (Gun g : p.guns) g.damage *= 1.25f;
			p.guns.add(new Shotgun(p, this));
			break;
		case 1: // Scout — +35 % speed, bonus SMG
			p.speed *= 1.35f;
			p.guns.add(new SMG(p, this));
			break;
		case 2: // Engineer — start with drone
			p.droneLevel = 1;
			p.drones.add(new Drone(p, 0f, this));
			break;
		}

		// Two starter zombies
		spawnEnemy(false); spawnEnemy(false);
	}

	// ── Draw dispatch ──────────────────────────────────────────────────────
	@Override
	public void draw() {
		switch (gameState) {
		case STATE_MENU:      drawMenu();            break;
		case STATE_PLAYING:   drawPlaying();         break;
		case STATE_PAUSED:    drawPaused();          break;
		case STATE_GAME_OVER: drawEndScreen();       break;
		case STATE_LEVELUP:   drawLevelUp();         break;
		case STATE_CHARACTER: drawCharacterSelect(); break;
		}
	}

	// ── Menu ───────────────────────────────────────────────────────────────
	private void drawMenu() {
		background(35, 32, 42);
		// Static floor preview
		pushMatrix(); translate(width / 2f, height / 2f);
		WorldMap.display(this, 0, 0);
		popMatrix();

		noStroke(); fill(0, 0, 0, 165); rect(0, 0, width, height);

		textAlign(CENTER, CENTER);
		fill(0, 0, 0, 90); textSize(70); text("BOXHEAD", width / 2f + 3, 168);
		fill(215, 50, 40);               text("BOXHEAD", width / 2f,     165);
		fill(170, 170, 170); textSize(26); text("2  P L A Y", width / 2f, 215);

		stroke(75, 70, 95); strokeWeight(1);
		line(width / 2f - 110, 250, width / 2f + 110, 250); noStroke();

		float sy = 308;
		drawButton("START GAME", width / 2f, sy, 190, 50,
		           inButton(mouseX, mouseY, width / 2f, sy, 190, 50), 42, 165, 68);
		// Show best score on menu
		if (highScore[0] > 0) {
			fill(110, 108, 135); textSize(11);
			text("Best run:  " + highScore[0] + " kills  ·  Wave " + highScore[1]
			     + "  ·  Level " + highScore[3], width / 2f, 365);
		}

		fill(85, 82, 105); textSize(11);
		text("WASD / Arrows   Move         Mouse   Aim", width / 2f, 540);
		text("Click / Space   Shoot        Q/E / Scroll   Switch gun", width / 2f, 558);
		text("R   Reload         P / Esc   Pause", width / 2f, 574);
	}

	// ── Playing ────────────────────────────────────────────────────────────
	private void drawPlaying() {
		gameTime++;

		// Camera follows player
		camX = p.x; camY = p.y;

		// World-space aim position from mouse
		aimX = mouseX - width / 2f + camX;
		aimY = mouseY - height / 2f + camY;

		background(35, 32, 42);

		// ── World-space rendering ──────────────────────────────────────────
		pushMatrix();
		translate(width / 2f - camX, height / 2f - camY);

		WorldMap.display(this, camX, camY);

		handleSpawning();
		manageCrates();
		Pathfinder.update(p.x, p.y); // rebuild flow field if player changed tiles

		float prevHP = p.currhp; // snapshot before updates for shield absorption

		// Update entities
		for (int i = 0; i < entities.size(); i++) {
			Entity e = entities.get(i);
			if (e.die == 1) continue;
			e.update(entities);
		}

		// Update guns for all living entities (player active gun + enemy guns)
		for (int i = 0; i < entities.size(); i++) {
			Entity e = entities.get(i);
			if (e.die == 1 || e.guns.isEmpty()) continue;
			int idx = (e == p) ? p.currgun : 0;
			if (idx < e.guns.size()) e.guns.get(idx).update(entities);
		}

		// Collect any health packs / gun pickups the player is touching
		collectPickups();

		// Sync drone/turret bullets to match player's active gun style & modifiers
		syncAlliedGuns();

		// Drones — orbit + auto-fire (not in entities list)
		for (Drone d : p.drones) d.update(entities);

		// Rotating knives — per-frame damage using player's upgradeable fields
		if (p.knifeCount > 0) {
			p.knifeAngle += p.knifeRotSpeed;
			float knifeR = 12f;
			for (int k = 0; k < p.knifeCount; k++) {
				float ka = p.knifeAngle + k * ((float) Math.PI * 2 / p.knifeCount);
				float kx = p.x + (float) Math.cos(ka) * p.knifeOrbitRadius;
				float ky = p.y + (float) Math.sin(ka) * p.knifeOrbitRadius;
				for (Entity e : entities) {
					if (e.enemy >= 0 || e.die == 1) continue;
					float dx = e.x - kx, dy = e.y - ky;
					if (dx * dx + dy * dy < (knifeR + e.radius) * (knifeR + e.radius)) {
						e.currhp -= p.knifeDamage;
						if (e.currhp <= 0) e.die = 1;
					}
				}
			}
		}

		// Aura — continuous ring of damage around player
		if (p.auraDamage > 0) {
			float arSq = p.auraRadius * p.auraRadius;
			for (Entity e : entities) {
				if (e.enemy >= 0 || e.die == 1) continue;
				float dx = e.x - p.x, dy = e.y - p.y;
				if (dx * dx + dy * dy < arSq) {
					e.currhp -= p.auraDamage;
					if (e.currhp <= 0) e.die = 1;
				}
			}
		}

		// Pulse — periodic shockwave
		if (p.pulseDamage > 0) {
			if (p.pulseCooldown > 0) {
				p.pulseCooldown--;
			} else {
				p.pulseCooldown    = p.pulseMaxCooldown;
				p.pulseAnimRadius  = 0f;
				p.pulseAnimTimer   = 25;
				float prSq = p.pulseMaxRadius * p.pulseMaxRadius;
				for (Entity e : entities) {
					if (e.enemy >= 0 || e.die == 1) continue;
					float dx = e.x - p.x, dy = e.y - p.y;
					if (dx * dx + dy * dy < prSq) {
						e.currhp -= p.pulseDamage;
						if (e.currhp <= 0) e.die = 1;
					}
				}
			}
			if (p.pulseAnimTimer > 0) {
				p.pulseAnimTimer--;
				p.pulseAnimRadius = p.pulseMaxRadius * (1f - p.pulseAnimTimer / 25f);
			}
		}

		// Magnet — pull pickups toward player
		if (p.magnetRadius > 0) {
			float mrSq = p.magnetRadius * p.magnetRadius;
			for (Entity e : entities) {
				if (!(e instanceof HealthPack) && !(e instanceof GunPickup)) continue;
				float dx = p.x - e.x, dy = p.y - e.y;
				float dSq = dx * dx + dy * dy;
				if (dSq > 0 && dSq < mrSq) {
					float dist = (float) Math.sqrt(dSq);
					float spd  = 3.5f + (p.magnetRadius - dist) * 0.02f;
					e.x += dx / dist * spd;
					e.y += dy / dist * spd;
				}
			}
		}

		// Shield absorption: redirect HP loss this frame into shield first
		float dmgTaken = prevHP - p.currhp;
		if (dmgTaken > 0 && p.shieldHP > 0) {
			float absorbed = Math.min(p.shieldHP, dmgTaken);
			p.shieldHP  -= absorbed;
			p.currhp    += absorbed;
			p.shieldCooldown = p.shieldHitDelay;
		}
		// Shield recharge
		if (p.shieldCooldown > 0) p.shieldCooldown--;
		else if (p.shieldHP < p.shieldMax)
			p.shieldHP = Math.min(p.shieldMax, p.shieldHP + p.shieldMax / 300f);
		// Clear die flag if shield saved the player from a killing blow
		if (p.currhp > 0) p.die = 0;

		// Thorns — reflect a fraction of contact damage back at attackers
		if (p.thorns > 0) {
			for (Entity e : entities) {
				if (e.die == 1 || e.enemy >= 0) continue;
				float dxt = e.x - p.x, dyt = e.y - p.y;
				if (dxt * dxt + dyt * dyt < (e.radius + p.radius) * (e.radius + p.radius)) {
					e.currhp -= e.damage * p.thorns;
					if (e.currhp <= 0) e.die = 1;
				}
			}
		}

		// Kill-streak timer decay
		if (killStreakTimer > 0) killStreakTimer--;
		else if (killStreak > 0) { killStreak = 0; xpMultiplier = 1f; }

		// Update + prune bullets; harvest side effects; trigger AoE on impact
		for (int i = bullets.size() - 1; i >= 0; i--) {
			Bullet b = bullets.get(i);
			b.update(entities);
			// Harvest chain arcs (pendingArcs format: x1,y1,x2,y2,r,g,b)
			for (float[] arc : b.pendingArcs)
				lightningArcs.add(new float[]{ arc[0], arc[1], arc[2], arc[3], 15f, arc[4], arc[5], arc[6] });
			b.pendingArcs.clear();
			// Vampirism — heal player a fraction of damage dealt
			if (p.vampirism > 0 && b.pendingHeal > 0 && b.ownerSide > 0)
				p.currhp = Math.min(p.maxhp, p.currhp + b.pendingHeal * p.vampirism);
			b.pendingHeal = 0f;
			// Harvest explosive-rounds splash (small AoE)
			for (float[] fx : b.pendingExplosions)
				explodeAt(fx[0], fx[1], fx[2], fx[3], b.ownerSide, 13f);
			b.pendingExplosions.clear();
			// Harvest floating damage numbers
			for (float[] ft : b.pendingFloaters)
				floatingTexts.add(new float[]{ ft[0], ft[1], ft[2], 45f });
			b.pendingFloaters.clear();
			if (b.dead) {
				if (b.aoeRadius > 0 && b.didHit) explodeAt(b.x, b.y, b.aoeRadius, b.aoeDamage, b.ownerSide);
				bullets.remove(i);
			}
		}

		// Animate explosions
		for (int i = explosions.size() - 1; i >= 0; i--) {
			float[] ex = explosions.get(i);
			ex[3]--;
			if (ex[3] <= 0) { explosions.remove(i); continue; }
			float t = ex[3] / ex[4];
			noFill();
			stroke(255, 160, 30, t * 210f);
			strokeWeight(3f + (1f - t) * 4f);
			float r = ex[2] * (0.6f + (1f - t) * 0.6f);
			ellipse(ex[0], ex[1], r * 2, r * 2);
			stroke(255, 220, 80, t * 120f);
			strokeWeight(1.5f);
			ellipse(ex[0], ex[1], r * 1.4f, r * 1.4f);
			noStroke();
		}

		// ── Elite enemy aura ───────────────────────────────────────────────
		for (Entity e : entities) {
			if (!e.isElite || e.die == 1 || e == p) continue;
			float pulse = 0.8f + 0.2f * (float) Math.sin(frameCount * 0.13f);
			noFill();
			stroke(255, 200, 50, 130 * pulse); strokeWeight(4f * pulse);
			ellipse(e.x, e.y, (e.radius + 14) * 2, (e.radius + 14) * 2);
			stroke(255, 230, 100, 55); strokeWeight(2f);
			ellipse(e.x, e.y, (e.radius + 22) * 2, (e.radius + 22) * 2);
			noStroke();
		}

		// Draw world
		for (Bullet b : bullets)  b.display(this);
		for (Entity e : entities) e.display(this);
		for (Entity e : entities) if (e != p) e.displayhp(this);
		for (Drone d : p.drones)  d.display(this);

		// ── Status-effect & hit-flash overlays (drawn on top of entities) ─
		for (Entity e : entities) {
			if (e.die == 1 || e == p) continue;
			if (e.hitFlashTimer > 0) {
				float t = e.hitFlashTimer / 7f;
				noStroke(); fill(255, 80, 80, t * 190f);
				rect(e.x - e.radius, e.y - e.radius, e.radius * 2, e.radius * 2, 4);
			}
			if (e.slowTimer > 0) {
				noStroke(); fill(80, 160, 255, 70);
				rect(e.x - e.radius, e.y - e.radius, e.radius * 2, e.radius * 2, 4);
			}
			if (e.burnTimer > 0) {
				float flicker = 0.4f + 0.6f * ((frameCount % 8) / 8f);
				noStroke(); fill(255, 130, 20, flicker * 160f);
				ellipse(e.x, e.y, e.radius * 3.2f, e.radius * 3.2f);
			}
		}

		// ── Danger zones ───────────────────────────────────────────────────
		for (int i = dangerZones.size() - 1; i >= 0; i--) {
			float[] z = dangerZones.get(i);
			z[3]--;
			if (z[3] <= 0) { dangerZones.remove(i); continue; }
			float t = z[3] / z[4]; // 1→0 over lifetime
			float zr;
			boolean active;
			if      (t > 0.70f) { zr = z[2] * (1f - (t - 0.70f) / 0.30f); active = false; }
			else if (t > 0.15f) { zr = z[2]; active = true; }
			else                { zr = z[2] * (t / 0.15f);  active = false; }

			if (active) {
				float dx = p.x - z[0], dy = p.y - z[1];
				if (dx * dx + dy * dy < zr * zr) {
					p.currhp -= 4f;
					if (p.currhp <= 0) p.die = 1;
				}
				fill(255, 20, 20, 22); noStroke();
				ellipse(z[0], z[1], zr * 2, zr * 2);
			}
			float borderAlpha = active ? 200f : 110f * (t > 0.70f ? 1f : t / 0.15f);
			noFill();
			stroke(255, 30, 30, borderAlpha); strokeWeight(active ? 3f : 1.8f);
			ellipse(z[0], z[1], zr * 2, zr * 2);
			if (active) {
				stroke(255, 80, 80, borderAlpha * 0.4f); strokeWeight(1f);
				ellipse(z[0], z[1], zr * 1.6f, zr * 1.6f);
			}
			noStroke();
		}

		// Lightning arcs (chain strike visual) — colour matches the bullet that fired them
		for (int i = lightningArcs.size() - 1; i >= 0; i--) {
			float[] arc = lightningArcs.get(i);
			arc[4]--;
			if (arc[4] <= 0) { lightningArcs.remove(i); continue; }
			float t = arc[4] / 15f;
			float ar = arc[5], ag = arc[6], ab = arc[7];
			noFill();
			stroke(ar, ag, ab, t * 230f);
			strokeWeight(2.5f * t + 0.5f);
			line(arc[0], arc[1], arc[2], arc[3]);
			stroke(Math.min(255, ar + 120), Math.min(255, ag + 60), Math.min(255, ab + 60), t * 140f);
			strokeWeight(0.8f);
			line(arc[0], arc[1], arc[2], arc[3]);
			noStroke();
		}

		// Floating damage numbers
		for (int i = floatingTexts.size() - 1; i >= 0; i--) {
			float[] ft = floatingTexts.get(i);
			ft[1] -= 0.75f;
			ft[3]--;
			if (ft[3] <= 0) { floatingTexts.remove(i); continue; }
			float alpha = Math.min(1f, ft[3] / 15f) * 225f;
			textAlign(CENTER, CENTER);
			fill(255, 70, 50, alpha);
			textSize(10f + (1f - ft[3] / 45f) * 4f);
			text("-" + (int) ft[2], ft[0], ft[1]);
		}
		noStroke(); strokeWeight(1);

		popMatrix();

		// ── Screen-space rendering ─────────────────────────────────────────
		drawCrosshair();
		if (waveNotifyTimer > 0) {
			waveNotifyTimer--;
			float a = Math.min(waveNotifyTimer, 30) / 30f * 255f;
			textAlign(CENTER, CENTER); fill(200, 140, 255, a);
			textSize(30); text("WAVE " + waveNumber, width / 2f, height / 2f - 60);
		}
		if (waveEventTimer > 0) {
			waveEventTimer--;
			float a = Math.min(waveEventTimer, 30) / 30f * 255f;
			textAlign(CENTER, CENTER); fill(255, 80, 60, a);
			textSize(26); text(waveEventText, width / 2f, height / 2f - 28);
		}
		// Kill-streak display
		if (killStreak >= 3) {
			float sa = Math.min(killStreakTimer, 20) / 20f;
			textAlign(CENTER, CENTER); fill(255, 215, 50, sa * 220f);
			textSize(20); text("×" + String.format("%.1f", xpMultiplier) + " STREAK!", width / 2f, height / 2f + 24);
		}
		drawHUD();

		// ── Death / level-up checks ────────────────────────────────────────
		if (p.die == 1) {
			saveHighScore();
			gameState = STATE_GAME_OVER; cursor(); return;
		}

		for (int i = entities.size() - 1; i >= 0; i--) {
			Entity e = entities.get(i);
			if (e.die == 1 && e != p) {
				if (e.enemy < 0) {
					kills++;
					killStreak++;
					killStreakTimer = 90;
					xpMultiplier = Math.min(3f, 1f + killStreak * 0.2f);
					p.gainXP(e.xpValue * xpMultiplier);
					// Elite guaranteed loot
					if (e.isElite) {
						entities.add(new HealthPack(e.x, e.y));
						GunPickup egp = makeRandomGunDrop(e.x + 24, e.y);
						if (egp != null) entities.add(egp);
					}
				} else if (e instanceof Crate) {
					Crate c   = (Crate) e;
					long  key = crateKey(c.tileX, c.tileY);
					destroyedCrates.add(key);
					activeCrateMap.remove(key);
					handleCrateDrop(c.x, c.y);
				}
				entities.remove(i);
			}
		}

		if (p.leveledUp) {
			p.leveledUp = false;
			savedCurrgun = p.currgun; // preserve before overlay (key-repeat can change it)
			setupLevelUpChoices();
			gameState = STATE_LEVELUP;
			cursor();
		}
	}

	// ── Spawn system ───────────────────────────────────────────────────────
	private void handleSpawning() {
		waveTimer++;
		if (waveTimer >= waveDuration()) {
			waveTimer = 0;
			waveNumber++;
			spawnRate = Math.max(55, 160 - (waveNumber - 1) * 9);
			waveNotifyTimer = 120;
			if (waveNumber % 4 == 0) spawnEnemy(true); // Devil every 4th wave
			// Random wave event (35 % chance from wave 3 onwards)
			if (waveNumber >= 3 && random(1f) < 0.35f) triggerWaveEvent();
		}
		// Spawn danger zone every ~40 s from wave 5 onwards
		if (waveNumber >= 5 && gameTime % 2400 == 0) {
			float ang = random(TWO_PI), dist = 110 + random(110);
			dangerZones.add(new float[]{
				p.x + cos(ang) * dist, p.y + sin(ang) * dist,
				150f, 540f, 540f
			});
		}

		spawnTimer--;
		if (spawnTimer <= 0) {
			spawnTimer = spawnRate;
			// Spawn count: 1 until wave 6, then +1 per 6 waves, capped at 3
			int count = Math.min(3, 1 + (waveNumber - 1) / 6);
			for (int i = 0; i < count; i++) spawnEnemy(false);
		}
	}

	private void spawnEnemy(boolean boss) {
		float angle = random(TWO_PI);
		float dist  = 370 + random(80);
		float x     = p.x + cos(angle) * dist;
		float y     = p.y + sin(angle) * dist;
		while (WorldMap.isWallAt(x, y)) { x += cos(angle) * WorldMap.TS; y += sin(angle) * WorldMap.TS; }

		if (boss) {
			Devil dv = new Devil(x, y, waveNumber);
			dv.xpValue = 250;
			entities.add(dv);
			return;
		}
		float roll = random(1f);
		if (waveNumber >= 8 && roll < 0.10f) {
			// Brute — slow tank (unlocks wave 8, was 6)
			Brute br = new Brute(x, y, waveNumber);
			br.xpValue = 80;
			entities.add(br);
		} else if (waveNumber >= 10 && roll < 0.17f) {
			// Marksman — long-range sniper (unlocks wave 10, was 7); reduced scaling 0.08→0.06
			Marksman mk = new Marksman(x, y, waveNumber);
			mk.xpValue = 65;
			int mkDmg = (int)(300 * (1 + (waveNumber - 1) * 0.06f));
			mk.guns.add(new Gun(mk, this, "MkShot", mkDmg, 4, 120, 150, 14f, 520f, 1, 0f, true, 255, 60, 60));
			entities.add(mk);
		} else if (waveNumber >= 6 && roll < 0.27f) {
			// Gunner — rapid-fire burster (unlocks wave 6, was 5); reduced scaling 0.09→0.07
			Gunner gn = new Gunner(x, y, waveNumber);
			gn.xpValue = 45;
			int gnDmg = (int)(55 * (1 + (waveNumber - 1) * 0.07f));
			gn.guns.add(new Gun(gn, this, "GnShot", gnDmg, 16, 50, 18, 9f, 260f, 1, 0.04f, false, 255, 200, 50));
			entities.add(gn);
		} else if (waveNumber >= 4 && roll < 0.37f) {
			// Shooter — balanced mid-range; reduced scaling 0.10→0.08
			Shooter sh = new Shooter(x, y, waveNumber);
			sh.xpValue = 40;
			int shDmg = (int)(110 * (1 + (waveNumber - 1) * 0.08f));
			sh.guns.add(new Gun(sh, this, "ShShot", shDmg, 8, 60, 100, 8f, 380f, 1, 0f, false, 80, 200, 255));
			entities.add(sh);
		} else if (waveNumber >= 3 && roll < 0.52f) {
			entities.add(new Skeleton(x, y, waveNumber));
		} else {
			entities.add(new Zombie(x, y, waveNumber));
		}
		// 8 % chance to make the just-added enemy an elite (from wave 3)
		if (waveNumber >= 3 && random(1f) < 0.08f && !entities.isEmpty()) {
			Entity last = entities.get(entities.size() - 1);
			if (last.enemy < 0) makeElite(last);
		}
	}

	// ── Crate management (procedural tile-based) ──────────────────────────

	private static long crateKey(int tx, int ty) {
		return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
	}

	/** Scans the ring just beyond the screen edge for crate tiles, spawning off-screen only. */
	private void manageCrates() {
		if (gameTime % 90 != 0) return; // re-scan every 1.5 s

		// Half-screen in tiles + 1 tile buffer so crates never pop into view
		float halfW = width  / 2f + WorldMap.TS;
		float halfH = height / 2f + WorldMap.TS;

		int r   = 13; // outer scan radius in tiles (just beyond the screen edge)
		int cx0 = (int) Math.floor((camX - r * WorldMap.TS) / WorldMap.TS);
		int cx1 = (int) Math.ceil ((camX + r * WorldMap.TS) / WorldMap.TS);
		int cy0 = (int) Math.floor((camY - r * WorldMap.TS) / WorldMap.TS);
		int cy1 = (int) Math.ceil ((camY + r * WorldMap.TS) / WorldMap.TS);

		for (int ty = cy0; ty <= cy1; ty++) {
			for (int tx = cx0; tx <= cx1; tx++) {
				float wx = tx * WorldMap.TS + WorldMap.TS / 2f;
				float wy = ty * WorldMap.TS + WorldMap.TS / 2f;
				// Only spawn crates that are currently off-screen
				if (Math.abs(wx - camX) < halfW && Math.abs(wy - camY) < halfH) continue;
				if (!WorldMap.isCrate(tx, ty)) continue;
				long key = crateKey(tx, ty);
				if (destroyedCrates.contains(key) || activeCrateMap.containsKey(key)) continue;
				Crate c = new Crate(wx, wy, tx, ty);
				activeCrateMap.put(key, c);
				entities.add(c);
			}
		}
	}

	// ── Crate drop & pickup helpers ────────────────────────────────────────

	private void handleCrateDrop(float x, float y) {
		float roll = random(1f);
		if      (roll < 0.15f) entities.add(new HealthPack(x, y));   // 15 % health
		else if (roll < 0.22f) {                                       //  7 % gun
			GunPickup gp = makeRandomGunDrop(x, y);
			if (gp != null) entities.add(gp);
		}
		// 78 % → nothing
	}

	private GunPickup makeRandomGunDrop(float x, float y) {
		List<String> available = new ArrayList<>();
		if (!hasGunByName("Shotgun")) available.add("Shotgun");
		if (!hasGunByName("Sniper"))  available.add("Sniper");
		if (!hasGunByName("Minigun")) available.add("Minigun");
		if (!hasGunByName("SMG"))     available.add("SMG");
		if (!hasGunByName("Rocket"))  available.add("Rocket");
		if (available.isEmpty()) return null;
		return new GunPickup(x, y, available.get((int) random(available.size())));
	}

	private boolean hasGunByName(String name) {
		for (Gun g : p.guns) if (name.equals(g.name)) return true;
		return false;
	}

	private void addGunByName(String name) {
		Gun newGun = null;
		switch (name) {
		case "Shotgun": newGun = new Shotgun(p, this); break;
		case "Sniper":  newGun = new Sniper(p, this);  break;
		case "Minigun": newGun = new Minigun(p, this); break;
		case "SMG":     newGun = new SMG(p, this);     break;
		case "Rocket":  newGun = new Rocket(p, this);  break;
		}
		if (newGun == null) return;
		// Inherit all active bullet modifiers from existing guns
		if (!p.guns.isEmpty()) {
			Gun ref = p.guns.get(0);
			newGun.bulletBounces    = ref.bulletBounces;
			newGun.bulletChains     = ref.bulletChains;
			newGun.explosiveRounds  = ref.explosiveRounds;
			newGun.explosiveAoeR    = ref.explosiveAoeR;
			newGun.bulletSlowFrames = ref.bulletSlowFrames;
			newGun.bulletBurnFrames = ref.bulletBurnFrames;
			newGun.bulletBurnDmg    = newGun.baseDamage * 0.04f * (ref.bulletBurnDmg > 0 ? 1 : 0);
			// Multishot: apply the same 1.5× multiplier if it's been taken
			if (takenUpgrades.contains(Upgrade.Type.MULTISHOT.name())) {
				newGun.pellets = Math.max(newGun.pellets + 1, (int) Math.round(newGun.pellets * 1.5f));
				if (newGun.spread == 0f) newGun.spread = 0.09f;
				else newGun.spread = Math.min(newGun.spread + 0.04f, 0.50f);
			}
		}
		p.guns.add(newGun);
	}

	/** Collects any HealthPack / GunPickup the player is touching. */
	private void collectPickups() {
		for (int i = entities.size() - 1; i >= 0; i--) {
			Entity e = entities.get(i);
			if (!(e instanceof HealthPack) && !(e instanceof GunPickup)) continue;
			float dx = p.x - e.x, dy = p.y - e.y;
			float cr = p.radius + e.radius + 6;
			if (dx * dx + dy * dy < cr * cr) {
				if (e instanceof HealthPack)
					p.currhp = Math.min(p.currhp + HealthPack.HEAL_AMOUNT, p.maxhp);
				else
					addGunByName(((GunPickup) e).gunName);
				entities.remove(i);
			}
		}
	}

	// ── High score I/O ────────────────────────────────────────────────────
	int[] loadHighScore() {
		try {
			File f = new File(sketchPath("boxhead_score.dat"));
			if (!f.exists()) return new int[]{0,0,0,0};
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line = br.readLine(); br.close();
			if (line == null) return new int[]{0,0,0,0};
			String[] pts = line.split(",");
			return new int[]{Integer.parseInt(pts[0]), Integer.parseInt(pts[1]),
			                 Integer.parseInt(pts[2]), Integer.parseInt(pts[3])};
		} catch (Exception e) { return new int[]{0,0,0,0}; }
	}

	void saveHighScore() {
		if (p == null) return;
		int secs = gameTime / 60;
		if (kills <= highScore[0] && waveNumber <= highScore[1]) return; // no improvement
		highScore = new int[]{kills, waveNumber, secs, p.level};
		try {
			PrintWriter pw = new PrintWriter(new FileWriter(sketchPath("boxhead_score.dat")));
			pw.println(kills + "," + waveNumber + "," + secs + "," + p.level);
			pw.close();
		} catch (Exception e) { /* ignore */ }
	}

	// ── Elite enemy modifier ───────────────────────────────────────────────
	private void makeElite(Entity e) {
		e.isElite   = true;
		e.maxhp     = (int)(e.maxhp * 2.2f);
		e.currhp    = e.maxhp;
		e.damage    *= 1.5f;
		e.speed     *= 1.15f;
		e.xpValue   = (int)(e.xpValue * 2.5f);
	}

	// ── Wave random event ──────────────────────────────────────────────────
	private void triggerWaveEvent() {
		int roll = (int) random(3);
		switch (roll) {
		case 0: // Boss rush
			spawnEnemy(true); spawnEnemy(true);
			waveEventText = "BOSS RUSH!";
			break;
		case 1: // Supply drop near player
			float ang = random(TWO_PI), d = 80 + random(80);
			entities.add(new HealthPack(p.x + cos(ang)*d, p.y + sin(ang)*d));
			GunPickup gp = makeRandomGunDrop(p.x - cos(ang)*d, p.y - sin(ang)*d);
			if (gp != null) entities.add(gp);
			waveEventText = "SUPPLY DROP!";
			break;
		case 2: // Horde
			for (int i = 0; i < 6; i++) spawnEnemy(false);
			waveEventText = "ENEMY HORDE!";
			break;
		}
		waveEventTimer = 180;
	}

	// ── Allied gun sync ────────────────────────────────────────────────────

	/** Copy bullet colour + modifiers from the player's active gun to all allied guns. */
	private void syncAlliedGuns() {
		if (p == null || p.guns.isEmpty()) return;
		Gun src = p.guns.get(p.currgun);
		for (Drone d : p.drones)         applyGunStyle(d.gun, src);
		for (Entity e : entities) {
			if (!(e instanceof Turret) || e.guns.isEmpty()) continue;
			applyGunStyle(e.guns.get(0), src);
		}
	}

	private void applyGunStyle(Gun t, Gun src) {
		t.bulletR          = src.bulletR;
		t.bulletG          = src.bulletG;
		t.bulletB          = src.bulletB;
		t.pellets          = src.pellets;
		t.spread           = src.spread;
		t.bulletSpeed      = src.bulletSpeed;
		t.bulletBounces    = src.bulletBounces;
		t.bulletChains     = src.bulletChains;
		t.explosiveRounds  = src.explosiveRounds;
		t.explosiveAoeR    = src.explosiveAoeR;
		t.bulletSlowFrames = src.bulletSlowFrames;
		t.bulletBurnFrames = src.bulletBurnFrames;
		t.bulletBurnDmg    = src.bulletBurnDmg;
		t.aoeRadius        = src.aoeRadius;
		t.aoeDamage        = src.aoeDamage;
		t.penetrating      = src.penetrating;
	}

	// ── AoE explosion ──────────────────────────────────────────────────────
	private void explodeAt(float x, float y, float radius, float dmg, float ownerSide) {
		explodeAt(x, y, radius, dmg, ownerSide, 28f);
	}

	private void explodeAt(float x, float y, float radius, float dmg, float ownerSide, float timer) {
		for (Entity e : entities) {
			if (e.die == 1) continue;
			if (e.enemy == 2) continue;                  // never blast pickups
			if (ownerSide > 0 && e.enemy > 0) continue;
			if (ownerSide < 0 && e.enemy < 0) continue;
			float dx = e.x - x, dy = e.y - y;
			if (dx * dx + dy * dy < radius * radius) {
				e.currhp -= dmg;
				if (e.currhp <= 0) e.die = 1;
			}
		}
		explosions.add(new float[]{ x, y, radius, timer, timer });
	}

	// ── Level-up ───────────────────────────────────────────────────────────

	/** Returns true when this upgrade type hasn't been taken yet this run. */
	private boolean notTaken(Upgrade.Type t) {
		return !takenUpgrades.contains(t.name());
	}

	private void setupLevelUpChoices() {
		List<Upgrade> pool = new ArrayList<>();

		// Core stat upgrades — always re-pooled (intentionally stackable)
		for (Upgrade u : Upgrade.STAT_UPGRADES) pool.add(u);

		// Per-gun level-up — always re-pooled (each gun tracks its own progress)
		for (int i = 0; i < p.guns.size(); i++) {
			Gun g        = p.guns.get(i);
			int nextLv   = g.level + 1;
			int dmgGain  = (int)(g.baseDamage * 0.20f);
			int ammoGain = Math.max(1, (int)(g.baseBull * 0.15f));
			Upgrade gu   = new Upgrade(Upgrade.Type.UPGRADE_GUN,
				g.name + " Lv." + nextLv,
				"+" + dmgGain + " dmg · faster fire & reload · +" + ammoGain + " ammo",
				g.bulletR, g.bulletG, g.bulletB);
			gu.targetIndex = i;
			// Skip if this gun's slot was already upgraded this run
			if (!takenUpgrades.contains("UPGRADE_GUN_" + i)) pool.add(gu);
		}

		// ── Defensive items ────────────────────────────────────────────────────

		// Shield
		if (p.shieldLevel == 0 && notTaken(Upgrade.Type.SHIELD)) {
			pool.add(Upgrade.SHIELD_UNLOCK);
		} else if (p.shieldLevel > 0) {
			if (notTaken(Upgrade.Type.UPGRADE_SHIELD))
				pool.add(new Upgrade(Upgrade.Type.UPGRADE_SHIELD, "Force Field Lv." + (p.shieldLevel + 1),
					"+300 max shield", 55, 130, 255));
			if (notTaken(Upgrade.Type.UPGRADE_SHIELD_RECHARGE))
				pool.add(new Upgrade(Upgrade.Type.UPGRADE_SHIELD_RECHARGE, "Quick Charge",
					"Recharge starts 40 frames sooner", 100, 175, 255));
		}

		// Drones
		if (p.drones.isEmpty() && notTaken(Upgrade.Type.DRONE)) {
			pool.add(Upgrade.DRONE_UNLOCK);
		} else if (!p.drones.isEmpty()) {
			if (p.drones.size() < 4 && notTaken(Upgrade.Type.UPGRADE_DRONE))
				pool.add(new Upgrade(Upgrade.Type.UPGRADE_DRONE, "Fleet Lv." + (p.droneLevel + 1),
					"+1 drone · +20% drone dmg", 40, 190, 185));
			if (notTaken(Upgrade.Type.UPGRADE_DRONE_POWER))
				pool.add(new Upgrade(Upgrade.Type.UPGRADE_DRONE_POWER, "Drone Overclocked",
					"+30% drone dmg · +25% fire rate", 40, 220, 200));
		}

		// Knives
		if (p.knifeCount == 0 && notTaken(Upgrade.Type.KNIVES)) {
			pool.add(Upgrade.KNIVES_UNLOCK);
		} else if (p.knifeCount > 0) {
			if (notTaken(Upgrade.Type.UPGRADE_KNIVES))
				pool.add(new Upgrade(Upgrade.Type.UPGRADE_KNIVES, "Blades Lv." + (p.knifeLevel + 1),
					"+1 blade · +25% blade dmg", 230, 215, 60));
			if (notTaken(Upgrade.Type.UPGRADE_KNIVES_ORBIT))
				pool.add(new Upgrade(Upgrade.Type.UPGRADE_KNIVES_ORBIT, "Wide Orbit",
					"+12px reach · faster spin", 200, 230, 60));
		}

		// Aura
		if (p.auraLevel == 0 && notTaken(Upgrade.Type.AURA)) {
			pool.add(Upgrade.AURA_UNLOCK);
		} else if (p.auraLevel > 0 && notTaken(Upgrade.Type.UPGRADE_AURA)) {
			pool.add(new Upgrade(Upgrade.Type.UPGRADE_AURA, "Aura Lv." + (p.auraLevel + 1),
				"+2 dmg/frame · +15px radius", 255, 165, 30));
		}

		// Pulse
		if (p.pulseLevel == 0 && notTaken(Upgrade.Type.PULSE)) {
			pool.add(Upgrade.PULSE_UNLOCK);
		} else if (p.pulseLevel > 0 && notTaken(Upgrade.Type.UPGRADE_PULSE)) {
			pool.add(new Upgrade(Upgrade.Type.UPGRADE_PULSE, "Shockwave Lv." + (p.pulseLevel + 1),
				"+200 dmg · +40px radius · shorter cooldown", 200, 100, 255));
		}

		// Magnet
		if (p.magnetLevel == 0 && notTaken(Upgrade.Type.MAGNET)) {
			pool.add(Upgrade.MAGNET_UNLOCK);
		} else if (p.magnetLevel > 0 && notTaken(Upgrade.Type.UPGRADE_MAGNET)) {
			pool.add(new Upgrade(Upgrade.Type.UPGRADE_MAGNET, "Magnet Lv." + (p.magnetLevel + 1),
				"+90px attraction range", 80, 230, 215));
		}

		// ── Passive traits ────────────────────────────────────────────────────
		if (p.vampirism == 0f && notTaken(Upgrade.Type.VAMPIRISM)) pool.add(Upgrade.VAMPIRISM_OPT);
		if (p.thorns    == 0f && notTaken(Upgrade.Type.THORNS))    pool.add(Upgrade.THORNS_OPT);

		// ── Turret ────────────────────────────────────────────────────────────
		long turretCount = entities.stream().filter(e -> e instanceof Turret).count();
		if (p.turretLevel == 0 && notTaken(Upgrade.Type.TURRET)) {
			pool.add(Upgrade.TURRET_UNLOCK);
		} else if (p.turretLevel > 0 && turretCount < p.turretLevel + 1
		           && notTaken(Upgrade.Type.UPGRADE_TURRET)) {
			pool.add(Upgrade.TURRET_UP_OPT);
		}

		// ── Bullet modifiers ──────────────────────────────────────────────────
		int ricochetLvl = p.guns.isEmpty() ? 0 : p.guns.get(0).bulletBounces;
		if (ricochetLvl == 0 && notTaken(Upgrade.Type.RICOCHET))
			pool.add(Upgrade.RICOCHET_UNLOCK);
		else if (ricochetLvl > 0 && ricochetLvl < 3 && notTaken(Upgrade.Type.RICOCHET_UP))
			pool.add(Upgrade.RICOCHET_UP_OPT);

		int chainLvl = p.guns.isEmpty() ? 0 : p.guns.get(0).bulletChains;
		if (chainLvl == 0 && notTaken(Upgrade.Type.CHAIN))
			pool.add(Upgrade.CHAIN_UNLOCK);
		else if (chainLvl > 0 && chainLvl < 4 && notTaken(Upgrade.Type.CHAIN_UP))
			pool.add(Upgrade.CHAIN_UP_OPT);

		if (!p.guns.isEmpty() && notTaken(Upgrade.Type.EXPLOSIVE_ROUNDS))
			pool.add(Upgrade.EXPLOSIVE_ROUNDS_OPT);
		if (!p.guns.isEmpty() && notTaken(Upgrade.Type.SLOW_ROUNDS))
			pool.add(Upgrade.SLOW_ROUNDS_OPT);
		if (!p.guns.isEmpty() && notTaken(Upgrade.Type.INCENDIARY))
			pool.add(Upgrade.INCENDIARY_OPT);

		// Fisher-Yates shuffle then take 4
		for (int i = pool.size() - 1; i > 0; i--) {
			int j = (int) random(i + 1);
			Upgrade tmp = pool.get(i); pool.set(i, pool.get(j)); pool.set(j, tmp);
		}
		currentUpgrades = pool.subList(0, Math.min(4, pool.size())).toArray(new Upgrade[0]);
	}

	/** Adds a gun-unlock card if not owned, or a gun-upgrade card for that gun if owned. */


	private void applyUpgrade(Upgrade u) {
		// Mark taken — UPGRADE_GUN is keyed by gun index so each gun tracks separately.
		String takenKey = (u.type == Upgrade.Type.UPGRADE_GUN)
		                  ? "UPGRADE_GUN_" + u.targetIndex : u.type.name();
		takenUpgrades.add(takenKey);
		switch (u.type) {
		// ── Global stat boosts ──────────────────────────────────────────────
		case DAMAGE_UP:
			for (Gun g : p.guns) g.damage *= 1.3f;
			break;
		case FIRE_RATE_UP:
			for (Gun g : p.guns) g.autoFireRate = Math.max(5, (int)(g.autoFireRate * 0.75f));
			break;
		case RELOAD_SPEED:
			for (Gun g : p.guns) g.reloadTime = Math.max(20, (int)(g.reloadTime * 0.75f));
			break;
		case SPEED_UP:  p.speed *= 1.2f; break;
		case MAX_HP_UP: p.maxhp += 300; p.currhp = Math.min(p.currhp + 300, p.maxhp); break;
		case HEAL:      p.currhp = Math.min(p.currhp + 500, p.maxhp); break;
		case MULTISHOT:
			for (Gun mg : p.guns) {
				mg.pellets = Math.max(mg.pellets + 1, (int) Math.round(mg.pellets * 1.5f));
				if (mg.spread == 0f) mg.spread = 0.09f;
				else mg.spread = Math.min(mg.spread + 0.04f, 0.50f);
			}
			break;
		// ── Per-gun level-up ────────────────────────────────────────────────
		case UPGRADE_GUN:
			Gun tg = p.guns.get(u.targetIndex);
			tg.level++;
			tg.damage      += tg.baseDamage    * 0.20f;
			tg.autoFireRate = Math.max(5, tg.autoFireRate - (int)(tg.baseFireRate   * 0.08f));
			tg.reloadTime   = Math.max(20, tg.reloadTime  - (int)(tg.baseReloadTime * 0.07f));
			int ab = Math.max(1, (int)(tg.baseBull * 0.15f));
			tg.maxbull  += ab;
			tg.currbull  = Math.min(tg.currbull + ab, tg.maxbull);
			break;
		// ── Gun unlocks ─────────────────────────────────────────────────────
		case GET_SHOTGUN: p.guns.add(new Shotgun(p, this)); break;
		case GET_SNIPER:  p.guns.add(new Sniper(p, this));  break;
		case GET_MINIGUN: p.guns.add(new Minigun(p, this)); break;
		case GET_SMG:     p.guns.add(new SMG(p, this));     break;
		case GET_ROCKET:  p.guns.add(new Rocket(p, this));  break;
		// ── Defensive: unlock ───────────────────────────────────────────────
		case SHIELD:
			p.shieldLevel++;
			p.shieldMax += 300;
			p.shieldHP = Math.min(p.shieldHP + 300, p.shieldMax);
			break;
		case DRONE:
			p.droneLevel++;
			if (p.drones.size() < 4) {
				float a0 = p.drones.isEmpty() ? 0f
					: p.drones.get(p.drones.size()-1).orbitAngle + (float)(Math.PI * 2 / (p.drones.size()+1));
				p.drones.add(new Drone(p, a0, this));
			}
			break;
		case KNIVES:
			p.knifeLevel++;
			p.knifeCount = Math.min(p.knifeCount + 2, 8);
			break;
		// ── Defensive: level-up ─────────────────────────────────────────────
		// ── Defensive: level-ups (primary) ─────────────────────────────────
		case UPGRADE_SHIELD:
			p.shieldLevel++;
			p.shieldMax += 300;
			p.shieldHP   = Math.min(p.shieldHP + 300, p.shieldMax);
			break;
		case UPGRADE_SHIELD_RECHARGE:
			p.shieldLevel++;
			p.shieldHitDelay = Math.max(60, p.shieldHitDelay - 40);
			break;
		case UPGRADE_DRONE:
			p.droneLevel++;
			if (p.drones.size() < 4) {
				float a1 = p.drones.get(p.drones.size()-1).orbitAngle + (float)(Math.PI * 2 / (p.drones.size()+1));
				p.drones.add(new Drone(p, a1, this));
			}
			for (Drone d : p.drones) {
				d.gun.damage      += d.gun.baseDamage * 0.20f;
				d.gun.autoFireRate = Math.max(5, d.gun.autoFireRate - (int)(d.gun.baseFireRate * 0.08f));
			}
			break;
		case UPGRADE_DRONE_POWER:
			p.droneLevel++;
			for (Drone d : p.drones) {
				d.gun.damage      += d.gun.baseDamage * 0.30f;
				d.gun.autoFireRate = Math.max(5, (int)(d.gun.autoFireRate * 0.75f));
			}
			break;
		case UPGRADE_KNIVES:
			p.knifeLevel++;
			p.knifeCount   = Math.min(p.knifeCount + 1, 8);
			p.knifeDamage  += p.knifeDamage * 0.25f;
			break;
		case UPGRADE_KNIVES_ORBIT:
			p.knifeLevel++;
			p.knifeOrbitRadius = Math.min(120f, p.knifeOrbitRadius + 12f);
			p.knifeRotSpeed    = Math.min(0.15f, p.knifeRotSpeed + 0.01f);
			break;
		// ── New defense items — unlock ──────────────────────────────────────
		case AURA:
			p.auraLevel++;
			p.auraDamage = 2f;
			p.auraRadius = 75f;
			break;
		case PULSE:
			p.pulseLevel++;
			p.pulseDamage      = 400f;
			p.pulseMaxRadius   = 185f;
			p.pulseMaxCooldown = 300;
			p.pulseCooldown    = 60; // brief warm-up before first pulse
			break;
		case MAGNET:
			p.magnetLevel++;
			p.magnetRadius = 165f;
			break;
		// ── New defense items — level-up ────────────────────────────────────
		case UPGRADE_AURA:
			p.auraLevel++;
			p.auraDamage += 2f;
			p.auraRadius  = Math.min(160f, p.auraRadius + 15f);
			break;
		case UPGRADE_PULSE:
			p.pulseLevel++;
			p.pulseDamage     += 200f;
			p.pulseMaxRadius   = Math.min(350f, p.pulseMaxRadius + 40f);
			p.pulseMaxCooldown = Math.max(120, p.pulseMaxCooldown - 30);
			break;
		case UPGRADE_MAGNET:
			p.magnetLevel++;
			p.magnetRadius = Math.min(450f, p.magnetRadius + 90f);
			break;
		// ── Bullet modifiers ────────────────────────────────────────────────
		case RICOCHET:
		case RICOCHET_UP:
			for (Gun g : p.guns) g.bulletBounces = Math.min(g.bulletBounces + 1, 3);
			break;
		case CHAIN:
		case CHAIN_UP:
			for (Gun g : p.guns) g.bulletChains = Math.min(g.bulletChains + 1, 4);
			break;
		case EXPLOSIVE_ROUNDS:
			for (Gun g : p.guns) g.explosiveRounds = true;
			break;
		case SLOW_ROUNDS:
			for (Gun g : p.guns) g.bulletSlowFrames = 120;
			break;
		case INCENDIARY:
			for (Gun g : p.guns) {
				g.bulletBurnFrames = 180;
				g.bulletBurnDmg    = g.baseDamage * 0.04f;
			}
			break;
		// ── Passive traits ───────────────────────────────────────────────────
		case VAMPIRISM:
			p.vampirism = Math.min(0.30f, p.vampirism + 0.08f);
			break;
		case THORNS:
			p.thorns = Math.min(1.0f, p.thorns + 0.40f);
			break;
		// ── Turret ───────────────────────────────────────────────────────────
		case TURRET:
			p.turretLevel = 1;
			entities.add(new Turret(p.x, p.y, this));
			break;
		case UPGRADE_TURRET:
			p.turretLevel++;
			// Place a new turret and boost all existing turrets
			entities.add(new Turret(p.x, p.y, this));
			for (Entity e : entities) {
				if (!(e instanceof Turret)) continue;
				if (!e.guns.isEmpty()) e.guns.get(0).damage *= 1.30f;
			}
			break;
		}
	}

	private void drawLevelUp() {
		// Draw frozen world in background
		background(35, 32, 42);
		pushMatrix(); translate(width / 2f - camX, height / 2f - camY);
		WorldMap.display(this, camX, camY);
		for (Bullet b : bullets)  b.display(this);
		for (Entity e : entities) e.display(this);
		for (Entity e : entities) if (e != p) e.displayhp(this);
		popMatrix();
		drawHUD();

		noStroke(); fill(0, 0, 0, 195); rect(0, 0, width, height);
		textAlign(CENTER, CENTER);
		fill(255, 215, 50); textSize(36); text("LEVEL UP!", width / 2f, 62);
		fill(185, 185, 185); textSize(14);
		text("Level " + p.level + "  —  Choose an upgrade:", width / 2f, 98);

		// 2 × 2 grid: 265 × 148 cards, 10 px gap
		float cardW = 265f, cardH = 148f, cardGap = 10f;
		float startX = (width - (2 * cardW + cardGap)) / 2f;
		float startY = 118f;
		for (int i = 0; i < currentUpgrades.length; i++) {
			int col = i % 2, row = i / 2;
			float cx = startX + col * (cardW + cardGap) + cardW / 2f;
			float cy = startY + row * (cardH + cardGap) + cardH / 2f;
			drawUpgradeCard(currentUpgrades[i], cx, cy, cardW, cardH, i);
		}
		textAlign(CENTER, CENTER); fill(105, 105, 105); textSize(11);
		text("Press  1 · 2 · 3 · 4  or click to choose", width / 2f, 456);
	}

	private void drawUpgradeCard(Upgrade u, float cx, float cy, float w, float h, int idx) {
		boolean hov = mouseX >= cx - w / 2 && mouseX <= cx + w / 2
		           && mouseY >= cy - h / 2 && mouseY <= cy + h / 2;

		// Shadow
		noStroke(); fill(0, 0, 0, 100);
		rect(cx - w / 2 + 2, cy - h / 2 + 3, w, h, 10);

		// Card body — darkened accent colour, lighter on hover
		int bgR = Math.min(255, Math.max(u.cr - 55, 0) + (hov ? 18 : 0));
		int bgG = Math.min(255, Math.max(u.cg - 55, 0) + (hov ? 18 : 0));
		int bgB = Math.min(255, Math.max(u.cb - 55, 0) + (hov ? 18 : 0));
		fill(bgR, bgG, bgB); rect(cx - w / 2, cy - h / 2, w, h, 10);

		// Left accent band (full height)
		float bandW = 54f;
		fill(u.cr, u.cg, u.cb);
		rect(cx - w / 2,          cy - h / 2, bandW,      h, 10);
		rect(cx - w / 2 + bandW - 10, cy - h / 2, 10, h);   // square off right edge

		// Number badge
		fill(0, 0, 0, 55); ellipse(cx - w / 2 + bandW / 2f, cy, 34, 34);
		fill(255); textSize(17); textAlign(CENTER, CENTER);
		text(idx + 1, cx - w / 2 + bandW / 2f, cy);

		// Text area bounds
		float tx = cx - w / 2 + bandW + 10;  // left edge
		float tr = cx + w / 2 - 10;          // right edge

		// Name — Processing text-box wraps automatically
		textAlign(LEFT, TOP); fill(255); textSize(13f);
		text(u.name, tx, cy - h / 2f + 12, tr, cy - h / 2f + 12 + h * 0.34f);

		// Thin separator line
		float divY = cy - h / 2f + h * 0.42f;
		stroke(u.cr, u.cg, u.cb, 120); strokeWeight(1f);
		line(tx, divY, tr, divY); noStroke();

		// Description — wrapped text box
		fill(185, 185, 185); textSize(10.5f);
		text(u.desc, tx, divY + 7, tr, cy + h / 2f - 10);

		// Hover border glow + key hint
		if (hov) {
			noFill(); stroke(255, 240, 80, 210); strokeWeight(1.8f);
			rect(cx - w / 2, cy - h / 2, w, h, 10); noStroke();
			fill(255, 240, 80); textSize(9f); textAlign(CENTER, CENTER);
			text("PRESS " + (idx + 1), cx - w / 2 + bandW / 2f, cy + h / 2f - 11);
		}
		// Always restore so callers don't inherit LEFT,TOP alignment
		noStroke(); strokeWeight(1); textAlign(CENTER, CENTER);
	}

	// ── Character select ───────────────────────────────────────────────────
	private void drawCharacterSelect() {
		background(35, 32, 42);
		pushMatrix(); translate(width / 2f, height / 2f); WorldMap.display(this, 0, 0); popMatrix();
		noStroke(); fill(0, 0, 0, 175); rect(0, 0, width, height);

		textAlign(CENTER, CENTER);
		fill(255, 215, 50); textSize(34); text("CHOOSE YOUR CLASS", width / 2f, 64);
		fill(170, 168, 190); textSize(13); text("Your choice shapes every run", width / 2f, 100);

		String[] names  = { "Soldier",         "Scout",            "Engineer"         };
		String[] icons  = { "S",               "SC",               "E"                };
		String[] taglines = { "Guns blazing",    "Speed & agility",  "Tech superiority" };
		String[] descs  = {
			"+25% gun damage\nStarts with Shotgun",
			"+35% move speed\nStarts with SMG",
			"Starts with Combat Drone\nExtra drone slot"
		};
		int[][]  cols   = { {210,80,35}, {60,195,90}, {40,190,185} };

		float cw = 172f, ch = 230f, cgap = 12f;
		float csx = (width - (3 * cw + 2 * cgap)) / 2f;
		for (int i = 0; i < 3; i++) {
			float ccx = csx + i * (cw + cgap) + cw / 2f;
			float ccy = 320f;
			boolean hov = inButton(mouseX, mouseY, ccx, ccy, cw, ch);
			int cr = cols[i][0], cg = cols[i][1], cb = cols[i][2];

			// Shadow + body
			noStroke(); fill(0, 0, 0, 100);
			rect(ccx - cw/2 + 2, ccy - ch/2 + 3, cw, ch, 12);
			fill(Math.max(cr-55,0)+(hov?18:0), Math.max(cg-55,0)+(hov?18:0), Math.max(cb-55,0)+(hov?18:0));
			rect(ccx - cw/2, ccy - ch/2, cw, ch, 12);

			// Coloured top band
			fill(cr, cg, cb);
			rect(ccx - cw/2, ccy - ch/2, cw, 80, 12);
			rect(ccx - cw/2, ccy - ch/2 + 66, cw, 14);

			// Icon letter
			fill(255, 255, 255, 45); ellipse(ccx, ccy - ch/2 + 40, 48, 48);
			fill(255); textSize(22); textAlign(CENTER, CENTER);
			text(icons[i], ccx, ccy - ch/2 + 40);

			// Name + tagline
			fill(255); textSize(15); text(names[i],    ccx, ccy - ch/2 + 92);
			fill(cr, cg, cb, 210); textSize(10); text(taglines[i], ccx, ccy - ch/2 + 110);

			// Description
			fill(185, 185, 185); textSize(11);
			text(descs[i], ccx - cw/2 + 12, ccy - ch/2 + 125, ccx + cw/2 - 12, ccy + ch/2 - 20);

			// Hover glow
			if (hov) {
				noFill(); stroke(255, 240, 80, 210); strokeWeight(2f);
				rect(ccx - cw/2, ccy - ch/2, cw, ch, 12); noStroke();
				fill(255, 240, 80); textSize(10); textAlign(CENTER, CENTER);
				text("CLICK TO PLAY", ccx, ccy + ch/2 - 14);
			}
		}
		fill(100, 98, 120); textSize(11); textAlign(CENTER, CENTER);
		text("Click a class to start your run", width / 2f, 448);
	}

	// ── Pause ──────────────────────────────────────────────────────────────
	private void drawPaused() {
		background(35, 32, 42);
		pushMatrix(); translate(width / 2f - camX, height / 2f - camY);
		WorldMap.display(this, camX, camY);
		for (Bullet b : bullets)  b.display(this);
		for (Entity e : entities) e.display(this);
		for (Entity e : entities) if (e != p) e.displayhp(this);
		popMatrix();
		drawHUD();

		noStroke(); fill(0, 0, 0, 158); rect(0, 0, width, height);
		textAlign(CENTER, CENTER); fill(200, 200, 200); textSize(50);
		text("PAUSED", width / 2f, height / 2f - 80);

		float ry = height / 2f - 8, my = height / 2f + 55;
		drawButton("RESUME",    width / 2f, ry, 190, 48, inButton(mouseX, mouseY, width / 2f, ry, 190, 48), 42, 165,  68);
		drawButton("MAIN MENU", width / 2f, my, 190, 48, inButton(mouseX, mouseY, width / 2f, my, 190, 48), 65,  68, 105);
		fill(85, 82, 105); textSize(11); text("P / Esc   Resume", width / 2f, height / 2f + 115);
	}

	// ── Game over ──────────────────────────────────────────────────────────
	private void drawEndScreen() {
		background(35, 32, 42);
		pushMatrix(); translate(width / 2f - camX, height / 2f - camY);
		WorldMap.display(this, camX, camY);
		for (Entity e : entities) e.display(this);
		popMatrix();

		noStroke(); fill(0, 0, 0, 170); rect(0, 0, width, height);
		textAlign(CENTER, CENTER);
		fill(215, 50, 40); textSize(62); text("GAME OVER", width / 2f, height / 2f - 100);

		int secs = gameTime / 60;
		String t = (secs / 60) + ":" + String.format("%02d", secs % 60);
		fill(195, 195, 195); textSize(17);
		text("Kills: " + kills + "   Wave: " + waveNumber + "   Time: " + t,
		     width / 2f, height / 2f - 48);
		if (p != null) {
			fill(155, 155, 155); textSize(14);
			text("Reached Level " + p.level, width / 2f, height / 2f - 22);
		}
		// High score
		if (highScore[0] > 0) {
			String hsTime = (highScore[2] / 60) + ":" + String.format("%02d", highScore[2] % 60);
			fill(255, 215, 50, 200); textSize(12);
			text("Best:  " + highScore[0] + " kills  ·  Wave " + highScore[1]
			     + "  ·  Level " + highScore[3] + "  ·  " + hsTime,
			     width / 2f, height / 2f + 2);
		}

		float py = height / 2f + 38, my = height / 2f + 92;
		drawButton("PLAY AGAIN", width / 2f, py, 190, 48, inButton(mouseX, mouseY, width / 2f, py, 190, 48), 42, 165,  68);
		drawButton("MAIN MENU",  width / 2f, my, 190, 48, inButton(mouseX, mouseY, width / 2f, my, 190, 48), 65,  68, 105);
	}

	// ── UI helpers ─────────────────────────────────────────────────────────
	private boolean inButton(float mx, float my, float cx, float cy, float w, float h) {
		return mx >= cx - w / 2 && mx <= cx + w / 2 && my >= cy - h / 2 && my <= cy + h / 2;
	}

	private void drawButton(String label, float cx, float cy, float w, float h,
	                        boolean hov, int r, int g, int b) {
		noStroke();
		fill(0, 0, 0, 80); rect(cx - w / 2 + 2, cy - h / 2 + 3, w, h, 10);
		fill(hov ? Math.min(r + 35, 255) : r, hov ? Math.min(g + 35, 255) : g, hov ? Math.min(b + 35, 255) : b);
		rect(cx - w / 2, cy - h / 2, w, h, 10);
		fill(255, 255, 255, hov ? 55 : 35); rect(cx - w / 2 + 3, cy - h / 2 + 2, w - 6, 4, 4);
		fill(255); textSize(18); textAlign(CENTER, CENTER); text(label, cx, cy);
	}

	private void drawCrosshair() {
		float cx = mouseX, cy = mouseY;
		noFill(); stroke(255, 255, 120, 200); strokeWeight(1.5f);
		line(cx - 10, cy, cx - 4, cy); line(cx + 4, cy, cx + 10, cy);
		line(cx, cy - 10, cx, cy - 4); line(cx, cy + 4, cx, cy + 10);
		ellipse(cx, cy, 6, 6);
		noStroke(); strokeWeight(1);
	}

	private void drawHUD() {
		if (p == null) return;

		// Top bar background
		noStroke(); fill(0, 0, 0, 180); rect(0, 0, width, 38);

		// Level + kills (left)
		fill(255, 215, 50); textSize(13); textAlign(LEFT, CENTER);
		text("LVL " + p.level + "   KILLS: " + kills, 12, 13);

		// XP bar + label
		float xpFrac = Math.min(1f, p.xp / p.xpToNextLevel);
		float xbW = 130;
		fill(30, 30, 35, 220); rect(12, 24, xbW + 2, 9, 3);
		fill(80, 205, 255); if (xpFrac > 0) rect(13, 25, xbW * xpFrac, 7, 2);
		fill(140, 220, 255); textSize(8); textAlign(LEFT, CENTER);
		text((int) p.xp + " / " + (int) p.xpToNextLevel + " XP", 13, 28);

		// Wave (right) + time in wave
		fill(200, 140, 255); textSize(13); textAlign(RIGHT, CENTER);
		text("WAVE " + waveNumber, width - 12, 19);
		// Wave progress bar — thin strip just below top bar
		float waveFrac = Math.min(1f, (float) waveTimer / waveDuration());
		fill(0, 0, 0, 90); rect(0, 38, width, 4);
		fill(160 + (int)(waveFrac * 40), 80, 255, 140 + (int)(waveFrac * 60));
		if (waveFrac > 0) rect(0, 38, width * waveFrac, 4);

		// Gun info (bottom-right)
		Gun gun = p.guns.get(p.currgun);
		float gx = width - 12, gy = height - 58;

		noStroke(); fill(0, 0, 0, 160);
		rect(gx - 145, gy - 4, 150, 62, 6);

		textAlign(RIGHT, CENTER);
		fill(220, 220, 220); textSize(13);
		text(gun.name, gx, gy + 4);

		if (gun.isReloading) {
			fill(220, 185, 35); textSize(11);
			text("RELOADING...", gx, gy + 20);
			// Reload bar
			float rProg = gun.reloadProgress();
			fill(40, 40, 40); rect(gx - 100, gy + 30, 102, 8, 3);
			fill(220, 185, 35); rect(gx - 100, gy + 30, 102 * rProg, 8, 3);
		} else {
			fill(gun.currbull == 0 ? color(220, 80, 60) : color(140, 215, 255));
			textSize(11);
			text(gun.currbull + " / " + gun.maxbull, gx, gy + 22);
		}

		// Gun slots (small squares below)
		float dotX = gx - 10, dotY = gy + 44;
		for (int i = 0; i < p.guns.size(); i++) {
			boolean active = (i == p.currgun);
			float sx = dotX - (p.guns.size() - 1 - i) * 20;
			noStroke();
			fill(active ? 200 : 80, active ? 200 : 80, active ? 200 : 80, active ? 220 : 120);
			rect(sx - 8, dotY - 8, 16, 16, 3);
			fill(active ? 255 : 150); textSize(8); textAlign(CENTER, CENTER);
			text(i + 1, sx, dotY);
		}

		// Shield bar (bottom-left, above HP bar)
		if (p.shieldMax > 0) {
			float shFrac = Math.max(0, Math.min(1, p.shieldHP / p.shieldMax));
			float shW = 160;
			noStroke(); fill(0, 0, 0, 140); rect(8, height - 46, shW + 6, 14, 3);
			fill(60, 130, 255, shFrac > 0 ? 200 : 60);
			if (shFrac > 0) rect(11, height - 44, shW * shFrac, 9, 2);
			fill(140, 190, 255); textSize(9); textAlign(LEFT, CENTER);
			text("SHIELD  " + (int) p.shieldHP + " / " + (int) p.shieldMax, 14, height - 40);
		}

		// HP bar (bottom-left)
		float hpFrac = Math.max(0, Math.min(1, p.currhp / p.maxhp));
		float hbW = 160;
		float hbY = (p.shieldMax > 0) ? height - 26 : height - 26;
		noStroke(); fill(0, 0, 0, 160); rect(8, hbY, hbW + 6, 18, 4);
		if      (hpFrac > 0.6f) fill(55, 200, 80);
		else if (hpFrac > 0.3f) fill(220, 185, 35);
		else                    fill(210, 50, 40);
		rect(11, hbY + 3, hbW * hpFrac, 12, 3);
		fill(220, 220, 220); textSize(10); textAlign(LEFT, CENTER);
		text("HP  " + (int) p.currhp + " / " + p.maxhp, 14, hbY + 9);

		// Active upgrade icons (bottom center)
		drawDefenseIcons();
	}

	private void drawDefenseIcons() {
		if (p == null) return;
		// Count active items
		int n = 0;
		if (p.shieldLevel > 0) n++;
		if (!p.drones.isEmpty()) n++;
		if (p.knifeCount > 0) n++;
		if (p.auraLevel > 0) n++;
		if (p.pulseLevel > 0) n++;
		if (p.magnetLevel > 0) n++;
		int bbounce = p.guns.isEmpty() ? 0 : p.guns.get(0).bulletBounces;
		int bchain  = p.guns.isEmpty() ? 0 : p.guns.get(0).bulletChains;
		boolean bexp = p.currgun < p.guns.size() && p.guns.get(p.currgun).explosiveRounds;
		if (bbounce > 0) n++;
		if (bchain  > 0) n++;
		if (bexp)        n++;
		if (n == 0) return;

		// Icons live in the top HUD bar (y=0–38), centred in the gap between
		// the XP section (~x=155) and the WAVE text (~x=510)
		float iw = 22f, ih = 22f, gap = 3f;
		float iy = 31f; // icon bottom — keeps icons within the 38 px top bar
		float cx = (width - (n * (iw + gap) - gap)) / 2f;
		if (p.shieldLevel > 0)   { _icon("SH", p.shieldLevel,   cx, iy, iw, ih, 55, 130, 255); cx += iw + gap; }
		if (!p.drones.isEmpty()) { _icon("DR", p.droneLevel,    cx, iy, iw, ih, 40, 190, 185); cx += iw + gap; }
		if (p.knifeCount > 0)   { _icon("KN", p.knifeLevel,    cx, iy, iw, ih, 230, 215, 60);  cx += iw + gap; }
		if (p.auraLevel > 0)    { _icon("AU", p.auraLevel,     cx, iy, iw, ih, 255, 165, 30);  cx += iw + gap; }
		if (p.pulseLevel > 0)   { _icon("PU", p.pulseLevel,    cx, iy, iw, ih, 200, 100, 255); cx += iw + gap; }
		if (p.magnetLevel > 0)  { _icon("MG", p.magnetLevel,   cx, iy, iw, ih, 80, 230, 215);  cx += iw + gap; }
		if (bbounce > 0)        { _icon("RC", bbounce,          cx, iy, iw, ih, 255, 200, 60);  cx += iw + gap; }
		if (bchain  > 0)        { _icon("CH", bchain,           cx, iy, iw, ih, 80, 200, 255);  cx += iw + gap; }
		if (bexp)               { _icon("EX", 1,                cx, iy, iw, ih, 255, 130, 50); }
	}

	private void _icon(String lbl, int lvl, float x, float y, float w, float h, int r, int g, int b) {
		noStroke(); fill(0, 0, 0, 130); rect(x + 1, y - h + 2, w, h, 3);
		fill(r, g, b, 185); rect(x, y - h, w, h, 3);
		fill(r, g, b, 70); rect(x, y - h, w, h * 0.38f, 3); // top sheen
		textAlign(CENTER, CENTER);
		fill(255); textSize(7.5f);
		text(lbl, x + w / 2f, y - h * 0.60f);        // upper half
		fill(255, 255, 200, 220); textSize(6.5f);
		text(String.valueOf(lvl), x + w / 2f, y - h * 0.20f); // lower quarter
	}

	// ── Input ──────────────────────────────────────────────────────────────
	@Override
	public void mousePressed() {
		if (mouseButton != LEFT) return;
		float mx = mouseX, my = mouseY;
		switch (gameState) {
		case STATE_MENU:
			if (inButton(mx, my, width / 2f, 308, 190, 50)) gameState = STATE_CHARACTER;
			break;
		case STATE_PLAYING:
			// Manual fire toward cursor (in addition to auto-fire)
			if (p != null && !p.guns.isEmpty())
				p.guns.get(p.currgun).manualFire(aimX, aimY);
			break;
		case STATE_PAUSED:
			if      (inButton(mx, my, width / 2f, height / 2f - 8,  190, 48)) resumeGame();
			else if (inButton(mx, my, width / 2f, height / 2f + 55, 190, 48)) goToMenu();
			break;
		case STATE_CHARACTER: {
			float cw = 172f, ch = 230f, cgap = 12f;
			float csx = (width - (3 * cw + 2 * cgap)) / 2f;
			for (int i = 0; i < 3; i++) {
				float ccx = csx + i * (cw + cgap) + cw / 2f;
				if (inButton(mx, my, ccx, 320, cw, ch)) {
					selectedCharacter = i;
					startGame();
					break;
				}
			}
			break;
		}
		case STATE_GAME_OVER:
			if      (inButton(mx, my, width / 2f, height / 2f + 38, 190, 48)) gameState = STATE_CHARACTER;
			else if (inButton(mx, my, width / 2f, height / 2f + 92, 190, 48)) goToMenu();
			break;
		case STATE_LEVELUP: {
			float cardW = 265f, cardH = 148f, cardGap = 10f;
			float startX = (width - (2 * cardW + cardGap)) / 2f;
			float startY = 118f;
			for (int i = 0; i < currentUpgrades.length; i++) {
				int col = i % 2, row = i / 2;
				float cx = startX + col * (cardW + cardGap) + cardW / 2f;
				float cy = startY + row * (cardH + cardGap) + cardH / 2f;
				if (inButton(mx, my, cx, cy, cardW, cardH)) {
					applyUpgrade(currentUpgrades[i]);
					p.currgun = Math.min(savedCurrgun, p.guns.size() - 1);
					gameState = STATE_PLAYING;
					noCursor();
					wPressed = aPressed = sPressed = dPressed = false;
					if (p != null) { p.xd = 0; p.yd = 0; }
					break;
				}
			}
			break;
		}
		}
	}

	@Override
	public void mouseWheel(MouseEvent event) {
		if (gameState != STATE_PLAYING || p == null || p.guns.isEmpty()) return;
		int delta = event.getCount() > 0 ? 1 : -1;
		p.currgun = (p.currgun + delta + p.guns.size()) % p.guns.size();
	}

	@Override
	public void keyPressed() {
		if (key == ESC) key = 0;

		if (keyCode == 27 || keyCode == 80) {
			if      (gameState == STATE_PLAYING) pauseGame();
			else if (gameState == STATE_PAUSED)  resumeGame();
			return;
		}

		if (gameState == STATE_PLAYING && key == ' ') {
			if (p != null && !p.guns.isEmpty())
				p.guns.get(p.currgun).manualFire(aimX, aimY);
			return;
		}

		// Level-up keyboard shortcuts: 1-4 select a card
		if (gameState == STATE_LEVELUP) {
			int idx = -1;
			if      (key == '1' && currentUpgrades.length > 0) idx = 0;
			else if (key == '2' && currentUpgrades.length > 1) idx = 1;
			else if (key == '3' && currentUpgrades.length > 2) idx = 2;
			else if (key == '4' && currentUpgrades.length > 3) idx = 3;
			if (idx >= 0 && currentUpgrades[idx] != null) {
				applyUpgrade(currentUpgrades[idx]);
				p.currgun = Math.min(savedCurrgun, p.guns.size() - 1);
				gameState = STATE_PLAYING;
				noCursor();
				wPressed = aPressed = sPressed = dPressed = false;
				if (p != null) { p.xd = 0; p.yd = 0; }
			}
			return;
		}

		if (gameState != STATE_PLAYING) return;

		switch (keyCode) {
		case 87: case 38: if (!wPressed) { p.yd -= 1; wPressed = true; } break; // W/Up
		case 65: case 37: if (!aPressed) { p.xd -= 1; aPressed = true; } break; // A/Left
		case 83: case 40: if (!sPressed) { p.yd += 1; sPressed = true; } break; // S/Down
		case 68: case 39: if (!dPressed) { p.xd += 1; dPressed = true; } break; // D/Right
		case 82: // R — reload
			if (!p.guns.isEmpty()) p.guns.get(p.currgun).startReload();
			break;
		case 81: // Q — prev gun
			if (!p.guns.isEmpty()) p.currgun = (p.currgun - 1 + p.guns.size()) % p.guns.size();
			break;
		case 69: // E — next gun
			if (!p.guns.isEmpty()) p.currgun = (p.currgun + 1) % p.guns.size();
			break;
		case 49: if (p.guns.size() > 0) p.currgun = 0; break; // 1
		case 50: if (p.guns.size() > 1) p.currgun = 1; break; // 2
		case 51: if (p.guns.size() > 2) p.currgun = 2; break; // 3
		case 52: if (p.guns.size() > 3) p.currgun = 3; break; // 4
		case 53: if (p.guns.size() > 4) p.currgun = 4; break; // 5
		case 54: if (p.guns.size() > 5) p.currgun = 5; break; // 6
		}
	}

	@Override
	public void keyReleased() {
		if (gameState != STATE_PLAYING) return;
		switch (keyCode) {
		case 87: case 38: if (wPressed) { p.yd += 1; wPressed = false; } break;
		case 65: case 37: if (aPressed) { p.xd += 1; aPressed = false; } break;
		case 83: case 40: if (sPressed) { p.yd -= 1; sPressed = false; } break;
		case 68: case 39: if (dPressed) { p.xd -= 1; dPressed = false; } break;
		}
	}
}
