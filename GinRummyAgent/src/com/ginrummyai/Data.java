package com.ginrummyai;

import ginrummy.Card;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;

public class Data {
    public INDArray data;
    public int turn;

    private static final int handDiscardData = 0;
    private static final int opponentDrawData = Card.NUM_CARDS;
    private static final int opponentDiscardData = Card.NUM_CARDS * 2;
    private static final int opponentAvoidanceData = Card.NUM_CARDS * 3;

    public Data(ArrayList<Card> hand, ArrayList<Card> discardPile) {
        data = Nd4j.zeros(1, 208);
        for (Card card : hand)
            data.putScalar(new int[]{0, handDiscardData + card.getId()}, 1);
        for (Card card : discardPile)
            data.putScalar(new int[]{0, handDiscardData + card.getId()}, -1);
        turn = 1;
    }

    public void undoPlayerDraw(Card card) {
        data.putScalar(new int[]{0, handDiscardData + card.getId()}, 0);
    }

    public void playerDraw(Card card) {
        data.putScalar(new int[]{0, handDiscardData + card.getId()}, 1);
    }

    public void opponentDrawFromDiscard(Card card) {
        data.putScalar(new int[]{0, opponentDrawData + card.getId()}, turn);
        data.putScalar(new int[]{0, handDiscardData + card.getId()}, 0);
    }

    public void opponentDrawFromDeck(Card avoided) {
        data.putScalar(new int[]{0, opponentAvoidanceData + avoided.getId()}, turn);
    }

    public void playerDiscard(Card card) {
        data.putScalar(new int[]{0, handDiscardData + card.getId()}, -1);
    }

    public void opponentDiscard(Card card) {
        data.putScalar(new int[]{0, opponentDiscardData + card.getId()}, turn);
        data.putScalar(new int[]{0, handDiscardData + card.getId()}, -1);
        turn++;
    }
}
