package datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import datautil.Connection;
import datautil.DBUtil;

public class KickpointReason {

	private String kpreason;
	private String Club_tag;
	private Integer amount;
	private Integer index;

	public KickpointReason(String reason, String Club_tag) {
		kpreason = reason;
		this.Club_tag = Club_tag;
	}

	public boolean Exists() {
		String sql = "SELECT 1 FROM kickpoint_reasons WHERE name = ? AND Club_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, kpreason);
			pstmt.setString(2, Club_tag);
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
		return Club_tag;
	}

	public int getAmount() {
		if (amount == null) {
			String sql = "SELECT amount FROM kickpoint_reasons WHERE Club_tag = ? AND name = ?";
			amount = DBUtil.getValueFromSQL(sql, Integer.class, Club_tag, kpreason);
		}
		return amount;
	}

	public Integer getIndex() {
		if (index == null) {
			String sql = "SELECT index FROM kickpoint_reasons WHERE Club_tag = ? AND name = ?";
			index = DBUtil.getValueFromSQL(sql, Integer.class, Club_tag, kpreason);
		}
		return index;
	}
}

