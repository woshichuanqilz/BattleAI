package battleaimod.utils;

import battleaimod.GameData.EnemyType;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.localization.UIStrings;

import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Map;

public class OriUtils {
    public static ArrayList<String> getEnemyTypes(){
        Gson gson = new Gson();

        try {
            // Read the JSON file
            FileReader reader = new FileReader("EnemyType.json");

            // read the content of key enemy_types
            Map enemy_type_map = gson.fromJson(reader, Map.class);
            System.out.println("test");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ArrayList<String>();
    }
}
