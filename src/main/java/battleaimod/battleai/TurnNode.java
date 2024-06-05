package battleaimod.battleai;

import battleaimod.ValueFunctions;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.EndCommand;
import savestate.SaveState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import static savestate.SaveStateMod.addRuntime;

public class TurnNode implements Comparable<TurnNode> {
    public final BattleAiController controller;
    public final int turnLabel;
    public Stack<StateNode> states;

    // If true, this turn node is in the process of executing commands.  It must
    public boolean runningCommands = false;

    // Once true, this node is fully processed and will no longer be iterated.
    public boolean isDone = false;

    public StateNode startingState;
    private boolean initialized = false;

    public List<TurnNode> children;
    public TurnNode parent;
    private Optional<Integer> cachedValue = Optional.empty();

    static int nodeIndex = 0;


    public TurnNode(StateNode statenode, BattleAiController controller, TurnNode parent) {
        startingState = statenode;
        this.controller = controller;
        this.parent = parent;
        this.turnLabel = nodeIndex++;
        children = new ArrayList<>();

        if (parent != null && BattleAiController.SHOULD_SHOW_TREE) {
            parent.children.add(this);
        }

////        BattleAiController.logger.info("OriCode new TurnNode created");
    }

    public boolean step() {
        if (isDone) {
//            BattleAiController.logger.info("OriCode TurnNode isDone");
            return true;
        }
//        BattleAiController.logger.info("OriCode GameTurn " + GameActionManager.turn);

        if (!initialized) {
            initialized = true;
            states = new Stack<>();
            states.push(startingState);
        }

        if (states.isEmpty()) {
            isDone = true;
//            BattleAiController.logger.info("OriCode states empty isDone");
            return true;
        }

        StateNode curState = states.peek();
        if (!runningCommands) {
            runningCommands = true;
            curState.saveState.loadState();
//            BattleAiController.logger.info("OriCode curState unRunning loadState");
            return false;
        }

        if (AbstractDungeon.player.currentHealth < 1) {
            curState.saveState = new SaveState();

            // Try to die as late as possible
            if (controller.deathNode == null ||
                    controller.deathNode.saveState.turn < curState.saveState.turn) {
                controller.deathNode = curState;
            }
        }

        if (curState != startingState && isNewTurn(curState)) {
            if (curState.saveState == null) {
                curState.saveState = new SaveState();
            }

            controller.turnsLoaded++;
            addRuntime("turnsLoaded", 1);
            TurnNode toAdd = new TurnNode(curState, controller, this);
            states.pop();
//            BattleAiController.logger.info("OriCode " + getClass().getName() + " states pop new turn, size:" + states.size());

            while (!states.isEmpty() && states.peek().isDone()) {
                states.pop();
//                BattleAiController.logger.info("OriCode " + getClass().getName() + " states pop is done, size:" + states.size());
            }

            runningCommands = false;

            int turnNumber = GameActionManager.turn;
            if (AbstractDungeon.player.currentHealth >= 1) {
                if (turnNumber >= controller.targetTurn) {
                    if (controller.bestTurn == null || toAdd.isBetterThan(controller.bestTurn)) {
                        controller.bestTurn = toAdd;
//                        BattleAiController.logger.info("OriCode bestTurn " + toAdd.turnLabel);
                    }
                } else {
                    if (controller.backupTurn == null ||
                            controller.backupTurn.startingState.saveState.turn < toAdd.startingState.saveState.turn ||
                            (toAdd.isBetterThan(controller.backupTurn)) && controller.backupTurn.startingState.saveState.turn == toAdd.startingState.saveState.turn) {
                        controller.backupTurn = toAdd;
//                        BattleAiController.logger.info("OriCode backupTurn " + toAdd.turnLabel);
                    }

                    controller.turns.add(toAdd);
                }
            }

//            BattleAiController.logger.info("OriCode new TurnNode created return True");
            return true;
        }

        if (curState.isDone()) {
            states.pop();
//            BattleAiController.logger.info("OriCode " + getClass().getName() + "states pop cs done, size:" + states.size());
            if (!states.empty()) {
                states.peek().saveState.loadState();
//                BattleAiController.logger.info("OriCode unEmpty loadState");
            }
        } else {
            Command toExecute = curState.step();

            if (toExecute == null) {
                controller.turnsLoaded++;
                states.pop();
//                BattleAiController.logger.info("OriCode " + getClass().getName() + "states pop, size:" + states.size());
                if (!states.isEmpty()) {
                    states.peek().saveState.loadState();
//                    BattleAiController.logger.info("OriCode curState exeNull and unEmpty loadState");
                }
//                BattleAiController.logger.info("OriCode curState exeNull return True");
                return true;
            } else {
                StateNode toAdd = new StateNode(curState, toExecute, controller);

                try {
                    toExecute.execute();
                    states.push(toAdd);
//                    BattleAiController.logger.info("OriCode " + getClass().getName() + " states push, size:" + states.size());
                } catch (IndexOutOfBoundsException e) {
                    addRuntime("Execution Exception", 1);
                }
            }
        }

        if (states.isEmpty()) {
            isDone = true;
        }

        return false;
    }

    @Override
    public String toString() {
        int monsterDamage = ValueFunctions
                .getTotalMonsterHealth(controller.startingState) - ValueFunctions
                .getTotalMonsterHealth(startingState.saveState);

        return String
                .format("hp:%03d ehp:%03d (%03d) turn:%02d score:%4d", ValueFunctions
                        .getPlayerDamage(this), getTotalMonsterHealth(this), monsterDamage, startingState.saveState.turn, getTurnScore(this));
    }

    public static int getTotalMonsterHealth(TurnNode turnNode) {
        return ValueFunctions.getTotalMonsterHealth(turnNode.startingState.saveState);
    }


    public static int getNumSlimes(TurnNode turnNode) {
        return turnNode.startingState.saveState.getNumSlimes();
    }

    public static int getTurnScore(TurnNode turnNode) {
        if (!turnNode.cachedValue.isPresent()) {
//            System.err.println("Turn Value " + turnNode.turnLabel + " " + ValueFunctions
//                    .caclculateTurnScore(turnNode));

            turnNode.cachedValue = Optional.of(ValueFunctions.calculateTurnScore(turnNode));
        }
        return turnNode.cachedValue.get();
    }


    @Override
    public int compareTo(TurnNode otherTurn) {
        long startCompare = System.currentTimeMillis();

        int otherScore = getTurnScore(otherTurn);
        int thisScore = getTurnScore(this);

        if (otherScore == thisScore) {
            return otherTurn.turnLabel - this.turnLabel;
        }

        int result = otherScore - thisScore;

        addRuntime("Comparing Turns", System.currentTimeMillis() - startCompare);
        addRuntime("Comparing Turns instance", 1);

        return result;
    }

    public boolean isBetterThan(TurnNode other) {
        return compareTo(other) < 0;
    }

    private boolean isNewTurn(StateNode childNode) {
        return (GameActionManager.turn > this.startingState.saveState.turn) || childNode.lastCommand instanceof EndCommand;
    }
}
