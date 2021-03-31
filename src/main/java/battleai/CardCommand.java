package battleai;

import com.megacrit.cardcrawl.actions.utility.NewQueueCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import communicationmod.CommunicationMod;

public class CardCommand implements Command {

    private final int cardIndex;
    private final int monsterIndex;
    private String displayString;

    public CardCommand(int cardIndex, int monsterIndex, String displayString) {
        this.cardIndex = cardIndex;
        this.monsterIndex = monsterIndex;
        this.displayString = displayString;
    }

    public CardCommand(int cardIndex, String displayString) {
        this.cardIndex = cardIndex;
        this.monsterIndex = -1;
        this.displayString = displayString;
    }

    @Override
    public void execute() {
        AbstractCard card = AbstractDungeon.player.hand.group.get(cardIndex);
        AbstractMonster monster = null;

        if (monsterIndex != -1) {
            monster = AbstractDungeon.getMonsters().monsters.get(monsterIndex);
        }


//        card.use(AbstractDungeon.player, monster);
//        AbstractDungeon.player.useCard(card, monster, card.cost);
//        AbstractDungeon.actionManager.
//        AbstractDungeon.actionManager.addToTop((AbstractGameAction)new UseCardAction(card, monster));
        AbstractDungeon.actionManager
                .addToTop(new NewQueueCardAction(card, monster));
        AbstractDungeon.actionManager.update();
//        AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, monster));
        CommunicationMod.readyForUpdate = true;
    }

    @Override
    public String toString() {
        return displayString + monsterIndex;
    }
}