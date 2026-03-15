package webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import datawrapper.KickpointReason;

public class KickpointReasonDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("ClubTag")
    private String ClubTag;

    @JsonProperty("amount")
    private Integer amount;

    public KickpointReasonDTO() {}

    public KickpointReasonDTO(KickpointReason reason) {
        try { this.name = reason.getReason(); } catch (Exception e) { this.name = null; }
        try { this.ClubTag = reason.getClubTag(); } catch (Exception e) { this.ClubTag = null; }
        try { this.amount = reason.getAmount(); } catch (Exception e) { this.amount = null; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getClubTag() { return ClubTag; }
    public void setClubTag(String ClubTag) { this.ClubTag = ClubTag; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
}

