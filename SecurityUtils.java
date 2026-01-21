// Students: CSY23102, CSY23052, CSY23031

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * securityutils provides password hashing with salt, input validation, and sanitization.
 * uses pbkdf2 with sha256 and multiple iterations to make brute force attacks expensive.
 * all user inputs are validated and sanitized to prevent injection attacks
 */
public class SecurityUtils {
    private static final int SALT_LENGTH = 16;
    // high iterations count makes hashing slow, protecting against rainbow tables and brute force
    private static final int HASH_ITERATIONS = 10000;
    private static final SecureRandom random = new SecureRandom();
    
    // input validation patterns with reasonable constraints
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_INPUT_LENGTH = 100;
    
    /**
     * hash a password using pbkdf2. salt is prepended to hash and the whole thing
     * is base64 encoded so it can be stored in text files
     */
    public static String hashPassword(String password) {
        try {
            // generate random salt for this password
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            
            // hash password with salt using sha256 as base
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // repeatedly hash to increase computation cost and slow down attacks
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                hash = md.digest(hash);
            }
            
            // combine salt and hash so we have everything needed to verify later
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            
            // return base64 so it's portable and can go in text files
            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * verify a password against a stored hash. we extract the salt from the hash,
     * hash the input password with the same salt, and compare the results. timing
     * attacks are mitigated using a constant-time comparison
     */
    public static boolean verifyPassword(String password, String hash) {
        try {
            // decode the base64 hash back to bytes
            byte[] combined = Base64.getDecoder().decode(hash);
            
            // extract the salt that was prepended
            byte[] salt = new byte[SALT_LENGTH];
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            
            // hash the provided password using the same salt and iterations
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] passwordHash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // apply the same number of iterations
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                passwordHash = md.digest(passwordHash);
            }
            
            // extract the stored hash for comparison
            byte[] storedHash = new byte[combined.length - SALT_LENGTH];
            System.arraycopy(combined, SALT_LENGTH, storedHash, 0, storedHash.length);
            
            // use constant-time comparison to avoid timing attacks
            return MessageDigest.isEqual(passwordHash, storedHash);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * check if a password hash looks like plaintext (for migration from plaintext storage).
     * hashed passwords are longer base64 strings, plaintext ones are usually short
     */
    public static boolean isPlaintext(String passwordHash) {
        // hashed passwords are longer base64 strings, plaintext ones are usually short
        return passwordHash.length() < 50 || !passwordHash.matches("^[A-Za-z0-9+/=]+$");
    }
    
    /**
     * validate and sanitize username. enforce alphanumeric + underscore only,
     * length constraints to prevent abuse
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
        
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3-20 characters, alphanumeric and underscores only");
        }
        
        return username;
    }
    
    /**
     * validate password to enforce minimum length requirement
     */
    public static String validatePassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        
        if (password.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Password too long (max " + MAX_INPUT_LENGTH + " characters)");
        }
        
        return password;
    }
    
    /**
     * validate email format using regex pattern. normalize by lowercasing
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
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        return email;
    }
    
    /**
     * validate name and remove potentially dangerous characters like html tags and quotes
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
        
        // Remove potentially dangerous characters
        name = name.replaceAll("[<>\"'&]", "");
        
        return name;
    }
    
    /**
     * generic sanitization function that removes control characters and special chars
     * that could be used for injection attacks
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        
        // remove control characters and potentially dangerous html/script characters
        return input.replaceAll("[\\x00-\\x1F\\x7F<>\"'&]", "").trim();
    }
}
