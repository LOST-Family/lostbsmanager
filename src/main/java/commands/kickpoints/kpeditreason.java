package commands.kickpoints;

import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Club;
import datawrapper.KickpointReason;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class kpeditreason extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpeditreason"))
			return;
		event.deferReply().queue();
		String title = "Kickpunkt-Grund Vorlage";

		OptionMapping ClubOption = event.getOption("Club");
		OptionMapping reasonoption = event.getOption("reason");
		OptionMapping amountoption = event.getOption("amount");
		OptionMapping indexOption = event.getOption("index");

		if (ClubOption == null || reasonoption == null || amountoption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Alle Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String reason = reasonoption.getAsString();
		String Clubtag = ClubOption.getAsString();
		int amount = amountoption.getAsInt();

		Club c = new Club(Clubtag);

		User userexecuted = new User(event.getUser().getId());
		if (!userexecuted.isColeaderOrHigher()) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer eines Clubs sein, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Club existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (Clubtag.equals("warteliste")) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Diesen Befehl kannst du nicht auf die Warteliste ausführen.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		KickpointReason kpreason = new KickpointReason(reason, Clubtag);

		if (!kpreason.Exists()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Diese Begründung existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (indexOption != null) {
			int index = indexOption.getAsInt();
			DBUtil.executeUpdate("UPDATE kickpoint_reasons SET amount = ?, index = ? WHERE name = ? AND Club_tag = ?",
					amount, index, reason,
					Clubtag);
		} else {
			DBUtil.executeUpdate("UPDATE kickpoint_reasons SET amount = ? WHERE name = ? AND Club_tag = ?", amount,
					reason,
					Clubtag);
		}

		Club Club = new Club(Clubtag);

		String desc = "Der Kickpunkt-Grund wurde bearbeitet.\n";
		desc += "Grund: " + reason + "\n";
		desc += "Club: " + Club.getInfoStringDB() + "\n";
		desc += "Anzahl: " + amount + "\n";
		if (indexOption != null) {
			desc += "Neuer Index: " + indexOption.getAsInt() + "\n";
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpeditreason"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("Club")) {
			List<Command.Choice> choices = DBManager.getClubsAutocompleteNoWaitlist(input);

			event.replyChoices(choices).queue();
		}
		if (focused.equals("reason")) {
			List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input,
					event.getOption("Club").getAsString());

			event.replyChoices(choices).queue();
		}
	}

}

