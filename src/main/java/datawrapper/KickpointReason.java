package datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import datautil.Connection;
import datautil.DBUtil;

public class KickpointReason {

	private String kpreason;
	private String club_tag;
	private Integer amount;
	private Integer index;

	public KickpointReason(String reason, String club_tag) {
		kpreason = reason;
		this.club_tag = club_tag;
	}

	public boolean Exists() {
		String sql = "SELECT 1 FROM kickpoint_reasons WHERE name = ? AND club_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, kpreason);
			pstmt.setString(2, club_tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	// all public getter Methods

	public String getReason() {
		return kpreason;
	}

	public String getClubTag() {
		return club_tag;
	}

	public int getAmount() {
		if (amount == null) {
			String sql = "SELECT amount FROM kickpoint_reasons WHERE club_tag = ? AND name = ?";
			amount = DBUtil.getValueFromSQL(sql, Integer.class, club_tag, kpreason);
		}
		return amount;
	}

	public Integer getIndex() {
		if (index == null) {
			String sql = "SELECT index FROM kickpoint_reasons WHERE club_tag = ? AND name = ?";
			index = DBUtil.getValueFromSQL(sql, Integer.class, club_tag, kpreason);
		}
		return index;
	}
}






