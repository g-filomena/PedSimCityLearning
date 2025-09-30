package pedsim.utilities;

import java.util.Arrays;
import java.util.stream.Collectors;
import pedsim.parameters.Pars;
import pedsim.parameters.RouteChoicePars;

public class ArgumentBuilder {

  public static String buildArgsString(String city, String days, String population,
      String percentage, String jobs) {

    String cityCentre = (RouteChoicePars.cityCentreRegionsID != null)
        ? Arrays.stream(RouteChoicePars.cityCentreRegionsID).map(String::valueOf).collect(
            Collectors.joining(","))
        : "";

    StringBuilder sb = new StringBuilder();
    sb.append("--headless ");
    sb.append("--city=").append(city).append(" ");
    sb.append("--days=").append(days).append(" ");
    sb.append("--population=").append(population).append(" ");
    sb.append("--percentage=").append(percentage).append(" ");
    sb.append("--jobs=").append(jobs).append(" ");

    sb.append("--metersPerDay=").append(Pars.metersPerDayPerPerson).append(" ");
    sb.append("--avgTripDistance=").append(RouteChoicePars.avgTripDistance).append(" ");
    if (!cityCentre.isBlank()) {
      sb.append("--cityCentreRegions=").append(cityCentre).append(" ");
    }
    if (Pars.localPath != null && !Pars.localPath.isBlank()) {
      sb.append("--localPath=").append(Pars.localPath).append(" ");
    }

    sb.append("--distanceNodeLandmark=").append(RouteChoicePars.distanceNodeLandmark).append(" ");
    sb.append("--distanceAnchors=").append(RouteChoicePars.distanceAnchors).append(" ");
    sb.append("--nrAnchors=").append(RouteChoicePars.nrAnchors).append(" ");
    sb.append("--threshold3dVisibility=").append(RouteChoicePars.threshold3dVisibility).append(" ");
    sb.append("--globalLandmarkThreshold=").append(RouteChoicePars.globalLandmarkThresholdCommunity)
        .append(" ");
    sb.append("--localLandmarkThreshold=").append(RouteChoicePars.localLandmarkThresholdCommunity)
        .append(" ");
    sb.append("--salientNodesPercentile=").append(RouteChoicePars.salientNodesPercentile)
        .append(" ");
    sb.append("--wayfindingEasiness=").append(RouteChoicePars.wayfindingEasinessThresholdCommunity)
        .append(" ");
    sb.append("--weightDistance=").append(RouteChoicePars.globalLandmarknessWeightDistanceCommunity)
        .append(" ");
    sb.append("--weightAngular=").append(RouteChoicePars.globalLandmarknessWeightAngularCommunity)
        .append(" ");
    sb.append("--regionNavActivation=").append(RouteChoicePars.regionNavActivationThreshold)
        .append(" ");
    sb.append("--wayfindingEasinessRegions=")
        .append(RouteChoicePars.wayfindingEasinessThresholdRegionsCommunity);

    return sb.toString().trim();
  }
}
