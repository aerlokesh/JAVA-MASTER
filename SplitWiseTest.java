import java.util.*;

class UserNotFoundException extends Exception {
    UserNotFoundException(String message) {
        super(message);
    }
}
class UserAlreadyExistsException extends Exception {
    UserAlreadyExistsException(String message) {
        super(message);
    }
}
class InvalidExpenseException extends Exception {
    InvalidExpenseException(String message) {
        super(message);
    }
}
class ExpenseNotFoundException extends Exception {
    ExpenseNotFoundException(String message) {
        super(message);
    }
}
class InvalidSettlementException extends Exception {
    InvalidSettlementException(String message) {
        super(message);
    }
}
enum SplitType{EQUAL,PERCENTAGE,EXACT}
class User{
    String userId;
    String userName;
    public User(String userName) {
        userId= UUID.randomUUID().toString().substring(0, 8);
        this.userName = userName;
    }
    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                '}';
    }
}
class Expense{
    String expenseId;
    String description;
    double amount;
    Map<String,Double> splits;
    SplitType splitType;

    public Expense(SplitType splitType, Map<String, Double> splits, double amount, String description) {
        this.splitType = splitType;
        this.splits = splits;
        this.amount = amount;
        this.description = description;
        this.expenseId = "EXPENSE-" +UUID.randomUUID().toString().substring(0, 8);
    }
}
interface SplitStrategy{
    Map<String,Double> calculate(double totalAmount, List<String> participants,Map<String,Double> splitDetails) throws InvalidExpenseException;
    SplitType getType();
}
class EqualSplitStrategy implements SplitStrategy{
    @Override
    public Map<String, Double> calculate(double totalAmount, List<String> participants, Map<String, Double> splitDetails) throws InvalidExpenseException {
        if(participants==null || participants.isEmpty()) throw new InvalidExpenseException("participants is empty");
        Map<String,Double> splits=new HashMap<>();
        for(String participant:participants){
            splits.put(participant,totalAmount/participants.size());
        }
        return splits;
    }
    @Override
    public SplitType getType() {
        return SplitType.EQUAL;
    }
}
class ExactSplitStrategy implements SplitStrategy{
    @Override
    public Map<String, Double> calculate(double totalAmount, List<String> participants, Map<String, Double> splitDetails) throws InvalidExpenseException {
        if(splitDetails==null || splitDetails.isEmpty()) throw new InvalidExpenseException("splitDetails is empty");
        Map<String,Double> splits=new HashMap<>();
        double sum=splitDetails.values().stream().mapToDouble(Double::doubleValue).sum();
        if(Math.abs(sum-totalAmount)>0.01) throw new InvalidExpenseException("sum does not equal total amount");
        return new HashMap<>(splitDetails);
    }

    @Override
    public SplitType getType() {
        return SplitType.EXACT;
    }
}
class PercentSplitStrategy implements SplitStrategy{
    @Override
    public Map<String, Double> calculate(double totalAmount, List<String> participants, Map<String, Double> splitDetails) throws InvalidExpenseException {
        if(splitDetails==null || splitDetails.isEmpty()) throw new InvalidExpenseException("splitDetails is empty");
        double percentageSum=splitDetails.values().stream().mapToDouble(Double::doubleValue).sum();
        if(Math.abs(percentageSum-100.0)>0.01) throw new InvalidExpenseException("percentage sum does not equal total amount");
        Map<String,Double> splits=new HashMap<>();
        for(Map.Entry<String,Double> entry:splitDetails.entrySet()){
            splits.put(entry.getKey(),percentageSum*entry.getValue()/100.0);
        }
        return splits;
    }

    @Override
    public SplitType getType() {
        return null;
    }
}
class SplitWiseSystem{
    Map<String,User> usersMap;
    Map<String,Expense> expenseMap;
    Map<String,Map<String,Double>> balances;
    public SplitWiseSystem() {
        this.usersMap=new HashMap<>();
        this.expenseMap=new HashMap<>();
        this.balances=new HashMap<>();
    }
    boolean addUser(String name) throws UserAlreadyExistsException {
        User user=new User(name);
        if(usersMap.containsKey(user.userId)) throw new UserAlreadyExistsException("User already exists"+user.userId);
        usersMap.put(user.userId,user);
        balances.put(user.userId,new HashMap<>());
        return true;
    }
    Expense addExpense(String description,double totalAmount,String paidBy,List<String> participants,Map<String,Double> splits,SplitStrategy splitStrategy) throws InvalidExpenseException, UserNotFoundException {
        if(totalAmount<=0) throw new InvalidExpenseException("participants is empty or totalAmount is <=0");
        if(!usersMap.containsKey(paidBy)) throw new UserNotFoundException(paidBy+" user not found");
        Set<String> userIds = new HashSet<>(usersMap.keySet());
        if(participants!=null) userIds.addAll(participants);
        for(String userId:userIds){
            if(!usersMap.containsKey(userId)) throw new UserNotFoundException(userId);
        }
        Expense expense=new Expense(splitStrategy.getType(),splits,totalAmount,description);
        Map<String,Double> calculatedSplits=splitStrategy.calculate(totalAmount,participants,splits);
        for(Map.Entry<String,Double> entry:calculatedSplits.entrySet()){
            if(entry.getKey().equals(paidBy)) continue;
            updateBalance(paidBy,entry.getKey(),entry.getValue());
        }
        return expense;
    }
    boolean updateBalance(String paidById,String borrowerId,Double amount){
        Map<String,Double> paidByBalance=balances.get(paidById);
        Map<String,Double> borrowerBalance=balances.get(borrowerId);
        double newPaidByBalance=paidByBalance.getOrDefault(borrowerId,0.0)+amount;
        double newBorrowerBalance=borrowerBalance.getOrDefault(paidById,0.0)+amount;
        if(Math.abs(newPaidByBalance)<0.01){
            paidByBalance.remove(borrowerId);
            borrowerBalance.remove(paidById);
        }else{
            paidByBalance.put(borrowerId,newPaidByBalance);
            borrowerBalance.put(paidById,newBorrowerBalance);
        }
        return true;
    }
    Expense addEqualExpense(String description,double totalAmount,String paidBy,List<String> participants) throws UserNotFoundException, InvalidExpenseException {
        return addExpense(description,totalAmount,paidBy,participants,null,new EqualSplitStrategy());
    }
    Expense addPercentageExpense(String description,double totalAmount,String paidBy,Map<String,Double> splits) throws UserNotFoundException, InvalidExpenseException {
        return addExpense(description,totalAmount,paidBy,null,splits,new PercentSplitStrategy());
    }
    Expense addExactExpense(String description,double totalAmount,String paidBy,Map<String,Double> splits) throws UserNotFoundException, InvalidExpenseException {
        return addExpense(description,totalAmount,paidBy,null,splits,new ExactSplitStrategy());
    }
    boolean settleUp(String paidBy,String receivedBy,double amount) throws UserNotFoundException, InvalidExpenseException {
        if(!usersMap.containsKey(paidBy)) throw new UserNotFoundException(paidBy+" user not found");
        if(!usersMap.containsKey(receivedBy)) throw new UserNotFoundException(receivedBy+" user not found");
        double amountToPay=balances.get(paidBy).getOrDefault(receivedBy,0.0);
        if(amountToPay<0.01) throw new InvalidExpenseException("no debt to settle");
        if(amount-amountToPay>0.01) throw new InvalidExpenseException("cant pay more than owed");
        updateBalance(receivedBy,paidBy,-amount);
        return true;
    }
}

public class SplitWiseTest {
    static int passed = 0, failed = 0;
    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }
    static String addUserGetId(SplitWiseSystem sys, String name) throws Exception {
        sys.addUser(name);
        return sys.usersMap.keySet().stream()
                .filter(id -> sys.usersMap.get(id).userName.equals(name))
                .reduce((a, b) -> b).orElseThrow();
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      SplitWise Test Suite                ║");
        System.out.println("╚══════════════════════════════════════════╝");
        testUserClass(); testExpenseClass(); testExceptionClasses(); testSplitTypeEnum();
        testConstructor(); testAddUser(); testEqualSplitStrategy(); testExactSplitStrategy();
        testPercentSplitStrategy(); testAddEqualExpense(); testAddExactExpense();
        testAddPercentageExpense(); testAddExpenseValidation(); testUpdateBalance();
        testSettleUp(); testSettleUpValidation(); testEndToEndEqualSplit();
        testEndToEndExactSplit(); testEndToEndPercentSplit(); testMultipleExpenses();
        testSettlePartial(); testBalanceAfterSettleFull();
        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    static void testUserClass() {
        System.out.println("\n── User Class ──");
        User u = new User("Alice");
        check("userName set", "Alice".equals(u.userName));
        check("userId non-null 8 chars", u.userId != null && u.userId.length() == 8);
        check("toString has userId", u.toString().contains(u.userId));
        check("Two users diff ids", !u.userId.equals(new User("Bob").userId));
    }

    static void testExpenseClass() {
        System.out.println("\n── Expense Class ──");
        Expense e = new Expense(SplitType.EQUAL, Map.of("a", 50.0), 100.0, "dinner");
        check("description", "dinner".equals(e.description));
        check("amount", e.amount == 100.0);
        check("splitType", e.splitType == SplitType.EQUAL);
        check("expenseId prefix", e.expenseId.startsWith("EXPENSE-"));
        check("unique ids", !e.expenseId.equals(new Expense(SplitType.EQUAL, Map.of(), 0, "x").expenseId));
    }

    static void testExceptionClasses() {
        System.out.println("\n── Exceptions ──");
        check("UserNotFoundException", "m".equals(new UserNotFoundException("m").getMessage()));
        check("UserAlreadyExistsException", "m".equals(new UserAlreadyExistsException("m").getMessage()));
        check("InvalidExpenseException", "m".equals(new InvalidExpenseException("m").getMessage()));
        check("ExpenseNotFoundException", "m".equals(new ExpenseNotFoundException("m").getMessage()));
        check("InvalidSettlementException", "m".equals(new InvalidSettlementException("m").getMessage()));
    }

    static void testSplitTypeEnum() {
        System.out.println("\n── SplitType Enum ──");
        check("EQUAL", SplitType.valueOf("EQUAL") == SplitType.EQUAL);
        check("PERCENTAGE", SplitType.valueOf("PERCENTAGE") == SplitType.PERCENTAGE);
        check("EXACT", SplitType.valueOf("EXACT") == SplitType.EXACT);
        check("3 values", SplitType.values().length == 3);
    }

    static void testConstructor() {
        System.out.println("\n── Constructor ──");
        SplitWiseSystem s = new SplitWiseSystem();
        check("usersMap init", s.usersMap != null && s.usersMap.isEmpty());
        check("expenseMap init", s.expenseMap != null && s.expenseMap.isEmpty());
        check("balances init", s.balances != null && s.balances.isEmpty());
    }

    static void testAddUser() {
        System.out.println("\n── AddUser ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            check("returns true", s.addUser("Alice"));
            check("usersMap size 1", s.usersMap.size() == 1);
            check("balances size 1", s.balances.size() == 1);
            s.addUser("Bob");
            check("usersMap size 2", s.usersMap.size() == 2);
            check("Alice exists", s.usersMap.values().stream().anyMatch(u -> "Alice".equals(u.userName)));
            check("Bob exists", s.usersMap.values().stream().anyMatch(u -> "Bob".equals(u.userName)));
            for (Map<String, Double> b : s.balances.values()) check("balance empty", b.isEmpty());
        } catch (Exception e) { check("addUser: " + e.getMessage(), false); }
    }

    static void testEqualSplitStrategy() {
        System.out.println("\n── EqualSplitStrategy ──");
        EqualSplitStrategy st = new EqualSplitStrategy();
        check("type is EQUAL", st.getType() == SplitType.EQUAL);
        try {
            Map<String,Double> r = st.calculate(100.0, List.of("a","b"), null);
            check("100/2=50 each", r.get("a") == 50.0 && r.get("b") == 50.0);
        } catch (Exception e) { check("2-way split", false); }
        try {
            Map<String,Double> r = st.calculate(90.0, List.of("a","b","c"), null);
            check("90/3=30 each", r.get("a") == 30.0 && r.get("c") == 30.0);
        } catch (Exception e) { check("3-way split", false); }
        try {
            check("1 person gets full", st.calculate(100.0, List.of("a"), null).get("a") == 100.0);
        } catch (Exception e) { check("1 person", false); }
        try { st.calculate(100.0, null, null); check("null throws", false); }
        catch (InvalidExpenseException e) { check("null throws", true); }
        try { st.calculate(100.0, List.of(), null); check("empty throws", false); }
        catch (InvalidExpenseException e) { check("empty throws", true); }
    }

    static void testExactSplitStrategy() {
        System.out.println("\n── ExactSplitStrategy ──");
        ExactSplitStrategy st = new ExactSplitStrategy();
        check("type is EXACT", st.getType() == SplitType.EXACT);
        try {
            Map<String,Double> r = st.calculate(100.0, null, new HashMap<>(Map.of("a",60.0,"b",40.0)));
            check("exact amounts", r.get("a") == 60.0 && r.get("b") == 40.0);
        } catch (Exception e) { check("exact split", false); }
        try { st.calculate(100.0, null, Map.of("a",60.0,"b",30.0)); check("wrong sum throws", false); }
        catch (InvalidExpenseException e) { check("wrong sum throws", true); }
        try { st.calculate(100.0, null, null); check("null throws", false); }
        catch (InvalidExpenseException e) { check("null throws", true); }
        try { st.calculate(100.0, null, Map.of()); check("empty throws", false); }
        catch (InvalidExpenseException e) { check("empty throws", true); }
    }

    static void testPercentSplitStrategy() {
        System.out.println("\n── PercentSplitStrategy ──");
        PercentSplitStrategy st = new PercentSplitStrategy();
        try {
            Map<String,Double> r = st.calculate(200.0, null, new HashMap<>(Map.of("a",60.0,"b",40.0)));
            check("a=60%->60", Math.abs(r.get("a") - 60.0) < 0.01);
            check("b=40%->40", Math.abs(r.get("b") - 40.0) < 0.01);
        } catch (Exception e) { check("percent split", false); }
        try { st.calculate(100.0, null, Map.of("a",50.0,"b",40.0)); check("not 100% throws", false); }
        catch (InvalidExpenseException e) { check("not 100% throws", true); }
        try { st.calculate(100.0, null, null); check("null throws", false); }
        catch (InvalidExpenseException e) { check("null throws", true); }
    }

    static void testAddEqualExpense() {
        System.out.println("\n── AddEqualExpense ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "Alice"), b = addUserGetId(s, "Bob"), c = addUserGetId(s, "Charlie");
            Expense exp = s.addEqualExpense("dinner", 300.0, a, List.of(a, b, c));
            check("returns Expense", exp != null);
            check("amount 300", exp.amount == 300.0);
            check("desc dinner", "dinner".equals(exp.description));
            check("Bob owes Alice 100", Math.abs(s.balances.get(a).getOrDefault(b, 0.0) - 100.0) < 0.01);
            check("Charlie owes Alice 100", Math.abs(s.balances.get(a).getOrDefault(c, 0.0) - 100.0) < 0.01);
        } catch (Exception e) { check("addEqualExpense: " + e.getMessage(), false); }
    }

    static void testAddExactExpense() {
        System.out.println("\n── AddExactExpense ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "Alice"), b = addUserGetId(s, "Bob");
            Map<String,Double> sp = new HashMap<>(); sp.put(a, 70.0); sp.put(b, 30.0);
            Expense exp = s.addExactExpense("lunch", 100.0, a, sp);
            check("returns Expense", exp != null);
            check("Bob owes Alice 30", Math.abs(s.balances.get(a).getOrDefault(b, 0.0) - 30.0) < 0.01);
        } catch (Exception e) { check("addExactExpense: " + e.getMessage(), false); }
    }

    static void testAddPercentageExpense() {
        System.out.println("\n── AddPercentageExpense ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "Alice"), b = addUserGetId(s, "Bob");
            Map<String,Double> sp = new HashMap<>(); sp.put(a, 60.0); sp.put(b, 40.0);
            Expense exp = s.addPercentageExpense("coffee", 200.0, a, sp);
            check("returns Expense", exp != null);
            check("Bob owes Alice something", s.balances.get(a).containsKey(b) && s.balances.get(a).get(b) > 0);
        } catch (Exception e) { check("addPercentageExpense: " + e.getMessage(), false); }
    }

    static void testAddExpenseValidation() {
        System.out.println("\n── AddExpense Validation ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "Alice");
            try { s.addEqualExpense("x", 0.0, a, List.of(a)); check("zero amt throws", false); }
            catch (InvalidExpenseException e) { check("zero amt throws", true); }
            try { s.addEqualExpense("x", -10.0, a, List.of(a)); check("neg amt throws", false); }
            catch (InvalidExpenseException e) { check("neg amt throws", true); }
            try { s.addEqualExpense("x", 100.0, "unknown", List.of(a)); check("unknown paidBy throws", false); }
            catch (UserNotFoundException e) { check("unknown paidBy throws", true); }
        } catch (Exception e) { check("validation setup: " + e.getMessage(), false); }
    }

    static void testUpdateBalance() {
        System.out.println("\n── UpdateBalance ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "Alice"), b = addUserGetId(s, "Bob");
            s.updateBalance(a, b, 50.0);
            check("Alice owed 50 by Bob", Math.abs(s.balances.get(a).get(b) - 50.0) < 0.01);
            check("Bob owes 50 to Alice", Math.abs(s.balances.get(b).get(a) - 50.0) < 0.01);
            s.updateBalance(a, b, 30.0);
            check("After +30, total 80", Math.abs(s.balances.get(a).get(b) - 80.0) < 0.01);
        } catch (Exception e) { check("updateBalance: " + e.getMessage(), false); }
    }

    static void testSettleUp() {
        System.out.println("\n── SettleUp ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "Alice"), b = addUserGetId(s, "Bob");
            s.addEqualExpense("dinner", 200.0, a, List.of(a, b));
            check("settleUp returns true", s.settleUp(b, a, 100.0));
            check("balance cleared", Math.abs(s.balances.get(a).getOrDefault(b, 0.0)) < 0.01);
        } catch (Exception e) { check("settleUp: " + e.getMessage(), false); }
    }

    static void testSettleUpValidation() {
        System.out.println("\n── SettleUp Validation ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "Alice"), b = addUserGetId(s, "Bob");
            try { s.settleUp("unknown", a, 10.0); check("unknown paidBy throws", false); }
            catch (UserNotFoundException e) { check("unknown paidBy throws", true); }
            try { s.settleUp(a, "unknown", 10.0); check("unknown receivedBy throws", false); }
            catch (UserNotFoundException e) { check("unknown receivedBy throws", true); }
            try { s.settleUp(a, b, 10.0); check("no debt throws", false); }
            catch (InvalidExpenseException e) { check("no debt throws", true); }
            s.addEqualExpense("t", 100.0, a, List.of(a, b)); // Bob owes 50
            try { s.settleUp(b, a, 100.0); check("overpay throws", false); }
            catch (InvalidExpenseException e) { check("overpay throws", true); }
        } catch (Exception e) { check("settleUp validation: " + e.getMessage(), false); }
    }

    static void testEndToEndEqualSplit() {
        System.out.println("\n── E2E Equal Split ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "A"), b = addUserGetId(s, "B"), c = addUserGetId(s, "C");
            s.addEqualExpense("trip", 300.0, a, List.of(a, b, c));
            check("B owes A 100", Math.abs(s.balances.get(a).getOrDefault(b, 0.0) - 100.0) < 0.01);
            check("C owes A 100", Math.abs(s.balances.get(a).getOrDefault(c, 0.0) - 100.0) < 0.01);
            s.settleUp(b, a, 100.0);
            check("B settled", Math.abs(s.balances.get(a).getOrDefault(b, 0.0)) < 0.01);
            check("C still owes", Math.abs(s.balances.get(a).getOrDefault(c, 0.0) - 100.0) < 0.01);
            s.settleUp(c, a, 100.0);
            check("All settled", s.balances.get(a).getOrDefault(c, 0.0) < 0.01);
        } catch (Exception e) { check("E2E equal: " + e.getMessage(), false); }
    }

    static void testEndToEndExactSplit() {
        System.out.println("\n── E2E Exact Split ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "A"), b = addUserGetId(s, "B");
            Map<String,Double> sp = new HashMap<>(); sp.put(a, 80.0); sp.put(b, 20.0);
            s.addExactExpense("gift", 100.0, a, sp);
            check("B owes A 20", Math.abs(s.balances.get(a).getOrDefault(b, 0.0) - 20.0) < 0.01);
            s.settleUp(b, a, 20.0);
            check("Settled", Math.abs(s.balances.get(a).getOrDefault(b, 0.0)) < 0.01);
        } catch (Exception e) { check("E2E exact: " + e.getMessage(), false); }
    }

    static void testEndToEndPercentSplit() {
        System.out.println("\n── E2E Percent Split ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "A"), b = addUserGetId(s, "B");
            Map<String,Double> sp = new HashMap<>(); sp.put(a, 50.0); sp.put(b, 50.0);
            s.addPercentageExpense("dinner", 200.0, a, sp);
            check("B owes A after 50/50", s.balances.get(a).containsKey(b) && s.balances.get(a).get(b) > 0);
        } catch (Exception e) { check("E2E percent: " + e.getMessage(), false); }
    }

    static void testMultipleExpenses() {
        System.out.println("\n── Multiple Expenses ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "A"), b = addUserGetId(s, "B");
            s.addEqualExpense("d1", 100.0, a, List.of(a, b)); // B owes A 50
            s.addEqualExpense("d2", 60.0, b, List.of(a, b));  // A owes B 30
            double aOwedByB = s.balances.get(a).getOrDefault(b, 0.0);
            double bOwedByA = s.balances.get(b).getOrDefault(a, 0.0);
            check("balances exist after 2 expenses", aOwedByB > 0 || bOwedByA > 0);
        } catch (Exception e) { check("multiple expenses: " + e.getMessage(), false); }
    }

    static void testSettlePartial() {
        System.out.println("\n── Settle Partial ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "A"), b = addUserGetId(s, "B");
            s.addEqualExpense("d", 200.0, a, List.of(a, b)); // B owes A 100
            s.settleUp(b, a, 40.0);
            check("After partial settle 40, remaining ~60", Math.abs(s.balances.get(a).getOrDefault(b, 0.0) - 60.0) < 0.01);
        } catch (Exception e) { check("settle partial: " + e.getMessage(), false); }
    }

    static void testBalanceAfterSettleFull() {
        System.out.println("\n── Balance After Full Settle ──");
        try {
            SplitWiseSystem s = new SplitWiseSystem();
            String a = addUserGetId(s, "A"), b = addUserGetId(s, "B");
            s.addEqualExpense("d", 100.0, a, List.of(a, b)); // B owes A 50
            s.settleUp(b, a, 50.0);
            boolean aClear = !s.balances.get(a).containsKey(b) || Math.abs(s.balances.get(a).get(b)) < 0.01;
            boolean bClear = !s.balances.get(b).containsKey(a) || Math.abs(s.balances.get(b).get(a)) < 0.01;
            check("Alice balance cleared", aClear);
            check("Bob balance cleared", bClear);
        } catch (Exception e) { check("full settle: " + e.getMessage(), false); }
    }
}
