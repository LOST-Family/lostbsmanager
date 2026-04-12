package commands.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datawrapper.Club;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class checkroles extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
		if (!event.getName().equals("checkroles"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Rollen-Check";

			// Check permissions - must be at least co-leader
			User userExecuted = new User(event.getUser().getId());
			boolean hasPermission = false;
			for (String clubtag : DBManager.getAllClubs()) {
				Player.RoleType role = userExecuted.getClubRoles().get(clubtag);
				if (role == Player.RoleType.ADMIN || role == Player.RoleType.PRESIDENT
						|| role == Player.RoleType.COPRESIDENT) {
					hasPermission = true;
					break;
				}
			}

			if (!hasPermission) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clubs sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			OptionMapping clubOption = event.getOption("club");
			OptionMapping ignoreHiddenColeadersOption = event.getOption("ignore_hiddencoleaders");

			if (clubOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der Parameter 'club' ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clubtag = clubOption.getAsString();

			boolean ignoreHiddenColeaders = false;
			if (ignoreHiddenColeadersOption != null) {
				String ignoreHiddenColeadersValue = ignoreHiddenColeadersOption.getAsString();
				if ("true".equalsIgnoreCase(ignoreHiddenColeadersValue)) {
					ignoreHiddenColeaders = true;
				} else {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Der ignore_hiddencoleaders Parameter muss entweder \"true\" enthalten oder nicht angegeben sein (false).",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			performRoleCheck(event.getHook(), event.getGuild(), title, clubtag, ignoreHiddenColeaders);

		}, "CheckRolesCommand-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("checkroles"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("club")) {
				List<Command.Choice> choices = DBManager.getClubsAutocomplete(input);
				event.replyChoices(choices).queue();
			} else if (focused.equals("ignore_hiddencoleaders")) {
				List<Command.Choice> choices = new ArrayList<>();
				if ("true".startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice("true", "true"));
				}
				event.replyChoices(choices).queue();
			}
		}, "CheckRolesAutocomplete-" + event.getUser().getId()).start();
	}

	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("checkroles_"))
			return;

		event.deferEdit().queue();

		// Parse the button ID: checkroles_{clubtag}_{ignoreHiddenColeaders}
		String remainder = id.substring("checkroles_".length());
		String clubtag;
		boolean ignoreHiddenColeaders = false;

		int lastUnderscore = remainder.lastIndexOf("_");
		if (lastUnderscore != -1) {
			clubtag = remainder.substring(0, lastUnderscore);
			String ignoreHiddenColeadersStr = remainder.substring(lastUnderscore + 1);
			ignoreHiddenColeaders = "true".equals(ignoreHiddenColeadersStr);
		} else {
			// Fallback for old button IDs without ignore_hiddencoleaders
			clubtag = remainder;
		}

		String title = "Rollen-Check";

		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		final boolean ignoreHiddenColeadersFinal = ignoreHiddenColeaders;
		new Thread(() -> {
			performRoleCheck(event.getHook(), event.getGuild(), title, clubtag, ignoreHiddenColeadersFinal);
		}, "CheckRolesRefresh-" + event.getUser().getId()).start();
	}

	@SuppressWarnings({ "null", "unused" })
	private void performRoleCheck(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild, String title,
			String clubtag, boolean ignoreHiddenColeaders) {

		if (guild == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Dieser Befehl kann nur auf einem Server ausgeführt werden.", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		Club club = new Club(clubtag);
		ArrayList<Player> playerlist = club.getPlayersDB();

		if (playerlist == null || playerlist.isEmpty()) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Keine Mitglieder in diesem Club gefunden.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Build description with members missing roles
		StringBuilder description = new StringBuilder();
		description.append("## ").append(club.getInfoStringDB()).append("\n\n");

		int totalMembers = 0;
		int membersWithoutRole = 0;
		int linkedMembers = 0;
		int unlinkedMembers = 0;

		List<String> missingRolesList = new ArrayList<>();
		List<String> unlinkedMembersList = new ArrayList<>();

		for (Player p : playerlist) {
			Player.RoleType roleDB = p.getRole();
			if (roleDB == null || roleDB == Player.RoleType.NOTINCLUB) {
				continue;
			}

			// Skip hidden coleaders if ignore_hiddencoleaders is true
			if (ignoreHiddenColeaders && p.isCoPresident()) {
				continue;
			}

			totalMembers++;

			User user = p.getUser();
			if (user == null) {
				unlinkedMembersList.add(String.format("%s - **%s**", p.getInfoStringDB(), getRoleDisplayName(roleDB)));
				unlinkedMembers++;
				continue;
			}

			linkedMembers++;

			// Always check the MEMBER role first
			String memberRoleId = club.getRoleID(Club.Role.MEMBER);

			// Get expected Discord role ID based on club role (for higher roles)
			String expectedRoleId = null;
			switch (roleDB) {
				case PRESIDENT -> expectedRoleId = club.getRoleID(Club.Role.PRESIDENT);
				case COPRESIDENT -> expectedRoleId = club.getRoleID(Club.Role.COPRESIDENT);
				case SENIOR -> expectedRoleId = club.getRoleID(Club.Role.SENIOR);
				case MEMBER -> expectedRoleId = memberRoleId;
				default -> {
                        }
			}

			// Check if Discord user has the expected role(s)
			Member member = guild.getMemberById(user.getUserID());
			if (member == null) {
				// User is linked but not in the Discord server
				missingRolesList.add(String.format("%s - **%s** - <@%s> (nicht auf dem Server)", p.getInfoStringDB(),
						getRoleDisplayName(roleDB), user.getUserID()));
				membersWithoutRole++;
			} else {
				// Check member role first (for everyone)
				if (memberRoleId != null) {
					Role memberRole = guild.getRoleById(memberRoleId);
					if (memberRole != null && !member.getRoles().contains(memberRole)) {
						missingRolesList.add(String.format("%s - **%s** - <@%s> (fehlt: %s)", p.getInfoStringDB(),
								getRoleDisplayName(roleDB), user.getUserID(), memberRole.getAsMention()));
						membersWithoutRole++;
						continue; // Skip checking other roles if member role is missing
					}
				}

				// Check additional role for non-members (leader, coleader, elder)
				if (expectedRoleId != null && !expectedRoleId.equals(memberRoleId)) {
					Role expectedRole = guild.getRoleById(expectedRoleId);
					if (expectedRole == null) {
						// Role doesn't exist in Discord server
						missingRolesList.add(String.format("%s - **%s** - <@%s> (Rolle nicht konfiguriert)",
								p.getInfoStringDB(), getRoleDisplayName(roleDB), user.getUserID()));
						membersWithoutRole++;
					} else if (!member.getRoles().contains(expectedRole)) {
						// Member doesn't have the expected role
						missingRolesList.add(String.format("%s - **%s** - <@%s> (fehlt: %s)", p.getInfoStringDB(),
								getRoleDisplayName(roleDB), user.getUserID(), expectedRole.getAsMention()));
						membersWithoutRole++;
					}
				}
			}
		}

		// Summary statistics
		description.append("**Statistik:**\n");
		description.append("Gesamte Mitglieder: ").append(totalMembers).append("\n");
		description.append("Verlinkte Mitglieder: ").append(linkedMembers).append("\n");
		description.append("Nicht verlinkte Mitglieder: ").append(unlinkedMembers).append("\n");
		description.append("Mitglieder ohne korrekte Rolle: ").append(membersWithoutRole).append("\n\n");

		// List members missing roles
		if (missingRolesList.isEmpty()) {
			description.append("**✅ Alle verlinkten Mitglieder haben die korrekte Discord-Rolle!**\n");
		} else {
			description.append("**Mitglieder ohne korrekte Discord-Rolle:**\n");
			for (String member : missingRolesList) {
				description.append(member).append("\n");
			}
		}

		description.append("\n");

		if (unlinkedMembersList.isEmpty()) {
			description.append("**✅ Alle Mitglieder sind verlinkt!**\n");
		} else {
			description.append("**Nicht verlinkte Mitglieder:**\n");
			for (String member : unlinkedMembersList) {
				description.append(member).append("\n");
			}
		}

		description.append("\n");

		// --- NEW: Check for members who have a role but shouldn't ---
		List<String> unnecessaryRolesList = new ArrayList<>();
		int membersWithUnnecessaryRoles = 0;

		String elderRoleId = club.getRoleID(Club.Role.SENIOR);
		String memberRoleId = club.getRoleID(Club.Role.MEMBER);

		java.util.HashSet<String> clubRoleIds = new java.util.HashSet<>();
		if (elderRoleId != null)
			clubRoleIds.add(elderRoleId);
		if (memberRoleId != null)
			clubRoleIds.add(memberRoleId);

		if (!clubRoleIds.isEmpty()) {
			for (Member m : guild.getMembers()) {
				List<Role> memberRoles = m.getRoles();
				boolean hasAnyClubRole = false;
				for (Role r : memberRoles) {
					if (clubRoleIds.contains(r.getId())) {
						hasAnyClubRole = true;
						break;
					}
				}

				if (hasAnyClubRole) {
					User user = new User(m.getId());
					ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();

					// Find the highest expected role for this user in this club
					Player.RoleType highestInGameRole = null;
					for (Player p : linkedAccounts) {
						if (p.getClubDB() == null)
							continue;
						if (clubtag.equals(p.getClubDB().getTag())) {
							Player.RoleType currentP = p.getRole();
							if (highestInGameRole == null || isHigherRole(currentP, highestInGameRole)) {
								highestInGameRole = currentP;
							}
						}
					}

					// Check if they should have any roles at all
					if (highestInGameRole == null || highestInGameRole == Player.RoleType.NOTINCLUB) {
						// Should not have any club roles
						StringBuilder rolesFound = new StringBuilder();
						for (Role r : memberRoles) {
							if (clubRoleIds.contains(r.getId())) {
								if (rolesFound.length() > 0)
									rolesFound.append(", ");
								rolesFound.append(r.getAsMention());
							}
						}
						unnecessaryRolesList.add(String.format("<@%s> - hat Rollen: %s (nicht im Club)", m.getId(),
								rolesFound.toString()));
						membersWithUnnecessaryRoles++;
					} else {

						List<String> invalidRoles = new ArrayList<>();
						for (Role r : memberRoles) {
							String rId = r.getId();
							if (!clubRoleIds.contains(rId))
								continue;

							boolean isAllowed = false;
							// Map the Discord role back to what it represents
							if (rId.equals(elderRoleId)) {
								if (highestInGameRole == Player.RoleType.SENIOR
										|| highestInGameRole == Player.RoleType.COPRESIDENT
										|| highestInGameRole == Player.RoleType.PRESIDENT)
									isAllowed = true;
							} else if (rId.equals(memberRoleId)) {
								isAllowed = true; // Everyone in club is a member
							}

							if (!isAllowed) {
								invalidRoles.add(r.getAsMention());
							}
						}

						if (!invalidRoles.isEmpty()) {
							unnecessaryRolesList.add(String.format("<@%s> - **%s** - hat zu hohe Rollen: %s", m.getId(),
									getRoleDisplayName(highestInGameRole), String.join(", ", invalidRoles)));
							membersWithUnnecessaryRoles++;
						}
					}
				}
			}
		}

		if (unnecessaryRolesList.isEmpty()) {
			description.append("**✅ Niemand hat Rollen, die er nicht haben sollte!**\n");
		} else {
			description.append("**Mitglieder mit Rollen, die sie nicht haben sollten:**\n");
			for (String member : unnecessaryRolesList) {
				description.append(member).append("\n");
			}
		}

		// Create refresh button
		Button refreshButton = Button.secondary("checkroles_" + clubtag + "_" + ignoreHiddenColeaders, "\u200B")
				.withEmoji(Emoji.fromUnicode("🔁"));

		// Add timestamp
		ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
		String formatiert = jetzt.format(formatter);

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, description.toString(), MessageUtil.EmbedType.INFO,
				"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
	}

	private boolean isHigherRole(Player.RoleType r1, Player.RoleType r2) {
		return getRoleWeight(r1) > getRoleWeight(r2);
	}

	private int getRoleWeight(Player.RoleType role) {
		if (role == null)
			return -1;
            return switch (role) {
                case PRESIDENT -> 4;
                case COPRESIDENT -> 3;
                case SENIOR -> 2;
                case MEMBER -> 1;
                default -> 0;
            };
	}

	private String getRoleDisplayName(Player.RoleType roleType) {
            return switch (roleType) {
                case PRESIDENT -> "Anführer";
                case COPRESIDENT -> "Vize-Anführer";
                case SENIOR -> "Ältester";
                case MEMBER -> "Mitglied";
                default -> "Unbekannt";
            };
	}
}






