package datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import datautil.APIUtil;
import datautil.Connection;
import datautil.DBUtil;

public class Club {

	private String club_tag;
	private String namedb;
	private String descriptiondb;
	private String descriptionapi;
	private String nameapi;
	private ArrayList<Player> playerlistdb;
	private ArrayList<Player> playerlistapi;
	private Long max_kickpoints;
	private Long index;
	private Integer kickpoints_expire_after_days;
	private ArrayList<KickpointReason> kickpoint_reasons;

	public enum Role {
		PRESIDENT, COPRESIDENT, SENIOR, MEMBER
	}

	public Club(String clubtag) {
		club_tag = clubtag;
	}

	public boolean ExistsDB() {
		String sql = "SELECT 1 FROM clubs WHERE tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, club_tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	// all public getter Methods

	public String getRoleID(Role role) {
		switch (role) {
			case PRESIDENT:
				return DBUtil.getValueFromSQL("SELECT president_roleid FROM clubs WHERE tag = ?", String.class,
						club_tag);
			case COPRESIDENT:
				return DBUtil.getValueFromSQL("SELECT copresident_roleid FROM clubs WHERE tag = ?", String.class,
						club_tag);
			case SENIOR:
				return DBUtil.getValueFromSQL("SELECT senior_roleid FROM clubs WHERE tag = ?", String.class, club_tag);
			case MEMBER:
				return DBUtil.getValueFromSQL("SELECT member_roleid FROM clubs WHERE tag = ?", String.class, club_tag);
		}
		return null;
	}

	public String getInfoStringAPI() {
		if (!club_tag.equals("warteliste")) {
			return getNameAPI() + " (" + club_tag + ")";
		} else {
			return getNameAPI();
		}
	}

	public String getInfoStringDB() {
		if (!club_tag.equals("warteliste")) {
			return getNameDB() + " (" + club_tag + ")";
		} else {
			return getNameDB();
		}
	}

	public String getTag() {
		return club_tag;
	}

	public Long getIndex() {
		if (index == null) {
			String sql = "SELECT index FROM clubs WHERE tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, club_tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						index = rs.getLong("index");
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return index;
	}

	public ArrayList<Player> getPlayersDB() {
		if (playerlistdb == null) {
			String sql = "SELECT player_tag FROM club_members WHERE club_tag = ? ";
			ArrayList<String> result = DBUtil.getArrayListFromSQL(sql, String.class, club_tag);

			playerlistdb = new ArrayList<>();
			for (String tags : result) {
				playerlistdb.add(new Player(tags));
			}
		}
		return playerlistdb;
	}

	public ArrayList<Player> getPlayersAPI() {
		if (playerlistapi == null) {
			playerlistapi = new ArrayList<>();
			JSONObject jsonObject = new JSONObject(APIUtil.getClubJson(club_tag));

			JSONArray members = jsonObject.getJSONArray("members");

			for (int i = 0; i < members.length(); i++) {
				JSONObject member = members.getJSONObject(i);
				if (member.has("tag") && member.has("name")) {
					playerlistapi.add(new Player(member.getString("tag")).setNameAPI(member.getString("name")));
				}
			}
		}
		return playerlistapi;
	}

	public Long getMaxKickpoints() {
		if (max_kickpoints == null) {
			String sql = "SELECT max_kickpoints FROM club_settings WHERE club_tag = ?";
			max_kickpoints = DBUtil.getValueFromSQL(sql, Long.class, club_tag);
		}
		return max_kickpoints;
	}

	public Integer getDaysKickpointsExpireAfter() {
		if (kickpoints_expire_after_days == null) {
			String sql = "SELECT kickpoints_expire_after_days FROM club_settings WHERE club_tag = ?";
			kickpoints_expire_after_days = DBUtil.getValueFromSQL(sql, Integer.class, club_tag);
		}
		return kickpoints_expire_after_days;
	}

	public ArrayList<KickpointReason> getKickpointReasons() {
		if (kickpoint_reasons == null) {
			kickpoint_reasons = new ArrayList<>();

			String sql = "SELECT name, club_tag FROM kickpoint_reasons WHERE club_tag = ? ORDER BY index ASC";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setObject(1, club_tag);

				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						kickpoint_reasons.add(new KickpointReason(rs.getString("name"), rs.getString("club_tag")));
					}
					Statement stmt = rs.getStatement();
					rs.close();
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return kickpoint_reasons;
	}

	public String getNameDB() {
		if (namedb == null) {
			String sql = "SELECT name FROM clubs WHERE tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, club_tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						namedb = rs.getString("name");
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return namedb;
	}

	public String getNameAPI() {
		if (nameapi == null) {
			JSONObject jsonObject = new JSONObject(APIUtil.getClubJson(club_tag));
			nameapi = jsonObject.getString("name");
		}
		return nameapi;
	}

	public String getDescriptionDB() {
		if (descriptiondb == null) {
			String sql = "SELECT description FROM clubs WHERE tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, club_tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						descriptiondb = rs.getString("description");
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return descriptiondb;
	}

	public String getDescriptionAPI() {
		if (descriptionapi == null) {
			JSONObject jsonobject = new JSONObject(APIUtil.getClubJson(club_tag));
			if (jsonobject.has("description") && !jsonobject.isNull("description")) {
				descriptionapi = jsonobject.getString("description");
			}
		}
		return descriptionapi;
	}
}
