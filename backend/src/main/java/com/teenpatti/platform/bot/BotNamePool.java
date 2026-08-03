package com.teenpatti.platform.bot;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-looking Indian display names for bots.
 */
public final class BotNamePool {

    private static final List<String> FIRST = List.of(
            "Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh", "Ayaan",
            "Krishna", "Ishaan", "Shaurya", "Atharv", "Kabir", "Rudra", "Rohan",
            "Ananya", "Aadhya", "Diya", "Pari", "Myra", "Sara", "Anika", "Ira",
            "Kiara", "Navya", "Aisha", "Meera", "Riya", "Saanvi", "Priya"
    );

    private static final List<String> LAST = List.of(
            "Sharma", "Verma", "Patel", "Singh", "Kumar", "Gupta", "Reddy", "Nair",
            "Mehta", "Joshi", "Kapoor", "Malhotra", "Chopra", "Iyer", "Das", "Khan"
    );

    private BotNamePool() {}

    public static String randomDisplayName() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return FIRST.get(r.nextInt(FIRST.size())) + " " + LAST.get(r.nextInt(LAST.size()));
    }
}
