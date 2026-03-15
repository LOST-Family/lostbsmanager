package commands.memberlist;

import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Club;
import datawrapper.Player;
import datawrapper.User;
import lostbsmanager.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class transfermember extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("transfermember"))
			return;
		event.deferReply().queue();
		String title = "Memberverwaltung";

		OptionMapping playeroption = event.getOption("player");
		OptionMapping cluboption = event.getOption("club");

		if (playeroption == null || cluboption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Beide Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playertag = playeroption.getAsString();
		String newclubtag = cluboption.getAsString();

		new Thread(() -> {
			Club newclub = new Club(newclubtag);

			if (!newclub.ExistsDB()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Club ist existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Player player = new Player(playertag);

			if (!player.IsLinked()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Player.RoleType role = player.getRole();

			Club playerclub = player.getClubDB();

			if (playerclub == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Spieler ist in keinem Club.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String clubtag = playerclub.getTag();

			User userexecuted = new User(event.getUser().getId());
			if (!clubtag.equals("warteliste")) {
				if (!userexecuted.isColeaderOrHigherInClub(clubtag)) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer des Clubs sein, in dem der Spieler gerade ist, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			} else {
				if (!userexecuted.isColeaderOrHigher()) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer eines Clubs sein, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			if (!newclubtag.equals("warteliste")) {
				if (!userexecuted.isColeaderOrHigherInClub(newclubtag)) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer des Clubs sein, in den du den Spieler transferieren möchtest, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			} else {
				if (!userexecuted.isColeaderOrHigher()) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer eines Clubs sein, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			if (clubtag.equals(newclubtag)) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du kannst einen Spieler nicht in den gleichen Club verschieben.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (role == Player.RoleType.PRESIDENT && userexecuted.getClubRoles().get(clubtag) != Player.RoleType.ADMIN) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als Leader zu entfernen, musst du Admin sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			if (role == Player.RoleType.COPRESIDENT && !(userexecuted.getClubRoles().get(clubtag) == Player.RoleType.ADMIN
					|| userexecuted.getClubRoles().get(clubtag) == Player.RoleType.PRESIDENT)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als Vize-Anführer zu entfernen, musst du Admin oder Anführer sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			DBUtil.executeUpdate("UPDATE club_members SET club_tag = ?, club_role = ? WHERE player_tag = ?", newclubtag,
					"member", playertag);

			String desc = "";
			if (!clubtag.equals("warteliste")) {
				if (!newclubtag.equals("warteliste")) {
					desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " wurde vom Club "
							+ playerclub.getInfoStringDB() + " zum Club " + newclub.getInfoStringDB() + " verschoben.";
				} else {
					desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " wurde vom Club "
							+ playerclub.getInfoStringDB() + " zur Warteliste verschoben.";
				}
			} else {
				desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB())
						+ " wurde von der Warteliste zum Club " + newclub.getInfoStringDB() + " verschoben.";
			}
			String userid = player.getUser().getUserID();
			Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
			Member member = guild.getMemberById(userid);
			if (member != null) {
				if (!clubtag.equals("warteliste")) {
					String memberroleid = playerclub.getRoleID(Club.Role.MEMBER);
					Role memberrole = guild.getRoleById(memberroleid);
					if (member.getRoles().contains(memberrole)) {
						guild.removeRoleFromMember(member, memberrole).queue();
						desc += "\n\n";
						desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + memberroleid
								+ "> entzogen.**\n";
					} else {
						desc += "\n\n";
						desc += "**Der User <@" + userid + "> hat die Rolle <@&" + memberroleid
								+ "> bereits nicht mehr.**\n";
					}
				}
				if (!newclubtag.equals("warteliste")) {
					String newmemberroleid = newclub.getRoleID(Club.Role.MEMBER);
					Role newmemberrole = guild.getRoleById(newmemberroleid);
					if (member.getRoles().contains(newmemberrole)) {
						desc += "\n\n";
						desc += "**Der User <@" + userid + "> hat die Rolle <@&" + newmemberroleid + "> bereits.**\n";
					} else {
						guild.addRoleToMember(member, newmemberrole).queue();
						desc += "\n\n";
						desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + newmemberroleid + "> gegeben.**\n";
					}
				}
			} else {
				desc += "\n\n**Der User <@" + userid
						+ "> existiert nicht auf dem Server. Ihm wurde somit keine Rolle hinzugefügt.**";
			}
			MessageChannelUnion channel = event.getChannel();
			MessageUtil.sendUserPingHidden(channel, userid);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
					.queue();
		}).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("transfermember"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClubType.INCLUB);

			event.replyChoices(choices).queue();
		}
		if (focused.equals("club")) {
			List<Command.Choice> choices = DBManager.getClubsAutocomplete(input);
			Player p = new Player(event.getOption("player").getAsString());
			Club c = p.getClubDB();
			Command.Choice todelete = null;
			if (c != null) {
				for (Command.Choice choice : choices) {
					if (choice.getAsString().equals(c.getTag())) {
						todelete = choice;
						break;
					}
				}
			}
			if (todelete != null) {
				choices.remove(todelete);
			}

			event.replyChoices(choices).queue();
		}
	}

}






