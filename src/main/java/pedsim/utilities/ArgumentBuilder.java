package pedsim.utilities;

import java.util.HashMap;
import java.util.Map;
import pedsim.parameters.Pars;
import pedsim.parameters.RouteChoicePars;
import pedsim.parameters.TimePars;

public class ArgumentBuilder {

  /**
   * Parse CLI arguments of the form --key=value or --flag. Unknown params are ignored. Missing ones
   * will later be filled by defaults.
   */
  public static Map<String, String> parseArgs(String[] args) {
    Map<String, String> params = new HashMap<>();
    for (String arg : args) {
      if (arg.startsWith("--")) {
        String[] parts = arg.substring(2).split("=", 2);
        if (parts.length == 2) {
          params.put(parts[0], parts[1]);
        } else {
          // for flags like --headless
          params.put(parts[0], "true");
        }
      }
    }
    return withDefaults(params);
  }

  /**
   * Build CLI string from parameters map.
   */
  public static String buildArgsString(Map<String, String> params) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      sb.append("--").append(entry.getKey());
      if (!entry.getValue().equals("true")) { // flags don’t need =value
        sb.append("=").append(entry.getValue());
      }
      sb.append(" ");
    }
    return sb.toString().trim();
  }

  /**
   * Build CLI args string directly from applet values.
   */
  public static String buildArgsStringFromApplet(pedsim.applet.PedSimCityApplet applet) {
    Map<String, String> params = new HashMap<>();
    params.put("headless", "true");
    params.put("cityName", applet.getCityName());
    params.put("days", applet.getDays());
    params.put("population", applet.getPopulation());
    params.put("percentage", applet.getPercentage());
    params.put("jobs", applet.getJobs());
    return buildArgsString(withDefaults(params));
  }

  /**
   * Fill missing parameters with default values from Pars / TimePars / RouteChoicePars.
   */
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
        if (i < RouteChoicePars.cityCentreRegionsID.length - 1) {
          sb.append(",");
        }
      }
      all.putIfAbsent("cityCentreRegions", sb.toString());
    }

    return all;
  }
}
