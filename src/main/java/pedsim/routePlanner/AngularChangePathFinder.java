package pedsim.routePlanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.locationtech.jts.planargraph.DirectedEdge;
import pedsim.agents.Agent;
import pedsim.dijkstra.DijkstraAngularChange;
import sim.graph.NodeGraph;
import sim.routing.Route;
import sim.routing.RoutingUtils;

/**
 * A pathfinder for least cumulative angular change based route calculations. This class extends the
 * functionality of the base class PathFinder.
 */
public class AngularChangePathFinder extends PathFinder {

  /**
   * Formulates the least cumulative angular change shortest path between an origin and a
   * destination node.
   *
   * @param originNode The origin node for the route.
   * @param destinationNode The destination node for the route.
   * @param agent The agent for which the route is completed.
   * @return A Route object representing the calculated route based on angular change.
   */
  public Route angularChangeBased(NodeGraph originNode, NodeGraph destinationNode, Agent agent) {

    this.agent = agent;
    previousJunction = null;

    NodeGraph dualOrigin =
        originNode.getDualNode(originNode, destinationNode, false, previousJunction);
    NodeGraph dualDestination = null;
    while (dualDestination == null || dualDestination.equals(dualOrigin)) {
      dualDestination =
          destinationNode.getDualNode(originNode, destinationNode, false, previousJunction);
    }

    NodeGraph commonJunction = RoutingUtils.getPrimalJunction(dualOrigin, dualDestination);
    if (commonJunction != null) {
      route.directedEdgesSequence.add(network.getDirectedEdgeBetween(originNode, commonJunction));
      route.directedEdgesSequence
          .add(network.getDirectedEdgeBetween(commonJunction, destinationNode));
      return route;
    }

    DijkstraAngularChange dijkstra = new DijkstraAngularChange();
    partialSequence = dijkstra.dijkstraAlgorithm(dualOrigin, dualDestination, destinationNode,
        new HashSet<>(centroidsToAvoid), previousJunction, agent);
    cleanDualPath(originNode, destinationNode);
    partialSequence = sequenceOnCommunityNetwork(partialSequence);
    route.directedEdgesSequence = partialSequence;

    route.computeRouteSequences();
    return route;
  }

  /**
   * Formulates the least cumulative angular change path through a sequence of intermediate nodes
   * [originNode, ..., destinationNode] using the provided agent properties. It allows combining the
   * angular-change local minimisation heuristic with navigational strategies based on the usage of
   * urban elements.
   *
   * @param sequenceNodes A list of nodes representing intermediate nodes.
   * @param agent The agent for which the route is completed.
   * @return A Route object representing the calculated sequence of routes based on angular change.
   */
  public Route angularChangeBasedSequence(List<NodeGraph> sequenceNodes, Agent agent) {

    this.agent = agent;
    this.regionBased = agent.getProperties().regionBasedNavigation;
    this.sequenceNodes = new ArrayList<>(sequenceNodes);

    originNode = sequenceNodes.get(0);
    tmpOrigin = originNode;
    destinationNode = sequenceNodes.get(this.sequenceNodes.size() - 1);
    this.sequenceNodes.remove(0);

    for (final NodeGraph currentNode : this.sequenceNodes) {

      moveOn = false; // for path cleaning and already traversed edges
      tmpDestination = currentNode;
      partialSequence = new ArrayList<>();

      if (tmpOrigin != originNode) {
        centroidsToAvoid = RoutingUtils.getCentroidsFromEdgesSequence(completeSequence);
        previousJunction = RoutingUtils.getPreviousJunction(completeSequence);

        // check if tmpDestination traversed already
        if (nodesFromEdgesSequence(completeSequence).contains(tmpDestination)) {
          controlPath(tmpDestination);
          tmpOrigin = tmpDestination;
          continue;
        }
      }
      // check if edge in between
      if (haveEdgesBetween()) {
        continue;
      }

      List<NodeGraph> dualNodesOrigin = getDualNodes(tmpOrigin, previousJunction);
      List<NodeGraph> dualNodesDestination = getDualNodes(tmpDestination, null);

      for (NodeGraph tmpDualOrigin : dualNodesOrigin) {
        for (NodeGraph tmpDualDestination : dualNodesDestination) {
          // check if just one node separates them
          NodeGraph commonJunction =
              RoutingUtils.getPrimalJunction(tmpDualOrigin, tmpDualDestination);

          if (commonJunction != null) {
            addEdgesCommonJunction(commonJunction);
          } else {
            final DijkstraAngularChange pathfinder = new DijkstraAngularChange();
            Set<NodeGraph> centroidsToAvoidSet = new HashSet<>(centroidsToAvoid);
            partialSequence = pathfinder.dijkstraAlgorithm(tmpDualOrigin, tmpDualDestination,
                destinationNode, centroidsToAvoidSet, tmpOrigin, agent);
          }
          if (!partialSequence.isEmpty()) {
            break;
          }
        }
        if (!partialSequence.isEmpty()) {
          break;
        }
      }

      while (partialSequence.isEmpty() && !moveOn) {
        dualBacktracking();
      }
      if (moveOn) {
        tmpOrigin = tmpDestination;
        continue;
      }
      cleanDualPath(tmpOrigin, tmpDestination);
      completeSequence.addAll(partialSequence);
      tmpOrigin = tmpDestination;
    }
    completeSequence = sequenceOnCommunityNetwork(completeSequence);
    route.directedEdgesSequence = completeSequence;
    route.computeRouteSequences();
    return route;
  }

  /**
   * Adds directed primal edges between the current origin and the common junction node, and between
   * the common junction and the current destination to the partial sequence.
   *
   * @param commonJunction The common junction node between the origin and destination.
   */
  private void addEdgesCommonJunction(NodeGraph commonJunction) {
    DirectedEdge first = network.getDirectedEdgeBetween(tmpOrigin, commonJunction);
    DirectedEdge second = network.getDirectedEdgeBetween(commonJunction, tmpDestination);
    partialSequence.add(first);
    partialSequence.add(second);
  }
}
