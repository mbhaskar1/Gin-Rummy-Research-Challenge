package com.ginrummyai;

import ginrummy.*;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Player implements GinRummyPlayer {
    public double fitness;

    protected int playerNum;
    protected int startingPlayerNum;
    Data data;
    ArrayList<Card> hand;
    ArrayList<Card> discardPile;
    MultiLayerNetwork model = null;
    Constants constants;

    boolean debug = false;

    private boolean opponentKnocked;
    private Card illegalDiscard;
    private final GRUtil util;

    public Player() throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        constants = new Constants();
        constants.MELDING_IMPORTANCE = 1.5;
        constants.CARD_VALUE_IMPORTANCE = 4.0;
        constants.EMERGENCY_CARD_VALUE_IMPORTANCE_MULTIPLIER = 2.5;
        constants.KNOCK_BONUS = 10;
        constants.GIN_HAND_BONUS = 20;
        constants.OPP_UTIL_GAIN_IMPORTANCE = 0.77;
        constants.LOW_CARD_BUMP = 0.2;
        constants.OPP_HAND_SAMPLES = 100;
        InputStream modelStream = new GinRummyUtil.FileResource(Player.class, "model-4-3.h5").asInputStream();
        model = KerasModelImport.importKerasSequentialModelAndWeights(modelStream);
        util = new GRUtil();
    }

    @Override
    public void startGame(int playerNum, int startingPlayerNum, Card[] cards) {
        this.playerNum = playerNum;
        this.startingPlayerNum = startingPlayerNum;
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        Collections.addAll(hand, cards);
        hand.sort(Comparator.comparingInt(Card::getId));
        data = new Data(hand, discardPile);
        illegalDiscard = null;
        opponentKnocked = false;
        util.optimalMeldings.clear();
        oppMelds = null;
    }

    @Override
    public boolean willDrawFaceUpCard(Card card) {
        if (discardPile.size() == 0) {
            discardPile.add(card);
            data.playerDiscard(card);
        }
        boolean drawFromDiscard = true;
        if (!util.drawOnlyFromDiscard(hand, discardPile)) {
            double[][] prediction = getPrediction();
            double discardExpectedValue = util.hypotheticalUtilityDrawDiscard(hand, discardPile, prediction, discardPile.get(discardPile.size() - 1), false, constants);
            double deckExpectedValue = 0;
            for (Card c : Card.allCards) {
                if (!hand.contains(c) && !discardPile.contains(c)) {
                    double[][] hypotheticalPrediction;
                    if(model != null){
                        data.playerDraw(c);
                        hypotheticalPrediction = getPrediction();
                        data.undoPlayerDraw(c);
                    }else{
                        hypotheticalPrediction = new double[4][13];
                        double p = 10.0 / (Card.NUM_CARDS - hand.size() - 1 - discardPile.size());
                        for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                            for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
                                if(prediction[suit][rank] > 0)
                                    hypotheticalPrediction[suit][rank] = p;
                            }
                        }
                        hypotheticalPrediction[c.suit][c.rank] = 0;
                    }
                    deckExpectedValue += (1 - prediction[c.suit][c.rank]) * util.hypotheticalUtilityDrawDiscard(hand, discardPile, hypotheticalPrediction, c, true, constants);
                }
            }
            deckExpectedValue /= Card.NUM_CARDS - 20 - discardPile.size();
            if (deckExpectedValue > discardExpectedValue - constants.INFORMATION_TAX)
                drawFromDiscard = false;
        }
        if (drawFromDiscard) {
            illegalDiscard = discardPile.get(discardPile.size() - 1);
            discardPile.remove(discardPile.size() - 1);
        } else {
            illegalDiscard = null;
        }
        return drawFromDiscard;
    }

    @Override
    public void reportDraw(int playerNum, Card drawnCard) {
        if (playerNum == this.playerNum) {
            util.insertCard(hand, drawnCard);
            data.playerDraw(drawnCard);
        } else {
            if (drawnCard == null) {
                data.opponentDrawFromDeck(discardPile.get(discardPile.size() - 1));
            } else {
                data.opponentDrawFromDiscard(drawnCard);
                if (!discardPile.isEmpty())
                    discardPile.remove(discardPile.size() - 1);
            }
        }
    }

    @Override
    public Card getDiscard() {
        double[][] prediction = getPrediction();
        oppHandPredictionSinceDiscard = prediction;
        ArrayList<Card> discardOptions = util.getAllowedDiscardOptions(hand, discardPile, prediction, illegalDiscard, constants);
        return util.determineDiscard(hand, discardPile, prediction, discardOptions, constants, debug);
    }

    @Override
    public void reportDiscard(int playerNum, Card discardedCard) {
        discardPile.add(discardedCard);
        if (playerNum == this.playerNum) {
            hand.remove(discardedCard);
            data.playerDiscard(discardedCard);
        } else {
            data.opponentDiscard(discardedCard);
            /*double avgOppDeadwood = 0;
            for(int i = 0; i < 30; i++){
                ArrayList<Card> oppHand = util.sampleHand(getPrediction(), 10);
                ArrayList<ArrayList<ArrayList<Card>>> meldSets = GinRummyUtil.cardsToBestMeldSets(oppHand);
                avgOppDeadwood += GinRummyUtil.getDeadwoodPoints(meldSets.isEmpty() ? new ArrayList<>() : meldSets.get(0), oppHand);
            }
            avgOppDeadwood /= 30;
            System.out.println("LOW: " + avgOppDeadwood);
            if(avgOppDeadwood < 18){
                GinRummyGame.setPlayVerbose(true);
            }*/
        }
    }

    public ArrayList<ArrayList<Card>> oppMelds;
    double[][] oppHandPredictionSinceDiscard;

    @Override
    public ArrayList<ArrayList<Card>> getFinalMelds() {
        ArrayList<ArrayList<ArrayList<Card>>> bestMeldSets = GinRummyUtil.cardsToBestMeldSets(hand);
        if (!opponentKnocked && (bestMeldSets.isEmpty() || GinRummyUtil.getDeadwoodPoints(bestMeldSets.get(0), hand) > GinRummyUtil.MAX_DEADWOOD))
            return null;
        if(opponentKnocked){
            if(bestMeldSets.isEmpty())
                return new ArrayList<>();
            if(bestMeldSets.size() == 1)
                return bestMeldSets.get(0);
            ArrayList<ArrayList<Card>> bestMeldSet = null;
            double maxLayoffValue = Double.NEGATIVE_INFINITY;
            for(ArrayList<ArrayList<Card>> meldSet : bestMeldSets){
                ArrayList<ArrayList<Card>> oppMeldsClone = new ArrayList<>();
                for(ArrayList<Card> meld : oppMelds)
                    oppMeldsClone.add(new ArrayList<>(meld));
                ArrayList<Card> melded = new ArrayList<>();
                for (ArrayList<Card> meld : meldSet) {
                    melded.addAll(meld);
                }
                ArrayList<Card> unmelded = util.getUnmelded(hand, melded);
                double value = layoffValue(unmelded, oppMeldsClone);
                if(value > maxLayoffValue){
                    maxLayoffValue = value;
                    bestMeldSet = meldSet;
                }
            }
            return bestMeldSet;
        }else{
            double avgOppDeadwood = 0;
            for(int i = 0; i < 30; i++){
                ArrayList<Card> oppHand = util.sampleHand(oppHandPredictionSinceDiscard, 10);
                ArrayList<ArrayList<ArrayList<Card>>> meldSets = GinRummyUtil.cardsToBestMeldSets(oppHand);
                avgOppDeadwood += GinRummyUtil.getDeadwoodPoints(meldSets.isEmpty() ? new ArrayList<>() : meldSets.get(0), oppHand);
            }
            avgOppDeadwood /= 30;
            if(avgOppDeadwood <= 18 && GinRummyUtil.getDeadwoodPoints(bestMeldSets.get(0), hand) > 6)
                return null;
            if(bestMeldSets.size() == 1)
                return bestMeldSets.get(0);
            ArrayList<ArrayList<Card>> bestMeldSet = null;
            double leastOppValue = Double.POSITIVE_INFINITY;
            for(ArrayList<ArrayList<Card>> meldSet : bestMeldSets){
                double avgOppValue = 0;
                for(int i = 0; i < 30; i++){
                    ArrayList<ArrayList<Card>> meldSetClone = new ArrayList<>();
                    for(ArrayList<Card> meld : meldSet)
                        meldSetClone.add(new ArrayList<>(meld));
                    ArrayList<Card> oppSample = util.sampleHand(oppHandPredictionSinceDiscard, 10);
                    ArrayList<ArrayList<ArrayList<Card>>> oppSampleMeldSets = GinRummyUtil.cardsToBestMeldSets(oppSample);
                    ArrayList<ArrayList<Card>> oppSampleMeldSet = oppSampleMeldSets.isEmpty() ? new ArrayList<>() : oppSampleMeldSets.get(0);
                    ArrayList<Card> oppSampleMelded = new ArrayList<>();
                    for(ArrayList<Card> meld : oppSampleMeldSet)
                        oppSampleMelded.addAll(meld);
                    ArrayList<Card> oppSampleUnmelded = util.getUnmelded(oppSample, oppSampleMelded);
                    avgOppValue += layoffValue(oppSampleUnmelded, meldSetClone);
                }
                avgOppValue /= 30;
                if(avgOppValue < leastOppValue){
                    leastOppValue = avgOppValue;
                    bestMeldSet = meldSet;
                }
            }
            return bestMeldSet;
        }
    }

    // Copied from ginrummy.GinRummyGame
    private double layoffValue(ArrayList<Card> unmelded, ArrayList<ArrayList<Card>> oppMelds) {
        boolean cardWasLaidOff;
        double oppValue = 0;
        do {
            cardWasLaidOff = false;
            Card layOffCard = null;
            ArrayList<Card> layOffMeld = null;
            for (Card card : unmelded) {
                for (ArrayList<Card> meld : oppMelds) {
                    ArrayList<Card> newMeld = new ArrayList<>(meld);
                    newMeld.add(card);
                    long newMeldBitString = GinRummyUtil.cardsToBitstring(newMeld);
                    if (GinRummyUtil.getAllMeldBitstrings().contains(newMeldBitString)) {
                        layOffCard = card;
                        layOffMeld = meld;
                        break;
                    }
                }
                if (layOffCard != null) {
                    unmelded.remove(layOffCard);
                    layOffMeld.add(layOffCard);
                    cardWasLaidOff = true;
                    oppValue += GinRummyUtil.getDeadwoodPoints(layOffCard);
                    break;
                }
            }
        } while (cardWasLaidOff);
        return oppValue;
    }

    @Override
    public void reportFinalMelds(int playerNum, ArrayList<ArrayList<Card>> melds) {
        if (playerNum != this.playerNum) {
            opponentKnocked = true;
            oppMelds = melds;
        }
    }

    @Override
    public void reportScores(int[] scores) {

    }

    @Override
    public void reportLayoff(int playerNum, Card layoffCard, ArrayList<Card> opponentMeld) {

    }

    @Override
    public void reportFinalHand(int playerNum, ArrayList<Card> hand) {

    }

    // Following methods used for sampling are from https://stackoverflow.com/questions/31019777/randomly-generating-combinations-from-variable-weights
    private double[] sizeDistribution(double[][] qs) {
        double[] sizeDist = new double[Card.NUM_CARDS + 1];
        sizeDist[0] = 1;
        int index = 1;
        for (double[] subQs : qs) {
            for (double q : subQs) {
                sizeDist[index] = 0;
                for (int j = index; j > 0; j--) {
                    sizeDist[j] += sizeDist[j - 1] * q;
                    sizeDist[j - 1] *= 1 - q;
                }
                index++;
            }
        }
        return sizeDist;
    }

    private double[] sizeDistributionWithout(double[] sizeDist, double q, double[][] ps) {
        double[] res = new double[sizeDist.length - 1];
        if (q >= 0.5) {
            res[res.length - 1] = sizeDist[sizeDist.length - 1];
            for (int j = sizeDist.length - 1; j > 1; j--) {
                res[j - 1] /= q;
                res[j - 2] = sizeDist[j - 1] - res[j - 1] * (1 - q);
            }
            res[0] /= q;
        } else {
            res[0] = sizeDist[0];
            for (int j = 1; j < sizeDist.length - 1; j++) {
                res[j - 1] /= 1 - q;
                res[j] = sizeDist[j] - res[j - 1] * q;
            }
            res[res.length - 1] /= 1 - q;
        }
        return res;
    }

    private void normalize(double[][] qs, int k) {
        double sum = 0;
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                sum += qs[suit][rank];
            }
        }
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                qs[suit][rank] *= k / sum;
            }
        }
    }

    private void fixProbabilities(double[][] ps, int k) {
        double sum = 0;
        int num_ignored = 0;
        for (double[] subPs : ps) {
            for (double p : subPs) {
                if (p != 1)
                    sum += p;
                else
                    num_ignored++;
            }
        }
        if(sum == 0)
            return;
        boolean again = false;
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                if (ps[suit][rank] != 1) {
                    ps[suit][rank] /= sum;
                    ps[suit][rank] *= (k - num_ignored);
                    if (ps[suit][rank] > 1) {
                        again = true;
                        ps[suit][rank] = 1;
                    }
                }
            }
        }
        if (again)
            fixProbabilities(ps, k);
    }

    private double[][] approximateQs(double[][] ps, int reps) {
        double sum = 0;
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                sum += ps[suit][rank];
            }
        }
        int k = (int) Math.round(sum);
        double[][] qs = new double[Card.NUM_SUITS][];
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            qs[suit] = ps[suit].clone();
        }
        for (int r = 0; r < reps; r++) {
            double[] sizeDist = sizeDistribution(qs);
            for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
                for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                    double[] d = sizeDistributionWithout(sizeDist, qs[suit][rank], ps);
                    double p = ps[suit][rank];
                    qs[suit][rank] = p * d[k] / ((1 - p) * d[k - 1] + p * d[k]);
                }
            }
            normalize(qs, k);
        }
        return qs;
    }

    public double[][] getPrediction() {
        INDArray prediction;
        if (model != null)
            prediction = model.output(data.data).reshape(4, 13);
        else {
            double[][] ps = new double[4][13];
            double p = 10.0 / (Card.NUM_CARDS - hand.size() - discardPile.size());
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
                    ps[suit][rank] = p;
                }
            }
            for (Card card : hand) {
                ps[card.suit][card.rank] = 0;
            }
            for (Card card : discardPile) {
                ps[card.suit][card.rank] = 0;
            }
            return ps;
        }
        double[][] probabilities = prediction.toDoubleMatrix();
        for (Card card : hand) {
            probabilities[card.suit][card.rank] = 0;
        }
        for (Card card : discardPile) {
            probabilities[card.suit][card.rank] = 0;
        }
        fixProbabilities(probabilities, 10);
        double[][] qs = approximateQs(probabilities, 100);
        if (debug) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
                    System.out.print(Card.getCard(rank, suit).toString() + ": " + String.format("%02d", (int) (Math.round(100 * probabilities[suit][rank]))) + " ");
                }
                System.out.println();
            }
        }
        return qs;
    }

    public static void main(String[] args) throws UnsupportedKerasConfigurationException, IOException, InvalidKerasConfigurationException {
        int numGames = 10;
        int[] scores = {0, 0};
        int numP1Wins = 0;
        GinRummyGame game = new GinRummyGame(new Player(), new Player());
        long startMs = System.currentTimeMillis();
        for (int i = 0; i < numGames; i++) {
            int[] res = game.play();
            scores[0] += res[0];
            scores[1] += res[1];
            if(res[1] > res[0])
                numP1Wins++;
            System.out.println(i);
        }
        long totalMs = System.currentTimeMillis() - startMs;
        System.out.printf("%d games played in %d ms.\n", numGames, totalMs);
        System.out.printf("Games Won: P0:%d, P1:%d.\n", numGames - numP1Wins, numP1Wins);
        System.out.printf("Average Scores: P0:%.2f, P1:%.2f.\n", (double)scores[0] / numGames, (double)scores[1] / numGames);
    }
}
