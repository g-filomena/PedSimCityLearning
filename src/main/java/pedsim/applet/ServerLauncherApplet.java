package pedsim.applet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import pedsim.utilities.LoggerUtil;

/**
 * Handles launching and stopping the simulation on a remote server via SSH, with PID tracking.
 */
public class ServerLauncherApplet {

  private String sshPath = "C:\\Windows\\System32\\OpenSSH\\ssh.exe";
  private String keyPath =
      "C:\\Users\\gfilo\\OneDrive - The University of Liverpool\\Scripts\\pedsimcityLearning\\id_ed25519";
  private String server = "gabriele@gdsl1.liv.ac.uk";

  private String projectDir = "/mnt/home/gabriele/PedSimCityLearning";
  private String mainClass = "pedsim.applet.PedSimCityApplet";

  private String lastPid = null; // PID of last launched server process

  // --- Getters & setters ---
  public String getSshPath() {
    return sshPath;
  }

  public void setSshPath(String sshPath) {
    this.sshPath = sshPath;
  }

  public String getKeyPath() {
    return keyPath;
  }

  public void setKeyPath(String keyPath) {
    this.keyPath = keyPath;
  }

  public String getServer() {
    return server;
  }

  public void setServer(String server) {
    this.server = server;
  }

  public String getProjectDir() {
    return projectDir;
  }

  public void setProjectDir(String projectDir) {
    this.projectDir = projectDir;
  }

  public String getMainClass() {
    return mainClass;
  }

  public void setMainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  // --- Run on server ---
  public void runOnServer(String argsString, PedSimCityApplet applet) {
    String remoteCmd = "echo '>> Checking Java version' && "
        + "/usr/local/software/java/jdk-21.0.6/bin/java -version && "
        + "/usr/local/software/java/jdk-21.0.6/bin/javac -version && " + "cd " + projectDir + " && "
        + "echo '>> pulling repo' && git pull && "
        + "echo '>> compiling sources' && mkdir -p bin && "
        + "find src/main/java -name '*.java' > sources.txt && "
        + "/usr/local/software/java/jdk-21.0.6/bin/javac -d bin -cp 'bin:lib/*' @sources.txt && "
        + "echo '>> running applet (headless)' && "
        // Run simulation in background, capture its PID
        + "(/usr/local/software/java/jdk-21.0.6/bin/java -cp 'bin:lib/*:src/main/resources' "
        + mainClass + " --headless " + argsString + " & echo $!)";

    applet.appendLog("[SERVER][CMD] " + remoteCmd);

    try {
      ProcessBuilder pb = new ProcessBuilder(sshPath, "-i", keyPath, server, remoteCmd);
      pb.redirectErrorStream(true);
      Process proc = pb.start();

      new Thread(() -> {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            // Detect PID: only digits in a line
            if (lastPid == null && line.matches("\\d+")) {
              lastPid = line.trim();
              applet.appendLog("[SERVER] Captured PID: " + lastPid);
              LoggerUtil.getLogger().info("[SERVER] Captured PID: " + lastPid);
            } else {
              applet.appendLog("[SERVER] " + line);
            }
          }
        } catch (Exception ex) {
          LoggerUtil.getLogger().warning("Error reading server output: " + ex.getMessage());
        }
      }).start();

    } catch (IOException e) {
      LoggerUtil.getLogger().severe("SSH Error: " + e.getMessage());
      applet.appendLog("SSH Error: " + e.getMessage());
    }
  }

  // --- Stop remote simulation using PID ---
  public void stopOnServer(PedSimCityApplet applet) {
    if (lastPid == null) {
      applet.appendLog("[SERVER] No PID recorded, cannot kill process safely.");
      return;
    }
    String killCmd = "kill -9 " + lastPid;
    try {
      ProcessBuilder pb = new ProcessBuilder(sshPath, "-i", keyPath, server, killCmd);
      pb.start();
      applet.appendLog("[SERVER] Sent kill command for PID " + lastPid);
      LoggerUtil.getLogger().info("[SERVER] Killed process PID=" + lastPid);
      lastPid = null; // reset
    } catch (IOException e) {
      applet.appendLog("SSH Error: " + e.getMessage());
    }
  }

  // --- Open configuration panel ---
  public void openConfigPanel() {
    new ServerConfigPanel(this);
  }
}
