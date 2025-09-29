package pedsim.routeMemorability;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import sim.routing.Route;

public class RouteComplexity {

  // Function to compute complexity score based on various factors
  void computeRouteComplexity(Route lastRoute) {

    LineString lastLineString = lastRoute.getLineString();
    double lastRouteLenght = lastRoute.getLength();
    RouteProperties lastRouteProperties = RouteProperties.getProperties(lastRoute);

    double routeLengthKm = lastRouteLenght / 1000.0;
    if (routeLengthKm == 0) {
      lastRouteProperties.complexity = 0.0;
      return;
    }

    double turnsPerKm = lastRouteProperties.turns / routeLengthKm;
    double intersectionsPerKm = lastRouteProperties.intersections / routeLengthKm;
    double landmarkPenalty = 1.0 - lastRouteProperties.ratioLocalLandmark;

    // normalize: cap each contribution to [0,1]
    double normTurns = Math.min(turnsPerKm / 10.0, 1.0); // assume 10 turns/km is "max complex"
    double normIntersections = Math.min(intersectionsPerKm / 10.0, 1.0); // same assumption
    double normLandmarks = landmarkPenalty;

    // directional entropy
    double dirEntropy = computeDirectionalEntropy(lastLineString); // already in [0,1]

    // combine factors
    double complexitySum = normTurns + normIntersections + normLandmarks + dirEntropy;

    lastRouteProperties.setRouteComplexity(complexitySum / 4.0);
  }

  private double computeDirectionalEntropy(LineString line) {
    // Step 1: compute headings for each segment
    List<Double> headings = new ArrayList<>();
    Coordinate[] coords = line.getCoordinates();
    for (int i = 1; i < coords.length; i++) {
      double dx = coords[i].x - coords[i - 1].x;
      double dy = coords[i].y - coords[i - 1].y;
      double angle = Math.atan2(dy, dx); // radians in [-π, π]
      headings.add(angle);
    }

    // Step 2: compute turn angles (differences between consecutive headings)
    List<Double> turns = new ArrayList<>();
    for (int i = 1; i < headings.size(); i++) {
      double diff = headings.get(i) - headings.get(i - 1);
      // normalize to [-π, π]
      diff = Math.atan2(Math.sin(diff), Math.cos(diff));
      turns.add(diff);
    }

    if (turns.isEmpty())
      return 0.0; // no turns → zero entropy

    // Step 3: bin turn angles
    int numBins = 8; // e.g. 8 bins for granularity
    int[] counts = new int[numBins];
    for (double turn : turns) {
      // map [-π, π] → [0, numBins-1]
      int bin = (int) ((turn + Math.PI) / (2 * Math.PI) * numBins);
      if (bin == numBins)
        bin--; // edge case
      counts[bin]++;
    }

    // Step 4: compute probabilities
    double[] probs = new double[numBins];
    int total = turns.size();
    for (int i = 0; i < numBins; i++) {
      probs[i] = counts[i] / (double) total;
    }

    // Step 5: Shannon entropy
    double entropy = 0.0;
    for (double p : probs) {
      if (p > 0) {
        entropy -= p * Math.log(p);
      }
    }

    // Step 6: normalize to [0,1]
    double maxEntropy = Math.log(numBins);
    return entropy / maxEntropy;
  }

  protected void computeExposureToSalientFeatures(Route lastRoute) {

    RouteProperties lastRouteProperties = RouteProperties.getProperties(lastRoute);
    double lastRouteLenght = lastRoute.getLength();

    lastRouteProperties.setExposure(
        Math.min((lastRouteProperties.cumulativeGlobalLandmarkness / lastRouteLenght * 100), 1.0));
  }
}
