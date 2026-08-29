# Create Horse Power: Community Edition

**Medieval power for Create: harness horses and other mobs to drive your machines — now maintained, fixed, and modernized for NeoForge 1.21.1.**

---

# Overview

**Create Horse Power: Community Edition** adds animal-powered machinery to the Create ecosystem.

Its centerpiece is the **Horse Crank**: attach a suitable animal, give it a proper walking path, and let it turn your Create contraptions without water wheels, windmills, engines, or electricity.

It is especially useful for **primitive, medieval, low-tech, and progression-focused modpacks**, where Create automation should become available before more advanced power sources.

Community Edition continues the original Create Horse Power project while focusing on **maintenance, stability, modern Minecraft support, and better modpack integration**.

## What Does It Add?

### Horse Crank

The Horse Crank converts animal labor into Create rotational power.

Attach a supported mob to the crank and it will walk around the mechanism while generating **RPM and Stress Units** for the connected Create network.

This makes animal power a practical early-game alternative to conventional Create generators.

### Different Worker Sizes

Workers can be classified into different power tiers:

- **Small workers** — lighter animals with lower stress capacity.
- **Medium workers** — stronger general-purpose animals.
- **Large workers** — powerful animals such as horses, donkeys, mules, and similar mobs.

This lets modpack authors build progression around the animals available to the player.

### Path Quality Matters

The surface around the Horse Crank affects how efficiently the animal can work.

Different configured path materials can provide:

- **Poor paths** — reduced output.
- **Normal paths** — standard output.
- **Great paths** — improved output.

That means upgrading the working area around the crank can become part of your progression instead of the block simply producing free power anywhere.

### Configurable Power

Server owners and modpack developers can configure:

- Base RPM.
- Stress capacity for each worker tier.
- Poor, normal, and great path multipliers.
- Which mobs are valid workers.
- Which blocks count as valid path materials.

Community Edition also supports **data-driven entity tags**, making it easier for datapacks and other mods to add compatible workers without hard-coded integrations.

---

# Why Community Edition?

The original Create Horse Power introduced a great idea, but development eventually slowed and several issues remained open.

**Community Edition exists to keep that idea usable on modern Create installations.**

Compared with the older version, CE focuses on fixing several long-standing problems:

- **Chunk reload reliability** — attached workers are tracked persistently instead of being forgotten when chunks unload and reload.
- **Stable adjacent Horse Cranks** — generator direction handling has been reworked to cooperate with Create's kinetic network instead of corrupting rotation state.
- **Proper leash cleanup** — breaking or detaching a Horse Crank no longer leaves behind ghost leash-knot entities.
- **Modern 1.21.1 data formats** — recipes, loot tables, advancements, and blockstate resources have been updated for current Minecraft formats.
- **NeoForge 1.21.1 support** — the project has been modernized for the current NeoForge/Create ecosystem.
- **Broader modded-mob compatibility** — worker handling is no longer limited to a narrow class of vanilla-style pathfinding mobs.
- **Data-driven worker categories** — datapacks can extend small, medium, and large worker lists using entity tags.
- **Cleaner Create integration** — kinetic lifecycle handling now follows Create's own block-entity and network behavior more closely.

The goal is not to turn Horse Power into a completely different mod. It is to keep the original concept simple while making it **reliable enough for real survival worlds and large modpacks**.

---

# Why Use Animal Power?

Create has many excellent ways to generate rotational force, but animal power fills a progression niche that most generators do not.

It works particularly well for:

- **Primitive and medieval modpacks**
- **Age-based progression**
- **Low-tech Create starts**
- **Historical or survival-focused packs**
- **Automation before wind, steam, electricity, or engines**
- **Players who want functional farms to be part of their mechanical infrastructure**

Instead of placing a passive generator and forgetting about it, your power source becomes part of the world: an animal, a working area, and a machine built around it.

---

# Modpack Friendly

Create Horse Power: Community Edition is designed with modpack authors in mind.

Worker lists, power values, path materials, and efficiency multipliers can all be adjusted to fit different progression systems.

For example, a pack could make:

**Donkey power → Horse power → Water power → Wind power → Steam power**

a real technological progression rather than giving the player every Create power source at once.

---

# Original Project & Credits

Create Horse Power: Community Edition is a maintained community fork of the original **Create Horse Power** project by **SteamPunkNation**.

The original project, concept, and much of the foundation of this mod come from:

[SteamPunkNation/CreateHorsePower — Powering Create Mod contraptions like it's medieval times.](https://github.com/SteamPunkNation/CreateHorsePower)

Huge credit goes to **SteamPunkNation and the original contributors** for creating the mod and establishing the animal-powered Create concept.

Community Edition builds on that work with maintenance, compatibility updates, bug fixes, and further modpack-focused improvements.

---

# Requirements

- **Minecraft 1.21.1**
- **NeoForge**
- **Create**

Exact supported versions are listed on each release file.

---

**Put your animals to work, power your early factories, and bring real horsepower to Create.**
