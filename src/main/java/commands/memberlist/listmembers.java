package commands.memberlist;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.Connection;
import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Club;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class listmembers extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("listmembers"))
			return;
		event.deferReply().queue();
		String title = "Memberliste";

		OptionMapping ClubOption = event.getOption("Club");

		if (ClubOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		final String Clubtag_raw = ClubOption.getAsString();
		final boolean markedOnly = Clubtag_raw.startsWith("marked_");
		final String Clubtag = markedOnly ? Clubtag_raw.substring("marked_".length()) : Clubtag_raw;
		final boolean isAllMarked = Clubtag.equals("all_marked");

		final String title_initial = "Memberliste";
		final String title_final = (markedOnly || isAllMarked) ? "Markierte Spieler" : title_initial;

		if ((markedOnly || isAllMarked) && !new User(event.getUser().getId()).isColeaderOrHigher()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title_final,
							"Du hast keine Berechtigung für diese Option (Coleader+ erforderlich).",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (isAllMarked) {
			new Thread(() -> {
				handleAllMarkedOption(event.getHook(), title_final);
			}).start();
			return;
		}

		final String finalTitle = title_final;
		final boolean isMarkedOnly = markedOnly;

		// Handle "noClub" option specially
		if (Clubtag.equals("noClub")) {
			new Thread(() -> {
				handleNoClubOptionGeneric(event.getHook(), finalTitle, isMarkedOnly);
			}).start();
			return;
		}

		Club c = new Club(Clubtag);

		new Thread(() -> {
			ArrayList<Player> playerlist = c.getPlayersDB();
			if (isMarkedOnly) {
				playerlist.removeIf(p -> !p.isMarked());
			}

			playerlist.sort(Comparator.comparing(Player::isMarked).reversed().thenComparing((p1, p2) -> {
				String name1 = p1.getNameDB() != null ? p1.getNameDB() : p1.getNameAPI();
				String name2 = p2.getNameDB() != null ? p2.getNameDB() : p2.getNameAPI();
				if (name1 == null && name2 == null)
					return 0;
				if (name1 == null)
					return 1; // nulls last
				if (name2 == null)
					return -1;
				return name1.compareTo(name2);
			}));

			String adminlist = "";
			String leaderlist = "";
			String coleaderlist = "";
			String elderlist = "";
			String memberlist = "";
			int ClubSizeCount = 0;

			for (Player p : playerlist) {
				boolean isHidden = p.isHiddenColeader();
				if (!isHidden) {
					ClubSizeCount++;
				}

				if (p.getRole() == Player.RoleType.ADMIN) {
					adminlist += p.getInfoStringDB();
					if (p.isMarked()) {
						adminlist += " (✗)";
					}
					adminlist += "\n";
				}
				if (p.getRole() == Player.RoleType.LEADER) {
					leaderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						leaderlist += " (✗)";
					}
					leaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.COLEADER) {
					coleaderlist += p.getInfoStringDB();
					if (isHidden) {
						coleaderlist += " (versteckt)";
					}
					if (p.isMarked()) {
						coleaderlist += " (✗)";
					}
					coleaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.ELDER) {
					elderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						elderlist += " (✗)";
					}
					elderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.MEMBER) {
					memberlist += p.getInfoStringDB();
					if (p.isMarked()) {
						memberlist += " (✗)";
					}
					memberlist += "\n";
				}
			}
			String desc;
			if (isMarkedOnly) {
				desc = "## " + c.getInfoStringDB() + "\n\n";
				for (Player p : playerlist) {
					desc += p.getInfoStringDB();
					if (p.getNote() != null && !p.getNote().isEmpty()) {
						desc += " - *" + p.getNote() + "*";
					}
					desc += "\n";
				}
				if (playerlist.isEmpty()) {
					desc += "Keine markierten Spieler gefunden.";
				}
			} else {
				desc = "## " + c.getInfoStringDB() + "\n";
				if (!Clubtag.equals("warteliste")) {
					desc += "**Admin:**\n";
					desc += adminlist == "" ? "---\n\n" : MessageUtil.unformat(adminlist) + "\n";
					desc += "**Anführer:**\n";
					desc += leaderlist == "" ? "---\n\n" : MessageUtil.unformat(leaderlist) + "\n";
					desc += "**Vize-Anführer:**\n";
					desc += coleaderlist == "" ? "---\n\n" : MessageUtil.unformat(coleaderlist) + "\n";
					desc += "**Ältester:**\n";
					desc += elderlist == "" ? "---\n\n" : MessageUtil.unformat(elderlist) + "\n";
					desc += "**Mitglied:**\n";
					desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
					desc += "\nInsgesamte Mitglieder des Clubs: " + ClubSizeCount;
				} else {
					desc += "**Wartend:**\n";
					desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
					desc += "\nInsgesamte Spieler auf der Warteliste: " + playerlist.size();
				}
			}

			String buttonId = "listmembers_" + Clubtag;
			if (isMarkedOnly) {
				buttonId += "_marked";
			}
			Button refreshButton = Button.secondary(buttonId, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔁"));

			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(finalTitle, desc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		}).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("listmembers"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("Club")) {
			User user = new User(event.getUser().getId());
			List<Command.Choice> choices = DBManager.getClubsAutocompleteWithMarked(input, user.isColeaderOrHigher());
			choices.add(new Command.Choice("Kein Club zugewiesen", "noClub"));
			event.replyChoices(choices).queue();
		}
	}

	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("listmembers_"))
			return;

		event.deferEdit().queue();

		final String Clubtag_raw = id.substring("listmembers_".length());
		final boolean markedOnly = Clubtag_raw.endsWith("_marked");
		final boolean isAllMarked = Clubtag_raw.equals("all_marked");
		final String Clubtag = isAllMarked ? ""
				: (markedOnly ? Clubtag_raw.substring(0, Clubtag_raw.length() - "_marked".length()) : Clubtag_raw);

		if (isAllMarked) {
			new Thread(() -> {
				handleAllMarkedOption(event.getHook(), "Markierte Spieler");
			}).start();
			return;
		}

		final String title = markedOnly ? "Markierte Spieler" : "Memberliste";

		// Handle "noClub" option specially
		if (Clubtag.equals("noClub")) {
			new Thread(() -> {
				handleNoClubOptionGeneric(event.getHook(), title, markedOnly);
			}).start();
			return;
		}

		Club c = new Club(Clubtag);

		new Thread(() -> {
			ArrayList<Player> playerlist = c.getPlayersDB();
			if (markedOnly) {
				playerlist.removeIf(p -> !p.isMarked());
			}

			playerlist.sort(Comparator.comparing(Player::isMarked).reversed().thenComparing((p1, p2) -> {
				String name1 = p1.getNameDB() != null ? p1.getNameDB() : p1.getNameAPI();
				String name2 = p2.getNameDB() != null ? p2.getNameDB() : p2.getNameAPI();
				if (name1 == null && name2 == null)
					return 0;
				if (name1 == null)
					return 1; // nulls last
				if (name2 == null)
					return -1;
				return name1.compareTo(name2);
			}));

			String adminlist = "";
			String leaderlist = "";
			String coleaderlist = "";
			String elderlist = "";
			String memberlist = "";
			int ClubSizeCount = 0;

			for (Player p : playerlist) {
				boolean isHidden = p.isHiddenColeader();
				if (!isHidden) {
					ClubSizeCount++;
				}

				if (p.getRole() == Player.RoleType.ADMIN) {
					adminlist += p.getInfoStringDB();
					if (p.isMarked()) {
						adminlist += " (✗)";

					}
					adminlist += "\n";
				}
				if (p.getRole() == Player.RoleType.LEADER) {
					leaderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						leaderlist += " (✗)";

					}
					leaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.COLEADER) {
					coleaderlist += p.getInfoStringDB();
					if (isHidden) {
						coleaderlist += " (versteckt)";
					}
					if (p.isMarked()) {
						coleaderlist += " (✗)";

					}
					coleaderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.ELDER) {
					elderlist += p.getInfoStringDB();
					if (p.isMarked()) {
						elderlist += " (✗)";

					}
					elderlist += "\n";
				}
				if (p.getRole() == Player.RoleType.MEMBER) {
					memberlist += p.getInfoStringDB();
					if (p.isMarked()) {
						memberlist += " (✗)";

					}
					memberlist += "\n";
				}
			}
			String desc;
			if (markedOnly) {
				desc = "## " + c.getInfoStringDB() + "\n\n";
				for (Player p : playerlist) {
					desc += p.getInfoStringDB();
					if (p.getNote() != null && !p.getNote().isEmpty()) {
						desc += " - *" + p.getNote() + "*";
					}
					desc += "\n";
				}
				if (playerlist.isEmpty()) {
					desc += "Keine markierten Spieler gefunden.";
				}
			} else {
				desc = "## " + c.getInfoStringDB() + "\n";
				if (!Clubtag.equals("warteliste")) {
					desc += "**Admin:**\n";
					desc += adminlist == "" ? "---\n\n" : MessageUtil.unformat(adminlist) + "\n";
					desc += "**Anführer:**\n";
					desc += leaderlist == "" ? "---\n\n" : MessageUtil.unformat(leaderlist) + "\n";
					desc += "**Vize-Anführer:**\n";
					desc += coleaderlist == "" ? "---\n\n" : MessageUtil.unformat(coleaderlist) + "\n";
					desc += "**Ältester:**\n";
					desc += elderlist == "" ? "---\n\n" : MessageUtil.unformat(elderlist) + "\n";
					desc += "**Mitglied:**\n";
					desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
					desc += "\nInsgesamte Mitglieder des Clubs: " + ClubSizeCount;
				} else {
					desc += "**Wartend:**\n";
					desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
					desc += "\nInsgesamte Spieler auf der Warteliste: " + playerlist.size();
				}
			}

			String buttonId = "listmembers_" + Clubtag;
			if (markedOnly) {
				buttonId += "_marked";
			}
			Button refreshButton = Button.secondary(buttonId, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔁"));

			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		}).start();
	}

	private void handleAllMarkedOption(net.dv8tion.jda.api.interactions.InteractionHook hook, String title) {
		StringBuilder desc = new StringBuilder();
		desc.append("## Alle markierten Spieler\n\n");

		String sql = "SELECT cm.player_tag, cm.Club_tag, c.name as Club_name, cm.note " +
				"FROM Club_members cm " +
				"JOIN Clubs c ON c.tag = cm.Club_tag " +
				"WHERE cm.marked = TRUE " +
				"ORDER BY c.index ASC, cm.player_tag ASC";

		java.util.LinkedHashMap<String, ArrayList<String>> groupedPlayers = new java.util.LinkedHashMap<>();

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String playerTag = rs.getString("player_tag");
					String ClubName = rs.getString("Club_name");
					String note = rs.getString("note");

					Player p = new Player(playerTag);
					String playerInfo = p.getInfoStringDB();
					if (note != null && !note.isEmpty()) {
						playerInfo += " - *" + note + "*";
					}

					groupedPlayers.computeIfAbsent(ClubName, _ -> new ArrayList<>()).add(playerInfo);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// Also look for players without Club who are marked
		// Note: The above query only gets players IN a Club.
		// Let's check if there are marked players NOT in Club_members or with special
		// tag.
		// Wait, 'marked' and 'note' are only in Club_members table.
		// If a player is not in a Club, they are not in Club_members (usually).
		// But handleNoClubOptionGeneric uses Player.isMarked() which checks
		// Club_members.

		if (groupedPlayers.isEmpty()) {
			desc.append("Keine markierten Spieler gefunden.");
		} else {
			for (String ClubName : groupedPlayers.keySet()) {
				desc.append("**").append(ClubName).append(":**\n");
				for (String playerInfo : groupedPlayers.get(ClubName)) {
					desc.append("• ").append(playerInfo).append("\n");
				}
				desc.append("\n");
			}
		}

		String finalDesc = desc.toString();
		Button refreshButton = Button.secondary("listmembers_all_marked", "\u200B")
				.withEmoji(Emoji.fromUnicode("🔁"));

		ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
		String formatiert = jetzt.format(formatter);

		if (finalDesc.length() > 4000) {
			ByteArrayInputStream inputStream = new ByteArrayInputStream(finalDesc.getBytes(StandardCharsets.UTF_8));
			hook.editOriginal(inputStream, "Alle_Markierten_Spieler.txt")
					.setEmbeds(MessageUtil.buildEmbed(title, "Die Liste ist zu lang und wurde als Datei gesendet.",
							MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
					.setActionRow(refreshButton).queue();
		} else {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, finalDesc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		}
	}

	private void handleNoClubOptionGeneric(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			boolean markedOnly) {
		// Get all linked players
		String sql = "SELECT bs_tag FROM players";
		ArrayList<String> allPlayerTags = DBUtil.getArrayListFromSQL(sql, String.class);

		// Filter players without Club and build output
		StringBuilder desc = new StringBuilder();
		if (markedOnly) {
			desc.append("## Markierte Spieler (Kein Club)\n\n");
		} else {
			desc.append("## Kein Club zugewiesen\n\n");
			desc.append("**Spieler ohne Club:**\n");
		}

		int count = 0;
		for (String tag : allPlayerTags) {
			Player p = new Player(tag);
			if (p.getClubDB() == null) {
				if (markedOnly && !p.isMarked()) {
					continue;
				}
				count++;
				desc.append(p.getInfoStringDB());
				if (markedOnly && p.getNote() != null && !p.getNote().isEmpty()) {
					desc.append(" - *").append(p.getNote()).append("*");
				} else if (!markedOnly && p.getUser() != null) {
					desc.append(" <@").append(p.getUser().getUserID()).append(">");
				}
				desc.append("\n");
			}
		}

		if (count == 0) {
			desc.append(markedOnly ? "Keine markierten Spieler ohne Club gefunden.\n"
					: "Keine Spieler ohne Club gefunden.\n");
		} else if (!markedOnly) {
			desc.append("\nInsgesamt ").append(count).append(" Spieler ohne Club.");
		}

		String finalDesc = desc.toString();

		// Check if message exceeds 4000 characters
		if (finalDesc.length() > 4000) {
			// Send as text file
			ByteArrayInputStream inputStream = new ByteArrayInputStream(finalDesc.getBytes(StandardCharsets.UTF_8));
			String description = "Die Liste wurde als Datei gesendet, da sie zu lang für eine Nachricht ist.";
			hook.editOriginal(inputStream, "Spieler_Liste.txt")
					.setEmbeds(MessageUtil.buildEmbed(title, description, MessageUtil.EmbedType.INFO)).queue();
		} else {
			// Send as embed
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, finalDesc, MessageUtil.EmbedType.INFO)).queue();
		}
	}

}

