package battleaimod.GameData;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

import java.util.ArrayList;
import java.util.List;

import javax.smartcardio.Card;
import java.util.ArrayList;

public abstract class AbstractScene {
    public String name;
    public int count;
    public String match_type;
    public ArrayList<String> monster_list;

    public <E> AbstractScene(String scene_name, int count, String match_type, ArrayList<String> monster_list) {
        this.name = scene_name;
        this.count = count;
        this.match_type = match_type;
        this.monster_list = monster_list;
    }

    abstract public String getEnemyType();
    abstract public void procRefBattle();
    public int getTotalMonsterDamage(){
        int total_damage = 0;
        MonsterGroup monster_group = AbstractDungeon.getMonsters();
        for (int i = 0; i < monster_group.monsters.size(); i++) {
            total_damage += monster_group.monsters.get(i).getIntentDmg();
        }
        return total_damage;
    }

    private void getCombinations(List<AbstractCard> cards, int targetEnergy, List<AbstractCard> temp, List<List<AbstractCard>> allCombinations) {
        if (targetEnergy < 0) return;
        if (targetEnergy == 0 || cards.isEmpty()) {
            allCombinations.add(new ArrayList<>(temp));
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            AbstractCard card = cards.get(i);
            if (card.type == AbstractCard.CardType.ATTACK) {
                temp.add(card);
                getCombinations(cards.subList(i + 1, cards.size()), targetEnergy - card.costForTurn, temp, allCombinations);
                temp.remove(temp.size() - 1); // backtrack
            }
        }
    }

    public List<List<AbstractCard>> getMaxDamage(int energyAdjust) {
        int energy = EnergyPanel.totalCount + energyAdjust;
        List<AbstractCard> attackCards = new ArrayList<>();
        for (AbstractCard card : AbstractDungeon.player.hand.group) {
            if (card.type == AbstractCard.CardType.ATTACK) {
                attackCards.add(card);
            }
        }
        List<List<AbstractCard>> allCombinations = new ArrayList<>();
        getCombinations(attackCards, energy, new ArrayList<>(), allCombinations);
        return allCombinations;
    }
}
