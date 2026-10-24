package com.shambanexa.implementation;

import java.util.ArrayList;
import java.util.List;

final class ForecastWatch {

    private ForecastWatch() {
    }

    static List<String> checks(List<Reading> days) {
        return checks(days, false);
    }

    static List<String> checks(List<Reading> days, boolean openHealthRecord) {
        List<String> notes = new ArrayList<>();
        int wet = 0;
        int muggy = 0;
        boolean cool = false;
        boolean hot = false;
        for (Reading day : days) {
            if (day.rainChance() != null && day.rainChance() >= 60) {
                wet++;
            }
            if (day.humidity() != null && day.highC() != null && day.humidity() >= 75 && day.highC() >= 26) {
                muggy++;
            }
            if (day.lowC() != null && day.lowC() <= 12) {
                cool = true;
            }
            if (day.highC() != null && day.highC() >= 32) {
                hot = true;
            }
        }
        if (wet >= 2) {
            notes.add("Rain is likely on " + wet + " days. Check drainage, feed stores, and dry bedding.");
        }
        if (muggy >= 2) {
            notes.add("Warm, humid days are in the forecast. Damp housing is worth checking.");
        }
        if (cool) {
            notes.add("A cool night is in the forecast.");
        }
        if (hot) {
            notes.add("A hot day is in the forecast. Shade and water for livestock are worth checking.");
        }
        if (openHealthRecord && wet >= 2) {
            notes.add("An open health record is already stored. Wet days are a reason to look at it again. This is not a new diagnosis.");
        }
        return notes;
    }

    static String sky(int code) {
        return switch (code) {
            case 0 -> "Clear";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Showers";
            case 85, 86 -> "Snow showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Weather " + code;
        };
    }

    record Reading(Integer rainChance, Double humidity, Double highC, Double lowC) {
    }
}
