package communicationmod;

import basemod.*;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import battleai.BattleAiController;
import battleai.CardCommand;
import battleai.Command;
import battleai.EndCommand;
import com.badlogic.gdx.graphics.Texture;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import communicationmod.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import savestate.SaveState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpireInitializer
public class CommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber, PreUpdateSubscriber {
    private static final Logger logger = LogManager.getLogger(CommunicationMod.class.getName());
    private static final String MODNAME = "Communication Mod";
    private static final String AUTHOR = "Forgotten Arbiter";
    private static final String DESCRIPTION = "This mod communicates with an external program to play Slay the Spire.";
    private static final String COMMAND_OPTION = "command";
    private static final String GAME_START_OPTION = "runAtGameStart";
    private static final String VERBOSE_OPTION = "verbose";
    private static final String INITIALIZATION_TIMEOUT_OPTION = "maxInitializationTimeout";
    private static final String DEFAULT_COMMAND = "";
    private static final long DEFAULT_TIMEOUT = 10L;
    private static final boolean DEFAULT_VERBOSITY = true;
    private static final StringBuilder inputBuffer = new StringBuilder();
    public static Stack<SaveState> saveStates = null;
    public static boolean messageReceived = false;
    public static boolean mustSendGameState = false;
    public static boolean readyForUpdate;
    private static Process listener;
    private static Thread writeThread;
    private static BlockingQueue<String> writeQueue;
    private static Thread readThread;
    private static BlockingQueue<String> readQueue;
    private static SpireConfig communicationConfig;
    private static BattleAiController battleAiController = null;
    private boolean canStep = false;
    private SaveState saveState;

    public CommunicationMod() {
        BaseMod.subscribe(this);

        Settings.ACTION_DUR_XFAST = 0.01F;
        Settings.ACTION_DUR_FASTER = 0.02F;
        Settings.ACTION_DUR_FAST = 0.025F;
        Settings.ACTION_DUR_MED = 0.05F;
        Settings.ACTION_DUR_LONG = .10F;
        Settings.ACTION_DUR_XLONG = .15F;

        try {
            Properties defaults = new Properties();
            defaults.put(GAME_START_OPTION, Boolean.toString(false));
            defaults.put(INITIALIZATION_TIMEOUT_OPTION, Long.toString(DEFAULT_TIMEOUT));
            defaults.put(VERBOSE_OPTION, Boolean.toString(DEFAULT_VERBOSITY));
            communicationConfig = new SpireConfig("CommunicationMod", "config", defaults);
            String command = communicationConfig.getString(COMMAND_OPTION);
            // I want this to always be saved to the file so people can set it more easily.
            if (command == null) {
                communicationConfig.setString(COMMAND_OPTION, DEFAULT_COMMAND);
                communicationConfig.save();
            }
            communicationConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (getRunOnGameStartOption()) {
            boolean success = startExternalProcess();
        }
    }

    public static void initialize() {
        CommunicationMod mod = new CommunicationMod();
    }

    public static void dispose() {
        logger.info("Shutting down child process...");
        if (listener != null) {
            listener.destroy();
        }
    }

    private static void sendMessage(String message) {
        if (writeQueue != null && writeThread.isAlive()) {
            writeQueue.add(message);
        }
    }

    private static boolean messageAvailable() {
        return readQueue != null && !readQueue.isEmpty();
    }

    private static String readMessage() {
        if (messageAvailable()) {
            return readQueue.remove();
        } else {
            return null;
        }
    }

    private static String readMessageBlocking() {
        try {
            return readQueue.poll(getInitializationTimeoutOption(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to read message from subprocess.");
        }
    }

    private static String[] getSubprocessCommand() {
        if (communicationConfig == null) {
            return new String[0];
        }
        return communicationConfig.getString(COMMAND_OPTION).trim().split("\\s+");
    }

    private static String getSubprocessCommandString() {
        if (communicationConfig == null) {
            return "";
        }
        return communicationConfig.getString(COMMAND_OPTION).trim();
    }

    private static boolean getRunOnGameStartOption() {
        if (communicationConfig == null) {
            return false;
        }
        return communicationConfig.getBool(GAME_START_OPTION);
    }

    private static long getInitializationTimeoutOption() {
        if (communicationConfig == null) {
            return DEFAULT_TIMEOUT;
        }
        return communicationConfig.getInt(INITIALIZATION_TIMEOUT_OPTION);
    }

    private static boolean getVerbosityOption() {
        if (communicationConfig == null) {
            return DEFAULT_VERBOSITY;
        }
        return communicationConfig.getBool(VERBOSE_OPTION);
    }

    private void sendGameState() {
        if (CommandExecutor.getAvailableCommands().contains("play") || CommandExecutor
                .isEndCommandAvailable() || CommandExecutor.isChooseCommandAvailable()) {
            if (battleAiController != null) {
                if (canStep || true) {
//                if (canStep || !battleAiController.runCommandMode) {
                    canStep = false;

                    battleAiController.step();
                }
            }
//            if (saveStates == null) {
//                saveStates = new Stack<>();
//            }
//
//            SaveState state = new SaveState();
//
//            new BattleAiController(state);
//            saveStates.push(state);
//
//
//            System.out.printf("saving state, stateStackSize:%s\n", saveStates.size());
        }
        String state = GameStateConverter.getCommunicationState();
        sendMessage(state);
    }

    public void receivePreUpdate() {
        if (listener != null && !listener.isAlive() && writeThread != null && writeThread
                .isAlive()) {
            logger.info("Child process has died...");
            writeThread.interrupt();
            readThread.interrupt();
        }
        if (messageAvailable()) {
            try {
                boolean stateChanged = CommandExecutor.executeCommand(readMessage());
                if (stateChanged) {
                    GameStateListener.registerCommandExecution();
                }
            } catch (InvalidCommandException e) {
                HashMap<String, Object> jsonError = new HashMap<>();
                jsonError.put("error", e.getMessage());
                jsonError.put("ready_for_command", GameStateListener.isWaitingForCommand());
                Gson gson = new Gson();
                sendMessage(gson.toJson(jsonError));
            }
        }
    }

    public void receivePostInitialize() {
        setUpOptionsMenu();
    }

    public void receivePostUpdate() {
        if (!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
        if (readyForUpdate) {
            sendGameState();
            readyForUpdate = false;
        }
        InputActionPatch.doKeypress = false;
    }

    public void receivePostDungeonUpdate() {

        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
            readyForUpdate = true;
        }
        if (AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

    private void setUpOptionsMenu() {
        ModPanel settingsPanel = new ModPanel();
        ModLabeledToggleButton gameStartOptionButton = new ModLabeledToggleButton(
                "Start external process at game launch",
                350, 550, Settings.CREAM_COLOR, FontHelper.charDescFont,
                getRunOnGameStartOption(), settingsPanel, modLabel -> {
        },
                modToggleButton -> {
                    if (communicationConfig != null) {
                        communicationConfig.setBool(GAME_START_OPTION, modToggleButton.enabled);
                        try {
                            communicationConfig.save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
        settingsPanel.addUIElement(gameStartOptionButton);

        ModLabel externalCommandLabel = new ModLabel(
                "", 350, 600, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
            modLabel.text = String
                    .format("External Process Command: %s", getSubprocessCommandString());
        });
        settingsPanel.addUIElement(externalCommandLabel);

        ModButton startProcessButton = new ModButton(
                350, 650, settingsPanel, modButton -> {
            BaseMod.modSettingsUp = false;
            startExternalProcess();
        });
        settingsPanel.addUIElement(startProcessButton);

        ModLabel startProcessLabel = new ModLabel(
                "(Re)start external process",
                475, 700, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
            if (listener != null && listener.isAlive()) {
                modLabel.text = "Restart external process";
            } else {
                modLabel.text = "Start external process";
            }
        });
        settingsPanel.addUIElement(startProcessLabel);

        ModButton editProcessButton = new ModButton(
                850, 650, settingsPanel, modButton -> {
        });
        settingsPanel.addUIElement(editProcessButton);

        ModLabel editProcessLabel = new ModLabel(
                "Set command (not implemented)",
                975, 700, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
        });
        settingsPanel.addUIElement(editProcessLabel);

        ModLabeledToggleButton verbosityOption = new ModLabeledToggleButton(
                "Suppress verbose log output",
                350, 500, Settings.CREAM_COLOR, FontHelper.charDescFont,
                getVerbosityOption(), settingsPanel, modLabel -> {
        },
                modToggleButton -> {
                    if (communicationConfig != null) {
                        communicationConfig.setBool(VERBOSE_OPTION, modToggleButton.enabled);
                        try {
                            communicationConfig.save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
        settingsPanel.addUIElement(verbosityOption);
        BaseMod.registerModBadge(ImageMaster
                .loadImage("Icon.png"), "Communication Mod", "Forgotten Arbiter", null, settingsPanel);
        BaseMod.addTopPanelItem(new SaveStateTopPanel());
        BaseMod.addTopPanelItem(new LoadStateTopPanel());
    }

    private void startCommunicationThreads() {
        writeQueue = new LinkedBlockingQueue<>();
        writeThread = new Thread(new DataWriter(writeQueue, listener
                .getOutputStream(), getVerbosityOption()));
        writeThread.start();
        readQueue = new LinkedBlockingQueue<>();
        readThread = new Thread(new DataReader(readQueue, listener
                .getInputStream(), getVerbosityOption()));
        readThread.start();
    }

    private boolean startExternalProcess() {
        if (readThread != null) {
            readThread.interrupt();
        }
        if (writeThread != null) {
            writeThread.interrupt();
        }
        if (listener != null) {
            listener.destroy();
            try {
                boolean success = listener.waitFor(2, TimeUnit.SECONDS);
                if (!success) {
                    listener.destroyForcibly();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                listener.destroyForcibly();
            }
        }
        ProcessBuilder builder = new ProcessBuilder(getSubprocessCommand());
        File errorLog = new File("communication_mod_errors.log");
        builder.redirectError(ProcessBuilder.Redirect.appendTo(errorLog));
        try {
            listener = builder.start();
        } catch (IOException e) {
            logger.error("Could not start external process.");
            e.printStackTrace();
        }
        if (listener != null) {
            startCommunicationThreads();
            // We wait for the child process to signal it is ready before we proceed. Note that the game
            // will hang while this is occurring, and it will time out after a specified waiting time.
            String message = readMessageBlocking();
            if (message == null) {
                // The child process waited too long to respond, so we kill it.
                readThread.interrupt();
                writeThread.interrupt();
                listener.destroy();
                logger.error("Timed out while waiting for signal from external process.");
                logger.error("Check communication_mod_errors.log for stderr from the process.");
                return false;
            } else {
                logger.info(String.format("Received message from external process: %s", message));
                if (GameStateListener.isWaitingForCommand()) {
                    mustSendGameState = true;
                }
                return true;
            }
        }
        return false;
    }

    public class SaveStateTopPanel extends TopPanelItem {
        public static final String ID = "yourmodname:SaveState";

        public SaveStateTopPanel() {
            super(new Texture("save.png"), ID);
        }

        @Override
        protected void onClick() {
            System.out.println("you clicked on save");
            saveState = new SaveState();
            if (battleAiController == null) {

//                ArrayList<Command> commands = new ArrayList<>();

//                commands.add(new CardCommand(0, 0));
//                commands.add(new CardCommand(0, 0));
//                commands.add(new CardCommand(1, 0));
//                commands.add(new EndCommand());
//                commands.add(new CardCommand(0, 0));
//                commands.add(new CardCommand(0, 0));
//                commands.add(new CardCommand(0, 0));
//                commands.add(new EndCommand());
//                battleAiController = new BattleAiController(commands);


                battleAiController = new BattleAiController(new SaveState());
            }
            readyForUpdate = true;
            // your onclick code
        }
    }

    public class LoadStateTopPanel extends TopPanelItem {
        public static final String ID = "yourmodname:LoadState";

        public LoadStateTopPanel() {
            super(new Texture("Icon.png"), ID);
        }

        @Override
        protected void onClick() {
//            saveStateController.loadState();
            System.out.println("you clicked on load");

            canStep = true;
            readyForUpdate = true;
            receivePostUpdate();

//            saveState.loadState();

//            battleAiController.step();
//            if (saveStates.size() < 2) {
//                System.out.println("Nothing to load");
//            } else {
//                saveStates.pop();
//                saveStates.peek().loadState();
//            }

            // your onclick code
        }
    }

}
