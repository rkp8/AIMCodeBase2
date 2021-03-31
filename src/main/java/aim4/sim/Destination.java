package aim4.sim;

public class Destination {
    private String destination; // private = restricted access
    private int vin; // private = restricted access

    public Destination(int vin, String destination){
        this.destination = destination;
        this.vin = vin;
    }



    // Getter
    public String getDestination() {
        return destination;
    }

    // Setter
    public void settheDestination(String newDestination) {
        this.destination = newDestination;
    }


    // Getter
    public int gettheVin() {
        return vin;
    }

    // Setter
    public void settheVin(int newVin) {
        this.vin = newVin;
    }

}
