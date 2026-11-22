# WoW Keybind Helper

A bar of live key prompts under the inventory, for the World of Wynncraft modpack (Fabric 1.21.11).
Not published on its own.

- **Live**: every prompt is read from the game's keybinds each frame, so rebinding in Controls or a
  Class Keybind Profiles switch shows up immediately.
- **Pinned row**: the four spells (named after the current class when Wynntils is installed) and the ultimate.
- **Pages**: everything else, one page at a time. Auto-cycles; hovering the bar pauses it; left/right
  click or scroll on the bar flips pages.
- **Item hover**: while the mouse is over an item, the page is replaced by the item actions.
- A key is drawn in its red pressed style while it is physically held.

## Configuring

`config/wowkeybindhelper.json` is written on first launch and re-read every time a container screen
opens, so edits apply without a restart. Ship it in the pack to set the layout.

Each entry is `{"label": "Map", "keys": ["wynntils.keybind.openMainMap"]}`. `keys` are tried in order;
each is a keybind id exactly as it appears after `key_` in `options.txt`, or a physical key such as
`key.keyboard.f3` for a fixed prompt. Entries whose keybind does not exist or is unbound are hidden.
Labels `$spell1`..`$spell4` become spell names.

## Building and testing

    gradlew build                 # build/libs/wowkeybindhelper-<version>.jar
    gradlew runClientGameTest     # opens the inventory in a real client, screenshots to build/run/clientGameTest/screenshots

## Porting to 26.x

Written against official Mojang names, which is what the unobfuscated 26.x game uses, so there is no
remap to do. Expected work:

- `build.gradle`: follow Fabric's 26.x template (no `mappings` line, Java 25, new Loom).
- `KeyPromptRenderer` is the only class that calls the GUI drawing API; fix it there if that API moved.
- `AbstractContainerScreenMixin` hooks `renderContents`, and the accessor reads `leftPos`, `topPos`,
  `imageWidth`, `imageHeight`, `hoveredSlot`. Check those names still exist.
- `WynntilsCompat` needs a 26.x Wynntils to compile against; the mod runs without it.
