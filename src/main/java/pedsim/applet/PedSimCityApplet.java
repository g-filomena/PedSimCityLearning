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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import pedsim.engine.Engine;
import pedsim.engine.Environment;
import pedsim.engine.Import;
import pedsim.parameters.ParameterManager;
import pedsim.parameters.Pars;
import pedsim.utilities.ArgumentBuilder;
import pedsim.utilities.LoggerUtil;
import pedsim.utilities.StringEnum;

public class PedSimCityApplet extends Frame {

  private static final long serialVersionUID = 1L;

  private Choice cityName;
  private Button startButton;
  private Button runServerButton;
  private Button endButton;
  private TextField daysTextField;
  private TextField jobsTextField;
  private TextField populationTextField;
  private TextField percentageTextField;
  private TextArea logArea;

  private boolean runningOnServer = false;
  private Thread simulationThread;

  private static final Logger logger = LoggerUtil.getLogger();

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

    Button configButton = new Button("Server Settings");
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

    // Redirect logger output
    LoggerUtil.redirectToTextArea(logArea);

    setSize(500, 520);
    setVisible(true);
  }

  // --- Simulation logic ---
  public void runSimulationLocal() throws Exception {
    importFiles();
    appendLog("Running ABM with " + Pars.numAgents + " agents for "
        + StringEnum.Learner.values().length + " scenarios.");

    Environment.prepare();
    appendLog("Environment prepared. Starting simulation...");

    Engine engine = new Engine();
    for (int jobNr = 0; jobNr < Pars.jobs; jobNr++) {
      appendLog("Executing Job: " + jobNr);
      engine.executeJob(jobNr);
    }
    appendLog("Simulation finished.");
  }

  private void importFiles() {
    try {
      Import importer = new Import();
      importer.importFiles();
    } catch (Exception e) {
      appendLog("Error importing files: " + e.getMessage());
    }
  }

  private void updateCityNameOptions() {
    cityName.removeAll();
    cityName.add("Muenster");
    cityName.validate();
  }

  // --- Parameter collection ---
  public Map<String, String> collectParameters() {
    Map<String, String> params = new HashMap<>();
    params.put("cityName", getCityName());
    params.put("days", getDays());
    params.put("population", getPopulation());
    params.put("percentage", getPercentage());
    params.put("jobs", getJobs());
    return params;
  }

  // --- Logging ---
  public void appendLog(String msg) {
    logArea.append(msg + "\n");
  }

  // --- Getters/setters for handler ---
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

  public Button getStartButton() {
    return startButton;
  }

  public Button getRunServerButton() {
    return runServerButton;
  }

  public Button getEndButton() {
    return endButton;
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

  public static void main(String[] args) {
    boolean headless = false;
    for (String arg : args) {
      if (arg.equals("--headless")) {
        headless = true;
        break;
      }
    }

    if (headless) {
      // Headless mode → no GUI
      Map<String, String> params = ArgumentBuilder.parseArgs(args);
      ParameterManager.setParameters(params);

      Pars.javaProject = false;
      Pars.localPath = "";

      System.out.println("[SERVER] Running headless simulation...");
      try {
        // Directly run simulation without Frame/Applet
        Import importer = new Import();
        importer.importFiles();
        Environment.prepare();
        Engine engine = new Engine();
        for (int jobNr = 0; jobNr < Pars.jobs; jobNr++) {
          System.out.println("[SERVER] Executing Job: " + jobNr);
          engine.executeJob(jobNr);
        }
        System.out.println("[SERVER] Simulation finished.");
      } catch (Exception ex) {
        ex.printStackTrace();
      }

    } else {
      // Normal local GUI mode
      PedSimCityApplet applet = new PedSimCityApplet();
      applet.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          applet.dispose();
        }
      });
    }
  }
}
