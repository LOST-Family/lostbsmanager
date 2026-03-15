package datawrapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import org.json.JSONObject;

import datautil.APIUtil;
import datautil.Connection;
import datautil.DBUtil;
import lostbsmanager.Bot;

public class Player {

	public enum RoleType {
		ADMIN, PRESIDENT, COPRESIDENT, SENIOR, MEMBER, NOTINCLUB
	};

	private JSONObject apiresult;
	private String tag;
	private String namedb;
	private String nameapi;
	private User user;
	private Club clubdb;
	private Club clubapi;
	private String clubtagcwdone;
	private Integer trophies;
	private ArrayList<Kickpoint> kickpoints;
	private Long kickpointstotal;
	private RoleType role;
	private Boolean mark;
	private String note;
	private Integer expLevel;

	public Player(String tag) {
		this.tag = tag;
	}

	public Player refreshData() {
		apiresult = null;
		namedb = null;
		nameapi = null;
		user = null;
		clubdb = null;
		clubapi = null;
		kickpoints = null;
		kickpointstotal = null;
		role = null;
		clubtagcwdone = null;
		trophies = null;
		mark = null;
		note = null;
		expLevel = null;
		return this;
	}

	public Player setNameDB(String name) {
		this.namedb = name;
		return this;
	}

	public Player setNameAPI(String name) {
		this.nameapi = name;
		return this;
	}

	public Player setUser(User user) {
		this.user = user;
		return this;
	}

	public Player setClubDB(Club club) {
		this.clubdb = club;
		return this;
	}

	public Player setClubAPI(Club club) {
		this.clubapi = club;
		return this;
	}

	public Player setKickpoints(ArrayList<Kickpoint> kickpoints) {
		this.kickpoints = kickpoints;
		return this;
	}

	public Player setKickpointsTotal(Long kptotal) {
		this.kickpointstotal = kptotal;
		return this;
	}

	public Player setRole(RoleType role) {
		this.role = role;
		return this;
	}

	public Player setMark(boolean mark) {
		this.mark = mark;
		return this;
	}

	public Player setNote(String note) {
		this.note = note;
		return this;
	}

	public Player setClubtagCWDone(String tag) {
		this.clubtagcwdone = tag;
		return this;
	}

	public boolean IsLinked() {
		String sql = "SELECT 1 FROM players WHERE bs_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean AccExists() {
		try {
			String encodedTag = URLEncoder.encode(tag, "UTF-8");
			URL url = URI.create("https://api.brawlstars.com/v1/players/" + encodedTag).toURL();

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Authorization", "Bearer " + Bot.api_key);
			connection.setRequestProperty("Accept", "application/json");

			int responseCode = connection.getResponseCode();

			if (responseCode == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line;
				StringBuilder responseContent = new StringBuilder();
				while ((line = in.readLine()) != null) {
					responseContent.append(line);
				}
				in.close();

				return true;
			} else {
				System.out.println("Verifizierung fehlgeschlagen. Fehlercode: " + responseCode);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	// all public getter Methods

	public String getInfoStringDB() {
		try {
			return getNameDB() + " (" + tag + ")";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getInfoStringAPI() {
		try {
			return getNameAPI() + " (" + tag + ")";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getTag() {
		return tag;
	}

	public String getNameDB() {
		if (namedb == null) {
			namedb = DBUtil.getValueFromSQL("SELECT name FROM players WHERE bs_tag = ?", String.class, tag);
		}
		return namedb;
	}

	public String getNameAPI() {
		if (nameapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
			return jsonObject.getString("name");
		}
		return nameapi;
	}

	public User getUser() {
		if (user == null) {
			String value = DBUtil.getValueFromSQL("SELECT discord_id FROM players WHERE bs_tag = ?", String.class, tag);
			user = value == null ? null : new User(value);
		}
		return user;
	}

	public Club getClubDB() {
		if (clubdb == null) {
			String value = DBUtil.getValueFromSQL("SELECT club_tag FROM club_members WHERE player_tag = ?",
					String.class, tag);
			clubdb = value == null ? null : new Club(value);
		}
		return clubdb;
	}

	public Club getClubAPI() {
		if (clubapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));

			// Prüfen, ob der Schlüssel "club" vorhanden ist und nicht null
			if (jsonObject.has("club") && !jsonObject.isNull("club")) {
				JSONObject clubObject = jsonObject.getJSONObject("club");
				if (clubObject.has("tag")) {
					clubapi = new Club(clubObject.getString("tag"));
				}
			}
		}
		return clubapi;
	}

	public ArrayList<Kickpoint> getActiveKickpoints() {
		if (kickpoints == null) {
			kickpoints = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ? ORDER BY expires_at ASC";
			for (Long id : DBUtil.getArrayListFromSQL(sql, Long.class, tag)) {
				Kickpoint kp = new Kickpoint(id);
				if (kp.getExpirationDate().isAfter(OffsetDateTime.now())) {
					kickpoints.add(kp);
				}
			}
		}
		return kickpoints;
	}

	public long getTotalKickpoints() {
		if (kickpointstotal == null) {
			ArrayList<Kickpoint> a = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ?";
			for (Long id : DBUtil.getArrayListFromSQL(sql, Long.class, tag)) {
				a.add(new Kickpoint(id));
			}
			kickpointstotal = 0L;
			for (Kickpoint kp : a) {
				kickpointstotal = kickpointstotal + kp.getAmount();
			}
		}
		return kickpointstotal;
	}

	public RoleType getRole() {
		if (role == null) {
			if (new Player(tag).getClubDB() == null) {
				return null;
			}
			boolean b = true;
			if (getUser() != null) {
				if (getUser().isAdmin()) {
					role = RoleType.ADMIN;
					b = false;
				}
			}
			if (b) {
				String sql = "SELECT club_role FROM club_members WHERE player_tag = ?";
				try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
					pstmt.setString(1, tag);
					try (ResultSet rs = pstmt.executeQuery()) {
						if (rs.next()) {
							String rolestring = rs.getString("club_role");
							role = rolestring.equals("president") ? RoleType.PRESIDENT
									: rolestring.equals("copresident") || rolestring.equals("hiddencopresident")
											? RoleType.COPRESIDENT
											: rolestring.equals("senior") ? RoleType.SENIOR
													: rolestring.equals("member") ? RoleType.MEMBER : null;
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return role;
	}

	public Integer getTrophies() {
		if (trophies == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			trophies = apiresult.getInt("trophies");
		}
		return trophies;
	}

	public Integer getSTRTrophies() {
		if (trophies == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			String currentseasonstring = Bot.seasonstringfallback;
			if (apiresult.has("leagueStatistics")) {
				JSONObject leagueStatistics = apiresult.getJSONObject("leagueStatistics");
				if (leagueStatistics.has("previousSeason")) {
					JSONObject previousSeason = leagueStatistics.getJSONObject("previousSeason");
					String previousseasonid = previousSeason.getString("id");
					int previousseasonyear = Integer.valueOf(previousseasonid.split("-")[0]);
					int previousseasonmonth = Integer.valueOf(previousseasonid.split("-")[1]);
					int currentseasonyear;
					int currentseasonmonth;
					if (previousseasonmonth + 1 == 13) {
						currentseasonyear = previousseasonyear + 1;
						currentseasonmonth = 1;
					} else {
						currentseasonyear = previousseasonyear;
						currentseasonmonth = previousseasonmonth + 1;
					}
					currentseasonstring = "" + currentseasonyear;
					if (currentseasonmonth < 10) {
						currentseasonstring += "0" + currentseasonmonth;
					} else {
						currentseasonstring += currentseasonmonth;
					}
				}
			}
			if (currentseasonstring != null) {
				Bot.seasonstringfallback = currentseasonstring;
				if (apiresult.has("progress")) {
					JSONObject progress = apiresult.getJSONObject("progress");
					if (progress.has("seasonal-trophy-road-" + currentseasonstring)) {
						JSONObject seasontrophyroad = progress
								.getJSONObject("seasonal-trophy-road-" + currentseasonstring);
						trophies = seasontrophyroad.getInt("trophies");
					}
				}
			}

		}
		return trophies;
	}

	

	

	

	

	public Boolean isMarked() {
		if (mark == null) {
			if (getClubDB() != null) {
				Boolean marked = DBUtil.getValueFromSQL("SELECT marked FROM club_members WHERE player_tag = ?",
						Boolean.class, tag);
				if (marked == null) {
					DBUtil.executeUpdate("UPDATE club_members SET marked = FALSE WHERE player_tag = ?", tag);
					mark = false;
				} else {
					mark = marked;
				}
			}
		}
		return mark == null ? false : mark;
	}

	public String getNote() {
		if (note == null) {
			if (getClubDB() != null) {
				note = DBUtil.getValueFromSQL("SELECT note FROM club_members WHERE player_tag = ?",
						String.class, tag);
			}
		}
		return note;
	}

	

	public String getClubtagCWDone() {
		if (clubtagcwdone == null) {
			// same logic here
			
		}
		return clubtagcwdone;
	}

	

	public boolean isHiddenCopresident() {
		if (getClubDB() == null) {
			return false;
		}
		String rolestring = DBUtil.getValueFromSQL("SELECT club_role FROM club_members WHERE player_tag = ?",
				String.class, tag);
		return "hiddencopresident".equals(rolestring);
	}

	

	public Integer getExpLevelAPI() {
		if (expLevel == null) {
			if (apiresult == null) {
				apiresult = new JSONObject(APIUtil.getPlayerJson(tag));
			}
			if (apiresult.has("expLevel")) {
				expLevel = apiresult.getInt("expLevel");
			}
		}
		return expLevel;
	}

	// ============================================================
	// Centralized Wins Calculation Methods
	// ============================================================

	/**
	 * Helper class to hold wins data with warning flag
	 */

	/**
	 * Helper class to hold wins record from database
	 */

	/**
	 * Calculate monthly wins for a specific month and year
	 * 
	 * @param year             The year
	 * @param month            The month (1-12)
	 * @param isCurrentMonth   Whether this is the current month
	 * @param startOfMonth     Start of the month
	 * @param startOfNextMonth Start of the next month
	 * @param zone             Time zone
	 * @return WinsData containing wins count and warning flag
	 */

	/**
	 * Get wins for the current month
	 * 
	 * @return WinsData containing wins count and warning flag
	 */

	/**
	 * Check if any wins data exists for this player in the database
	 * 
	 * @return true if data exists, false otherwise
	 */

	/**
	 * Get wins record at or after a specific date/time
	 * 
	 * @param dateTime The date/time to search from
	 * @return WinsRecord or null if not found
	 */

	/**
	 * Check if a recorded time is at the start of a month
	 * 
	 * @param recordedAt    The recorded time
	 * @param expectedStart The expected start of month
	 * @return true if recorded time is on the first day of the month
	 */

	/**
	 * Save current wins for this player to the database
	 */


    public boolean isCoPresident() {
        return this.role == RoleType.COPRESIDENT;
    }
}








