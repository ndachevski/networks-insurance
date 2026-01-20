import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * SecurityUtils.java - provide secure password hashing, input validation, and sanitization
 * this utility class help protect user account by hashing password before store, validating input,
 * and removing dangerous character from user input to prevent injection attack
 */
public class SecurityUtils {
    // how long random salt should be (byte)
    private static final int SALT_LENGTH = 16;
    // how many time we hash password to make it slow (so brute force harder)
    private static final int HASH_ITERATIONS = 10000;
    // random number generator for salt
    private static final SecureRandom random = new SecureRandom();
    
    // pattern to validate username - only alphanumeric and underscore, 3-20 character
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    // pattern to validate email address
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    // maximum length for user input to prevent huge input
    private static final int MAX_INPUT_LENGTH = 100;
    
    /**
     * hash password using pbkdf2 with random salt so same password hash different every time.
     * this way even if attacker get password hash, they cannot easily crack it
     */
    public static String hashPassword(String password) {
        try {
            // generate random salt so each password hash different
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            
            // hash password with salt using sha-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // apply many iteration so it slow (this make brute force attack much slower)
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                hash = md.digest(hash);
            }
            
            // combine salt and hash together
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            
            // encode to base64 so can store as string
            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * check if password correct by hashing it with same salt and compare result
     */
    public static boolean verifyPassword(String password, String hash) {
        try {
            // decode the hash that stored
            byte[] combined = Base64.getDecoder().decode(hash);
            
            // extract salt from combined hash+salt
            byte[] salt = new byte[SALT_LENGTH];
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            
            // hash provided password with same salt
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] passwordHash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // apply same number of iteration
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                passwordHash = md.digest(passwordHash);
            }
            
            // extract stored hash from combined
            byte[] storedHash = new byte[combined.length - SALT_LENGTH];
            System.arraycopy(combined, SALT_LENGTH, storedHash, 0, storedHash.length);
            
            // compare - use secure compare that take same time regardless of where difference happen
            return MessageDigest.isEqual(passwordHash, storedHash);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * check if password is plaintext (old format) so we can upgrade it to hashed
     */
    public static boolean isPlaintext(String passwordHash) {
        // plaintext password short and not look like base64
        // hashed password longer and only contain base64 character
        return passwordHash.length() < 50 || !passwordHash.matches("^[A-Za-z0-9+/=]+$");
    }
    
    /**
     * validate username - check if follow rule and not dangerous
     */
    public static String validateUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        
        username = username.trim();
        
        if (username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        if (username.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Username too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        // check if username follow pattern rule
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3-20 characters, alphanumeric and underscores only");
        }
        
        return username;
    }
    
    /**
     * validate password - check if strong enough
     */
    public static String validatePassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        
        // password must be at least 6 character
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        
        if (password.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Password too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        return password;
    }
    
    /**
     * validate email - check format correct
     */
    public static String validateEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email cannot be null");
        }
        
        email = email.trim().toLowerCase();
        
        if (email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        if (email.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Email too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        // check if email match expected pattern
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        return email;
    }
    
    /**
     * validate name - check not too long and remove dangerous character
     */
    public static String validateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        
        name = name.trim();
        
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        
        if (name.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Name too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        // remove potentially dangerous character like html tag
        name = name.replaceAll("[<>\"'&]", "");
        
        return name;
    }
    
    /**
     * sanitize string to prevent injection attack - remove bad character
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        
        // remove control character and potentially dangerous character
        return input.replaceAll("[\\x00-\\x1F\\x7F<>\"'&]", "").trim();
    }
}
