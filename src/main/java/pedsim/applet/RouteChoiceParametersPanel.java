package pedsim.applet;

import java.awt.Button;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import pedsim.parameters.RouteChoicePars;

/**
 * A graphical user interface panel for configuring various simulation RouteChoiceParameters.
 */
public class RouteChoiceParametersPanel extends Frame {
  private static final long serialVersionUID = 1L;
  private static final int X = 10;
  private int Y = 50;
  private static final int Y_SPACE_BETWEEN = 30;
  TextField localPathField = new TextField(null);
  ArrayList<TextField> doubleTextFields = new ArrayList<>();
  ArrayList<TextField> booleanTextFields = new ArrayList<>();

  String[] doubleStrings = {"Max distance of a candidate Local Landmark from a Node (m)",
      "Max distance of a Global Landmark (as anchor) from a Node",
      "Max Number of Anchors to be identified for each Destination",
      "Min distance Between a node and the agent destination for considering 3D Visibility",
      "Min score for a building to be considered a Global Landmark [0.0 - 0.95]",
      "Min score for a building to be considered a Local Landmark [0.0 - 0.95]",
      "Percentile for choosing Salient Nodes (on centrality values) [0.0 - 1.0]",
      "Wayfinding Easiness Threshold above which local landmarks are not identified [0.0 - 1.0]",
      "Weight Global Landmarkness in combination with Distance Edge Cost [0.0 - 1.0]",
      "Weight Global Landmarkness in combination with Angular Edge Cost [0.0 - 1.0]",
      "Minimum distance necessary to activate Region-based navigation (m)",
      "Wayfinding Easiness Threshold above which local landmarks are not identified within regions"};

  Double defaultValues[] = {RouteChoicePars.distanceNodeLandmark, RouteChoicePars.distanceAnchors,
      Double.valueOf(RouteChoicePars.nrAnchors), RouteChoicePars.threshold3dVisibility,
      RouteChoicePars.globalLandmarkThresholdCommunity,
      RouteChoicePars.localLandmarkThresholdCommunity, RouteChoicePars.salientNodesPercentile,
      RouteChoicePars.wayfindingEasinessThresholdCommunity,
      RouteChoicePars.globalLandmarknessWeightDistanceCommunity,
      RouteChoicePars.globalLandmarknessWeightAngularCommunity,
      RouteChoicePars.regionNavActivationThreshold,
      RouteChoicePars.wayfindingEasinessThresholdRegionsCommunity};

  /**
   * Constructs the Parameters Panel.
   */
  public RouteChoiceParametersPanel() {
    super("Parameters Panel");
    setLayout(null);

    for (String string : doubleStrings) {
      Double defaultValue = defaultValues[Arrays.asList(doubleStrings).indexOf(string)];
      addDoubleField(string, defaultValue, X, Y);
      Y += Y_SPACE_BETWEEN;
    }

    Label localPathLabel = new Label(
        "Fill this field only with a local path, only if running as Java Project, not from JAR");
    localPathLabel.setBounds(10, 480, 450, 20);
    localPathLabel.setFont(new Font("Arial", Font.ITALIC, 12));
    add(localPathLabel);
    localPathLabel = new Label("e.g.: C:/Users/YourUser/Scripts/pedsimcity/src/main/resources/");
    localPathLabel.setBounds(10, 500, 350, 20);
    localPathLabel.setFont(new Font("Arial", Font.ITALIC, 12));
    add(localPathLabel);

    localPathField.setBounds(360, 500, 350, 20);
    add(localPathField);

    Button applyButton = new Button("Apply");
    applyButton.setBounds(10, 550, 80, 30);
    applyButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        adjustParameters();
        closePanel();
      }
    });
    add(applyButton);

    setSize(800, 600);
    setVisible(true);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        closePanel();
      }
    });
  }

  /**
   * Adds double-interpreter field to the panel for adjusting simulation RouteChoiceParameters.
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
   * Adds a boolean-interpreter field to the panel for adjusting simulation RouteChoiceParameters.
   *
   * @param fieldName The name of the parameter.
   * @param defaultValue The default value for the parameter.
   * @param x The x-coordinate for the field.
   * @param y The y-coordinate for the field.
   */

  /**
   * Adds a boolean-interpreter field to the panel for adjusting simulation RouteChoiceParameters.
   *
   * @param fieldName The name of the parameter.
   * @param defaultValue The default value for the parameter.
   * @param x The x-coordinate for the field.
   * @param y The y-coordinate for the field.
   */
  private void addBooleanField(String fieldName, boolean defaultValue, int x, int y) {
    Label label = new Label(fieldName + ":");
    TextField textField = new TextField(Boolean.toString(defaultValue));
    label.setBounds(x, y, 600, 20);
    textField.setBounds(x + 600, y, 100, 20);
    add(label);
    add(textField);
    booleanTextFields.add(textField);
  }

  public static void main(String[] args) {
    RouteChoiceParametersPanel frame = new RouteChoiceParametersPanel();
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
  }

  /**
   * Adjusts the simulation parameters based on the values entered in text fields. Parses the values
   * and updates the corresponding parameters in the Parameters class.
   */
  private void adjustParameters() {

    RouteChoicePars.distanceNodeLandmark = Double.parseDouble(doubleTextFields.get(0).getText());
    RouteChoicePars.distanceAnchors = Double.parseDouble(doubleTextFields.get(1).getText());
    RouteChoicePars.nrAnchors = (int) Double.parseDouble(doubleTextFields.get(2).getText());
    RouteChoicePars.threshold3dVisibility = Double.parseDouble(doubleTextFields.get(3).getText());
    RouteChoicePars.globalLandmarkThresholdCommunity =
        Double.min(Double.parseDouble(doubleTextFields.get(4).getText()), 0.95);
    RouteChoicePars.localLandmarkThresholdCommunity =
        Double.min(Double.parseDouble(doubleTextFields.get(5).getText()), 0.95);
    RouteChoicePars.salientNodesPercentile =
        Double.min(Double.parseDouble(doubleTextFields.get(6).getText()), 1.0);
    RouteChoicePars.wayfindingEasinessThresholdCommunity =
        Double.min(Double.parseDouble(doubleTextFields.get(7).getText()), 1.0);
    RouteChoicePars.globalLandmarknessWeightDistanceCommunity =
        Double.min(Double.parseDouble(doubleTextFields.get(8).getText()), 1.0);
    RouteChoicePars.globalLandmarknessWeightAngularCommunity =
        Double.min(Double.parseDouble(doubleTextFields.get(9).getText()), 1.0);
    RouteChoicePars.regionNavActivationThreshold =
        Double.parseDouble(doubleTextFields.get(10).getText());
    RouteChoicePars.wayfindingEasinessThresholdRegionsCommunity =
        Double.max(Double.parseDouble(doubleTextFields.get(11).getText()), 1.0);
  }

  /**
   * Closes the Panel.
   */
  private void closePanel() {
    setVisible(false);
    dispose();
  }

  // /**
  // * Retrieves the value of a text field based on its y-coordinate.
  // *
  // * @param y The y-coordinate of the text field.
  // * @return The text entered in the text field or an empty string if not found.
  // */
  // private String getTextFieldValue(int y) {
  // TextField textField = null;
  // for (int i = 0; i < getComponentCount(); i++) {
  // if (getComponent(i) instanceof TextField && getComponent(i).getY() == y) {
  // textField = (TextField) getComponent(i);
  // break;
  // }
  // }
  // return textField != null ? textField.getText() : "";
  // }
}
