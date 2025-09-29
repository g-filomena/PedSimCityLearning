package pedsim.routeMemorability;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.locationtech.jts.geom.LineString;
import pedsim.parameters.LearningPars;
import sim.graph.NodeGraph;
import sim.routing.Route;
import sim.util.geo.Utilities;

public class RouteNovelty {

  private LineString lastLineString;
  private double lastRouteLenght;
  private RouteProperties lastRouteProperties;
  final double MEAN = 0.35;
  final double STD = 0.10;

  void computeNovelty(Route lastRoute, List<Route> previousRoutes) {

    lastRouteProperties = RouteProperties.getProperties(lastRoute);
    if (previousRoutes.size() < LearningPars.MIN_WALKED_ROUTES_SIZE) {
      lastRouteProperties.novelty = Utilities.fromDistribution(MEAN, STD, null);
      return;
    }
    lastLineString = lastRoute.getLineString();
    lastRouteLenght = lastRoute.getLength();

    // --- 1) Overlap length novelty ---
    double overlap = previousRoutes.stream()
        .mapToDouble(route -> lastLineString.intersection(route.getLineString()).getLength()).sum();
    double noveltyByOverlap = 1.0 - overlap / lastRouteLenght;

    // --- 2) Jaccard novelty: pairwise against each previous route ---
    double sumNoveltyByJaccard = 0.0;
    for (Route route : previousRoutes) {
      Set<NodeGraph> currentNodes = new HashSet<>(lastRoute.nodesSequence);
      Set<NodeGraph> prevNodes = new HashSet<>(route.nodesSequence);

      if (!currentNodes.isEmpty() || !prevNodes.isEmpty()) {
        Set<NodeGraph> intersection = new HashSet<>(currentNodes);
        intersection.retainAll(prevNodes);

        Set<NodeGraph> union = new HashSet<>(currentNodes);
        union.addAll(prevNodes);

        double jaccard = (union.isEmpty()) ? 0.0 : (double) intersection.size() / union.size();
        double noveltyThisPair = 1.0 - jaccard;
        sumNoveltyByJaccard += noveltyThisPair;
      }
    }
    double noveltyByJaccard = sumNoveltyByJaccard / previousRoutes.size();

    // --- 3) Combine geometric and topological novelty ---
    lastRouteProperties.novelty = (noveltyByOverlap + noveltyByJaccard) / 2.0;
  }

}
