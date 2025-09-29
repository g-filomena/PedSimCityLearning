package pedsim.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import pedsim.cognitiveMap.CognitiveMap;
import pedsim.cognitiveMap.IncrementalLearning;
import pedsim.communityCognitiveMap.CommunityCognitiveMap;
import pedsim.engine.PedSimCity;
import pedsim.parameters.TimePars;
import pedsim.routePlanner.RoutePlanner;
import pedsim.utilities.StringEnum.AgentStatus;
import pedsim.utilities.StringEnum.Learner;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.graph.Graph;
import sim.graph.GraphUtils;
import sim.graph.NodeGraph;
import sim.graph.NodesLookup;
import sim.routing.Route;
import sim.util.geo.MasonGeometry;

/**
 * This class represents an agent in the pedestrian simulation. Agents move along paths between
 * origin and destination nodes.
 */
public class Agent implements Steppable {

  private static final long serialVersionUID = 1L;
  PedSimCity state;
  public Integer agentID;

  public AgentStatus status;
  protected double timeAtDestination = Double.MAX_VALUE;
  public IncrementalLearning learning;

  // Initial Attributes
  public NodeGraph originNode = null;
  public NodeGraph destinationNode = null;

  private EmpiricalAgentProperties agentProperties;
  private CognitiveMap cognitiveMap;

  Stoppable killAgent;
  private MasonGeometry currentLocation;
  final AtomicBoolean reachedDestination = new AtomicBoolean(false);

  public Route route;
  NodeGraph lastDestination;
  Random random = new Random();
  protected AgentMovement agentMovement;
  double metersWalkedTot;
  private double distanceNextDestination = 0.0;
  public double metersWalkedDay = 0.0;
  public Learner learner;
  private Heuristics heuristics;

  /**
   * Constructor Function. Creates a new agent with the specified agent properties.
   *
   * @param state the PedSimCity simulation state.
   */
  public Agent(PedSimCity state) {

    this.state = state;
    initialiseAgentProperties();
    cognitiveMap = new CognitiveMap(this);
    heuristics = new Heuristics(this);
    status = AgentStatus.WAITING;
    final GeometryFactory fact = new GeometryFactory();
    currentLocation = new MasonGeometry(fact.createPoint(new Coordinate(10, 10)));
    currentLocation.isMovable = true;
    updateAgentPosition(cognitiveMap.getHomeNode().getCoordinate());
    learning = new IncrementalLearning(this);
  }

  public Agent() {}

  /**
   * Initialises the agent properties.
   */
  protected void initialiseAgentProperties() {

    agentProperties = new EmpiricalAgentProperties(this);
  }

  /**
   * This is called every tick by the scheduler. It moves the agent along the path.
   *
   * @param state the simulation state.
   */
  @Override
  public void step(SimState state) {
    final PedSimCity stateSchedule = (PedSimCity) state;
    if (isWaiting()) {
      return;
    }
    if (isWalkingAlone() && destinationNode == null) {
      planNewTrip();
    } else if (reachedDestination.get()) {
      handleReachedDestination(stateSchedule);
    } else if (isAtDestination() && timeAtDestination <= state.schedule.getSteps()) {
      goHome();
    } else if (isAtDestination()) {
      ;
    } else {
      agentMovement.keepWalking();
    }
  }

  private synchronized void planNewTrip() {
    defineOriginDestination();
    if (destinationNode.getID() == originNode.getID()) {
      reachedDestination.set(true);
      return;
    }
    planRoute();
    agentMovement = new AgentMovement(this);
    agentMovement.initialisePath(route);
  }

  public void nextActivity() {

    if (!cognitiveMap.formed) {
      getCognitiveMap().formCognitiveMap();
    }
    startWalkingAlone();
  }

  private void startWalkingAlone() {
    destinationNode = null;
    status = AgentStatus.WALKING;
    updateAgentLists(true, false);
  }

  protected synchronized void defineOriginDestination() {
    defineOrigin();
    defineDestination();
  }

  private void defineOrigin() {

    if (isWalkingAlone()) {
      originNode = cognitiveMap.getHomeNode();
    } else if (isGoingHome()) {
      if (currentLocation.getGeometry().getCoordinate() != lastDestination.getCoordinate()) {
        currentLocation.geometry = lastDestination.getMasonGeometry().geometry;
      }
      originNode = lastDestination;
    }
  }

  private void defineDestination() {
    // Define destination based on agent status
    if (isWalkingAlone()) {
      randomDestination();
    } else if (isGoingHome()) {
      destinationNode = cognitiveMap.getHomeNode();
    }
  }

  private void randomDestination() {

    double lowerLimit = distanceNextDestination * 0.90;
    double upperLimit = distanceNextDestination;
    Graph network = CommunityCognitiveMap.getCommunityNetwork();
    List<NodeGraph> candidates = new ArrayList<>();
    while (candidates.isEmpty()) {
      candidates =
          NodesLookup.getNodesBetweenDistanceInterval(network, originNode, lowerLimit, upperLimit);
      candidates.retainAll(GraphUtils.getNodesFromNodeIDs(getCognitiveMap().getAgentKnownNodes(),
          PedSimCity.nodesMap));
      lowerLimit = lowerLimit * 0.90;
      upperLimit = upperLimit * 1.10;
    }
    destinationNode = NodesLookup.randomNodeFromList(candidates);
  }

  private void handleReachedDestination(PedSimCity stateSchedule) {

    reachedDestination.set(false);
    updateAgentPosition(destinationNode.getCoordinate());

    if ((destinationNode.getID() != originNode.getID()) && route.getLineString().getLength() > 10
        && isLearner()) {
      learning.updateAgentMemory(route);
    }

    updateAgentLists(false, destinationNode == cognitiveMap.getHomeNode());
    originNode = null;
    lastDestination = destinationNode;
    destinationNode = null;
    switch (status) {
      case WALKING:
        handleReachedSoloDestination();
        break;
      case GOING_HOME:
        handleReachedHome();
        break;
      default:
        break;
    }
  }

  /**
   * Moves the agent to the given coordinates.
   *
   * @param c the coordinates.
   */
  public void updateAgentPosition(Coordinate coordinate) {
    GeometryFactory geometryFactory = new GeometryFactory();
    Point newLocation = geometryFactory.createPoint(coordinate);
    state.agents.setGeometryLocation(currentLocation, newLocation);
    currentLocation.geometry = newLocation;
  }

  /**
   * Handles the agent's status when it reaches its solo destination.
   */
  private void handleReachedSoloDestination() {
    status = AgentStatus.AT_DESTINATION;
    calculateTimeAtDestination(state.schedule.getSteps());
  }

  /**
   * Handles the agent's status when it reaches home.
   */
  private void handleReachedHome() {
    status = AgentStatus.WAITING;
  }

  /**
   * Calculates the time the agent will stay at its destination.
   *
   * @param steps the current simulation step.
   */
  protected void calculateTimeAtDestination(long steps) {
    // Generate a random number between 15 (inclusive) and 120 (inclusive)
    int randomMinutes = 15 + random.nextInt(106);
    // Multiply with MINUTES_IN_STEPS
    timeAtDestination = (randomMinutes * TimePars.MINUTE_TO_STEPS) + steps;
  }

  /**
   * The agent goes home after reaching its destination.
   */
  protected void goHome() {

    state.agentsWalking.add(this);
    status = AgentStatus.GOING_HOME;
    planNewTrip();
  }

  /**
   * Updates the agent's status in the agent lists.
   *
   * @param isWalking indicates whether the agent is walking or not.
   * @param reachedHome indicates whether the agent has reached home.
   */
  public void updateAgentLists(boolean isWalking, boolean reachedHome) {

    if (isWalking) {
      state.agentsWalking.add(this);
      state.agentsAtHome.remove(this);
    } else {
      if (reachedHome) {
        state.agentsAtHome.add(this);
      }
      state.agentsWalking.remove(this);
    }
  }

  /**
   * Plans the route for the agent.
   *
   * @throws Exception
   */
  protected void planRoute() {
    RoutePlanner planner = new RoutePlanner(originNode, destinationNode, this);
    route = planner.definePath();
  }

  /**
   * Sets the stoppable reference for the agent.
   *
   * @param a The stoppable reference.
   */
  public void setStoppable(Stoppable a) {
    this.killAgent = a;
  }

  /**
   * Removes the agent from the simulation.
   *
   */
  protected void removeAgent() {
    state.agentsList.remove(this);
    killAgent.stop();
    if (state.agentsList.isEmpty()) {
      state.finish();
    }
  }

  /**
   * Gets the geometry representing the agent's location.
   *
   * @return The geometry representing the agent's location.
   */
  public MasonGeometry getLocation() {
    return currentLocation;
  }

  /**
   * Gets the agent's properties.
   *
   * @return The agent's properties.
   */
  public AgentProperties getProperties() {
    return agentProperties;
  }

  /**
   * Gets the agent's cognitive map.
   *
   * @return The cognitive map.
   */
  public CognitiveMap getCognitiveMap() {
    return cognitiveMap;
  }

  /**
   * Checks if the agent is waiting.
   *
   * @return true if the agent is waiting, false otherwise.
   */
  private boolean isWaiting() {
    return status.equals(AgentStatus.WAITING);
  }

  /**
   * Checks if the agent is walking alone.
   *
   * @return true if the agent is walking alone, false otherwise.
   */
  public boolean isWalkingAlone() {
    return status.equals(AgentStatus.WALKING);
  }

  /**
   * Checks if the agent is going home.
   *
   * @return true if the agent is going home, false otherwise.
   */
  public boolean isGoingHome() {
    return status.equals(AgentStatus.GOING_HOME);
  }

  /**
   * Checks if the agent is at its destination.
   *
   * @return true if the agent is at its destination, false otherwise.
   */
  private boolean isAtDestination() {
    return status.equals(AgentStatus.AT_DESTINATION);
  }

  /**
   * Gets the total distance the agent has walked.
   *
   * @return The total distance the agent has walked in kilometers.
   */
  public double getTotalMetersWalked() {
    return metersWalkedTot;
  }

  /**
   * Gets the distance the agent has walked in the current day.
   *
   * @return The distance walked by the agent today in kilometers.
   */
  public double getMetersWalkedDay() {
    return metersWalkedDay;
  }

  /**
   * Sets the distance to the next destination for the agent.
   *
   * @param distanceNextDestination The distance to the next destination.
   */
  public void setDistanceNextDestination(double distanceNextDestination) {
    this.distanceNextDestination = distanceNextDestination;
  }

  /**
   * Checks if the agent is a learner.
   *
   * @return true if the agent can learn, false otherwise.
   */
  public boolean isLearner() {
    return learner.equals(Learner.LEARNER);
  }

  /**
   * Gets the simulation state of the agent.
   *
   * @return The PedSimCity simulation state.
   */
  public PedSimCity getState() {
    return state;
  }

  public Enum<?> getAgentScenario() {
    return learner;
  }

  public Heuristics getHeuristics() {
    return this.heuristics;
  }
}
