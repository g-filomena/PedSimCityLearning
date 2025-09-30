package pedsim.applet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ServerLauncherApplet {

  private String sshPath = "C:\\Windows\\System32\\OpenSSH\\ssh.exe";
  private String keyPath =
      "C:\\Users\\gfilo\\OneDrive - The University of Liverpool\\Scripts\\pedsimcityLearning\\id_ed25519";
  private String server = "gabriele@gdsl1.liv.ac.uk";

  // Now configurable as well
  private String projectDir = "/mnt/home/gabriele/PedSimCityLearning";
  private String mainClass = "pedsim.applet.PedSimCityApplet";

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
    String remoteCmd = "cd " + projectDir + " && " + "echo '>> pulling repo' && git pull && "
        + "echo '>> compiling sources' && mkdir -p bin && "
        + "find src/main/java -name '*.java' > sources.txt && "
        + "javac -d bin -cp 'bin:lib/*' @sources.txt && "
        + "echo '>> running applet (headless)' && " + "java -cp 'bin:lib/*' " + mainClass
        + " --headless " + argsString;

    try {
      ProcessBuilder pb = new ProcessBuilder(sshPath, "-i", keyPath, server, remoteCmd);
      Process proc = pb.start();

      // stdout
      new Thread(() -> {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            applet.appendLog("[SERVER] " + line);
          }
        } catch (Exception ex) {
          applet.appendLog("Error reading server logs: " + ex.getMessage());
        }
      }).start();

      // stderr
      new Thread(() -> {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            applet.appendLog("[SERVER][ERR] " + line);
          }
        } catch (Exception ex) {
          applet.appendLog("Error reading server errors: " + ex.getMessage());
        }
      }).start();

    } catch (IOException e) {
      applet.appendLog("SSH Error: " + e.getMessage());
    }
  }

  // --- Stop remote simulation ---
  public void stopOnServer(PedSimCityApplet applet) {
    String killCmd = "pkill -f " + mainClass;
    try {
      ProcessBuilder pb = new ProcessBuilder(sshPath, "-i", keyPath, server, killCmd);
      pb.start();
      applet.appendLog("[SERVER] Sent kill command.");
    } catch (IOException e) {
      applet.appendLog("SSH Error: " + e.getMessage());
    }
  }

  // --- Open configuration panel ---
  public void openConfigPanel() {
    new ServerConfigPanel(this);
  }
}
