package com.ginrummyai;

import ginrummy.Card;
import ginrummy.GinRummyUtil;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class GameTester {
    public static Player player;
    public static Scanner sc;

    public static void PlayerDrawInitial(){
        System.out.print("Enter Discard Pile Card: ");
        Card card = Card.strCardMap.get(sc.nextLine());
        player.debug = false;
        if(player.willDrawFaceUpCard(card)){
            System.out.println("Drawing " + card.toString());
            player.reportDraw(1, card);
            player.debug = true;
            PlayerDiscard();
        }else{
            System.out.println("Passing " + card.toString());
        }
        player.debug = true;
    }

    public static void PlayerDrawInitial2(){
        System.out.print("Enter Card Drawn: ");
        Card card = Card.strCardMap.get(sc.nextLine());
        System.out.println("Drawing " + card.toString());
        player.reportDraw(1, card);
        PlayerDiscard();
    }

    public static void PlayerDraw(){
        GRUtil util = new GRUtil();
        double avgOppDeadwood = 0;
        for(int i = 0; i < 30; i++){
            ArrayList<Card> oppHand = util.sampleHand(player.getPrediction(), 10);
            ArrayList<ArrayList<ArrayList<Card>>> meldSets = GinRummyUtil.cardsToBestMeldSets(oppHand);
            avgOppDeadwood += GinRummyUtil.getDeadwoodPoints(meldSets.isEmpty() ? new ArrayList<>() : meldSets.get(0), oppHand);
        }
        avgOppDeadwood /= 30;
        System.out.println("Average Opp Deadwood: " + avgOppDeadwood);
        System.out.println("Player Draw");
        Card card = player.discardPile.get(player.discardPile.size() - 1);
        player.debug = false;
        if(player.willDrawFaceUpCard(card)){
            System.out.println("Drawing " + card.toString());
            player.reportDraw(1, card);
        }else{
            System.out.println("Drawing from deck");
            System.out.print("Enter Card Drawn: ");
            Card draw = Card.strCardMap.get(sc.nextLine());
            System.out.println("Drawing " + draw.toString());
            player.reportDraw(1, draw);
        }
        player.debug = true;
        PlayerDiscard();
    }

    public static void OppDraw(){
        System.out.println("Opp Draw");
        System.out.print("Drawn from discard pile? (0=No; 1=Yes): ");
        String cardString = sc.nextLine();
        if(cardString.equals("0")){
            System.out.println("Opponent drew from deck");
            player.reportDraw(0, null);
        }else{
            Card card;
            if(player.discardPile.isEmpty()){
                System.out.print("Enter card drawn: ");
                card = Card.strCardMap.get(sc.nextLine());
            }else
                card = player.discardPile.get(player.discardPile.size() - 1);
            System.out.println("Opponent drew " + card.toString() + " from discard pile");
            player.reportDraw(0, card);
        }
        OppDiscard();
    }

    public static void PlayerDiscard(){
        System.out.println("Player Discard");
        Card card = player.getDiscard();
        System.out.println("Discarding " + card.toString());
        player.reportDiscard(1, card);
        OppDraw();
    }

    public static void OppDiscard(){
        System.out.println("Opp Discard");
        System.out.print("Enter card discarded: ");
        Card card = Card.strCardMap.get(sc.nextLine());
        System.out.println("Opponent discarding " + card.toString());
        player.reportDiscard(0, card);
        PlayerDraw();
    }

    public static void main(String[] args) throws UnsupportedKerasConfigurationException, IOException, InvalidKerasConfigurationException {
        Card.strCardMap.put("10C", Card.getCard(9, 0));
        Card.strCardMap.put("10H", Card.getCard(9, 1));
        Card.strCardMap.put("10S", Card.getCard(9, 2));
        Card.strCardMap.put("10D", Card.getCard(9, 3));
        sc = new Scanner(System.in);
        System.out.print("Enter Opening Hand: ");
        String openingHand = sc.nextLine();
        Card[] hand = new Card[10];
        for(int i = 0; i < 10; i++){
            String card = openingHand.substring(3 * i, 3 * i + 2);
            hand[i] = Card.strCardMap.get(card);
        }
        System.out.print("Going First? (0=NO; 1=YES): " );
        int first = sc.nextInt();
        sc.nextLine();
        player = new Player();
        player.startGame(1, first, hand);
        player.debug = true;
        if(first == 1){
            PlayerDrawInitial();
            System.out.println("Did Opp draw from discard (0=NO; 1=YES): ");
            int draw = sc.nextInt();
            sc.nextLine();
            if(draw == 1)
                OppDraw();
            PlayerDrawInitial2();
        }else{
            System.out.println("Did Opp draw from discard (0=NO; 1=YES): ");
            int draw = sc.nextInt();
            sc.nextLine();
            if(draw == 1)
                OppDraw();
            PlayerDrawInitial();
            OppDraw();
        }
        while(true){
            System.out.print("Enter Action: ");
            String input = sc.nextLine();
            if(input.equals("PDI")){
                PlayerDrawInitial();
            }else if(input.equals("PDI2")){
                PlayerDrawInitial2();
            }else if(input.equals("OD")){
                OppDraw();
            }
        }
    }
}
