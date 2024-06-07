package battleaimod.battleai;

import battleaimod.BattleAiMod;
import com.badlogic.gdx.maps.tiled.BaseTmxMapLoader;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.blue.Turbo;
import com.megacrit.cardcrawl.cards.green.Concentrate;
import com.megacrit.cardcrawl.cards.green.HeelHook;
import com.megacrit.cardcrawl.cards.green.SneakyStrike;
import com.megacrit.cardcrawl.cards.red.BodySlam;
import com.megacrit.cardcrawl.cards.red.Dropkick;
import com.megacrit.cardcrawl.cards.red.SpotWeakness;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import ludicrousspeed.simulator.commands.*;
import savestate.SaveState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import static battleaimod.ValueFunctions.getStateScore;

public class StateNode {

    private final BattleAiController controller;

    public SaveState saveState;
    private boolean initialized = false;
    private boolean isDone = false;

    // The list of lastCommands obtained by iterating up the parents will be the backwards sequence
    // of commands to get to this state.
    public final StateNode parent;
    public final Command lastCommand;

    // List of commands available from the current state, ordered by guessed result from best to
    // worst.
    public List<Command> commands;
    private int commandIndex = 0;

    // The number of actions taken in this turn, used to limit the total number of actions to
    // prevent infinite loops.
    int turnDepth;

    public ArrayList<StateNode> children = new ArrayList<>();
    public int childIndex = 0;
    private static int process_times = 0;

    public StateNode(StateNode parent, Command lastCommand, BattleAiController controller) {
        this.parent = parent;
        this.lastCommand = lastCommand;
        this.controller = controller;
    }

    public void process(){
        StateNode.process_times ++;
        if(StateNode.process_times > 1000){
            BattleAiController.root.isDone = true;
            isDone = true;
            return;
        }
        if(saveState == null){
            saveState = new SaveState();
        }
        else{
            saveState.loadState();
        }

        if (parent == null || parent.saveState.turn < saveState.turn) {
            turnDepth = 0;
        } else {
            turnDepth = parent.turnDepth + 1;
        }

        if(commands == null){
            commands = CommandList.getAvailableCommands(null, BattleAiMod.actionHeuristics);
            if(!(AbstractDungeon.getCurrRoom() instanceof MonsterRoomBoss)){
                // remove PotionCommand from commands
                commands.removeIf(command -> command instanceof PotionCommand);
            }
        }

        if(saveState.getPlayerHealth() < 1
                || commandIndex >= commands.size()
                || turnDepth > BattleAiController.maxTurnCount){
            String log_str = getString();
            isDone = true;
            BattleAiController.curStateNode = parent;
            BattleAiController.logger.info("OriCode StateNode.process() - " + log_str);
            return;
        }

        //最小生命筛选
//        if(controller.bestEnd != null
//           && BattleAiController.curStateNode.saveState.getPlayerHealth() < controller.bestEnd.saveState.getPlayerHealth()){
//            // 没有吸血的卡
//            isDone = true;
//            BattleAiController.curStateNode = parent;
//            return;
//        }

        if(isBattleOver()){
            isDone = true;
//            BattleAiController.logger.info("OriCode StateNode.process() - Battle is over");
            // 这个时候需要评估bestEnd, 以及是否需要更新bestEnd, 当前的StateNode 是一个END,是否是bestEnd
            if(controller.bestEnd == null || getStateScore(this) > getStateScore(controller.bestEnd)){
                controller.bestEnd = this;
            }
            BattleAiController.curStateNode = parent;
            BattleAiController.logger.info("OriCode StateNode.process() - battle over");
            return;
        }

        Command toExecute = commands.get(commandIndex);
        commandIndex ++;
        toExecute.execute();
        StateNode child_node = new StateNode(this, toExecute, controller);
        BattleAiController.curStateNode = child_node;
        children.add(child_node);
    }

    private int getDamage(){
        return controller.startingHealth - BattleAiController.curStateNode.saveState.getPlayerHealth();
    }

    private String getString() {
        String log_str = "";
        if(saveState.getPlayerHealth() < 1){
            log_str = "Player is dead";
        } else if(commandIndex >= commands.size()){
            log_str = "No more commands";
        } else if(turnDepth > BattleAiController.maxTurnCount){
            log_str = "Max turn count reached";
        } else if (controller.bestEnd != null
                && saveState.getPlayerHealth() < controller.bestEnd.saveState.getPlayerHealth()){
            log_str = "Player health is less than bestEnd";
        }
        return log_str;
    }

    /**
     * Performs the next step and returns true iff the parent should load state
     */
    public Command step() {
        if (saveState == null) {
            saveState = new SaveState();
        }

        if (parent == null || parent.saveState.turn < saveState.turn) {
            turnDepth = 0;
        } else {
            turnDepth = parent.turnDepth + 1;
        }

        if (commands == null) {
            populateCommands();
        }

        if (turnDepth > 50) {
            boolean hasEnd = false;
            for (Command command : commands) {
                if (command instanceof EndCommand) {
                    hasEnd = true;
                    break;
                }
            }

            if (hasEnd) {
                commands.clear();
                commands.add(new EndCommand());
            }
        }

        if (!initialized) {
            initialized = true;

            if (AbstractDungeon.player.isDead || AbstractDungeon.player.isDying || saveState
                    .getPlayerHealth() < 1) {
                if (controller.deathNode == null || controller.deathNode.saveState.turn < saveState.turn) {
                    controller.deathNode = this;
                }

//                BattleAiController.logger.info("OriCode StateNode.step() - Player is dead");
                isDone = true;
                return null;
            }

            if (isBattleOver()) {
                boolean isBestWin = controller.bestEnd == null ||
                        getStateScore(this) > getStateScore(controller.bestEnd);
                if (isBestWin) {
                    controller.bestEnd = this;
//                    BattleAiController.logger.info("OriCode StateNode.step() - New best end");
                }

//                BattleAiController.logger.info("OriCode StateNode.step() - Battle is over");
                isDone = true;
                return null;
            } else {
                commandIndex = 0;
            }
        }

        if (commands.isEmpty()) {
            isDone = true;
            return null;
        }

        Command toExecute = commands.get(commandIndex);
        commandIndex++;
        isDone = commandIndex >= commands.size();

        return toExecute;
    }

    private boolean isBattleOver() {
        return AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead();
    }

    private void populateCommands() {
        Comparator<AbstractCard> combinedPlayHeuristic = (card1, card2) -> {
            for (Comparator<AbstractCard> heuristic : BattleAiMod.cardPlayHeuristics) {
                int heuristicResult = heuristic.compare(card1, card2);

                if (heuristicResult != 0) {
                    return heuristicResult;
                }
            }
            if (card1.upgraded && !card2.upgraded) {
                return -1;
            }

            return 0;
        };

        Comparator<Command> commandComparator = (command1, command2) -> {
            if (command1 instanceof CardCommand && command2 instanceof CardCommand) {
                CardCommand cardCommand1 = (CardCommand) command1;
                CardCommand cardCommand2 = (CardCommand) command2;

                AbstractCard card1 = AbstractDungeon.player.hand.group.get(cardCommand1.cardIndex);
                AbstractCard card2 = AbstractDungeon.player.hand.group.get(cardCommand2.cardIndex);

                int cardHeuristic = combinedPlayHeuristic.compare(card1, card2);
                if (cardHeuristic != 0) {
                    return cardHeuristic;
                } else {
                    return cardCommand1.cardIndex - cardCommand2.cardIndex;
                }
            }

            return 0;
        };

        commands = CommandList.getAvailableCommands(null, BattleAiMod.actionHeuristics);

        HashMap<Integer, ArrayList<Command>> byCardIndex = new HashMap<>();
        ArrayList<Command> otherCommands = new ArrayList<>();

        // Free Eviscerate, Free dropkick, free heel hook
        ArrayList<Command> reallyGoodCommandsToRunFirst = new ArrayList<>();
        ArrayList<Command> reallyBadCommandsToRunLast = new ArrayList<>();

        ArrayList<Function<Command, Boolean>> firstActionConditions = new ArrayList<>();
        ArrayList<Function<Command, Boolean>> lastActionConditions = new ArrayList<>();

        // Dropkick
        firstActionConditions.add((command) -> {
            if (command instanceof CardCommand) {
                CardCommand cardCommand = (CardCommand) command;
                AbstractCard card = AbstractDungeon.player.hand.group.get(cardCommand.cardIndex);
                if (card.cardID.equals(Dropkick.ID)) {
                    if (cardCommand.monsterIndex > -1) {
                        AbstractMonster monster = AbstractDungeon.getMonsters().monsters
                                .get(cardCommand.monsterIndex);
                        return !monster.isDeadOrEscaped() && monster.hasPower("Vulnerable");
                    }
                }
            }

            if (command instanceof CardCommand) {
                CardCommand cardCommand = (CardCommand) command;
                AbstractCard card = AbstractDungeon.player.hand.group.get(cardCommand.cardIndex);
                if (card.cardID.equals(HeelHook.ID)) {
                    if (cardCommand.monsterIndex > -1) {
                        AbstractMonster monster = AbstractDungeon.getMonsters().monsters
                                .get(cardCommand.monsterIndex);
                        return !monster.isDeadOrEscaped() && monster.hasPower("Weakened");
                    }
                }
            }

            return false;
        });

        lastActionConditions.add((command) -> {
            if (command instanceof CardCommand) {
                CardCommand cardCommand = (CardCommand) command;
                AbstractCard card = AbstractDungeon.player.hand.group.get(cardCommand.cardIndex);
                if (card.cardID.equals(SpotWeakness.ID)) {
                    if (cardCommand.monsterIndex > -1) {
                        AbstractMonster monster = AbstractDungeon.getMonsters().monsters
                                .get(cardCommand.monsterIndex);
                        return monster != null && monster.getIntentBaseDmg() < 0;
                    }
                }
            }

            if (command instanceof CardCommand) {
                CardCommand cardCommand = (CardCommand) command;
                AbstractCard card = AbstractDungeon.player.hand.group.get(cardCommand.cardIndex);
                if (card.cardID.equals(Concentrate.ID)) {
                    int numCards = 3;
                    if (card.upgraded) {
                        numCards = 2;
                    }

                    return AbstractDungeon.player.hand.size() <= numCards + 1;
                }
            }

            if (command instanceof CardCommand) {
                CardCommand cardCommand = (CardCommand) command;
                AbstractCard card = AbstractDungeon.player.hand.group.get(cardCommand.cardIndex);
                if (card.cardID.equals(Turbo.ID)) {
                    int totalHandCost = 0;
                    for (AbstractCard handCard : AbstractDungeon.player.hand.group) {
                        totalHandCost += handCard.costForTurn;
                    }

                    return totalHandCost <= EnergyPanel.totalCount;
                }
            }

            return false;
        });

        // TODO: spot weakness
        // TODO: catalyst

        ArrayList<Function<AbstractCard, Boolean>> playCardsLastConditions = new ArrayList<>();
        ArrayList<Function<AbstractCard, Boolean>> playCardsFirstConditions = new ArrayList<>();

        for (Command command : commands) {
            boolean shouldGoEarly = false;
            boolean shouldGoLate = false;

            for (Function<Command, Boolean> condition : firstActionConditions) {
                if (condition.apply(command)) {
                    shouldGoEarly = true;
                    break;
                }
            }

            for (Function<Command, Boolean> condition : lastActionConditions) {
                if (condition.apply(command)) {
                    shouldGoLate = true;
                    break;
                }
            }

            if (shouldGoEarly) {
                reallyGoodCommandsToRunFirst.add(command);
            } else if (shouldGoLate) {
                reallyBadCommandsToRunLast.add(command);
            } else if (command instanceof CardCommand) {
                CardCommand cardCommand = (CardCommand) command;

                if (!byCardIndex.containsKey(cardCommand.cardIndex)) {
                    byCardIndex.put(cardCommand.cardIndex, new ArrayList<>());
                }

                byCardIndex.get(cardCommand.cardIndex).add(command);
            } else {
                otherCommands.add(command);
            }
        }

        // Body slam
        playCardsLastConditions.add((card) -> {
            // De-prioritize body slam if you have extra energy
            return card.cardID.equals(BodySlam.ID) && EnergyPanel.totalCount > card.costForTurn;
        });

        // Sneaky Strike
        playCardsFirstConditions.add((card) -> {
            // its probably n4
            return card.cardID
                    .equals(SneakyStrike.ID) && GameActionManager.totalDiscardedThisTurn > 0;
        });


        ArrayList<Integer> sortedIndeces = new ArrayList(byCardIndex.keySet());
        sortedIndeces.sort((index1, index2) -> {
            AbstractCard card1 = AbstractDungeon.player.hand.group.get(index1);
            AbstractCard card2 = AbstractDungeon.player.hand.group.get(index2);

            for (Function<AbstractCard, Boolean> command : playCardsFirstConditions) {
                if (command.apply(card1)) {
                    if (!command.apply(card2)) {
                        return -1;
                    }
                }

                if (command.apply(card2)) {
                    return 1;
                }
            }

            for (Function<AbstractCard, Boolean> command : playCardsLastConditions) {
                if (command.apply(card1)) {
                    if (!command.apply(card2)) {
                        return 1;
                    }
                }

                if (command.apply(card2)) {
                    return -1;
                }
            }

            return combinedPlayHeuristic.compare(card1, card2);
        });

        ArrayList result = new ArrayList();
        result.addAll(reallyGoodCommandsToRunFirst);

        for (int sortedIndex : sortedIndeces) {
            result.addAll(byCardIndex.get(sortedIndex));
        }

        result.addAll(otherCommands);
        result.addAll(reallyBadCommandsToRunLast);

        commands = result;
    }

    public boolean isDone() {
        return isDone;
    }

    public static int getPlayerDamage(StateNode node) {
        return node.controller.startingHealth - node.saveState.getPlayerHealth();
    }
}
