package pedsim.routeMemorability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.javatuples.Pair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import pedsim.agents.Agent;
import pedsim.communityCognitiveMap.CommunityCognitiveMap;
import pedsim.engine.PedSimCity;
import pedsim.parameters.RouteChoicePars;
import sim.field.geo.VectorLayer;
import sim.graph.EdgeGraph;
import sim.graph.NodeGraph;
import sim.routing.Route;
import sim.util.geo.MasonGeometry;

/**
 * Novelty Score: Compare the current path with previous paths taken by the agent. A simple approach
 * could involve measuring the number of unique nodes or regions visited in the path. Exposure
 * Score: Evaluate the proximity or coverage of important locations within a certain distance from
 * the path. This could be done using distance-based metrics or spatial overlays with a layer
 * representing important locations. Spatial Complexity Score: Calculate a measure of spatial
 * complexity based on the number of turns, changes in direction, or variety of street types
 * encountered in the path.
 */
public class RouteProperties {

  static final int DISTANCE_ALONG_VISIBILITY = 10;
  static final double BUFFER_RADIUS = 20.0;
  private Route route;
  private Geometry routeBuffer;
  Polygon visibilitySpace;

  protected Set<NodeGraph> visitedLocations = new HashSet<>();
  List<MasonGeometry> buildingsAlong;
  List<MasonGeometry> localLandmarksAlong;
  List<MasonGeometry> globalLandmarksAlong;

  protected static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  protected int turns;
  protected int intersections;
  // TODO
  // protected double ratioGlobalLandmark;
  protected double ratioLocalLandmark;
  protected double cumulativeGlobalLandmarkness;
  protected double cumulativeLocalLandmarkness;

  protected double novelty;
  protected double exposure;
  protected double complexity;
  protected double meaningfulness;
  private Agent agent;

  public RouteProperties(Route route, Agent agent) {

    this.route = route;
    this.agent = agent;
    this.route = route;
    route.attributes.put("properties", this);
  }

  public void computeRouteProperties() {
    countTurnsIntersections();
    cumulativeLandmarkness();

    routeBuffer = route.getLineString().buffer(BUFFER_RADIUS);
    buildingsAlong = new ArrayList<>(PedSimCity.buildings.containedFeatures(routeBuffer));

    if (buildingsAlong.isEmpty())
      // ratioGlobalLandmark = 0.0;
      ratioLocalLandmark = 0.0;

    else {
      globalLandmarksAlong = buildingsAlong.stream().filter(
          building -> CommunityCognitiveMap.getGlobalLandmarks().getGeometries().contains(building))
          .toList();
      localLandmarksAlong = buildingsAlong.stream().filter(
          building -> CommunityCognitiveMap.getLocalLandmarks().getGeometries().contains(building))
          .toList();
      ratioLocalLandmark = localLandmarksAlong.size() / (double) buildingsAlong.size();
    }
    findVisitedLocations();
    computeVisibilitySpace();
  }

  private void findVisitedLocations() {
    // If route.visitedLocations is not empty, map and collect them
    if (!route.getVisitedLocations().isEmpty())
      visitedLocations = route.getVisitedLocations().stream()
          .map(CommunityCognitiveMap.getCommunityNetwork()::findNode).collect(Collectors.toSet());
    else {
      // If visitedLocations is empty, filter nodesSequence for salient nodes
      Set<NodeGraph> salientNodes = CommunityCognitiveMap.getCommunityNetwork()
          .getSalientNodes(RouteChoicePars.salientNodesPercentile).keySet();
      visitedLocations =
          route.nodesSequence.stream().filter(salientNodes::contains).collect(Collectors.toSet());
    }

    visitedLocations.add(route.originNode);
    visitedLocations.add(route.destinationNode);
  }

  private void countTurnsIntersections() {
    intersections = route.nodesSequence.size() - 2;

    for (int i = 0; i < route.dualNodesSequence.size() - 1; i++) {
      NodeGraph dualNode = route.dualNodesSequence.get(i);
      NodeGraph nextDualNode = route.dualNodesSequence.get(i + 1);
      EdgeGraph commonEdge =
          CommunityCognitiveMap.getCommunityDualNetwork().getEdgeBetween(dualNode, nextDualNode);
      if (commonEdge.getDeflectionAngle() > RouteChoicePars.thresholdTurn) {
        turns++;
      }
    }
  }

  private void cumulativeLandmarkness() {

    cumulativeGlobalLandmarkness =
        route.nodesSequence.parallelStream().filter(node -> !node.visibleBuildings3d.isEmpty())
            .mapToDouble(node -> node.visibleBuildings3d.parallelStream()
                .mapToDouble(landmark -> landmark.attributes.get("globalLandmarkness").getDouble())
                .max().orElse(0.0))
            .sum();

    cumulativeLocalLandmarkness =
        route.nodesSequence.parallelStream().filter(node -> !node.visibleBuildings3d.isEmpty())
            .mapToDouble(node -> node.adjacentBuildings.parallelStream()
                .mapToDouble(landmark -> landmark.attributes.get("localLandmarkness").getDouble())
                .filter(value -> value > agent.getCognitiveMap().getLocalLandmarkThreshold()).max()
                .orElse(0.0))
            .sum();
  }

  private void computeVisibilitySpace() {
    List<Geometry> visibleSpaces = IntStream.range(0, route.nodesSequence.size()).mapToObj(i -> {
      NodeGraph node = route.nodesSequence.get(i);
      NodeGraph next = (i < route.nodesSequence.size() - 1) ? route.nodesSequence.get(i + 1) : null;

      List<Geometry> geometries = new ArrayList<>();
      if (next != null) {
        Polygon cone = createVisibilityCone(node, next, 100.0, PedSimCity.buildings, 300.0);
        if (cone != null)
          geometries.add(cone);
      }
      geometries.add(node.getMasonGeometry().geometry.buffer(BUFFER_RADIUS));
      return geometries;
    }).flatMap(List::stream).collect(Collectors.toCollection(ArrayList::new));

    visibleSpaces.add(routeBuffer);
    Geometry combined = GeometryCombiner.combine(visibleSpaces);

    if (combined instanceof MultiPolygon mp && mp.getNumGeometries() == 1)
      visibilitySpace = (Polygon) mp.getGeometryN(0);
    else if (combined instanceof Polygon p)
      visibilitySpace = p;
    else
      visibilitySpace = (Polygon) combined.buffer(0);
  }

  /**
   * Creates a visibility polygon from one node to another considering obstructions and a maximum
   * expansion distance.
   *
   * @param fromNode the starting node of the visibility polygon
   * @param toNode the destination node of the visibility polygon
   * @param visibilityAngle the angle of visibility in degrees
   * @param obstructions a layer containing potential obstructions to visibility
   * @param maxExpansionDistance the maximum distance the visibility polygon can expand
   * @return a Polygon representing the visibility area from the starting node to the destination
   *         node
   */
  private static Polygon createVisibilityCone(NodeGraph fromNode, NodeGraph toNode,
      Double visibilityAngle, VectorLayer obstructions, double maxExpansionDistance) {

    Coordinate from = fromNode.getCoordinate();
    Coordinate to = toNode.getCoordinate();
    Pair<Coordinate, Coordinate> pair = new Pair<>(from, to);

    if (PedSimCity.visibilityPolygonsCache.containsKey(pair))
      return PedSimCity.visibilityPolygonsCache.get(pair);

    double edgeAngle = Math.toDegrees(Math.atan2(to.y - from.y, to.x - from.x));
    int limit = (int) (visibilityAngle / 2.0);

    List<LineString> rays = IntStream
        .iterate(-limit, i -> i <= limit, i -> i + DISTANCE_ALONG_VISIBILITY).mapToObj(i -> {
          double angle = Math.toRadians(edgeAngle + i);
          Coordinate c = new Coordinate(to.x + maxExpansionDistance * Math.cos(angle),
              to.y + maxExpansionDistance * Math.sin(angle));
          return GEOMETRY_FACTORY.createLineString(new Coordinate[] {to, c});
        }).toList();

    Geometry toGeom = toNode.getMasonGeometry().geometry;
    Geometry buffer = toGeom.buffer(maxExpansionDistance);

    List<Geometry> obstacles = obstructions.containedFeatures(buffer).stream().map(m -> m.geometry)
        .filter(g -> rays.stream().anyMatch(r -> g.crosses(r) && !g.touches(toGeom))).toList();

    List<LineString> clipped = obstacles.isEmpty() ? rays : rays.stream().map(r -> {
      Geometry inter = r.intersection(UnaryUnionOp.union(obstacles));
      return inter.isEmpty() ? r
          : GEOMETRY_FACTORY.createLineString(new Coordinate[] {to, inter.getCoordinates()[0]});
    }).toList();

    List<Coordinate> polyCoords = new ArrayList<>();
    polyCoords.add(to);
    clipped.forEach(r -> polyCoords.add(r.getCoordinateN(1)));
    polyCoords.add(to);

    Polygon cone = GEOMETRY_FACTORY.createPolygon(polyCoords.toArray(new Coordinate[0]));
    PedSimCity.visibilityPolygonsCache.put(pair, cone);
    return cone;
  }

  public static RouteProperties getProperties(Route route) {
    return (RouteProperties) route.attributes.get("properties");
  }

  public void setRouteComplexity(double complexity) {
    this.complexity = complexity;
  }

  public void setExposure(double exposure) {
    this.exposure = exposure;
  }

  public void setMeaningfulness(double meaningfulness) {
    this.meaningfulness = meaningfulness;
  }

  public double getMeaningfulness() {
    return meaningfulness;
  }

  public Polygon getVisibilitySpace() {
    return this.visibilitySpace;
  }

}
