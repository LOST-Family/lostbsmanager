package commands.memberlist;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Club;
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

public class memberstatus extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("memberstatus"))
			return;
		event.deferReply().queue();
		String title = "Memberstatus";

		OptionMapping ClubOption = event.getOption("Club");
		OptionMapping excludeLeadersOption = event.getOption("exclude_leaders");

		if (ClubOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String Clubtag = ClubOption.getAsString();
		
		boolean excludeLeaders = false;
		if (excludeLeadersOption != null) {
			String excludeLeadersValue = excludeLeadersOption.getAsString();
			if ("true".equalsIgnoreCase(excludeLeadersValue)) {
				excludeLeaders = true;
			} else {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der exclude_leaders Parameter muss entweder \"true\" enthalten oder nicht angegeben sein (false).",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		if (Clubtag.equals("warteliste")) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Diesen Befehl kannst du nicht auf die Warteliste ausführen.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Club c = new Club(Clubtag);

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Club existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		final boolean excludeLeadersFinal = excludeLeaders;
		new Thread(() -> {
			ArrayList<Player> playerlistdb = c.getPlayersDB();

			ArrayList<String> taglistdb = new ArrayList<>();
			playerlistdb.forEach(p -> taglistdb.add(p.getTag()));

			ArrayList<Player> playerlistapi = c.getPlayersAPI();

			ArrayList<String> taglistapi = new ArrayList<>();
			playerlistapi.forEach(p -> taglistapi.add(p.getTag()));

			ArrayList<Player> membernotinClub = new ArrayList<>();
			ArrayList<Player> inClubnotmember = new ArrayList<>();

			for (String s : taglistdb) {
				if (!taglistapi.contains(s)) {
					Player p = new Player(s);
					// Skip hidden coleaders - they don't need to be in the Club ingame
					if (p.isHiddenColeader()) {
						continue;
					}
					// Skip leaders/coleaders/admins if exclude_leaders is true
					if (excludeLeadersFinal) {
						Player.RoleType role = p.getRole();
						if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
								|| role == Player.RoleType.COLEADER) {
							continue;
						}
					}
					membernotinClub.add(p);
				}
			}

			for (String s : taglistapi) {
				if (!taglistdb.contains(s)) {
					inClubnotmember.add(new Player(s));
				}
			}

			String membernotinClubstr = "";

			for (Player p : membernotinClub) {
				membernotinClubstr += p.getInfoStringDB() + "\n";
			}

			String inClubnotmemberstr = "";

			for (Player p : inClubnotmember) {
				inClubnotmemberstr += p.getInfoStringAPI() + "\n";
			}

			String desc = "## " + c.getInfoStringDB() + "\n";

			desc += "**Mitglied, ingame nicht im Club:**\n\n";
			desc += membernotinClubstr == "" ? "---\n\n" : MessageUtil.unformat(membernotinClubstr) + "\n";
			desc += "**Kein Mitglied, ingame im Club:**\n\n";
			desc += inClubnotmemberstr == "" ? "---\n\n" : MessageUtil.unformat(inClubnotmemberstr) + "\n";

			Button refreshButton = Button.secondary("memberstatus_" + Clubtag + "_" + excludeLeadersFinal, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));

			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		}).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("memberstatus"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("Club")) {
			List<Command.Choice> choices = DBManager.getClubsAutocompleteNoWaitlist(input);

			event.replyChoices(choices).queue();
		}
		if (focused.equals("exclude_leaders")) {
			List<Command.Choice> choices = new ArrayList<>();
			if ("true".startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice("true", "true"));
			}
			event.replyChoices(choices).queue();
		}
	}

	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("memberstatus_"))
			return;

		event.deferEdit().queue();

		// Parse the button ID: memberstatus_{Clubtag}_{excludeLeaders}
		String remainder = id.substring("memberstatus_".length());
		String Clubtag;
		boolean excludeLeaders = false;
		
		int lastUnderscore = remainder.lastIndexOf("_");
		if (lastUnderscore != -1) {
			Clubtag = remainder.substring(0, lastUnderscore);
			String excludeLeadersStr = remainder.substring(lastUnderscore + 1);
			excludeLeaders = "true".equals(excludeLeadersStr);
		} else {
			// Fallback for old button IDs without exclude_leaders
			Clubtag = remainder;
		}
		
		String title = "Memberstatus";

		if (Clubtag.equals("warteliste")) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Diesen Befehl kannst du nicht auf die Warteliste ausführen.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Club c = new Club(Clubtag);

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Club existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		final boolean excludeLeadersFinal = excludeLeaders;
		new Thread(() -> {
			ArrayList<Player> playerlistdb = c.getPlayersDB();

			ArrayList<String> taglistdb = new ArrayList<>();
			playerlistdb.forEach(p -> taglistdb.add(p.getTag()));

			ArrayList<Player> playerlistapi = c.getPlayersAPI();

			ArrayList<String> taglistapi = new ArrayList<>();
			playerlistapi.forEach(p -> taglistapi.add(p.getTag()));

			ArrayList<Player> membernotinClub = new ArrayList<>();
			ArrayList<Player> inClubnotmember = new ArrayList<>();

			for (String s : taglistdb) {
				if (!taglistapi.contains(s)) {
					Player p = new Player(s);
					// Skip hidden coleaders - they don't need to be in the Club ingame
					if (p.isHiddenColeader()) {
						continue;
					}
					// Skip leaders/coleaders/admins if exclude_leaders is true
					if (excludeLeadersFinal) {
						Player.RoleType role = p.getRole();
						if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
								|| role == Player.RoleType.COLEADER) {
							continue;
						}
					}
					membernotinClub.add(p);
				}
			}

			for (String s : taglistapi) {
				if (!taglistdb.contains(s)) {
					inClubnotmember.add(new Player(s));
				}
			}

			String membernotinClubstr = "";

			for (Player p : membernotinClub) {
				membernotinClubstr += p.getInfoStringDB() + "\n";
			}

			String inClubnotmemberstr = "";

			for (Player p : inClubnotmember) {
				inClubnotmemberstr += p.getInfoStringAPI() + "\n";
			}

			String desc = "## " + c.getInfoStringDB() + "\n";

			desc += "**Mitglied, ingame nicht im Club:**\n\n";
			desc += membernotinClubstr == "" ? "---\n\n" : MessageUtil.unformat(membernotinClubstr) + "\n";
			desc += "**Kein Mitglied, ingame im Club:**\n\n";
			desc += inClubnotmemberstr == "" ? "---\n\n" : MessageUtil.unformat(inClubnotmemberstr) + "\n";

			Button refreshButton = Button.secondary("memberstatus_" + Clubtag + "_" + excludeLeadersFinal, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));

			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		}).start();
	}

}

