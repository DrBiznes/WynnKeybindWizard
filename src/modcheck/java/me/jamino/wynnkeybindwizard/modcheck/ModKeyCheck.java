package me.jamino.wynnkeybindwizard.modcheck;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import me.jamino.wynnkeybindwizard.compat.WynnExtrasKeys;
import me.jamino.wynnkeybindwizard.config.BarConfig;

/**
 * {@code gradlew checkModKeys}: is every keybind id the layout uses still in the newest release of
 * the mod it belongs to? An entry whose keybind vanished is hidden without a word, so a mod
 * renaming one is otherwise only noticed when someone misses the prompt.
 *
 * <p>Downloads the newest release of each mod for the Minecraft version from Modrinth (cached in
 * {@code build/modcheck}) and, with {@code --mods <folder>}, also reads every jar in that folder,
 * e.g. a pack's mods folder. Each id belongs to one mod (by its prefix, see {@link #MODS}) and is
 * only looked for in that mod's jars, recognised by the id in their fabric.mod.json, so another
 * mod that merely mentions the id (Class Keybind Profiles lists the spell keybinds) cannot pass
 * it. It counts as present when it is a translation key in a lang file or a string constant in a
 * class: mods name their keybinds with one or the other. WynnExtras settings
 * ({@code wynnextras:<setting>}) must be a public int field of its config class. When a mod is
 * checked twice (Modrinth and the folder), the id must be in both.
 *
 * <p>Exits non-zero when an id is missing from a mod that was checked. Ids of a mod that has no
 * release for this Minecraft version and is not in {@code --mods} are reported as not checked.
 */
public final class ModKeyCheck {
    /**
     * A mod the layout uses: its Modrinth slug (null when it is not on Modrinth), its Fabric mod id,
     * and the prefix of its keybind ids. WynnMod's ids are plain names with no prefix, so it comes
     * last and owns every id that matches no other prefix. Add a mod here before the layout uses it.
     */
    private record Mod(String name, String slug, String modId, String idPrefix) {}

    private static final List<Mod> MODS = List.of(
            new Mod("Wynntils", "wynntils", "wynntils", "wynntils."),
            new Mod("WynnExtras", "wynnextras", "wynnextras", WynnExtrasKeys.PREFIX),
            new Mod("Wynncraft Spell Caster", "wynncraft-spell-caster", "wynncraft-spell-caster", "key.wynncraft-spell-caster."),
            new Mod("Better Wynn Macros", "better-wynn-macros", "ktnwynnmacros", "key.ktnwynnmacros."),
            new Mod("Wynnventory", "wynnventory", "wynnventory", "key.wynnventory."),
            new Mod("Nimble ReWynnded", "nimble-rewynnded", "nimblerewynnded", "key.nimblerewynnded."),
            new Mod("Zoomify", "zoomify", "zoomify", "zoomify."),
            new Mod("Iris", "iris", "iris", "iris."),
            new Mod("WynnTitles", null, "wynntitles", "key.wynntitles."),
            new Mod("WynnMod", "wynnmod", "wynnmod", ""));

    private static final String USER_AGENT = "wynnkeybindwizard-modcheck";
    private static final String WYNNEXTRAS_CONFIG = WynnExtrasKeys.CONFIG_CLASS.replace('.', '/');
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    /** What one jar offers: lang keys, class string constants, and public instance fields per class. */
    private static final class Jar {
        final String label;
        /** From fabric.mod.json; null when the jar has none. */
        String modId;
        final Set<String> langKeys = new HashSet<>();
        final Set<String> strings = new HashSet<>();
        final Map<String, Map<String, String>> fields = new HashMap<>();

        Jar(String label) {
            this.label = label;
        }
    }

    private ModKeyCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ModKeyCheck <minecraft version> <cache dir> [--mods <folder>] [--config <wynnkeybindwizard.json>]");
            System.exit(2);
        }
        String minecraft = args[0];
        Path cache = Path.of(args[1]);
        Path modsDir = null;
        Path configFile = null;
        for (int i = 2; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--mods" -> modsDir = Path.of(args[i + 1]);
                case "--config" -> configFile = Path.of(args[i + 1]);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }

        BarConfig layout = configFile == null
                ? new BarConfig()
                : new Gson().fromJson(Files.readString(configFile), BarConfig.class);
        Set<String> ids = new LinkedHashSet<>();
        for (BarConfig.Entry entry : layout.keybinds) ids.addAll(entry.keys);
        System.out.println("Checking " + ids.size() + " keybind ids from "
                + (configFile == null ? "the default layout" : configFile) + " against Minecraft " + minecraft + "\n");

        // ---- sources
        Set<String> vanilla = vanillaLangKeys();
        List<Jar> jars = new ArrayList<>();
        Files.createDirectories(cache);
        for (Mod mod : MODS) {
            if (mod.slug == null) continue;
            JsonObject version = newestVersion(mod.slug, minecraft);
            if (version == null) {
                System.out.println("  " + mod.name + ": no release for " + minecraft + " on Modrinth");
                continue;
            }
            String number = version.get("version_number").getAsString();
            String type = version.get("version_type").getAsString();
            Path file = download(version, cache.resolve(mod.slug + "-" + version.get("id").getAsString() + ".jar"));
            jars.add(index(Files.readAllBytes(file), mod.name + " " + number));
            System.out.println("  " + mod.name + " " + number + (type.equals("release") ? "" : " (" + type + ")"));
        }
        if (modsDir != null) {
            try (var stream = Files.list(modsDir)) {
                for (Path file : stream.filter(f -> f.toString().endsWith(".jar")).sorted().toList()) {
                    jars.add(index(Files.readAllBytes(file), file.getFileName().toString()));
                }
            }
            System.out.println("  every jar in " + modsDir);
        }
        System.out.println();

        // ---- ids
        int missing = 0;
        int unchecked = 0;
        int warnings = 0;
        for (String id : ids) {
            if (id.startsWith("key.keyboard.") || id.startsWith("key.mouse.")) continue; // a physical key, nothing to look up
            if (vanilla.contains(id)) continue;

            Mod owner = owner(id);
            List<Jar> ownerJars = jars.stream().filter(jar -> owner.modId.equals(jar.modId)).toList();
            if (ownerJars.isEmpty()) {
                System.out.println("skipped  " + id + "  (" + owner.name + " not checked"
                        + (owner.slug == null ? "; not on Modrinth, pass -PmodsDir=<pack mods folder>" : "") + ")");
                unchecked++;
                continue;
            }

            List<String> present = new ArrayList<>();
            List<String> absent = new ArrayList<>();
            String warning = null;
            for (Jar jar : ownerJars) {
                boolean has;
                if (id.startsWith(WynnExtrasKeys.PREFIX)) {
                    String setting = id.substring(WynnExtrasKeys.PREFIX.length());
                    Map<String, String> config = jar.fields.get(WYNNEXTRAS_CONFIG);
                    has = config != null && "I".equals(config.get(setting));
                    if (has && !hasFeatureSwitch(config, setting)) {
                        warning = "no " + setting + "Enabled switch in " + jar.label
                                + ": the prompt will show even while the feature is off";
                    }
                } else {
                    has = jar.langKeys.contains(id) || jar.strings.contains(id);
                }
                (has ? present : absent).add(jar.label);
            }

            if (absent.isEmpty()) {
                System.out.println("ok       " + id + "  (" + String.join(", ", present) + ")");
            } else {
                System.out.println("MISSING  " + id + "  (not in " + String.join(", ", absent) + ")");
                missing++;
            }
            if (warning != null) {
                System.out.println("  WARN   " + warning);
                warnings++;
            }
        }

        System.out.println();
        System.out.println(missing == 0
                ? "All checked keybind ids are present."
                : missing + " keybind id(s) missing: the mod renamed or removed them. Update the entries in BarConfig.");
        if (unchecked > 0) System.out.println(unchecked + " id(s) not checked (see above).");
        if (warnings > 0) System.out.println(warnings + " warning(s).");
        System.exit(missing == 0 ? 0 : 1);
    }

    /** The first mod whose prefix the id starts with; WynnMod's empty prefix, last in the list, takes the rest. */
    private static Mod owner(String id) {
        for (Mod mod : MODS) if (id.startsWith(mod.idPrefix)) return mod;
        throw new IllegalStateException("MODS must end with a mod that has an empty prefix");
    }

    /** Same rule as {@link WynnExtrasKeys}: {@code <setting>Enabled}, or {@code <name>Enabled} for {@code <name>Key}. */
    private static boolean hasFeatureSwitch(Map<String, String> config, String setting) {
        if ("Z".equals(config.get(setting + "Enabled"))) return true;
        return setting.endsWith("Key") && "Z".equals(config.get(setting.substring(0, setting.length() - 3) + "Enabled"));
    }

    // ---- Modrinth

    /** Newest version for this Minecraft version on Fabric, of any release type (some mods only publish betas). */
    private static JsonObject newestVersion(String slug, String minecraft) throws IOException, InterruptedException {
        String query = "?game_versions=" + URLEncoder.encode("[\"" + minecraft + "\"]", StandardCharsets.UTF_8)
                + "&loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
        HttpResponse<String> response = HTTP.send(request("https://api.modrinth.com/v2/project/" + slug + "/version" + query),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) throw new IOException("no Modrinth project '" + slug + "'");
        if (response.statusCode() != 200) throw new IOException("Modrinth answered " + response.statusCode() + " for " + slug);
        JsonObject newest = null;
        for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray()) {
            JsonObject version = element.getAsJsonObject();
            if (newest == null || version.get("date_published").getAsString().compareTo(newest.get("date_published").getAsString()) > 0) {
                newest = version;
            }
        }
        return newest;
    }

    private static Path download(JsonObject version, Path target) throws IOException, InterruptedException {
        if (Files.exists(target)) return target;
        JsonArray files = version.getAsJsonArray("files");
        JsonObject chosen = files.get(0).getAsJsonObject();
        for (JsonElement file : files) {
            if (file.getAsJsonObject().get("primary").getAsBoolean()) chosen = file.getAsJsonObject();
        }
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> response = HTTP.send(request(chosen.get("url").getAsString()), HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() != 200) throw new IOException("download failed (" + response.statusCode() + "): " + chosen.get("url"));
        Files.move(partial, target);
        return target;
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).build();
    }

    // ---- jar contents

    private static Set<String> vanillaLangKeys() throws IOException {
        try (InputStream in = ModKeyCheck.class.getClassLoader().getResourceAsStream("assets/minecraft/lang/en_us.json")) {
            if (in == null) throw new IOException("Minecraft's en_us.json is not on the classpath");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().keySet();
        }
    }

    private static Jar index(byte[] jarBytes, String label) throws IOException {
        Jar jar = new Jar(label);
        index(jarBytes, jar, 0);
        return jar;
    }

    private static void index(byte[] jarBytes, Jar jar, int depth) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String name = entry.getName();
                if (depth == 0 && name.equals("fabric.mod.json")) {
                    jar.modId = modId(zip.readAllBytes());
                } else if (name.endsWith(".class")) {
                    readClass(zip.readAllBytes(), jar);
                } else if (name.contains("/lang/") && name.endsWith(".json")) {
                    try {
                        JsonElement lang = JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                        if (lang.isJsonObject()) jar.langKeys.addAll(lang.getAsJsonObject().keySet());
                    } catch (RuntimeException ignored) {
                        // Not a plain lang file; nothing to learn from it.
                    }
                } else if (name.endsWith(".jar") && depth < 2) {
                    index(zip.readAllBytes(), jar, depth + 1); // bundled libraries
                }
            }
        }
    }

    private static String modId(byte[] fabricModJson) {
        try {
            JsonElement id = JsonParser.parseString(new String(fabricModJson, StandardCharsets.UTF_8)).getAsJsonObject().get("id");
            return id == null ? null : id.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Collects every UTF-8 constant (string literals among them) and, for each class, its public instance fields. */
    private static void readClass(byte[] bytes, Jar jar) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0xCAFEBABE) return;
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNameIndex = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> {
                        utf8[i] = in.readUTF();
                        jar.strings.add(utf8[i]);
                    }
                    case 7 -> classNameIndex[i] = in.readUnsignedShort();
                    case 8, 16, 19, 20 -> in.skipNBytes(2);
                    case 15 -> in.skipNBytes(3);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                    case 5, 6 -> {
                        in.skipNBytes(8);
                        i++; // takes two slots
                    }
                    default -> {
                        return; // unknown constant: a newer class format than this reader knows
                    }
                }
            }
            in.readUnsignedShort();
            String className = utf8[classNameIndex[in.readUnsignedShort()]];
            in.readUnsignedShort();
            in.skipNBytes(2L * in.readUnsignedShort());
            Map<String, String> fields = new HashMap<>();
            int fieldCount = in.readUnsignedShort();
            for (int f = 0; f < fieldCount; f++) {
                int access = in.readUnsignedShort();
                String name = utf8[in.readUnsignedShort()];
                String descriptor = utf8[in.readUnsignedShort()];
                int attributes = in.readUnsignedShort();
                for (int a = 0; a < attributes; a++) {
                    in.readUnsignedShort();
                    in.skipNBytes(in.readInt());
                }
                if (Modifier.isPublic(access) && !Modifier.isStatic(access)) fields.put(name, descriptor);
            }
            if (className != null) jar.fields.put(className, fields);
        } catch (IOException | RuntimeException ignored) {
            // A malformed or unusual class; its strings so far are still collected.
        }
    }
}
