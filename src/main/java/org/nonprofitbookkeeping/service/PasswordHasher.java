package org.nonprofitbookkeeping.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** PBKDF2-HMAC-SHA256 credential hashing with per-password random salt. */
public final class PasswordHasher
{
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int VERSION = 1;
    public static final int DEFAULT_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 32;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random;

    public PasswordHasher()
    {
        this(DEFAULT_ITERATIONS, new SecureRandom());
    }

    PasswordHasher(int iterations, SecureRandom random)
    {
        if (iterations <= 0)
        {
            throw new IllegalArgumentException("iterations must be positive");
        }
        this.iterations = iterations;
        this.random = Objects.requireNonNull(random, "random");
    }

    SecurityRepository.CredentialData hash(char[] password)
    {
        requirePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        return new SecurityRepository.CredentialData(
                ALGORITHM,
                iterations,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash),
                VERSION);
    }

    boolean verify(char[] password, SecurityRepository.CredentialData credential)
    {
        if (password == null || credential == null || !ALGORITHM.equals(credential.algorithm()))
        {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try
        {
            salt = Base64.getDecoder().decode(credential.saltBase64());
            expected = Base64.getDecoder().decode(credential.hashBase64());
        }
        catch (IllegalArgumentException ex)
        {
            return false;
        }
        byte[] actual = derive(password, salt, credential.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations)
    {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try
        {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        }
        catch (GeneralSecurityException ex)
        {
            throw new IllegalStateException("Required password hashing algorithm is unavailable.", ex);
        }
        finally
        {
            spec.clearPassword();
        }
    }

    static void requirePassword(char[] password)
    {
        if (password == null || password.length == 0)
        {
            throw new IllegalArgumentException("Password must not be blank. Use Clear Password for passwordless login.");
        }
        if (password.length > 1024)
        {
            throw new IllegalArgumentException("Password must be at most 1024 characters.");
        }
    }
}
