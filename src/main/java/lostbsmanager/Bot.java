package lostbsmanager;

import java.io.File;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.json.JSONArray;
import org.json.JSONObject;

import commands.admin.copyreasons;
import commands.admin.restart;
import commands.kickpoints.clubconfig;
import commands.kickpoints.kpadd;
import commands.kickpoints.kpaddreason;
import commands.kickpoints.kpclub;
import commands.kickpoints.kpedit;
import commands.kickpoints.kpeditreason;
import commands.kickpoints.kpinfo;
import commands.kickpoints.kplistreasons;
import commands.kickpoints.kpmember;
import commands.kickpoints.kpremove;
import commands.kickpoints.kpremovereason;
import commands.links.link;
import commands.links.playerinfo;
import commands.links.relink;
import commands.links.unlink;
import commands.memberlist.addmember;
import commands.memberlist.editmember;
import commands.memberlist.listmembers;
import commands.memberlist.memberstatus;
import commands.memberlist.removemember;
import commands.memberlist.signoff;
import commands.memberlist.signofflist;
import commands.memberlist.togglemark;
import commands.memberlist.transfermember;
//import commands.reminders.remindersadd;
//import commands.reminders.remindersinfo;
//import commands.reminders.remindersremove;
import commands.util.checkroles;



import commands.util.trackchannels;
//import commands.wins.wins;
//import commands.wins.winsfails;
import datautil.APIUtil;
import datautil.DBUtil;
import datawrapper.Club;
import datawrapper.Player;
import webserver.LinkWebServer;
import webserver.api.RestApiServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Bot extends ListenerAdapter {

	private final static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private static final int MIN_LEVEL_FOR_PING = 45;

	private static JDA jda;
	private static RestApiServer restApiServer;
	public static String VERSION;
	public static String guild_id;
	public static String api_key;
	public static String url;
	public static String user;
	public static String password;
	public static String exmemberroleid;
	public static String seasonstringfallback;

	public static void main(String[] args) throws Exception {
		util.DiscordLogger.setup();
		VERSION = "1.2.5";
		guild_id = System.getenv("BS_MANAGER_GUILD_ID");
		api_key = System.getenv("BS_MANAGER_API_KEY");
		url = System.getenv("BS_MANAGER_DB_URL");
		user = System.getenv("BS_MANAGER_DB_USER");
		password = System.getenv("BS_MANAGER_DB_PASSWORD");
		exmemberroleid = System.getenv("BS_MANAGER_EXMEMBER_ROLEID");

		String token = System.getenv("BS_MANAGER_TOKEN");

		if (datautil.Connection.checkDB()) {
			System.out.println("Verbindung zur Datenbank funktioniert.");
		} else {
			System.out.println("Verbindung zur Datenbank fehlgeschlagen.");
		}

		datautil.Connection.tablesExists();
		datautil.Connection.migrateRemindersTable();
		datautil.Connection.migrateClubMembersTable();
		datautil.Connection.migrateKickpointReasonsTable();

		// Start REST API servers
		LinkWebServer.start();
		// Start generic REST API (club/player endpoints) similar to lostmanager
		int restPort = 8070;
		try {
			restPort = Integer.parseInt(System.getenv().getOrDefault("REST_API_PORT", "8060"));
		} catch (NumberFormatException e) {
			System.err.println("Invalid REST_API_PORT, using default 8060");
		}
		try {
			restApiServer = new RestApiServer(restPort);
			restApiServer.start();
			System.out.println("RestApiServer started on port " + restPort);
		} catch (Exception e) {
			System.err.println("Failed to start RestApiServer: " + e.getMessage());
			e.printStackTrace();
		}

		startNameUpdates();
		startLoadingLists();
		//startReminders();
		//startMonthlyWinsSave();

		JDABuilder.createDefault(token).enableIntents(GatewayIntent.GUILD_MEMBERS)
				.setMemberCachePolicy(MemberCachePolicy.ALL).setChunkingFilter(ChunkingFilter.ALL)
				.setActivity(Activity.playing("mit deinen Kickpunkten"))
				.addEventListeners(new Bot(), new link(), new unlink(), new restart(), new copyreasons(),
						new addmember(),
						new removemember(), new listmembers(), new editmember(), new playerinfo(), new memberstatus(),
						new kpaddreason(), new kpremovereason(), new kpeditreason(), new kpadd(), new kpmember(),
						new kpremove(), new kpedit(), new kpinfo(), new kplistreasons(), new kpclub(), new clubconfig(),
						new transfermember(), new togglemark(), new checkroles(), new relink(), new trackchannels(), new signoff(),
						new signofflist())
				.build();
	}

	@SuppressWarnings("null")
	public static void registerCommands(JDA jda, String guildId) {
		Guild guild = jda.getGuildById(guildId);
		if (guild != null) {
			guild.updateCommands().addCommands(Commands
					.slash("link", "Verlinke einen Brawl Stars Account mit einem Discord User oder einer UserID.")
					.addOption(OptionType.STRING, "tag", "Der Tag des Brawl Stars Accounts", true)
					.addOption(OptionType.MENTIONABLE, "user", "Der User, mit dem der Account verlinkt werden soll.")
					.addOption(OptionType.STRING, "userid",
							"Die ID des Users, mit dem der Account verlinkt werden soll."),
					Commands.slash("unlink", "Lösche eine Verlinkung eines Brawl Stars Accounts.")
							.addOptions(new OptionData(OptionType.STRING, "tag",
									"Der Spieler, wessen Verknüpfung entfernt werden soll", true)
									.setAutoComplete(true)),
					Commands.slash("relink",
							"Verlinke einen Brawl Stars Account neu mit einem Discord User oder einer UserID.")
							.addOptions(
									new OptionData(OptionType.STRING, "tag", "Der Tag des Brawl Stars Accounts", true)
											.setAutoComplete(true))
							.addOption(OptionType.MENTIONABLE, "user",
									"Der User, mit dem der Account verlinkt werden soll.")
							.addOption(OptionType.STRING, "userid",
									"Die ID des Users, mit dem der Account verlinkt werden soll."),
					Commands.slash("restart", "Startet den Bot neu."),
					Commands.slash("copyreasons", "Kopiere Kickpunkt-Gründe eines Clubs auf alle anderen Clubs.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, von dem kopiert werden soll.", true).setAutoComplete(true)),
					Commands.slash("addmember", "Füge einen Spieler zu einem Club hinzu.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, zu welchem der Spieler hinzugefügt werden soll", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher hinzugefügt werden soll", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "role",
									"Die Rolle, welche der Spieler bekommen soll", true).setAutoComplete(true)),
					Commands.slash("removemember", "Entferne einen Spieler aus seinem Club.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher entfernt werden soll", true).setAutoComplete(true)),
					Commands.slash("togglemark", "Schaltet die Markierung eines Spielers in einem Club an/aus.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher markiert/entmarkiert werden soll", true)
									.setAutoComplete(true)),
					Commands.slash("listmembers", "Liste aller Spieler in einem Club.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, welcher ausgegeben werden soll.", true).setAutoComplete(true)),
					Commands.slash("editmember", "Ändere die Rolle eines Mitglieds.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher bearbeitet werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "role",
									"Die Rolle, welcher der Spieler sein soll.", true).setAutoComplete(true)),
					Commands.slash("playerinfo",
							"Info eines Spielers. Bei Eingabe eines Parameters werden Infos über diesen Nutzer aufgelistet.")
							.addOptions(new OptionData(OptionType.MENTIONABLE, "user",
									"Der User, über welchem Informationen über verlinkte Accounts gesucht sind."))
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, über welchem Informationen gesucht sind.").setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "getapifile",
									"(Optional) Wenn 'true', wird die API-Datei des Spielers mitgesendet")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("memberstatus",
							"Status über einen Club, welche Spieler keine Mitglieder sind und welche Mitglieder fehlen.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, welcher ausgegeben werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "exclude_leaders",
									"(Optional) Wenn 'true', werden Leader, Co-Leader und Admins von der Prüfung ausgeschlossen")
									.setAutoComplete(true).setRequired(false)),
					Commands.slash("kpaddreason", "Erstelle einen vorgefertigten Kickpunktgrund.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, für welchen dieser erstellt wird.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true))
							.addOptions(
									new OptionData(OptionType.INTEGER, "amount", "Die Anzahl der Kickpunkte.", true))
							.addOptions(
									new OptionData(OptionType.INTEGER, "index", "Der Index für die Sortierung.")),
					Commands.slash("kpremovereason", "Lösche einen vorgefertigten Kickpunktgrund.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, für welchen dieser erstellt wird.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true)
									.setAutoComplete(true)),
					Commands.slash("kpeditreason", "Aktualisiere die Anzahl der Kickpunkte für eine Grund-Vorlage.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, für welchen dieser erstellt wird.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.INTEGER, "amount", "Die Anzahl.", true))
							.addOptions(new OptionData(OptionType.INTEGER, "index", "Der neue Index.")),
					Commands.slash("kpadd", "Gebe einem Spieler Kickpunkte.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher die Kickpunkte erhält.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Die Grund-Vorlage.")
									.setAutoComplete(true)),
					Commands.slash("kpmember", "Zeige alle Kickpunkte eines Spielers an.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher angezeigt werden soll.", true).setAutoComplete(true)),
					Commands.slash("kpremove", "Lösche einen Kickpunkt.")
							.addOptions(new OptionData(OptionType.INTEGER, "id",
									"Die ID des Kickpunkts. Ist unter /kpmember zu sehen.", true)),
					Commands.slash("kpedit", "Editiere einen Kickpunkt.")
							.addOptions(new OptionData(OptionType.INTEGER, "id",
									"Die ID des Kickpunkts. Ist unter /kpmember zu sehen.", true)),
					Commands.slash("kpinfo", "Infos über Kickpunkt-Gründe eines Clubs.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Die Club, welcher angezeigt werden soll.", true).setAutoComplete(true)),
					Commands.slash("kplistreasons", "Liste aller Kickpunkt-Gründe eines Clubs sortiert nach Index.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, dessen Gründe angezeigt werden sollen.", true).setAutoComplete(true)),
					Commands.slash("kpclub", "Zeige die Kickpunktanzahlen aller Spieler in einem Club.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, welcher angezeigt werden soll.", true).setAutoComplete(true)),
					Commands.slash("clubconfig", "Ändere Einstellungen an einem Club.")
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, welcher bearbeitet werden soll.", true).setAutoComplete(true)),
					Commands.slash("leaguetrophylist", "Sortierte Rangliste.")
							.addOptions(new OptionData(OptionType.STRING, "timestamp",
									"Der Zeitpunkt der gespeicherten Liste", true).setAutoComplete(true)),
					Commands.slash("transfermember", "Transferiere einen Spieler in einen anderen Club.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, welcher transferiert werden soll", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "club",
									"Der Club, zu welchem der Spieler hinzugefügt werden soll", true)
									.setAutoComplete(true)),
					
					Commands.slash("remindersremove", "Entferne einen Reminder.")
							.addOptions(new OptionData(OptionType.INTEGER, "id",
									"Die ID des Reminders. Ist unter /remindersinfo zu sehen.", true)),
					
					
					Commands.slash("signoff",
							"Melde einen Spieler ab (Abwesenheit). Abgemeldete Spieler erhalten keine automatischen Kickpunkte.")
							.addOptions(new OptionData(OptionType.STRING, "player",
									"Der Spieler, der ab-/angemeldet werden soll.", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.STRING, "action",
									"Die Aktion (create, end, extend, info)", true).setAutoComplete(true))
							.addOptions(new OptionData(OptionType.INTEGER, "days",
									"(Optional) Dauer der Abmeldung in Tagen. Ohne = unbegrenzt.").setRequired(false))
							.addOptions(new OptionData(OptionType.STRING, "reason",
									"(Optional) Grund der Abmeldung.").setRequired(false))
							.addOptions(new OptionData(OptionType.BOOLEAN, "pings",
									"(Optional) Soll der Spieler trotzdem Reminder-Pings erhalten? Standard: Nein")
									.setRequired(false)),
					Commands.slash("signofflist",
							"Zeige alle Abmeldungen für einen bestimmten Monat an.")
							.addOptions(new OptionData(OptionType.STRING, "month",
									"Der Monat, für den die Abmeldungen angezeigt werden sollen.", true)
									.setAutoComplete(true))
							.addOptions(new OptionData(OptionType.BOOLEAN, "showreasons",
									"(Optional) Zeige Begründungen an.").setRequired(false)))
					.queue();
		}
	}

	@Override
	public void onReady(@Nonnull ReadyEvent event) {
		setJda(event.getJDA());
		util.DiscordLogger.setJda(event.getJDA());
		registerCommands(event.getJDA(), guild_id);
	}

	@Override
	public void onShutdown(@Nonnull ShutdownEvent event) {
		LinkWebServer.stop();
		if (restApiServer != null) {
			try {
				restApiServer.stop();
			} catch (Exception e) {
				System.err.println("Error stopping RestApiServer: " + e.getMessage());
			}
		}
		stopScheduler();
	}

	public static void setJda(JDA instance) {
		jda = instance;
	}

	public static JDA getJda() {
		return jda;
	}

	public static void startLoadingLists() {} 


	@SuppressWarnings("null")
	public static void startNameUpdates() {
		System.out.println("Alle 2h werden nun die Namen aktualisiert. " + System.currentTimeMillis());
		Runnable task = () -> {
			Thread thread = new Thread(() -> {

				// Update club badges and descriptions
				String clubSql = "SELECT tag FROM clubs";
				for (String clubTag : DBUtil.getArrayListFromSQL(clubSql, String.class)) {
					if (clubTag.equals("warteliste")) {
						continue;
					}
					try {
						Club club = new Club(clubTag);
						String description = club.getDescriptionAPI();
						DBUtil.executeUpdate("UPDATE clubs SET description = ? WHERE tag = ?", description, clubTag);
					} catch (Exception e) {
						System.out.println(
								"Fehler beim Badge/Description Update von Club " + clubTag + ": " + e.getMessage());
					}
				}

				// Update User names
				String usersql = "SELECT discord_id FROM users";
				for (String id : DBUtil.getArrayListFromSQL(usersql, String.class)) {
					try {
						DBUtil.executeUpdate("UPDATE users SET name = ? WHERE discord_id = ?",
								getJda().getGuildById(guild_id).retrieveMemberById(id).submit().get()
										.getEffectiveName(),
								id);
					} catch (Exception e) {
						if (e.getMessage().contains("Unknown Member")) {
							continue;
						}
						System.out.println("Fehler beim Namenupdate von ID " + id + "; Error: " + e.getMessage());
					}
				}

				String sql = "SELECT bs_tag FROM players";
				for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
					try {
						Player p = new Player(tag);
						DBUtil.executeUpdate("UPDATE players SET name = ? WHERE bs_tag = ?", p.getNameAPI(), tag);
					} catch (Exception e) {
						System.out.println(
								"Beim Updaten des Namens von Spieler mit Tag " + tag + " ist ein Fehler aufgetreten.");
					}
				}

			});
			thread.start();
		};
		scheduler.scheduleAtFixedRate(task, 0, 2, TimeUnit.HOURS);
	}
 


	@SuppressWarnings("null")
	public static void updateTrackChannels() {
		ZoneId zoneId = ZoneId.of("Europe/Berlin");
		ZonedDateTime now = ZonedDateTime.now(zoneId);

		for (datawrapper.TrackChannel tc : datawrapper.TrackChannel.getAll()) {
			// Hardcoded logic for CR-EOS
			if (tc.getName().equalsIgnoreCase("CR-EOS")) {
				ZonedDateTime nextFirstMonday = now.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
						.withHour(10).withMinute(0).withSecond(0).withNano(0);
				if (now.isAfter(nextFirstMonday)) {
					nextFirstMonday = now.plusMonths(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
							.withHour(10).withMinute(0).withSecond(0).withNano(0);
				}

				if (tc.getTimestamp() == null || !tc.getTimestamp().isEqual(nextFirstMonday.toOffsetDateTime())) {
					tc.setTimestamp(nextFirstMonday.toOffsetDateTime());
				}
			}

			String newName;
			if (tc.getTimestamp() == null || tc.getTimestamp().isBefore(now.toOffsetDateTime())) {
				newName = tc.getName() + " in X";
			} else {
				java.time.Duration duration = java.time.Duration.between(now.toOffsetDateTime(), tc.getTimestamp());
				long days = duration.toDays();
				long hours = duration.toHours() % 24;
				newName = tc.getName() + " in " + days + "D " + hours + "H";
			}

			try {
				Guild guild = getJda().getGuildById(guild_id);
				if (guild != null) {
					GuildChannel channel = guild.getGuildChannelById(tc.getChannelId());
					if (channel != null) {
						if (!channel.getName().equals(newName)) {
							channel.getManager().setName(newName).queue();
						}
					}
				}
			} catch (Exception e) {
				System.err
						.println("Fehler beim Updaten des Channel-Namens für " + tc.getName() + ": " + e.getMessage());
			}
		}
	}


	private static boolean isDayOfWeek(DayOfWeek dayOfWeek, String weekdayString) {
		if (weekdayString == null) {
			return false;
		}
		String normalized = weekdayString.toLowerCase();
		switch (normalized) {
			case "monday":
				return dayOfWeek == DayOfWeek.MONDAY;
			case "tuesday":
				return dayOfWeek == DayOfWeek.TUESDAY;
			case "wednesday":
				return dayOfWeek == DayOfWeek.WEDNESDAY;
			case "thursday":
				return dayOfWeek == DayOfWeek.THURSDAY;
			case "friday":
				return dayOfWeek == DayOfWeek.FRIDAY;
			case "saturday":
				return dayOfWeek == DayOfWeek.SATURDAY;
			case "sunday":
				return dayOfWeek == DayOfWeek.SUNDAY;
			default:
				return false;
		}
	}

	public void stopScheduler() {
		scheduler.shutdown();
	}

}

