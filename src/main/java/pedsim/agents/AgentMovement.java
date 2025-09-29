package pedsim.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.planargraph.DirectedEdge;
import pedsim.communityCognitiveMap.CommunityCognitiveMap;
import pedsim.engine.PedSimCity;
import pedsim.parameters.Pars;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.GraphUtils;
import sim.graph.NodeGraph;
import sim.routing.Astar;
import sim.routing.Route;

public class AgentMovement {

  // How much to move the agent by in each step()
  double reach = 0.0;

  // start, current, end position along current line
  DirectedEdge firstDirectedEdge = null;
  private EdgeGraph currentEdge = null;
  private DirectedEdge currentDirectedEdge = null;
  double currentIndex = 0.0;
  double endIndex = 0.0;

  // used by agent to walk along line segment
  int indexOnSequence = 0;
  protected LengthIndexedLine indexedSegment = null;
  protected List<DirectedEdge> directedEdgesSequence = new ArrayList<>();
  private Agent agent;
  private List<DirectedEdge> edgesWalkedSoFar = new ArrayList<>();

  boolean originalRoute = true;
  boolean increaseSpeedAtNight = false;
  Set<Integer> edgesToAvoid;

  PedSimCity state;
  Random random = new Random();

  private NodeGraph currentNode;
  private Graph network;

  public AgentMovement(Agent agent) {
    this.agent = agent;
    this.state = agent.getState();
    this.network = CommunityCognitiveMap.getCommunityNetwork();
  }

  /**
   * Initialises the directedEdgesSequence (the path) for the agent.
   */
  public void initialisePath(Route route) {

    indexOnSequence = 0;
    this.directedEdgesSequence = route.directedEdgesSequence;

    // set up how to traverse this first link
    firstDirectedEdge = directedEdgesSequence.get(indexOnSequence);
    currentNode = (NodeGraph) firstDirectedEdge.getFromNode();
    agent.updateAgentPosition(currentNode.getCoordinate());
    // Sets the Agent up to proceed along an Edge
    setupEdge(firstDirectedEdge);
  }

  /**
   * Sets the agent up to proceed along a specified edge.
   *
   * @param directedEdge The DirectedEdge to traverse next.
   */
  void setupEdge(DirectedEdge directedEdge) {

    currentDirectedEdge = directedEdge;
    currentEdge = (EdgeGraph) currentDirectedEdge.getEdge();

    if (isEdgeCrowded(currentEdge)) {
      rerouteOrIncreaseSpeed();
    }
    updateCounts();

    if (PedSimCity.indexedEdgeCache.containsKey(currentDirectedEdge)) {
      indexedSegment = PedSimCity.indexedEdgeCache.get(currentDirectedEdge);
    } else {
      addIndexedSegment(currentEdge);
      indexedSegment = PedSimCity.indexedEdgeCache.get(currentDirectedEdge);
    }

    currentIndex = indexedSegment.getStartIndex();
    endIndex = indexedSegment.getEndIndex();
    return;
  }

  /**
   * Moves the agent along the current path.
   */
  protected void keepWalking() {

    resetReach(); // as the segment might have changed level of crowdness
    // updateReach();
    // move along the current segment
    currentIndex += reach;

    // check to see if the progress has taken the current index beyond its goal
    // If so, proceed to the next edge
    if (currentIndex > endIndex) {
      final Coordinate currentPos = indexedSegment.extractPoint(endIndex);
      agent.updateAgentPosition(currentPos);
      double residualMove = currentIndex - endIndex;
      transitionToNextEdge(residualMove);
    } else {
      // just update the position!
      final Coordinate currentPos = indexedSegment.extractPoint(currentIndex);
      agent.updateAgentPosition(currentPos);
    }
  }

  /**
   * Transitions to the next edge in the {@code directedEdgesSequence}.
   *
   * @param residualMove The amount of distance the agent can still travel this step.
   */
  void transitionToNextEdge(double residualMove) {

    // update the counter for where the index on the directedEdgesSequence is
    indexOnSequence += 1;
    currentEdge.decrementAgentCount(); // Leave current edge

    // check to make sure the Agent has not reached the end of the
    // directedEdgesSequence already
    if (indexOnSequence >= directedEdgesSequence.size()) {
      agent.reachedDestination.set(true);
      indexOnSequence -= 1; // make sure index is correct
      updateData();
      return;
    }

    // prepare to setup to the next edge
    DirectedEdge nextDirectedEdge = directedEdgesSequence.get(indexOnSequence);
    setupEdge(nextDirectedEdge);

    reach = residualMove;
    currentIndex += reach;

    // check to see if the progress has taken the current index beyond its goal
    // given the direction of movement. If so, proceed to the next edge
    if (currentIndex > endIndex) {
      residualMove = currentIndex - endIndex;
      transitionToNextEdge(residualMove);
    }
  }

  /**
   * Adds an indexed segment to the indexed edge cache.
   *
   * @param edge The edge to add to the indexed edge cache.
   */
  private void addIndexedSegment(EdgeGraph edge) {

    LineString line = edge.getLine();
    double distanceToStart = line.getStartPoint().distance(agent.getLocation().geometry);
    double distanceToEnd = line.getEndPoint().distance(agent.getLocation().geometry);

    if (distanceToEnd < distanceToStart) {
      line = line.reverse();
    }

    final LineString finalLine = line;
    PedSimCity.indexedEdgeCache.put(currentDirectedEdge, new LengthIndexedLine(finalLine));
  }

  /**
   * Resets the agent's movement reach to the base move rate.
   */
  private void resetReach() {
    reach = Pars.moveRate;
  }

  /**
   * Increases the agent's movement reach based on the speed factor for night time.
   */
  private void increaseReach() {
    reach = reach + (Pars.moveRate * Pars.SPEED_INCREMENT_FACTOR);
  }

  /**
   * Checks whether the edge is overcrowded based on the agent count.
   *
   * @param edge The edge to check.
   * @return true if the edge is overcrowded, false otherwise.
   */
  private boolean isEdgeCrowded(EdgeGraph edge) {
    double volumePercentile = calculateVolumesPercentile(20);
    return edge.getAgentCount() >= volumePercentile;
  }

  /**
   * Calculates the volume percentile for a given percentile.
   *
   * @param percentile The percentile to calculate.
   * @return The volume at the given percentile.
   */
  private double calculateVolumesPercentile(int percentile) {
    // Collect volumes from edges (Set to List)
    List<Integer> volumes = PedSimCity.edges.stream().map(EdgeGraph::getAgentCount) // Map each edge
                                                                                    // to its
                                                                                    // agentCount
        .filter(agentCount -> agentCount > 0) // Only keep agent counts greater than 0
        .sorted() // Sort the agent counts
        .collect(Collectors.toList()); // Collect to a List

    // Calculate the index for the percentile
    int index = (int) Math.ceil(percentile / 100.0 * volumes.size()) - 1;
    index = Math.max(0, index); // Ensure the index is within bounds

    // Return the value at the calculated index

    if (!volumes.isEmpty()) {
      return volumes.get(index);
    } else {
      return Double.MAX_VALUE;
    }
  }

  /**
   * Computes an alternative route for the agent to avoid dangerous or unsuitable edges, reusing a
   * cached route if available.
   */
  private void computeAlternativeRoute() {
    NodeGraph currentNode =
        (NodeGraph) edgesWalkedSoFar.get(edgesWalkedSoFar.size() - 1).getToNode();

    // Pair<NodeGraph, NodeGraph> routeKey = Pair.with(currentNode, agent.destinationNode);
    // Map<Pair<NodeGraph, NodeGraph>, List<DirectedEdge>> cache = PedSimCity.alternativeRoutes;
    //
    // // Check if a cached route already exists
    // if (cache.containsKey(routeKey)) {
    // resetPath(new ArrayList<>(cache.get(routeKey)));
    // originalRoute = false;
    // return;
    // }

    setEdgesToAvoid();
    Astar aStar = new Astar();
    Route alternativeRoute =
        aStar.astarRoute(currentNode, agent.destinationNode, network, edgesToAvoid);

    int iteration = 0;
    while (alternativeRoute == null) {
      switch (iteration) {
        case 0 -> {
          // TODO
          // edgesToAvoid.removeAll(CommunityCognitiveMap.getNeighbourhoodEdges());
        }
        default -> {
          edgesToAvoid.clear();
        }
      }
      alternativeRoute = aStar.astarRoute(currentNode, agent.destinationNode,
          CommunityCognitiveMap.getCommunityNetwork(), edgesToAvoid);
      iteration++;
    }

    // Cache and apply the new route
    // cache.put(routeKey, new ArrayList<>(alternativeRoute.directedEdgesSequence));
    resetPath(alternativeRoute.directedEdgesSequence);
    originalRoute = false;
  }

  /**
   * Gets the set of edges that the agent should avoid during movement.
   *
   * @return The set of edges to avoid.
   */
  private void setEdgesToAvoid() {

    // the disregarded one
    edgesToAvoid.addAll(CommunityCognitiveMap.getCommunityNetwork().getEdgeIDs());
    edgesToAvoid.removeAll(agent.getCognitiveMap().getEdgeIDsInKnownNetwork());
    edgesToAvoid.add(currentEdge.getID());
    edgesToAvoid.removeAll(GraphUtils.getEdgeIDs(agent.destinationNode.getEdges()));
  }

  /**
   * Determines whether to reroute the agent or increase its speed.
   */
  private void rerouteOrIncreaseSpeed() {
    if (random.nextDouble() < 0.5 && canReroute()) {
      computeAlternativeRoute();
    } else {
      increaseSpeedAtNight = true;
    }
  }

  /**
   * Checks if the agent can reroute based on the current edge and destination.
   *
   * @return true if the agent can reroute, false otherwise.
   */
  private boolean canReroute() {

    if (currentEdge.getNodes().contains(agent.destinationNode) || indexOnSequence == 0
        || !originalRoute) {
      return false;
    }
    return true;
  }

  /**
   * Updates the counts for the current edge the agent is walking on.
   */
  private void updateCounts() {
    edgesWalkedSoFar.add(currentDirectedEdge);
    currentEdge.incrementAgentCount();
    agent.metersWalkedTot += currentEdge.getLength();
    agent.metersWalkedDay += currentEdge.getLength();
  }

  /**
   * Updates the data related to the agent's ruote to derive pedestrian volumes.
   */
  public void updateData() {
    agent.route.resetRoute(new ArrayList<>(edgesWalkedSoFar));
    state.flowHandler.updateFlowsData(agent, agent.route, agent.learner, null);
  }

  /**
   * Resets the path for the agent to follow a new sequence of directed edges.
   *
   * @param directedEdgesSequence The new sequence of directed edges.
   */
  private void resetPath(List<DirectedEdge> directedEdgesSequence) {

    indexOnSequence = 0;
    this.directedEdgesSequence = directedEdgesSequence;
    currentDirectedEdge = directedEdgesSequence.get(0);

    // set up how to traverse this first link
    currentDirectedEdge = directedEdgesSequence.get(indexOnSequence);
    currentEdge = (EdgeGraph) currentDirectedEdge.getEdge();
    currentNode = (NodeGraph) firstDirectedEdge.getFromNode();
    edgesToAvoid.clear();
    agent.updateAgentPosition(currentNode.getCoordinate());
  }
}
