package pedsim.applet;

import java.awt.event.ActionListener;

import pedsim.engine.PedSimCity;
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
					ParameterManager.collectParameters(applet);
					PedSimCity.runSimulation();
				} catch (Exception ex) {
					String msg = "Error running local simulation: " + ex.getMessage();
					applet.appendLog(msg);
					LoggerUtil.getLogger().severe(msg);
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
			ParameterManager.collectParametersForServerRun(applet);
			serverLauncher.runOnServer(applet);
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
				Thread t = applet.getSimulationThread();
				if (t != null && t.isAlive()) {
					System.exit(0);
				}
			}
		};
	}

	// -----------------------
	// Button state handling
	// -----------------------
	private void prepareEndButton() {
		applet.endButton.setBounds(10, 330, 120, 50);
		applet.add(applet.endButton);
		applet.startButton.setVisible(false);
		applet.runServerButton.setVisible(false);
	}
}
