package com.anvex.defender.auth;

import java.util.List;

/**
 * The seeded dummy accounts. Passwords are intentionally published: this is a classroom dummy
 * application, not a production credential store. Passwords are only ever stored as salted BCrypt
 * hashes in the database.
 */
public final class SeedAccounts {

    public record Seed(String username, String displayName, String role, String demoPassword) {
    }

    public static final List<Seed> ALL = List.of(
            new Seed("asha.raman", "Asha Raman", "STUDENT", "Asha!2345"),
            new Seed("rohan.mehta", "Rohan Mehta", "STUDENT", "Rohan!2345"),
            new Seed("meera.iyer", "Meera Iyer", "STUDENT", "Meera!2345"),
            new Seed("kabir.singh", "Kabir Singh", "STUDENT", "Kabir!2345"),
            new Seed("zoya.khan", "Zoya Khan", "STUDENT", "Zoya!2345"),
            new Seed("arjun.nair", "Arjun Nair", "STUDENT", "Arjun!2345"),
            new Seed("priya.desai", "Priya Desai", "STUDENT", "Priya!2345"),
            new Seed("vikram.rao", "Vikram Rao", "STUDENT", "Vikram!2345"),
            new Seed("neha.joshi", "Neha Joshi", "STUDENT", "Neha!2345"),
            new Seed("aditya.pillai", "Aditya Pillai", "STUDENT", "Aditya!2345"),
            new Seed("sara.khan", "Sara Khan", "STUDENT", "Sara!2345"),
            new Seed("lab.admin", "Lab Administrator", "ANALYST", "Admin!2345")
    );

    private SeedAccounts() {
    }
}
