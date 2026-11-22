# WoW Keybind Helper

Live key prompts around the inventory, for the World of Wynncraft modpack (Fabric 1.21.11).
Not published on its own.

- **Bottom bar**: a short row of the most important keybinds under the inventory, ending in an
  **All Keybinds** button.
- **All Keybinds popup**: every configured keybind under headings. Opens from the button or its own
  rebindable key (default `Y`), over the inventory without closing it. The key only works in
  inventory screens, not in the world.
- **Spell keys**: in their own bar above the inventory, as a row of the bottom bar, or only in the
  popup. They are always the current class's keys, named after its spells when Wynntils is installed.
- **Dynamic mode** (optional): while an item is hovered the bottom bar shows what you can do with
  that item (compare for gear, stat views for identified items, lock, favorite...), and a keybind you
  cannot use yet (Mount without a mount) gives its slot to the next one in line. Needs Wynntils to
  tell items apart; without it every item action is offered.
- **Live**: every prompt is read from the game's keybinds each frame, so rebinding in Controls or a
  Class Keybind Profiles switch shows up immediately. A key is drawn red while physically held.

## Configuring

`config/wowkeybindhelper.json` is written on first launch and re-read every time a container screen
opens, so edits apply without a restart. Ship it in the pack to set the layout. With Cloth Config
and Mod Menu installed (both optional) the same settings, and which keybinds go on the bar, can be
changed in game; adding new keybind entries is done in the file.

`keybinds` is one list. Its order is the priority order for the bar. Each entry:

| field | meaning |
| --- | --- |
| `label` | Text beside the key. `$spell1`..`$spell4` become spell names. |
| `keys` | Tried in order. A keybind id exactly as it appears after `key_` in `options.txt`, or a physical key such as `key.keyboard.f3` for a fixed prompt. |
| `group` | Heading in the popup. `Spells` is special: those entries are the spell bar. |
| `onBar` | Candidate for the bottom bar; the first `barSlots` usable ones are shown. For item actions it means shown while hovering an item, up to `itemSlots` (default 5); unpicked ones stay in the popup. |
| `when` | Empty for always, `has_mount`, or an item condition (`item`, `item_stats`, `item_gear`) which makes it an item action. |

An entry whose keybind does not exist (mod not installed) or is unbound is hidden everywhere.

## Building and testing

    gradlew build                 # build/libs/wowkeybindhelper-<version>.jar
    gradlew runClientGameTest     # drives a real client, screenshots to build/run/clientGameTest/screenshots

## Porting to 26.x

Written against official Mojang names, which is what the unobfuscated 26.x game uses, so there is no
remap to do. Expected work:

- `build.gradle`: follow Fabric's 26.x template (no `mappings` line, Java 25, new Loom).
- `KeyPromptRenderer` is the only class that calls the GUI drawing API; fix it there if that API moved.
- `AbstractContainerScreenMixin` hooks `renderContents` and `renderTooltip`, and the accessor reads
  `leftPos`, `topPos`, `imageWidth`, `imageHeight`, `hoveredSlot`. Check those names still exist.
- Input goes through Fabric's screen events (`allowKeyPress`, `allowMouseClick`, `allowMouseScroll`)
  and `KeyEvent`/`MouseButtonEvent`; their signatures changed in 1.21.11 and may again.
- `WynntilsCompat` needs a 26.x Wynntils to compile against, and Cloth Config / Mod Menu need 26.x
  builds for the settings screen; the mod runs without any of them.
