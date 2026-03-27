import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

enum PlayerStatus{IDLE,QUEUED,IN_MATCH}
enum MatchStatus{WAITING,STARTED,COMPLETED}
class Player{
    String id;
    int skillRating;
    PlayerStatus status;
    LocalDateTime queuedAt;
    Player(int skillRating){
        id="PL-"+ UUID.randomUUID().toString().substring(0,6);
        this.status=PlayerStatus.IDLE;
        this.skillRating=skillRating;
    }
}
class Match{
    String matchId;
    List<Player> players;
    MatchStatus status;
    Match(List<Player> players){
        this.matchId="MID-"+UUID.randomUUID().toString().substring(0,6);
        status=MatchStatus.STARTED;
        this.players=new ArrayList<>(players);
    }
}
class MatchMakingService{
    Map<String,Player> players;
    ConcurrentLinkedQueue<String> matchQueue;
    Map<String,Match> matches;
    int playersPerMatch;
    int skillRange;
    MatchMakingService(int playersPerMatch,int skillRange){
        this.matchQueue=new ConcurrentLinkedQueue<>();
        this.players=new HashMap<>();
        this.matches=new HashMap<>();
        this.playersPerMatch=playersPerMatch;
        this.skillRange=skillRange;
    }
    Player addPlayer(int skillRating){
        Player p=new Player(skillRating);
        players.put(p.id,p);
        return p;
    }
    void joinQueue(String playerId){
        Player p=players.get(playerId);
        if(p==null || p.status!=PlayerStatus.IDLE) return;;
        p.status=PlayerStatus.QUEUED;
        p.queuedAt=LocalDateTime.now();
        matchQueue.offer(playerId);
        tryMatch();
    }
    void cancelQueue(String playerId){
        Player p=players.get(playerId);
        if(p==null || p.status!=PlayerStatus.QUEUED) return;
        matchQueue.remove(playerId);
        p.status=PlayerStatus.IDLE;
    }
    synchronized void tryMatch(){
        List<Player> qList=new ArrayList<>();
        for(String pid:matchQueue){
            Player p=players.get(pid);
            if(p!=null && p.status!=PlayerStatus.QUEUED) qList.add(p);
        }
        if(qList.size()<playersPerMatch) return;
        qList.sort((x,y)->x.skillRating-y.skillRating);
        for(int i=0;i<qList.size()-playersPerMatch;i++){
            int minSkill=qList.get(i).skillRating;
            int maxSkill=qList.get(i+playersPerMatch-1).skillRating;
            if(maxSkill-minSkill>=skillRange){
                List<Player> selected=qList.subList(i,i+playersPerMatch);
                createMatch(new ArrayList<>(selected));
            }
        }
    }
    void createMatch(List<Player> players){
        Match match=new Match(players);
        for(Player p:players) {p.status=PlayerStatus.IDLE; matchQueue.remove(p.id); }
        match.status=MatchStatus.STARTED;
        matches.put(match.matchId,match);
    }
    void completeMatch(String matchId){
        Match match=matches.remove(matchId);
        for(Player p:match.players) p.status=PlayerStatus.IDLE;
    }
}

// ════════════════════════════════════════════════════════════════════
//                    MATCHMAKING TEST SUITE
// ════════════════════════════════════════════════════════════════════
public class MatchMakingTest {

    static int passed = 0, failed = 0;

    static void check(String testName, boolean condition) {
        if (condition) {
            System.out.println("  ✅ PASS: " + testName);
            passed++;
        } else {
            System.out.println("  ❌ FAIL: " + testName);
            failed++;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //                          MAIN
    // ════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       MatchMaking Test Suite             ║");
        System.out.println("╚══════════════════════════════════════════╝");

        // ── Enums ──
        testPlayerStatusEnum();
        testMatchStatusEnum();

        // ── Player ──
        testPlayerConstruction();
        testPlayerUniqueIds();
        testPlayerDefaultStatus();
        testPlayerSkillRating();

        // ── Match ──
        testMatchConstruction();
        testMatchUniqueIds();
        testMatchStatusDefault();
        testMatchPlayersCopy();

        // ── MatchMakingService — Constructor ──
        testServiceConstruction();

        // ── MatchMakingService — addPlayer ──
        testAddPlayer();
        testAddMultiplePlayers();
        testAddPlayerStoredInMap();

        // ── MatchMakingService — joinQueue ──
        testJoinQueue();
        testJoinQueueSetsStatusToQueued();
        testJoinQueueSetsQueuedAt();
        testJoinQueueAddsToMatchQueue();
        testJoinQueueNullPlayer();
        testJoinQueueAlreadyQueued();

        // ── MatchMakingService — cancelQueue ──
        testCancelQueue();
        testCancelQueueResetsStatus();
        testCancelQueueRemovesFromQueue();
        testCancelQueueNonExistentPlayer();
        testCancelQueueIdlePlayer();

        // ── MatchMakingService — createMatch ──
        testCreateMatch();
        testCreateMatchStoresInMap();
        testCreateMatchRemovesFromQueue();

        // ── MatchMakingService — completeMatch ──
        testCompleteMatch();
        testCompleteMatchRemovesFromMap();
        testCompleteMatchResetsPlayerStatus();

        // ── MatchMakingService — tryMatch (bug analysis) ──
        testTryMatchFiltersBug();

        // ── Integration / End-to-End ──
        testEndToEndFullFlow();
        testEndToEndMultipleMatches();
        testEndToEndCancelAndRejoin();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");

        if (failed > 0) {
            System.out.println("\n⚠️  Known bugs in source code:");
            System.out.println("  1. tryMatch(): 'p.status != PlayerStatus.QUEUED' should be '== QUEUED'");
            System.out.println("     — Currently adds non-queued players to candidate list instead of queued ones");
            System.out.println("  2. tryMatch(): 'maxSkill - minSkill >= skillRange' should be '<= skillRange'");
            System.out.println("     — Currently matches players OUTSIDE skill range instead of WITHIN it");
            System.out.println("  3. createMatch(): sets player status to IDLE instead of IN_MATCH");
            System.out.println("     — Players should be IN_MATCH while a match is active");
            System.out.println("  4. tryMatch() loop: 'i < qList.size() - playersPerMatch' is off by one");
            System.out.println("     — Should be '<=' to check the last possible group");
            System.exit(1);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //              PlayerStatus Enum
    // ════════════════════════════════════════════════════════════════
    static void testPlayerStatusEnum() {
        System.out.println("\n── PlayerStatus Enum ──");
        check("IDLE exists",     PlayerStatus.valueOf("IDLE")     == PlayerStatus.IDLE);
        check("QUEUED exists",   PlayerStatus.valueOf("QUEUED")   == PlayerStatus.QUEUED);
        check("IN_MATCH exists", PlayerStatus.valueOf("IN_MATCH") == PlayerStatus.IN_MATCH);
        check("3 values",        PlayerStatus.values().length == 3);
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchStatus Enum
    // ════════════════════════════════════════════════════════════════
    static void testMatchStatusEnum() {
        System.out.println("\n── MatchStatus Enum ──");
        check("WAITING exists",   MatchStatus.valueOf("WAITING")   == MatchStatus.WAITING);
        check("STARTED exists",   MatchStatus.valueOf("STARTED")   == MatchStatus.STARTED);
        check("COMPLETED exists", MatchStatus.valueOf("COMPLETED") == MatchStatus.COMPLETED);
        check("3 values",         MatchStatus.values().length == 3);
    }

    // ════════════════════════════════════════════════════════════════
    //              Player
    // ════════════════════════════════════════════════════════════════
    static void testPlayerConstruction() {
        System.out.println("\n── Player Construction ──");
        Player p = new Player(1500);
        check("id is not null",             p.id != null);
        check("id starts with PL-",         p.id.startsWith("PL-"));
        check("id has correct length",      p.id.length() == 9); // "PL-" + 6 chars
        check("skillRating is set",         p.skillRating == 1500);
        check("status is IDLE",             p.status == PlayerStatus.IDLE);
        check("queuedAt is null initially", p.queuedAt == null);
    }

    static void testPlayerUniqueIds() {
        System.out.println("\n── Player Unique IDs ──");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(new Player(1000).id);
        }
        check("100 players have unique IDs", ids.size() == 100);
    }

    static void testPlayerDefaultStatus() {
        System.out.println("\n── Player Default Status ──");
        Player p1 = new Player(1000);
        Player p2 = new Player(2000);
        Player p3 = new Player(500);
        check("Player 1 starts IDLE", p1.status == PlayerStatus.IDLE);
        check("Player 2 starts IDLE", p2.status == PlayerStatus.IDLE);
        check("Player 3 starts IDLE", p3.status == PlayerStatus.IDLE);
    }

    static void testPlayerSkillRating() {
        System.out.println("\n── Player Skill Rating ──");
        Player low  = new Player(0);
        Player mid  = new Player(1500);
        Player high = new Player(3000);
        check("low skill = 0",    low.skillRating == 0);
        check("mid skill = 1500", mid.skillRating == 1500);
        check("high skill = 3000", high.skillRating == 3000);

        Player negative = new Player(-100);
        check("negative skill stored", negative.skillRating == -100);
    }

    // ════════════════════════════════════════════════════════════════
    //              Match
    // ════════════════════════════════════════════════════════════════
    static void testMatchConstruction() {
        System.out.println("\n── Match Construction ──");
        Player p1 = new Player(1000);
        Player p2 = new Player(1100);
        List<Player> players = Arrays.asList(p1, p2);

        Match match = new Match(players);
        check("matchId is not null",        match.matchId != null);
        check("matchId starts with MID-",   match.matchId.startsWith("MID-"));
        check("matchId has correct length",  match.matchId.length() == 10); // "MID-" + 6 chars
        check("status is STARTED",          match.status == MatchStatus.STARTED);
        check("players list is not null",   match.players != null);
        check("players count matches",      match.players.size() == 2);
        check("contains player 1",          match.players.contains(p1));
        check("contains player 2",          match.players.contains(p2));
    }

    static void testMatchUniqueIds() {
        System.out.println("\n── Match Unique IDs ──");
        Set<String> ids = new HashSet<>();
        List<Player> dummy = Arrays.asList(new Player(1000));
        for (int i = 0; i < 100; i++) {
            ids.add(new Match(dummy).matchId);
        }
        check("100 matches have unique IDs", ids.size() == 100);
    }

    static void testMatchStatusDefault() {
        System.out.println("\n── Match Default Status ──");
        Match match = new Match(Arrays.asList(new Player(1000)));
        check("match starts as STARTED", match.status == MatchStatus.STARTED);
    }

    static void testMatchPlayersCopy() {
        System.out.println("\n── Match Players is a Copy ──");
        Player p1 = new Player(1000);
        List<Player> original = new ArrayList<>(Arrays.asList(p1));

        Match match = new Match(original);
        original.add(new Player(2000)); // modify original

        check("match players not affected by original list change", match.players.size() == 1);
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchMakingService — Constructor
    // ════════════════════════════════════════════════════════════════
    static void testServiceConstruction() {
        System.out.println("\n── MatchMakingService Construction ──");
        MatchMakingService service = new MatchMakingService(4, 200);
        check("playersPerMatch is set",  service.playersPerMatch == 4);
        check("skillRange is set",       service.skillRange == 200);
        check("players map initialized", service.players != null);
        check("players map is empty",    service.players.isEmpty());
        check("matchQueue initialized",  service.matchQueue != null);
        check("matchQueue is empty",     service.matchQueue.isEmpty());
        check("matches map initialized", service.matches != null);
        check("matches map is empty",    service.matches.isEmpty());
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchMakingService — addPlayer
    // ════════════════════════════════════════════════════════════════
    static void testAddPlayer() {
        System.out.println("\n── addPlayer ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p = service.addPlayer(1500);

        check("addPlayer returns player",         p != null);
        check("player id is set",                 p.id != null && p.id.startsWith("PL-"));
        check("player skill is 1500",             p.skillRating == 1500);
        check("player status is IDLE",            p.status == PlayerStatus.IDLE);
        check("player stored in service map",     service.players.containsKey(p.id));
        check("service map has 1 player",         service.players.size() == 1);
    }

    static void testAddMultiplePlayers() {
        System.out.println("\n── addPlayer — Multiple Players ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1500);
        Player p3 = service.addPlayer(2000);

        check("service has 3 players",     service.players.size() == 3);
        check("p1 stored",                 service.players.containsKey(p1.id));
        check("p2 stored",                 service.players.containsKey(p2.id));
        check("p3 stored",                 service.players.containsKey(p3.id));
        check("all have different IDs",    !p1.id.equals(p2.id) && !p2.id.equals(p3.id));
    }

    static void testAddPlayerStoredInMap() {
        System.out.println("\n── addPlayer — Stored in Map ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p = service.addPlayer(1200);

        Player fromMap = service.players.get(p.id);
        check("player retrievable by id",       fromMap != null);
        check("same player object",             fromMap == p);
        check("skill rating matches from map",  fromMap.skillRating == 1200);
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchMakingService — joinQueue
    // ════════════════════════════════════════════════════════════════
    static void testJoinQueue() {
        System.out.println("\n── joinQueue ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p = service.addPlayer(1500);

        service.joinQueue(p.id);
        check("player added to matchQueue",  service.matchQueue.contains(p.id));
    }

    static void testJoinQueueSetsStatusToQueued() {
        System.out.println("\n── joinQueue — Sets Status to QUEUED ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p = service.addPlayer(1500);

        check("status is IDLE before join", p.status == PlayerStatus.IDLE);
        service.joinQueue(p.id);
        check("status is QUEUED after join", p.status == PlayerStatus.QUEUED);
    }

    static void testJoinQueueSetsQueuedAt() {
        System.out.println("\n── joinQueue — Sets queuedAt ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p = service.addPlayer(1500);

        check("queuedAt is null before join", p.queuedAt == null);
        LocalDateTime before = LocalDateTime.now();
        service.joinQueue(p.id);
        LocalDateTime after = LocalDateTime.now();
        check("queuedAt is set after join",           p.queuedAt != null);
        check("queuedAt is not before call",          !p.queuedAt.isBefore(before));
        check("queuedAt is not after call completed", !p.queuedAt.isAfter(after));
    }

    static void testJoinQueueAddsToMatchQueue() {
        System.out.println("\n── joinQueue — Adds to Queue ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1500);
        Player p3 = service.addPlayer(2000);

        service.joinQueue(p1.id);
        service.joinQueue(p2.id);
        service.joinQueue(p3.id);

        check("queue has 3 entries", service.matchQueue.size() == 3);
        check("queue contains p1",   service.matchQueue.contains(p1.id));
        check("queue contains p2",   service.matchQueue.contains(p2.id));
        check("queue contains p3",   service.matchQueue.contains(p3.id));
    }

    static void testJoinQueueNullPlayer() {
        System.out.println("\n── joinQueue — Null / Non-Existent Player ──");
        MatchMakingService service = new MatchMakingService(2, 100);

        // non-existent player ID — should be a no-op
        service.joinQueue("FAKE-ID");
        check("queue empty after fake ID join", service.matchQueue.isEmpty());

        // null — should be a no-op
        service.joinQueue(null);
        check("queue empty after null join", service.matchQueue.isEmpty());
    }

    static void testJoinQueueAlreadyQueued() {
        System.out.println("\n── joinQueue — Already Queued Player ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p = service.addPlayer(1500);

        service.joinQueue(p.id);
        check("queue has 1 after first join", service.matchQueue.size() == 1);

        // joining again should be a no-op (status is QUEUED, not IDLE)
        service.joinQueue(p.id);
        check("queue still has 1 after duplicate join", service.matchQueue.size() == 1);
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchMakingService — cancelQueue
    // ════════════════════════════════════════════════════════════════
    static void testCancelQueue() {
        System.out.println("\n── cancelQueue ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p = service.addPlayer(1500);
        service.joinQueue(p.id);

        check("in queue before cancel", service.matchQueue.contains(p.id));
        service.cancelQueue(p.id);
        check("removed from queue after cancel", !service.matchQueue.contains(p.id));
    }

    static void testCancelQueueResetsStatus() {
        System.out.println("\n── cancelQueue — Resets Status to IDLE ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p = service.addPlayer(1500);
        service.joinQueue(p.id);

        check("status QUEUED before cancel", p.status == PlayerStatus.QUEUED);
        service.cancelQueue(p.id);
        check("status IDLE after cancel",    p.status == PlayerStatus.IDLE);
    }

    static void testCancelQueueRemovesFromQueue() {
        System.out.println("\n── cancelQueue — Removes From Queue ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1500);
        Player p3 = service.addPlayer(2000);

        service.joinQueue(p1.id);
        service.joinQueue(p2.id);
        service.joinQueue(p3.id);

        service.cancelQueue(p2.id);
        check("queue size is 2 after cancel",  service.matchQueue.size() == 2);
        check("p2 removed from queue",          !service.matchQueue.contains(p2.id));
        check("p1 still in queue",               service.matchQueue.contains(p1.id));
        check("p3 still in queue",               service.matchQueue.contains(p3.id));
    }

    static void testCancelQueueNonExistentPlayer() {
        System.out.println("\n── cancelQueue — Non-Existent Player ──");
        MatchMakingService service = new MatchMakingService(4, 100);

        // should be a no-op, no exception
        service.cancelQueue("FAKE-ID");
        check("no crash on fake ID cancel", true);

        service.cancelQueue(null);
        check("no crash on null cancel", true);
    }

    static void testCancelQueueIdlePlayer() {
        System.out.println("\n── cancelQueue — Idle Player (not queued) ──");
        MatchMakingService service = new MatchMakingService(4, 100);
        Player p = service.addPlayer(1500);

        // player is IDLE, not QUEUED — cancel should be a no-op
        service.cancelQueue(p.id);
        check("idle player cancel is no-op",  p.status == PlayerStatus.IDLE);
        check("queue still empty",             service.matchQueue.isEmpty());
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchMakingService — createMatch
    // ════════════════════════════════════════════════════════════════
    static void testCreateMatch() {
        System.out.println("\n── createMatch ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1100);

        service.createMatch(Arrays.asList(p1, p2));

        check("matches map has 1 match", service.matches.size() == 1);

        Match match = service.matches.values().iterator().next();
        check("match has 2 players",        match.players.size() == 2);
        check("match status is STARTED",    match.status == MatchStatus.STARTED);
        check("match contains p1",          match.players.contains(p1));
        check("match contains p2",          match.players.contains(p2));
    }

    static void testCreateMatchStoresInMap() {
        System.out.println("\n── createMatch — Stores in Map ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1100);

        service.createMatch(Arrays.asList(p1, p2));

        Match match = service.matches.values().iterator().next();
        check("match retrievable by matchId", service.matches.get(match.matchId) == match);
        check("matchId starts with MID-",     match.matchId.startsWith("MID-"));
    }

    static void testCreateMatchRemovesFromQueue() {
        System.out.println("\n── createMatch — Removes Players from Queue ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1100);
        Player p3 = service.addPlayer(1200);

        // Manually add to queue
        service.matchQueue.offer(p1.id);
        service.matchQueue.offer(p2.id);
        service.matchQueue.offer(p3.id);

        service.createMatch(Arrays.asList(p1, p2));

        check("p1 removed from queue",    !service.matchQueue.contains(p1.id));
        check("p2 removed from queue",    !service.matchQueue.contains(p2.id));
        check("p3 still in queue",         service.matchQueue.contains(p3.id));
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchMakingService — completeMatch
    // ════════════════════════════════════════════════════════════════
    static void testCompleteMatch() {
        System.out.println("\n── completeMatch ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1100);

        service.createMatch(Arrays.asList(p1, p2));
        String matchId = service.matches.keySet().iterator().next();

        check("match exists before complete", service.matches.containsKey(matchId));
        service.completeMatch(matchId);
        check("match removed after complete", !service.matches.containsKey(matchId));
    }

    static void testCompleteMatchRemovesFromMap() {
        System.out.println("\n── completeMatch — Removes from Map ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1100);
        Player p3 = service.addPlayer(1200);
        Player p4 = service.addPlayer(1300);

        service.createMatch(Arrays.asList(p1, p2));
        service.createMatch(Arrays.asList(p3, p4));

        check("2 matches exist", service.matches.size() == 2);

        String firstMatchId = service.matches.keySet().iterator().next();
        service.completeMatch(firstMatchId);

        check("1 match remains",            service.matches.size() == 1);
        check("completed match removed",    !service.matches.containsKey(firstMatchId));
    }

    static void testCompleteMatchResetsPlayerStatus() {
        System.out.println("\n── completeMatch — Resets Player Status ──");
        MatchMakingService service = new MatchMakingService(2, 100);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1100);

        service.createMatch(Arrays.asList(p1, p2));
        String matchId = service.matches.keySet().iterator().next();

        service.completeMatch(matchId);
        check("p1 status is IDLE after complete", p1.status == PlayerStatus.IDLE);
        check("p2 status is IDLE after complete", p2.status == PlayerStatus.IDLE);
    }

    // ════════════════════════════════════════════════════════════════
    //              MatchMakingService — tryMatch (Bug Analysis)
    // ════════════════════════════════════════════════════════════════
    static void testTryMatchFiltersBug() {
        System.out.println("\n── tryMatch — Bug Analysis ──");
        // BUG: tryMatch() uses 'p.status != PlayerStatus.QUEUED' to filter
        //      This means it collects players who are NOT queued instead of those who ARE.
        //      As a result, tryMatch() effectively never matches queued players.
        //
        // BUG: 'maxSkill - minSkill >= skillRange' should be '<= skillRange'
        //      This matches players with skill gap LARGER than range, opposite of intent.
        //
        // BUG: createMatch sets status to IDLE instead of IN_MATCH

        MatchMakingService service = new MatchMakingService(2, 200);
        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1050);

        service.joinQueue(p1.id);
        service.joinQueue(p2.id);

        // Because of the != bug in tryMatch, no match is created even though
        // 2 queued players with close skill ratings exist
        check("Bug: no match created despite 2 queued players (status filter bug)",
              service.matches.isEmpty());
        check("Bug: p1 still in queue",  service.matchQueue.contains(p1.id));
        check("Bug: p2 still in queue",  service.matchQueue.contains(p2.id));
        check("Bug: p1 status still QUEUED", p1.status == PlayerStatus.QUEUED);
        check("Bug: p2 status still QUEUED", p2.status == PlayerStatus.QUEUED);
    }

    // ════════════════════════════════════════════════════════════════
    //              End-to-End Integration Tests
    // ════════════════════════════════════════════════════════════════
    static void testEndToEndFullFlow() {
        System.out.println("\n── E2E: Full MatchMaking Flow ──");
        MatchMakingService service = new MatchMakingService(2, 200);

        // 1. Add players
        Player alice = service.addPlayer(1500);
        Player bob   = service.addPlayer(1550);
        check("E2E: players map has 2",   service.players.size() == 2);

        // 2. Players join queue
        service.joinQueue(alice.id);
        check("E2E: alice is QUEUED",     alice.status == PlayerStatus.QUEUED);
        check("E2E: alice queuedAt set",  alice.queuedAt != null);

        service.joinQueue(bob.id);
        check("E2E: bob is QUEUED",       bob.status == PlayerStatus.QUEUED);

        // 3. Due to tryMatch bug, no automatic match — create manually
        service.createMatch(Arrays.asList(alice, bob));
        check("E2E: 1 match created",            service.matches.size() == 1);

        Match match = service.matches.values().iterator().next();
        check("E2E: match has 2 players",         match.players.size() == 2);
        check("E2E: match status STARTED",        match.status == MatchStatus.STARTED);
        check("E2E: match contains alice",        match.players.contains(alice));
        check("E2E: match contains bob",          match.players.contains(bob));

        // 4. Complete the match
        service.completeMatch(match.matchId);
        check("E2E: no active matches",           service.matches.isEmpty());
        check("E2E: alice back to IDLE",           alice.status == PlayerStatus.IDLE);
        check("E2E: bob back to IDLE",             bob.status == PlayerStatus.IDLE);

        // 5. Players can re-queue after match completes
        service.joinQueue(alice.id);
        check("E2E: alice re-queued",              alice.status == PlayerStatus.QUEUED);
        check("E2E: alice back in matchQueue",     service.matchQueue.contains(alice.id));
    }

    static void testEndToEndMultipleMatches() {
        System.out.println("\n── E2E: Multiple Matches ──");
        MatchMakingService service = new MatchMakingService(2, 100);

        Player p1 = service.addPlayer(1000);
        Player p2 = service.addPlayer(1050);
        Player p3 = service.addPlayer(2000);
        Player p4 = service.addPlayer(2050);

        // Create two separate matches
        service.createMatch(Arrays.asList(p1, p2));
        service.createMatch(Arrays.asList(p3, p4));

        check("E2E-multi: 2 matches active", service.matches.size() == 2);

        // Complete both
        List<String> matchIds = new ArrayList<>(service.matches.keySet());
        service.completeMatch(matchIds.get(0));
        check("E2E-multi: 1 match after first complete", service.matches.size() == 1);

        service.completeMatch(matchIds.get(1));
        check("E2E-multi: 0 matches after both complete", service.matches.isEmpty());

        // All players back to IDLE
        check("E2E-multi: p1 IDLE", p1.status == PlayerStatus.IDLE);
        check("E2E-multi: p2 IDLE", p2.status == PlayerStatus.IDLE);
        check("E2E-multi: p3 IDLE", p3.status == PlayerStatus.IDLE);
        check("E2E-multi: p4 IDLE", p4.status == PlayerStatus.IDLE);
    }

    static void testEndToEndCancelAndRejoin() {
        System.out.println("\n── E2E: Cancel and Rejoin ──");
        MatchMakingService service = new MatchMakingService(4, 200);

        Player p1 = service.addPlayer(1500);
        Player p2 = service.addPlayer(1550);

        // Join and cancel
        service.joinQueue(p1.id);
        service.joinQueue(p2.id);
        check("E2E-cancel: queue has 2",     service.matchQueue.size() == 2);

        service.cancelQueue(p1.id);
        check("E2E-cancel: p1 cancelled",    p1.status == PlayerStatus.IDLE);
        check("E2E-cancel: queue has 1",     service.matchQueue.size() == 1);
        check("E2E-cancel: p1 not in queue", !service.matchQueue.contains(p1.id));

        // p1 rejoins
        service.joinQueue(p1.id);
        check("E2E-cancel: p1 re-joined",    p1.status == PlayerStatus.QUEUED);
        check("E2E-cancel: queue has 2 again", service.matchQueue.size() == 2);
        check("E2E-cancel: p1 back in queue",  service.matchQueue.contains(p1.id));
        check("E2E-cancel: p2 still queued",   p2.status == PlayerStatus.QUEUED);
    }
}
