package cn.lunadeer.mc.deerlingbot.configuration;

import cn.lunadeer.mc.deerlingbot.managers.AchievementThresholds;
import cn.lunadeer.mc.deerlingbot.managers.PlayerCardProfile;
import cn.lunadeer.mc.deerlingbot.utils.configuration.ConfigurationManager;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AchievementThresholdConfigurationTest {
    @TempDir Path directory;

    @AfterEach
    void restoreDefaultThresholds() {
        Configuration configuration = new Configuration();
        configuration.setYaml(new YamlConfiguration());
        configuration.prepareAchievementThresholds();
        configuration.publishAchievementThresholds();
    }

    @Test
    void oldConfigurationGetsDefaultSectionWithKilometersAndComments() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "version: 1\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(AchievementThresholds.defaults(), Configuration.achievementThresholdSnapshot());
        assertEquals(AchievementThresholds.defaults().elytra().gold() / 100_000,
                yaml.getLong("achievement-thresholds.elytra.gold"));
        assertEquals(AchievementThresholds.defaults().fishing().bronze(),
                yaml.getLong("achievement-thresholds.fishing.bronze"));
        assertEquals(List.of("bronze", "silver", "gold"),
                yaml.getConfigurationSection("achievement-thresholds.trading").getKeys(false).stream().toList());
        assertTrue(Files.readString(file).contains("整数公里"));
    }

    @Test
    void customYamlControlsEveryAchievementAndConversionKeepsCentimeterPrecision() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                achievement-thresholds:
                  mining: {bronze: 20, silver: 40, gold: 80}
                  crafting: {bronze: 30, silver: 60, gold: 120}
                  elytra: {bronze: 1, silver: 2, gold: 4}
                  fishing: {bronze: 3, silver: 6, gold: 12}
                  trading: {bronze: 5, silver: 10, gold: 20}
                  walking: {bronze: 2, silver: 4, gold: 8}
                """);
        ConfigurationManager.load(Configuration.class, file.toFile());
        AchievementThresholds snapshot = Configuration.achievementThresholdSnapshot();
        assertEquals(new AchievementThresholds.Tiers(200_000, 400_000, 800_000), snapshot.walking());
        PlayerCardProfile profile = PlayerCardProfile.fromJson(JSONObject.parseObject("""
                {"stats": {
                  "minecraft:mined":{"minecraft:stone":50},
                  "minecraft:crafted":{"minecraft:stick":90},
                  "minecraft:custom":{"minecraft:aviate_one_cm":150000,"minecraft:walk_one_cm":300000,
                    "minecraft:fish_caught":9,"minecraft:traded_with_villager":15}
                }}
                """), snapshot);
        assertEquals(List.of("银", "银", "铜", "银", "银", "铜"),
                profile.achievements().stream().map(PlayerCardProfile.Achievement::level).toList());
        assertEquals(List.of("25%", "50%", "50%", "50%", "50%", "50%"),
                profile.achievements().stream().map(PlayerCardProfile.Achievement::progressPercentText).toList());
        // Keep both the published snapshot and pre-existing player profiles immutable across reload.
        Files.writeString(file, "achievement-thresholds:\n  fishing: {bronze: 10, silver: 20, gold: 40}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(3, snapshot.fishing().bronze());
        assertEquals("银", profile.achievements().get(3).level());
        assertEquals(10, Configuration.achievementThresholdSnapshot().fishing().bronze());
        assertEquals(AchievementThresholds.defaults().mining(), Configuration.achievementThresholdSnapshot().mining());
    }

    @Test
    void missingKeyUsesFactoryDefaultInsteadOfThePreviousReloadValue() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "achievement-thresholds:\n  mining: {bronze: 1, silver: 2, gold: 3}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        Files.writeString(file, "achievement-thresholds:\n  mining: {}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(AchievementThresholds.defaults().mining(), Configuration.achievementThresholdSnapshot().mining());
        assertEquals(AchievementThresholds.defaults().mining().gold(), Configuration.achievementThresholds.mining.gold);
    }

    @Test
    void invalidThresholdsRejectBeforeMutationAndDoNotRewriteTheFile() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "achievement-thresholds:\n  trading: {bronze: 2, silver: 4, gold: 8}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        AchievementThresholds before = Configuration.achievementThresholdSnapshot();
        AchievementThresholdConfiguration mutableBefore = Configuration.achievementThresholds;
        String previousBotId = Configuration.botId;
        for (String invalid : List.of(
                "achievement-thresholds: []\n",
                "achievement-thresholds:\n  mining: 10\n",
                "achievement-thresholds:\n  fishing: {bronze: 0}\n",
                "achievement-thresholds:\n  fishing: {bronze: 20, silver: 10, gold: 40}\n",
                "achievement-thresholds:\n  fishing: {bronze: 10, silver: 10, gold: 40}\n",
                "achievement-thresholds:\n  fishing: {bronze: 1.5}\n",
                "achievement-thresholds:\n  fishing: {bronze: '10'}\n",
                "achievement-thresholds:\n  walking: {gold: 21475}\n",
                "achievement-thresholds:\n  elytra: {gold: 9223372036854775807}\n",
                "achievement-thresholds:\n  trading: {gold: 2147483648}\n")) {
            String document = "bot-id: must-not-apply\n" + invalid;
            Files.writeString(file, document);
            Exception error = assertThrows(Exception.class,
                    () -> ConfigurationManager.load(Configuration.class, file.toFile()), invalid);
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            assertInstanceOf(AchievementThresholdConfiguration.InvalidThresholds.class, cause);
            assertSame(before, Configuration.achievementThresholdSnapshot());
            assertSame(mutableBefore, Configuration.achievementThresholds);
            assertEquals(previousBotId, Configuration.botId);
            assertEquals(document, Files.readString(file));
        }
    }

    @Test
    void largeAggregateCountsRemainLongButVanillaDistanceHasAnAttainableLimit() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                achievement-thresholds:
                  mining: {bronze: 1000, silver: 1000000, gold: 3000000000}
                  walking: {bronze: 1, silver: 100, gold: 21474}
                """);
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(3_000_000_000L, Configuration.achievementThresholdSnapshot().mining().gold());
        assertEquals(2_147_400_000L, Configuration.achievementThresholdSnapshot().walking().gold());
        long[] detached = Configuration.achievementThresholdSnapshot().mining().values();
        detached[0] = -1;
        assertEquals(1000, Configuration.achievementThresholdSnapshot().mining().bronze());
    }
}
