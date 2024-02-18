package bguspl.set.ex;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Iterator;
import java.util.Random;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    protected Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    /**
     * The Dealer that controls the game 
     */
    private Dealer dealer;
    /**
     * The class constructor.
     */
    protected ArrayBlockingQueue<Integer> playersActions;
     /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     * @param playersActions  - actions that the player does. 
     
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer=dealer;
        this.playersActions = new ArrayBlockingQueue<Integer>(3);
    }
    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        // TODO implement main player loop
        while (!terminate) {
            synchronized(playerThread){
                while(playersActions.isEmpty()){
                    try {
                        wait();
                    } catch (InterruptedException intterupt) {}
                }
                while(!playersActions.isEmpty()){
                    int currAction =(playersActions.poll());
                    if (!table.removeToken(id, currAction)){
                        table.placeToken(id,currAction);
                    }
                }
                if(table.playersTokens[id].size()==3){
                    try {
                        dealer.s.acquire();
                        dealer.setHasSetToCheck(id);  
                    } catch (InterruptedException interrupt) {}
                    dealer.s.release();
                }

            }
        }    
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                while(playersActions.size()==3){ 
                    try {
                        synchronized (this) { wait(); }
                    } catch (InterruptedException ignored) {}
                }
                Random rand = new Random();
                keyPressed(rand.nextInt(env.config.tableSize));
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate=true;

    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
     ///// need to add condition for cardspressed אם יש 3 נבחרים ורוצים לבחור אחד נוסף זה לא ימנע כי הa action ריק 
        if((table.playersTokens[id].size()==3&&table.playersTokens[id].contains(slot))|(table.playersTokens[id].size()<3)){
          playersActions.add(slot);
        }
        playerThread.notifyAll();
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        synchronized(playerThread){
            int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.ui.setScore(id, ++score);
            score=score++;
            try {
                env.ui.setFreeze(id, env.config.pointFreezeMillis);
                Thread.sleep(env.config.pointFreezeMillis);
            } catch (InterruptedException interrupt ) {}
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        //TODO implement
        synchronized(playerThread){
            try {
                env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
                Thread.sleep(env.config.penaltyFreezeMillis);
            } catch (InterruptedException interrupt ) {}
        }
    }
    public int score() {
        return score;
    }

    public void removeTokenFromActionsQueue(int slot){ //the method will remove the token from the player's actions queue
        playersActions.remove(slot);
    }

    public void removeAllActions(){
        playersActions.clear();
    //    notifyAll();
    }


}
