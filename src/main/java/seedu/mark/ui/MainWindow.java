package seedu.mark.ui;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.controlsfx.control.Notifications;
import seedu.mark.commons.core.GuiSettings;
import seedu.mark.commons.core.LogsCenter;
import seedu.mark.logic.Logic;
import seedu.mark.logic.commands.TabCommand.Tab;
import seedu.mark.logic.commands.exceptions.CommandException;
import seedu.mark.logic.commands.results.CommandResult;
import seedu.mark.logic.parser.exceptions.ParseException;
import seedu.mark.model.bookmark.Url;
import seedu.mark.model.reminder.Reminder;

/**
 * The Main Window. Provides the basic application layout containing
 * a menu bar and space where other JavaFX elements can be placed.
 */
public class MainWindow extends UiPart<Stage> {

    private static final String FXML = "MainWindow.fxml";

    private final Logger logger = LogsCenter.getLogger(getClass());

    private Stage primaryStage;
    private Logic logic;

    // Independent Ui parts residing in this Ui container
    private BookmarkListPanel bookmarkListPanel;
    private ResultDisplay resultDisplay;
    private HelpWindow helpWindow;
    private BrowserPanel browserPanel;
    private DashboardPanel dashboardPanel;
    private OfflinePanel offlinePanel;

    @FXML
    private ToggleButton dashboardButton;

    @FXML
    private ToggleButton onlineButton;

    @FXML
    private ToggleButton offlineButton;

    @FXML
    private SplitPane splitPane;

    @FXML
    private StackPane mainViewAreaPlaceholder;

    @FXML
    private StackPane commandBoxPlaceholder;

    @FXML
    private MenuItem helpMenuItem;

    @FXML
    private StackPane bookmarkListPanelPlaceholder;

    @FXML
    private StackPane resultDisplayPlaceholder;

    @FXML
    private StackPane statusbarPlaceholder;

    @FXML
    private StackPane folderStructurePlaceholder;

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool (1);
    Runnable r = () -> showDueReminder();

    private ObservableList<Reminder> reminders;
    private List<ReminderWindow> reminderWindows = new ArrayList<>();

    public MainWindow(Stage primaryStage, Logic logic) {
        super(FXML, primaryStage);

        // Set dependencies
        this.primaryStage = primaryStage;
        this.logic = logic;

        // Configure the UI
        setWindowDefaultSize(logic.getGuiSettings());

        setAccelerators();

        helpWindow = new HelpWindow();
        reminders = logic.getReminderList();
        setReminderWindows();
        reminders.addListener((ListChangeListener<? super Reminder>) change -> {
            while (change.next()) {
                setReminderWindows();
            }
        });
        executor.scheduleAtFixedRate( r , 0L , 5L , TimeUnit.SECONDS);
    }

    private void setReminderWindows() {
        reminderWindows.clear();
        for (int i = 0; i < reminders.size(); i++) {
            Reminder reminder = reminders.get(i);
            ReminderWindow window = new ReminderWindow(reminder.getUrl(), reminder.getNote());
            reminderWindows.add(window);
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    private void setAccelerators() {
        setAccelerator(helpMenuItem, KeyCombination.valueOf("F1"));
    }

    /**
     * Sets the accelerator of a MenuItem.
     * @param keyCombination the KeyCombination value of the accelerator
     */
    private void setAccelerator(MenuItem menuItem, KeyCombination keyCombination) {
        menuItem.setAccelerator(keyCombination);

        /*
         * TODO: the code below can be removed once the bug reported here
         * https://bugs.openjdk.java.net/browse/JDK-8131666
         * is fixed in later version of SDK.
         *
         * According to the bug report, TextInputControl (TextField, TextArea) will
         * consume function-key events. Because CommandBox contains a TextField, and
         * ResultDisplay contains a TextArea, thus some accelerators (e.g F1) will
         * not work when the focus is in them because the key event is consumed by
         * the TextInputControl(s).
         *
         * For now, we add following event filter to capture such key events and open
         * help window purposely so to support accelerators even when focus is
         * in CommandBox or ResultDisplay.
         */
        getRoot().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof TextInputControl && keyCombination.match(event)) {
                menuItem.getOnAction().handle(new ActionEvent());
                event.consume();
            }
        });
    }

    /**
     * Fills up all the placeholders of this window.
     */
    void fillInnerParts() {
        browserPanel = new BrowserPanel(logic.getCurrentUrlProperty());
        dashboardPanel = new DashboardPanel(logic, getCurrentUrlChangeHandler());
        offlinePanel = new OfflinePanel(logic.getObservableDocument());
        mainViewAreaPlaceholder.getChildren().add(dashboardPanel.getRoot());

        bookmarkListPanel = new BookmarkListPanel(logic.getFilteredBookmarkList(),
                logic.getCurrentUrlProperty(), logic.getBookmarkDisplayingCacheProperty(),
                getCurrentUrlChangeHandler());
        bookmarkListPanelPlaceholder.getChildren().add(bookmarkListPanel.getRoot());

        resultDisplay = new ResultDisplay();
        resultDisplayPlaceholder.getChildren().add(resultDisplay.getRoot());

        StatusBarFooter statusBarFooter = new StatusBarFooter(logic.getMarkFilePath());
        statusbarPlaceholder.getChildren().add(statusBarFooter.getRoot());

        CommandBox commandBox = new CommandBox(this::executeCommand);
        commandBoxPlaceholder.getChildren().add(commandBox.getRoot());
    }

    /**
     * Sets the default size based on {@code guiSettings}.
     */
    private void setWindowDefaultSize(GuiSettings guiSettings) {
        primaryStage.setHeight(guiSettings.getWindowHeight());
        primaryStage.setWidth(guiSettings.getWindowWidth());
        if (guiSettings.getWindowCoordinates() != null) {
            primaryStage.setX(guiSettings.getWindowCoordinates().getX());
            primaryStage.setY(guiSettings.getWindowCoordinates().getY());
        }
    }

    /**
     * Opens the help window or focuses on it if it's already opened.
     */
    @FXML
    public void handleHelp() {
        if (!helpWindow.isShowing()) {
            helpWindow.show();
        } else {
            helpWindow.focus();
        }
    }

    void show() {
        primaryStage.show();
    }

    /**
     * Closes the application.
     */
    @FXML
    private void handleExit() {
        GuiSettings guiSettings = new GuiSettings(primaryStage.getWidth(), primaryStage.getHeight(),
                (int) primaryStage.getX(), (int) primaryStage.getY());
        logic.setGuiSettings(guiSettings);
        helpWindow.hide();
        for (ReminderWindow window : reminderWindows) {
            window.hide();
        }
        primaryStage.hide();
    }

    /**
     * Switches to dashboard view
     */
    @FXML
    public void handleSwitchToDashboard() {
        dashboardButton.setSelected(true);
        mainViewAreaPlaceholder.getChildren().set(0, dashboardPanel.getRoot());
    }

    /**
     * Switches to online view
     */
    @FXML
    public void handleSwitchToOnline() {
        onlineButton.setSelected(true);
        mainViewAreaPlaceholder.getChildren().set(0, browserPanel.getRoot());
    }

    /**
     * Switches to offline view
     */
    @FXML
    public void handleSwitchToOffline() {
        offlineButton.setSelected(true);
        mainViewAreaPlaceholder.getChildren().set(0, offlinePanel.getRoot());
    }

    /**
     * Directs to the appropriate handler to switch Tab.
     * @param tab The tab to switch to
     */
    private void handleTabSwitchRequestIfAny(Tab tab) {
        requireNonNull(tab);

        switch (tab) {
        case DASHBOARD:
            handleSwitchToDashboard();
            break;
        case ONLINE:
            handleSwitchToOnline();
            break;
        case OFFLINE:
            handleSwitchToOffline();
            break;
        default:
            break;
        }
    }

    private void handleFolderExpand(int levelsToExpand) {
        dashboardPanel.folderStructureTreeView.expand(levelsToExpand);
    }

    public Consumer<Url> getCurrentUrlChangeHandler() {
        return url -> {
            logic.setCurrentUrl(url);
            handleSwitchToOnline();
        };
    }

    public BookmarkListPanel getBookmarkListPanel() {
        return bookmarkListPanel;
    }

    public DashboardPanel getDashboardPanel() {
        return dashboardPanel;
    }

    public OfflinePanel getOfflinePanel() {
        return offlinePanel;
    }

    public BrowserPanel getBrowserPanel() {
        return browserPanel;
    }

    /**
     * Executes the command and returns the result.
     *
     * @see seedu.mark.logic.Logic#execute(String)
     */
    private CommandResult executeCommand(String commandText) throws CommandException, ParseException {
        try {
            CommandResult commandResult = logic.execute(commandText);
            logger.info("Result: " + commandResult.getFeedbackToUser());
            resultDisplay.setFeedbackToUser(commandResult.getFeedbackToUser());

            if (commandResult.isShowHelp()) {
                handleHelp();
            }

            if (commandResult.isExit()) {
                handleExit();
            }

            if (commandResult.getTab() != null) {
                handleTabSwitchRequestIfAny(commandResult.getTab());
            }

            if (commandResult.getLevelsToExpand() != 0) {
                handleFolderExpand(commandResult.getLevelsToExpand());
            }

            return commandResult;
        } catch (CommandException | ParseException e) {
            logger.info("Invalid command: " + commandText);
            resultDisplay.setFeedbackToUser(e.getMessage());
            throw e;
        }
    }

    private long compareTime(LocalDateTime before, LocalDateTime after) {
        return Duration.between(before, after).toHours();
    }

    private void showDueReminder() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("running show");

        for (int i = 0; i < reminders.size(); i++) {
            Reminder reminder = reminders.get(i);
            LocalDateTime remindTime = reminder.getRemindTime();
            System.out.println(compareTime(now, remindTime));
            System.out.println(now.isBefore(remindTime) && compareTime(now, remindTime) < 5);
            if (now.isBefore(remindTime) && compareTime(now, remindTime) < 5) {
                System.out.println(true);
                ReminderWindow window = reminderWindows.get(i);

                if (window.isShowing()) {
                    window.show();
                } else {
                    window.focus();
                }
            }
        }
    }
}
