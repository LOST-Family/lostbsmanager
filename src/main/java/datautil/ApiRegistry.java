package datautil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class ApiRegistry {

    private static Endpoint.Param pp(String name) {
        return new Endpoint.Param(name, name, OptionType.STRING, true, true, List.of());
    }

    private static Endpoint.Param pq(String name, OptionType type) {
        return new Endpoint.Param(name, name, type, false, false, List.of());
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
        // players
        new Endpoint("players", "get", "Get player by tag", "GET", "/players/{tag}", List.of(pp("tag"))),
        new Endpoint("players", "battlelog", "Get player battle log", "GET", "/players/{tag}/battlelog", List.of(pp("tag"))),
        // clubs
        new Endpoint("clubs", "get", "Get club by tag", "GET", "/clubs/{tag}", List.of(pp("tag"))),
        new Endpoint("clubs", "members", "List club members", "GET", "/clubs/{tag}/members", List.of(
            pp("tag"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        // rankings
        new Endpoint("rankings", "players", "Top players in region", "GET", "/rankings/{country_code}/players", List.of(
            pp("country_code"), pq("limit", OptionType.INTEGER)
        )),
        new Endpoint("rankings", "clubs", "Top clubs in region", "GET", "/rankings/{country_code}/clubs", List.of(
            pp("country_code"), pq("limit", OptionType.INTEGER)
        )),
        new Endpoint("rankings", "brawlers", "Top brawler players in region", "GET", "/rankings/{country_code}/brawlers/{brawler_id}", List.of(
            pp("country_code"), pp("brawler_id"), pq("limit", OptionType.INTEGER)
        )),
        // brawlers
        new Endpoint("brawlers", "list", "List all brawlers", "GET", "/brawlers", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("brawlers", "get", "Get brawler by ID", "GET", "/brawlers/{brawler_id}", List.of(pp("brawler_id"))),
        // events
        new Endpoint("events", "rotation", "Get current event rotation", "GET", "/events/rotation", List.of())
    );

    public static Endpoint find(String group, String name) {
        for (Endpoint e : ENDPOINTS) {
            if (e.group().equals(group) && e.name().equals(name)) return e;
        }
        return null;
    }

    @SuppressWarnings("null")
    public static SlashCommandData buildSlashCommand() {
        SlashCommandData cmd = Commands.slash("api", "Brawl Stars API");

        Map<String, List<Endpoint>> byGroup = new LinkedHashMap<>();
        for (Endpoint e : ENDPOINTS) {
            byGroup.computeIfAbsent(e.group(), k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<Endpoint>> entry : byGroup.entrySet()) {
            SubcommandGroupData group = new SubcommandGroupData(entry.getKey(), entry.getKey() + " endpoints");
            for (Endpoint endpoint : entry.getValue()) {
                SubcommandData sub = new SubcommandData(endpoint.name(), endpoint.description());
                for (Endpoint.Param param : endpoint.params()) {
                    OptionData opt = new OptionData(param.type(), param.name(), param.description(), param.required());
                    if (!param.choices().isEmpty()) {
                        opt.addChoices(param.choices());
                    }
                    sub.addOptions(opt);
                }
                group.addSubcommands(sub);
            }
            cmd.addSubcommandGroups(group);
        }

        return cmd;
    }
}
