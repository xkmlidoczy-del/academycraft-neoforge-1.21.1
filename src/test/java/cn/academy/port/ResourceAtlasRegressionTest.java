package cn.academy.port;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Resource existence alone is insufficient: modern atlas definitions must include each sprite. */
public final class ResourceAtlasRegressionTest {
    private static int assertions;
    private static final Set<String> MODELS = new HashSet<>();
    private static final Set<String> SPRITES = new HashSet<>();

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static JsonObject json(String path) {
        try (var stream = ResourceAtlasRegressionTest.class.getResourceAsStream(path)) {
            check(stream != null, "resource exists: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new AssertionError(path, failure);
        }
    }

    private static void model(String id) {
        if (!MODELS.add(id)) return;
        var model = json("/assets/academy/models/item/" + id + ".json");
        for (var entry : model.getAsJsonObject("textures").entrySet()) {
            var texture = entry.getValue().getAsString();
            check(texture.startsWith("academy:items/"), "classic icon sprite identifier: " + texture);
            check(ResourceAtlasRegressionTest.class.getResource(
                    "/assets/academy/textures/" + texture.substring("academy:".length()) + ".png") != null,
                    "sprite PNG exists: " + texture);
            SPRITES.add(texture);
        }
        if (model.has("overrides")) for (var entry : model.getAsJsonArray("overrides")) {
            var target = entry.getAsJsonObject().get("model").getAsString();
            check(target.startsWith("academy:item/"), "override remains in item model namespace");
            model(target.substring("academy:item/".length()));
        }
    }

    public static void main(String[] args) {
        var atlas = json("/assets/minecraft/atlases/blocks.json");
        boolean includesClassicItems = false;
        for (var entry : atlas.getAsJsonArray("sources")) {
            var source = entry.getAsJsonObject();
            includesClassicItems |= source.get("type").getAsString().equals("minecraft:directory")
                    && source.get("source").getAsString().equals("items")
                    && source.get("prefix").getAsString().equals("items/");
        }
        check(includesClassicItems, "modern blocks/item atlas explicitly scans classic textures/items");
        for (var id : new String[]{"portable_developer", "induction_factor", "magnetic_coil", "coin", "needle"})
            model(id);
        check(MODELS.size() == 11, "all five inventory models and six overrides visited");
        check(SPRITES.size() == 10, "all ten unique current item sprites have atlas coverage");
        System.out.println("PASS " + assertions + " item-model/atlas resource assertions");
    }
}
