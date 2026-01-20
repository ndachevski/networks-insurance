import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RateLimiter.java - prevent brute force attack by limiting login attempt
 * if someone try login too many time in short period, we block them for a while.
 * this help prevent someone from guessing password by trying many time
 */
public class RateLimiter {
    // how many attempt allow before lockout
    private static final int MAX_ATTEMPTS = 5;
    // how long lock account after too many attempt (15 minute)
    private static final long LOCKOUT_DURATION = 15 * 60 * 1000;
    // time window to count attempt (1 minute)
    private static final long WINDOW_DURATION = 60 * 1000;
    
    /**
     * hold attempt information for one identifier (ip:username)
     */
    private static class AttemptInfo {
        // how many attempt made
        AtomicInteger attempts = new AtomicInteger(0);
        // when first attempt happen
        AtomicLong firstAttempt = new AtomicLong(System.currentTimeMillis());
        // when lockout end (0 mean not locked)
        AtomicLong lockoutUntil = new AtomicLong(0);
    }
    
    // store attempt info for each identifier
    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();
    
    /**
     * check if identifier (ip:username) currently rate limited (locked out)
     */
    public boolean isRateLimited(String identifier) {
        AttemptInfo info = attempts.get(identifier);
        if (info == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        
        // check if locked out and lockout still active
        if (info.lockoutUntil.get() > now) {
            return true;
        }
        
        // if lockout expired, reset and allow attempt
        if (info.lockoutUntil.get() > 0 && info.lockoutUntil.get() <= now) {
            info.attempts.set(0);
            info.lockoutUntil.set(0);
            info.firstAttempt.set(now);
            return false;
        }
        
        // if time window expired, reset counter
        if (now - info.firstAttempt.get() > WINDOW_DURATION) {
            info.attempts.set(0);
            info.firstAttempt.set(now);
            return false;
        }
        
        return false;
    }
    
    /**
     * record a failed login attempt. increment counter and check if need lockout
     */
    public void recordFailedAttempt(String identifier) {
        // get or create attempt info for this identifier
        AttemptInfo info = attempts.computeIfAbsent(identifier, k -> new AttemptInfo());
        
        long now = System.currentTimeMillis();
        
        // if time window expired since first attempt, reset counter
        if (now - info.firstAttempt.get() > WINDOW_DURATION) {
            info.attempts.set(0);
            info.firstAttempt.set(now);
        }
        
        // increment attempt counter
        int currentAttempts = info.attempts.incrementAndGet();
        
        // if exceed max attempt, lock out
        if (currentAttempts >= MAX_ATTEMPTS) {
            info.lockoutUntil.set(now + LOCKOUT_DURATION);
        }
    }
    
    /**
     * record successful login - reset counter for this identifier
     */
    public void recordSuccess(String identifier) {
        // remove entry so next login start fresh
        attempts.remove(identifier);
    }
    
    /**
     * get how many second left until lockout end. return 0 if not locked
     */
    public long getRemainingLockoutTime(String identifier) {
        AttemptInfo info = attempts.get(identifier);
        if (info == null) {
            return 0;
        }
        
        // calculate remaining time in second
        long remaining = info.lockoutUntil.get() - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }
}
