package battleaimod.utils;

import battleaimod.GameData.EnemyType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.localization.UIStrings;

import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Map;

public class OriUtils {
    public static ArrayList<String> getEnemyTypes(){
        try {
            ArrayList<EnemyType> enemy_types = new ArrayList<>();
            FileReader reader = new FileReader("EnemyType.json");
            Gson gson = new Gson();
//            Map<String, EnemyType> enemy_type_map = gson.fromJson(reader, Map.class);
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
            JsonArray enemy_types_json = null;
            if (jsonObject.has("enemy_types")) {
                enemy_types_json = jsonObject.get("enemy_types").getAsJsonArray();
                // enemy_types_json to enemy_types
                for (JsonElement enemy_type_json : enemy_types_json) {
                    EnemyType enemyType = gson.fromJson(enemy_type_json, EnemyType.class);
                    enemy_types.add(enemyType);
                }
            }
            System.out.println("test");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ArrayList<String>();
    }
}
