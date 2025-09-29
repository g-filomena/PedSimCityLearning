package pedsim.communityCognitiveMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.javatuples.Pair;
import org.locationtech.jts.geom.Geometry;
import pedsim.engine.PedSimCity;
import pedsim.parameters.Pars;
import pedsim.parameters.RouteChoicePars;
import pedsim.utilities.StringEnum.BarrierType;
import pedsim.utilities.StringEnum.RoadType;
import sim.field.geo.VectorLayer;
import sim.graph.Building;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.GraphUtils;
import sim.graph.NodeGraph;
import sim.routing.Route;
import sim.util.geo.MasonGeometry;

/**
 * This class represent a community share cognitive map (or Image of the City) used for storing
 * meaningful information about the environment and, in turn, navigating.
 */
public class CommunityCognitiveMap {

  /**
   * Stores local landmarks as a VectorLayer.
   */
  private static VectorLayer localLandmarks = new VectorLayer();

  /**
   * Stores global landmarks as a VectorLayer.
   */
  private static VectorLayer globalLandmarks = new VectorLayer();

  /**
   * Maps pairs of nodes to gateways.
   */
  public Map<Pair<NodeGraph, NodeGraph>, Gateway> gatewaysMap = new HashMap<>();

  /**
   * Stores barriers as a VectorLayer.
   */
  protected static VectorLayer barriers;
  static Graph communityNetwork;
  static Graph communityDualNetwork;

  private static HashMap<EdgeGraph, RoadType> roadTypeMap = new HashMap<>();

  // known street segments
  protected static Set<NodeGraph> communityKnownNodes = new HashSet<>();
  protected static Set<EdgeGraph> communityKnownEdges = new HashSet<>();
  protected static Set<EdgeGraph> cityCenterEdges;
  protected static Set<Integer> communityKnownRegions = new HashSet<>();

  // road classification
  public static Set<EdgeGraph> primaryEdges = new HashSet<>();
  public static Set<EdgeGraph> secondaryEdges = new HashSet<>();
  public static Set<EdgeGraph> tertiaryEdges = new HashSet<>();
  public static Set<EdgeGraph> neighbourhoodEdges = new HashSet<>();
  public static Set<EdgeGraph> unknownEdges = new HashSet<>();

  // night relevant sets
  protected static Set<EdgeGraph> edgesWithinParks = new HashSet<>();
  protected static Set<EdgeGraph> edgesAlongWater = new HashSet<>();

  // public static Set<Integer> edgesWithinParks = new HashSet<>();
  // public static List<NodeGraph> unknownNodes = new ArrayList<>();

  public static Map<Pair<NodeGraph, NodeGraph>, Route> routesSubNetwork = new ConcurrentHashMap<>();
  public static Map<Pair<NodeGraph, NodeGraph>, Route> forcedRoutesSubNetwork =
      new ConcurrentHashMap<>();
  public static Map<Pair<NodeGraph, NodeGraph>, Double> cachedHeuristics =
      new ConcurrentHashMap<>();

  protected static Set<Integer> communityKnownBarriers = new HashSet<>();

  /**
   * Singleton instance of the CognitiveMap.
   */
  private static final CommunityCognitiveMap instance = new CommunityCognitiveMap();

  Building buildingsHandler = new Building();

  /**
   * Sets up the community cognitive map.
   */
  public static void setCommunityCognitiveMap() {

    setCommunityNetwork(PedSimCity.network, PedSimCity.dualNetwork);
    if (!PedSimCity.buildings.getGeometries().isEmpty()) {
      identifyLandmarks(PedSimCity.buildings);
      integrateLandmarks();
    }
    identifyRegionElements();
    barriers = PedSimCity.barriers;
    setCommunityBarriers();
  }

  /**
   * Gets the singleton instance of CognitiveMap.
   *
   * @return The CognitiveMap instance.
   */
  public static CommunityCognitiveMap getInstance() {
    return instance;
  }

  /**
   * Sets the community network, which includes the road type classification and road
   * classification.
   *
   * @param network The primary network.
   * @param dualNetwork The dual network.
   */
  private static void setCommunityNetwork(Graph network, Graph dualNetwork) {

    communityNetwork = network;
    communityDualNetwork = dualNetwork;

    communityNetwork.getEdges()
        .forEach(edge -> Pars.roadTypes.entrySet().stream()
            .filter(entry -> Arrays.asList(entry.getValue())
                .contains(edge.attributes.get("roadType").getString()))
            .findFirst().ifPresent(entry -> roadTypeMap.put(edge, entry.getKey())));
    buildRoadClassification();
    prepareCommunityKnownEdges();
    buildCommunityKnownNetwork();
  }

  /**
   * Sets the community-known edges based on road classification and city centre edges.
   */
  private static void prepareCommunityKnownEdges() {

    communityKnownEdges = new HashSet<>(primaryEdges);
    communityKnownEdges.addAll(secondaryEdges);
    if (RouteChoicePars.includeTertiary) {
      communityKnownEdges.addAll(tertiaryEdges);
    }

    cityCenterEdges = new HashSet<>();
    for (int regionID : RouteChoicePars.cityCentreRegionsID) {
      communityKnownRegions.add(regionID);
      cityCenterEdges.addAll(PedSimCity.regionsMap.get(regionID).edges);
    }

    communityKnownEdges.addAll(cityCenterEdges);
    // Flatten the list of nodes for each edge
    communityKnownNodes.addAll(communityKnownEdges.stream()
        .flatMap(edge -> edge.getNodes().stream()).collect(Collectors.toSet()));

    // add salient nodes
    Set<NodeGraph> salientNodes = new HashSet<>(
        communityNetwork.getSalientNodes(RouteChoicePars.salientNodesPercentile).keySet());
    communityKnownNodes.addAll(salientNodes);
    communityKnownEdges.addAll(GraphUtils.edgesFromNodes(salientNodes));

  }

  private static void buildCommunityKnownNetwork() {

    // Islands islands = new Islands(communityNetwork);
    // tmpKnowEdges = islands.mergeConnectedIslands(tmpKnowEdges);
    // communityKnownEdges.addAll(tmpKnowEdges);
  }

  /**
   * Builds the road classification by categorising edges into primary, secondary, tertiary,
   * neighbourhood, or unknown road types.
   */
  private static void buildRoadClassification() {

    primaryEdges =
        roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.PRIMARY)
            .map(Map.Entry::getKey).collect(Collectors.toSet());

    secondaryEdges =
        roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.SECONDARY)
            .map(Map.Entry::getKey).collect(Collectors.toSet());

    tertiaryEdges =
        roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.TERTIARY)
            .map(Map.Entry::getKey).collect(Collectors.toSet());

    neighbourhoodEdges =
        roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.NEIGHBOURHOOD)
            .map(Map.Entry::getKey).collect(Collectors.toSet());

    unknownEdges =
        roadTypeMap.entrySet().stream().filter(entry -> entry.getValue() == RoadType.UNKNOWN)
            .map(Map.Entry::getKey).collect(Collectors.toSet());
  }

  // private static double proportionUnknownEdges(NodeGraph node) {
  //
  // List<Integer> edges = GraphUtils.getEdgeIDs(node.getEdges());
  // long unknownEdgesCount =
  // edges.stream().filter(CommunityCognitiveMap.unknownEdges::contains).count();
  // long totalEdgesCount = edges.size();
  // return (double) unknownEdgesCount / totalEdgesCount;
  // }

  /**
   * Integrates landmarks into the street network, sets local landmarkness, and computes global
   * landmarkness values for nodes.
   */
  private static void integrateLandmarks() {
    // Integrate landmarks into the street network
    List<Integer> globalLandmarksID = globalLandmarks.getIntColumn("buildingID");
    VectorLayer sightLinesLight =
        PedSimCity.sightLines.selectFeatures("buildingID", globalLandmarksID, true);
    // free up memory
    PedSimCity.sightLines = null;
    LandmarkIntegration.setLocalLandmarksAtJunctions();
    LandmarkIntegration.assignAnchoringLandmarksToNodes();
    LandmarkIntegration.assignVisibileGlobalLandmakrsToNodes(sightLinesLight);
  }

  /**
   * Integrates landmarks into the street network, sets local landmarkness, and computes global
   * landmarkness values for nodes.
   */
  private static void identifyRegionElements() {

    boolean integrateLandmarks = false;
    if (!PedSimCity.buildings.getGeometries().isEmpty()) {
      integrateLandmarks = true;
    }

    for (final Entry<Integer, Region> entry : PedSimCity.regionsMap.entrySet()) {
      Region region = entry.getValue();
      if (integrateLandmarks) {
        region.buildings = getBuildingsWithinRegion(region);
        setRegionLandmarks(region);
      }
      BarrierIntegration.setRegionBarriers(region);
    }
    barriers = PedSimCity.barriers;
  }

  /**
   * Sets landmarks (local or global) from a set of buildings (VectorLayer) based on a threshold set
   * initially by the user.
   *
   * @param buildings The set of buildings.
   */
  private static void identifyLandmarks(VectorLayer buildings) {

    List<MasonGeometry> buildingsGeometries = buildings.getGeometries();
    for (final MasonGeometry building : buildingsGeometries) {
      if (building
          .getDoubleAttribute("lScore_sc") >= RouteChoicePars.localLandmarkThresholdCommunity) {
        localLandmarks.addGeometry(building);
      }
      if (building
          .getDoubleAttribute("gScore_sc") >= RouteChoicePars.globalLandmarkThresholdCommunity) {
        globalLandmarks.addGeometry(building);
      }
    }
  }

  /**
   * Sets region landmarks (local or global) from a set of buildings (ArrayList) based on a
   * threshold set initially by the user.
   *
   * @param region The region for which to set landmarks.
   */
  private static void setRegionLandmarks(Region region) {

    for (MasonGeometry building : region.buildings) {
      if (building
          .getDoubleAttribute("lScore_sc") >= RouteChoicePars.localLandmarkThresholdCommunity) {
        region.localLandmarks.add(building);
      }
      if (building
          .getDoubleAttribute("gScore_sc") >= RouteChoicePars.globalLandmarkThresholdCommunity) {
        region.globalLandmarks.add(building);
      }
    }
  }

  /**
   * Gets local landmarks for a specific region.
   *
   * @param region The region for which to get local landmarks.
   * @return A list of local landmarks.
   */
  public List<MasonGeometry> getRegionLocalLandmarks(Region region) {
    return region.localLandmarks;
  }

  /**
   * Gets global landmarks for a specific region.
   *
   * @param region The region for which to get global landmarks.
   * @return A list of global landmarks.
   */
  public static List<MasonGeometry> getRegionGlobalLandmarks(Region region) {
    return region.globalLandmarks;
  }

  /**
   * Returns all the buildings enclosed between two nodes.
   *
   * @param originNode The first node.
   * @param destinationNode The second node.
   * @return A list of buildings.
   */
  public List<MasonGeometry> getBuildings(NodeGraph originNode, NodeGraph destinationNode) {
    Geometry smallestCircle = GraphUtils.smallestEnclosingGeometryBetweenNodes(
        new ArrayList<>(Arrays.asList(originNode, destinationNode)));
    return PedSimCity.buildings.containedFeatures(smallestCircle);
  }

  /**
   * Get buildings within a specified region.
   *
   * @param region The region for which buildings are to be retrieved.
   * @return An ArrayList of MasonGeometry objects representing buildings within the region.
   */
  public static List<MasonGeometry> getBuildingsWithinRegion(Region region) {
    VectorLayer regionNetwork = region.regionNetwork;
    Geometry convexHull = regionNetwork.getConvexHull();
    return PedSimCity.buildings.containedFeatures(convexHull);
  }

  // TODO ADD PARK, BASED ON HOW BIG?
  private static void setCommunityBarriers() {

    PedSimCity.barriersMap.forEach((barrierID, barrier) -> {
      BarrierType barrierType = barrier.type;
      if (barrierType.equals(BarrierType.WATER) || barrierType.equals(BarrierType.ROAD)) {
        communityKnownBarriers.add(barrierID);
      }
    });
  }

  public static Graph getCommunityNetwork() {
    return communityNetwork;
  }

  public static Graph getCommunityDualNetwork() {
    return communityDualNetwork;
  }

  public static Set<NodeGraph> getCommunityKnownNodes() {
    return communityKnownNodes;
  }

  public static Set<EdgeGraph> getCommunityKnownEdges() {
    return communityKnownEdges;
  }

  public Set<Integer> getLocalLandmarksIDs() {
    return new HashSet<Integer>(localLandmarks.getIDs());
  }

  public static VectorLayer getLocalLandmarks() {
    return localLandmarks;
  }

  public static VectorLayer getGlobalLandmarks() {
    return globalLandmarks;
  }

  public static VectorLayer getBarriers() {
    return barriers;
  }

  public static VectorLayer getBuildings() {
    return PedSimCity.buildings;
  }

  public static Route getRoutes(Pair<NodeGraph, NodeGraph> nodePair) {
    NodeGraph node = nodePair.getValue0();
    NodeGraph otherNode = nodePair.getValue1();
    Pair<NodeGraph, NodeGraph> reversePair = new Pair<>(otherNode, node);
    return routesSubNetwork.getOrDefault(nodePair, routesSubNetwork.get(reversePair));
  }

  public static Route getForcedRoutes(Pair<NodeGraph, NodeGraph> nodePair) {
    NodeGraph node = nodePair.getValue0();
    NodeGraph otherNode = nodePair.getValue1();
    Pair<NodeGraph, NodeGraph> reversePair = new Pair<>(otherNode, node);
    return forcedRoutesSubNetwork.getOrDefault(nodePair, forcedRoutesSubNetwork.get(reversePair));
  }

}
