package battleaimod.GameData;

public class Two_Louses_Scene extends AbstractScene {
    public Two_Louses_Scene() {
        super("TWO_LOUSES_SCENE", 2, "contain", new java.util.ArrayList<>());
        this.monster_list.add("Louse Normal");
    }

    @Override
    public String getEnemyType() {
        return "TWO_LOUSES_SCENE";
    }
}
