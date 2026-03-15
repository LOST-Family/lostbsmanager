package datawrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import lostbsmanager.Bot;
import datautil.DBManager;
import datautil.DBUtil;

public class User {

	public enum PermissionType {
		ADMIN, LEADER, COLEADER, ELDER, MEMBER, NOTHING
	}

	private HashMap<String, Player.RoleType> Clubroles;
	private String userid;
	private ArrayList<Player> linkedaccounts;
	private Boolean isadmin;
	private String nickname;

	public User(String userid) {
		this.userid = userid;
	}

	public User refreshData() {
		Clubroles = null;
		linkedaccounts = null;
		isadmin = null;
		return this;
	}

	// all public getter Methods
	public String getUserID() {
		return userid;
	}

	public boolean isAdmin() {
		if (isadmin == null) {
			if (DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class,
					userid) == null) {
				DBUtil.executeUpdate("INSERT INTO users (discord_id, name, is_admin) VALUES (?, ?, ?)", userid,
						getNickname(), false);
			}
			isadmin = DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class, userid);
		}
		return isadmin;
	}

	@SuppressWarnings("null")
	public String getNickname() {
		if (nickname == null) {
			try {
				nickname = Bot.getJda().getGuildById(Bot.guild_id).retrieveMemberById(userid).submit().get()
						.getEffectiveName();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		return nickname;
	}

	public ArrayList<Player> getAllLinkedAccounts() {
		if (linkedaccounts == null) {
			linkedaccounts = new ArrayList<>();
			String sql = "SELECT bs_tag FROM players WHERE discord_id = ?";
			for (String tag : DBUtil.getArrayListFromSQL(sql, String.class, userid)) {
				linkedaccounts.add(new Player(tag));
			}
		}
		return linkedaccounts;
	}

	public HashMap<String, Player.RoleType> getClubRoles() {
		if (Clubroles == null) {
			Clubroles = new HashMap<>();
			boolean admin = false;
			if (DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class,
					userid) == null) {
				DBUtil.executeUpdate("INSERT INTO users (discord_id, is_admin) VALUES (?, ?)", userid, false);
			}
			if (DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class, userid)) {
				admin = true;
			}

			ArrayList<Player> linkedaccs = getAllLinkedAccounts();
			ArrayList<String> allClubs = DBManager.getAllClubs();
			if (admin) {
				for (String Clubtag : allClubs) {
					Clubroles.put(Clubtag, Player.RoleType.ADMIN);
				}
			} else {
				for (Player p : linkedaccs) {
					if (p.getClubDB() != null) {
						Clubroles.put(p.getClubDB().getTag(), p.getRole());
					}
				}
			}
			for (String Clubtag : allClubs) {
				if (!Clubroles.containsKey(Clubtag)) {
					Clubroles.put(Clubtag, Player.RoleType.NOTINClub);
				}
			}
		}
		return Clubroles;
	}

	public boolean isColeaderOrHigherInClub(String Clubtag) {
		Player.RoleType role = getClubRoles().get(Clubtag);
		return role == Player.RoleType.ADMIN
				|| role == Player.RoleType.LEADER
				|| role == Player.RoleType.COLEADER;
	}

	public boolean isColeaderOrHigher() {
		if (isAdmin())
			return true;

		for (Player.RoleType role : getClubRoles().values()) {
			if (role == Player.RoleType.LEADER || role == Player.RoleType.COLEADER || role == Player.RoleType.ADMIN) {
				return true;
			}
		}
		return false;
	}

}

