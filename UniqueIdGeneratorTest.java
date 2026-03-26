import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class InvalidConfigurationException1 extends Exception{
    InvalidConfigurationException1(String message){
        super(message);
    }
}
class IDGeneratorException extends Exception{
    IDGeneratorException(String message){
        super(message);
    }
}
enum IDGeneratorType{
    SNOWFLAKE_GENERATOR,AUTO_INCREMENT,UUID_GENERATOR
}
interface IDGenerator{
    long nextId() throws IDGeneratorException;
    String nextIdString() throws IDGeneratorException;
    IDGeneratorType getType();
}

// 41 bits Timestamp + 5 bits datacenter id + 5 bits machine Id + 12 bits sequence id
class SnowflakeIDGenerator implements IDGenerator{
    long EPOCH=1577836800000L;
    int DATACENTER_BITS=5;
    int MACHINE_BITS=5;
    int SEQUENCE_BITS=12;
    int datacenterID;
    int machineID;
    int sequence;
    long previousTimestamp;
    int MAX_DATACENTER_ID=(1<<DATACENTER_BITS)-1;
    int MAX_MACHINE_ID=(1<<DATACENTER_BITS)-1;
    int MAX_SEQUENCE=(1<<SEQUENCE_BITS)-1;
    int MACHINE_SHIFTS=SEQUENCE_BITS;
    int DATACENTER_SHIFTS=MACHINE_SHIFTS+SEQUENCE_BITS;
    int TIMESTAMP_SHIFTS=DATACENTER_SHIFTS+MACHINE_SHIFTS+ SEQUENCE_BITS;
    SnowflakeIDGenerator(int datacenterID,int machineID) throws InvalidConfigurationException1 {
        if(datacenterID<0 || datacenterID>MAX_DATACENTER_ID) throw new InvalidConfigurationException1("datacenterID is not in range");
        if(machineID<0 || machineID>MAX_MACHINE_ID) throw new InvalidConfigurationException1("machineID is not in range");
        this.datacenterID=datacenterID;
        this.machineID=machineID;
        this.sequence=0;
    }
    @Override
    public long nextId() throws IDGeneratorException {
        long now=System.currentTimeMillis()-EPOCH;
        if(now<previousTimestamp) throw new IDGeneratorException("clock moved backward");
        if(now==previousTimestamp){
            sequence=(sequence+1)&MAX_SEQUENCE;
            if(sequence==0){
                while (now<=previousTimestamp){
                    now=System.currentTimeMillis()-EPOCH;
                }
            }
        }else {
            sequence=0;
        }
        previousTimestamp=now;
        return (now<<TIMESTAMP_SHIFTS)|((long) datacenterID <<DATACENTER_SHIFTS)|((long) machineID <<MACHINE_SHIFTS)|(sequence);
    }

    @Override
    public String nextIdString() throws IDGeneratorException {
        return String.valueOf(nextId());
    }

    @Override
    public IDGeneratorType getType() {
        return IDGeneratorType.SNOWFLAKE_GENERATOR;
    }
}
class AutoIncrementGenerator implements IDGenerator{
    AtomicLong counter;
    String prefix;
    AutoIncrementGenerator(String prefix,long startFrom){
        counter=new AtomicLong(startFrom);
        this.prefix=prefix;
    }
    @Override
    public long nextId() throws IDGeneratorException {
        return counter.incrementAndGet();
    }

    @Override
    public String nextIdString() throws IDGeneratorException {
        return prefix+counter.incrementAndGet();
    }

    @Override
    public IDGeneratorType getType() {
        return IDGeneratorType.AUTO_INCREMENT;
    }
}
class UUIDGenerator implements IDGenerator{

    @Override
    public long nextId() throws IDGeneratorException {
        return UUID.randomUUID().getMostSignificantBits()&Long.MAX_VALUE;
    }

    @Override
    public String nextIdString() throws IDGeneratorException {
        return UUID.randomUUID().toString();
    }

    @Override
    public IDGeneratorType getType() {
        return IDGeneratorType.UUID_GENERATOR;
    }
}
class IDGeneratorService{
    Map<String,IDGenerator> generatorMap;
    IDGeneratorService(){
        this.generatorMap=new HashMap<>();
    }
    void register(String name,IDGenerator generator){
        generatorMap.put(name,generator);
    }
    long generateId(String name) throws IDGeneratorException {
        return generatorMap.get(name).nextId();
    }
    String generateIdString(String name) throws IDGeneratorException {
        return generatorMap.get(name).nextIdString();
    }
}
public class UniqueIdGeneratorTest {

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

    public static void main(String[] args) throws Exception {

        System.out.println("==========================================");
        System.out.println(" UniqueIdGenerator  —  Test Suite");
        System.out.println("==========================================\n");

        // ── 1. Snowflake ID Generator ──
        System.out.println("── Snowflake ID Generator ──");
        {
            SnowflakeIDGenerator sf = new SnowflakeIDGenerator(1, 1);
            check("getType() == SNOWFLAKE_GENERATOR", sf.getType() == IDGeneratorType.SNOWFLAKE_GENERATOR);

            long id1 = sf.nextId();
            long id2 = sf.nextId();
            long id3 = sf.nextId();
            check("nextId() returns positive value", id1 > 0);
            check("IDs are unique (id1 != id2)", id1 != id2);
            check("IDs are unique (id2 != id3)", id2 != id3);
            check("IDs are monotonically increasing", id1 < id2 && id2 < id3);

            String idStr = sf.nextIdString();
            check("nextIdString() returns non-empty string", idStr != null && !idStr.isEmpty());
            check("nextIdString() is parseable as long", Long.parseLong(idStr) > 0);
        }
        System.out.println();

        // ── 2. Snowflake — invalid config ──
        System.out.println("── Snowflake — Invalid Configuration ──");
        {
            boolean threwForBadDC = false;
            try { new SnowflakeIDGenerator(-1, 0); }
            catch (InvalidConfigurationException1 e) { threwForBadDC = true; }
            check("Throws for negative datacenterID", threwForBadDC);

            boolean threwForBadMachine = false;
            try { new SnowflakeIDGenerator(0, 99); }
            catch (InvalidConfigurationException1 e) { threwForBadMachine = true; }
            check("Throws for out-of-range machineID", threwForBadMachine);

            boolean validEdge = false;
            try { new SnowflakeIDGenerator(31, 31); validEdge = true; }
            catch (InvalidConfigurationException1 e) { /* should not throw */ }
            check("Allows max valid datacenterID=31 machineID=31", validEdge);
        }
        System.out.println();

        // ── 3. Snowflake — uniqueness under rapid generation ──
        System.out.println("── Snowflake — Rapid Uniqueness (1000 IDs) ──");
        {
            SnowflakeIDGenerator sf = new SnowflakeIDGenerator(0, 0);
            java.util.Set<Long> ids = new java.util.HashSet<>();
            boolean allUnique = true;
            for (int i = 0; i < 1000; i++) {
                if (!ids.add(sf.nextId())) { allUnique = false; break; }
            }
            check("1000 rapidly generated IDs are all unique", allUnique);
        }
        System.out.println();

        // ── 4. Auto Increment Generator ──
        System.out.println("── Auto Increment Generator ──");
        {
            AutoIncrementGenerator ai = new AutoIncrementGenerator("ORD-", 100);
            check("getType() == AUTO_INCREMENT", ai.getType() == IDGeneratorType.AUTO_INCREMENT);

            long n1 = ai.nextId();
            long n2 = ai.nextId();
            check("nextId() starts from startFrom+1 (101)", n1 == 101);
            check("nextId() increments (102)", n2 == 102);

            String s1 = ai.nextIdString();
            check("nextIdString() has correct prefix", s1.startsWith("ORD-"));
            check("nextIdString() contains incremented number", s1.equals("ORD-103"));

            String s2 = ai.nextIdString();
            check("Subsequent nextIdString() increments", s2.equals("ORD-104"));
        }
        System.out.println();

        // ── 5. UUID Generator ──
        System.out.println("── UUID Generator ──");
        {
            UUIDGenerator ug = new UUIDGenerator();
            check("getType() == UUID_GENERATOR", ug.getType() == IDGeneratorType.UUID_GENERATOR);

            long uid1 = ug.nextId();
            long uid2 = ug.nextId();
            check("nextId() returns positive value", uid1 > 0);
            check("nextId() IDs are unique", uid1 != uid2);

            String us1 = ug.nextIdString();
            String us2 = ug.nextIdString();
            check("nextIdString() returns UUID format (contains dashes)", us1.contains("-"));
            check("nextIdString() length is 36", us1.length() == 36);
            check("nextIdString() IDs are unique", !us1.equals(us2));
        }
        System.out.println();

        // ── 6. IDGeneratorService — registration & generation ──
        System.out.println("── IDGeneratorService — Registration & Generation ──");
        {
            IDGeneratorService service = new IDGeneratorService();
            service.register("snowflake", new SnowflakeIDGenerator(2, 3));
            service.register("auto", new AutoIncrementGenerator("USR-", 0));
            service.register("uuid", new UUIDGenerator());

            long sfId = service.generateId("snowflake");
            check("Service generates snowflake ID > 0", sfId > 0);

            long autoId = service.generateId("auto");
            check("Service generates auto-increment ID (1)", autoId == 1);

            String uuidStr = service.generateIdString("uuid");
            check("Service generates UUID string", uuidStr != null && uuidStr.length() == 36);

            String autoStr = service.generateIdString("auto");
            check("Service generates auto-increment string with prefix", autoStr.equals("USR-2"));
        }
        System.out.println();

        // ── 7. IDGeneratorService — multiple generators are independent ──
        System.out.println("── IDGeneratorService — Independent Generators ──");
        {
            IDGeneratorService service = new IDGeneratorService();
            service.register("orders", new AutoIncrementGenerator("ORD-", 1000));
            service.register("users", new AutoIncrementGenerator("USR-", 5000));

            long o1 = service.generateId("orders");
            long o2 = service.generateId("orders");
            long u1 = service.generateId("users");
            check("Orders start at 1001", o1 == 1001);
            check("Orders increment to 1002", o2 == 1002);
            check("Users start at 5001 (independent)", u1 == 5001);
        }
        System.out.println();

        // ── Summary ──
        System.out.println("==========================================");
        System.out.printf(" Results:  %d passed,  %d failed%n", passed, failed);
        System.out.println("==========================================");
        if (failed > 0) System.exit(1);
    }
}
