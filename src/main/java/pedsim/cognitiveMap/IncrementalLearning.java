package pedsim.cognitiveMap;

import java.util.ArrayList;
import org.locationtech.jts.geom.Polygon;
import pedsim.agents.Agent;
import pedsim.parameters.LearningPars;
import pedsim.parameters.TimePars;
import pedsim.routeMemorability.MemoryTrace;
import pedsim.routeMemorability.RouteMeaningfulness;
import pedsim.routeMemorability.RouteProperties;
import pedsim.routePlanner.RoutePlanner;
import sim.graph.NodeGraph;
import sim.routing.Route;

public class IncrementalLearning {

  private Agent agent;
  private CognitiveMap cognitiveMap;
  public ArrayList<Route> routesSoFar = new ArrayList<>();
  RouteMeaningfulness routeMeaningfulness;

  // private HashMap<NodeGraph, Integer> visitedLocations = new HashMap<>();
  // private HashMap<NodeGraph, Double> visitedLocationsWeights = new HashMap<>();

  VividnessGrid vividnessGrid;

  public IncrementalLearning(Agent agent) {
    this.agent = agent;
    this.cognitiveMap = agent.getCognitiveMap();
    routeMeaningfulness = new RouteMeaningfulness();
  }

  public void buildBasicMemory() {
    while (routesSoFar.size() < LearningPars.MIN_WALKED_ROUTES_SIZE) {
      // TODO agent.getProperties().randomizeRouteChoiceParameters();

      NodeGraph originNode = cognitiveMap.getHomeNode();
      NodeGraph destinationNode = cognitiveMap.getWorkNode();
      RoutePlanner planner = new RoutePlanner(originNode, destinationNode, agent);
      Route route = planner.definePath();
      RouteProperties routeProperties = new RouteProperties(route, agent);
      routeProperties.computeRouteProperties();
      if (LearningPars.usingMeaningfulness)
        routeMeaningfulness.computeMeaningfulnessFactors(route, routesSoFar);
      routesSoFar.add(route);
    }

    if (LearningPars.usingMeaningfulness) {
      routeMeaningfulness.recomputeInitialRoutesMeaningfulness(routesSoFar);
    }
    for (Route route : routesSoFar)
      expandCollage(route);
  }

  public void updateAgentMemory(Route route) {

    RouteProperties routeProperties = RouteProperties.getProperties(route);
    if (routeProperties == null) {
      routeProperties = new RouteProperties(route, agent);
      routeProperties.computeRouteProperties();
    }

    if (LearningPars.usingMeaningfulness)
      routeMeaningfulness.computeMeaningfulnessFactors(route, routesSoFar);

    expandCollage(route);
    // Add the last route to the list of routes walked so far
    routesSoFar.add(route);
  }

  private void expandCollage(Route route) {

    double memoryWeight = 1.0;
    RouteProperties routeProperties = RouteProperties.getProperties(route);
    Polygon routeVisibilitySpace = routeProperties.getVisibilitySpace();
    if (vividnessGrid == null)
      vividnessGrid =
          new VividnessGrid(routeVisibilitySpace.getEnvelopeInternal(), LearningPars.cellSize);
    if (LearningPars.usingMeaningfulness)
      memoryWeight = routeProperties.getMeaningfulness();

    vividnessGrid.addVisibilitySpace(routeVisibilitySpace, memoryWeight);
    MemoryTrace memoryTrace =
        new MemoryTrace(routeVisibilitySpace, route, memoryWeight, this.agent.getState());
    cognitiveMap.memoryTraces.add(memoryTrace);
    cognitiveMap.readjustCognitiveMap(vividnessGrid.updateCollage(computePercentileThreshold()));
  }

  /**
   * Apply decay to the vividness grid for one simulation step, based on the agent's individual
   * memory ability.
   *
   * If at least one cell falls below the activeThreshold during this step, trigger a callback (to
   * update the cognitive map, etc.).
   *
   * @param memoryAbility value between 0 and 1 (higher = better memory, slower decay)
   */
  public void applyDecay(double memoryAbility) {

    if (vividnessGrid == null)
      return;

    double activeThreshold = computePercentileThreshold();
    float factor = (float) computeDecayFactor(memoryAbility);
    boolean changeTriggered = false;

    for (int i = 0; i < vividnessGrid.density.length; i++) {
      float oldValue = vividnessGrid.density[i];
      vividnessGrid.density[i] *= factor;

      if (vividnessGrid.density[i] < 1e-6f)
        vividnessGrid.density[i] = 0f;

      // if any cell just crossed below threshold, flag it
      if (!changeTriggered && oldValue >= activeThreshold
          && vividnessGrid.density[i] < activeThreshold)
        changeTriggered = true;
    }

    if (changeTriggered)
      cognitiveMap.readjustCognitiveMap(vividnessGrid.updateCollage(activeThreshold));
  }

  /**
   * Compute the per-step decay factor given the base decay rate and individual memory ability.
   *
   * Memory ability is a value between 0 and 1: - 1.0 = excellent memory (slow decay, longest
   * half-life) - 0.0 = very poor memory (fast decay, shortest half-life) - e.g., 0.75 = fairly good
   * memory (close to slow decay)
   *
   * @param memoryAbility between 0 and 1
   * @return per-step decay factor (multiplier per tick)
   */
  public static double computeDecayFactor(double memoryAbility) {
    double lambda = computeBaseDecayRate();

    // Invert ability: higher ability → lower effective decay
    // Effective λ ranges between [lambda, 2*lambda]
    double adjustedLambda = lambda * Math.pow(2.0, 1.0 - memoryAbility);
    return Math.exp(-adjustedLambda * TimePars.STEP_DURATION);
  }

  /**
   * Compute the base decay rate (λ) from the half-life specified in TimePars.
   * 
   * Half-life is the amount of simulated time (in days) required for a memory value to decay to 50%
   * of its original strength if not reinforced.
   *
   * The formula is: λ = ln(2) / halfLifeSeconds
   *
   * @return base decay rate (λ) in per-second units
   */
  public static double computeBaseDecayRate() {
    double halfLifeSeconds = LearningPars.halfLifeMemoryForRoutes * 60 * 60 * 24;
    return Math.log(2.0) / halfLifeSeconds;
  }

  public double computePercentileThreshold() {
    float[] d = vividnessGrid.density;
    int n = 0;
    for (int i = 0; i < d.length; i++)
      if (d[i] > 0f)
        n++;
    if (n == 0)
      return 0.0;

    float[] a = new float[n];
    int p = 0;
    for (int i = 0; i < d.length; i++) {
      float v = d[i];
      if (v > 0f)
        a[p++] = v;
    }

    int k = (int) (LearningPars.memoryPercentile * (n - 1));
    int l = 0, r = n - 1;
    while (true) {
      float pivot = a[(l + r) >>> 1];
      int i = l, j = r;
      while (i <= j) {
        while (a[i] < pivot)
          i++;
        while (a[j] > pivot)
          j--;
        if (i <= j) {
          float t = a[i];
          a[i] = a[j];
          a[j] = t;
          i++;
          j--;
        }
      }
      if (k <= j)
        r = j;
      else if (k >= i)
        l = i;
      else
        return a[k];
    }
  }

  // private int calculatePercentileThreshold(Collection<Integer> values, double
  // percentile) {
  // return values.stream().sorted().skip((long) (values.size() * percentile) -
  // 1).findFirst()
  // .orElse(0);
  // }

  // private void triggerChange() {
  // routeProperties.computeVisibilitySpace();

  // List<NodeGraph> nodesWithinVisibilitySpace =
  // PedSimCity.network.getNodesWithinPolygon(visibilitySpace);
  // Pair<Polygon, List<NodeGraph>> pair = new Pair<>(visibilitySpace,
  // nodesWithinVisibilitySpace);
  // cognitiveMap.collage.put(pair, routeProperties.meaningfulness);
  // cognitiveMap.readjustCognitiveMap();
  // }

  // private void updateVisitedLocations() {
  //
  // for (NodeGraph location : routeProperties.visitedLocations) {
  // visitedLocations.compute(location, (k, v) -> (v == null) ? 1 : v + 1);
  // }
  // for (NodeGraph location : visitedLocations.keySet()) {
  // double count = visitedLocations.get(location);
  // visitedLocationsWeights.put(location, count / visitedLocations.size());
  // }
  //
  // }

  // public void memoryDecay() {
  //
  // if (routesSoFar.size() < MIN_WALKED_ROUTES_SIZE
  // || cognitiveMap.collage.size() < MIN_WALKED_ROUTES_SIZE) {
  // return;
  // }
  //
  // // adjustMemoryPercentile(); // Dynamically adjust the MEMORY_PERCENTILE
  //
  // List<Pair<Polygon, List<NodeGraph>>> cognitiveSpacesToPreserve = new
  // ArrayList<>();
  // if (!visitedLocationsWeights.isEmpty()) {
  // cognitiveSpacesToPreserve = new ArrayList<>(getSpacesToPreserve());
  // }
  //
  // // Create a new collage filtered by percentile
  // HashMap<Pair<Polygon, List<NodeGraph>>, Double> newCollage =
  // new HashMap<>(Utilities.filterMapByPercentile(cognitiveMap.collage,
  // MEMORY_PERCENTILE));
  //
  // // Add the preserved entries to the new collage
  // cognitiveSpacesToPreserve
  // .forEach(geometry -> newCollage.put(geometry,
  // cognitiveMap.collage.get(geometry)));
  //
  // // Update the cognitive map collage
  // cognitiveMap.collage = newCollage;
  // System.out.println("AgentID " + agent.agentID + " size collage " +
  // cognitiveMap.collage.size());
  //
  // }

  // private void adjustMemoryPercentile() {
  // // Example dynamic adjustment based on activity
  // if (agent.getActivityLevel() > HIGH_ACTIVITY_THRESHOLD) {
  // MEMORY_PERCENTILE += 0.1;
  // } else if (agent.getActivityLevel() < LOW_ACTIVITY_THRESHOLD) {
  // MEMORY_PERCENTILE -= 0.1;
  // }
  // MEMORY_PERCENTILE = Math.min(0.9, Math.max(0.1, MEMORY_PERCENTILE));
  // }
  //
  // public List<Pair<Polygon, List<NodeGraph>>> getSpacesToPreserve() {
  //
  // // Calculate the 80th percentile threshold
  // int threshold = calculatePercentileThreshold(visitedLocations.values(),
  // PERCENTILE_THRESHOLD);
  // Set<NodeGraph> mostVisitedLocation = new HashSet<>();
  //
  // // Collect most visited locations
  // mostVisitedLocation =
  // visitedLocations.entrySet().stream().filter(entry -> entry.getValue() >=
  // threshold)
  // .map(Map.Entry::getKey).collect(Collectors.toSet());
  //
  // final Set<NodeGraph> mostVisitedLocationF = new
  // HashSet<>(mostVisitedLocation);
  //
  // // Collect polygons to preserve because they contain the most visited
  // locations
  // return cognitiveMap.collage.keySet().stream()
  // .filter(pair -> mostVisitedLocationF.stream().anyMatch(
  // visitedNode ->
  // pair.getValue0().contains(visitedNode.getMasonGeometry().geometry)))
  // .collect(Collectors.toList());
  //
  // }

  // private void applyDecay(double baseDecayRate, double cognitiveLoad, long
  // currentStep) {
  // for (Map.Entry<Pair<Polygon, List<NodeGraph>>, MemoryTrace> entry :
  // cognitiveMap.collage
  // .entrySet()) {
  // MemoryTrace trace = entry.getValue();
  //
  // // steps since last update
  // long deltaSteps = currentStep - trace.lastUpdated;
  //
  // // convert to elapsed simulation seconds
  // double elapsedSeconds = deltaSteps * TimePars.STEP_DURATION;
  //
  // // decay factor scaled by cognitive load
  // double lambda = baseDecayRate * (1.0 + cognitiveLoad);
  //
  // // exponential decay
  // trace.weight = trace.weight * Math.exp(-lambda * elapsedSeconds);
  //
  // // update lastUpdatedStep
  // trace.lastUpdated = currentStep;
  // }

  // prune traces that have become negligible
  // cognitiveMap.collage.entrySet().removeIf(entry->entry.getValue().weight<1e-6);

}
