package pedsim.parameters;

import java.util.HashMap;
import java.util.Map;
import pedsim.applet.PedSimCityApplet;
import pedsim.applet.RouteChoiceParametersPanel;

/**
 * Central manager for simulation parameters. Provides a unified way to set parameters from CLI
 * args, GUI panels, or applets.
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
      for (int i = 0; i < tokens.length; i++) {
        ids[i] = Integer.parseInt(tokens[i].trim());
      }
      RouteChoicePars.cityCentreRegionsID = ids;
    });

    // --- Headless flag (no-op) ---
    paramMap.put("headless", v -> {
      // used only as a marker, no effect here
    });
  }

  /** Sets a parameter by key */
  public static void setParameter(String key, String value) {
    ParameterSetter setter = paramMap.get(key);
    if (setter != null) {
      setter.apply(value);
    } else {
      System.err.println("Unknown parameter: " + key);
    }
  }

  /** Utility for applying multiple parameters at once */
  public static void setParameters(Map<String, String> params) {
    for (Map.Entry<String, String> entry : params.entrySet()) {
      setParameter(entry.getKey(), entry.getValue());
    }
  }

  // ----------------------------------------------------
  // New helpers to apply from Applet/GUI directly
  // ----------------------------------------------------

  public static void applyFromApplet(PedSimCityApplet applet) {
    setParameter("cityName", applet.getCityName());
    setParameter("days", applet.getDays());
    setParameter("population", applet.getPopulation());
    setParameter("percentage", applet.getPercentage());
    setParameter("jobs", applet.getJobs());
  }

  public static void applyFromRoutePanel(RouteChoiceParametersPanel panel) {
    setParameter("distanceNodeLandmark", Double.toString(RouteChoicePars.distanceNodeLandmark));
    setParameter("distanceAnchors", Double.toString(RouteChoicePars.distanceAnchors));
    setParameter("nrAnchors", Integer.toString(RouteChoicePars.nrAnchors));
    setParameter("threshold3dVisibility", Double.toString(RouteChoicePars.threshold3dVisibility));
    setParameter("globalLandmarkThresholdCommunity",
        Double.toString(RouteChoicePars.globalLandmarkThresholdCommunity));
    setParameter("localLandmarkThresholdCommunity",
        Double.toString(RouteChoicePars.localLandmarkThresholdCommunity));
    setParameter("salientNodesPercentile", Double.toString(RouteChoicePars.salientNodesPercentile));
    setParameter("wayfindingEasinessThresholdCommunity",
        Double.toString(RouteChoicePars.wayfindingEasinessThresholdCommunity));
    setParameter("globalLandmarknessWeightDistanceCommunity",
        Double.toString(RouteChoicePars.globalLandmarknessWeightDistanceCommunity));
    setParameter("globalLandmarknessWeightAngularCommunity",
        Double.toString(RouteChoicePars.globalLandmarknessWeightAngularCommunity));
    setParameter("regionNavActivationThreshold",
        Double.toString(RouteChoicePars.regionNavActivationThreshold));
    setParameter("wayfindingEasinessThresholdRegionsCommunity",
        Double.toString(RouteChoicePars.wayfindingEasinessThresholdRegionsCommunity));
  }

  /**
   * Builds a CLI args string (for server launch) from current applet values
   */
  /**
   * Builds a CLI args string (for server launch) from current applet values
   */
  public static String buildArgsStringFromApplet(pedsim.applet.PedSimCityApplet applet) {
    StringBuilder sb = new StringBuilder("--headless");

    // --- Core parameters from applet ---
    sb.append(" --cityName=").append(applet.getCityName());
    sb.append(" --days=").append(applet.getDays());
    sb.append(" --population=").append(applet.getPopulation());
    sb.append(" --percentage=").append(applet.getPercentage());
    sb.append(" --jobs=").append(applet.getJobs());

    // --- RouteChoicePars extras ---
    sb.append(" --distanceNodeLandmark=").append(RouteChoicePars.distanceNodeLandmark);
    sb.append(" --distanceAnchors=").append(RouteChoicePars.distanceAnchors);
    sb.append(" --nrAnchors=").append(RouteChoicePars.nrAnchors);
    sb.append(" --threshold3dVisibility=").append(RouteChoicePars.threshold3dVisibility);

    if (RouteChoicePars.cityCentreRegionsID != null
        && RouteChoicePars.cityCentreRegionsID.length > 0) {
      sb.append(" --cityCentreRegions=");
      for (int i = 0; i < RouteChoicePars.cityCentreRegionsID.length; i++) {
        sb.append(RouteChoicePars.cityCentreRegionsID[i]);
        if (i < RouteChoicePars.cityCentreRegionsID.length - 1) {
          sb.append(",");
        }
      }
    }

    // --- Other optional RouteChoice parameters ---
    sb.append(" --globalLandmarkThresholdCommunity=")
        .append(RouteChoicePars.globalLandmarkThresholdCommunity);
    sb.append(" --localLandmarkThresholdCommunity=")
        .append(RouteChoicePars.localLandmarkThresholdCommunity);
    sb.append(" --salientNodesPercentile=").append(RouteChoicePars.salientNodesPercentile);
    sb.append(" --wayfindingEasinessThresholdCommunity=")
        .append(RouteChoicePars.wayfindingEasinessThresholdCommunity);
    sb.append(" --globalLandmarknessWeightDistanceCommunity=")
        .append(RouteChoicePars.globalLandmarknessWeightDistanceCommunity);
    sb.append(" --globalLandmarknessWeightAngularCommunity=")
        .append(RouteChoicePars.globalLandmarknessWeightAngularCommunity);
    sb.append(" --regionNavActivationThreshold=")
        .append(RouteChoicePars.regionNavActivationThreshold);
    sb.append(" --wayfindingEasinessThresholdRegionsCommunity=")
        .append(RouteChoicePars.wayfindingEasinessThresholdRegionsCommunity);

    return sb.toString();
  }


  @FunctionalInterface
  private interface ParameterSetter {
    void apply(String value);
  }
}
