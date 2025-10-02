package pedsim.applet;

import java.awt.Button;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Label;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import pedsim.engine.PedSimCity;
import pedsim.parameters.ParameterManager;
import pedsim.utilities.LoggerUtil;

public class PedSimCityApplet extends Frame {

  private static final long serialVersionUID = 1L;

  private Choice cityName;
  Button startButton;
  Button runServerButton;
  private Button configButton;
  Button endButton;
  private TextField daysTextField;
  private TextField jobsTextField;
  private TextField populationTextField;
  private TextField percentageTextField;
  private TextArea logArea;

  private boolean runningOnServer = false;
  private Thread simulationThread;

  public PedSimCityApplet() {
    super("PedSimCity Applet");
    setLayout(null);

    // --- GUI fields ---
    Label cityNameLabel = new Label("City Name:");
    cityNameLabel.setBounds(10, 70, 80, 20);
    add(cityNameLabel);

    cityName = new Choice();
    cityName.setBounds(140, 70, 150, 20);
    updateCityNameOptions();
    add(cityName);

    Label daysLabel = new Label("Duration in days:");
    daysTextField = new TextField("7");
    daysLabel.setBounds(10, 100, 120, 20);
    daysTextField.setBounds(190, 100, 100, 20);
    add(daysLabel);
    add(daysTextField);

    Label populationLabel = new Label("Actual Population:");
    populationTextField = new TextField("100000");
    populationLabel.setBounds(10, 130, 120, 20);
    populationTextField.setBounds(190, 130, 100, 20);
    add(populationLabel);
    add(populationTextField);

    Label percentageLabel = new Label("% Represented by Agents:");
    percentageTextField = new TextField("0.01");
    percentageLabel.setBounds(10, 160, 150, 20);
    percentageTextField.setBounds(190, 160, 100, 20);
    add(percentageLabel);
    add(percentageTextField);

    Label nrJobsLabel = new Label("Jobs:");
    jobsTextField = new TextField("1");
    nrJobsLabel.setBounds(10, 190, 100, 20);
    jobsTextField.setBounds(190, 190, 100, 20);
    add(nrJobsLabel);
    add(jobsTextField);

    // --- Buttons ---
    startButton = new Button("Run Simulation");
    startButton.setBounds(10, 330, 120, 50);
    startButton.setBackground(new Color(0, 220, 0));
    add(startButton);

    runServerButton = new Button("Run on Server");
    runServerButton.setBounds(150, 330, 120, 50);
    runServerButton.setBackground(new Color(0, 150, 200));
    add(runServerButton);

    endButton = new Button("End Simulation");
    endButton.setBackground(Color.PINK);

    configButton = new Button("Server Settings");
    configButton.setBounds(280, 280, 120, 40);
    configButton.setBackground(new Color(200, 200, 0));
    add(configButton);

    // --- Log area ---
    logArea = new TextArea("", 10, 80, TextArea.SCROLLBARS_VERTICAL_ONLY);
    logArea.setEditable(false);
    logArea.setBounds(10, 400, 460, 80);
    add(logArea);

    // --- Handlers ---
    ServerLauncherApplet serverLauncher = new ServerLauncherApplet();
    PedSimCityActionHandler handler = new PedSimCityActionHandler(this, serverLauncher);

    startButton.addActionListener(handler.runLocalListener());
    runServerButton.addActionListener(handler.runServerListener());
    configButton.addActionListener(e -> serverLauncher.openConfigPanel());
    endButton.addActionListener(handler.endListener());

    // Redirect logger output to both console + TextArea
    LoggerUtil.redirectToTextArea(logArea);

    setSize(500, 520);
    setVisible(true);
  }

  private void updateCityNameOptions() {
    cityName.removeAll();
    cityName.add("Muenster");
    cityName.validate();
  }

  // --- Logging utility ---
  public void appendLog(String msg) {
    LoggerUtil.getLogger().info(msg);
    if (logArea != null) {
      logArea.append(msg + "\n");
    }
  }

  // ---------------------------------------------------
  // Main entrypoint
  // ---------------------------------------------------
  public static void main(String[] args) throws Exception {
    boolean headless = false;
    for (String arg : args) {
      if ("--headless".equals(arg) || arg.startsWith("--headless=")) {
        headless = true;
        break;
      }
    }

    Map<String, String> params = pedsim.parameters.ParameterManager.parseArgs(args);
    ParameterManager.setParameters(params);

    if (headless) {
      LoggerUtil.getLogger().info("[SERVER] Running headless simulation...");
      PedSimCity.runSimulation(); // ✅ No GUI
    } else {
      PedSimCityApplet applet = new PedSimCityApplet();
      applet.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          applet.dispose();
        }
      });
    }
  }

  // =====================================================
  // Getters & Setters
  // =====================================================

  public String getCityName() {
    return cityName.getSelectedItem();
  }

  public String getDays() {
    return daysTextField.getText();
  }

  public String getPopulation() {
    return populationTextField.getText();
  }

  public String getPercentage() {
    return percentageTextField.getText();
  }

  public String getJobs() {
    return jobsTextField.getText();
  }

  public void setRunningOnServer(boolean value) {
    this.runningOnServer = value;
  }

  public boolean isRunningOnServer() {
    return runningOnServer;
  }

  public void setSimulationThread(Thread t) {
    this.simulationThread = t;
  }

  public Thread getSimulationThread() {
    return simulationThread;
  }
}
