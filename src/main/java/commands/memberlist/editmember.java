package commands.memberlist;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Club;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class editmember extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("editmember"))
			return;
		event.deferReply().queue();
		String title = "Memberverwaltung";

		OptionMapping playeroption = event.getOption("player");
		OptionMapping roleoption = event.getOption("role");

		if (playeroption == null || roleoption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Alle Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playertag = playeroption.getAsString();
		String role = roleoption.getAsString();

		new Thread(() -> {
			Player p = new Player(playertag);
			Club c = p.getClubDB();

			if (!p.IsLinked()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (c == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Der Spieler ist in keinem Club.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (c.getTag().equals("warteliste")) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Diesen Befehl kannst du nicht auf die Warteliste ausführen.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String Clubtag = c.getTag();
			User userexecuted = new User(event.getUser().getId());
			if (!userexecuted.isColeaderOrHigherInClub(Clubtag)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer des Clubs sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (!(role.equals("leader") || role.equals("coleader") || role.equals("hiddencoleader") || role.equals("elder") || role.equals("member"))) {
				event.getHook()
						.editOriginalEmbeds(
								MessageUtil.buildEmbed(title, "Gib eine gültige Rolle an.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			if (role.equals("leader") && userexecuted.getClubRoles().get(Clubtag) != Player.RoleType.ADMIN) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als Leader hinzuzufügen, musst du Admin sein.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			if (role.equals("coleader") && !(userexecuted.getClubRoles().get(Clubtag) == Player.RoleType.ADMIN
					|| userexecuted.getClubRoles().get(Clubtag) == Player.RoleType.LEADER)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als Vize-Anführer hinzuzufügen, musst du Admin oder Anführer sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			if (role.equals("hiddencoleader") && !(userexecuted.getClubRoles().get(Clubtag) == Player.RoleType.ADMIN
					|| userexecuted.getClubRoles().get(Clubtag) == Player.RoleType.LEADER)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als Vize-Anführer (versteckt) hinzuzufügen, musst du Admin oder Anführer sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			DBUtil.executeUpdate("UPDATE Club_members SET Club_role = ? WHERE player_tag = ?", role, playertag);
			String rolestring = role.equals("leader") ? "Anführer"
					: role.equals("coleader") ? "Vize-Anführer"
							: role.equals("hiddencoleader") ? "Vize-Anführer (versteckt)"
								: role.equals("elder") ? "Ältester" : role.equals("member") ? "Mitglied" : null;
			String desc = null;
			try {
				desc = "Der Spieler " + MessageUtil.unformat(p.getInfoStringDB()) + " im Club " + c.getInfoStringDB()
						+ " ist nun " + rolestring + ".";
			} catch (Exception e) {
				e.printStackTrace();
			}
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
		}).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("editmember"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocompleteNoWaitlist(input,
					DBManager.InClubType.INClub);

			event.replyChoices(choices).queue();
		}
		if (focused.equals("role")) {
			List<Command.Choice> choices = new ArrayList<>();
			choices.add(new Command.Choice("Anführer", "leader"));
			choices.add(new Command.Choice("Vize-Anführer", "coleader"));
			choices.add(new Command.Choice("Vize-Anführer (versteckt)", "hiddencoleader"));
			choices.add(new Command.Choice("Ältester", "elder"));
			choices.add(new Command.Choice("Mitglied", "member"));
			event.replyChoices(choices).queue();
		}
	}

}

