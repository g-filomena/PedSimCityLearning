package pedsim.applet;

import java.awt.event.ActionListener;
import pedsim.engine.PedSimCity;
import pedsim.parameters.ParameterManager;
import pedsim.parameters.Pars;
import pedsim.utilities.LoggerUtil;

/**
 * Handles button actions for PedSimCityApplet (local + server execution).
 */
public class PedSimCityActionHandler {

  private final PedSimCityApplet applet;
  private final ServerLauncherApplet serverLauncher;

  public PedSimCityActionHandler(PedSimCityApplet applet, ServerLauncherApplet serverLauncher) {
    this.applet = applet;
    this.serverLauncher = serverLauncher;
  }

  // -----------------------
  // Run locally
  // -----------------------
  public ActionListener runLocalListener() {
    return e -> {
      applet.setRunningOnServer(false);
      prepareEndButton();

      Thread simThread = new Thread(() -> {
        try {
          // Collect parameters from GUI
          ParameterManager.applyFromApplet(applet);
          // Finalize dependent values
          Pars.setSimulationParameters();
          // Run the core simulation
          PedSimCity.runSimulation();
        } catch (Exception ex) {
          applet.appendLog("Error: " + ex.getMessage());
          LoggerUtil.getLogger().severe("Error running local sim: " + ex.getMessage());
        }
      });

      applet.setSimulationThread(simThread);
      simThread.start();
    };
  }

  // -----------------------
  // Run on server
  // -----------------------
  public ActionListener runServerListener() {
    return e -> {
      applet.setRunningOnServer(true);
      prepareEndButton();

      // Collect parameters → build CLI args string
      String argsString = ParameterManager.toArgStringFromApplet(applet);
      serverLauncher.runOnServer(argsString, applet);
    };
  }

  // -----------------------
  // End simulation
  // -----------------------
  public ActionListener endListener() {
    return e -> {
      if (applet.isRunningOnServer()) {
        serverLauncher.stopOnServer(applet);
      } else {
        if (applet.getSimulationThread() != null && applet.getSimulationThread().isAlive()) {
          System.exit(0);
        }
      }
    };
  }

  private void prepareEndButton() {
    applet.endButton.setBounds(10, 330, 120, 50);
    applet.add(applet.endButton);
    applet.startButton.setVisible(false);
    applet.runServerButton.setVisible(false);
  }
}
