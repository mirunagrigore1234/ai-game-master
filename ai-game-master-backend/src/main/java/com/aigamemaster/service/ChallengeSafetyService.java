package com.aigamemaster.service;

import com.aigamemaster.model.Challenge;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ChallengeSafetyService {

    private static final Pattern UNSAFE_TERMS = Pattern.compile(
            "\\b("
                    + "kill|murder|suicide|self[- ]harm|hurt|attack|fight|weapon|gun|knife|"
                    + "steal|drug|drunk|alcohol|sex|sexual|nude|naked|destroy|break|trespass|"
                    + "traffic|road|street crossing|private information|phone number|address|"
                    + "harass|threat|injur|jump off|climb onto|describe|explain|name three|"
                    + "identify a sound|tell us|write|say"
                    + ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    public boolean isSafe(Challenge challenge) {
        if (challenge == null || challenge.getChallenge() == null || challenge.getChallenge().isBlank()) {
            return false;
        }

        String text = challenge.getChallenge().toLowerCase(Locale.ROOT);
        return !UNSAFE_TERMS.matcher(text).find();
    }
}
