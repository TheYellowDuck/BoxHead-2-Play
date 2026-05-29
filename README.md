# BoxHead 2 Play

A top-down endless survival shooter built in Java with the Processing framework, inspired by the classic Flash game *BoxHead 2 Play* and modernised with *Survivor.io*-style mechanics.

## Play

Download `BoxHead2Play.jar` from the [latest release](../../releases/latest) and run:

```
java -jar BoxHead2Play.jar
```

Requires Java 11+. No installation needed.

### Controls

| Action | Key |
|---|---|
| Move | WASD / Arrow keys |
| Aim | Mouse |
| Shoot | Left click / Space |
| Switch gun | Q / E / Scroll wheel / 1–6 |
| Reload | R |
| Pause | P / Esc |

## Features

- **Procedural infinite map** — value-noise terrain generates a unique layout every run; enclosed floor pockets are detected and broken open at runtime via BFS connectivity analysis
- **BFS flow-field pathfinding** — dual fields (standard + clearance-inflated for large enemies) rebuild on player tile change; enemies navigate through complex corridors without getting stuck
- **7 enemy types** with wave-scaled stats — Zombie, Skeleton, Shooter, Gunner, Brute, Marksman, and Devil (boss), each with distinct AI behaviour
- **6 weapons** — Pistol, Shotgun, SMG, Minigun, Sniper Rifle, Rocket Launcher; all acquired through procedural crate drops and upgradeable via level-up cards
- **Comprehensive upgrade system** — 30+ upgrades across weapons, bullet modifiers, and defensive items; each upgrade is one-per-run and applied globally across all owned guns
- **Bullet modifier stack** — Ricochet (up to 3 bounces), Chain Strike (arcs to 4 enemies), Frag Rounds (AoE splash per hit), Cryo (slow), Incendiary (burn DoT); all modifiers propagate to drones and turrets in real time
- **Defensive items** — Force Field (contact-absorbing shield), Combat Drones (orbiting auto-turrets that mirror the player's active weapon), Blade Spin (rotating melee), Damage Aura, Shockwave pulse, Pickup Magnet
- **Kill-streak XP multiplier**, floating damage numbers, elite enemy variants, wave events (Boss Rush, Supply Drop, Enemy Horde), and danger zones

## Technical Highlights

- **Entity–component–collision** — shared `Entity` base handles physics, status effects (slow, burn), hit-flash, and HP bars; weapon logic lives in a unified `Gun` class used by player, enemies, drones, and turrets alike
- **Reactive map repair** — `WorldMap.wouldIsolate` uses a local 9×9 BFS to prevent small enclosed pockets at generation time; the `Pathfinder.fixEnclosures` pass catches larger rings after each flow-field rebuild and permanently opens wall tiles via `WorldMap.openWall`
- **Shield absorption model** — shield intercepts the total HP delta from entity-contact damage before committing the kill flag; bullet damage intentionally bypasses the shield for difficulty
- **Upgrades-propagate-to-allies** — `syncAlliedGuns` runs every frame, copying the full bullet style (pellet count, spread, speed, AoE radius, penetration, all modifiers) from the player's active gun to every drone and turret

## Stack

- **Language:** Java 21
- **Rendering:** [Processing 4](https://processing.org/) (`core.jar`) — software-rasterised 2D, no OpenGL dependency
- **Build:** `javac` + `jar`; single fat JAR, no build tool required

## Build from source

```bash
javac -cp lib/core.jar -d bin src/BoxHead2Play/*.java
mkdir -p build/fatjar
cd build/fatjar && jar xf ../../lib/core.jar && rm -rf META-INF
cp -r ../../bin/BoxHead2Play .
cd ../..
jar cfm BoxHead2Play.jar build/MANIFEST.MF -C build/fatjar .
```
