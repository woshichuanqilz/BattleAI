package battleaimod.GameData;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.List;

public class Two_Louses_Scene extends AbstractScene {
    public Two_Louses_Scene() {
        super("TWO_LOUSES_SCENE", 2, "contain", new java.util.ArrayList<>());
        this.monster_list.add("Louse Normal");
    }

    @Override
    public String getEnemyType() {
        return "TWO_LOUSES_SCENE";
    }

    @Override
    public void procRefBattle() {
        int dmg = getTotalMonsterDamage();
        List<List<AbstractCard>> l = getMaxDamage(0);
        return;
    }

}
