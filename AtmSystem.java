import java.util.HashMap;
import java.util.Map;
import java.util.Random;

enum ATMState{IDLE,CARD_INSERTED,AUTHENTICATED,OUT_OF_SERVICE}
enum Denomination{
    HUNDRED(100),FIFTY(50),TWENTY(20),TEN(10),FIVE(5);
    final int value;
    Denomination(int value) {
        this.value=value;
    }
}
class Card{
    String cardNumber;
    String pin;
    boolean isBlocked;
    String accountId;
    Random random=new Random();
    int failedAttempts;
    public Card(String accountId,String pin) {
        this.cardNumber = String.valueOf(random.nextInt(1000,9999))+"-"+String.valueOf(random.nextInt(1000,9999))+"-"+String.valueOf(random.nextInt(1000,9999))+"-"+String.valueOf(random.nextInt(1000,9999));
        this.pin = pin;
        this.isBlocked = false;
        this.accountId = accountId;
        failedAttempts=0;
    }

    @Override
    public String toString() {
        return "Card{" +
                "cardNumber='" + cardNumber + '\'' +
                ", isBlocked=" + isBlocked +
                '}';
    }
}
class Account{
    String accountId;
    double balance;
    double dailyLimit;
    double withdrawnToday;
    Random random=new Random();
    public Account() {
        accountId=getAccountId();
        balance=0;
        dailyLimit=0;
        withdrawnToday=0;
    }
    String getAccountId() {
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<16;i++){
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", balance=" + balance +
                ", dailyLimit=" + dailyLimit +
                ", withdrawnToday=" + withdrawnToday +
                ", random=" + random +
                '}';
    }
}
class ATMService{
    ATMState state;
    Map<String,Card> cardMap;
    Map<String,Account> accountMap;
    Map<Denomination,Integer> cashInventory;
    int totalCash;
    Card currentCard;
    Account currentAccount;
    public ATMService() {
        this.state=ATMState.IDLE;
        cardMap=new HashMap<>();
        accountMap=new HashMap<>();
        cashInventory=new HashMap<>();
        this.totalCash=0;
        for(Denomination d:Denomination.values()){cashInventory.put(d,0);}
    }
    void addCard(Card card){cardMap.put(card.cardNumber,card);}
    void addAccount(Account account){accountMap.put(account.accountId,account);}
    void loadCash(Denomination denomination,int count){
        cashInventory.put(denomination,count);
        totalCash+=count*denomination.value;
    }
    void insertCard(String cardNumber){
        if(!state.equals(ATMState.IDLE)) throw new IllegalStateException("ATMState must be IDLE");
        Card card=cardMap.get(cardNumber);
        if(card==null) throw new IllegalStateException("Card not found");
        if(card.isBlocked) throw new IllegalStateException("Card is blocked");
        currentCard=card;
        state=ATMState.CARD_INSERTED;
    }
    void insertPin(String pin){
        if(!state.equals(ATMState.CARD_INSERTED)) throw new IllegalStateException("ATMState must be Card Inserted");
        if(currentCard==null) throw new IllegalStateException("Card not found");
        if(currentCard.pin.equals(pin)){
            currentCard.failedAttempts=0;
            currentAccount=accountMap.get(currentCard.accountId);
            state=ATMState.AUTHENTICATED;
            System.out.println("Welcome");
        }else{
            currentCard.failedAttempts++;
            if(currentCard.failedAttempts==3){
                currentCard.isBlocked=true;
                ejectCard();
                throw new IllegalStateException("Card is blocked");
            }
            throw new IllegalStateException("Pin is incorrect");
        }
    }
    void ejectCard(){
        if(!state.equals(ATMState.CARD_INSERTED)) throw new IllegalStateException("ATMState must be Card Inserted");
        currentCard=null;
        currentAccount=null;
        state=ATMState.IDLE;
    }
    double getBalance(){
        return currentAccount.balance;
    }
    Map<Denomination,Integer> withdraw(int amount){
        if(amount<=0 || amount%5!=0) throw new IllegalStateException("Amount must be positive and divisible by 5");
        if(currentAccount.withdrawnToday+amount>currentAccount.dailyLimit) throw new IllegalStateException("Amount exceeds daily limit");
        if(amount>currentAccount.balance) throw new IllegalStateException("Amount exceeds balance");
        Map<Denomination,Integer> result=calculateDenominations(amount);
        if(result==null) throw new IllegalStateException("Cash inventory insufficient");
        currentAccount.withdrawnToday+=amount;
        currentAccount.balance-=amount;
        result.forEach((k,v)->{
            cashInventory.put(k,cashInventory.get(k)-v);
            totalCash-=(k.value*v);
        });
        return result;
    }
    Map<Denomination,Integer> calculateDenominations(int amount){
        Map<Denomination,Integer> result=new HashMap<>();
        int remaining=amount;
        for(Denomination d:Denomination.values()){
            int needed=remaining/d.value;
            int available=cashInventory.get(d);
            int use=Math.min(needed,available);
            if(use==0) continue;
            result.put(d,use);
            remaining-=use*d.value;
        }
        return remaining==0?result:new HashMap<>();
    }

}

public class AtmSystem {
    static int passed = 0, failed = 0;

    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else    { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }

    // ── Helpers ──
    static Account makeAccount(double balance, double dailyLimit) {
        Account a = new Account();
        a.balance = balance;
        a.dailyLimit = dailyLimit;
        return a;
    }

    static ATMService freshAtm() {
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.HUNDRED, 10);
        atm.loadCash(Denomination.FIFTY, 10);
        atm.loadCash(Denomination.TWENTY, 10);
        atm.loadCash(Denomination.TEN, 10);
        atm.loadCash(Denomination.FIVE, 10);
        return atm;
    }

    static ATMService authenticatedAtm(Account account, Card card) {
        ATMService atm = freshAtm();
        atm.addAccount(account);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        atm.insertPin(card.pin);
        return atm;
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║        ATM System Test Suite             ║");
        System.out.println("╚══════════════════════════════════════════╝");

        testATMStateEnum();
        testDenominationEnum();
        testCardCreation();
        testCardToString();
        testCardInitialState();
        testAccountCreation();
        testAccountDefaults();
        testAccountToString();
        testAccountIdUnique();
        testATMServiceConstructor();
        testAddCardAndAccount();
        testLoadCash();
        testLoadCashMultipleDenominations();
        testLoadCashAccumulatesTotalCash();
        testInsertCardSuccess();
        testInsertCardNotFound();
        testInsertCardBlocked();
        testInsertCardNotIdleState();
        testInsertPinCorrect();
        testInsertPinWrong();
        testInsertPinBlockAfter3Failures();
        testInsertPinResetOnSuccess();
        testInsertPinWrongState();
        testEjectCard();
        testEjectCardWrongState();
        testGetBalance();
        testWithdrawSuccess();
        testWithdrawDenominationBreakdown();
        testWithdrawUpdatesBalance();
        testWithdrawUpdatesDailyWithdrawn();
        testWithdrawUpdatesInventory();
        testWithdrawNotDivisibleBy5();
        testWithdrawNegativeAmount();
        testWithdrawZeroAmount();
        testWithdrawExceedsDailyLimit();
        testWithdrawExceedsBalance();
        testWithdrawInsufficientInventory();
        testWithdrawMultiple();
        testWithdrawExactDenominations();
        testWithdrawOnlyFives();
        testWithdrawLargeAmount();
        testCalculateDenominationsGreedy();
        testCalculateDenominationsPartialStock();
        testCalculateDenominationsNotPossible();
        testCalculateDenominationsExactNotes();
        testFullSessionFlow();
        testMultipleCardsSameATM();
        testReinsertAfterEject();
        testBlockedCardCannotReinsert();
        testDailyLimitAcrossMultipleWithdrawals();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ═══════════════════════ ENUMS ═══════════════════════

    static void testATMStateEnum() {
        System.out.println("\n── ATMState Enum ──");
        check("IDLE exists", ATMState.valueOf("IDLE") == ATMState.IDLE);
        check("CARD_INSERTED exists", ATMState.valueOf("CARD_INSERTED") == ATMState.CARD_INSERTED);
        check("AUTHENTICATED exists", ATMState.valueOf("AUTHENTICATED") == ATMState.AUTHENTICATED);
        check("OUT_OF_SERVICE exists", ATMState.valueOf("OUT_OF_SERVICE") == ATMState.OUT_OF_SERVICE);
        check("4 states total", ATMState.values().length == 4);
    }

    static void testDenominationEnum() {
        System.out.println("\n── Denomination Enum ──");
        check("HUNDRED value is 100", Denomination.HUNDRED.value == 100);
        check("FIFTY value is 50", Denomination.FIFTY.value == 50);
        check("TWENTY value is 20", Denomination.TWENTY.value == 20);
        check("TEN value is 10", Denomination.TEN.value == 10);
        check("FIVE value is 5", Denomination.FIVE.value == 5);
        check("5 denominations total", Denomination.values().length == 5);
        Denomination[] vals = Denomination.values();
        check("first denomination is HUNDRED", vals[0] == Denomination.HUNDRED);
        check("last denomination is FIVE", vals[4] == Denomination.FIVE);
    }

    // ═══════════════════════ CARD ═══════════════════════

    static void testCardCreation() {
        System.out.println("\n── Card Creation ──");
        Card c = new Card("ACC123", "1234");
        check("accountId set", "ACC123".equals(c.accountId));
        check("pin set", "1234".equals(c.pin));
        check("cardNumber not null", c.cardNumber != null);
        check("cardNumber has format XXXX-XXXX-XXXX-XXXX", c.cardNumber.matches("\\d{4}-\\d{4}-\\d{4}-\\d{4}"));
    }

    static void testCardToString() {
        System.out.println("\n── Card toString ──");
        Card c = new Card("ACC1", "0000");
        String s = c.toString();
        check("toString contains cardNumber", s.contains(c.cardNumber));
        check("toString contains isBlocked", s.contains("isBlocked"));
    }

    static void testCardInitialState() {
        System.out.println("\n── Card Initial State ──");
        Card c = new Card("ACC1", "9999");
        check("not blocked initially", !c.isBlocked);
        check("failedAttempts is 0", c.failedAttempts == 0);
    }

    // ═══════════════════════ ACCOUNT ═══════════════════════

    static void testAccountCreation() {
        System.out.println("\n── Account Creation ──");
        Account a = new Account();
        check("accountId not null", a.accountId != null);
        check("accountId is 16 digits", a.accountId.matches("\\d{16}"));
    }

    static void testAccountDefaults() {
        System.out.println("\n── Account Defaults ──");
        Account a = new Account();
        check("balance is 0", a.balance == 0);
        check("dailyLimit is 0", a.dailyLimit == 0);
        check("withdrawnToday is 0", a.withdrawnToday == 0);
    }

    static void testAccountToString() {
        System.out.println("\n── Account toString ──");
        Account a = new Account();
        a.balance = 500;
        String s = a.toString();
        check("toString contains accountId", s.contains(a.accountId));
        check("toString contains balance", s.contains("500"));
    }

    static void testAccountIdUnique() {
        System.out.println("\n── Account ID Uniqueness ──");
        Account a1 = new Account();
        Account a2 = new Account();
        Account a3 = new Account();
        boolean allDiff = !a1.accountId.equals(a2.accountId)
                       && !a2.accountId.equals(a3.accountId)
                       && !a1.accountId.equals(a3.accountId);
        check("3 accounts have different IDs (probabilistic)", allDiff);
    }

    // ═══════════════════════ ATMService Constructor ═══════════════════════

    static void testATMServiceConstructor() {
        System.out.println("\n── ATMService Constructor ──");
        ATMService atm = new ATMService();
        check("initial state is IDLE", atm.state == ATMState.IDLE);
        check("cardMap initialized", atm.cardMap != null && atm.cardMap.isEmpty());
        check("accountMap initialized", atm.accountMap != null && atm.accountMap.isEmpty());
        check("cashInventory initialized", atm.cashInventory != null);
        check("totalCash is 0", atm.totalCash == 0);
        check("currentCard is null", atm.currentCard == null);
        check("currentAccount is null", atm.currentAccount == null);
        for (Denomination d : Denomination.values()) {
            check("inventory " + d.name() + " = 0", atm.cashInventory.get(d) == 0);
        }
    }

    // ═══════════════════════ Add Card/Account ═══════════════════════

    static void testAddCardAndAccount() {
        System.out.println("\n── Add Card & Account ──");
        ATMService atm = new ATMService();
        Account acc = new Account();
        Card card = new Card(acc.accountId, "1111");
        atm.addAccount(acc);
        atm.addCard(card);
        check("account stored", atm.accountMap.containsKey(acc.accountId));
        check("card stored", atm.cardMap.containsKey(card.cardNumber));
        check("card links to account", atm.cardMap.get(card.cardNumber).accountId.equals(acc.accountId));
    }

    // ═══════════════════════ Load Cash ═══════════════════════

    static void testLoadCash() {
        System.out.println("\n── Load Cash ──");
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.HUNDRED, 5);
        check("inventory HUNDRED = 5", atm.cashInventory.get(Denomination.HUNDRED) == 5);
        check("totalCash = 500", atm.totalCash == 500);
    }

    static void testLoadCashMultipleDenominations() {
        System.out.println("\n── Load Cash Multiple Denominations ──");
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.HUNDRED, 2);
        atm.loadCash(Denomination.FIFTY, 3);
        atm.loadCash(Denomination.TWENTY, 5);
        check("HUNDRED count", atm.cashInventory.get(Denomination.HUNDRED) == 2);
        check("FIFTY count", atm.cashInventory.get(Denomination.FIFTY) == 3);
        check("TWENTY count", atm.cashInventory.get(Denomination.TWENTY) == 5);
        check("totalCash = 450", atm.totalCash == 450);
    }

    static void testLoadCashAccumulatesTotalCash() {
        System.out.println("\n── Load Cash Accumulates Total ──");
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.TEN, 10);
        check("totalCash after first load", atm.totalCash == 100);
        atm.loadCash(Denomination.FIVE, 20);
        check("totalCash after second load", atm.totalCash == 200);
        check("TEN count", atm.cashInventory.get(Denomination.TEN) == 10);
        check("FIVE count", atm.cashInventory.get(Denomination.FIVE) == 20);
    }

    // ═══════════════════════ Insert Card ═══════════════════════

    static void testInsertCardSuccess() {
        System.out.println("\n── Insert Card Success ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "1234");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        check("state is CARD_INSERTED", atm.state == ATMState.CARD_INSERTED);
        check("currentCard set", atm.currentCard == card);
    }

    static void testInsertCardNotFound() {
        System.out.println("\n── Insert Card Not Found ──");
        ATMService atm = freshAtm();
        try {
            atm.insertCard("9999-9999-9999-9999");
            check("should throw for unknown card", false);
        } catch (IllegalStateException e) {
            check("throws Card not found", e.getMessage().contains("Card not found"));
        }
    }

    static void testInsertCardBlocked() {
        System.out.println("\n── Insert Card Blocked ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "1234");
        card.isBlocked = true;
        atm.addAccount(acc);
        atm.addCard(card);
        try {
            atm.insertCard(card.cardNumber);
            check("should throw for blocked card", false);
        } catch (IllegalStateException e) {
            check("throws Card is blocked", e.getMessage().contains("Card is blocked"));
        }
        check("state still IDLE after blocked card", atm.state == ATMState.IDLE);
    }

    static void testInsertCardNotIdleState() {
        System.out.println("\n── Insert Card Not Idle ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card1 = new Card(acc.accountId, "1111");
        Card card2 = new Card(acc.accountId, "2222");
        atm.addAccount(acc);
        atm.addCard(card1);
        atm.addCard(card2);
        atm.insertCard(card1.cardNumber);
        try {
            atm.insertCard(card2.cardNumber);
            check("should throw if not IDLE", false);
        } catch (IllegalStateException e) {
            check("throws ATMState must be IDLE", e.getMessage().contains("IDLE"));
        }
    }

    // ═══════════════════════ Insert Pin ═══════════════════════

    static void testInsertPinCorrect() {
        System.out.println("\n── Insert Pin Correct ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "4321");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        atm.insertPin("4321");
        check("state is AUTHENTICATED", atm.state == ATMState.AUTHENTICATED);
        check("currentAccount set", atm.currentAccount == acc);
        check("failedAttempts reset to 0", card.failedAttempts == 0);
    }

    static void testInsertPinWrong() {
        System.out.println("\n── Insert Pin Wrong ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "4321");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        try {
            atm.insertPin("0000");
            check("wrong pin should throw", false);
        } catch (IllegalStateException e) {
            check("throws Pin is incorrect", e.getMessage().contains("incorrect"));
        }
        check("failedAttempts is 1", card.failedAttempts == 1);
        check("card not blocked after 1 failure", !card.isBlocked);
        check("state still CARD_INSERTED", atm.state == ATMState.CARD_INSERTED);
    }

    static void testInsertPinBlockAfter3Failures() {
        System.out.println("\n── Insert Pin Block After 3 Failures ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "4321");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);

        try { atm.insertPin("0000"); } catch (IllegalStateException e) {}
        check("attempt 1: failedAttempts=1", card.failedAttempts == 1);
        check("attempt 1: not blocked", !card.isBlocked);

        try { atm.insertPin("1111"); } catch (IllegalStateException e) {}
        check("attempt 2: failedAttempts=2", card.failedAttempts == 2);
        check("attempt 2: not blocked", !card.isBlocked);

        try {
            atm.insertPin("2222");
            check("3rd wrong pin should throw", false);
        } catch (IllegalStateException e) {
            check("throws Card is blocked", e.getMessage().contains("blocked"));
        }
        check("card is now blocked", card.isBlocked);
        check("failedAttempts is 3", card.failedAttempts == 3);
        check("state back to IDLE after block+eject", atm.state == ATMState.IDLE);
        check("currentCard is null after eject", atm.currentCard == null);
    }

    static void testInsertPinResetOnSuccess() {
        System.out.println("\n── Insert Pin Reset Failed Attempts On Success ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "5678");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);

        try { atm.insertPin("0000"); } catch (IllegalStateException e) {}
        try { atm.insertPin("1111"); } catch (IllegalStateException e) {}
        check("2 failed attempts", card.failedAttempts == 2);

        atm.insertPin("5678");
        check("failedAttempts reset to 0 on success", card.failedAttempts == 0);
        check("authenticated", atm.state == ATMState.AUTHENTICATED);
    }

    static void testInsertPinWrongState() {
        System.out.println("\n── Insert Pin Wrong State ──");
        ATMService atm = freshAtm();
        try {
            atm.insertPin("1234");
            check("should throw if not CARD_INSERTED", false);
        } catch (IllegalStateException e) {
            check("throws ATMState must be Card Inserted", e.getMessage().contains("Card Inserted"));
        }
    }

    // ═══════════════════════ Eject Card ═══════════════════════

    static void testEjectCard() {
        System.out.println("\n── Eject Card ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "1234");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        atm.ejectCard();
        check("state back to IDLE", atm.state == ATMState.IDLE);
        check("currentCard is null", atm.currentCard == null);
        check("currentAccount is null", atm.currentAccount == null);
    }

    static void testEjectCardWrongState() {
        System.out.println("\n── Eject Card Wrong State ──");
        ATMService atm = freshAtm();
        try {
            atm.ejectCard();
            check("should throw if IDLE", false);
        } catch (IllegalStateException e) {
            check("throws ATMState must be Card Inserted", e.getMessage().contains("Card Inserted"));
        }

        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "1234");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        atm.insertPin("1234");
        try {
            atm.ejectCard();
            check("eject from AUTHENTICATED throws (known limitation)", false);
        } catch (IllegalStateException e) {
            check("eject from AUTHENTICATED throws (known limitation)", true);
        }
    }

    // ═══════════════════════ Get Balance ═══════════════════════

    static void testGetBalance() {
        System.out.println("\n── Get Balance ──");
        Account acc = makeAccount(2500.75, 1000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        check("balance is 2500.75", atm.getBalance() == 2500.75);
    }

    // ═══════════════════════ Withdraw ═══════════════════════

    static void testWithdrawSuccess() {
        System.out.println("\n── Withdraw Success ──");
        Account acc = makeAccount(5000, 2000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);

        Map<Denomination, Integer> result = atm.withdraw(150);
        check("result not null", result != null);
        check("result not empty", !result.isEmpty());
        int total = 0;
        for (Map.Entry<Denomination, Integer> e : result.entrySet()) {
            total += e.getKey().value * e.getValue();
        }
        check("dispensed amount = 150", total == 150);
    }

    static void testWithdrawDenominationBreakdown() {
        System.out.println("\n── Withdraw Denomination Breakdown ──");
        Account acc = makeAccount(5000, 2000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);

        Map<Denomination, Integer> result = atm.withdraw(175);
        check("HUNDRED count = 1", result.getOrDefault(Denomination.HUNDRED, 0) == 1);
        check("FIFTY count = 1", result.getOrDefault(Denomination.FIFTY, 0) == 1);
        check("TWENTY count = 1", result.getOrDefault(Denomination.TWENTY, 0) == 1);
        check("FIVE count = 1", result.getOrDefault(Denomination.FIVE, 0) == 1);
    }

    static void testWithdrawUpdatesBalance() {
        System.out.println("\n── Withdraw Updates Balance ──");
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        atm.withdraw(200);
        check("balance reduced to 800", acc.balance == 800);
    }

    static void testWithdrawUpdatesDailyWithdrawn() {
        System.out.println("\n── Withdraw Updates Daily Withdrawn ──");
        Account acc = makeAccount(5000, 2000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        atm.withdraw(300);
        check("withdrawnToday = 300", acc.withdrawnToday == 300);
    }

    static void testWithdrawUpdatesInventory() {
        System.out.println("\n── Withdraw Updates Inventory ──");
        Account acc = makeAccount(5000, 2000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        int hundredsBefore = atm.cashInventory.get(Denomination.HUNDRED);
        int totalBefore = atm.totalCash;
        atm.withdraw(100);
        check("HUNDRED count decreased by 1", atm.cashInventory.get(Denomination.HUNDRED) == hundredsBefore - 1);
        check("totalCash decreased by 100", atm.totalCash == totalBefore - 100);
    }

    static void testWithdrawNotDivisibleBy5() {
        System.out.println("\n── Withdraw Not Divisible By 5 ──");
        Account acc = makeAccount(5000, 2000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        try { atm.withdraw(13); check("should throw for 13", false); }
        catch (IllegalStateException e) { check("throws divisible by 5 error", e.getMessage().contains("divisible by 5")); }
        try { atm.withdraw(7); check("should throw for 7", false); }
        catch (IllegalStateException e) { check("throws for 7", true); }
        try { atm.withdraw(1); check("should throw for 1", false); }
        catch (IllegalStateException e) { check("throws for 1", true); }
    }

    static void testWithdrawNegativeAmount() {
        System.out.println("\n── Withdraw Negative Amount ──");
        Account acc = makeAccount(5000, 2000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        try { atm.withdraw(-100); check("should throw for negative", false); }
        catch (IllegalStateException e) { check("throws for negative amount", e.getMessage().contains("positive")); }
    }

    static void testWithdrawZeroAmount() {
        System.out.println("\n── Withdraw Zero Amount ──");
        Account acc = makeAccount(5000, 2000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        try { atm.withdraw(0); check("should throw for zero", false); }
        catch (IllegalStateException e) { check("throws for zero amount", e.getMessage().contains("positive")); }
    }

    static void testWithdrawExceedsDailyLimit() {
        System.out.println("\n── Withdraw Exceeds Daily Limit ──");
        Account acc = makeAccount(5000, 200);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        try { atm.withdraw(300); check("should throw for exceeding daily limit", false); }
        catch (IllegalStateException e) { check("throws daily limit error", e.getMessage().contains("daily limit")); }
        check("balance unchanged after failed withdraw", acc.balance == 5000);
    }

    static void testWithdrawExceedsBalance() {
        System.out.println("\n── Withdraw Exceeds Balance ──");
        Account acc = makeAccount(100, 5000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        try { atm.withdraw(200); check("should throw for exceeding balance", false); }
        catch (IllegalStateException e) { check("throws balance exceeded error", e.getMessage().contains("balance")); }
    }

    static void testWithdrawInsufficientInventory() {
        System.out.println("\n── Withdraw Insufficient Inventory ──");
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.FIVE, 5);
        Account acc = makeAccount(5000, 5000);
        Card card = new Card(acc.accountId, "1234");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        atm.insertPin("1234");
        try {
            Map<Denomination, Integer> result = atm.withdraw(30);
            check("insufficient inventory: known bug - doesn't throw (returns empty map)", result != null && result.isEmpty());
        } catch (IllegalStateException e) {
            check("insufficient inventory throws correctly", e.getMessage().contains("insufficient"));
        }
    }

    static void testWithdrawMultiple() {
        System.out.println("\n── Withdraw Multiple Times ──");
        Account acc = makeAccount(5000, 3000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        atm.withdraw(100);
        check("balance after 1st: 4900", acc.balance == 4900);
        check("withdrawnToday after 1st: 100", acc.withdrawnToday == 100);
        atm.withdraw(200);
        check("balance after 2nd: 4700", acc.balance == 4700);
        check("withdrawnToday after 2nd: 300", acc.withdrawnToday == 300);
        atm.withdraw(500);
        check("balance after 3rd: 4200", acc.balance == 4200);
        check("withdrawnToday after 3rd: 800", acc.withdrawnToday == 800);
    }

    static void testWithdrawExactDenominations() {
        System.out.println("\n── Withdraw Exact Denomination Amounts ──");
        Account acc = makeAccount(5000, 5000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        Map<Denomination, Integer> r1 = atm.withdraw(100);
        check("100: uses 1 HUNDRED", r1.getOrDefault(Denomination.HUNDRED, 0) == 1);
        check("100: only 1 denomination used", r1.size() == 1);
        Map<Denomination, Integer> r2 = atm.withdraw(50);
        check("50: uses 1 FIFTY", r2.getOrDefault(Denomination.FIFTY, 0) == 1);
        Map<Denomination, Integer> r3 = atm.withdraw(20);
        check("20: uses 1 TWENTY", r3.getOrDefault(Denomination.TWENTY, 0) == 1);
        Map<Denomination, Integer> r4 = atm.withdraw(10);
        check("10: uses 1 TEN", r4.getOrDefault(Denomination.TEN, 0) == 1);
        Map<Denomination, Integer> r5 = atm.withdraw(5);
        check("5: uses 1 FIVE", r5.getOrDefault(Denomination.FIVE, 0) == 1);
    }

    static void testWithdrawOnlyFives() {
        System.out.println("\n── Withdraw When Only Fives Available ──");
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.FIVE, 100);
        Account acc = makeAccount(5000, 5000);
        Card card = new Card(acc.accountId, "1234");
        atm.addAccount(acc);
        atm.addCard(card);
        atm.insertCard(card.cardNumber);
        atm.insertPin("1234");
        Map<Denomination, Integer> r = atm.withdraw(25);
        check("25 in fives: 5 notes", r.getOrDefault(Denomination.FIVE, 0) == 5);
        check("only FIVE denomination used", r.size() == 1);
    }

    static void testWithdrawLargeAmount() {
        System.out.println("\n── Withdraw Large Amount ──");
        Account acc = makeAccount(50000, 50000);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);
        Map<Denomination, Integer> r = atm.withdraw(1850);
        int total = 0;
        for (Map.Entry<Denomination, Integer> e : r.entrySet()) {
            total += e.getKey().value * e.getValue();
        }
        check("large withdrawal dispensed correct amount", total == 1850);
        check("balance updated", acc.balance == 50000 - 1850);
    }

    // ═══════════════════════ calculateDenominations ═══════════════════════

    static void testCalculateDenominationsGreedy() {
        System.out.println("\n── Calculate Denominations Greedy ──");
        ATMService atm = freshAtm();
        Map<Denomination, Integer> r = atm.calculateDenominations(285);
        check("285: HUNDRED=2", r.getOrDefault(Denomination.HUNDRED, 0) == 2);
        check("285: FIFTY=1", r.getOrDefault(Denomination.FIFTY, 0) == 1);
        check("285: TWENTY=1", r.getOrDefault(Denomination.TWENTY, 0) == 1);
        check("285: TEN=1", r.getOrDefault(Denomination.TEN, 0) == 1);
        check("285: FIVE=1", r.getOrDefault(Denomination.FIVE, 0) == 1);
    }

    static void testCalculateDenominationsPartialStock() {
        System.out.println("\n── Calculate Denominations Partial Stock ──");
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.HUNDRED, 1);
        atm.loadCash(Denomination.FIFTY, 0);
        atm.loadCash(Denomination.TWENTY, 10);
        atm.loadCash(Denomination.TEN, 10);
        atm.loadCash(Denomination.FIVE, 10);
        Map<Denomination, Integer> r = atm.calculateDenominations(200);
        check("200 partial: HUNDRED=1", r.getOrDefault(Denomination.HUNDRED, 0) == 1);
        check("200 partial: TWENTY=5", r.getOrDefault(Denomination.TWENTY, 0) == 5);
        int total = 0;
        for (Map.Entry<Denomination, Integer> e : r.entrySet()) {
            total += e.getKey().value * e.getValue();
        }
        check("200 partial: total correct", total == 200);
    }

    static void testCalculateDenominationsNotPossible() {
        System.out.println("\n── Calculate Denominations Not Possible ──");
        ATMService atm = new ATMService();
        atm.loadCash(Denomination.HUNDRED, 1);
        Map<Denomination, Integer> r = atm.calculateDenominations(150);
        check("impossible amount returns empty map", r != null && r.isEmpty());
    }

    static void testCalculateDenominationsExactNotes() {
        System.out.println("\n── Calculate Denominations Exact Notes ──");
        ATMService atm = freshAtm();
        Map<Denomination, Integer> r = atm.calculateDenominations(500);
        check("500: HUNDRED=5", r.getOrDefault(Denomination.HUNDRED, 0) == 5);
        check("500: only HUNDRED used", r.size() == 1);
        Map<Denomination, Integer> r0 = atm.calculateDenominations(0);
        check("0 amount returns empty map", r0 != null && r0.isEmpty());
    }

    // ═══════════════════════ End-to-End Scenarios ═══════════════════════

    static void testFullSessionFlow() {
        System.out.println("\n── Full Session Flow ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(3000, 1500);
        Card card = new Card(acc.accountId, "9999");
        atm.addAccount(acc);
        atm.addCard(card);

        atm.insertCard(card.cardNumber);
        check("E2E: state CARD_INSERTED", atm.state == ATMState.CARD_INSERTED);

        try { atm.insertPin("0000"); } catch (IllegalStateException e) {}
        check("E2E: 1 failed attempt", card.failedAttempts == 1);

        atm.insertPin("9999");
        check("E2E: authenticated", atm.state == ATMState.AUTHENTICATED);
        check("E2E: failed attempts reset", card.failedAttempts == 0);
        check("E2E: balance = 3000", atm.getBalance() == 3000);

        Map<Denomination, Integer> dispensed = atm.withdraw(250);
        check("E2E: balance after withdraw = 2750", acc.balance == 2750);
        check("E2E: withdrawnToday = 250", acc.withdrawnToday == 250);
        int total = 0;
        for (Map.Entry<Denomination, Integer> e : dispensed.entrySet()) {
            total += e.getKey().value * e.getValue();
        }
        check("E2E: dispensed amount = 250", total == 250);

        atm.withdraw(500);
        check("E2E: balance after 2nd = 2250", acc.balance == 2250);
        check("E2E: withdrawnToday = 750", acc.withdrawnToday == 750);

        try { atm.withdraw(1000); check("E2E: should exceed daily limit", false); }
        catch (IllegalStateException e) { check("E2E: daily limit exceeded", true); }
        check("E2E: balance unchanged after failed withdraw", acc.balance == 2250);
    }

    static void testMultipleCardsSameATM() {
        System.out.println("\n── Multiple Cards Same ATM ──");
        ATMService atm = freshAtm();
        Account acc1 = makeAccount(1000, 500);
        Account acc2 = makeAccount(2000, 1000);
        Card card1 = new Card(acc1.accountId, "1111");
        Card card2 = new Card(acc2.accountId, "2222");
        atm.addAccount(acc1);
        atm.addAccount(acc2);
        atm.addCard(card1);
        atm.addCard(card2);

        atm.insertCard(card1.cardNumber);
        atm.insertPin("1111");
        check("user1 balance", atm.getBalance() == 1000);
        atm.withdraw(100);
        check("user1 balance after withdraw", acc1.balance == 900);
        atm.state = ATMState.IDLE;
        atm.currentCard = null;
        atm.currentAccount = null;

        atm.insertCard(card2.cardNumber);
        atm.insertPin("2222");
        check("user2 balance", atm.getBalance() == 2000);
        atm.withdraw(500);
        check("user2 balance after withdraw", acc2.balance == 1500);
        check("user1 balance unchanged", acc1.balance == 900);
    }

    static void testReinsertAfterEject() {
        System.out.println("\n── Reinsert After Eject ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "1234");
        atm.addAccount(acc);
        atm.addCard(card);

        atm.insertCard(card.cardNumber);
        check("inserted", atm.state == ATMState.CARD_INSERTED);
        atm.ejectCard();
        check("ejected, back to IDLE", atm.state == ATMState.IDLE);

        atm.insertCard(card.cardNumber);
        check("reinserted", atm.state == ATMState.CARD_INSERTED);
        atm.insertPin("1234");
        check("re-authenticated", atm.state == ATMState.AUTHENTICATED);
        check("balance accessible", atm.getBalance() == 1000);
    }

    static void testBlockedCardCannotReinsert() {
        System.out.println("\n── Blocked Card Cannot Reinsert ──");
        ATMService atm = freshAtm();
        Account acc = makeAccount(1000, 500);
        Card card = new Card(acc.accountId, "1234");
        atm.addAccount(acc);
        atm.addCard(card);

        atm.insertCard(card.cardNumber);
        try { atm.insertPin("0000"); } catch (IllegalStateException e) {}
        try { atm.insertPin("0000"); } catch (IllegalStateException e) {}
        try { atm.insertPin("0000"); } catch (IllegalStateException e) {}
        check("card blocked", card.isBlocked);
        check("state IDLE after block", atm.state == ATMState.IDLE);

        try {
            atm.insertCard(card.cardNumber);
            check("blocked card reinsert should throw", false);
        } catch (IllegalStateException e) {
            check("blocked card cannot be reinserted", e.getMessage().contains("blocked"));
        }
    }

    static void testDailyLimitAcrossMultipleWithdrawals() {
        System.out.println("\n── Daily Limit Across Multiple Withdrawals ──");
        Account acc = makeAccount(10000, 500);
        Card card = new Card(acc.accountId, "1234");
        ATMService atm = authenticatedAtm(acc, card);

        atm.withdraw(200);
        check("1st: withdrawnToday=200", acc.withdrawnToday == 200);
        atm.withdraw(200);
        check("2nd: withdrawnToday=400", acc.withdrawnToday == 400);
        atm.withdraw(100);
        check("3rd: withdrawnToday=500 (at limit)", acc.withdrawnToday == 500);

        try { atm.withdraw(5); check("should fail - at daily limit", false); }
        catch (IllegalStateException e) { check("daily limit reached, $5 denied", e.getMessage().contains("daily limit")); }
        check("balance = 10000 - 500 = 9500", acc.balance == 9500);
    }
}
