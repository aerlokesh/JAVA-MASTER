import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class ParkingLotFullException extends Exception{
    ParkingLotFullException(String message){
        super(message);
    }
}
class InvalidVehicleException extends Exception{
    InvalidVehicleException(String message){
        super(message);
    }
}
enum VehicleType{MOTORCYCLE,CAR,TRUCK,BUS}
enum SpotType{COMPACT,REGULAR,LARGE}
class Vehicle{
    String licensePlate;
    VehicleType type;
    Vehicle(String licensePlate,VehicleType type){
        this.type=type;
        this.licensePlate=licensePlate;
    }
}
class ParkingSpot{
    String spotId;
    SpotType type;
    int floor;
    Vehicle currentVehicle;
    public ParkingSpot(String spotId, SpotType type, int floor) {
        this.spotId = spotId;
        this.type = type;
        this.floor = floor;
    }
    boolean isAvailable(){return currentVehicle==null;}
    boolean canFit(VehicleType vehicleType){
        return switch (vehicleType){
            case MOTORCYCLE -> true;
            case CAR -> this.type == SpotType.REGULAR || this.type == SpotType.LARGE;
            case TRUCK, BUS -> this.type == SpotType.LARGE;
            default -> false;
        };
    }
    void park(Vehicle vehicle){this.currentVehicle=vehicle;}
    void unpark(){this.currentVehicle=null;}
}
class ParkingTicket{
    String ticketId;
    Vehicle vehicle;
    ParkingSpot spot;
    long entryTime;
    long exitTime;
    double fees;

    public ParkingTicket(ParkingSpot spot, Vehicle vehicle, String ticketId) {
        this.spot = spot;
        this.vehicle = vehicle;
        this.ticketId = ticketId;
        this.entryTime=System.currentTimeMillis();
    }
}
class ParkingService{
    String name;
    int numFloors;
    List<List<ParkingSpot>> floors;
    Map<String,ParkingTicket> activeTickets;
    AtomicInteger ticketCounter;
    Map<VehicleType,Double> hourlyRates;

    ParkingService(int numFloors, String name, Map<VehicleType, Double> hourlyRates,int compact,int regular,int large) {
        this.numFloors = numFloors;
        this.name = name;
        this.hourlyRates = hourlyRates;
        this.floors=new ArrayList<>();
        this.activeTickets=new HashMap<>();
        this.ticketCounter=new AtomicInteger(1);
        for(int f=1;f<=numFloors;f++){
            List<ParkingSpot> spots=new ArrayList<>();
            for(int i=0;i<compact;i++) spots.add(new ParkingSpot("F"+f+"C"+i,SpotType.COMPACT,f));
            for(int i=0;i<regular;i++) spots.add(new ParkingSpot("F"+f+"C"+i,SpotType.REGULAR,f));
            for(int i=0;i<large;i++) spots.add(new ParkingSpot("F"+f+"C"+i,SpotType.LARGE,f));
            floors.add(spots);
        }
    }
    ParkingTicket park(Vehicle vehicle) throws InvalidVehicleException, ParkingLotFullException {
        if(vehicle==null || vehicle.licensePlate.isEmpty()) throw new InvalidVehicleException("invalid vehicle plate");
        if(activeTickets.containsKey(vehicle.licensePlate)) throw new InvalidVehicleException("already parked");
        ParkingSpot spot=findSpot(vehicle.type);
        if(spot==null) throw new ParkingLotFullException("full parking lot");
        spot.park(vehicle);
        ParkingTicket ticket=new ParkingTicket(spot,vehicle,"T-"+ticketCounter.incrementAndGet());
        activeTickets.put(vehicle.licensePlate,ticket);
        return ticket;
    }
    ParkingTicket unpark(Vehicle vehicle) throws InvalidVehicleException {
        ParkingTicket ticket=activeTickets.get(vehicle.licensePlate);
        if(ticket==null) throw new InvalidVehicleException("Vehicle not found");
        ticket.exitTime=System.currentTimeMillis();
        int hours= Math.toIntExact(Math.max(1, (ticket.exitTime - ticket.entryTime) / 1000 * 60 * 60));
        ticket.fees= hourlyRates.get(vehicle.type)*hours;
        ticket.spot.unpark();
        activeTickets.remove(vehicle.licensePlate);
        return ticket;
    }
    ParkingSpot findSpot(VehicleType vehicleType){
        return floors.stream().flatMap(List::stream).filter(ParkingSpot::isAvailable).filter(x->x.canFit(vehicleType)).findFirst().orElse(null);
    }

}
public class ParkingLotTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String testName, boolean condition) {
        if (condition) {
            System.out.println("  ✅ PASS: " + testName);
            passed++;
        } else {
            System.out.println("  ❌ FAIL: " + testName);
            failed++;
        }
    }

    static Map<VehicleType, Double> defaultRates() {
        Map<VehicleType, Double> rates = new HashMap<>();
        rates.put(VehicleType.MOTORCYCLE, 5.0);
        rates.put(VehicleType.CAR, 10.0);
        rates.put(VehicleType.TRUCK, 20.0);
        rates.put(VehicleType.BUS, 25.0);
        return rates;
    }

    public static void main(String[] args) throws Exception {

        System.out.println("==========================================");
        System.out.println(" Parking Lot  —  Test Suite");
        System.out.println("==========================================\n");

        // ── 1. Vehicle creation ──
        System.out.println("── Vehicle Creation ──");
        {
            Vehicle car = new Vehicle("ABC-123", VehicleType.CAR);
            check("Vehicle license plate stored", car.licensePlate.equals("ABC-123"));
            check("Vehicle type stored", car.type == VehicleType.CAR);

            Vehicle moto = new Vehicle("MOTO-1", VehicleType.MOTORCYCLE);
            check("Motorcycle type stored", moto.type == VehicleType.MOTORCYCLE);
        }
        System.out.println();

        // ── 2. ParkingSpot basics ──
        System.out.println("── ParkingSpot Basics ──");
        {
            ParkingSpot spot = new ParkingSpot("F1C0", SpotType.REGULAR, 1);
            check("Spot starts available", spot.isAvailable());
            check("Spot ID correct", spot.spotId.equals("F1C0"));
            check("Spot type correct", spot.type == SpotType.REGULAR);
            check("Spot floor correct", spot.floor == 1);

            Vehicle car = new Vehicle("X-1", VehicleType.CAR);
            spot.park(car);
            check("Spot occupied after park()", !spot.isAvailable());
            check("Spot holds correct vehicle", spot.currentVehicle == car);

            spot.unpark();
            check("Spot available after unpark()", spot.isAvailable());
            check("Spot vehicle is null after unpark()", spot.currentVehicle == null);
        }
        System.out.println();

        // ── 3. ParkingSpot.canFit() logic ──
        // NOTE: There's a bug — canFit() compares VehicleType param with SpotType
        // using .equals(), which always returns false for different enum types.
        // Only MOTORCYCLE returns true (hardcoded). These tests reveal the bug.
        System.out.println("── ParkingSpot.canFit() ──");
        {
            ParkingSpot compact = new ParkingSpot("S1", SpotType.COMPACT, 1);
            ParkingSpot regular = new ParkingSpot("S2", SpotType.REGULAR, 1);
            ParkingSpot large = new ParkingSpot("S3", SpotType.LARGE, 1);

            // Motorcycle should fit anywhere
            check("Motorcycle fits COMPACT", compact.canFit(VehicleType.MOTORCYCLE));
            check("Motorcycle fits REGULAR", regular.canFit(VehicleType.MOTORCYCLE));
            check("Motorcycle fits LARGE", large.canFit(VehicleType.MOTORCYCLE));

            // Car should fit REGULAR and LARGE (will FAIL due to bug)
            check("Car fits REGULAR", regular.canFit(VehicleType.CAR));
            check("Car fits LARGE", large.canFit(VehicleType.CAR));
            check("Car does NOT fit COMPACT", !compact.canFit(VehicleType.CAR));

            // Truck should fit LARGE only (will FAIL due to bug)
            check("Truck fits LARGE", large.canFit(VehicleType.TRUCK));
            check("Truck does NOT fit REGULAR", !regular.canFit(VehicleType.TRUCK));
            check("Truck does NOT fit COMPACT", !compact.canFit(VehicleType.TRUCK));

            // Bus should fit LARGE only (will FAIL due to bug)
            check("Bus fits LARGE", large.canFit(VehicleType.BUS));
            check("Bus does NOT fit REGULAR", !regular.canFit(VehicleType.BUS));
        }
        System.out.println();

        // ── 4. ParkingService — constructor & floor setup ──
        // NOTE: Loop uses f=1;f<numFloors so numFloors=3 creates only 2 floors.
        System.out.println("── ParkingService — Constructor ──");
        {
            ParkingService lot = new ParkingService(3, "TestLot", defaultRates(), 2, 3, 1);
            check("Lot name stored", lot.name.equals("TestLot"));
            check("numFloors stored", lot.numFloors == 3);
            // Bug: loop starts at 1 and goes < numFloors, so only 2 floors created
            check("Floor count created (expects 3, bug creates 2)", lot.floors.size() == 3);
        }
        System.out.println();

        // ── 5. ParkingService — park motorcycle ──
        // Motorcycle is the only type that can actually park due to canFit() bug
        System.out.println("── ParkingService — Park Motorcycle ──");
        {
            ParkingService lot = new ParkingService(3, "MotoLot", defaultRates(), 2, 2, 2);
            Vehicle moto = new Vehicle("MOTO-1", VehicleType.MOTORCYCLE);
            ParkingTicket ticket = lot.park(moto);
            check("Ticket is not null", ticket != null);
            check("Ticket ID starts with T-", ticket.ticketId.startsWith("T-"));
            check("Ticket vehicle matches", ticket.vehicle == moto);
            check("Ticket spot is assigned", ticket.spot != null);
            check("Entry time is set", ticket.entryTime > 0);
            check("Active tickets contains vehicle", lot.activeTickets.containsKey("MOTO-1"));
        }
        System.out.println();

        // ── 6. ParkingService — park car (will fail due to canFit bug) ──
        System.out.println("── ParkingService — Park Car ──");
        {
            ParkingService lot = new ParkingService(3, "CarLot", defaultRates(), 0, 5, 5);
            Vehicle car = new Vehicle("CAR-1", VehicleType.CAR);
            boolean parked = false;
            boolean fullException = false;
            try {
                ParkingTicket ticket = lot.park(car);
                parked = true;
            } catch (ParkingLotFullException e) {
                fullException = true;
            }
            check("Car should park in REGULAR/LARGE spot (fails due to canFit bug)", parked);
            if (fullException) {
                System.out.println("    ⚠️  ParkingLotFullException thrown — canFit() bug prevents CAR parking");
            }
        }
        System.out.println();

        // ── 7. ParkingService — invalid vehicle ──
        System.out.println("── ParkingService — Invalid Vehicle ──");
        {
            ParkingService lot = new ParkingService(3, "TestLot", defaultRates(), 2, 2, 2);
            boolean threwForNull = false;
            try { lot.park(null); }
            catch (Exception e) { threwForNull = true; }
            check("Throws for null vehicle", threwForNull);

            boolean threwForEmpty = false;
            try { lot.park(new Vehicle("", VehicleType.CAR)); }
            catch (InvalidVehicleException e) { threwForEmpty = true; }
            check("Throws for empty license plate", threwForEmpty);
        }
        System.out.println();

        // ── 8. ParkingService — duplicate parking ──
        System.out.println("── ParkingService — Duplicate Parking ──");
        {
            ParkingService lot = new ParkingService(3, "DupLot", defaultRates(), 5, 5, 5);
            Vehicle moto = new Vehicle("DUP-1", VehicleType.MOTORCYCLE);
            lot.park(moto);
            boolean threwDup = false;
            try { lot.park(moto); }
            catch (InvalidVehicleException e) { threwDup = true; }
            check("Throws when parking already-parked vehicle", threwDup);
        }
        System.out.println();

        // ── 9. ParkingService — unpark ──
        System.out.println("── ParkingService — Unpark ──");
        {
            ParkingService lot = new ParkingService(3, "UnparkLot", defaultRates(), 5, 5, 5);
            Vehicle moto = new Vehicle("UP-1", VehicleType.MOTORCYCLE);
            ParkingTicket parkTicket = lot.park(moto);
            Thread.sleep(10); // small delay so exit > entry
            ParkingTicket unparkTicket = lot.unpark(moto);
            check("Unpark returns ticket", unparkTicket != null);
            check("Exit time is set", unparkTicket.exitTime > 0);
            check("Exit time > entry time", unparkTicket.exitTime > unparkTicket.entryTime);
            check("Fees are calculated (> 0)", unparkTicket.fees > 0);
            check("Vehicle removed from active tickets", !lot.activeTickets.containsKey("UP-1"));

            // BUG: spot.unpark() is never called in ParkingService.unpark()
            check("Spot freed after unpark (bug: spot.unpark() not called)", parkTicket.spot.isAvailable());
        }
        System.out.println();

        // ── 10. ParkingService — unpark non-existent vehicle ──
        System.out.println("── ParkingService — Unpark Non-Existent ──");
        {
            ParkingService lot = new ParkingService(3, "ErrLot", defaultRates(), 2, 2, 2);
            boolean threwNotFound = false;
            try { lot.unpark(new Vehicle("GHOST-1", VehicleType.CAR)); }
            catch (InvalidVehicleException e) { threwNotFound = true; }
            check("Throws when unparking non-existent vehicle", threwNotFound);
        }
        System.out.println();

        // ── 11. ParkingService — lot capacity ──
        System.out.println("── ParkingService — Lot Full ──");
        {
            // 3 floors × 1 compact per floor = 3 spots total
            ParkingService lot = new ParkingService(3, "TinyLot", defaultRates(), 1, 0, 0);
            lot.park(new Vehicle("M1", VehicleType.MOTORCYCLE));
            lot.park(new Vehicle("M2", VehicleType.MOTORCYCLE));
            lot.park(new Vehicle("M3", VehicleType.MOTORCYCLE));
            boolean threwFull = false;
            try { lot.park(new Vehicle("M4", VehicleType.MOTORCYCLE)); }
            catch (ParkingLotFullException e) { threwFull = true; }
            check("Throws ParkingLotFullException when lot is full", threwFull);
        }
        System.out.println();

        // ── 12. ParkingTicket fields ──
        System.out.println("── ParkingTicket Fields ──");
        {
            ParkingSpot spot = new ParkingSpot("T-SPOT", SpotType.COMPACT, 1);
            Vehicle v = new Vehicle("TKT-1", VehicleType.MOTORCYCLE);
            ParkingTicket ticket = new ParkingTicket(spot, v, "T-100");
            check("Ticket ID set", ticket.ticketId.equals("T-100"));
            check("Ticket vehicle set", ticket.vehicle == v);
            check("Ticket spot set", ticket.spot == spot);
            check("Entry time > 0", ticket.entryTime > 0);
            check("Exit time initially 0", ticket.exitTime == 0);
            check("Fees initially 0", ticket.fees == 0.0);
        }
        System.out.println();

        // ── Summary ──
        System.out.println("==========================================");
        System.out.printf(" Results:  %d passed,  %d failed%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.out.println("\n⚠️  Known bugs in source code:");
            System.out.println("  1. canFit(): compares VehicleType with SpotType via .equals() — always false for CAR/TRUCK/BUS");
            System.out.println("     Fix: change 'type.equals(...)' to 'this.type.equals(...)' inside canFit()");
            System.out.println("  2. Constructor loop: 'f=1;f<numFloors' creates numFloors-1 floors instead of numFloors");
            System.out.println("     Fix: change to 'f=0;f<numFloors' or 'f=1;f<=numFloors'");
            System.out.println("  3. unpark(): never calls spot.unpark() — spot stays occupied");
            System.out.println("     Fix: add 'ticket.spot.unpark()' in ParkingService.unpark()");
            System.out.println("  4. Fee calc: divides by 1000 then multiplies by 3600 (wrong order)");
            System.out.println("     Fix: '/ (1000 * 60 * 60)' instead of '/ 1000 * 60 * 60'");
            System.exit(1);
        }
    }
}
