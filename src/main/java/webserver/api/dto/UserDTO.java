package webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import datawrapper.User;
import datawrapper.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class UserDTO {

    @JsonProperty("isAdmin")
    private boolean isAdmin;

    @JsonProperty("linkedPlayers")
    private List<String> linkedPlayers;

    @JsonProperty("highestRole")
    private String highestRole;

    @JsonProperty("nickname")
    private String nickname;

    public UserDTO() {
        this.linkedPlayers = new ArrayList<>();
    }

    public UserDTO(User user) {
        this.isAdmin = user.isAdmin();

        this.linkedPlayers = new ArrayList<>();
        ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();
        if (linkedAccounts != null) {
            for (Player player : linkedAccounts) { this.linkedPlayers.add(player.getTag()); }
        }

        HashMap<String, Player.RoleType> roles = user.getClubRoles();
        Collection<Player.RoleType> rolestypes = roles.values();

        Player.RoleType highest = Player.RoleType.NOTINCLUB;

        for (Player.RoleType role : rolestypes) {
            switch (role) {
            case ADMIN:
                highestRole = role.toString();
                break;
            case PRESIDENT:
                if (highest != Player.RoleType.ADMIN)
                    highestRole = role.toString();
                break;
            case COPRESIDENT:
                if (highest != Player.RoleType.ADMIN || highest != Player.RoleType.PRESIDENT)
                    highestRole = role.toString();
                break;
            case SENIOR:
                if (highest != Player.RoleType.ADMIN || highest != Player.RoleType.PRESIDENT
                        || highest != Player.RoleType.COPRESIDENT)
                    highestRole = role.toString();
                break;
            case MEMBER:
                if (highest != Player.RoleType.ADMIN || highest != Player.RoleType.PRESIDENT
                        || highest != Player.RoleType.COPRESIDENT || highest != Player.RoleType.SENIOR)
                    highestRole = role.toString();
                break;
            default:
                break;
            }
        }

        this.nickname = user.getNickname();
    }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }
    public List<String> getLinkedPlayers() { return linkedPlayers; }
    public void setLinkedPlayers(List<String> linkedPlayers) { this.linkedPlayers = linkedPlayers; }
    public String getHighestRole() { return highestRole; }
    public void setHighestRole(String role) { highestRole = role; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}






