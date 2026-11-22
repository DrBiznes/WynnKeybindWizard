# Modpack makers guide

Wynn Keybind Wizard shows live key prompts around the inventory. You choose which keybinds appear,
from Wynntils and from any other mod in your pack, and ship the layout as one config file.

## Setup

1. Install the mod and launch once. `config/wynnkeybindwizard.json` is written with the default layout.
2. Set the layout in game (Mod Menu > Wynn Keybind Wizard, needs Cloth Config) or in the file. The file
   is re-read every time a container screen opens, so edits apply without a restart.
3. Ship `config/wynnkeybindwizard.json` in your pack.

Players can change the same settings in game. A pack update that ships the file again replaces
their changes, as with any config a pack ships.

## What players see

- **Bottom bar**: a short row of the most important keybinds under the inventory, ending in an
  **All Keybinds** button.
- **All Keybinds popup**: every configured keybind under headings. Opens from the button or its own
  rebindable key (default `Y`), over the inventory without closing it. The key only works in
  inventory screens, not in the world.
- **Spell keys**: in their own bar above the inventory, as a row of the bottom bar, or only in the
  popup. They are always the current class's keys, named after its spells when Wynntils is installed.
  Works with whichever spell macro mod the pack uses: Wynncraft Spell Caster, Better Wynn Macros or
  Wynntils' own spell keybinds (the first one with a bound key wins).
- **Dynamic mode** (optional): while an item is hovered the bottom bar shows what you can do with
  that item (compare for gear, stat views for identified items, lock, favorite...), and a keybind you
  cannot use yet (Mount without a mount) gives its slot to the next one in line. Needs Wynntils to
  tell items apart; without it every item action is offered.
- **Live**: every prompt is read from the game's keybinds each frame, so rebinding in Controls or a
  Class Keybind Profiles switch shows up immediately. A key is drawn red while physically held.
- **Missing mods are fine**: an entry whose mod is not installed, or whose key is unbound, is hidden
  everywhere. The defaults cover Wynntils, WynnMod, WynnExtras, Wynnventory, WynnTitles, Nimble
  ReWynnded, Zoomify and Iris, plus the spell macro mods above, and only the ones present show up.

## Custom commands (Wynntils and WynnMod)

Wynntils has six **Custom Command Keybinds** that run a command you choose (set under Wynntils >
Custom Command Keybinds), and WynnMod has six chat **Hotkeys** that do the same. What they do
differs per pack, so name them on the **Custom Commands** page of the settings screen, or in the
file:

```json
"customCommands": ["Party Finder", "Use Menu", "", "", "", ""]
```

Each name is the label for that slot's key on the bar and in the popup. Empty slots are hidden.
Slot 1 is Wynntils' first custom command or, if that is unbound, WynnMod's Hotkey 1, and so on.
The slots are the `$command1`..`$command6` entries in `keybinds`, under the `Commands` heading;
move or regroup them like any other entry.

## Adding another mod's keybind

**In game:** open the **Add Keybind** page, pick the keybind from the list (every keybind in
Controls, with the mod it belongs to; type to search), give it a label and a heading, choose whether
it is an item action or a bar candidate, and Save. Keybinds added this way are listed on the same
page with a Remove switch.

**In the file:** find the keybind's id in `options.txt`. It is the part after `key_`, so
`key_wynnventory.openRewardScreen:key.keyboard.n` is `wynnventory.openRewardScreen`. Add an entry:

```json
{"label": "Lootpools", "group": "Menus", "keys": ["wynnventory.openRewardScreen"], "onBar": true}
```

Players without that mod never see the entry.

WynnExtras keeps its keys in its own settings instead of Controls, so they are not on the Add
Keybind page. Name the setting with a `wynnextras:` prefix (the setting names are the ones in
`config/wynnextras.json`):

```json
{"label": "Territory Menu", "group": "Menus", "keys": ["wynnextras:territoryMenuKey"]}
```

The entry is hidden while that WynnExtras feature is switched off.

## Config reference

| setting | meaning |
| --- | --- |
| `enabled` | Turns the bars off without uninstalling. |
| `showOnAllContainers` | Also show under chests, banks and other container screens, not just the inventory. |
| `barSlots` | How many keybinds the bottom bar holds (the All Keybinds button is extra). |
| `itemSlots` | How many item actions the bar holds while an item is hovered. |
| `spellBar` | `TOP`, `BOTTOM` or `OFF` (popup only). |
| `dynamic` | Item actions while hovering, and hiding keybinds you cannot use yet. |
| `offsetY`, `scale`, `backgroundOpacity` | Gap from the inventory, size, and darkness (0-100) of the box behind the bars. |
| `showHeldKeys` | Draw a key red while it is held down. |
| `customCommands` | Names for the six custom command slots (Wynntils custom commands, WynnMod hotkeys); see above. |
| `keybinds` | Everything the mod shows; see below. |

`keybinds` is one list. Its order is the priority order for the bar. Each entry:

| field | meaning |
| --- | --- |
| `label` | Text beside the key. `$spell1`..`$spell4` become spell names, `$command1`..`$command6` the `customCommands` names. |
| `keys` | Tried in order. A keybind id exactly as it appears after `key_` in `options.txt`, a WynnExtras setting as `wynnextras:<setting>`, or a physical key such as `key.keyboard.f3` for a fixed prompt. |
| `group` | Heading in the popup. `Spells` is special: those entries are the spell bar. |
| `onBar` | Candidate for the bottom bar; the first `barSlots` usable ones are shown. For item actions it means shown while hovering an item, up to `itemSlots`; unpicked ones stay in the popup. |
| `when` | Empty for always, `has_mount`, or an item condition (`item`, `item_stats`, `item_gear`) which makes it an item action. |
