package pedsim.cognitiveMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.javatuples.Pair;
import org.locationtech.jts.planargraph.DirectedEdge;
import pedsim.communityCognitiveMap.CommunityCognitiveMap;
import pedsim.engine.PedSimCity;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.GraphUtils;
import sim.graph.Islands;
import sim.graph.NodeGraph;
import sim.routing.Astar;
import sim.routing.Route;

public class NetworkBuilder {

  protected Set<EdgeGraph> necessaryEdges = new HashSet<>();
  protected Set<NodeGraph> necessaryNodes = new HashSet<>();
  protected Set<NodeGraph> necessaryDualNodes = new HashSet<>();
  protected Set<EdgeGraph> necessaryDualEdges = new HashSet<>();

  // they include known nodes as well as nodes that are not known, but represented
  // in the CM
  CognitiveMap cognitiveMap;

  NetworkBuilder(CognitiveMap cognitiveMap) {
    this.cognitiveMap = cognitiveMap;
  }

  protected synchronized void buildKnownNetwork() {

    necessaryEdges = new HashSet<>(
        GraphUtils.getEdgesFromEdgeIDs(cognitiveMap.agentKnownEdges, PedSimCity.edgesMap));

    Graph graph = CommunityCognitiveMap.getCommunityNetwork();
    Islands islands = new Islands(graph);
    if (islands.findDisconnectedIslands(necessaryEdges).size() > 1) {
      necessaryEdges = islands.mergeConnectedIslands(necessaryEdges);
    }
    necessaryNodes = GraphUtils.nodesFromEdges(necessaryEdges);
    buildKNownDualNetwork();
  }

  private void buildKNownDualNetwork() {

    necessaryDualEdges = new HashSet<>();
    for (EdgeGraph edge : necessaryEdges) {
      for (DirectedEdge directedEdge : edge.getDualNode().getOutEdges().getEdges()) {
        necessaryDualEdges.add((EdgeGraph) directedEdge.getEdge());
      }
    }

    Graph dualGraph = CommunityCognitiveMap.getCommunityDualNetwork();
    Islands dualIslands = new Islands(dualGraph);
    if (dualIslands.findDisconnectedIslands(necessaryDualEdges).size() > 1) {
      necessaryDualEdges = dualIslands.mergeConnectedIslands(necessaryDualEdges);
    }
    necessaryDualNodes = GraphUtils.nodesFromEdges(necessaryDualEdges);
    accommodateDualNetwork();
  }

  private void accommodateDualNetwork() {
    necessaryDualNodes.forEach(dualNode -> {
      EdgeGraph edge = dualNode.getPrimalEdge();
      necessaryEdges.add(edge);
      necessaryNodes.addAll(edge.getNodes());
    });
  }

  // known node always in community network
  public void addRouteToNetwork(NodeGraph knownNode, NodeGraph newNode) {

    EdgeGraph edgeBetween =
        CommunityCognitiveMap.getCommunityNetwork().getEdgeBetween(knownNode, newNode);
    Set<EdgeGraph> newEdges = new HashSet<>();
    Route route = null;

    if (edgeBetween == null) {
      route = findMostKnownRoute(knownNode, newNode);
      newEdges.addAll(route.edgesSequence);
    } else {
      newEdges.add(edgeBetween);
    }

    Graph graph = CommunityCognitiveMap.getCommunityNetwork();
    Islands islands = new Islands(graph);
    necessaryEdges.addAll(newEdges);
    necessaryEdges.addAll(newNode.getEdges());
    if (islands.findDisconnectedIslands(necessaryEdges).size() > 1) {
      necessaryEdges = islands.mergeConnectedIslands(necessaryEdges);
    }
    necessaryNodes.addAll(GraphUtils.nodesFromEdges(necessaryEdges));
    // addRouteToDualNetwork(newEdges);
  }

  private void addRouteToDualNetwork(Set<EdgeGraph> newEdges) {

    for (EdgeGraph edge : newEdges) {
      for (DirectedEdge directedEdge : edge.getDualNode().getOutEdges().getEdges()) {
        necessaryDualEdges.add((EdgeGraph) directedEdge.getEdge());
      }
    }

    Graph dualGraph = CommunityCognitiveMap.getCommunityDualNetwork();
    Islands dualIslands = new Islands(dualGraph);
    if (dualIslands.findDisconnectedIslands(necessaryDualEdges).size() > 1) {
      necessaryDualEdges = dualIslands.mergeConnectedIslands(necessaryDualEdges);
    }

    necessaryDualNodes = GraphUtils.nodesFromEdges(necessaryDualEdges);
  }

  private Route findMostKnownRoute(NodeGraph originNode, NodeGraph destinationNode) {
    Pair<NodeGraph, NodeGraph> nodesPair = new Pair<>(originNode, destinationNode);
    Pair<NodeGraph, NodeGraph> reversePair = new Pair<>(destinationNode, originNode);

    // 1. Try to fetch from cache
    Route cached = getRouteFromNetworks(nodesPair, reversePair);
    if (cached != null)
      return cached;

    // 2. Build initial "avoid all" set
    List<Set<EdgeGraph>> edgeCategories = Arrays.asList(CommunityCognitiveMap.tertiaryEdges,
        CommunityCognitiveMap.neighbourhoodEdges, CommunityCognitiveMap.unknownEdges);

    Set<Integer> edgesToAvoid =
        edgeCategories.stream().flatMap(cat -> GraphUtils.getEdgeIDs(cat).stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    Graph communityNetwork = CommunityCognitiveMap.getCommunityNetwork();
    Astar aStar = new Astar();

    // 3. Progressive relaxation loop
    for (int attempt = 0; attempt <= edgeCategories.size(); attempt++) {
      if (attempt > 0) {
        // allow one more category at each retry
        edgesToAvoid.removeAll(GraphUtils.getEdgeIDs(edgeCategories.get(attempt - 1)));
      }

      Route astarRoute =
          aStar.astarRoute(originNode, destinationNode, communityNetwork, edgesToAvoid);
      if (astarRoute != null) {
        cacheRoute(nodesPair, astarRoute, attempt == edgeCategories.size());
        return astarRoute;
      }
    }
    return null;
  }

  private Route getRouteFromNetworks(Pair<NodeGraph, NodeGraph> nodesPair,
      Pair<NodeGraph, NodeGraph> reversePair) {
    // Try normal routes first, then forced routes
    return CommunityCognitiveMap.routesSubNetwork.getOrDefault(nodesPair,
        CommunityCognitiveMap.routesSubNetwork.getOrDefault(reversePair,
            CommunityCognitiveMap.forcedRoutesSubNetwork.getOrDefault(nodesPair,
                CommunityCognitiveMap.forcedRoutesSubNetwork.get(reversePair))));
  }

  /**
   * Store route in appropriate cache.
   */
  private void cacheRoute(Pair<NodeGraph, NodeGraph> nodesPair, Route route, boolean forced) {
    Map<Pair<NodeGraph, NodeGraph>, Route> targetMap =
        forced ? CommunityCognitiveMap.forcedRoutesSubNetwork
            : CommunityCognitiveMap.routesSubNetwork;
    targetMap.put(nodesPair, route);
  }
}
