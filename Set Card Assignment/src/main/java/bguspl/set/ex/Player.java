package bguspl.set.ex;

import java.time.chrono.ThaiBuddhistChronology;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import javax.sql.rowset.spi.SyncFactory;

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
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * queue of slots of the placed tokens
     */
    public List <Integer> tokensQueue;

      /**
     * one seconde in miliseconde.
     */
    private final long SECONDE=1000;
     /**
     * 0.6 seconde in miliseconde.
     */
    private final long SECONDE_0_6=600;
    /*
     * the dealer object
     */
    public Dealer dealer;
    /*
     * true iff the player can insert tokens to his queue
     */
     public boolean canInsert;

     /*
     * true iff the player can remove tokens after his set got checked
     */
    public boolean RemoveAfterPenalty;

    /*
     * thread of the current player
     */
    public Thread playerTread;

    /**
     * state of penalty/score : 0- regular (didn't have 3 tokens)
     *                          1- gets penalty
     *                          2- gets point
     */
    public int state;
     /**
     *   state 0 -regular (didn't have 3 tokens)
     */
    private final int STATE_0 = 0;
    /**
     *   state 1- gets penalty.
     */
    private final int STATE_1 = 1;
    /**
     *   state 2- gets point.
     */
    private final int STATE_2 = 2;

    /**
     *   The size which can be send to check if there is a set.
     */
    private final int SETSIZE = 3;
    /**
     * atomicInteger of the size of the token queue.
     */
    private AtomicInteger AtomicList;
    
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer=dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        tokensQueue=new LinkedList<Integer>();
        state=STATE_0;
        playerThread=Thread.currentThread();
        canInsert=true;
        RemoveAfterPenalty=false; 
        AtomicList= new AtomicInteger();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread=Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            if(tokensQueue.size()==SETSIZE && RemoveAfterPenalty==false && state==STATE_0){
                    canInsert=false;                    
                    dealer.insertCheckSetQueue(id); // insert the player to check queue line
                    try {
                        synchronized (this) { wait(); }
                    } catch (InterruptedException ignored) {}
                    if(state==STATE_1){
                        penalty();
                    }
                    else if(state==STATE_2){
                        point();
                    }
                    if (!human) 
                        aiThread.interrupt();
            }
            state=STATE_0;
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }


    /*
     * return token queue player
     */
    public List <Integer> getTokenQueue(){
        return tokensQueue;
    }

    /*
     * return the player to the start position.
     */
    public void InitPlayerAfterTimeRunOut (){
        canInsert=false;
        RemoveAfterPenalty=false;
        removeAllTokensFromQueue();
        state=STATE_0;
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
       aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                List<Integer> givenSlots=Arrays.asList(0,1,2,3,4,5,6,7,8,9,10,11);
                Random random=new Random();
                int token;
                while(canInsert && tokensQueue.size()<=SETSIZE){
                    token=random.nextInt(givenSlots.size());//ganerate random number
                    try {
                        Thread.sleep(0);
                    } catch (InterruptedException e) {}

                    keyPressed(token);
                }
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }


    /**
     * @post - player's thread is awake and his field of terminate=true
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        Thread.currentThread().interrupt();
        terminate=true;
    }

    /**
     * return player boolean terminate (for tests).
     */
    public Boolean getTerminate(){
        return terminate;
    }

    /**
     * This method is called when a key is pressed.
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(table.slotToCard[slot]!=null){
            if(canInsert){
                int slotIndex=-1;
                for(int i=0 ; i<tokensQueue.size() ; i++) //checks if the token exist in the queue 
                    if(tokensQueue.size()>0 ){
                        if(tokensQueue.get(i)==slot){
                        slotIndex=i;
                    }
                }
                
               if(slotIndex!=-1 && !tokensQueue.isEmpty()){
                int listSize;
                int listsizechanged;
                do{
                    listSize=tokensQueue.size();
                       table.removeToken(id, slot);
                       if(tokensQueue.size()>0)
                            tokensQueue.remove(slotIndex); //remove from the players queue
                       if(RemoveAfterPenalty)
                           RemoveAfterPenalty=false;
                    listsizechanged=tokensQueue.size();

                } while(AtomicList.compareAndSet(listSize, listsizechanged));
                
               }
               else if(slotIndex==-1 && canInsert && tokensQueue.size()<SETSIZE){
                  table.placeToken(id, slot);
                  tokensQueue.add(slot);    
               }
          }
      }   
    }

    /**
     * Award a point to a player and perform other related actions.
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score++;
        
        long sleepTime=env.config.pointFreezeMillis+System.currentTimeMillis();
        if(sleepTime-System.currentTimeMillis()==0){
            env.ui.setScore(id,score);
        }
        while(sleepTime-System.currentTimeMillis()>0){
            try {
                env.ui.setFreeze(id, sleepTime-System.currentTimeMillis()+SECONDE_0_6);
               Thread.sleep(SECONDE);
                env.ui.setScore(id,score);
                Thread.currentThread().interrupt();
                env.ui.setFreeze(id, 0);
            } catch (InterruptedException e) {}
        }
        removeAllTokensFromQueue();
        canInsert=true;
    }

    /**
     * @post - the player can't insert or remove tokens for penaltyFreezeMillis time
     * @post - update ui with freez time of player each seconde
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        int loops=(int) env.config.penaltyFreezeMillis/(int)SECONDE;
        for(int i=0 ; i<loops ; i++){
            try{
                
                env.ui.setFreeze(id,(loops-i)*SECONDE );
                Thread.sleep(SECONDE);
                if(i+1==loops){
                    Thread.currentThread().interrupt();
                    env.ui.setFreeze(id,(loops-i)*SECONDE-SECONDE );
                }
            }
            catch (InterruptedException e) {}
        }
        canInsert=true;
         RemoveAfterPenalty=true;
    }

     /** 
     * return playerScore.
     */
    public int score() {
        return score;
    }
    
     /** 
     * return playerAiThread.
     */
    public Thread getAiThread(){
        return aiThread;
    }

    /** 
     * return is player human.
     */
    public boolean isHuman(){
        return human;
    }

    /** 
     * remove all the tokens from the player queue when the dealer clear the table.
     */
    public void removeAllTokensFromQueue() {
        tokensQueue.clear();
    }

    /**
     * remove the tokens from the player queue
     * returns true iff we removed some tokens
     */
    public boolean removeTokensFromQueue(List <Integer> slots) {
        boolean removed=false;
        for(int i=0 ; i<slots.size() ; i++){
            if(tokensQueue.size()>0 && tokensQueue.contains(slots.get(i))){
                if(dealer.CheckSetRequestQueue.contains(id)){
                    dealer.CheckSetRequestQueue.remove(id);
                }
                tokensQueue.remove(slots.get(i));
                removed=true;
            }
        }
      
        if (!human) 
            aiThread.interrupt();
        return removed;
    }
}
