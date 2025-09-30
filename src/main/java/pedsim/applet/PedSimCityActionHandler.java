package pedsim.applet;

import java.awt.event.ActionListener;
import pedsim.parameters.Pars;
import pedsim.parameters.TimePars;
import pedsim.utilities.ArgumentBuilder;

public class PedSimCityActionHandler {

  private final PedSimCityApplet applet;
  private final ServerLauncherApplet serverLauncher;

  public PedSimCityActionHandler(PedSimCityApplet applet, ServerLauncherApplet serverLauncher) {
    this.applet = applet;
    this.serverLauncher = serverLauncher;
  }

  public ActionListener runLocalListener() {
    return e -> {
      applet.setRunningOnServer(false);
      prepareEndButton();

      Thread simThread = new Thread(() -> {
        try {
          Pars.cityName = applet.getCityName();
          Pars.jobs = Integer.parseInt(applet.getJobs());
          TimePars.numberOfDays = Integer.parseInt(applet.getDays());
          Pars.population = Integer.parseInt(applet.getPopulation());
          Pars.percentagePopulationAgent = Double.parseDouble(applet.getPercentage());
          Pars.setSimulationParameters();

          applet.runSimulationLocal();
        } catch (Exception ex) {
          applet.appendLog("Error: " + ex.getMessage());
        }
      });
      applet.setSimulationThread(simThread);
      simThread.start();
    };
  }

  public ActionListener runServerListener() {
    return e -> {
      applet.setRunningOnServer(true);
      prepareEndButton();

      String argsString = ArgumentBuilder.buildArgsString(applet.getCityName(), applet.getDays(),
          applet.getPopulation(), applet.getPercentage(), applet.getJobs());
      serverLauncher.runOnServer(argsString, applet);
    };
  }

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
