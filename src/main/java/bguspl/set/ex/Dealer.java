package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Random;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    /**
     * containes id of a player that has set to check
     */
    private volatile int hasSetToCheck;
    /**
     * the set is correct 
     */
    private volatile boolean isSet;

     /**
     * Semaphore of the dealer 
     */
    Semaphore s;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        //playersWithSet = new ArrayBlockingQueue<Player>(players.length);    
        hasSetToCheck= -1;
        this.s=new Semaphore(players.length,true);  
        isSet = false; 
        terminate=false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player:players){
            Thread playerThread = new ThreadLogger(player,Integer.valueOf(player.id).toString(),env.logger);
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
            }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        // updateReshuffleTime();
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            if (System.currentTimeMillis() < reshuffleTime) {          
                removeCardsFromTable();
                placeCardsOnTable();
                hasSetToCheck=-1;
                updateTimerDisplay(isSet);
                isSet=false;
                
            } 
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate=true;
        

    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() { //cards from table ,token from table ,token from player
        // TODO implement
        if(hasSetToCheck!=-1){
            LinkedList<Integer> playersTokens = table.playersTokens[hasSetToCheck];
            if (playersTokens.size()==3){ // still has a full set to check
                int[] setToCheck = new int[3]; // array of slots
                setToCheck[0]= playersTokens.removeFirst();
                setToCheck[1]=playersTokens.removeFirst();
                setToCheck[2]=playersTokens.removeFirst();
                isSet=env.util.testSet(setToCheck);
                if(isSet){
                    synchronized(table){
                        for(int i = 0; i<3 ;i++){
                                table.removeCard(setToCheck[i]); //remove card from table 
                                for (int j=0; j<players.length;j++){ 
                                    if (table.playersTokens[j].contains(setToCheck[i])){
                                        table.removeToken(j,setToCheck[i]);  //remove token from table for specific player
                                        players[j].removeTokenFromActionsQueue(setToCheck[i]); //remove token from player
                                    }
                                }
                        }
                    }
                    players[hasSetToCheck].point();
                } else {
                    players[hasSetToCheck].penalty();
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        if(table.countCards() < env.config.tableSize && deck.size()!=0 ){
            synchronized(table){
                for(int i=0; i<table.slotToCard.length; i++){
                    if(table.slotToCard[i]==null){
                        Random rand= new Random();
                        int card =deck.get(rand.nextInt(deck.size()));
                        table.placeCard(card,i);
                        deck.remove(deck.indexOf(card)); 
                    }
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        while(hasSetToCheck==-1){
            try {
                Thread.sleep(1000);
                break;
            }catch (InterruptedException interrupt){
                continue;}
        }  
  
    }
    

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (!reset){
            env.ui.setCountdown(reshuffleTime-1000-System.currentTimeMillis(), false);
        }
        else{ //set was found
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            updateReshuffleTime();
         }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
       synchronized(table){ 
            for (int slot :table.cardToSlot){
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
            table.removeAllTokensFromTable();
            removeActionsFromPlayers();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        List<Integer> winList=new LinkedList<Integer>();
        int maxScore=-1;
        for(int i=0; i<players.length;i++){ //finds max score 
            if(players[i].score()>maxScore){
                maxScore=players[i].score();
            }  
        }
        for(int i=0; i<players.length;i++){ //looking for players with max score
            if(players[i].score()==maxScore){
                winList.add(players[i].id);
            }
        }
        int[] winners = new int[winList.size()]; //creating an array of winners
        int place=0;
        for (int id :winList){
            winners[place]=id;
            place++;
        }
        env.ui.announceWinner(winners);
        terminate();
    }

    public void removeActionsFromPlayers(){
        for(Player player:players){
            player.removeAllActions();
        }
    }

    private synchronized void updateReshuffleTime(){
        reshuffleTime=System.currentTimeMillis()+env.config.turnTimeoutMillis;
    }

    // public int getHasSetToCheck(){
    //     return hasSetToCheck;
    // }
    public void setHasSetToCheck(int id){
        hasSetToCheck=id;
    }
}

