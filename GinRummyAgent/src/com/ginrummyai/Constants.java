package com.ginrummyai;

public class Constants {
    public double MELDING_IMPORTANCE;
    public double CARD_VALUE_IMPORTANCE;
    public double EMERGENCY_CARD_VALUE_IMPORTANCE_MULTIPLIER;
    public double KNOCK_BONUS;
    public double GIN_HAND_BONUS;
    public int NUM_DISCARD_OPTIONS;
    public int OPP_HAND_SAMPLES;
    public double MELD_PROBABILITY_EXPONENTIATION_FACTOR;
    public double OPP_UTIL_GAIN_IMPORTANCE;
    public double INFORMATION_TAX;
    public double MELDING_POTENTIAL_IMPORTANCE;
    public double LOW_CARD_BUMP;

    public Constants() {
        MELDING_IMPORTANCE = 1.5;
        CARD_VALUE_IMPORTANCE = 4;
        EMERGENCY_CARD_VALUE_IMPORTANCE_MULTIPLIER = 2;
        KNOCK_BONUS = 10;
        GIN_HAND_BONUS = 20;
        NUM_DISCARD_OPTIONS = 6;
        OPP_HAND_SAMPLES = 25;
        MELD_PROBABILITY_EXPONENTIATION_FACTOR = 1;
        OPP_UTIL_GAIN_IMPORTANCE = 1;
        INFORMATION_TAX = 0;
        MELDING_POTENTIAL_IMPORTANCE = 0.8;
        LOW_CARD_BUMP = 0;
    }
}
