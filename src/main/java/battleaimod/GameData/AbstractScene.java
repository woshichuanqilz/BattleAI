package battleaimod.GameData;
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
//    abstract static public procRefBattle();
}
