# Changelog

## Unreleased

- Hovering an item now shows at most 5 actions (new `itemSlots` setting, "Item actions on the bar"
  in the settings screen). The normal bar still holds 6.
- Screenshot and Mark to Compare no longer show on the bar while hovering an item. They are still
  in the All Keybinds popup.
- Item actions now use `onBar` like other keybinds, and the settings screen has an Item Actions page
  to pick them. Older config files are updated automatically when they load.

## 0.3.0

- The All Keybinds popup now opens only from inventory screens. The key no longer does anything in
  the world, and the separate full-screen version of the popup is gone.
- A "Show on inventory" switch above the Spells heading in the popup turns the spell bar off and back
  on. The bar comes back where it was before.
- "Lock Slot" and "Item Lock" are now one **Lock Slot** entry. It uses the Wynntils lock key, or
  middle mouse without Wynntils. Config files from 0.2.0 are fixed automatically when they load.
- When several item actions share a key, the popup now lists only the first one, like the bar
  already did. Keys shared by different groups, such as Ultimate and Favorite on F, still show in
  both places.
- Added an **Ingredient Pouch** entry to the popup, under Menus.

## 0.2.0

- The bottom bar is now one short row of chosen keybinds, ending in an **All Keybinds** button.
- New All Keybinds popup with every keybind under headings. It opens from the button or its own
  rebindable key (default `Y`), over the inventory without closing it.
- Spell keys can go in a bar above the inventory, in a row of the bottom bar, or only in the popup.
  They always show the current class's keys.
- Optional dynamic mode: hovering an item shows what you can do with it, and Mount gives up its slot
  when you have no mount.
- The config is now one keybind list. Cloth Config and Mod Menu, if installed, add a settings screen
  that edits the same file.

## 0.1.0

- First version: a bar of live key prompts under the inventory.
- Prompts follow rebinds and Class Keybind Profiles switches as they happen, and a key turns red
  while it is held.
- A pinned row for the four spells and the ultimate, with spell names from Wynntils.
- Everything else on pages that cycle on their own. Hovering pauses them, and clicking or scrolling
  flips pages.
- Item actions replace the page while an item is hovered.
- Layout is set in `config/wowkeybindhelper.json`.
