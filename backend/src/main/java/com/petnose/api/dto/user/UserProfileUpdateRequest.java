package com.petnose.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserProfileUpdateRequest {

    private boolean displayNamePresent;
    private String displayName;
    private boolean contactPhonePresent;
    private String contactPhone;
    private boolean regionPresent;
    private String region;

    @JsonProperty("display_name")
    public void setDisplayName(String displayName) {
        this.displayNamePresent = true;
        this.displayName = displayName;
    }

    @JsonProperty("contact_phone")
    public void setContactPhone(String contactPhone) {
        this.contactPhonePresent = true;
        this.contactPhone = contactPhone;
    }

    @JsonProperty("region")
    public void setRegion(String region) {
        this.regionPresent = true;
        this.region = region;
    }

    public boolean hasDisplayName() {
        return displayNamePresent;
    }

    public String displayName() {
        return displayName;
    }

    public boolean hasContactPhone() {
        return contactPhonePresent;
    }

    public String contactPhone() {
        return contactPhone;
    }

    public boolean hasRegion() {
        return regionPresent;
    }

    public String region() {
        return region;
    }

    public boolean hasAnyProfileField() {
        return displayNamePresent || contactPhonePresent || regionPresent;
    }
}
