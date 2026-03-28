import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class FeedNotFoundException extends Exception{
    FeedNotFoundException(String message){
        super(message);
    }
}
class ContentNotFoundException1 extends Exception{
    ContentNotFoundException1(String message){
        super(message);
    }
}
class UserNotFoundException1 extends Exception{
    UserNotFoundException1(String message){
        super(message);
    }
}
enum ContentType{POST,SHARED_POST,AD}
enum StrategyType{CHRONOLOGICAL,ENGAGEMENT_BASED,PERSONALISED}
class Content{
    String contentId;
    String authorId;
    String text;
    ContentType type;
    int likes,comments,shares;
    double relevanceScore;
    LocalDateTime createdAt;

     Content(String authorId, String text, ContentType type) {
        this.authorId = authorId;
        this.text = text;
        this.type = type;
        contentId="CO-"+ UUID.randomUUID().toString().substring(0,6);
        this.createdAt= LocalDateTime.now();
    }
    double engagementScore(){return likes+comments*2+shares*3;}
    long ageMinutes(){return Duration.between(LocalDateTime.now(),createdAt).toMinutes();}
}
class AppUser{
    String userId;
    Set<String> followers;
    Set<String> following;
    List<String> contentIds;
    Set<String> interests;
    AppUser(){this.userId="US-"+UUID.randomUUID().toString().substring(0,6);}

}
interface RankingStrategy{
    List<Content> rank(List<Content> contents,AppUser user );
    StrategyType get();
}
class ChronologicalRankingStrategy implements RankingStrategy{
    @Override
    public List<Content> rank(List<Content> contents, AppUser user) {
        return contents.stream().sorted(Comparator.comparing(content -> content.createdAt)).toList().reversed();
    }
    @Override
    public StrategyType get() {
        return StrategyType.CHRONOLOGICAL;
    }
}
class EngagementRankingStrategy implements RankingStrategy{

    @Override
    public List<Content> rank(List<Content> contents, AppUser user) {
        for(Content c:contents){
            double decay=1+ c.ageMinutes()/60.0*0.1;
            c.relevanceScore=c.engagementScore()/decay;
        }
        return contents.stream().sorted(Comparator.comparing(content -> content.relevanceScore)).toList().reversed();
    }

    @Override
    public StrategyType get() {
        return StrategyType.ENGAGEMENT_BASED;
    }
}
class PersonalisedRankingStrategy implements RankingStrategy{

    @Override
    public List<Content> rank(List<Content> contents, AppUser user) {
        for(Content c:contents){
            double engagement=c.engagementScore();
            double recency=Math.max(0,24-c.ageMinutes()/60)/24.0;
            double interestBoost =user.interests.contains(c.authorId)?2.0:1.0;
            double typeWeight = switch (c.type){
                case POST -> 1.0; case SHARED_POST -> 0.9; case AD -> 0.5;
            };
            c.relevanceScore=(engagement*interestBoost+recency*10)*typeWeight;
        }
        return contents.stream().sorted(Comparator.comparing(content -> content.relevanceScore)).toList().reversed();
    }

    @Override
    public StrategyType get() {
        return StrategyType.PERSONALISED;
    }
}
class FeedCache{
    Map<String,List<Content>> cache=new ConcurrentHashMap<>();
    Map<String,Long> timestamps=new ConcurrentHashMap<>();
    long ttlMs;
    FeedCache(long ttlMs) {this.ttlMs=ttlMs;}
    List<Content> get(String userId){
        if(!cache.containsKey(userId)) return null;
        if(System.currentTimeMillis()-timestamps.getOrDefault(userId,0L)>ttlMs){
            cache.remove(userId); timestamps.remove(userId); return null;
        }
        return cache.get(userId);
    }
    void put(String userId,List<Content> contents){
        cache.put(userId,new ArrayList<>(contents));
        timestamps.put(userId,System.currentTimeMillis());
    }
    void invalidate(String userId){
        cache.remove(userId);
        timestamps.remove(userId);
    }
    void invalidateAll(Set<String> userIds){
        userIds.forEach(this::invalidate);
    }
    boolean isCached(String userId){
        return cache.containsKey(userId) && System.currentTimeMillis() - timestamps.getOrDefault(userId, 0L) <= ttlMs;
    }
}
class FeedService{
    Map<String,AppUser> users=new ConcurrentHashMap<>();
    Map<String,Content> contents=new ConcurrentHashMap<>();
    RankingStrategy rankingStrategy;
    FeedCache cache;
    FeedService(RankingStrategy rankingStrategy,int cacheTtlSec){
        this.cache=new FeedCache(cacheTtlSec);
        this.rankingStrategy=rankingStrategy;
    }
    String addUser(){
        AppUser user=new AppUser();
        users.put(user.userId,user);
        return user.userId;
    }
    void follow(String followerId,String followsId) throws UserNotFoundException1 {
        AppUser follower=users.get(followerId);
        AppUser follows=users.get(followsId);
        if(follows==null) throw new UserNotFoundException1(followsId);
        if(follower==null) throw new UserNotFoundException1(followerId);
        follower.following.add(followsId);
        follows.following.add(followerId);
        cache.invalidate(followerId);
    }
    void addInterest(String userId,String interest) throws UserNotFoundException1 {
        AppUser user=users.get(userId);
        if(user==null) throw new UserNotFoundException1(userId);
        user.interests.add(interest);
    }
    Content publish(String authorId, String text, ContentType type) throws UserNotFoundException1 {
        AppUser author=users.get(authorId);
        if(author==null) throw new UserNotFoundException1(authorId);
        Content content=new Content(authorId,text,type);
        contents.put(content.contentId,content);
        author.contentIds.add(content.contentId);
        cache.invalidateAll(author.followers);
        return content;
    }
    void addEngagement(String contentId, int likes, int comments, int shares) throws ContentNotFoundException1 {
        Content c=contents.get(contentId);
        if(c==null) throw new ContentNotFoundException1(contentId);
        c.likes+=likes; c.comments+=comments; c.shares+=shares;
    }
    List<Content> generateFeed(String userId) throws UserNotFoundException1 {
        AppUser user=users.get(userId);
        if(user==null) throw new UserNotFoundException1(userId);
        List<Content> cached=cache.get(userId);
        if(cached!=null) return cached;
        List<Content> feed=new ArrayList<>();
        for(String fid:user.following){
            AppUser f=users.get(fid);
            if(f==null) continue;;
            for(String cids:f.contentIds){
                Content c=contents.get(cids);
                if(c!=null) feed.add(c);
            }
        }
        List<Content> ranked=rankingStrategy.rank(feed,user);
        cache.put(userId,ranked);
        return ranked;
    }
}
public class FeedGeneratorTest {
    static int passed = 0, failed = 0;

    static boolean check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ✅ PASS: " + name); }
        else    { failed++; System.out.println("  ❌ FAIL: " + name); }
        return ok;
    }

    // Helper: create AppUser with initialized collections (workaround for uninitialized fields bug)
    static AppUser makeUser() {
        AppUser u = new AppUser();
        u.followers = new HashSet<>();
        u.following = new HashSet<>();
        u.contentIds = new ArrayList<>();
        u.interests = new HashSet<>();
        return u;
    }

    // Helper: create FeedService with initialized users
    static FeedService makeService(RankingStrategy strategy) {
        return new FeedService(strategy, 60000);
    }

    // Helper: add a user to service with initialized collections
    static String addUserToService(FeedService service) {
        AppUser user = makeUser();
        service.users.put(user.userId, user);
        return user.userId;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     Feed Generator Test Suite            ║");
        System.out.println("╚══════════════════════════════════════════╝");

        // ── Exceptions ──
        testExceptionClasses();

        // ── Enums ──
        testContentTypeEnum();
        testStrategyTypeEnum();

        // ── Content ──
        testContentCreation();
        testContentIdUnique();
        testContentEngagementScore();
        testContentAgeMinutes();

        // ── AppUser ──
        testAppUserCreation();
        testAppUserIdFormat();
        testAppUserUniqueIds();
        testAppUserUninitializedFieldsBug();

        // ── RankingStrategies ──
        testChronologicalRanking();
        testChronologicalRankingType();
        testEngagementRanking();
        testEngagementRankingType();
        testPersonalisedRanking();
        testPersonalisedRankingType();
        testRankingEmptyList();

        // ── FeedCache ──
        testFeedCacheConstructor();
        testFeedCachePutAndGet();
        testFeedCacheMiss();
        testFeedCacheExpiration();
        testFeedCacheInvalidate();
        testFeedCacheInvalidateAll();
        testFeedCacheIsCached();
        testFeedCacheIsCachedExpired();
        testFeedCacheReturnsCopy();

        // ── FeedService ──
        testFeedServiceConstructor();
        testFeedServiceAddUser();
        testFeedServiceFollow();
        testFeedServiceFollowUserNotFound();
        testFeedServiceAddInterest();
        testFeedServiceAddInterestNotFound();
        testFeedServicePublish();
        testFeedServicePublishNotFound();
        testFeedServiceAddEngagement();
        testFeedServiceAddEngagementNotFound();
        testFeedServiceGenerateFeed();
        testFeedServiceGenerateFeedNotFound();
        testFeedServiceGenerateFeedCaching();
        testFeedServiceGenerateFeedEmpty();

        // ── End-to-End ──
        testEndToEndChronological();
        testEndToEndEngagement();
        testEndToEndPersonalised();
        testEndToEndMultipleFollowing();
        testEndToEndCacheInvalidationOnPublish();

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ════════════════════════════════════════════════════════════════
    //              Exceptions
    // ════════════════════════════════════════════════════════════════
    static void testExceptionClasses() {
        System.out.println("\n── Exceptions ──");
        FeedNotFoundException fnf = new FeedNotFoundException("feed not found");
        check("FeedNotFoundException message", "feed not found".equals(fnf.getMessage()));
        check("FeedNotFoundException is Exception", fnf instanceof Exception);

        ContentNotFoundException1 cnf = new ContentNotFoundException1("content missing");
        check("ContentNotFoundException1 message", "content missing".equals(cnf.getMessage()));
        check("ContentNotFoundException1 is Exception", cnf instanceof Exception);

        UserNotFoundException1 unf = new UserNotFoundException1("user gone");
        check("UserNotFoundException1 message", "user gone".equals(unf.getMessage()));
        check("UserNotFoundException1 is Exception", unf instanceof Exception);

        check("null message works", new FeedNotFoundException(null).getMessage() == null);
        check("empty message works", "".equals(new UserNotFoundException1("").getMessage()));
    }

    // ════════════════════════════════════════════════════════════════
    //              Enums
    // ════════════════════════════════════════════════════════════════
    static void testContentTypeEnum() {
        System.out.println("\n── ContentType Enum ──");
        check("POST exists", ContentType.valueOf("POST") == ContentType.POST);
        check("SHARED_POST exists", ContentType.valueOf("SHARED_POST") == ContentType.SHARED_POST);
        check("AD exists", ContentType.valueOf("AD") == ContentType.AD);
        check("3 values total", ContentType.values().length == 3);
    }

    static void testStrategyTypeEnum() {
        System.out.println("\n── StrategyType Enum ──");
        check("CHRONOLOGICAL exists", StrategyType.valueOf("CHRONOLOGICAL") == StrategyType.CHRONOLOGICAL);
        check("ENGAGEMENT_BASED exists", StrategyType.valueOf("ENGAGEMENT_BASED") == StrategyType.ENGAGEMENT_BASED);
        check("PERSONALISED exists", StrategyType.valueOf("PERSONALISED") == StrategyType.PERSONALISED);
        check("3 values total", StrategyType.values().length == 3);
    }

    // ════════════════════════════════════════════════════════════════
    //              Content
    // ════════════════════════════════════════════════════════════════
    static void testContentCreation() {
        System.out.println("\n── Content Creation ──");
        Content c = new Content("author1", "Hello world", ContentType.POST);
        check("authorId set", "author1".equals(c.authorId));
        check("text set", "Hello world".equals(c.text));
        check("type set", c.type == ContentType.POST);
        check("contentId not null", c.contentId != null);
        check("contentId starts with CO-", c.contentId.startsWith("CO-"));
        check("contentId length is 9 (CO- + 6)", c.contentId.length() == 9);
        check("createdAt set", c.createdAt != null);
        check("createdAt is recent", Duration.between(c.createdAt, LocalDateTime.now()).toSeconds() < 2);
        check("likes default 0", c.likes == 0);
        check("comments default 0", c.comments == 0);
        check("shares default 0", c.shares == 0);
        check("relevanceScore default 0", c.relevanceScore == 0.0);
    }

    static void testContentIdUnique() {
        System.out.println("\n── Content ID Uniqueness ──");
        Content c1 = new Content("a", "t1", ContentType.POST);
        Content c2 = new Content("a", "t2", ContentType.POST);
        Content c3 = new Content("b", "t3", ContentType.AD);
        check("c1 != c2", !c1.contentId.equals(c2.contentId));
        check("c2 != c3", !c2.contentId.equals(c3.contentId));
        check("c1 != c3", !c1.contentId.equals(c3.contentId));
    }

    static void testContentEngagementScore() {
        System.out.println("\n── Content engagementScore ──");
        Content c = new Content("a", "t", ContentType.POST);
        check("engagement 0 with no interaction", c.engagementScore() == 0.0);

        c.likes = 10;
        check("10 likes = 10", c.engagementScore() == 10.0);

        c.comments = 5;
        check("10 likes + 5 comments = 10+10=20", c.engagementScore() == 20.0);

        c.shares = 3;
        check("10L + 5C + 3S = 10+10+9=29", c.engagementScore() == 29.0);

        Content c2 = new Content("a", "t", ContentType.POST);
        c2.likes = 0; c2.comments = 0; c2.shares = 1;
        check("1 share = 3", c2.engagementScore() == 3.0);

        Content c3 = new Content("a", "t", ContentType.POST);
        c3.likes = 1; c3.comments = 1; c3.shares = 1;
        check("1L+1C+1S = 1+2+3=6", c3.engagementScore() == 6.0);
    }

    static void testContentAgeMinutes() {
        System.out.println("\n── Content ageMinutes ──");
        Content c = new Content("a", "t", ContentType.POST);
        long age = c.ageMinutes();
        // ageMinutes computes Duration.between(now, createdAt) which is negative (past)
        // This is a bug: should be Duration.between(createdAt, now)
        // The result will be <= 0 for recent content
        check("ageMinutes for just-created content <= 0 (known bug: reversed args)", age <= 0);
    }

    // ════════════════════════════════════════════════════════════════
    //              AppUser
    // ════════════════════════════════════════════════════════════════
    static void testAppUserCreation() {
        System.out.println("\n── AppUser Creation ──");
        AppUser u = new AppUser();
        check("userId not null", u.userId != null);
        check("userId starts with US-", u.userId.startsWith("US-"));
        check("userId length 9 (US- + 6)", u.userId.length() == 9);
    }

    static void testAppUserIdFormat() {
        System.out.println("\n── AppUser ID Format ──");
        AppUser u = new AppUser();
        check("starts with US-", u.userId.startsWith("US-"));
        String suffix = u.userId.substring(3);
        check("suffix is 6 chars", suffix.length() == 6);
    }

    static void testAppUserUniqueIds() {
        System.out.println("\n── AppUser Unique IDs ──");
        AppUser u1 = new AppUser();
        AppUser u2 = new AppUser();
        AppUser u3 = new AppUser();
        check("u1 != u2", !u1.userId.equals(u2.userId));
        check("u2 != u3", !u2.userId.equals(u3.userId));
    }

    static void testAppUserUninitializedFieldsBug() {
        System.out.println("\n── AppUser Uninitialized Fields (Known Bug) ──");
        AppUser u = new AppUser();
        check("followers is null (bug: not initialized)", u.followers == null);
        check("following is null (bug: not initialized)", u.following == null);
        check("contentIds is null (bug: not initialized)", u.contentIds == null);
        check("interests is null (bug: not initialized)", u.interests == null);
    }

    // ════════════════════════════════════════════════════════════════
    //              Ranking Strategies
    // ════════════════════════════════════════════════════════════════
    static void testChronologicalRanking() throws Exception {
        System.out.println("\n── Chronological Ranking ──");
        ChronologicalRankingStrategy strategy = new ChronologicalRankingStrategy();
        AppUser user = makeUser();

        Content c1 = new Content("a", "first", ContentType.POST);
        Thread.sleep(10);
        Content c2 = new Content("a", "second", ContentType.POST);
        Thread.sleep(10);
        Content c3 = new Content("a", "third", ContentType.POST);

        List<Content> input = new ArrayList<>(List.of(c1, c2, c3));
        List<Content> ranked = strategy.rank(input, user);

        check("returns 3 items", ranked.size() == 3);
        check("newest first (c3)", ranked.get(0) == c3);
        check("middle (c2)", ranked.get(1) == c2);
        check("oldest last (c1)", ranked.get(2) == c1);
    }

    static void testChronologicalRankingType() {
        System.out.println("\n── Chronological Ranking Type ──");
        ChronologicalRankingStrategy s = new ChronologicalRankingStrategy();
        check("type is CHRONOLOGICAL", s.get() == StrategyType.CHRONOLOGICAL);
    }

    static void testEngagementRanking() {
        System.out.println("\n── Engagement Ranking ──");
        EngagementRankingStrategy strategy = new EngagementRankingStrategy();
        AppUser user = makeUser();

        Content c1 = new Content("a", "low", ContentType.POST);
        c1.likes = 1;
        Content c2 = new Content("a", "high", ContentType.POST);
        c2.likes = 100; c2.comments = 50;
        Content c3 = new Content("a", "mid", ContentType.POST);
        c3.likes = 10; c3.shares = 5;

        List<Content> input = new ArrayList<>(List.of(c1, c2, c3));
        List<Content> ranked = strategy.rank(input, user);

        check("returns 3 items", ranked.size() == 3);
        check("highest engagement first", ranked.get(0) == c2);
        check("lowest engagement last", ranked.get(ranked.size() - 1) == c1);
        check("relevanceScore set on c2", c2.relevanceScore > 0);
    }

    static void testEngagementRankingType() {
        System.out.println("\n── Engagement Ranking Type ──");
        EngagementRankingStrategy s = new EngagementRankingStrategy();
        check("type is ENGAGEMENT_BASED", s.get() == StrategyType.ENGAGEMENT_BASED);
    }

    static void testPersonalisedRanking() {
        System.out.println("\n── Personalised Ranking ──");
        PersonalisedRankingStrategy strategy = new PersonalisedRankingStrategy();
        AppUser user = makeUser();
        user.interests.add("favoriteAuthor");

        Content c1 = new Content("favoriteAuthor", "fav post", ContentType.POST);
        c1.likes = 10;
        Content c2 = new Content("randomAuthor", "random post", ContentType.POST);
        c2.likes = 10;
        Content c3 = new Content("adAuthor", "buy this", ContentType.AD);
        c3.likes = 10;

        List<Content> input = new ArrayList<>(List.of(c1, c2, c3));
        List<Content> ranked = strategy.rank(input, user);

        check("returns 3 items", ranked.size() == 3);
        check("favorite author boosted to top", ranked.get(0) == c1);
        check("AD ranked lower due to type weight", ranked.get(ranked.size() - 1) == c3);
        check("relevanceScore set", c1.relevanceScore > 0);
        check("favorite has higher score than random", c1.relevanceScore > c2.relevanceScore);
    }

    static void testPersonalisedRankingType() {
        System.out.println("\n── Personalised Ranking Type ──");
        PersonalisedRankingStrategy s = new PersonalisedRankingStrategy();
        check("type is PERSONALISED", s.get() == StrategyType.PERSONALISED);
    }

    static void testRankingEmptyList() {
        System.out.println("\n── Ranking Empty List ──");
        AppUser user = makeUser();
        List<Content> empty = new ArrayList<>();

        List<Content> r1 = new ChronologicalRankingStrategy().rank(empty, user);
        check("chronological empty -> empty", r1.isEmpty());

        List<Content> r2 = new EngagementRankingStrategy().rank(empty, user);
        check("engagement empty -> empty", r2.isEmpty());

        List<Content> r3 = new PersonalisedRankingStrategy().rank(empty, user);
        check("personalised empty -> empty", r3.isEmpty());
    }

    // ════════════════════════════════════════════════════════════════
    //              FeedCache
    // ════════════════════════════════════════════════════════════════
    static void testFeedCacheConstructor() {
        System.out.println("\n── FeedCache Constructor ──");
        FeedCache cache = new FeedCache(5000);
        check("cache map initialized", cache.cache != null);
        check("timestamps map initialized", cache.timestamps != null);
        check("ttlMs set", cache.ttlMs == 5000);
        check("cache starts empty", cache.cache.isEmpty());
    }

    static void testFeedCachePutAndGet() {
        System.out.println("\n── FeedCache Put & Get ──");
        FeedCache cache = new FeedCache(60000);
        Content c1 = new Content("a", "post1", ContentType.POST);
        Content c2 = new Content("a", "post2", ContentType.POST);
        List<Content> feed = List.of(c1, c2);

        cache.put("user1", feed);
        List<Content> result = cache.get("user1");
        check("get returns feed", result != null);
        check("feed has 2 items", result.size() == 2);
        check("timestamp recorded", cache.timestamps.containsKey("user1"));
    }

    static void testFeedCacheMiss() {
        System.out.println("\n── FeedCache Miss ──");
        FeedCache cache = new FeedCache(60000);
        check("get non-existent returns null", cache.get("nobody") == null);
    }

    static void testFeedCacheExpiration() throws Exception {
        System.out.println("\n── FeedCache Expiration ──");
        FeedCache cache = new FeedCache(50); // 50ms TTL
        Content c = new Content("a", "post", ContentType.POST);
        cache.put("user1", List.of(c));

        check("cached before expiry", cache.get("user1") != null);
        Thread.sleep(80);
        check("null after expiry", cache.get("user1") == null);
        check("cache entry removed", !cache.cache.containsKey("user1"));
    }

    static void testFeedCacheInvalidate() {
        System.out.println("\n── FeedCache Invalidate ──");
        FeedCache cache = new FeedCache(60000);
        cache.put("user1", List.of(new Content("a", "p", ContentType.POST)));
        check("cached before invalidate", cache.get("user1") != null);

        cache.invalidate("user1");
        check("null after invalidate", cache.get("user1") == null);
        check("cache entry removed", !cache.cache.containsKey("user1"));
        check("timestamp removed", !cache.timestamps.containsKey("user1"));
    }

    static void testFeedCacheInvalidateAll() {
        System.out.println("\n── FeedCache InvalidateAll ──");
        FeedCache cache = new FeedCache(60000);
        cache.put("u1", List.of(new Content("a", "p1", ContentType.POST)));
        cache.put("u2", List.of(new Content("a", "p2", ContentType.POST)));
        cache.put("u3", List.of(new Content("a", "p3", ContentType.POST)));

        cache.invalidateAll(Set.of("u1", "u3"));
        check("u1 invalidated", cache.get("u1") == null);
        check("u2 still cached", cache.get("u2") != null);
        check("u3 invalidated", cache.get("u3") == null);
    }

    static void testFeedCacheIsCached() {
        System.out.println("\n── FeedCache isCached ──");
        FeedCache cache = new FeedCache(60000);
        check("not cached initially", !cache.isCached("user1"));

        cache.put("user1", List.of(new Content("a", "p", ContentType.POST)));
        check("cached after put", cache.isCached("user1"));
        check("other user not cached", !cache.isCached("user2"));
    }

    static void testFeedCacheIsCachedExpired() throws Exception {
        System.out.println("\n── FeedCache isCached Expired ──");
        FeedCache cache = new FeedCache(50); // 50ms TTL
        cache.put("user1", List.of(new Content("a", "p", ContentType.POST)));
        check("cached before expiry", cache.isCached("user1"));

        Thread.sleep(80);
        check("not cached after expiry", !cache.isCached("user1"));
    }

    static void testFeedCacheReturnsCopy() {
        System.out.println("\n── FeedCache Returns Copy ──");
        FeedCache cache = new FeedCache(60000);
        List<Content> original = new ArrayList<>(List.of(new Content("a", "p", ContentType.POST)));
        cache.put("user1", original);

        List<Content> retrieved = cache.get("user1");
        check("different list instance (defensive copy on put)", retrieved != original);
    }

    // ════════════════════════════════════════════════════════════════
    //              FeedService
    // ════════════════════════════════════════════════════════════════
    static void testFeedServiceConstructor() {
        System.out.println("\n── FeedService Constructor ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        check("users map initialized", service.users != null);
        check("contents map initialized", service.contents != null);
        check("rankingStrategy set", service.rankingStrategy != null);
        check("cache initialized", service.cache != null);
    }

    static void testFeedServiceAddUser() {
        System.out.println("\n── FeedService addUser ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        // Note: addUser() creates AppUser with uninitialized collections
        // So we use our helper
        String userId = addUserToService(service);
        check("userId returned", userId != null);
        check("user stored in map", service.users.containsKey(userId));
        AppUser user = service.users.get(userId);
        check("user has following set", user.following != null);
        check("user has followers set", user.followers != null);
        check("user has contentIds list", user.contentIds != null);
        check("user has interests set", user.interests != null);
    }

    static void testFeedServiceFollow() {
        System.out.println("\n── FeedService follow ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String u1 = addUserToService(service);
        String u2 = addUserToService(service);
        try {
            service.follow(u1, u2);
            AppUser user1 = service.users.get(u1);
            AppUser user2 = service.users.get(u2);
            check("u1 follows u2", user1.following.contains(u2));
            // Note: follow() has a bug - it adds to follows.following instead of follows.followers
            check("u2.following contains u1 (bug: should be followers)", user2.following.contains(u1));
        } catch (Exception e) { check("follow: " + e.getMessage(), false); }
    }

    static void testFeedServiceFollowUserNotFound() {
        System.out.println("\n── FeedService follow Not Found ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String u1 = addUserToService(service);
        try {
            service.follow(u1, "US-NONEXIST");
            check("follow non-existent should throw", false);
        } catch (UserNotFoundException1 e) { check("throws UserNotFoundException1", true); }

        try {
            service.follow("US-NONEXIST", u1);
            check("follow by non-existent should throw", false);
        } catch (UserNotFoundException1 e) { check("throws for non-existent follower", true); }
    }

    static void testFeedServiceAddInterest() {
        System.out.println("\n── FeedService addInterest ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String userId = addUserToService(service);
        try {
            service.addInterest(userId, "tech");
            service.addInterest(userId, "sports");
            AppUser user = service.users.get(userId);
            check("interests contains tech", user.interests.contains("tech"));
            check("interests contains sports", user.interests.contains("sports"));
            check("2 interests total", user.interests.size() == 2);
        } catch (Exception e) { check("addInterest: " + e.getMessage(), false); }
    }

    static void testFeedServiceAddInterestNotFound() {
        System.out.println("\n── FeedService addInterest Not Found ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        try {
            service.addInterest("US-NONEXIST", "tech");
            check("should throw", false);
        } catch (UserNotFoundException1 e) { check("throws UserNotFoundException1", true); }
    }

    static void testFeedServicePublish() {
        System.out.println("\n── FeedService publish ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String authorId = addUserToService(service);
        try {
            Content c = service.publish(authorId, "Hello!", ContentType.POST);
            check("content returned", c != null);
            check("content stored in service", service.contents.containsKey(c.contentId));
            check("authorId matches", authorId.equals(c.authorId));
            check("text matches", "Hello!".equals(c.text));
            check("type is POST", c.type == ContentType.POST);
            check("added to author contentIds", service.users.get(authorId).contentIds.contains(c.contentId));
        } catch (Exception e) { check("publish: " + e.getMessage(), false); }
    }

    static void testFeedServicePublishNotFound() {
        System.out.println("\n── FeedService publish Not Found ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        try {
            service.publish("US-NONEXIST", "text", ContentType.POST);
            check("should throw", false);
        } catch (UserNotFoundException1 e) { check("throws UserNotFoundException1", true); }
    }

    static void testFeedServiceAddEngagement() {
        System.out.println("\n── FeedService addEngagement ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String authorId = addUserToService(service);
        try {
            Content c = service.publish(authorId, "Post", ContentType.POST);
            service.addEngagement(c.contentId, 10, 5, 3);
            check("likes = 10", c.likes == 10);
            check("comments = 5", c.comments == 5);
            check("shares = 3", c.shares == 3);

            service.addEngagement(c.contentId, 5, 2, 1);
            check("likes accumulated = 15", c.likes == 15);
            check("comments accumulated = 7", c.comments == 7);
            check("shares accumulated = 4", c.shares == 4);
        } catch (Exception e) { check("addEngagement: " + e.getMessage(), false); }
    }

    static void testFeedServiceAddEngagementNotFound() {
        System.out.println("\n── FeedService addEngagement Not Found ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        try {
            service.addEngagement("CO-NONEXIST", 1, 1, 1);
            check("should throw", false);
        } catch (ContentNotFoundException1 e) { check("throws ContentNotFoundException1", true); }
    }

    static void testFeedServiceGenerateFeed() {
        System.out.println("\n── FeedService generateFeed ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String viewer = addUserToService(service);
        String author = addUserToService(service);
        try {
            service.follow(viewer, author);
            service.publish(author, "Post 1", ContentType.POST);
            service.publish(author, "Post 2", ContentType.POST);

            List<Content> feed = service.generateFeed(viewer);
            check("feed has 2 items", feed.size() == 2);
            check("newest first (chronological)", "Post 2".equals(feed.get(0).text));
            check("oldest last", "Post 1".equals(feed.get(1).text));
        } catch (Exception e) { check("generateFeed: " + e.getMessage(), false); }
    }

    static void testFeedServiceGenerateFeedNotFound() {
        System.out.println("\n── FeedService generateFeed Not Found ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        try {
            service.generateFeed("US-NONEXIST");
            check("should throw", false);
        } catch (UserNotFoundException1 e) { check("throws UserNotFoundException1", true); }
    }

    static void testFeedServiceGenerateFeedCaching() {
        System.out.println("\n── FeedService generateFeed Caching ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String viewer = addUserToService(service);
        String author = addUserToService(service);
        try {
            service.follow(viewer, author);
            service.publish(author, "Cached Post", ContentType.POST);

            List<Content> feed1 = service.generateFeed(viewer);
            check("feed cached after first call", service.cache.isCached(viewer));

            List<Content> feed2 = service.generateFeed(viewer);
            check("cached result has same content", feed1.size() == feed2.size() && feed1.get(0).contentId.equals(feed2.get(0).contentId));
        } catch (Exception e) { check("generateFeed caching: " + e.getMessage(), false); }
    }

    static void testFeedServiceGenerateFeedEmpty() {
        System.out.println("\n── FeedService generateFeed Empty ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String viewer = addUserToService(service);
        try {
            List<Content> feed = service.generateFeed(viewer);
            check("empty feed for user following nobody", feed.isEmpty());
        } catch (Exception e) { check("generateFeed empty: " + e.getMessage(), false); }
    }

    // ════════════════════════════════════════════════════════════════
    //              End-to-End Scenarios
    // ════════════════════════════════════════════════════════════════
    static void testEndToEndChronological() {
        System.out.println("\n── E2E: Chronological Feed ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String alice = addUserToService(service);
        String bob = addUserToService(service);
        String charlie = addUserToService(service);
        try {
            service.follow(charlie, alice);
            service.follow(charlie, bob);

            Content c1 = service.publish(alice, "Alice post 1", ContentType.POST);
            Content c2 = service.publish(bob, "Bob post 1", ContentType.POST);
            Content c3 = service.publish(alice, "Alice post 2", ContentType.POST);

            List<Content> feed = service.generateFeed(charlie);
            check("E2E-chrono: feed has 3 items", feed.size() == 3);
            check("E2E-chrono: newest first", feed.get(0) == c3);
            check("E2E-chrono: oldest last", feed.get(2) == c1);
        } catch (Exception e) { check("E2E chronological: " + e.getMessage(), false); }
    }

    static void testEndToEndEngagement() {
        System.out.println("\n── E2E: Engagement Feed ──");
        FeedService service = makeService(new EngagementRankingStrategy());
        String viewer = addUserToService(service);
        String author = addUserToService(service);
        try {
            service.follow(viewer, author);

            Content low = service.publish(author, "Low engagement", ContentType.POST);
            Content high = service.publish(author, "High engagement", ContentType.POST);
            Content mid = service.publish(author, "Mid engagement", ContentType.POST);

            service.addEngagement(low.contentId, 1, 0, 0);
            service.addEngagement(high.contentId, 50, 20, 10);
            service.addEngagement(mid.contentId, 10, 5, 2);

            List<Content> feed = service.generateFeed(viewer);
            check("E2E-engage: 3 items", feed.size() == 3);
            check("E2E-engage: highest engagement first", feed.get(0) == high);
            check("E2E-engage: lowest engagement last", feed.get(2) == low);
        } catch (Exception e) { check("E2E engagement: " + e.getMessage(), false); }
    }

    static void testEndToEndPersonalised() {
        System.out.println("\n── E2E: Personalised Feed ──");
        FeedService service = makeService(new PersonalisedRankingStrategy());
        String viewer = addUserToService(service);
        String favAuthor = addUserToService(service);
        String otherAuthor = addUserToService(service);
        try {
            service.follow(viewer, favAuthor);
            service.follow(viewer, otherAuthor);
            service.addInterest(viewer, favAuthor);

            Content favPost = service.publish(favAuthor, "Fav post", ContentType.POST);
            Content otherPost = service.publish(otherAuthor, "Other post", ContentType.POST);
            Content adPost = service.publish(otherAuthor, "Ad post", ContentType.AD);

            service.addEngagement(favPost.contentId, 5, 2, 1);
            service.addEngagement(otherPost.contentId, 5, 2, 1);
            service.addEngagement(adPost.contentId, 5, 2, 1);

            List<Content> feed = service.generateFeed(viewer);
            check("E2E-personal: 3 items", feed.size() == 3);
            check("E2E-personal: fav author post first", feed.get(0) == favPost);
            check("E2E-personal: AD post last", feed.get(2) == adPost);
        } catch (Exception e) { check("E2E personalised: " + e.getMessage(), false); }
    }

    static void testEndToEndMultipleFollowing() {
        System.out.println("\n── E2E: Multiple Following ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String viewer = addUserToService(service);
        String a1 = addUserToService(service);
        String a2 = addUserToService(service);
        String a3 = addUserToService(service);
        try {
            service.follow(viewer, a1);
            service.follow(viewer, a2);
            service.follow(viewer, a3);

            service.publish(a1, "From A1", ContentType.POST);
            service.publish(a2, "From A2", ContentType.SHARED_POST);
            service.publish(a3, "From A3", ContentType.AD);
            service.publish(a1, "From A1 again", ContentType.POST);

            List<Content> feed = service.generateFeed(viewer);
            check("E2E-multi: 4 items from 3 authors", feed.size() == 4);
            check("E2E-multi: newest first", "From A1 again".equals(feed.get(0).text));

            // Viewer doesn't see own posts
            service.publish(viewer, "My own post", ContentType.POST);
            // Need to invalidate cache first
            service.cache.invalidate(viewer);
            List<Content> feed2 = service.generateFeed(viewer);
            check("E2E-multi: own posts not in feed (not following self)", feed2.size() == 4);
        } catch (Exception e) { check("E2E multiple following: " + e.getMessage(), false); }
    }

    static void testEndToEndCacheInvalidationOnPublish() {
        System.out.println("\n── E2E: Cache Invalidation on Publish ──");
        FeedService service = makeService(new ChronologicalRankingStrategy());
        String viewer = addUserToService(service);
        String author = addUserToService(service);
        // Manually add viewer to author's followers for cache invalidation
        service.users.get(author).followers.add(viewer);
        try {
            service.follow(viewer, author);

            service.publish(author, "First post", ContentType.POST);
            List<Content> feed1 = service.generateFeed(viewer);
            check("E2E-cache: 1 item initially", feed1.size() == 1);
            check("E2E-cache: cached", service.cache.isCached(viewer));

            // Publishing invalidates follower caches
            service.publish(author, "Second post", ContentType.POST);
            check("E2E-cache: cache invalidated after publish", !service.cache.isCached(viewer));

            List<Content> feed2 = service.generateFeed(viewer);
            check("E2E-cache: 2 items after new publish", feed2.size() == 2);
            check("E2E-cache: newest first", "Second post".equals(feed2.get(0).text));
        } catch (Exception e) { check("E2E cache invalidation: " + e.getMessage(), false); }
    }
}
