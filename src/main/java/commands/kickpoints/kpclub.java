package commands.kickpoints;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Club;
import datawrapper.Kickpoint;
import datawrapper.Player;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class kpclub extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpclub"))
			return;
		event.deferReply().queue();
		String title = "Aktive Kickpunkte des Clubs";

		OptionMapping clubOption = event.getOption("club");

		if (clubOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter Club ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clubtag = clubOption.getAsString();

		if (clubtag.equals("warteliste")) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Diesen Befehl kannst du nicht auf die Warteliste ausführen.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		new Thread(() -> {
			String desc = "";
			ArrayList<Player> playerlist = new ArrayList<>();

			if (clubtag.equals("all")) {
				for (String clubtags : DBManager.getAllClubs()) {
					if (!clubtags.equals("warteliste"))
						playerlist.addAll(new Club(clubtags).getPlayersDB());
				}
				desc = "### Kickpunkte aller Spieler aller Clubs:\n";
			} else {
				Club c = new Club(clubtag);

				if (!c.ExistsDB()) {
					event.getHook().editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Club existiert nicht.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}
				playerlist.addAll(c.getPlayersDB());
				desc = "### Kickpunkte aller Spieler des Clubs " + c.getInfoStringDB() + ":\n";
			}

			HashMap<String, Integer> kpamounts = new HashMap<>();

			for (Player p : playerlist) {
				ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

				int totalkps = 0;
				for (Kickpoint kpi : activekps) {
					totalkps += kpi.getAmount();
				}
				if (totalkps > 0) {
					kpamounts.put(p.getInfoStringDB(), totalkps);
				}
			}

			LinkedHashMap<String, Integer> sorted = kpamounts.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, _) -> e1, LinkedHashMap::new));

			// Ausgabe sortiert
			for (String key : sorted.keySet()) {
				String kp = sorted.get(key) == 1 ? "Kickpunkt" : "Kickpunkte";
				desc += key + ": " + sorted.get(key) + " " + kp + "\n\n";
			}

			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
							"Zuletzt aktualisiert am " + formatiert))
					.setActionRow(Button.secondary("kpclub_" + clubtag, "\u200B").withEmoji(Emoji.fromUnicode("🔁")))
					.queue();
		}).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpclub"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("club")) {
			List<Command.Choice> choices = DBManager.getClubsAutocompleteNoWaitlist(input);
			choices.add(new Command.Choice("Alle Clubs", "all"));

			event.replyChoices(choices).queue();
		}
	}

	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("kpclub_"))
			return;

		event.deferEdit().queue();

		String clubtag = id.substring("kpclub_".length());
		String title = "Aktive Kickpunkte des Clubs";

		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();
		String descr;
		ArrayList<Player> playerlist = new ArrayList<>();

		if (clubtag.equals("all")) {
			for (String clubtags : DBManager.getAllClubs()) {
				if (!clubtags.equals("warteliste"))
					playerlist.addAll(new Club(clubtags).getPlayersDB());
			}
			descr = "### Kickpunkte aller Spieler aller Clubs:\n";
		} else {
			Club c = new Club(clubtag);

			if (!c.ExistsDB()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Club existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			playerlist.addAll(c.getPlayersDB());
			descr = "### Kickpunkte aller Spieler des Clubs " + c.getInfoStringDB() + ":\n";
		}

		HashMap<String, Integer> kpamounts = new HashMap<>();

		new Thread(new Runnable() {
			String desc = descr;

			@Override
			public void run() {

				for (Player p : playerlist) {
					ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

					int totalkps = 0;
					for (Kickpoint kpi : activekps) {
						totalkps += kpi.getAmount();
					}
					if (totalkps > 0) {
						kpamounts.put(MessageUtil.unformat(p.getInfoStringDB()), totalkps);
					}
				}

				LinkedHashMap<String, Integer> sorted = kpamounts.entrySet().stream()
						.sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).collect(Collectors
								.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, _) -> e1, LinkedHashMap::new));

				// Ausgabe sortiert
				for (String key : sorted.keySet()) {
					String kp = sorted.get(key) == 1 ? "Kickpunkt" : "Kickpunkte";
					desc += key + ": " + sorted.get(key) + " " + kp + "\n\n";
				}

				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);

				event.getInteraction().getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc,
						MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert)).queue();

			}
		}).start();
	}

}



