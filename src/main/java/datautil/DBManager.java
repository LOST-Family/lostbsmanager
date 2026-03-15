package datautil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import datawrapper.Player;
import net.dv8tion.jda.api.interactions.commands.Command;
import util.Triplet;
import util.Tuple;

public class DBManager {

	private static ArrayList<Tuple<String, String>> Clubs;
	private static Boolean Clubslocked;
	private static ArrayList<Triplet<String, String, String>> players;
	private static Boolean playerslocked;

	public enum InClubType {
		INClub, NOTINClub, ALL
	}

	public static ArrayList<String> getAllClubs() {
		return DBUtil.getArrayListFromSQL("SELECT tag FROM Clubs ORDER BY index ASC", String.class);
	}

	public static List<Command.Choice> getKPReasonsAutocomplete(String input, String Clubtag) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT name, Club_tag FROM kickpoint_reasons WHERE Club_tag = ?";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, Clubtag);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String name = rs.getString("name");

					if (name.toLowerCase().contains(input.toLowerCase())) {
						choices.add(new Command.Choice(name, name));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return choices;
	}

	public static int getAvailableKPID() {
		String sql = "SELECT id FROM kickpoints";
		int available = 0;

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				ArrayList<Integer> used = new ArrayList<>();
				while (rs.next()) {
					used.add(rs.getInt("id"));
				}
				while (used.contains(available)) {
					available++;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return available;
	}

	public static List<Command.Choice> getClubsAutocomplete(String input) {
		return getClubsAutocompleteWithMarked(input, false);
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getClubsAutocompleteWithMarked(String input, boolean includeMarked) {
		if (Clubs == null) {
			cacheClubs();
		}

		List<Command.Choice> choices = new ArrayList<>();

		for (Tuple<String, String> available : Clubs) {
			String display = available.getFirst();
			String tag = available.getSecond();
			if (display.toLowerCase().contains(input.toLowerCase())
					|| tag.toLowerCase().startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice(display, tag));

				if (choices.size() >= 25) {
					break;
				}
			}
		}

		if (includeMarked && "alle markierten spieler".contains(input.toLowerCase())) {
			choices.add(new Command.Choice("Alle markierten Spieler", "all_marked"));
		}
		Thread thread = new Thread(() -> {
			if (!Clubslocked)
				cacheClubs();
		});
		thread.start();

		return choices;
	}

	private static void cacheClubs() {
		ArrayList<Tuple<String, String>> list = new ArrayList<>();

		String sql = "SELECT name, tag FROM Clubs ORDER BY index ASC";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String name = rs.getString("name");

					String display = name;
					if (!tag.equals("warteliste")) {
						display += " (" + tag + ")";
					}

					list.add(new Tuple<String, String>(display, tag));

				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		Clubs = list;
		Clubslocked = true;
		new java.util.Timer().schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				Clubslocked = false;
			}
		}, 10000);
	}

	public static List<Command.Choice> getClubsAutocompleteNoWaitlist(String input) {
		if (Clubs == null) {
			cacheClubs();
		}

		List<Command.Choice> choices = new ArrayList<>();
		for (Tuple<String, String> available : Clubs) {
			String display = available.getFirst();
			String tag = available.getSecond();
			if (!tag.equals("warteliste")) {
				if (display.toLowerCase().contains(input.toLowerCase())
						|| tag.toLowerCase().startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice(display, tag));
					if (choices.size() == 25) {
						break;
					}
				}
			}
		}
		Thread thread = new Thread(() -> {
			if (!Clubslocked)
				cacheClubs();
		});
		thread.start();

		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getPlayerlistAutocomplete(String input, InClubType inClubtype) {
		if (players == null) {
			cachePlayers();
		}

		List<Command.Choice> choices = new ArrayList<>();
		for (Triplet<String, String, String> available : players) {
			String display = available.getFirst();
			String ClubName = available.getSecond();
			String tag = available.getThird();

			if (inClubtype == InClubType.NOTINClub) {
				if (ClubName == null || ClubName.isEmpty()) {
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			} else if (inClubtype == InClubType.INClub) {
				if (ClubName != null && !ClubName.isEmpty()) {
					display += " - " + ClubName;
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break; // Max 25 Vorschläge
						}
					}
				}
			} else if (inClubtype == InClubType.ALL) {
				if (ClubName != null && !ClubName.isEmpty()) {
					display += " - " + ClubName;
				}

				// Filter mit Eingabe (input ist String mit aktuell eingegebenem Text)
				if (display.toLowerCase().contains(input.toLowerCase())
						|| tag.toLowerCase().startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice(display, tag));
					if (choices.size() == 25) {
						break; // Max 25 Vorschläge
					}
				}
			}

		}
		Thread thread = new Thread(() -> {
			if (!playerslocked)
				cachePlayers();
		});
		thread.start();

		return choices;
	}

	private static void cachePlayers() {
		ArrayList<Triplet<String, String, String>> list = new ArrayList<>();

		String sql = "SELECT players.bs_tag AS tag, players.name AS player_name, Clubs.name AS Club_name "
				+ "FROM players " + "LEFT JOIN Club_members ON Club_members.player_tag = players.bs_tag "
				+ "LEFT JOIN Clubs ON Clubs.tag = Club_members.Club_tag";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String ClubName = rs.getString("Club_name");

					String display = new Player(tag).getInfoStringDB();

					list.add(new Triplet<String, String, String>(display, ClubName, tag));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		players = list;
		playerslocked = true;
		new java.util.Timer().schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				playerslocked = false;
			}
		}, 10000);
	}

	public static List<Command.Choice> getPlayerlistAutocompleteNoWaitlist(String input, InClubType inClubtype) {
		if (players == null) {
			cachePlayers();
		}

		List<Command.Choice> choices = new ArrayList<>();
		for (Triplet<String, String, String> available : players) {
			String display = available.getFirst();
			String ClubName = available.getSecond();
			String tag = available.getThird();
			if (!tag.equals("warteliste")) {
				if (inClubtype == InClubType.NOTINClub) {
					if (ClubName == null || ClubName.isEmpty()) {
						if (display.toLowerCase().contains(input.toLowerCase())
								|| tag.toLowerCase().startsWith(input.toLowerCase())) {
							choices.add(new Command.Choice(display, tag));
							if (choices.size() == 25) {
								break;
							}
						}
					}
				} else if (inClubtype == InClubType.INClub) {
					if (ClubName != null && !ClubName.isEmpty()) {
						display += " - " + ClubName;
						if (display.toLowerCase().contains(input.toLowerCase())
								|| tag.toLowerCase().startsWith(input.toLowerCase())) {
							choices.add(new Command.Choice(display, tag));
							if (choices.size() == 25) {
								break; // Max 25 Vorschläge
							}
						}
					}
				} else if (inClubtype == InClubType.ALL) {
					if (ClubName != null && !ClubName.isEmpty()) {
						display += " - " + ClubName;
					}

					// Filter mit Eingabe (input ist String mit aktuell eingegebenem Text)
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break; // Max 25 Vorschläge
						}
					}
				}
			}

		}
		Thread thread = new Thread(() -> {
			if (!playerslocked)
				cachePlayers();
		});
		thread.start();

		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getPlayerlistAutocompleteAllLostClubs(String input) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT players.bs_tag AS tag, players.name AS player_name, Clubs.name AS Club_name, Clubs.tag AS Club_tag "
				+ "FROM players "
				+ "LEFT JOIN Club_members ON Club_members.player_tag = players.bs_tag "
				+ "LEFT JOIN Clubs ON Clubs.tag = Club_members.Club_tag "
				+ "WHERE Clubs.tag IS NOT NULL AND Clubs.tag != 'warteliste'";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String playerName = rs.getString("player_name");
					String ClubName = rs.getString("Club_name");

					String display = playerName + " (" + tag + ")";
					if (ClubName != null && !ClubName.isEmpty()) {
						display += " [" + ClubName + "]";
					}

					// Filter with input
					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getTrackChannelsAutocomplete(String input) {
		List<Command.Choice> choices = new ArrayList<>();
		String sql = "SELECT id, name FROM trackchannels";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					int id = rs.getInt("id");
					String name = rs.getString("name");
					String display = id + " | " + name;

					if (display.toLowerCase().contains(input.toLowerCase())) {
						choices.add(new Command.Choice(display, String.valueOf(id)));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return choices;
	}

}


