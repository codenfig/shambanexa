package com.shambanexa.implementation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastWatchTest {

    @Test
    void wetAndHumidDaysBecomeChecksWithoutNamingADisease() {
        List<String> notes = ForecastWatch.checks(List.of(
                new ForecastWatch.Reading(84, 70.0, 26.4, 15.9),
                new ForecastWatch.Reading(69, 82.0, 27.2, 17.6),
                new ForecastWatch.Reading(55, 78.0, 27.0, 17.9)
        ));
        assertEquals(2, notes.size());
        assertTrue(notes.get(0).startsWith("Rain is likely on 2 days"));
        assertTrue(notes.get(1).contains("Damp housing"));
        assertTrue(notes.stream().noneMatch(note -> note.toLowerCase().contains("disease")));
    }

    @Test
    void anOpenHealthRecordIsAReasonToLookAgain() {
        List<String> notes = ForecastWatch.checks(List.of(
                new ForecastWatch.Reading(84, 70.0, 26.4, 15.9),
                new ForecastWatch.Reading(69, 82.0, 27.2, 17.6)
        ), true);
        assertTrue(notes.stream().anyMatch(note -> note.contains("not a new diagnosis")));
    }

    @Test
    void skyLabelsStayShort() {
        assertEquals("Drizzle", ForecastWatch.sky(51));
        assertEquals("Showers", ForecastWatch.sky(80));
    }
}
