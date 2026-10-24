package com.shambanexa.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PercentTest {

    @Test
    void portfolioDeltasMatchTheBrandBoard() {
        assertEquals(new BigDecimal("4.2"), Percent.change(12540, 12035));
        assertEquals(new BigDecimal("8.1"), Percent.change(new BigDecimal("87420"), new BigDecimal("80870")));
    }

    @Test
    void demoPasswordsMatchStoredHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("Admin@123", "$2y$05$BoA7aKihmXeSzmKErQSweOnQvIkKHek3oXA0tL6ENrbvguYDa.1bC"));
        assertTrue(encoder.matches("Manager@123", "$2y$05$G/PAoLtSekxS53CJeNgKguRsITDWsdOqrAoSME.6LcDf.G3wDENCW"));
    }
}
