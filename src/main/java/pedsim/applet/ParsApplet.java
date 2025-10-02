package pedsim.applet;

import java.awt.Button;
import java.awt.Frame;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import pedsim.parameters.Pars;
import pedsim.parameters.RouteChoicePars;

/**
 * A graphical user interface panel for configuring various simulation parameters.
 */
public class ParsApplet extends Frame {
  private static final long serialVersionUID = 1L;
  private static final int X = 10;
  private int y = 50;
  private static final int Y_SPACE_BETWEEN = 30;
  ArrayList<TextField> doubleTextFields = new ArrayList<>();
  ArrayList<TextField> booleanTextFields = new ArrayList<>();

  TextField cityCentreField = new TextField();

  String[] doubleStrings =
      {"Average distance in meters walked a day, per person", "Average Trip Distance between OD",};

  Double defaultValues[] = {Pars.metersPerDayPerPerson, RouteChoicePars.avgTripDistance};

  /**
   * Constructs the Parameters Panel.
   */
  public ParsApplet() {
    super("Parameters Panel");
    setLayout(null);

    for (String string : doubleStrings) {
      Double defaultValue = defaultValues[Arrays.asList(doubleStrings).indexOf(string)];
      addDoubleField(string, defaultValue, X, y);
      y += Y_SPACE_BETWEEN;
    }

    Label cityCentreLabel = new Label("Enter City Centre Region IDs (comma-separated):");
    cityCentreLabel.setBounds(X, y, 350, 20);
    add(cityCentreLabel);

    String defaultCityCentreRegions = Arrays.stream(RouteChoicePars.cityCentreRegionsID)
        .map(String::valueOf).collect(Collectors.joining(","));
    cityCentreField = new TextField(defaultCityCentreRegions);
    cityCentreField.setBounds(X + 500, y, 200, 20);
    add(cityCentreField);

    y += Y_SPACE_BETWEEN;

    // Apply Button
    Button applyButton = new Button("Apply");
    applyButton.setBounds(X, y, 80, 30);
    applyButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        inputCityCentreRegions();
        closePanel();
      }
    });
    add(applyButton);

    setSize(800, 350);
    setVisible(true);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        closePanel();
      }
    });
  }

  private void inputCityCentreRegions() {
    if (cityCentreField.getText().isBlank()) {
      return;
    }
    String[] cityCentreArray = cityCentreField.getText().split(",");
    Integer[] cityCentreRegions;

    cityCentreRegions = new Integer[cityCentreArray.length];
    for (int i = 0; i < cityCentreArray.length; i++) {
      cityCentreRegions[i] = Integer.parseInt(cityCentreArray[i].trim());
    }

    RouteChoicePars.cityCentreRegionsID = cityCentreRegions;
  }

  /**
   * Adds double-interpreter field to the panel for adjusting simulation parameters.
   *
   * @param fieldName The name of the parameter.
   * @param defaultValue The default value for the parameter.
   * @param x The x-coordinate for the field.
   * @param y The y-coordinate for the field.
   */
  private void addDoubleField(String fieldName, double defaultValue, int x, int y) {
    Label label = new Label(fieldName + ":");
    TextField textField = new TextField(Double.toString(defaultValue));
    label.setBounds(x, y, 600, 20);
    textField.setBounds(x + 600, y, 100, 20);
    add(label);
    add(textField);
    doubleTextFields.add(textField);
  }

  /**
   * Adds a boolean-interpreter field to the panel for adjusting simulation parameters.
   *
   * @param fieldName The name of the parameter.
   * @param defaultValue The default value for the parameter.
   * @param x The x-coordinate for the field.
   * @param y The y-coordinate for the field.
   */

  public static void main(String[] args) {
    ParsApplet frame = new ParsApplet();
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
  }

  /**
   * Closes the Panel.
   */
  private void closePanel() {
    setVisible(false);
    dispose();
  }
}
