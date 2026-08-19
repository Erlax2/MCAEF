# MCAEF Changelog

## 1.0.4

### First Person Model compatibility

- Added compatibility handling for First Person Model when using MCA's `VILLAGER` player model.
- MCAEF no longer forcibly restores the local player's head visibility while the camera is in first person.
- Prevents MCAEF's player visibility repair system from interfering with intentional first-person head hiding.
- Keeps the existing visibility repair behavior for the head while rendering in third person.
- Body, arms and legs continue to use MCAEF's visibility repair logic normally.
- Client-side compatibility change only; no dedicated server changes are required.

### Technical

- Updated `McaPlayerVisibilityGuard` to distinguish between first-person and third-person camera rendering.
- Preserves the visibility corruption protection introduced for MCA/Epic Fight while avoiding conflicts with first-person body rendering mods.

## 1.0.3

- Added MCA Editor compatibility improvements.
- Added compatibility bridges for YDM's Weapon Master.
- Added Curios rendering compatibility.
- Improved support for external humanoid render layers under Epic Fight.
