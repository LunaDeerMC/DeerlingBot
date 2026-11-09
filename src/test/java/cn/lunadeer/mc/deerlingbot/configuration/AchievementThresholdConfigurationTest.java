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
        assertEquals(AchievementThresholds.defaults().fishing().wood(),
                yaml.getLong("achievement-thresholds.fishing.wood"));
        assertEquals(List.of("wood", "iron", "copper", "diamond", "gold"),
                yaml.getConfigurationSection("achievement-thresholds.trading").getKeys(false).stream().toList());
        assertTrue(Files.readString(file).contains("整数公里"));
        assertTrue(Files.readString(file).contains("木、铁、铜、钻、金"));
    }

    @Test
    void customYamlControlsEveryAchievementAndConversionKeepsCentimeterPrecision() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                achievement-thresholds:
                  mining: {wood: 10, iron: 20, copper: 30, diamond: 40, gold: 50}
                  crafting: {wood: 10, iron: 20, copper: 30, diamond: 40, gold: 50}
                  elytra: {wood: 1, iron: 2, copper: 3, diamond: 4, gold: 5}
                  fishing: {wood: 10, iron: 20, copper: 30, diamond: 40, gold: 50}
                  trading: {wood: 10, iron: 20, copper: 30, diamond: 40, gold: 50}
                  walking: {wood: 1, iron: 2, copper: 3, diamond: 4, gold: 5}
                """);
        ConfigurationManager.load(Configuration.class, file.toFile());
        AchievementThresholds snapshot = Configuration.achievementThresholdSnapshot();
        assertEquals(new AchievementThresholds.Tiers(100_000, 200_000, 300_000, 400_000, 500_000), snapshot.walking());
        PlayerCardProfile profile = PlayerCardProfile.fromJson(JSONObject.parseObject("""
                {"stats": {
                  "minecraft:mined":{"minecraft:stone":35},
                  "minecraft:crafted":{"minecraft:stick":45},
                  "minecraft:custom":{"minecraft:aviate_one_cm":150000,"minecraft:walk_one_cm":250000,
                    "minecraft:fish_caught":25,"minecraft:traded_with_villager":55}
                }}
                """), snapshot);
        assertEquals(List.of("铜", "钻", "木", "铁", "金", "铁"),
                profile.achievements().stream().map(PlayerCardProfile.Achievement::level).toList());
        assertEquals(List.of("50%", "50%", "50%", "50%", "100%", "50%"),
                profile.achievements().stream().map(PlayerCardProfile.Achievement::progressPercentText).toList());
        // Published snapshots and already-created player profiles stay immutable across reload.
        Files.writeString(file, "achievement-thresholds:\n  fishing: {wood: 20, iron: 40, copper: 60, diamond: 80, gold: 100}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(10, snapshot.fishing().wood());
        assertEquals("铁", profile.achievements().get(3).level());
        assertEquals(20, Configuration.achievementThresholdSnapshot().fishing().wood());
        assertEquals(AchievementThresholds.defaults().mining(), Configuration.achievementThresholdSnapshot().mining());
    }

    @Test
    void publishedThreeTierDefaultsUpgradeToCalibratedFiveTierDefaults() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                achievement-thresholds:
                  mining: {bronze: 500, silver: 36000, gold: 680000}
                  crafting: {bronze: 300, silver: 31000, gold: 1200000}
                  elytra: {bronze: 8, silver: 360, gold: 5100}
                  fishing: {bronze: 5, silver: 98, gold: 1100}
                  trading: {bronze: 25, silver: 390, gold: 18000}
                  walking: {bronze: 5, silver: 120, gold: 1500}
                custom-section: {preserve: yes}
                """);
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(AchievementThresholds.defaults(), Configuration.achievementThresholdSnapshot());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        for (String category : List.of("mining", "crafting", "elytra", "fishing", "trading", "walking")) {
            assertEquals(List.of("wood", "iron", "copper", "diamond", "gold"),
                    yaml.getConfigurationSection("achievement-thresholds." + category).getKeys(false).stream().toList());
        }
        assertTrue(yaml.getBoolean("custom-section.preserve"));
        String once = Files.readString(file);
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(once, Files.readString(file), "second load must not repeat migration or alter thresholds");
    }

    @Test
    void customizedLegacyMilestonesSurviveWhileIntermediateTiersAreInserted() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                achievement-thresholds:
                  mining: {bronze: 100, silver: 10000, gold: 1000000}
                  walking: {bronze: 1, silver: 9, gold: 81}
                """);
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(new AchievementThresholds.Tiers(100, 1000, 10000, 100000, 1000000),
                Configuration.achievementThresholdSnapshot().mining());
        assertEquals(AchievementThresholds.Tiers.kilometers(1, 3, 9, 27, 81),
                Configuration.achievementThresholdSnapshot().walking());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        assertFalse(yaml.contains("achievement-thresholds.walking.silver"));
        assertEquals(27, yaml.getLong("achievement-thresholds.walking.diamond"));
        assertEquals(1000, Configuration.achievementThresholds.mining.iron);
    }

    @Test
    void sparseLegacyConfigurationUsesPublishedOldDefaultsBeforeMigration() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                achievement-thresholds:
                  fishing: {bronze: 5}
                  trading: {bronze: 100}
                """);
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(AchievementThresholds.defaults().fishing(), Configuration.achievementThresholdSnapshot().fishing());
        assertEquals(new AchievementThresholds.Tiers(100, 197, 390, 2649, 18000),
                Configuration.achievementThresholdSnapshot().trading());
    }

    @Test
    void missingKeyUsesFactoryDefaultInsteadOfThePreviousReloadValue() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "achievement-thresholds:\n  mining: {wood: 1, iron: 2, copper: 3, diamond: 4, gold: 5}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        Files.writeString(file, "achievement-thresholds:\n  mining: {}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(AchievementThresholds.defaults().mining(), Configuration.achievementThresholdSnapshot().mining());
        assertEquals(AchievementThresholds.defaults().mining().gold(), Configuration.achievementThresholds.mining.gold);
    }

    @Test
    void goldOnlyIsANewSparseConfigurationAndDoesNotTriggerLegacyMigration() throws Exception {
        Path file = directory.resolve("config.yml");
        long customGold = AchievementThresholds.defaults().mining().gold() + 1;
        Files.writeString(file, "achievement-thresholds:\n  mining: {gold: " + customGold + "}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(customGold, Configuration.achievementThresholdSnapshot().mining().gold());
        assertEquals(AchievementThresholds.defaults().mining().copper(),
                Configuration.achievementThresholdSnapshot().mining().copper());
    }

    @Test
    void invalidThresholdsRejectBeforeMutationAndDoNotRewriteTheFile() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "achievement-thresholds:\n  trading: {wood: 2, iron: 4, copper: 6, diamond: 8, gold: 10}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        AchievementThresholds before = Configuration.achievementThresholdSnapshot();
        AchievementThresholdConfiguration mutableBefore = Configuration.achievementThresholds;
        String previousBotId = Configuration.botId;
        for (String invalid : List.of(
                "achievement-thresholds: []\n",
                "achievement-thresholds:\n  mining: 10\n",
                "achievement-thresholds:\n  fishing: {wood: 0}\n",
                "achievement-thresholds:\n  fishing: {wood: 20, iron: 10, copper: 30, diamond: 40, gold: 50}\n",
                "achievement-thresholds:\n  fishing: {wood: 10, iron: 10, copper: 30, diamond: 40, gold: 50}\n",
                "achievement-thresholds:\n  fishing: {wood: 1.5}\n",
                "achievement-thresholds:\n  fishing: {wood: '10'}\n",
                "achievement-thresholds:\n  walking: {gold: 21475}\n",
                "achievement-thresholds:\n  elytra: {gold: 9223372036854775807}\n",
                "achievement-thresholds:\n  trading: {gold: 2147483648}\n",
                "achievement-thresholds:\n  mining: {bronze: 100, silver: 10000, gold: 1000000}\n  walking: {wood: -1}\n",
                "achievement-thresholds:\n  fishing: {bronze: 1, silver: 2, gold: 3}\n",
                "achievement-thresholds:\n  fishing: {bronze: 0, silver: 10, gold: 100}\n",
                "achievement-thresholds:\n  fishing: {bronze: 1.5, silver: 10, gold: 100}\n",
                "achievement-thresholds:\n  fishing: {bronze: 5, silver: 10, gold: 9}\n",
                "achievement-thresholds:\n  walking: {bronze: 1, silver: 100, gold: 21475}\n",
                "achievement-thresholds:\n  mining: {bronze: 100, silver: 10000, wood: 100, gold: 1000000}\n")) {
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
    void failedLaterCategoryDoesNotEvenMutateTheParsedYaml() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("achievement-thresholds:\n  mining: {bronze: 100, silver: 10000, gold: 1000000}\n  walking: {wood: 0}\n");
        String before = yaml.saveToString();
        Configuration configuration = new Configuration();
        configuration.setYaml(yaml);
        assertThrows(AchievementThresholdConfiguration.InvalidThresholds.class, configuration::prepareAchievementThresholds);
        assertEquals(before, yaml.saveToString());
    }

    @Test
    void largeAggregateCountsRemainLongButVanillaDistanceHasAnAttainableLimit() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                achievement-thresholds:
                  mining: {wood: 1000, iron: 10000, copper: 1000000, diamond: 1000000000, gold: 9223372036854775807}
                  walking: {wood: 1, iron: 10, copper: 100, diamond: 1000, gold: 21474}
                """);
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(Long.MAX_VALUE, Configuration.achievementThresholdSnapshot().mining().gold());
        assertEquals(2_147_400_000L, Configuration.achievementThresholdSnapshot().walking().gold());
        long[] detached = Configuration.achievementThresholdSnapshot().mining().values();
        detached[0] = -1;
        assertEquals(1000, Configuration.achievementThresholdSnapshot().mining().wood());
    }

    @Test
    void legacyInterpolationAvoidsLongOverflowAndKeepsStrictOrdering() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "achievement-thresholds:\n  mining: {bronze: 4000000000, silver: 16000000000, gold: 64000000000}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(new AchievementThresholds.Tiers(4_000_000_000L, 8_000_000_000L, 16_000_000_000L,
                32_000_000_000L, 64_000_000_000L), Configuration.achievementThresholdSnapshot().mining());
        Files.writeString(file, "achievement-thresholds:\n  mining: {bronze: 1, silver: 3, gold: 5}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(new AchievementThresholds.Tiers(1, 2, 3, 4, 5), Configuration.achievementThresholdSnapshot().mining());
        Files.writeString(file, "achievement-thresholds:\n  mining: {bronze: 9223372036854775801, silver: 9223372036854775804, gold: 9223372036854775807}\n");
        ConfigurationManager.load(Configuration.class, file.toFile());
        assertEquals(new AchievementThresholds.Tiers(Long.MAX_VALUE - 6, Long.MAX_VALUE - 5,
                Long.MAX_VALUE - 3, Long.MAX_VALUE - 2, Long.MAX_VALUE), Configuration.achievementThresholdSnapshot().mining());
    }
}
