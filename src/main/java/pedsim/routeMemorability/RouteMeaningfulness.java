package pedsim.routeMemorability;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.distribution.NormalDistribution;
import pedsim.parameters.LearningPars;
import pedsim.utilities.StringEnum.RouteMeaningfulnessFactor;
import sim.routing.Route;

/**
 * Novelty Score: Compare the current path with previous paths taken by the agent. A simple approach
 * could involve measuring the number of unique nodes or regions visited in the path. Exposure
 * Score: Evaluate the proximity or coverage of important locations within a certain distance from
 * the path. This could be done using distance-based metrics or spatial overlays with a layer
 * representing important locations. Spatial Complexity Score: Calculate a measure of spatial
 * complexity based on the number of turns, changes in direction, or variety of street types
 * encountered in the path.
 */
public class RouteMeaningfulness {

  RouteComplexity routeComplexity = new RouteComplexity();
  RouteNovelty routeNovelty = new RouteNovelty();
  final double STD = 0.10;

  public void computeMeaningfulnessFactors(Route lastRoute, List<Route> previousRoutes) {

    routeComplexity.computeRouteComplexity(lastRoute);
    routeComplexity.computeExposureToSalientFeatures(lastRoute);
    routeNovelty.computeNovelty(lastRoute, previousRoutes);
    RouteProperties lastRouteProperties = RouteProperties.getProperties(lastRoute);
    if (previousRoutes.size() > LearningPars.MIN_WALKED_ROUTES_SIZE)
      lastRouteProperties
          .setMeaningfulness(calculateMeaningfulnessZScores(lastRoute, previousRoutes));
  }

  // Method to calculate the meaningfulness score (Me) using the rescaled Z-scores
  // of the three factors
  private double calculateMeaningfulnessZScores(Route route, List<Route> previousRoutes) {

    RouteProperties lastRouteProperties = RouteProperties.getProperties(route);
    List<Double> zScores = new ArrayList<>();
    zScores.add(calculateZScore(1.0 - lastRouteProperties.complexity, previousRoutes,
        RouteMeaningfulnessFactor.EASINESS));
    zScores.add(calculateZScore(lastRouteProperties.exposure, previousRoutes,
        RouteMeaningfulnessFactor.EXPOSURE));
    zScores.add(calculateZScore(lastRouteProperties.novelty, previousRoutes,
        RouteMeaningfulnessFactor.NOVELTY));
    return zScores.stream().mapToDouble(val -> val).average().orElse(0.0);
  }

  private double calculateZScore(double currentRouteValue, List<Route> previousRoutes,
      RouteMeaningfulnessFactor meaningfulnessFactor) {
    // Calculate z-score

    ArrayList<Double> values = new ArrayList<>();
    for (Route previousRoute : previousRoutes) {
      RouteProperties routeProperties = RouteProperties.getProperties(previousRoute);

      double value = switch (meaningfulnessFactor) {
        case EASINESS -> 1.0 - routeProperties.complexity;
        case EXPOSURE -> routeProperties.exposure;
        case NOVELTY -> routeProperties.novelty;
      };
      values.add(value);
    }

    double mean = values.stream().mapToDouble(val -> val).average().orElse(0.0);
    double std = Math
        .sqrt(values.stream().mapToDouble(val -> Math.pow(val - mean, 2)).average().orElse(0.0));

    // The reason for returning the CDF value instead of the z-score is that the CDF
    // value provides a standardised probability measure which is often more
    // intuitive and useful for comparing different distributions. The CDF value is
    // between 0 and 1, representing the percentile rank of the routeValue in the
    // context of the distribution. This makes it easier to interpret and use in
    // further calculations.

    if (std == 0) {
      std = STD; // fallback to avoid division by zero
    }

    // compute standard z-score
    double zScore = (currentRouteValue - mean) / std;
    // use the standard normal distribution (mean=0, std=1)
    NormalDistribution standardNormal = new NormalDistribution(0, 1);
    return standardNormal.cumulativeProbability(zScore);
  }

  // // Recompute the meaningfulness of the initial routes once the
  // // threshold is reached
  public void recomputeInitialRoutesMeaningfulness(List<Route> routes) {

    for (Route route : routes) {
      List<Route> otherRoutes = routes.stream().filter(r -> r != route).toList();
      RouteProperties routeProperties = RouteProperties.getProperties(route);
      routeNovelty.computeNovelty(route, otherRoutes); // recomputing
      routeProperties.setMeaningfulness(calculateMeaningfulnessZScores(route, otherRoutes));
    }
  }
}
