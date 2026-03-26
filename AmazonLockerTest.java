import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

class NoCompartmentException extends Exception {
    public NoCompartmentException(String message) {
        super(message);
    }
}
class InvalidCodeException extends Exception {
    public InvalidCodeException(String message) {
        super(message);

    }
}
class ParcelNotFoundException extends Exception {
    public ParcelNotFoundException(String message) {
        super(message);
    }
}
class AlreadyPickedUpException extends Exception {
    public AlreadyPickedUpException(String message) {
        super(message);
    }
}
class ExpiredCodeException extends Exception {
    public ExpiredCodeException(String message) {
        super(message);
    }
}
enum CompartmentStatus{FREE,OCCUPIED}
enum Size{SMALL,MEDIUM,LARGE}
class Assignment{
    String code;
    String parcelId;
    String compartmentId;
    LocalDateTime expiresAt;
    boolean isPickedUp;

    public Assignment(String code, String parcelId, String compartmentId) {
        this.code = code;
        this.parcelId = parcelId;
        this.compartmentId = compartmentId;
        this.expiresAt = LocalDateTime.now().plusDays(30);
        isPickedUp = false;
    }
    boolean isExpired(){
        return expiresAt.isBefore(LocalDateTime.now());
    }
    @Override
    public String toString() {
        return "Assignment{" +
                "code='" + code + '\'' +
                ", parcelId='" + parcelId + '\'' +
                ", compartmentId='" + compartmentId + '\'' +
                ", expiresAt=" + expiresAt +
                ", isPickedUp=" + isPickedUp +
                '}';
    }
}
class Compartment{
    String id;
    CompartmentStatus status;
    Size size;
    public Compartment(Size size) {
        status = CompartmentStatus.FREE;
        this.size = size;
        this.id= UUID.randomUUID().toString().substring(0, 8);
    }

    public Compartment(String id, CompartmentStatus status, Size size) {
        this.id = id;
        this.status = status;
        this.size = size;
    }
}
class Parcel{
    String id;
    Size size;
    String recipientName;
    public Parcel(Size size, String recipientName) {
        this.size = size;
        this.recipientName = recipientName;
        this.id= UUID.randomUUID().toString().substring(0, 8);
    }
}
class Locker{
    String id;
    Map<String,String> parcelToCode;
    Map<String,Assignment> codeToAssignment;
    List<Compartment> compartments;
    public Locker(List<Compartment> compartments) {
        this.compartments = compartments;
        parcelToCode = new HashMap<>();
        codeToAssignment = new HashMap<>();
        id= UUID.randomUUID().toString().substring(0, 8);
    }
    String addParcel(Parcel parcel) throws NoCompartmentException {
        Compartment freeCompartment =findFreeCompartment(parcel.size);
        if(freeCompartment == null) freeCompartment = findLargerCompartment(parcel.size);
        if(freeCompartment == null) throw new NoCompartmentException("No compartment for "+parcel);
        String code=generateCode();
        Assignment assignment=new Assignment(code,parcel.id,freeCompartment.id);
        parcelToCode.put(parcel.id, code);
        codeToAssignment.put(code,assignment);
        freeCompartment.status = CompartmentStatus.OCCUPIED;
        return code;
    }
    Assignment pickUpParcel(String code) throws InvalidCodeException, AlreadyPickedUpException, ExpiredCodeException {
        Assignment assignment=codeToAssignment.get(code);
        if(assignment==null) throw new InvalidCodeException("No assignment for "+code);
        if(assignment.isPickedUp) throw new AlreadyPickedUpException("Assignment already picked up");
        if(assignment.isExpired()) throw new ExpiredCodeException("Code expired");
        Compartment compartment=findCompartmentById(assignment.compartmentId);
        compartment.status = CompartmentStatus.FREE;
        assignment.isPickedUp=true;
        return assignment;
    }
    int cleanUp(){
        List<String> expiredKeys=codeToAssignment.entrySet().stream().filter(x->x.getValue().isExpired()).map(Map.Entry::getKey).toList();
        for(String expiredKey:expiredKeys){
            Assignment assignment=codeToAssignment.remove(expiredKey);
            if(assignment!=null){
                findCompartmentById(assignment.compartmentId).status = CompartmentStatus.FREE;
            }
        }
        return expiredKeys.size();
    }
    Compartment findFreeCompartment(Size size){
        return compartments.stream().filter(compartment -> compartment.status==CompartmentStatus.FREE && compartment.size.equals(size)).findFirst().orElse(null);
    }
    Compartment findLargerCompartment(Size size){
        return compartments.stream().filter(compartment -> compartment.status == CompartmentStatus.FREE && compartment.size.ordinal() > size.ordinal()).min(Comparator.comparing(c -> c.size)).orElse(null);
    }
    Compartment findCompartmentById(String id){
        return compartments.stream().filter(compartment -> compartment.id.equals(id)).findFirst().orElse(null);
    }
    String generateCode(){
        for(int i=0;i<10;i++){
            String code="CODE"+UUID.randomUUID().toString().substring(0, 8);
            if(codeToAssignment.containsKey(code)) continue;
            return code;
        }
        return null;
    }
}
public class AmazonLockerTest {
    static int passed = 0, failed = 0;
    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }

    static Locker newLocker() {
        List<Compartment> comps = new ArrayList<>();
        comps.add(new Compartment(Size.SMALL));
        comps.add(new Compartment(Size.SMALL));
        comps.add(new Compartment(Size.MEDIUM));
        comps.add(new Compartment(Size.MEDIUM));
        comps.add(new Compartment(Size.LARGE));
        return new Locker(comps);
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      Amazon Locker Test Suite            ║");
        System.out.println("╚══════════════════════════════════════════╝");

        testExceptionClasses();
        testEnums();
        testAssignmentClass();
        testCompartmentClass();
        testParcelClass();
        testLockerConstructor();
        testAddParcelBasic();
        testAddParcelSizeMatching();
        testAddParcelUpsize();
        testAddParcelNoCompartmentThrows();
        testPickUpParcel();
        testPickUpInvalidCodeThrows();
        testPickUpAlreadyPickedUpThrows();
        testPickUpExpiredThrows();
        testCompartmentFreedAfterPickup();
        testGenerateCode();
        testFindFreeCompartment();
        testFindLargerCompartment();
        testFindCompartmentById();
        testCleanUp();
        testMultipleParcels();
        testEndToEnd();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    static void testExceptionClasses() {
        System.out.println("\n── Exceptions ──");
        check("NoCompartmentException", "m".equals(new NoCompartmentException("m").getMessage()));
        check("InvalidCodeException", "m".equals(new InvalidCodeException("m").getMessage()));
        check("ParcelNotFoundException", "m".equals(new ParcelNotFoundException("m").getMessage()));
        check("AlreadyPickedUpException", "m".equals(new AlreadyPickedUpException("m").getMessage()));
        check("ExpiredCodeException", "m".equals(new ExpiredCodeException("m").getMessage()));
    }

    static void testEnums() {
        System.out.println("\n── Enums ──");
        check("CompartmentStatus FREE", CompartmentStatus.valueOf("FREE") == CompartmentStatus.FREE);
        check("CompartmentStatus OCCUPIED", CompartmentStatus.valueOf("OCCUPIED") == CompartmentStatus.OCCUPIED);
        check("CompartmentStatus 2 values", CompartmentStatus.values().length == 2);
        check("Size SMALL", Size.valueOf("SMALL") == Size.SMALL);
        check("Size MEDIUM", Size.valueOf("MEDIUM") == Size.MEDIUM);
        check("Size LARGE", Size.valueOf("LARGE") == Size.LARGE);
        check("Size 3 values", Size.values().length == 3);
        check("Size ordinal: SMALL < MEDIUM < LARGE",
                Size.SMALL.ordinal() < Size.MEDIUM.ordinal() && Size.MEDIUM.ordinal() < Size.LARGE.ordinal());
    }

    static void testAssignmentClass() {
        System.out.println("\n── Assignment Class ──");
        Assignment a = new Assignment("CODE123", "P1", "C1");
        check("code set", "CODE123".equals(a.code));
        check("parcelId set", "P1".equals(a.parcelId));
        check("compartmentId set", "C1".equals(a.compartmentId));
        check("isPickedUp default false", !a.isPickedUp);
        check("expiresAt in future", a.expiresAt.isAfter(LocalDateTime.now()));
        check("expiresAt ~30 days", a.expiresAt.isAfter(LocalDateTime.now().plusDays(29)));
        check("not expired", !a.isExpired());
        check("toString has code", a.toString().contains("CODE123"));
        check("toString has parcelId", a.toString().contains("P1"));

        // Force expired
        Assignment exp = new Assignment("X", "P2", "C2");
        exp.expiresAt = LocalDateTime.now().minusDays(1);
        check("forced expired returns true", exp.isExpired());
    }

    static void testCompartmentClass() {
        System.out.println("\n── Compartment Class ──");
        Compartment c1 = new Compartment(Size.SMALL);
        check("default status FREE", c1.status == CompartmentStatus.FREE);
        check("size SMALL", c1.size == Size.SMALL);
        check("id non-null 8 chars", c1.id != null && c1.id.length() == 8);
        check("unique ids", !c1.id.equals(new Compartment(Size.SMALL).id));

        Compartment c2 = new Compartment("myid", CompartmentStatus.OCCUPIED, Size.LARGE);
        check("custom id", "myid".equals(c2.id));
        check("custom status OCCUPIED", c2.status == CompartmentStatus.OCCUPIED);
        check("custom size LARGE", c2.size == Size.LARGE);
    }

    static void testParcelClass() {
        System.out.println("\n── Parcel Class ──");
        Parcel p = new Parcel(Size.MEDIUM, "Alice");
        check("size MEDIUM", p.size == Size.MEDIUM);
        check("recipientName", "Alice".equals(p.recipientName));
        check("id non-null 8 chars", p.id != null && p.id.length() == 8);
        check("unique ids", !p.id.equals(new Parcel(Size.MEDIUM, "Bob").id));
    }

    static void testLockerConstructor() {
        System.out.println("\n── Locker Constructor ──");
        Locker l = newLocker();
        check("id non-null", l.id != null && l.id.length() == 8);
        check("compartments set", l.compartments.size() == 5);
        check("parcelToCode empty", l.parcelToCode.isEmpty());
        check("codeToAssignment empty", l.codeToAssignment.isEmpty());
    }

    static void testAddParcelBasic() {
        System.out.println("\n── AddParcel Basic ──");
        try {
            Locker l = newLocker();
            Parcel p = new Parcel(Size.SMALL, "Alice");
            String code = l.addParcel(p);
            check("code not null", code != null);
            check("code starts with CODE", code.startsWith("CODE"));
            check("parcelToCode has entry", l.parcelToCode.containsKey(p.id));
            check("codeToAssignment has entry", l.codeToAssignment.containsKey(code));
            Assignment a = l.codeToAssignment.get(code);
            check("assignment parcelId matches", p.id.equals(a.parcelId));
            check("assignment code matches", code.equals(a.code));
        } catch (Exception e) { check("addParcel basic: " + e.getMessage(), false); }
    }

    static void testAddParcelSizeMatching() {
        System.out.println("\n── AddParcel Size Matching ──");
        try {
            Locker l = newLocker();

            // Small parcel gets small compartment
            Parcel small = new Parcel(Size.SMALL, "A");
            String code1 = l.addParcel(small);
            Assignment a1 = l.codeToAssignment.get(code1);
            Compartment c1 = l.findCompartmentById(a1.compartmentId);
            check("small parcel gets small compartment", c1.size == Size.SMALL);

            // Medium parcel gets medium compartment
            Parcel med = new Parcel(Size.MEDIUM, "B");
            String code2 = l.addParcel(med);
            Assignment a2 = l.codeToAssignment.get(code2);
            Compartment c2 = l.findCompartmentById(a2.compartmentId);
            check("medium parcel gets medium compartment", c2.size == Size.MEDIUM);

            // Large parcel gets large compartment
            Parcel large = new Parcel(Size.LARGE, "C");
            String code3 = l.addParcel(large);
            Assignment a3 = l.codeToAssignment.get(code3);
            Compartment c3 = l.findCompartmentById(a3.compartmentId);
            check("large parcel gets large compartment", c3.size == Size.LARGE);
        } catch (Exception e) { check("size matching: " + e.getMessage(), false); }
    }

    static void testAddParcelUpsize() {
        System.out.println("\n── AddParcel Upsize ──");
        try {
            // Only LARGE compartments available
            List<Compartment> comps = new ArrayList<>();
            comps.add(new Compartment(Size.LARGE));
            comps.add(new Compartment(Size.LARGE));
            Locker l = new Locker(comps);

            Parcel small = new Parcel(Size.SMALL, "A");
            String code = l.addParcel(small);
            Assignment a = l.codeToAssignment.get(code);
            Compartment c = l.findCompartmentById(a.compartmentId);
            check("small parcel upsized to LARGE when no SMALL/MEDIUM", c.size == Size.LARGE);

            // Only MEDIUM compartments available
            List<Compartment> comps2 = new ArrayList<>();
            comps2.add(new Compartment(Size.MEDIUM));
            Locker l2 = new Locker(comps2);
            Parcel small2 = new Parcel(Size.SMALL, "B");
            String code2 = l2.addParcel(small2);
            Assignment a2 = l2.codeToAssignment.get(code2);
            Compartment c2 = l2.findCompartmentById(a2.compartmentId);
            check("small parcel upsized to MEDIUM when no SMALL", c2.size == Size.MEDIUM);
        } catch (Exception e) { check("upsize: " + e.getMessage(), false); }
    }

    static void testAddParcelNoCompartmentThrows() {
        System.out.println("\n── AddParcel No Compartment ──");
        // All occupied
        try {
            Locker l = new Locker(new ArrayList<>());
            l.addParcel(new Parcel(Size.SMALL, "A"));
            check("no compartments throws", false);
        } catch (NoCompartmentException e) { check("no compartments throws", true); }

        // All compartments occupied
        try {
            Compartment c = new Compartment("c1", CompartmentStatus.OCCUPIED, Size.SMALL);
            Locker l = new Locker(new ArrayList<>(List.of(c)));
            l.addParcel(new Parcel(Size.SMALL, "A"));
            check("all occupied throws", false);
        } catch (NoCompartmentException e) { check("all occupied throws", true); }

        // Large parcel but only small compartments
        try {
            List<Compartment> comps = new ArrayList<>();
            comps.add(new Compartment(Size.SMALL));
            Locker l = new Locker(comps);
            l.addParcel(new Parcel(Size.LARGE, "A"));
            check("large in small throws", false);
        } catch (NoCompartmentException e) { check("large in small throws", true); }
    }

    static void testPickUpParcel() {
        System.out.println("\n── PickUp Parcel ──");
        try {
            Locker l = newLocker();
            Parcel p = new Parcel(Size.SMALL, "Alice");
            String code = l.addParcel(p);

            Assignment a = l.pickUpParcel(code);
            check("pickup returns assignment", a != null);
            check("assignment marked picked up", a.isPickedUp);
            check("parcelId matches", p.id.equals(a.parcelId));
            check("code matches", code.equals(a.code));
        } catch (Exception e) { check("pickup: " + e.getMessage(), false); }
    }

    static void testPickUpInvalidCodeThrows() {
        System.out.println("\n── PickUp Invalid Code ──");
        try {
            Locker l = newLocker();
            l.pickUpParcel("INVALID_CODE");
            check("invalid code throws", false);
        } catch (InvalidCodeException e) { check("invalid code throws", true); }
          catch (Exception e) { check("invalid code throws wrong exception", false); }
    }

    static void testPickUpAlreadyPickedUpThrows() {
        System.out.println("\n── PickUp Already Picked Up ──");
        try {
            Locker l = newLocker();
            String code = l.addParcel(new Parcel(Size.SMALL, "A"));
            l.pickUpParcel(code); // first pickup

            l.pickUpParcel(code); // second pickup
            check("already picked up throws", false);
        } catch (AlreadyPickedUpException e) { check("already picked up throws", true); }
          catch (Exception e) { check("already picked up wrong exception: " + e.getClass().getSimpleName(), false); }
    }

    static void testPickUpExpiredThrows() {
        System.out.println("\n── PickUp Expired ──");
        try {
            Locker l = newLocker();
            String code = l.addParcel(new Parcel(Size.SMALL, "A"));
            // Force expire
            l.codeToAssignment.get(code).expiresAt = LocalDateTime.now().minusDays(1);

            l.pickUpParcel(code);
            check("expired code throws", false);
        } catch (ExpiredCodeException e) { check("expired code throws", true); }
          catch (Exception e) { check("expired wrong exception: " + e.getClass().getSimpleName(), false); }
    }

    static void testCompartmentFreedAfterPickup() {
        System.out.println("\n── Compartment Freed After Pickup ──");
        try {
            Locker l = newLocker();
            Parcel p = new Parcel(Size.SMALL, "A");
            String code = l.addParcel(p);

            Assignment a = l.codeToAssignment.get(code);
            Compartment c = l.findCompartmentById(a.compartmentId);
            check("compartment OCCUPIED after add", c.status == CompartmentStatus.OCCUPIED);

            l.pickUpParcel(code);
            check("compartment FREE after pickup", c.status == CompartmentStatus.FREE);
        } catch (Exception e) { check("compartment freed: " + e.getMessage(), false); }
    }

    static void testGenerateCode() {
        System.out.println("\n── Generate Code ──");
        Locker l = newLocker();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20; i++) codes.add(l.generateCode());
        check("20 unique codes generated", codes.size() == 20);
        check("all codes start with CODE", codes.stream().allMatch(c -> c.startsWith("CODE")));
        check("all codes length >= 12", codes.stream().allMatch(c -> c.length() >= 12));
    }

    static void testFindFreeCompartment() {
        System.out.println("\n── FindFreeCompartment ──");
        Locker l = newLocker();
        Compartment found = l.findFreeCompartment(Size.SMALL);
        check("finds free SMALL", found != null && found.size == Size.SMALL);

        Compartment foundMed = l.findFreeCompartment(Size.MEDIUM);
        check("finds free MEDIUM", foundMed != null && foundMed.size == Size.MEDIUM);

        Compartment foundLarge = l.findFreeCompartment(Size.LARGE);
        check("finds free LARGE", foundLarge != null && foundLarge.size == Size.LARGE);

        // All occupied
        Locker l2 = new Locker(List.of(new Compartment("x", CompartmentStatus.OCCUPIED, Size.SMALL)));
        check("no free returns null", l2.findFreeCompartment(Size.SMALL) == null);
    }

    static void testFindLargerCompartment() {
        System.out.println("\n── FindLargerCompartment ──");
        // Only large available
        List<Compartment> comps = new ArrayList<>();
        comps.add(new Compartment("o1", CompartmentStatus.OCCUPIED, Size.SMALL));
        comps.add(new Compartment(Size.LARGE));
        Locker l = new Locker(comps);

        Compartment found = l.findLargerCompartment(Size.SMALL);
        check("finds larger for SMALL", found != null && found.size == Size.LARGE);

        // Medium and Large available, should pick Medium (smallest larger)
        List<Compartment> comps2 = new ArrayList<>();
        comps2.add(new Compartment(Size.MEDIUM));
        comps2.add(new Compartment(Size.LARGE));
        Locker l2 = new Locker(comps2);
        Compartment found2 = l2.findLargerCompartment(Size.SMALL);
        check("picks smallest larger (MEDIUM over LARGE)", found2 != null && found2.size == Size.MEDIUM);

        // No larger available for LARGE
        check("no larger for LARGE returns null", l.findLargerCompartment(Size.LARGE) == null);
    }

    static void testFindCompartmentById() {
        System.out.println("\n── FindCompartmentById ──");
        Compartment c = new Compartment("abc123", CompartmentStatus.FREE, Size.SMALL);
        Locker l = new Locker(new ArrayList<>(List.of(c)));
        check("finds by id", l.findCompartmentById("abc123") == c);
        check("not found returns null", l.findCompartmentById("nope") == null);
    }

    static void testCleanUp() {
        System.out.println("\n── CleanUp ──");
        try {
            Locker l = newLocker();
            String code1 = l.addParcel(new Parcel(Size.SMALL, "A"));
            String code2 = l.addParcel(new Parcel(Size.MEDIUM, "B"));

            // Nothing expired
            int cleaned = l.cleanUp();
            check("cleanUp 0 when nothing expired", cleaned == 0);

            // Expire one
            l.codeToAssignment.get(code1).expiresAt = LocalDateTime.now().minusDays(1);
            int cleaned2 = l.cleanUp();
            check("cleanUp 1 after expiring one", cleaned2 == 1);
            check("expired assignment removed from map", !l.codeToAssignment.containsKey(code1));

            // Compartment freed
            // The compartment that was used by code1 should now be FREE
            Assignment remaining = l.codeToAssignment.get(code2);
            check("non-expired assignment still exists", remaining != null);
        } catch (Exception e) { check("cleanup: " + e.getMessage(), false); }
    }

    static void testMultipleParcels() {
        System.out.println("\n── Multiple Parcels ──");
        try {
            Locker l = newLocker(); // 2S, 2M, 1L
            String c1 = l.addParcel(new Parcel(Size.SMALL, "A"));
            String c2 = l.addParcel(new Parcel(Size.SMALL, "B"));
            check("2 small parcels added", l.codeToAssignment.size() == 2);

            // 3rd small should upsize to MEDIUM
            String c3 = l.addParcel(new Parcel(Size.SMALL, "C"));
            Assignment a3 = l.codeToAssignment.get(c3);
            Compartment comp3 = l.findCompartmentById(a3.compartmentId);
            check("3rd small upsized to MEDIUM", comp3.size == Size.MEDIUM);

            // Add 2 more to fill remaining MEDIUM and LARGE
            l.addParcel(new Parcel(Size.MEDIUM, "D"));
            l.addParcel(new Parcel(Size.LARGE, "E"));
            check("5 parcels in locker", l.codeToAssignment.size() == 5);

            // 6th should fail
            try {
                l.addParcel(new Parcel(Size.SMALL, "F"));
                check("6th parcel throws when full", false);
            } catch (NoCompartmentException e) { check("6th parcel throws when full", true); }

            // All codes unique
            Set<String> codes = new HashSet<>(List.of(c1, c2, c3));
            check("all codes unique", codes.size() == 3);
        } catch (Exception e) { check("multiple parcels: " + e.getMessage(), false); }
    }

    static void testEndToEnd() {
        System.out.println("\n── End-to-End ──");
        try {
            Locker l = newLocker();

            // Add parcels
            Parcel p1 = new Parcel(Size.SMALL, "Alice");
            Parcel p2 = new Parcel(Size.MEDIUM, "Bob");
            Parcel p3 = new Parcel(Size.LARGE, "Charlie");
            String code1 = l.addParcel(p1);
            String code2 = l.addParcel(p2);
            String code3 = l.addParcel(p3);
            check("E2E: 3 parcels added", l.codeToAssignment.size() == 3);

            // Alice picks up
            Assignment a1 = l.pickUpParcel(code1);
            check("E2E: Alice picked up", a1.isPickedUp);
            check("E2E: compartment freed", l.findCompartmentById(a1.compartmentId).status == CompartmentStatus.FREE);

            // Now we can add another small parcel
            Parcel p4 = new Parcel(Size.SMALL, "Dave");
            String code4 = l.addParcel(p4);
            check("E2E: Dave's parcel added after Alice pickup", code4 != null);

            // Bob picks up
            Assignment a2 = l.pickUpParcel(code2);
            check("E2E: Bob picked up", a2.isPickedUp);

            // Try double pickup
            try {
                l.pickUpParcel(code2);
                check("E2E: double pickup throws", false);
            } catch (AlreadyPickedUpException e) { check("E2E: double pickup throws", true); }

            // Expire Charlie's code and try pickup
            l.codeToAssignment.get(code3).expiresAt = LocalDateTime.now().minusDays(1);
            try {
                l.pickUpParcel(code3);
                check("E2E: expired pickup throws", false);
            } catch (ExpiredCodeException e) { check("E2E: expired pickup throws", true); }

            // Cleanup expired
            int cleaned = l.cleanUp();
            check("E2E: cleanup removes 1 expired", cleaned == 1);

            // Dave picks up successfully
            Assignment a4 = l.pickUpParcel(code4);
            check("E2E: Dave picks up ok", a4.isPickedUp && p4.id.equals(a4.parcelId));
        } catch (Exception e) { check("E2E: " + e.getMessage(), false); }
    }
}
