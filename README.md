# BoxHead 2 Play

A top-down endless survival shooter built in **Java** with the Processing framework, inspired by the classic Flash game *BoxHead 2 Play* and modernised with *Survivor.io*-style mechanics. It features a procedurally generated infinite map, BFS flow-field pathfinding, seven enemy types, six weapons, and a 30+ card upgrade system — all built on a shared object-oriented entity model.

<video src="https://github.com/user-attachments/assets/593965ae-445f-4185-848e-70c6862cbbfb" controls width="100%"></video>

## Features

- **Procedural infinite map** — value-noise terrain generates a unique layout every run; enclosed floor pockets are detected and broken open at runtime via BFS connectivity analysis
- **BFS flow-field pathfinding** — dual fields (standard + clearance-inflated for large enemies) rebuild on player tile change; enemies navigate through complex corridors without getting stuck
- **7 enemy types** with wave-scaled stats — Zombie, Skeleton, Shooter, Gunner, Brute, Marksman, and Devil (boss), each with distinct AI behaviour
- **6 weapons** — Pistol, Shotgun, SMG, Minigun, Sniper Rifle, Rocket Launcher; all acquired through procedural crate drops and upgradeable via level-up cards
- **Comprehensive upgrade system** — 30+ upgrades across weapons, bullet modifiers, and defensive items; each upgrade is one-per-run and applied globally across all owned guns
- **Bullet modifier stack** — Ricochet (up to 3 bounces), Chain Strike (arcs to 4 enemies), Frag Rounds (AoE splash per hit), Cryo (slow), Incendiary (burn DoT); all modifiers propagate to drones and turrets in real time
- **Defensive items** — Force Field (contact-absorbing shield), Combat Drones (orbiting auto-turrets that mirror the player's active weapon), Blade Spin (rotating melee), Damage Aura, Shockwave pulse, Pickup Magnet
- **Kill-streak XP multiplier**, floating damage numbers, elite enemy variants, wave events (Boss Rush, Supply Drop, Enemy Horde), and danger zones

## How It Works

- **Shared entity model** — a common `Entity` base handles physics, status effects (slow, burn), hit-flash, and HP bars; weapon logic lives in a unified `Gun` class used by the player, enemies, drones, and turrets alike, with concrete enemy and weapon subclasses extending the shared behaviour.
- **BFS flow-field pathfinding** — two distance fields (a standard field and a clearance-inflated one for large enemies) are rebuilt from the player whenever the player crosses a tile boundary, so every enemy can follow the gradient to the player through complex corridors.
- **Reactive map repair** — `WorldMap.wouldIsolate` uses a local 9×9 BFS to prevent small enclosed pockets at generation time; the `Pathfinder.fixEnclosures` pass catches larger rings after each flow-field rebuild and permanently opens wall tiles via `WorldMap.openWall`.
- **Shield absorption model** — the shield intercepts the total HP delta from entity-contact damage before committing the kill flag; bullet damage intentionally bypasses the shield for difficulty.
- **Upgrades propagate to allies** — `syncAlliedGuns` runs every frame, copying the full bullet style (pellet count, spread, speed, AoE radius, penetration, and all modifiers) from the player's active gun to every drone and turret.

## Skills Demonstrated

- Object-oriented design — 30+ classes for entities, weapons, enemies, and items
- Inheritance & polymorphism — enemy and weapon hierarchies over a shared `Entity` / `Gun` base
- Entity–component modelling — physics, status effects, hit-flash, and HP bars on a common base
- Game AI — seven enemy types with distinct behaviours, wave-scaled stats, and elite variants
- BFS flow-field pathfinding — dual distance fields rebuilt on player movement
- Graph connectivity analysis — BFS enclosure detection (`wouldIsolate`, `fixEnclosures`) and runtime wall opening
- Procedural generation — value-noise infinite terrain with runtime connectivity repair
- Collision detection & physics — entity contact, bullet hits, knockback, status effects
- Spatial partitioning — tile grid for navigation and world representation
- Game systems design — 30+ upgrades, a bullet-modifier stack, defensive items, and wave events
- Real-time state propagation — per-frame weapon-style sync from player to drones and turrets
- Custom 2D rendering — Processing software rasteriser, floating damage numbers, visual effects
- Input handling — keyboard and mouse aiming, weapon switching via keys and scroll wheel
- Performance optimisation — flow-field rebuilt only when the player changes tiles
- Fat-JAR packaging — single distributable JAR bundling the Processing core

## Tech Stack

- Java 21 (runs on Java 11+)
- Processing 4 (`core.jar`) — software-rasterised 2D, no OpenGL dependency
- `javac` + `jar` — single fat JAR, no build tool required
- Git

## Demo & Links

- ⬇️ [Download the latest release](https://github.com/TheYellowDuck/BoxHead-2-Play/releases/latest)

## Getting Started

Download `BoxHead2Play.jar` from the [latest release](../../releases/latest) and run:

```bash
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

### Build from source

```bash
javac -cp lib/core.jar -d bin src/BoxHead2Play/*.java
mkdir -p build/fatjar
cd build/fatjar && jar xf ../../lib/core.jar && rm -rf META-INF
cp -r ../../bin/BoxHead2Play .
cd ../..
jar cfm BoxHead2Play.jar build/MANIFEST.MF -C build/fatjar .
```
