package aim4.im.v2i.policy.utils;

/**
 * The record of a reservation.
 */
public class ReservationRecord {
    /**
     * The VIN of a vehicle
     */
    private int vin;
    /**
     * The ACZ lane ID
     */
    private int aczLaneId;

    /**
     * Create a record of a reservation
     *
     * @param vin       the VIN of a vehicle
     * @param aczLaneId the ACZ lane ID
     */
    public ReservationRecord(int vin, int aczLaneId) {
        this.vin = vin;
        this.aczLaneId = aczLaneId;
    }

    /**
     * Get the VIN of a vehicle.
     *
     * @return the VIN of a vehicle
     */
    public int getVin() {
        return vin;
    }

    /**
     * Get the ACZ lane ID.
     *
     * @return the ACZ lane ID
     */
    public int getAczLaneId() {
        return aczLaneId;
    }
}
