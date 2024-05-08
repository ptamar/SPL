package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.UserInterfaceDecorator;

import java.lang.reflect.Array;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * players Queue to check their set
     */
    public Deque<Integer> CheckSetRequestQueue;

    public volatile int CheckSetRequestQueueSize;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The amount of time the timer needs to sleep.
     */
    private long sleep;

    /**
     * reference to dealer's thread
     */
    public Thread dealThread;

    /**
     * players threads array
     */
    private Thread[] ArrayPlayersThreads;

     /**
     * 0.1 seconde in miliseconde.
     */
    private final long SECONDE_10 = 10;
    /**
     * one seconde in miliseconde.
     */
    private final long SECONDE = 1000;
    /**
     * 10 seconde in miliseconde.
     */
    private final long SECONDE_10_k = 10000;
    /**
     * minute plus seconed in miliseconde.
     */
    private final long MINUTE = 60999;

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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        CheckSetRequestQueue = new LinkedList<>();
        reshuffleTime = env.config.turnTimeoutMillis;
        sleep = SECONDE;
        dealThread = Thread.currentThread();
        ArrayPlayersThreads = new Thread[players.length];
        terminate = false;
        CheckSetRequestQueueSize = 0;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        startPlayersThreads(); // creats Players Threads
        while (!shouldFinish()) {
            placeCardsOnTable();
            AblePlayersInsertTokens();
            timerLoop();
            UnablePlayersInsertTokens();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        table.removeAllToken();
        announceWinners();

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        terminate();
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + MINUTE;
        AblePlayersInsertTokens();
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(true);

            CheckSetFromQueue();

            terminate = shouldFinish();
        }
        initAllPlayers();
    }

    /**
     * check if their are sets waits to be checked and checks them.
     */
    private void CheckSetFromQueue() {
        boolean isThereLegalSet;
        while (CheckSetRequestQueue.size() > 0) { // the queue is not empty
            int PlayerId = CheckSetRequestQueue.removeLast();
            List<Integer> slots1 = new LinkedList<Integer>(players[PlayerId].getTokenQueue());
            List<Integer> slots2 = new LinkedList<Integer>(players[PlayerId].getTokenQueue());
            List<Integer> slots3 = new LinkedList<Integer>(players[PlayerId].getTokenQueue());
            List<Integer> slots4 = new LinkedList<Integer>(players[PlayerId].getTokenQueue());

            isThereLegalSet = IsSet(slots1, PlayerId);
            if (isThereLegalSet) {
                players[PlayerId].state = STATE_2;
                ArrayPlayersThreads[PlayerId].interrupt();
                makePlayersRemoveTokensFromSlots(slots1);// remove the tokens from the slot in the players queue
                table.flagIsdealerWorking = true;
                table.removeTokensFromSpecifieSlot(slots4);// remove the tokens from the slot in the table
                removeCardsFromTable(slots2);
                placeCardsOnTable(slots3);
                table.flagIsdealerWorking = false;
            } else {
                players[PlayerId].state = STATE_1;
                ArrayPlayersThreads[PlayerId].interrupt();
            }
        }
    }

    /*
     * Checks if The player has a legel Set, accordingly call point\panalized
     * return true if it is a legal set, otherwise false
     */
    public boolean IsSet(List<Integer> slots, int playerID) {
        boolean isLegalSet;
        int[] cards = new int[slots.size()];
        for (int i = 0; i < cards.length; i++)
            cards[i] = table.slotToCard[slots.get(i)];
        isLegalSet = env.util.testSet(cards);
        return isLegalSet;
    }

    /**
     * @post - all players with fields: canInsert=false, RemoveAfterPenalty=false, state=0 and empty token queue
     * init all players after interation- time run out
     */
    public void initAllPlayers() {
        for (int i = 0; i < players.length; i++) {
            players[i].InitPlayerAfterTimeRunOut();
            ArrayPlayersThreads[i].interrupt();
            if (!players[i].isHuman())
                players[i].getAiThread().interrupt();
            ;
        }
    }

    /**
     * change 'canInsert' field of all players to false- can't insert tokens
     */
    private void UnablePlayersInsertTokens() {
        for (int i = 0; i < players.length; i++) {
            players[i].canInsert = false;
        }
    }

    /**
     * change 'canInsert' field of all players to false- can't insert tokens
     */
    private void AblePlayersInsertTokens() {
        for (int i = 0; i < players.length; i++) {
            players[i].canInsert = true;
        }
    }

    /**
     * The function is called when player had set
     * removes token from player's queue if the token of the set was used.
     * 
     * @param slots - the set to check
     */
    private void makePlayersRemoveTokensFromSlots(List<Integer> slots) {
        boolean removedPlayersToken = false;
        for (int i = 0; i < players.length; i++) {
            removedPlayersToken = players[i].removeTokensFromQueue(slots);
            if (removedPlayersToken) { // if we removed token of player, remove him from checkSetQueue
                players[i].canInsert = true;
                ArrayPlayersThreads[i].interrupt();
                if (!players[i].isHuman())
                    players[i].getAiThread().interrupt();
            }
        }
    }

    // creats Players Threads
    private void startPlayersThreads() {
        int i = 0;
        while (i < players.length) {
            Thread playerThread = new Thread(players[i], "player" + i);
            ArrayPlayersThreads[i] = playerThread;
            playerThread.start();
            i++;
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (Player player : players)
            player.terminate();
        Thread.currentThread().interrupt();
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return (terminate || (env.util.findSets(deck, 1).size() == 0 && table.isThereSetsOnTable()));
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(List<Integer> slots) {
        if (slots.size() == SETSIZE) {
            Thread.currentThread().interrupt();
            for (int i = 0; i < slots.size(); i++) {
                table.removeCard(slots.get(i), table.slotToCard[slots.get(i)]);// remove the cards from the table
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * assumes that recieves empty table
     */
    private void placeCardsOnTable() {
        table.flagIsdealerWorking = true;
        int cardsInDeck = deck.size();
        int numCardsToPlace = Math.min(env.config.tableSize, cardsInDeck); // limit the number of card to place
         // according to the left amount in the deck
        if (numCardsToPlace > 0) { // there are remained cards in the Deck
            List<Integer> slotList = new LinkedList<Integer>();
            for (int j = 0; j < numCardsToPlace; j++) // creats list of the available slots
                slotList.add(j);
            Random rand = new Random();
            for (int i = 0; i < numCardsToPlace; i++) {
                int maxslot = slotList.size();
                int randspotidlist = rand.nextInt(maxslot); // generate index in the list of the spots
                int slot = slotList.get(randspotidlist); // slot - the value of the slot
                int indexOfcard = rand.nextInt(deck.size());
                int card = deck.get(indexOfcard); // card- the value of the card
                table.placeCard(card, slot);
                RemoveCardsFromDeck(indexOfcard); // after the card was placed on table- it is deleted from the deck
                slotList.remove(randspotidlist); // the slot is no more availble
            }
        }
        table.flagIsdealerWorking = false;
    }

    /*
     * place cards in Table if a legal set was found
     */
    private void placeCardsOnTable(List<Integer> slots) {
        table.flagIsdealerWorking = true;
        int cardsInDeck = deck.size();
        int numCardsToPlace = Math.min(slots.size(), cardsInDeck); // limit the number of card to place according to the
         // left amount in the deck
        if (numCardsToPlace > 0) { // there are remained cards in the Deck
            Random rand = new Random();
            for (int i = 0; i < numCardsToPlace; i++) {
                int maxslot = slots.size();
                int randspotidlist = rand.nextInt(maxslot); // generates a number between 0 to maxslot-1
                int slot = slots.get(randspotidlist);
                int indexOfCard = rand.nextInt(deck.size());
                int card = deck.remove(indexOfCard);
                table.placeCard(card, slot);
                slots.remove(randspotidlist); // the slot is no more availble
            }
        }
        table.flagIsdealerWorking = false;
    }

    /**
     * remove cards from the deck
     */
    public void RemoveCardsFromDeck(int indexOfcard) {
        deck.remove(indexOfcard);
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(sleep);

        } catch (InterruptedException wakeupDealer) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reshuffleTime - System.currentTimeMillis() < SECONDE_10_k && reshuffleTime - System.currentTimeMillis() > 0.000) {
            sleep = SECONDE_10;
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), true);
        } else {
            sleep = SECONDE;
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), false);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.flagIsdealerWorking = true;
        table.removeAllToken();
        Random rand = new Random();
        int cardsOnTable = table.countCards();
        List<Integer> slotsOnTable = table.slotsOnTable();
        while (cardsOnTable > 0) {
            int maxslot = slotsOnTable.size();
            int randspotidlist = rand.nextInt(maxslot); // generate index in the list of the spots
            int slot = slotsOnTable.get(randspotidlist); // slot - the value of the slot
            if (table.slotToCard[slot] != null) {
                int card = table.slotToCard[slot]; // card- the value of the card
                deck.add(card);
                table.removeCard(slot, card);
                slotsOnTable.remove(randspotidlist); // the slot is no more availble
                cardsOnTable = table.countCards();

            }

        }
        // remove all tokens from the players queue
        for (int i = 0; i < players.length; i++) {
            players[i].removeAllTokensFromQueue();// remove all the tokens from the players queue
        }
        // remove all sets from dealer queue
        CheckSetRequestQueue.clear();
        // update timer
        updateTimerDisplay(true);
        table.flagIsdealerWorking = false;
        // table.notifyAllOnLock();
    }

    /**
    * @pre CheckSetRequestQueue without playerID
     * @post CheckSetRequestQueue with playerID in the the head of the queue
     * Insert player Id of the player that need the dealer to check its queue.
     */
    public void insertCheckSetQueue(int playerID) {
        synchronized (this) {
            if (!CheckSetRequestQueue.contains(playerID)) {
                CheckSetRequestQueue.addFirst(playerID);
                CheckSetRequestQueueSize++;
            }
        }
    }

    /**
     * 
     * @return CheckSetRequestQueue
     */
    public Deque<Integer> getCheckSet() {
        return CheckSetRequestQueue;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        Thread.currentThread().interrupt();
        int maxScore = 0;
        int countwinners = 0;
        int[] winnerPlayers = new int[1];

        for (int i = 0; i < players.length; i++) { // find highest score
            int playerScore = players[i].score();
            if (playerScore > maxScore) {
                maxScore = playerScore;
                countwinners = 1;
                winnerPlayers[0] = players[i].id;
            } else if (playerScore == maxScore) // more than 1 player have the highest score
                countwinners++;
        }
        // creates an array of winners if there is a tie
        if (countwinners > 1) {
            winnerPlayers = new int[countwinners];
            int placeInWinnerPlayers = 0;
            for (int i = 0; i < players.length; i++) {
                if (players[i].score() == maxScore) {
                    winnerPlayers[placeInWinnerPlayers] = players[i].id;
                    placeInWinnerPlayers++;
                }
            }
        }
        env.ui.announceWinner(winnerPlayers);
    }

    /**
     * 
     * @return deck
     */
    public List<Integer> getDeck() {
        return deck;
    }

}
