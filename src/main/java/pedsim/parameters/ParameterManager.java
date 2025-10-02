package pedsim.parameters;

import java.util.HashMap;
import java.util.Map;
import pedsim.applet.PedSimCityApplet;
import pedsim.applet.RouteChoiceParametersPanel;

/**
 * Central manager for simulation parameters. Provides a unified way to set parameters from: - CLI
 * args - GUI applet - RouteChoice parameters panel
 */
public class ParameterManager {

  private static final Map<String, ParameterSetter> paramMap = new HashMap<>();

  static {
    // --- Core Pars ---
    paramMap.put("cityName", v -> Pars.cityName = v);
    paramMap.put("population", v -> Pars.population = Integer.parseInt(v));
    paramMap.put("percentage", v -> Pars.percentagePopulationAgent = Double.parseDouble(v));
    paramMap.put("jobs", v -> Pars.jobs = Integer.parseInt(v));

    // --- TimePars ---
    paramMap.put("days", v -> TimePars.numberOfDays = Integer.parseInt(v));

    // --- RouteChoicePars ---
    paramMap.put("distanceNodeLandmark",
        v -> RouteChoicePars.distanceNodeLandmark = Double.parseDouble(v));
    paramMap.put("distanceAnchors", v -> RouteChoicePars.distanceAnchors = Double.parseDouble(v));
    paramMap.put("nrAnchors", v -> RouteChoicePars.nrAnchors = (int) Double.parseDouble(v));
    paramMap.put("threshold3dVisibility",
        v -> RouteChoicePars.threshold3dVisibility = Double.parseDouble(v));
    paramMap.put("globalLandmarkThresholdCommunity",
        v -> RouteChoicePars.globalLandmarkThresholdCommunity = Double.parseDouble(v));
    paramMap.put("localLandmarkThresholdCommunity",
        v -> RouteChoicePars.localLandmarkThresholdCommunity = Double.parseDouble(v));
    paramMap.put("salientNodesPercentile",
        v -> RouteChoicePars.salientNodesPercentile = Double.parseDouble(v));
    paramMap.put("wayfindingEasinessThresholdCommunity",
        v -> RouteChoicePars.wayfindingEasinessThresholdCommunity = Double.parseDouble(v));
    paramMap.put("globalLandmarknessWeightDistanceCommunity",
        v -> RouteChoicePars.globalLandmarknessWeightDistanceCommunity = Double.parseDouble(v));
    paramMap.put("globalLandmarknessWeightAngularCommunity",
        v -> RouteChoicePars.globalLandmarknessWeightAngularCommunity = Double.parseDouble(v));
    paramMap.put("regionNavActivationThreshold",
        v -> RouteChoicePars.regionNavActivationThreshold = Double.parseDouble(v));
    paramMap.put("wayfindingEasinessThresholdRegionsCommunity",
        v -> RouteChoicePars.wayfindingEasinessThresholdRegionsCommunity = Double.parseDouble(v));

    // --- Example for list-based args (comma separated) ---
    paramMap.put("cityCentreRegions", v -> {
      String[] tokens = v.split(",");
      Integer[] ids = new Integer[tokens.length];
      for (int i = 0; i < tokens.length; i++)
        ids[i] = Integer.parseInt(tokens[i].trim());
      RouteChoicePars.cityCentreRegionsID = ids;
    });

    // --- Headless flag (no-op) ---
    paramMap.put("headless", v -> {
      /* marker only */ });
  }

  // --------------------------
  // Parameter application
  // --------------------------

  public static void setParameter(String key, String value) {
    ParameterSetter setter = paramMap.get(key);
    if (setter != null)
      setter.apply(value);
    else
      System.err.println("Unknown parameter: " + key);
  }

  public static void setParameters(Map<String, String> params) {
    for (Map.Entry<String, String> entry : params.entrySet())
      setParameter(entry.getKey(), entry.getValue());
  }

  // --------------------------
  // Parse CLI args
  // --------------------------

  public static Map<String, String> parseArgs(String[] args) {
    Map<String, String> params = new HashMap<>();
    for (String arg : args) {
      if (arg.startsWith("--")) {
        String[] parts = arg.substring(2).split("=", 2);
        if (parts.length == 2)
          params.put(parts[0], parts[1]);
        else
          params.put(parts[0], "true"); // for flags like --headless
      }
    }
    return withDefaults(params);
  }

  // --------------------------
  // Apply from GUI
  // --------------------------

  public static void applyFromApplet(PedSimCityApplet applet) {
    setParameter("cityName", applet.getCityName());
    setParameter("days", applet.getDays());
    setParameter("population", applet.getPopulation());
    setParameter("percentage", applet.getPercentage());
    setParameter("jobs", applet.getJobs());
  }

  public static void applyFromRoutePanel(RouteChoiceParametersPanel panel) {
    setParameter("distanceNodeLandmark", panel.getDoubleFieldValue(0));
    setParameter("distanceAnchors", panel.getDoubleFieldValue(1));
    setParameter("nrAnchors", panel.getDoubleFieldValue(2));
    setParameter("threshold3dVisibility", panel.getDoubleFieldValue(3));
    setParameter("globalLandmarkThresholdCommunity", panel.getDoubleFieldValue(4));
    setParameter("localLandmarkThresholdCommunity", panel.getDoubleFieldValue(5));
    setParameter("salientNodesPercentile", panel.getDoubleFieldValue(6));
    setParameter("wayfindingEasinessThresholdCommunity", panel.getDoubleFieldValue(7));
    setParameter("globalLandmarknessWeightDistanceCommunity", panel.getDoubleFieldValue(8));
    setParameter("globalLandmarknessWeightAngularCommunity", panel.getDoubleFieldValue(9));
    setParameter("regionNavActivationThreshold", panel.getDoubleFieldValue(10));
    setParameter("wayfindingEasinessThresholdRegionsCommunity", panel.getDoubleFieldValue(11));
  }

  // --------------------------
  // CLI string builders
  // --------------------------

  public static String toArgString(Map<String, String> params) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      sb.append("--").append(entry.getKey());
      if (!"true".equals(entry.getValue()))
        sb.append("=").append(entry.getValue());
      sb.append(" ");
    }
    return sb.toString().trim();
  }

  public static String toArgStringFromApplet(PedSimCityApplet applet) {
    Map<String, String> params = new HashMap<>();
    params.put("headless", "true");
    params.put("cityName", applet.getCityName());
    params.put("days", applet.getDays());
    params.put("population", applet.getPopulation());
    params.put("percentage", applet.getPercentage());
    params.put("jobs", applet.getJobs());
    return toArgString(withDefaults(params));
  }

  // --------------------------
  // Defaults merger
  // --------------------------

  private static Map<String, String> withDefaults(Map<String, String> params) {
    Map<String, String> all = new java.util.LinkedHashMap<>(params);

    // --- Core ---
    all.putIfAbsent("cityName", Pars.cityName);
    all.putIfAbsent("population", String.valueOf(Pars.population));
    all.putIfAbsent("percentage", String.valueOf(Pars.percentagePopulationAgent));
    all.putIfAbsent("jobs", String.valueOf(Pars.jobs));

    // --- Time ---
    all.putIfAbsent("days", String.valueOf(TimePars.numberOfDays));

    // --- RouteChoice ---
    all.putIfAbsent("distanceNodeLandmark", String.valueOf(RouteChoicePars.distanceNodeLandmark));
    all.putIfAbsent("distanceAnchors", String.valueOf(RouteChoicePars.distanceAnchors));
    all.putIfAbsent("nrAnchors", String.valueOf(RouteChoicePars.nrAnchors));
    all.putIfAbsent("threshold3dVisibility", String.valueOf(RouteChoicePars.threshold3dVisibility));
    all.putIfAbsent("globalLandmarkThresholdCommunity",
        String.valueOf(RouteChoicePars.globalLandmarkThresholdCommunity));
    all.putIfAbsent("localLandmarkThresholdCommunity",
        String.valueOf(RouteChoicePars.localLandmarkThresholdCommunity));
    all.putIfAbsent("salientNodesPercentile",
        String.valueOf(RouteChoicePars.salientNodesPercentile));
    all.putIfAbsent("wayfindingEasinessThresholdCommunity",
        String.valueOf(RouteChoicePars.wayfindingEasinessThresholdCommunity));
    all.putIfAbsent("globalLandmarknessWeightDistanceCommunity",
        String.valueOf(RouteChoicePars.globalLandmarknessWeightDistanceCommunity));
    all.putIfAbsent("globalLandmarknessWeightAngularCommunity",
        String.valueOf(RouteChoicePars.globalLandmarknessWeightAngularCommunity));
    all.putIfAbsent("regionNavActivationThreshold",
        String.valueOf(RouteChoicePars.regionNavActivationThreshold));
    all.putIfAbsent("wayfindingEasinessThresholdRegionsCommunity",
        String.valueOf(RouteChoicePars.wayfindingEasinessThresholdRegionsCommunity));

    // --- Arrays ---
    if (RouteChoicePars.cityCentreRegionsID != null
        && RouteChoicePars.cityCentreRegionsID.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < RouteChoicePars.cityCentreRegionsID.length; i++) {
        sb.append(RouteChoicePars.cityCentreRegionsID[i]);
        if (i < RouteChoicePars.cityCentreRegionsID.length - 1)
          sb.append(",");
      }
      all.putIfAbsent("cityCentreRegions", sb.toString());
    }

    return all;
  }

  @FunctionalInterface
  private interface ParameterSetter {
    void apply(String value);
  }
}
