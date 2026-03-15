package commands.kickpoints;

import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Club;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import util.MessageUtil;

public class Clubconfig extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("Clubconfig"))
			return;
		String title = "Clubconfig";

		OptionMapping ClubOption = event.getOption("Club");

		if (ClubOption == null) {
			event.replyEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String Clubtag = ClubOption.getAsString();

		User userexecuted = new User(event.getUser().getId());
		if (!userexecuted.isColeaderOrHigher()) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer eines Clubs sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		Club c = new Club(Clubtag);

		if (!c.ExistsDB()) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Gib einen gültigen Club an!", MessageUtil.EmbedType.ERROR))
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

		TextInput kpdays;
		TextInput kpmax;
		if (c.getDaysKickpointsExpireAfter() != null) {
			kpdays = TextInput.create("days", "Gültigkeitsdauer von Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 60").setMinLength(1).setValue(c.getDaysKickpointsExpireAfter() + "").build();
		} else {
			kpdays = TextInput.create("days", "Gültigkeitsdauer von Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 60").setMinLength(1).build();
		}

		if (c.getMaxKickpoints() != null) {
			kpmax = TextInput.create("max", "Maximale Anzahl an Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 9").setMinLength(1).setValue(c.getMaxKickpoints() + "").build();
		} else {
			kpmax = TextInput.create("max", "Maximale Anzahl an Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 9").setMinLength(1).build();
		}

		Modal modal = Modal.create("Clubconfig_" + c.getTag(), "Clubconfig bearbeiten")
				.addActionRows(ActionRow.of(kpdays), ActionRow.of(kpmax)).build();

		event.replyModal(modal).queue();

	}

	@SuppressWarnings("null")
	@Override
	public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
		if (event.getModalId().startsWith("Clubconfig")) {
			event.deferReply().queue();
			String title = "Clubconfig";
			String daysstr = event.getValue("days").getAsString();
			String maxstr = event.getValue("max").getAsString();
			int days = -1;
			int max = -1;
			try {
				days = Integer.valueOf(daysstr);
				max = Integer.valueOf(maxstr);
			} catch (Exception ex) {
				event.getHook()
						.editOriginalEmbeds(
								MessageUtil.buildEmbed(title, "Es müssen Zahlen sein.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String Clubtag = event.getModalId().split("_")[1];

			Club c = new Club(Clubtag);

			if (c.getDaysKickpointsExpireAfter() == null) {
				DBUtil.executeUpdate(
						"INSERT INTO Club_settings (Club_tag, max_kickpoints, kickpoints_expire_after_days) VALUES (?, ?, ?)",
						Clubtag, max, days);
			} else {
				DBUtil.executeUpdate(
						"UPDATE Club_settings SET max_kickpoints = ?, kickpoints_expire_after_days = ? WHERE Club_tag = ?",
						max, days, Clubtag);
			}

			String desc = "### Die Club-Settings wurden bearbeitet.\n";
			desc += "Club: " + c.getInfoStringDB() + "\n";
			desc += "Gültigkeitsdauer von Kickpunkten: " + c.getDaysKickpointsExpireAfter() + " Tage\n";
			desc += "Maximale Anzahl an Kickpunkten: " + c.getMaxKickpoints() + "\n";

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
					.queue();
		}
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("Clubconfig"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("Club")) {
			List<Command.Choice> choices = DBManager.getClubsAutocompleteNoWaitlist(input);

			event.replyChoices(choices).queue();
		}
	}

}

