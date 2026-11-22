# Changelog

## 0.5.0

- The bottom bar now sits on the **bottom edge of the screen**. The new **Bottom bar position**
  setting (`barPosition`, `SCREEN_BOTTOM` or `UNDER_INVENTORY`) puts it back right under the
  inventory.
- Spell keys on the inventory always stay white. They no longer turn red when you hold a key that
  is also bound to something else, like Favorite on the same key as a spell.
- The All Keybinds popup is now drawn over everything other mods add to the screen (FancyMenu
  layouts, Wynntils' inventory widgets, toasts), so they no longer cover it.
- The spell bar sits a little higher, clear of the trim at the top of Wynncraft's inventory art.
- Opening the inventory with the cursor on the Ingredient Pouch no longer switches the bar to item
  actions.
- Numpad Enter now reads ENTER instead of NENT (Wynntils binds Hold to Compare to it by default).

## 0.4.0

- Key prompts are now drawn with **Wynncraft's own key art**, read from its resource pack while
  it is loaded (on the server, or preloaded by Wynntils). Resource packs that reskin those keys
  reskin the prompts too. The mod no longer ships any key textures: without Wynncraft's pack,
  prompts are plain text keys. Arrow keys now read UP, DOWN, LEFT and RIGHT.
- **WynnMod** support: its six chat hotkeys fill the custom command slots when Wynntils' custom
  commands are unbound, and Ping and Scan Party are in the popup.
- **WynnExtras** support: entries can read keys from WynnExtras' own settings with
  `wynnextras:<setting>`. Territory Menu and Guild Bank are in the popup while those WynnExtras
  features are on.
- Spell keys also come from **Wynncraft Spell Caster** and **Better Wynn Macros**, not just
  Wynntils' spell keybinds. Each spell shows the first of those that has a key bound.
- Renamed to **Wynn Keybind Wizard**. The mod id is now `wynnkeybindwizard`, so the config file is
  `config/wynnkeybindwizard.json` and the jar is `wynnkeybindwizard-<version>.jar`.
- Now MIT licensed, with an icon and project links, ready to be used in any Wynncraft modpack.
- Wynntils' six custom command keybinds are built in. Name them on the new **Custom Commands**
  settings page (or `customCommands` in the file) and they show up with that label; unnamed slots
  are hidden. This replaces the hard-coded Party Finder and Use Menu entries.
- New **Add Keybind** settings page: pick any keybind from any installed mod, give it a label and
  heading, and it joins the bar and popup. Added keybinds can be removed from the same page.
- The Background switch is now a **Background opacity** slider (`backgroundOpacity`, 0-100). At 0
  the bars show just the keys with no box behind them.
- Hovering an item now shows at most 5 actions (new `itemSlots` setting, "Item actions on the bar"
  in the settings screen). The normal bar still holds 6.
- Screenshot and Mark to Compare no longer show on the bar while hovering an item. They are still
  in the All Keybinds popup.
- Item actions now use `onBar` like other keybinds, and the settings screen has an Item Actions page
  to pick them.
- The All Keybinds popup now uses Wynntils' menu background, so resource packs that reskin Wynntils
  (like WynnEdits Dark Theme) reskin the popup too. Without Wynntils it keeps the old flat look.

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
- Layout is set in `config/wynnkeybindwizard.json`.
