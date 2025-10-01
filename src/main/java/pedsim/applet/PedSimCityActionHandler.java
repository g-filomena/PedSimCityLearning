package pedsim.applet;

import java.awt.event.ActionListener;
import pedsim.parameters.ParameterManager;
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
          // Collect and apply parameters
          ParameterManager.applyFromApplet(applet);

          // Finalize dependent values
          pedsim.parameters.Pars.setSimulationParameters();

          applet.runSimulationLocal();
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

      // Collect parameters → build CLI args
      String argsString = ParameterManager.buildArgsStringFromApplet(applet);
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
    applet.getEndButton().setBounds(10, 330, 120, 50);
    applet.add(applet.getEndButton());
    applet.getStartButton().setVisible(false);
    applet.getRunServerButton().setVisible(false);
  }
}
