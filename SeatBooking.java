import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SeatNotFoundException extends Exception{
    SeatNotFoundException(String message){
        super(message);
    }
}
class SeatAlreadyBookedException extends Exception{
    SeatAlreadyBookedException(String message){
        super(message);
    }
}
class InvalidBookingException extends Exception{
    InvalidBookingException(String message){
        super(message);
    }
}
enum SeatStatus {AVAILABLE,RESERVED,BOOKED}
enum PricingType {PREMIUM,STANDARD,WEEKEND}
interface PricingStrategy {
    double calculate(double basePrice);
    PricingType getPricingType();
}
class PremiumPricingStrategy implements PricingStrategy {
    @Override
    public double calculate(double basePrice) {
        return basePrice * 2;
    }
    @Override
    public PricingType getPricingType() {
        return PricingType.PREMIUM;
    }
}
class StandardPricingStrategy implements PricingStrategy {

    @Override
    public double calculate(double basePrice) {
        return basePrice;
    }

    @Override
    public PricingType getPricingType() {
        return PricingType.STANDARD;
    }
}
class WeekendPricingStrategy implements PricingStrategy {

    @Override
    public double calculate(double basePrice) {
        return basePrice*1.5;
    }

    @Override
    public PricingType getPricingType() {
        return PricingType.WEEKEND;
    }
}
class Seat{
    String seatId;
    SeatStatus seatStatus;
    String reservedBy;
    long reservationExpiry;
    double basePrice;

    Seat(double basePrice) {
        this.seatId="SEAT-"+ UUID.randomUUID().toString().substring(0,8);
        this.seatStatus = SeatStatus.AVAILABLE;
        this.basePrice = basePrice;
    }
    boolean isReservationExpired() {
        return (reservationExpiry < System.currentTimeMillis()) && (seatStatus == SeatStatus.RESERVED);
    }

    @Override
    public String toString() {
        return "Seat{" +
                "seatId='" + seatId + '\'' +
                ", seatStatus=" + seatStatus +
                ", reservedBy='" + reservedBy + '\'' +
                ", reservationExpiry=" + reservationExpiry +
                '}';
    }
}
class BookingSystem{
    ConcurrentHashMap<String,Seat> seatMap;
    ConcurrentHashMap<String, Lock> lockMap;
    ScheduledExecutorService scheduler;
    int timeoutSeconds=10*60;

    public BookingSystem() {
        this.seatMap = new ConcurrentHashMap<>();
        this.lockMap = new ConcurrentHashMap<>();
        scheduler= Executors.newScheduledThreadPool(2);
    }
    Seat addSeat(double basePrice) {
        Seat seat = new Seat(basePrice);
        seatMap.put(seat.seatId, seat);
        return seat;
    }
    boolean reserveSeat(String seatId,String userId,int timeout) throws InterruptedException, SeatAlreadyBookedException {
        Seat seat = seatMap.get(seatId);
        Lock lock = lockMap.computeIfAbsent(seat.seatId, k -> new ReentrantLock());
        if(!lock.tryLock(1, TimeUnit.SECONDS)) throw new SeatAlreadyBookedException("cannot acquire lock as seat already bpoked");
        try{
            if(seat.isReservationExpired()) seat.seatStatus = SeatStatus.AVAILABLE;
            if(seat.seatStatus!=SeatStatus.AVAILABLE) throw new SeatAlreadyBookedException("seat already booked");
            seat.seatStatus = SeatStatus.RESERVED;
            seat.reservedBy = userId;
            seat.reservationExpiry=System.currentTimeMillis()+timeout* 1000L;
            scheduleExpiration(seatId,timeoutSeconds);
            return true;
        }finally {
            lock.unlock();
        }
    }
    boolean confirmBooking(String seatId,String userId) throws InterruptedException, SeatNotFoundException, InvalidBookingException {
        Seat seat = seatMap.get(seatId);
        if(seat==null) throw new SeatNotFoundException(" seat not found");
        Lock lock = lockMap.computeIfAbsent(seat.seatId, k -> new ReentrantLock());
        if(!lock.tryLock(1,TimeUnit.SECONDS)) throw new InvalidBookingException("cant acquire lock");
        try{
            if(seat.seatStatus!=SeatStatus.RESERVED) throw new InvalidBookingException("seat not reserved");
            if(seat.isReservationExpired()){
                seat.seatStatus = SeatStatus.AVAILABLE;
                seat.reservedBy = null;
                throw new InvalidBookingException("seat reservation expired");
            }
            if(!Objects.equals(seat.reservedBy, userId)) throw new InvalidBookingException("seat not reserved by "+userId);
            seat.seatStatus = SeatStatus.BOOKED;
            return true;
        }finally {
            lock.unlock();
        }
    }
    boolean cancelSeat(String seatId) throws SeatNotFoundException, InterruptedException, InvalidBookingException {
        Seat seat = seatMap.get(seatId);
        if(seat==null) throw new SeatNotFoundException("seat not found");
        Lock lock = lockMap.computeIfAbsent(seat.seatId, k -> new ReentrantLock());
        if(!lock.tryLock(1,TimeUnit.SECONDS)) throw new InvalidBookingException("cant acquire lock");
        try{
            if(seat.seatStatus==SeatStatus.RESERVED || seat.seatStatus==SeatStatus.BOOKED){
                seat.seatStatus = SeatStatus.AVAILABLE;
                seat.reservedBy = null;
                return true;
            }
        }finally {
            lock.unlock();
        }
        return true;
    }
    void scheduleExpiration(String seatId,int delaySeconds){
        scheduler.schedule(()->{
            Seat seat = seatMap.get(seatId);
            Lock lock = lockMap.get(seat.seatId);
            if(lock != null){
                lock.lock();
                try {
                    if(seat.isReservationExpired()){
                        seat.seatStatus = SeatStatus.AVAILABLE;
                        seat.reservedBy = null;
                        System.out.println("AUTO EXPIRED "+seatId);
                    }
                }finally {
                    lock.unlock();
                }

            }
        }, delaySeconds, TimeUnit.SECONDS);
    }
    List<String> getAvailableSeats(){
        return seatMap.values().stream().filter(x->x.seatStatus==SeatStatus.AVAILABLE).map(x->x.seatId).toList();
    }
    void shutdown(){
        scheduler.shutdown();
    }
}

public class SeatBooking {
    static int passed = 0, failed = 0;
    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      SeatBooking Test Suite              ║");
        System.out.println("╚══════════════════════════════════════════╝");

        testExceptionClasses();
        testEnums();
        testPricingStrategies();
        testSeatClass();
        testSeatIsReservationExpired();
        testBookingSystemConstructor();
        testAddSeat();
        testReserveSeat();
        testReserveAlreadyReservedThrows();
        testConfirmBooking();
        testConfirmNotReservedThrows();
        testConfirmWrongUserThrows();
        testConfirmExpiredThrows();
        testConfirmNotFoundThrows();
        testCancelReservedSeat();
        testCancelBookedSeat();
        testCancelAvailableSeat();
        testCancelNotFoundThrows();
        testGetAvailableSeats();
        testReserveAfterCancel();
        testMultipleSeats();
        testEndToEnd();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");

        System.exit(failed > 0 ? 1 : 0);
    }

    static void testExceptionClasses() {
        System.out.println("\n── Exceptions ──");
        check("SeatNotFoundException", "m".equals(new SeatNotFoundException("m").getMessage()));
        check("SeatAlreadyBookedException", "m".equals(new SeatAlreadyBookedException("m").getMessage()));
        check("InvalidBookingException", "m".equals(new InvalidBookingException("m").getMessage()));
    }

    static void testEnums() {
        System.out.println("\n── Enums ──");
        check("SeatStatus AVAILABLE", SeatStatus.valueOf("AVAILABLE") == SeatStatus.AVAILABLE);
        check("SeatStatus RESERVED", SeatStatus.valueOf("RESERVED") == SeatStatus.RESERVED);
        check("SeatStatus BOOKED", SeatStatus.valueOf("BOOKED") == SeatStatus.BOOKED);
        check("SeatStatus 3 values", SeatStatus.values().length == 3);
        check("PricingType PREMIUM", PricingType.valueOf("PREMIUM") == PricingType.PREMIUM);
        check("PricingType STANDARD", PricingType.valueOf("STANDARD") == PricingType.STANDARD);
        check("PricingType WEEKEND", PricingType.valueOf("WEEKEND") == PricingType.WEEKEND);
        check("PricingType 3 values", PricingType.values().length == 3);
    }

    static void testPricingStrategies() {
        System.out.println("\n── PricingStrategies ──");
        PremiumPricingStrategy premium = new PremiumPricingStrategy();
        check("premium 100 -> 200", Math.abs(premium.calculate(100.0) - 200.0) < 0.01);
        check("premium 0 -> 0", Math.abs(premium.calculate(0.0)) < 0.01);
        check("premium 50.5 -> 101", Math.abs(premium.calculate(50.5) - 101.0) < 0.01);
        check("premium type PREMIUM", premium.getPricingType() == PricingType.PREMIUM);

        StandardPricingStrategy standard = new StandardPricingStrategy();
        check("standard 100 -> 100", Math.abs(standard.calculate(100.0) - 100.0) < 0.01);
        check("standard 0 -> 0", Math.abs(standard.calculate(0.0)) < 0.01);
        check("standard type STANDARD", standard.getPricingType() == PricingType.STANDARD);

        WeekendPricingStrategy weekend = new WeekendPricingStrategy();
        check("weekend 100 -> 150", Math.abs(weekend.calculate(100.0) - 150.0) < 0.01);
        check("weekend 200 -> 300", Math.abs(weekend.calculate(200.0) - 300.0) < 0.01);
        check("weekend 0 -> 0", Math.abs(weekend.calculate(0.0)) < 0.01);
        check("weekend type WEEKEND", weekend.getPricingType() == PricingType.WEEKEND);
    }

    static void testSeatClass() {
        System.out.println("\n── Seat Class ──");
        Seat s = new Seat(100.0);
        check("seatId starts with SEAT-", s.seatId.startsWith("SEAT-"));
        check("seatId length", s.seatId.length() == 13); // "SEAT-" + 8 chars
        check("default AVAILABLE", s.seatStatus == SeatStatus.AVAILABLE);
        check("basePrice set", s.basePrice == 100.0);
        check("reservedBy null", s.reservedBy == null);
        check("reservationExpiry 0", s.reservationExpiry == 0);
        check("toString has seatId", s.toString().contains(s.seatId));
        check("toString has status", s.toString().contains("AVAILABLE"));
        check("unique ids", !s.seatId.equals(new Seat(100.0).seatId));
    }

    static void testSeatIsReservationExpired() {
        System.out.println("\n── Seat isReservationExpired ──");
        Seat s1 = new Seat(50.0);
        check("AVAILABLE with 0 expiry -> not expired", !s1.isReservationExpired());

        Seat s2 = new Seat(50.0);
        s2.seatStatus = SeatStatus.RESERVED;
        s2.reservationExpiry = System.currentTimeMillis() + 60000;
        check("RESERVED with future expiry -> not expired", !s2.isReservationExpired());

        Seat s3 = new Seat(50.0);
        s3.seatStatus = SeatStatus.RESERVED;
        s3.reservationExpiry = System.currentTimeMillis() - 1000;
        check("RESERVED with past expiry -> expired", s3.isReservationExpired());

        Seat s4 = new Seat(50.0);
        s4.seatStatus = SeatStatus.BOOKED;
        s4.reservationExpiry = System.currentTimeMillis() - 1000;
        check("BOOKED with past expiry -> not expired (only RESERVED)", !s4.isReservationExpired());

        Seat s5 = new Seat(50.0);
        s5.seatStatus = SeatStatus.AVAILABLE;
        s5.reservationExpiry = System.currentTimeMillis() - 1000;
        check("AVAILABLE with past expiry -> not expired", !s5.isReservationExpired());
    }

    static void testBookingSystemConstructor() {
        System.out.println("\n── BookingSystem Constructor ──");
        BookingSystem bs = new BookingSystem();
        check("seatMap initialized", bs.seatMap != null && bs.seatMap.isEmpty());
        check("lockMap initialized", bs.lockMap != null && bs.lockMap.isEmpty());
        check("scheduler initialized", bs.scheduler != null);
        check("timeoutSeconds default 600", bs.timeoutSeconds == 600);
        bs.shutdown();
    }

    static void testAddSeat() {
        System.out.println("\n── AddSeat ──");
        BookingSystem bs = new BookingSystem();
        Seat s1 = bs.addSeat(100.0);
        check("addSeat returns seat", s1 != null);
        check("seat in seatMap", bs.seatMap.containsKey(s1.seatId));
        check("basePrice 100", s1.basePrice == 100.0);
        check("status AVAILABLE", s1.seatStatus == SeatStatus.AVAILABLE);

        Seat s2 = bs.addSeat(200.0);
        check("seatMap size 2", bs.seatMap.size() == 2);
        check("different ids", !s1.seatId.equals(s2.seatId));

        Seat s3 = bs.addSeat(0.0);
        check("zero price seat", s3.basePrice == 0.0);
        bs.shutdown();
    }

    static void testReserveSeat() {
        System.out.println("\n── ReserveSeat ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            boolean result = bs.reserveSeat(s.seatId, "user1", 300);
            check("reserveSeat returns true", result);
            check("status RESERVED", s.seatStatus == SeatStatus.RESERVED);
            check("reservedBy user1", "user1".equals(s.reservedBy));
            check("reservationExpiry in future", s.reservationExpiry > System.currentTimeMillis());
        } catch (Exception e) { check("reserveSeat: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testReserveAlreadyReservedThrows() {
        System.out.println("\n── Reserve Already Reserved ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.reserveSeat(s.seatId, "user1", 300);
            try {
                bs.reserveSeat(s.seatId, "user2", 300);
                check("double reserve throws", false);
            } catch (SeatAlreadyBookedException e) { check("double reserve throws", true); }
        } catch (Exception e) { check("reserve setup: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testConfirmBooking() {
        System.out.println("\n── ConfirmBooking ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.reserveSeat(s.seatId, "user1", 300);
            boolean result = bs.confirmBooking(s.seatId, "user1");
            check("confirmBooking returns true", result);
            check("status BOOKED", s.seatStatus == SeatStatus.BOOKED);
        } catch (Exception e) { check("confirmBooking: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testConfirmNotReservedThrows() {
        System.out.println("\n── Confirm Not Reserved ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.confirmBooking(s.seatId, "user1");
            check("confirm AVAILABLE throws", false);
        } catch (InvalidBookingException e) { check("confirm AVAILABLE throws", true); }
          catch (Exception e) { check("confirm not reserved: " + e.getClass().getSimpleName(), false); }
        bs.shutdown();
    }

    static void testConfirmWrongUserThrows() {
        System.out.println("\n── Confirm Wrong User ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.reserveSeat(s.seatId, "user1", 300);
            bs.confirmBooking(s.seatId, "user2");
            check("confirm wrong user throws", false);
        } catch (InvalidBookingException e) { check("confirm wrong user throws", true); }
          catch (Exception e) { check("wrong user: " + e.getClass().getSimpleName(), false); }
        bs.shutdown();
    }

    static void testConfirmExpiredThrows() {
        System.out.println("\n── Confirm Expired ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.reserveSeat(s.seatId, "user1", 300);
            // Force expire
            s.reservationExpiry = System.currentTimeMillis() - 1000;
            bs.confirmBooking(s.seatId, "user1");
            check("confirm expired throws", false);
        } catch (InvalidBookingException e) {
            check("confirm expired throws", true);
            // After expired confirm, seat should be AVAILABLE
            Seat s = bs.seatMap.values().iterator().next();
            check("expired seat reset to AVAILABLE", s.seatStatus == SeatStatus.AVAILABLE);
            check("expired seat reservedBy null", s.reservedBy == null);
        } catch (Exception e) { check("expired: " + e.getClass().getSimpleName(), false); }
        bs.shutdown();
    }

    static void testConfirmNotFoundThrows() {
        System.out.println("\n── Confirm Not Found ──");
        BookingSystem bs = new BookingSystem();
        try {
            bs.confirmBooking("SEAT-nonexist", "user1");
            check("confirm non-existent throws", false);
        } catch (SeatNotFoundException e) { check("confirm non-existent throws", true); }
          catch (Exception e) { check("not found: " + e.getClass().getSimpleName(), false); }
        bs.shutdown();
    }

    static void testCancelReservedSeat() {
        System.out.println("\n── Cancel Reserved Seat ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.reserveSeat(s.seatId, "user1", 300);
            check("before cancel: RESERVED", s.seatStatus == SeatStatus.RESERVED);
            boolean result = bs.cancelSeat(s.seatId);
            check("cancelSeat returns true", result);
            check("after cancel: AVAILABLE", s.seatStatus == SeatStatus.AVAILABLE);
            check("after cancel: reservedBy null", s.reservedBy == null);
        } catch (Exception e) { check("cancel reserved: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testCancelBookedSeat() {
        System.out.println("\n── Cancel Booked Seat ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.reserveSeat(s.seatId, "user1", 300);
            bs.confirmBooking(s.seatId, "user1");
            check("before cancel: BOOKED", s.seatStatus == SeatStatus.BOOKED);
            bs.cancelSeat(s.seatId);
            check("after cancel BOOKED: AVAILABLE", s.seatStatus == SeatStatus.AVAILABLE);
        } catch (Exception e) { check("cancel booked: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testCancelAvailableSeat() {
        System.out.println("\n── Cancel Available Seat ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            boolean result = bs.cancelSeat(s.seatId);
            check("cancel AVAILABLE returns true (no-op)", result);
            check("still AVAILABLE", s.seatStatus == SeatStatus.AVAILABLE);
        } catch (Exception e) { check("cancel available: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testCancelNotFoundThrows() {
        System.out.println("\n── Cancel Not Found ──");
        BookingSystem bs = new BookingSystem();
        try {
            bs.cancelSeat("SEAT-nonexist");
            check("cancel non-existent throws", false);
        } catch (SeatNotFoundException e) { check("cancel non-existent throws", true); }
          catch (Exception e) { check("cancel not found: " + e.getClass().getSimpleName(), false); }
        bs.shutdown();
    }

    static void testGetAvailableSeats() {
        System.out.println("\n── GetAvailableSeats ──");
        BookingSystem bs = new BookingSystem();
        try {
            check("empty system has 0 available", bs.getAvailableSeats().isEmpty());

            Seat s1 = bs.addSeat(100.0);
            Seat s2 = bs.addSeat(200.0);
            Seat s3 = bs.addSeat(150.0);
            check("3 seats all available", bs.getAvailableSeats().size() == 3);

            bs.reserveSeat(s1.seatId, "user1", 300);
            check("after reserve 1, 2 available", bs.getAvailableSeats().size() == 2);
            check("reserved seat not in available", !bs.getAvailableSeats().contains(s1.seatId));

            bs.reserveSeat(s2.seatId, "user2", 300);
            bs.confirmBooking(s2.seatId, "user2");
            check("after book 1 + reserve 1, 1 available", bs.getAvailableSeats().size() == 1);
            check("available seat is s3", bs.getAvailableSeats().contains(s3.seatId));
        } catch (Exception e) { check("getAvailableSeats: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testReserveAfterCancel() {
        System.out.println("\n── Reserve After Cancel ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s = bs.addSeat(100.0);
            bs.reserveSeat(s.seatId, "user1", 300);
            bs.cancelSeat(s.seatId);
            check("after cancel: AVAILABLE", s.seatStatus == SeatStatus.AVAILABLE);

            // Now another user can reserve
            boolean result = bs.reserveSeat(s.seatId, "user2", 300);
            check("re-reserve after cancel works", result);
            check("now reserved by user2", "user2".equals(s.reservedBy));
        } catch (Exception e) { check("reserve after cancel: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testMultipleSeats() {
        System.out.println("\n── Multiple Seats ──");
        BookingSystem bs = new BookingSystem();
        try {
            Seat s1 = bs.addSeat(100.0);
            Seat s2 = bs.addSeat(200.0);
            Seat s3 = bs.addSeat(300.0);

            bs.reserveSeat(s1.seatId, "alice", 300);
            bs.reserveSeat(s2.seatId, "bob", 300);
            bs.reserveSeat(s3.seatId, "charlie", 300);

            check("all 3 reserved", s1.seatStatus == SeatStatus.RESERVED
                    && s2.seatStatus == SeatStatus.RESERVED
                    && s3.seatStatus == SeatStatus.RESERVED);

            bs.confirmBooking(s1.seatId, "alice");
            bs.confirmBooking(s2.seatId, "bob");
            check("s1 BOOKED, s2 BOOKED, s3 RESERVED",
                    s1.seatStatus == SeatStatus.BOOKED
                    && s2.seatStatus == SeatStatus.BOOKED
                    && s3.seatStatus == SeatStatus.RESERVED);

            check("0 available", bs.getAvailableSeats().size() == 0);

            bs.cancelSeat(s2.seatId);
            check("after cancel s2, 1 available", bs.getAvailableSeats().size() == 1);
        } catch (Exception e) { check("multiple seats: " + e.getMessage(), false); }
        bs.shutdown();
    }

    static void testEndToEnd() {
        System.out.println("\n── End-to-End ──");
        BookingSystem bs = new BookingSystem();
        try {
            // Setup: add 3 seats
            Seat s1 = bs.addSeat(100.0);
            Seat s2 = bs.addSeat(150.0);
            Seat s3 = bs.addSeat(200.0);
            check("E2E: 3 available initially", bs.getAvailableSeats().size() == 3);

            // User1 reserves s1
            bs.reserveSeat(s1.seatId, "user1", 300);
            check("E2E: user1 reserved s1", s1.seatStatus == SeatStatus.RESERVED);

            // User2 tries to reserve s1 -> fails
            try {
                bs.reserveSeat(s1.seatId, "user2", 300);
                check("E2E: user2 cant reserve s1", false);
            } catch (SeatAlreadyBookedException e) { check("E2E: user2 cant reserve s1", true); }

            // User2 reserves s2 instead
            bs.reserveSeat(s2.seatId, "user2", 300);
            check("E2E: user2 reserved s2", s2.seatStatus == SeatStatus.RESERVED);

            // User1 confirms s1
            bs.confirmBooking(s1.seatId, "user1");
            check("E2E: s1 BOOKED", s1.seatStatus == SeatStatus.BOOKED);

            // User2 cancels s2
            bs.cancelSeat(s2.seatId);
            check("E2E: s2 back to AVAILABLE", s2.seatStatus == SeatStatus.AVAILABLE);

            // Check available: s2 and s3
            List<String> avail = bs.getAvailableSeats();
            check("E2E: 2 available (s2, s3)", avail.size() == 2);
            check("E2E: s2 available", avail.contains(s2.seatId));
            check("E2E: s3 available", avail.contains(s3.seatId));

            // User3 reserves s3, then it expires
            bs.reserveSeat(s3.seatId, "user3", 300);
            s3.reservationExpiry = System.currentTimeMillis() - 1000; // force expire
            try {
                bs.confirmBooking(s3.seatId, "user3");
                check("E2E: expired confirm throws", false);
            } catch (InvalidBookingException e) { check("E2E: expired confirm throws", true); }

            // s3 should be AVAILABLE again after expired confirm
            check("E2E: s3 AVAILABLE after expired", s3.seatStatus == SeatStatus.AVAILABLE);

            // Pricing check
            PremiumPricingStrategy pp = new PremiumPricingStrategy();
            StandardPricingStrategy sp = new StandardPricingStrategy();
            WeekendPricingStrategy wp = new WeekendPricingStrategy();
            check("E2E: premium price s1", Math.abs(pp.calculate(s1.basePrice) - 200.0) < 0.01);
            check("E2E: standard price s2", Math.abs(sp.calculate(s2.basePrice) - 150.0) < 0.01);
            check("E2E: weekend price s3", Math.abs(wp.calculate(s3.basePrice) - 300.0) < 0.01);

        } catch (Exception e) { check("E2E: " + e.getMessage(), false); }
        bs.shutdown();
    }
}
