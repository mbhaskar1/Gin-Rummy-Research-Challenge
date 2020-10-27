package com.ginrummyai;

import java.text.DecimalFormat;
import java.util.*;

import ginrummy.Card;
import ginrummy.GinRummyUtil;

public class GRUtil {
    public HashMap<ArrayList<Card>, ArrayList<Card>> optimalMeldings = new HashMap<>();

    public void insertCard(ArrayList<Card> sortedCards, Card card) {
        for (int i = 0; i < sortedCards.size(); i++) {
            if (sortedCards.get(i).getId() > card.getId()) {
                sortedCards.add(i, card);
                return;
            }
        }
        sortedCards.add(card);
    }

    public ArrayList<Card> getOptimalMelded(ArrayList<Card> hand) {
        if(optimalMeldings.containsKey(hand))
            return optimalMeldings.get(hand);
        ArrayList<ArrayList<ArrayList<Card>>> meldSets = GinRummyUtil.cardsToBestMeldSets(hand);
        if(meldSets.isEmpty())
            return new ArrayList<>();
        ArrayList<ArrayList<Card>> melds = meldSets.get(0);
        ArrayList<Card> melded = new ArrayList<>();
        for (ArrayList<Card> meld : melds) {
            melded.addAll(meld);
        }
        melded.sort(Comparator.comparingInt(Card::getId));
        optimalMeldings.put(hand, melded);
        return melded;
    }

    public ArrayList<Card> getOptimalMelded(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, Constants constants) {
        ArrayList<Card> key = new ArrayList<>();
        key.addAll(hand);
        key.addAll(discardPile);
        if(optimalMeldings.containsKey(hand))
            return optimalMeldings.get(hand);
        if(optimalMeldings.containsKey(key))
            return optimalMeldings.get(key);
        ArrayList<ArrayList<ArrayList<Card>>> meldSets = GinRummyUtil.cardsToBestMeldSets(hand);
        if(meldSets.isEmpty()) {
            ArrayList<Card> res = new ArrayList<>();
            optimalMeldings.put(hand, res);
            return res;
        }
        ArrayList<Card> bestMelded = new ArrayList<>();
        if(meldSets.size() == 1) {
            ArrayList<ArrayList<Card>> bestMelds = meldSets.get(0);
            for (ArrayList<Card> meld : bestMelds) {
                bestMelded.addAll(meld);
            }
        }else{
            double bestVal = Double.NEGATIVE_INFINITY;
            for(ArrayList<ArrayList<Card>> melds : meldSets){
                ArrayList<Card> melded = new ArrayList<>();
                for (ArrayList<Card> meld : melds) {
                    melded.addAll(meld);
                }
                ArrayList<Card> unmelded = getUnmelded(hand, melded);
                unmelded.sort(Comparator.comparingInt(Card::getId)); //Might not need to do this
                double[] values = cardValueHeuristic(unmelded, melded, discardPile, oppHandPrediction, cardsToKnock(unmelded) <= 1 + (hand.size() - 10), constants);
                double avg = 0;
                for(double v : values)
                    avg += v;
                avg /= values.length;
                if(avg > bestVal){
                    bestVal = avg;
                    bestMelded = melded;
                }
            }
        }
        bestMelded.sort(Comparator.comparingInt(Card::getId));
        optimalMeldings.put(meldSets.size() == 1 ? hand : key, bestMelded);
        return bestMelded;
    }

    public ArrayList<Card> getOptimalMelded(ArrayList<Card> hand, ArrayList<Card> discardPile, ArrayList<Card> oppHand, Constants constants) {
        ArrayList<Card> key = new ArrayList<>();
        key.addAll(hand);
        key.addAll(discardPile);
        if(optimalMeldings.containsKey(hand))
            return optimalMeldings.get(hand);
        if(optimalMeldings.containsKey(key))
            return optimalMeldings.get(key);
        ArrayList<ArrayList<ArrayList<Card>>> meldSets = GinRummyUtil.cardsToBestMeldSets(hand);
        if(meldSets.isEmpty()) {
            ArrayList<Card> res = new ArrayList<>();
            optimalMeldings.put(hand, res);
            return res;
        }
        ArrayList<Card> bestMelded = new ArrayList<>();
        if(meldSets.size() == 1) {
            ArrayList<ArrayList<Card>> bestMelds = meldSets.get(0);
            for (ArrayList<Card> meld : bestMelds) {
                bestMelded.addAll(meld);
            }
        }else{
            double bestVal = Double.NEGATIVE_INFINITY;
            for(ArrayList<ArrayList<Card>> melds : meldSets){
                ArrayList<Card> melded = new ArrayList<>();
                for (ArrayList<Card> meld : melds) {
                    melded.addAll(meld);
                }
                ArrayList<Card> unmelded = getUnmelded(hand, melded);
                unmelded.sort(Comparator.comparingInt(Card::getId)); //Might not need to do this
                double[] values = cardValueHeuristic(unmelded, melded, discardPile, oppHand, cardsToKnock(unmelded) <= 1 + (hand.size() - 10), constants);
                double avg = 0;
                for(double v : values)
                    avg += v;
                avg /= values.length;
                if(avg > bestVal){
                    bestVal = avg;
                    bestMelded = melded;
                }
            }
        }
        bestMelded.sort(Comparator.comparingInt(Card::getId));
        optimalMeldings.put(meldSets.size() == 1 ? hand : key, bestMelded);
        return bestMelded;
    }

    public ArrayList<Card> getUnmelded(ArrayList<Card> hand, ArrayList<Card> melded) {
        ArrayList<Card> unmelded = new ArrayList<>();
        for (Card card : hand) {
            boolean unmeldedCard = true;
            for (Card meldedCard : melded) {
                if (card.getId() == meldedCard.getId()) {
                    unmeldedCard = false;
                    break;
                }
            }
            if (unmeldedCard) {
                unmelded.add(card);
            }
        }

        return unmelded;
    }

    public int cardsToKnock(ArrayList<Card> unmelded) {
        unmelded.sort(Comparator.comparingInt(GinRummyUtil::getDeadwoodPoints));
        int sum = 0;
        for (int i = 0; i < unmelded.size(); i++) {
            sum += GinRummyUtil.getDeadwoodPoints(unmelded.get(i));
            if (sum > 10) {
                return unmelded.size() - i;
            }
        }
        return 0;
    }

    public double[] cardValueHeuristic(ArrayList<Card> unmeldedCards, ArrayList<Card> meldedCards, ArrayList<Card> discardPile, double[][] oppHandPrediction, boolean emergency, Constants constants) {
        byte[][] cardState = new byte[Card.NUM_RANKS][Card.NUM_SUITS];
        for (int i = 0; i < Card.NUM_RANKS; i++) {
            for (int j = 0; j < Card.NUM_SUITS; j++) {
                cardState[i][j] = 1;
            }
        }
        for (Card card : discardPile)
            cardState[card.rank][card.suit] = 0;
        for (Card card : meldedCards)
            cardState[card.rank][card.suit] = 0;
        for (Card card : unmeldedCards)
            cardState[card.rank][card.suit] = 2;

        double[] cardValues = new double[unmeldedCards.size()];
        for (int i = 0; i < unmeldedCards.size(); i++) {
            cardValues[i] = 0.0;
            Card card = unmeldedCards.get(i);
            if (card.rank >= 2 &&
                    cardState[card.rank - 1][card.suit] > 0 &&
                    cardState[card.rank - 2][card.suit] > 0 &&
                    !(card.rank > 2 && cardState[card.rank - 3][card.suit] == 2)) {
                ArrayList<Card> analyze = new ArrayList<>();
                if (cardState[card.rank - 1][card.suit] != 2)
                    analyze.add(Card.getCard(card.rank - 1, card.suit));
                if (cardState[card.rank - 2][card.suit] != 2)
                    analyze.add(Card.getCard(card.rank - 2, card.suit));
                cardValues[i] += (1 - probContainsMelded(analyze, oppHandPrediction, constants)) * (constants.MELDING_POTENTIAL_IMPORTANCE
                        + (cardState[card.rank - 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0)
                        + (cardState[card.rank - 2][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0));
            }
            if (card.rank >= 1 && card.rank < Card.NUM_RANKS - 1 &&
                    cardState[card.rank - 1][card.suit] > 0 &&
                    cardState[card.rank + 1][card.suit] > 0 &&
                    !(card.rank > 1 && cardState[card.rank - 2][card.suit] == 2)) {
                ArrayList<Card> analyze = new ArrayList<>();
                if (cardState[card.rank - 1][card.suit] != 2)
                    analyze.add(Card.getCard(card.rank - 1, card.suit));
                if (cardState[card.rank + 1][card.suit] != 2)
                    analyze.add(Card.getCard(card.rank + 1, card.suit));
                cardValues[i] += (1 - probContainsMelded(analyze, oppHandPrediction, constants)) * (constants.MELDING_POTENTIAL_IMPORTANCE
                        + (cardState[card.rank - 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0)
                        + (cardState[card.rank + 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0));
            }
            if (card.rank < Card.NUM_RANKS - 2 &&
                    cardState[card.rank + 1][card.suit] > 0 &&
                    cardState[card.rank + 2][card.suit] > 0 &&
                    !(card.rank > 0 && cardState[card.rank - 1][card.suit] == 2)) {
                ArrayList<Card> analyze = new ArrayList<>();
                if (cardState[card.rank + 1][card.suit] != 2)
                    analyze.add(Card.getCard(card.rank + 1, card.suit));
                if (cardState[card.rank + 2][card.suit] != 2)
                    analyze.add(Card.getCard(card.rank + 2, card.suit));
                cardValues[i] += (1 - probContainsMelded(analyze, oppHandPrediction, constants)) * (constants.MELDING_POTENTIAL_IMPORTANCE
                        + (cardState[card.rank + 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0)
                        + (cardState[card.rank + 2][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0));
            }
            int numPossible = 0, numPossessed = 0;
            ArrayList<Card> analyze = new ArrayList<>();
            for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
                if (cardState[card.rank][suit] == 1) {
                    analyze.add(Card.getCard(card.rank, suit));
                    numPossible++;
                } else if (cardState[card.rank][suit] == 2)
                    numPossessed++;
            }
            if (numPossible + numPossessed >= 3) {
                cardValues[i] += (1 - probContainsMelded(analyze, oppHandPrediction, constants)) * numPossible * (numPossessed > 1 ? constants.MELDING_POTENTIAL_IMPORTANCE + constants.MELDING_IMPORTANCE : constants.MELDING_POTENTIAL_IMPORTANCE);
            }

            cardValues[i] += (emergency ? constants.EMERGENCY_CARD_VALUE_IMPORTANCE_MULTIPLIER : 1) * constants.CARD_VALUE_IMPORTANCE * (5 - GinRummyUtil.getDeadwoodPoints(card)) / 4.0;
            if(card.rank == 0)
                cardValues[i] += constants.LOW_CARD_BUMP;
            if(card.rank == 1)
                cardValues[i] += constants.LOW_CARD_BUMP / 2;
        }

        return cardValues;
    }

    public double[] cardValueHeuristic(ArrayList<Card> unmeldedCards, ArrayList<Card> meldedCards, ArrayList<Card> discardPile, ArrayList<Card> oppMeldedCards, boolean emergency, Constants constants){
        byte[][] cardState = new byte[Card.NUM_RANKS][Card.NUM_SUITS];
        for (int i = 0; i < Card.NUM_RANKS; i++) {
            for (int j = 0; j < Card.NUM_SUITS; j++) {
                cardState[i][j] = 1;
            }
        }
        for (Card card : discardPile)
            cardState[card.rank][card.suit] = 0;
        for (Card card : meldedCards)
            cardState[card.rank][card.suit] = 0;
        for (Card card : oppMeldedCards)
            cardState[card.rank][card.suit] = 0;
        for (Card card : unmeldedCards)
            cardState[card.rank][card.suit] = 2;

        double[] cardValues = new double[unmeldedCards.size()];
        for (int i = 0; i < unmeldedCards.size(); i++) {
            cardValues[i] = 0.0;
            Card card = unmeldedCards.get(i);
            if (card.rank >= 2 &&
                    cardState[card.rank - 1][card.suit] > 0 &&
                    cardState[card.rank - 2][card.suit] > 0 &&
                    !(card.rank > 2 && cardState[card.rank - 3][card.suit] == 2)) {
                cardValues[i] += constants.MELDING_POTENTIAL_IMPORTANCE
                        + (cardState[card.rank - 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0)
                        + (cardState[card.rank - 2][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0);
            }
            if (card.rank >= 1 && card.rank < Card.NUM_RANKS - 1 &&
                    cardState[card.rank - 1][card.suit] > 0 &&
                    cardState[card.rank + 1][card.suit] > 0 &&
                    !(card.rank > 1 && cardState[card.rank - 2][card.suit] == 2)) {
                cardValues[i] += constants.MELDING_POTENTIAL_IMPORTANCE
                        + (cardState[card.rank - 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0)
                        + (cardState[card.rank + 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0);
            }
            if (card.rank < Card.NUM_RANKS - 2 &&
                    cardState[card.rank + 1][card.suit] > 0 &&
                    cardState[card.rank + 2][card.suit] > 0 &&
                    !(card.rank > 0 && cardState[card.rank - 1][card.suit] == 2)) {
                cardValues[i] += constants.MELDING_POTENTIAL_IMPORTANCE
                        + (cardState[card.rank + 1][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0)
                        + (cardState[card.rank + 2][card.suit] == 2 ? constants.MELDING_IMPORTANCE : 0);
            }
            int numPossible = 0, numPossessed = 0;
            for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
                if (cardState[card.rank][suit] == 1) {
                    numPossible++;
                } else if (cardState[card.rank][suit] == 2)
                    numPossessed++;
            }
            if (numPossible + numPossessed >= 3) {
                cardValues[i] += numPossible * (numPossessed > 1 ? constants.MELDING_POTENTIAL_IMPORTANCE + constants.MELDING_IMPORTANCE : constants.MELDING_POTENTIAL_IMPORTANCE);
            }

            cardValues[i] += (emergency ? constants.EMERGENCY_CARD_VALUE_IMPORTANCE_MULTIPLIER : 1) * constants.CARD_VALUE_IMPORTANCE * (5 - GinRummyUtil.getDeadwoodPoints(card)) / 4.0;
            if(card.rank == 0)
                cardValues[i] += constants.LOW_CARD_BUMP;
            if(card.rank == 1)
                cardValues[i] += constants.LOW_CARD_BUMP / 2;
        }

        return cardValues;
    }

    public double handUtility(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, boolean emergency, Constants constants) {
        ArrayList<Card> melded = getOptimalMelded(hand, discardPile, oppHandPrediction, constants);
        ArrayList<Card> unmelded = getUnmelded(hand, melded);

        if (unmelded.isEmpty())
            return melded.size() + constants.GIN_HAND_BONUS;

        double[] cardValues = cardValueHeuristic(unmelded, melded, discardPile, oppHandPrediction, emergency, constants);

        double avgCardValue = 0;
        for (double value : cardValues) {
            avgCardValue += value;
        }
        avgCardValue /= cardValues.length;

        return melded.size() + avgCardValue + (GinRummyUtil.getDeadwoodPoints(unmelded) <= 10 ? constants.KNOCK_BONUS : 0);
    }

    public double handUtility(ArrayList<Card> hand, ArrayList<Card> discardPile, ArrayList<Card> oppHand, boolean emergency, Constants constants){
        ArrayList<Card> melded = getOptimalMelded(hand, discardPile, oppHand, constants);
        ArrayList<Card> unmelded = getUnmelded(hand, melded);

        if (unmelded.isEmpty())
            return melded.size() + constants.GIN_HAND_BONUS;

        double[] cardValues = cardValueHeuristic(unmelded, melded, discardPile, getOptimalMelded(oppHand, discardPile, hand, constants), emergency, constants);

        double avgCardValue = 0;
        for (double value : cardValues) {
            avgCardValue += value;
        }
        avgCardValue /= cardValues.length;

        return melded.size() + avgCardValue + (GinRummyUtil.getDeadwoodPoints(unmelded) <= 10 ? constants.KNOCK_BONUS : 0);
    }

    public double hypotheticalUtilityDraw(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, Card draw, boolean fromDeck, Constants constants) {
        ArrayList<Card> melded = getOptimalMelded(hand, discardPile, oppHandPrediction, constants);
        ArrayList<Card> unmelded = getUnmelded(hand, melded);
        insertCard(hand, draw);
        if (!fromDeck) {
            discardPile.remove(discardPile.size() - 1);
        }
        double util = handUtility(hand, discardPile, oppHandPrediction, cardsToKnock(unmelded) <= 1, constants);
        if (!fromDeck)
            discardPile.add(draw);
        hand.remove(draw);
        return util;
    }

    public double hypotheticalUtilityDraw(ArrayList<Card> hand, ArrayList<Card> discardPile, ArrayList<Card> oppHand, Card draw, boolean fromDeck, Constants constants){
        ArrayList<Card> melded = getOptimalMelded(hand, discardPile, oppHand, constants);
        ArrayList<Card> unmelded = getUnmelded(hand, melded);
        insertCard(hand, draw);
        if (!fromDeck) {
            discardPile.remove(discardPile.size() - 1);
        }
        double util = handUtility(hand, discardPile, oppHand, cardsToKnock(unmelded) <= 1, constants);
        if (!fromDeck)
            discardPile.add(draw);
        hand.remove(draw);
        return util;
    }

    public double hypotheticalUtilityDiscard(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, Card discard, Constants constants) {
        ArrayList<Card> melded = getOptimalMelded(hand, discardPile, oppHandPrediction, constants);
        ArrayList<Card> unmelded = getUnmelded(hand, melded);
        hand.remove(discard);
        discardPile.add(discard);
        double util = handUtility(hand, discardPile, oppHandPrediction, cardsToKnock(unmelded) <= 2, constants);
        discardPile.remove(discard);
        insertCard(hand, discard);
        return util;
    }

    final boolean disregardOpp = true;
    public double hypotheticalUtilityDrawDiscard(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, Card draw, boolean fromDeck, Constants constants) {
        insertCard(hand, draw);
        if (!fromDeck) {
            discardPile.remove(discardPile.size() - 1);
        }
        Card discard;
        if(disregardOpp)
            discard = determineDiscardDisregardingOpp(hand, discardPile, oppHandPrediction, getAllowedDiscardOptions(hand, discardPile, oppHandPrediction, fromDeck ? null : draw, constants), constants, false);
        else
            discard = determineDiscard(hand, discardPile, oppHandPrediction, getAllowedDiscardOptions(hand, discardPile, oppHandPrediction, fromDeck ? null : draw, constants), constants, false);
        double util = hypotheticalUtilityDiscard(hand, discardPile, oppHandPrediction, discard, constants);
        if (!fromDeck)
            discardPile.add(draw);
        hand.remove(draw);
        return util;
    }

    public ArrayList<Card> sampleHand(double[][] prediction, int numCards){
        while(true){
            ArrayList<Card> hand = new ArrayList<>();
            for(int suit = 0; suit < Card.NUM_SUITS; suit++){
                for(int rank = 0; rank < Card.NUM_RANKS; rank++){
                    if(Math.random() < prediction[suit][rank]){
                        hand.add(Card.getCard(rank, suit));
                    }
                }
            }
            if(hand.size() == numCards)
                return hand;
        }
    }

    public Card determineDiscard(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, ArrayList<Card> discardOptions, Constants constants, boolean debug){
        int action = 0;
        if (discardOptions.size() > 1) {
            double[] expectedValues = new double[discardOptions.size()];
            for(int i = 0; i < discardOptions.size(); i++){
                Card discard = discardOptions.get(i);
                double playerHypotheticalHandUtil = hypotheticalUtilityDiscard(hand, discardPile, oppHandPrediction, discard, constants);
                double oppExpectedUtilGain = 0;
                for(int j = 0; j < constants.OPP_HAND_SAMPLES; j++){
                    ArrayList<Card> oppHand = sampleHand(oppHandPrediction, 10);
                    discardPile.add(discard);
                    // In the following line, hand still actually contains discard, but this shouldn't be an issue because discard will not be part of melded (as it wouldn't have been discarded otherwise)
                    double discardUtil = hypotheticalUtilityDraw(oppHand, discardPile, hand, discard, false, constants);
                    double deckUtil = 0;
                    int deckSize = 0;
                    for(Card card : Card.allCards){
                        if(!oppHand.contains(card) && !hand.contains(card) && !discardPile.contains(card)){ //hand still contains discard, so this loop will also not include discard
                            deckUtil += hypotheticalUtilityDraw(oppHand, discardPile, hand, card, true, constants);
                            deckSize++;
                        }
                    }
                    discardPile.remove(discardPile.size() - 1);
                    deckUtil /= deckSize;
                    oppExpectedUtilGain += Math.max(0.0, discardUtil - deckUtil);
                }
                oppExpectedUtilGain /= constants.OPP_HAND_SAMPLES;
                expectedValues[i] = playerHypotheticalHandUtil - constants.OPP_UTIL_GAIN_IMPORTANCE * oppExpectedUtilGain;
                if(debug){
                    System.out.printf("%s: %.2f %.2f%n", discard.toString(), playerHypotheticalHandUtil, oppExpectedUtilGain);
                }
            }
            double maxValue = expectedValues[0];
            for(int i = 1; i < discardOptions.size(); i++){
                if(expectedValues[i] > maxValue){
                    maxValue = expectedValues[i];
                    action = i;
                }
            }
            if(debug){
                System.out.println(discardOptions);
                DecimalFormat df = new DecimalFormat("0.00");
                Arrays.stream(expectedValues).forEach(e -> System.out.print(df.format(e) + " " ));
                System.out.println();
            }
        }
        return discardOptions.get(action);
    }

    public Card determineDiscardDisregardingOpp(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, ArrayList<Card> discardOptions, Constants constants, boolean debug){
        int action = 0;
        if (discardOptions.size() > 1) {
            double[] expectedValues = new double[discardOptions.size()];
            for(int i = 0; i < discardOptions.size(); i++){
                Card discard = discardOptions.get(i);
                double playerHypotheticalHandUtil = hypotheticalUtilityDiscard(hand, discardPile, oppHandPrediction, discard, constants);
                expectedValues[i] = playerHypotheticalHandUtil;
            }
            double maxValue = expectedValues[0];
            for(int i = 1; i < discardOptions.size(); i++){
                if(expectedValues[i] > maxValue){
                    maxValue = expectedValues[i];
                    action = i;
                }
            }
        }
        return discardOptions.get(action);
    }

    public ArrayList<Card> getAllowedDiscardOptions(ArrayList<Card> hand, ArrayList<Card> discardPile, double[][] oppHandPrediction, Card illegalDiscard, Constants constants){
        ArrayList<Card> melded = getOptimalMelded(hand, discardPile, oppHandPrediction, constants);
        ArrayList<Card> actual = getUnmelded(hand, melded);
        actual.remove(illegalDiscard);

        // If all cards are melded, choose any card that keeps all cards melded (Since there are 11 cards, there will be at least one meld with at least 4 cards)
        if(actual.isEmpty()){
            for(int i = 0; i < melded.size(); i++){
                if(melded.get(i) != illegalDiscard) {
                    ArrayList<Card> meldedTest = new ArrayList<>(melded);
                    meldedTest.remove(i);
                    ArrayList<Card> newMelded = getOptimalMelded(meldedTest);
                    if (newMelded.size() == meldedTest.size()) {
                        actual.add(melded.get(i));
                        return actual;
                    }
                }
            }
            System.out.println(hand);
            System.out.println(actual);
            System.out.println(melded);
            System.err.println("ERROR: THIS SHOULDN'T HAPPEN");
        }

        double avgOppDeadwood = 0;
        for(int i = 0; i < 30; i++){
            ArrayList<Card> oppHand = sampleHand(oppHandPrediction, 10);
            ArrayList<ArrayList<ArrayList<Card>>> meldSets = GinRummyUtil.cardsToBestMeldSets(oppHand);
            avgOppDeadwood += GinRummyUtil.getDeadwoodPoints(meldSets.isEmpty() ? new ArrayList<>() : meldSets.get(0), oppHand);
        }
        avgOppDeadwood /= 30;

        // If discarding the highest unmelded card results in a knockable hand, only allow that discard
        int maxIndex = 0;
        int maxValue = 0;
        for(int i = 0; i < actual.size(); i++){
            int val = GinRummyUtil.getDeadwoodPoints(actual.get(i));
            if(val > maxValue){
                maxValue = val;
                maxIndex = i;
            }
        }
        Card maxCard = actual.get(maxIndex);
        actual.remove(maxIndex);
        int newHandValue = GinRummyUtil.getDeadwoodPoints(actual);
        if(newHandValue <= 10 - (avgOppDeadwood <= 18 ? 4 : 0)){
            ArrayList<Card> res = new ArrayList<>();
            res.add(maxCard);
            return res;
        }
        insertCard(actual, maxCard);

        // In previous version, would typically return N discard options with least card value. For now, will return all options.
        return actual;
        /*if(actual.size() <= constants.NUM_DISCARD_OPTIONS)
            return actual;

        double[] cardValues = cardValueHeuristic(actual, melded,)*/
    }

    public boolean drawOnlyFromDiscard(ArrayList<Card> hand, ArrayList<Card> discardPile){
        int handMeldVal = GinRummyUtil.getDeadwoodPoints(getOptimalMelded(hand));
        insertCard(hand, discardPile.get(discardPile.size() - 1));
        int newHandMeldVal = GinRummyUtil.getDeadwoodPoints(getOptimalMelded(hand));
        boolean res = newHandMeldVal > handMeldVal;
        hand.remove(discardPile.get(discardPile.size() - 1));
        return res;
    }

    private Set<ArrayList<Card>> getAssociatedPrimitiveMelds(ArrayList<Card> cards) {
        Set<ArrayList<Card>> melds = new HashSet<>();
        for (Card card : cards) {
            for (int i = 0; i < Card.NUM_SUITS; i++) {
                if (i != card.suit) {
                    ArrayList<Card> meld = new ArrayList<>();
                    for (int j = 0; j < Card.NUM_SUITS; j++) {
                        if (j != i) {
                            meld.add(Card.getCard(card.rank, j));
                        }
                    }
                    melds.add(meld);
                }
            }
            if (card.rank >= 2) {
                ArrayList<Card> meld = new ArrayList<>();
                meld.add(Card.getCard(card.rank - 2, card.suit));
                meld.add(Card.getCard(card.rank - 1, card.suit));
                meld.add(card);
                melds.add(meld);
            }
            if (card.rank >= 1 && card.rank < Card.NUM_RANKS - 1) {
                ArrayList<Card> meld = new ArrayList<>();
                meld.add(Card.getCard(card.rank - 1, card.suit));
                meld.add(card);
                meld.add(Card.getCard(card.rank + 1, card.suit));
                melds.add(meld);
            }
            if (card.rank < Card.NUM_RANKS - 2) {
                ArrayList<Card> meld = new ArrayList<>();
                meld.add(card);
                meld.add(Card.getCard(card.rank + 1, card.suit));
                meld.add(Card.getCard(card.rank + 2, card.suit));
                melds.add(meld);
            }
        }

        return melds;
    }

    private double probContainsMelded(ArrayList<Card> cards, double[][] handPrediction, Constants constants) {
        if(handPrediction == null)
            return 0.0;
        double prob = 0.0;
        Set<ArrayList<Card>> melds = getAssociatedPrimitiveMelds(cards);
        for (ArrayList<Card> meld : melds) {
            prob += probMeld(meld, handPrediction);
        }
        return Math.pow(prob, constants.MELD_PROBABILITY_EXPONENTIATION_FACTOR);
    }

    private double probMeld(ArrayList<Card> meld, double[][] handPrediction) {
        if(handPrediction == null)
            return 0.0;
        double prob = 1.0;
        for (Card card : meld) {
            prob *= handPrediction[card.suit][card.rank];
        }
        return prob;
    }

    public static void main(String[] args) {

    }
}
