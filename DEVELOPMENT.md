# Development

## Building and testing

    gradlew build                 # build/libs/wynnkeybindwizard-<version>.jar
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
