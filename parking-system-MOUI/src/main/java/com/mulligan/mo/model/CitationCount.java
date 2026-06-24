package com.mulligan.mo.model;

/**
 * Citation total for one parking space in the MO reports.
 */
public class CitationCount {
    private String parkingSpaceId;
    private String parkingZoneId;
    private long citationCount;

    public CitationCount(String parkingSpaceId, String parkingZoneId, long citationCount) {
        this.parkingSpaceId = parkingSpaceId;
        this.parkingZoneId = parkingZoneId;
        this.citationCount = citationCount;
    }

    public String getParkingSpaceId() {
        return parkingSpaceId;
    }

    public void setParkingSpaceId(String parkingSpaceId) {
        this.parkingSpaceId = parkingSpaceId;
    }

    public String getParkingZoneId() {
        return parkingZoneId;
    }

    public void setParkingZoneId(String parkingZoneId) {
        this.parkingZoneId = parkingZoneId;
    }

    public long getCitationCount() {
        return citationCount;
    }

    public void setCitationCount(long citationCount) {
        this.citationCount = citationCount;
    }

    @Override
    public String toString() {
        return "CitationCount{" +
                "parkingSpaceId='" + parkingSpaceId + '\'' +
                ", parkingZoneId='" + parkingZoneId + '\'' +
                ", citationCount=" + citationCount +
                '}';
    }
}
