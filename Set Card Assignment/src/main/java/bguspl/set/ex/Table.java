package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * true iff the dealre is working on the table 
     */
    public boolean flagIsdealerWorking;

    /**
     * Constructor for testing.
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        flagIsdealerWorking=true;
    }

    /**
     * Constructor for actual usage.
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        flagIsdealerWorking=true;
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * @return - slotsOnTable  that contains the number of slots on the table that are filled with cards
     * creat list of the slots that have card 
     */
    public List<Integer> slotsOnTable(){
        List <Integer> slotsOnTable=new LinkedList<>();
        for (Integer slot : cardToSlot)
            if (slot != null)
                slotsOnTable.add(slot);
        return slotsOnTable;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        synchronized(this){
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
        this.notifyAll();
        }
    }

    /**
     * @post - update the ui that card in place of slot is null
     * @post - cardToSlot[card] is null
     * @post - slotToCard[slot] is null
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot,int card) {
        synchronized(this){
        cardToSlot[card] = null;
        slotToCard[slot] = null;
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        
            env.ui.removeCard(slot);
            this.notifyAll();

        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        synchronized(this){
            while(flagIsdealerWorking){
                try {
                    this.wait();
                } catch (InterruptedException e) {} 
                }
            env.ui.placeToken(player, slot);
            this.notifyAll();
         }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     */
    public void removeToken(int player, int slot) {
        synchronized(this){
            while(flagIsdealerWorking){
                try {
                    this.wait();
                } catch (InterruptedException e) {} 
            }
            if(slotToCard[slot]!=null)
                env.ui.removeToken(player, slot); 
            this.notifyAll(); 
        }
    }
    
    /**
     * Removes all tokens of  a grid slot.
     * @param slot   - the slot from which to remove the token.
     */
    public void removeTokensFromSpecifieSlot( List<Integer>slots) {
        for(int i=0; i<slots.size();i++){
            env.ui.removeTokens(slots.get(i));
        }
    }

    /**
     * Removes all tokens from the desk.
     */
    public void removeAllToken() {
        synchronized(this){
            env.ui.removeTokens();
        }
    }

    /**
     * checks if there are sets on the table
     */
    public boolean isThereSetsOnTable(){
        List<Integer>slots=new LinkedList<>();
        for(int i=0;i< slotToCard.length;i++){
            slots.add(slotToCard[i]);
        }
        return env.util.findSets(slots, 1).size()!=0;
    }

}
