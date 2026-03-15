package webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import datawrapper.KickpointReason;

public class KickpointReasonDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("clubTag")
    private String clubTag;

    @JsonProperty("amount")
    private Integer amount;

    public KickpointReasonDTO() {}

    public KickpointReasonDTO(KickpointReason reason) {
        try { this.name = reason.getReason(); } catch (Exception e) { this.name = null; }
        try { this.clubTag = reason.getClubTag(); } catch (Exception e) { this.clubTag = null; }
        try { this.amount = reason.getAmount(); } catch (Exception e) { this.amount = null; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getClubTag() { return clubTag; }
    public void setClubTag(String clubTag) { this.clubTag = clubTag; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
}



