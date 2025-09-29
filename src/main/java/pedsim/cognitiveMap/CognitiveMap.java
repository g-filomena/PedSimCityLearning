package pedsim.cognitiveMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import pedsim.agents.Agent;
import pedsim.communityCognitiveMap.CommunityCognitiveMap;
import pedsim.engine.PedSimCity;
import pedsim.parameters.LearningPars;
import pedsim.parameters.Pars;
import pedsim.parameters.RouteChoicePars;
import pedsim.routeMemorability.MemoryTrace;
import sim.graph.EdgeGraph;
import sim.graph.GraphUtils;
import sim.graph.Islands;
import sim.graph.NodeGraph;
import sim.graph.NodesLookup;
import sim.routing.Astar;

/**
 * Represents an agent's cognitive map, which provides access to various map attributes. In this
 * version of PedSimCity, this is a simple structure designed for further developments.
 */
public class CognitiveMap extends CommunityCognitiveMap {

  // in the community network
  private final NodeGraph homeNode;
  private final NodeGraph workNode;
  Geometry activityBone;

  // public HashMap<Pair<Polygon, List<NodeGraph>>, MemoryTrace> collage = new
  // LinkedHashMap<>();
  public List<MemoryTrace> memoryTraces = new ArrayList<MemoryTrace>();
  private List<Polygon> cognitiveCollage = new ArrayList<Polygon>();
  Geometry knownSpace = null;

  NetworkBuilder networkBuilder;
  protected Set<Integer> activityBoneNodes = new HashSet<>();
  protected Set<Integer> activityBoneEdges = new HashSet<>();
  protected Set<Integer> agentKnownNodes = new HashSet<>();
  protected Set<Integer> agentKnownEdges = new HashSet<>();

  protected Set<Integer> agentKnownRegions = new HashSet<>();
  protected Set<Integer> agentKnownBarriers = new HashSet<>();
  protected Set<Integer> agentKnownLocalLandmarks = new HashSet<>();

  protected Agent agent;
  GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
  public boolean formed = false;
  // public List<Set<NodeGraph>> islands;
  public double spatialAbility;
  public VividnessGrid vividnessGrid = null;

  /**
   * Constructs an AgentCognitiveMap.
   */
  public CognitiveMap(Agent agent) {

    this.agent = agent;
    homeNode = NodesLookup.randomNodeDMA(CommunityCognitiveMap.getCommunityNetwork(), "live");
    workNode = NodesLookup.randomNodeBetweenDistanceIntervalDMA(
        CommunityCognitiveMap.getCommunityNetwork(), homeNode, 200, 2000, "work");

    spatialAbility = Math.min(1.0, Math.max(0.0,
        LearningPars.MEAN_MEMORY_ROUTES + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5));
  }

  public void formCognitiveMap() {

    buildActivityBone();
    fuseBoneWithCommunityNetwork();
    networkBuilder = new NetworkBuilder(this);
    networkBuilder.buildKnownNetwork();
    identifyKnownUrbanElements();
    formed = true;
    agent.learning.buildBasicMemory();

  }

  private void buildActivityBone() {

    NodeGraph[] knownNodes = {homeNode, workNode};
    Set<NodeGraph> activityBoneNodesTmp = new HashSet<>();
    Queue<NodeGraph> queue = new LinkedList<>();

    for (NodeGraph node : knownNodes) {
      activityBoneNodesTmp.add(node);
      queue.add(node);
      int region = node.getRegionID();
      agentKnownRegions.add(region);
      activityBoneNodesTmp.addAll(PedSimCity.regionsMap.get(region).nodes);
    }

    Map<NodeGraph, Double> distanceMap = new HashMap<>();
    distanceMap.put(homeNode, 0.0);
    distanceMap.put(workNode, 0.0);

    // Step 1: Collect nearby nodes with cumulative distance tracking
    while (!queue.isEmpty()) {
      NodeGraph currentNode = queue.poll();
      double currentDistance = distanceMap.get(currentNode);

      for (EdgeGraph edge : currentNode.getEdges()) {
        NodeGraph neighborNode = edge.getOtherNode(currentNode);
        double newDistance = currentDistance + edge.getLength(); // Cumulative distance

        if (!activityBoneNodesTmp.contains(neighborNode) && newDistance <= Pars.homeWorkRadius) {
          activityBoneNodesTmp.add(neighborNode);
          queue.add(neighborNode);
          distanceMap.put(neighborNode, newDistance); // Store cumulative distance
        }
      }
    }

    // Step 2: Ensure path connectivity (shortest path between home and work)
    Astar astar = new Astar();
    List<NodeGraph> shortestPath = astar.astarRoute(homeNode, workNode,
        CommunityCognitiveMap.getCommunityNetwork(), null).nodesSequence;
    if (!shortestPath.isEmpty()) {
      activityBoneNodesTmp.addAll(shortestPath);
    }

    if (RouteChoicePars.cityCentreRegionsID.length > 0) {
      for (Integer regionID : RouteChoicePars.cityCentreRegionsID) {
        activityBoneNodesTmp.addAll(PedSimCity.regionsMap.get(regionID).nodes);
      }
    }

    for (NodeGraph node : activityBoneNodesTmp) {
      this.activityBoneNodes.add(node.getID());
      this.activityBoneEdges.addAll(GraphUtils.getEdgeIDs(node.getEdges()));
    }
  }

  private void fuseBoneWithCommunityNetwork() {

    for (Integer regionID : RouteChoicePars.cityCentreRegionsID) {
      agentKnownRegions.add(regionID);
      agentKnownNodes.addAll(GraphUtils.getNodeIDs(PedSimCity.regionsMap.get(regionID).nodes));
    }

    agentKnownNodes.addAll(activityBoneNodes);
    agentKnownNodes.addAll(GraphUtils.getNodeIDs(CommunityCognitiveMap.getCommunityKnownNodes()));
    agentKnownEdges.addAll(activityBoneEdges);
    agentKnownEdges.addAll(GraphUtils.getEdgeIDs(CommunityCognitiveMap.getCommunityKnownEdges()));
  }

  public void readjustCognitiveMap(List<Polygon> polygons) {

    agentKnownNodes.clear();
    agentKnownEdges.clear();
    cognitiveCollage = new ArrayList<Polygon>(polygons);
    // for (Pair<Polygon, List<NodeGraph>> pair : collage.keySet()) {
    for (Polygon polygon : cognitiveCollage) {
      // List<NodeGraph> nodesInKnownSpace = pair.getValue1();
      List<NodeGraph> nodesInKnownSpace =
          CommunityCognitiveMap.getCommunityNetwork().getNodesWithinPolygon(polygon);
      agentKnownNodes.addAll(GraphUtils.getNodeIDs(nodesInKnownSpace));
      nodesInKnownSpace
          .forEach(node -> agentKnownEdges.addAll(GraphUtils.getEdgeIDs(node.getEdges())));
    }

    fuseBoneWithCommunityNetwork();
    identifyKnownUrbanElements();
    networkBuilder.buildKnownNetwork();
  }

  private void identifyKnownUrbanElements() {
    deriveOtherKnownRegions();
    findKnownBarriers();
  }

  public void deriveOtherKnownRegions() {

    Islands islands = new Islands(CommunityCognitiveMap.getCommunityNetwork());
    List<Integer> potentiallyKnownRegions = new ArrayList<>();

    for (int nodeID : agentKnownNodes) {
      NodeGraph node = PedSimCity.nodesMap.get(nodeID);
      int regionID = node.getRegionID();
      if (agentKnownRegions.contains(regionID)) {
        continue;
      }
      potentiallyKnownRegions.add(regionID);
    }

    for (int regionID : potentiallyKnownRegions) {
      Set<EdgeGraph> regionEdges = new HashSet<>(getEdgesInKnownNetwork().stream()
          .filter(edge -> edge.getRegionID() == regionID).collect(Collectors.toList()));

      if (islands.findDisconnectedIslands(regionEdges).size() == 1) {
        agentKnownRegions.add(regionID);
      }
    }
  }

  // Methods to add and retrieve nodes, edges, landmarks, and regions
  public void findKnownLocalLandmarks(double localLandmarkThreshold) {

    agentKnownLocalLandmarks = new HashSet<>();

    List<NodeGraph> tmpNodes = GraphUtils.getNodesFromNodeIDs(agentKnownNodes, PedSimCity.nodesMap);
    // Collect local landmarks efficiently using streams
    tmpNodes.stream().flatMap(node -> node.adjacentBuildings.stream()).filter(building -> {
      Double lScore = building.attributes.get("localLandmarkness").getDouble();
      return lScore != null && lScore > localLandmarkThreshold;

    }).map(building -> building.buildingID).forEach(agentKnownLocalLandmarks::add);
  }

  // Methods to add and retrieve nodes, edges, landmarks, and regions
  private void findKnownBarriers() {

    agentKnownBarriers = new HashSet<>();
    List<EdgeGraph> tmpEdges = GraphUtils.getEdgesFromEdgeIDs(agentKnownEdges, PedSimCity.edgesMap);
    for (EdgeGraph edge : tmpEdges) {
      List<Integer> barrierIDs = edge.attributes.get("barriers").getArray();
      agentKnownBarriers.addAll(barrierIDs);
    }
    agentKnownBarriers.addAll(CommunityCognitiveMap.communityKnownBarriers);
  }

  public NodeGraph getHomeNode() {
    return homeNode;
  }

  public NodeGraph getWorkNode() {
    return workNode;
  }

  public Set<Integer> getAgentKnownNodes() {
    return new HashSet<>(agentKnownNodes);
  }

  public Set<Integer> getAgentKnownEdges() {
    return new HashSet<>(agentKnownEdges);
  }

  public Set<Integer> getAgentKnownRegions() {
    return agentKnownRegions;
  }

  /**
   * Gets the local landmarks from the cognitive map.
   *
   * @return The local landmarks.
   */
  @Override
  public Set<Integer> getLocalLandmarksIDs() {
    return agentKnownLocalLandmarks;
  }

  public Set<Integer> getAgentKnownBarriers() {
    return agentKnownBarriers;
  }

  public Set<NodeGraph> getNodesInKnownNetwork() {
    return new HashSet<>(networkBuilder.necessaryNodes);
  }

  public Set<EdgeGraph> getEdgesInKnownNetwork() {
    return new HashSet<>(networkBuilder.necessaryEdges);
  }

  public Set<Integer> getNodeIDsInKnownNetwork() {
    return new HashSet<>(GraphUtils.getNodeIDs(networkBuilder.necessaryNodes));
  }

  public Set<Integer> getEdgeIDsInKnownNetwork() {
    return new HashSet<>(GraphUtils.getEdgeIDs(networkBuilder.necessaryEdges));
  }

  public boolean isRegionKnown(Integer regionID) {
    return agentKnownRegions.contains(regionID);
  }

  public Set<NodeGraph> getNodesInKnownDualNetwork() {
    return new HashSet<>(networkBuilder.necessaryDualNodes);
  }

  public Set<EdgeGraph> getEdgesInKnownDualNetwork() {
    return new HashSet<>(networkBuilder.necessaryDualEdges);
  }

  // public void resetRegionMap() {
  // knownRegionsMap.clear();
  // }

  public boolean isInKnownNetwork(NodeGraph nodeGraph) {
    if (getNodesInKnownNetwork().contains(nodeGraph)) {
      return true;
    } else {
      return false;
    }
  }

  public boolean isInKnownNetwork(EdgeGraph edgeGraph) {
    if (getEdgesInKnownNetwork().contains(edgeGraph)) {
      return true;
    } else {
      return false;
    }
  }

  public double getWayfindingEasinessThreshold(boolean regionBased) {
    // TODO Auto-generated method stub
    return 0;
  }

  public double getLocalLandmarkThreshold() {
    // TODO Auto-generated method stub
    return 0;
  }

  // /**
  // * Gets local landmarks for a specific region.
  // *
  // * @param region The region for which to get local landmarks.
  // * @return A list of local landmarks.
  // */
  // @Override
  // public List<MasonGeometry> getRegionLocalLandmarks(Region region) {
  // List<MasonGeometry> regionLocalLandmarks = new
  // ArrayList<>(region.localLandmarks);
  // regionLocalLandmarks.retainAll(knownLocalLandmarks.getGeometries());
  // return regionLocalLandmarks;
  // }

  // knownNodes.add(homeNode);
  // knownNodes.add(workNode);
}
