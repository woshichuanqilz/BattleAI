package battleaimod.utils;

import battleaimod.GameData.EnemyType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;

import java.io.FileReader;
import java.io.IOException;

import java.util.*;

public class OriUtils {
    public static ArrayList<EnemyType> enemy_types = new ArrayList<>();
    public static String getEnemyType(){
        try {
            if(enemy_types.isEmpty()){
                FileReader reader = new FileReader("EnemyType.json");
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
                if (jsonObject.has("enemy_types")) {
                    for (JsonElement enemy_type_json : jsonObject.get("enemy_types").getAsJsonArray()) {
                        EnemyType enemyType = gson.fromJson(enemy_type_json, EnemyType.class);
                        Collections.sort(enemyType.monster_list);
                        enemy_types.add(enemyType);
                    }
                }
            }

            ArrayList<AbstractMonster> monster_group = AbstractDungeon.getMonsters().monsters;
            ArrayList<String> monsters_id = new ArrayList<>();
            int monster_count = monster_group.size();
            for (AbstractMonster monster : monster_group) {
                monsters_id.add(monster.id);
            }
            Collections.sort(monsters_id);

            boolean temp = false;
            boolean is_match = false;
            for (EnemyType enemyType : enemy_types) {
                if (enemyType.count == monster_count) {
                    is_match = true;
                    if(Objects.equals(enemyType.match_type, "contain")){
                        for (String monster_id : monsters_id) {
                            if (!enemyType.monster_list.contains(monster_id)) {
                                is_match = false;
                                break;
                            }
                        }
                    } else if (Objects.equals(enemyType.match_type, "equal")) {
                        if (!enemyType.monster_list.equals(monsters_id)) {
                            is_match = false;
                        }
                    }
                    if (is_match) {
                        return enemyType.name;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "Unknown";
    }


    public static int getTotalMonsterDamage(){
        int totalDamage = 0;
        for (int i = 0; i < AbstractDungeon.getMonsters().monsters.size(); i++) {
            AbstractMonster monster = AbstractDungeon.getMonsters().monsters.get(i);
            if (monster.intent == AbstractMonster.Intent.ATTACK) {
                totalDamage += monster.getIntentDmg();
            }
        }
        return totalDamage;
    }
}
