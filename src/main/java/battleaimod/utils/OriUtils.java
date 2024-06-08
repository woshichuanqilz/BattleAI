package battleaimod.utils;

import battleaimod.GameData.AbstractScene;
import battleaimod.GameData.EnemyType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import org.reflections.Reflections;

import java.io.FileReader;
import java.io.IOException;

import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class OriUtils {
    public static ArrayList<AbstractScene> enemy_types = new ArrayList<>();

    public static List<Class<?>> getChildClasses(Class<?> parentClass) {
        List<Class<?>> childClasses = new ArrayList<>();

        ClassLoader classLoader = parentClass.getClassLoader();
        Package parentPackage = parentClass.getPackage();

        String packageName = parentPackage.getName();
        List<Class<?>> classes = getClassesFromPackage(packageName, classLoader);

        for (Class<?> aClass : classes) {
            if (parentClass.isAssignableFrom(aClass) && !parentClass.equals(aClass)) {
                childClasses.add(aClass);
            }
        }

        return childClasses;
    }

    public static List<Class<?>> getChildClassesFromJar(String jarFilePath, String packageName, Class<?> parentClass) {
        List<Class<?>> childClasses = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarFilePath)) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace("/", ".").replace(".class", "");

                    if (className.startsWith(packageName)) {
                        try {
                            Class<?> clazz = Class.forName(className);
                            if (parentClass.isAssignableFrom(clazz) && !parentClass.equals(clazz)) {
                                childClasses.add(clazz);
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return childClasses;
    }

    private static List<Class<?>> getClassesFromPackage(String packageName, ClassLoader classLoader) {
        List<Class<?>> classes = new ArrayList<>();

        String path = packageName.replace('.', '/');
        try {
            ClassLoader loader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
            java.util.Enumeration<java.net.URL> resources = loader.getResources(path);

            while (resources.hasMoreElements()) {
                java.net.URL resource = resources.nextElement();
                java.io.File dir = new java.io.File(resource.getFile());

                if (dir.exists() && dir.isDirectory()) {
                    String[] files = dir.list();
                    if (files != null) {
                        for (String file : files) {
                            if (file.endsWith(".class")) {
                                String className = packageName + '.' + file.substring(0, file.length() - 6);
                                try {
                                    Class<?> clazz = Class.forName(className);
                                    classes.add(clazz);
                                } catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        return classes;
    }

    public static String getEnemyType(){
        //            if(enemy_types.isEmpty()){
//                FileReader reader = new FileReader("EnemyType.json");
//                Gson gson = new Gson();
//                JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
//                if (jsonObject.has("enemy_types")) {
//                    for (JsonElement enemy_type_json : jsonObject.get("enemy_types").getAsJsonArray()) {
//                        EnemyType enemyType = gson.fromJson(enemy_type_json, EnemyType.class);
//                        Collections.sort(enemyType.monster_list);
//                        enemy_types.add(enemyType);
//                    }
//                }
//            }

        Class<?> parentClass = AbstractScene.class;
        List<Class<?>> childClasses = getChildClasses(parentClass);

        for (Class<?> childClass : childClasses) {
            System.out.println(childClass.getName());
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
        for (AbstractScene enemyType : enemy_types) {
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
