package pedsim.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javatuples.Pair;
import pedsim.parameters.PopulationPars;
import pedsim.utilities.StringEnum.AgentBarrierType;
import pedsim.utilities.StringEnum.LandmarkType;
import pedsim.utilities.StringEnum.RouteChoiceProperty;
import sim.util.geo.Utilities;

/**
 * `EmpiricalAgentProperties` is a subclass of `AgentProperties` that represents the properties of
 * an agent in a pedestrian simulation with empirical-based parameters. It extends the base
 * `AgentProperties` class to incorporate additional parameters.
 */
public class EmpiricalAgentProperties extends AgentProperties {

  Map<RouteChoiceProperty, Double> elementsMap = new HashMap<>();
  Map<RouteChoiceProperty, Double> minimisationMap = new HashMap<>();
  Map<RouteChoiceProperty, Double> localHeuristicsMap = new HashMap<>();
  Map<RouteChoiceProperty, Double> regionBasedMap = new HashMap<>();
  Map<RouteChoiceProperty, Double> subGoalsMap = new HashMap<>();
  Map<RouteChoiceProperty, Double> distantLandmarksMap = new HashMap<>();
  Map<RouteChoiceProperty, Double> randomElementsMap = new HashMap<>();

  public EmpiricalAgentProperties(Agent agent) {
    super(agent);
  }

  /**
   */
  public void updateProbabilities(List<Double> probs, List<Pair<Double, Double>> pDistribution) {
    for (int i = 0; i < probs.size(); i++) {
      double p = Utilities.fromDistribution(pDistribution.get(i).getValue0(),
          pDistribution.get(i).getValue1(), null);
      probs.set(i, p);
    }
  }

  /**
   * Randomly picks one RouteChoiceProperty based on its probability weight.
   *
   * @param properties The ordered list of candidate properties. The order here determines how ties
   *        are resolved and ensures deterministic iteration.
   * @param weights A map giving each property its probability weight.
   * @return The chosen RouteChoiceProperty.
   */
  protected RouteChoiceProperty pickProperty(List<RouteChoiceProperty> properties,
      Map<RouteChoiceProperty, Double> weights) {
    // Sum all weights
    double totalWeight = 0.0;
    for (RouteChoiceProperty prop : properties) {
      totalWeight += weights.getOrDefault(prop, 0.0);
    }

    // Draw a random value in [0, totalWeight)
    double r = random.nextDouble() * totalWeight;

    // Walk through the properties in the given order until threshold is passed
    double cumulative = 0.0;
    for (RouteChoiceProperty prop : properties) {
      cumulative += weights.getOrDefault(prop, 0.0);
      if (r <= cumulative) {
        return prop;
      }
    }

    // Fallback: return the first property if something goes wrong
    return properties.get(0);
  }

  private void mapGroupProbabilities(List<RouteChoiceProperty> props,
      List<Pair<Double, Double>> pars, Map<RouteChoiceProperty, Double> outMap) {
    // 1:1 mapping
    if (pars.size() == props.size()) {
      for (int i = 0; i < props.size(); i++) {
        double w =
            Utilities.fromDistribution(pars.get(i).getValue0(), pars.get(i).getValue1(), null);
        outMap.put(props.get(i), w);
      }
      return;
    }
    // binary group, single param -> complement
    if (props.size() == 2 && pars.size() == 1) {
      double p = Utilities.fromDistribution(pars.get(0).getValue0(), pars.get(0).getValue1(), null);
      outMap.put(props.get(0), p);
      outMap.put(props.get(1), 1.0 - p);
      return;
    }
    // ternary (subGoals)
    if (props.size() == 3 && pars.size() == 2) {
      double p0 =
          Utilities.fromDistribution(pars.get(0).getValue0(), pars.get(0).getValue1(), null);
      double p1 =
          Utilities.fromDistribution(pars.get(1).getValue0(), pars.get(1).getValue1(), null);
      outMap.put(props.get(0), p0);
      outMap.put(props.get(1), p1);
      outMap.put(props.get(2), 1.0 - (p0 + p1));
      return;
    }
  }

  private void setRouteChoiceParameters() {
    // Elements
    mapGroupProbabilities(
        List.of(RouteChoiceProperty.USING_ELEMENTS, RouteChoiceProperty.NOT_USING_ELEMENTS),
        List.of(new Pair<>(PopulationPars.probUsingElements, PopulationPars.probUsingElementsSD),
            new Pair<>(PopulationPars.probNotUsingElements, PopulationPars.probNotUsingElementsSD)),
        elementsMap);

    // Minimisation
    mapGroupProbabilities(
        List.of(RouteChoiceProperty.ROAD_DISTANCE, RouteChoiceProperty.ANGULAR_CHANGE),
        List.of(new Pair<>(PopulationPars.probRoadDistance, PopulationPars.probRoadDistanceSD),
            new Pair<>(PopulationPars.probAngularChange, PopulationPars.probAngularChangeSD)),
        minimisationMap);

    // Local heuristics
    mapGroupProbabilities(
        List.of(RouteChoiceProperty.ROAD_DISTANCE_LOCAL, RouteChoiceProperty.ANGULAR_CHANGE_LOCAL),
        List.of(
            new Pair<>(PopulationPars.probLocalRoadDistance,
                PopulationPars.probLocalRoadDistanceSD),
            new Pair<>(PopulationPars.probLocalAngularChange,
                PopulationPars.probLocalAngularChangeSD)),
        localHeuristicsMap);

    // Region-based
    mapGroupProbabilities(
        List.of(RouteChoiceProperty.REGION_BASED, RouteChoiceProperty.NOT_REGION_BASED),
        List.of(new Pair<>(PopulationPars.probRegionBasedNavigation,
            PopulationPars.probRegionBasedNavigationSD)),
        regionBasedMap);

    // Subgoals
    mapGroupProbabilities(
        List.of(RouteChoiceProperty.LOCAL_LANDMARKS, RouteChoiceProperty.BARRIER_SUBGOALS,
            RouteChoiceProperty.NO_SUBGOALS),
        List.of(new Pair<>(PopulationPars.probLocalLandmarks, PopulationPars.probLocalLandmarksSD),
            new Pair<>(PopulationPars.probBarrierSubGoals, PopulationPars.probBarrierSubGoalsSD)),
        subGoalsMap);

    // Distant landmarks
    mapGroupProbabilities(
        List.of(RouteChoiceProperty.USING_DISTANT, RouteChoiceProperty.NOT_USING_DISTANT),
        List.of(
            new Pair<>(PopulationPars.probDistantLandmarks, PopulationPars.probDistantLandmarksSD)),
        distantLandmarksMap);

    // Composite random elements
    randomElementsMap.put(RouteChoiceProperty.REGION_BASED,
        regionBasedMap.get(RouteChoiceProperty.REGION_BASED));
    randomElementsMap.put(RouteChoiceProperty.LOCAL_LANDMARKS,
        subGoalsMap.get(RouteChoiceProperty.LOCAL_LANDMARKS));
    randomElementsMap.put(RouteChoiceProperty.BARRIER_SUBGOALS,
        subGoalsMap.get(RouteChoiceProperty.BARRIER_SUBGOALS));
    randomElementsMap.put(RouteChoiceProperty.USING_DISTANT,
        distantLandmarksMap.get(RouteChoiceProperty.USING_DISTANT));

    setBarriersEffect();
  }

  public void routeChoiceMechanismsFromProbs() {
    reset();
    setRouteChoiceParameters();

    // Elements choice
    RouteChoiceProperty elementsChoice = pickProperty(
        List.of(RouteChoiceProperty.USING_ELEMENTS, RouteChoiceProperty.NOT_USING_ELEMENTS),
        elementsMap);
    usingElements = (elementsChoice == RouteChoiceProperty.USING_ELEMENTS);

    // If not using elements, only minimisation and STOP
    if (!usingElements) {
      RouteChoiceProperty minChoice = pickProperty(
          List.of(RouteChoiceProperty.ROAD_DISTANCE, RouteChoiceProperty.ANGULAR_CHANGE),
          minimisationMap);
      minimisingDistance = (minChoice == RouteChoiceProperty.ROAD_DISTANCE);
      minimisingAngular = !minimisingDistance;

      // TEMPORARY override
      if (minimisingAngular) {
        minimisingDistance = true;
        minimisingAngular = false;
      }
      return;
    }

    // Preferences
    preferenceNaturalBarriers = naturalBarriersMean < 0.95;
    aversionSeveringBarriers = severingBarriersMean > 1.05;

    // Local heuristic
    RouteChoiceProperty localChoice = pickProperty(
        List.of(RouteChoiceProperty.ROAD_DISTANCE_LOCAL, RouteChoiceProperty.ANGULAR_CHANGE_LOCAL),
        localHeuristicsMap);
    localHeuristicDistance = (localChoice == RouteChoiceProperty.ROAD_DISTANCE_LOCAL);
    localHeuristicAngular = !localHeuristicDistance;
    if (localHeuristicAngular) {
      localHeuristicDistance = true;
      localHeuristicAngular = false;
    }

    // Activation loop (region / subgoals / distant only)
    while (usingElements && !elementsActivated) {
      // region
      RouteChoiceProperty regionChoice = pickProperty(
          List.of(RouteChoiceProperty.REGION_BASED, RouteChoiceProperty.NOT_REGION_BASED),
          regionBasedMap);
      regionBasedNavigation = (regionChoice == RouteChoiceProperty.REGION_BASED);
      elementsActivated = true;

      // subgoals
      RouteChoiceProperty subChoice = pickProperty(List.of(RouteChoiceProperty.LOCAL_LANDMARKS,
          RouteChoiceProperty.BARRIER_SUBGOALS, RouteChoiceProperty.NO_SUBGOALS), subGoalsMap);
      usingLocalLandmarks = (subChoice == RouteChoiceProperty.LOCAL_LANDMARKS);
      barrierBasedNavigation = (subChoice == RouteChoiceProperty.BARRIER_SUBGOALS);
      if (usingLocalLandmarks || barrierBasedNavigation) {
        elementsActivated = true;
        barrierType = AgentBarrierType.SEPARATING;
        landmarkType = LandmarkType.LOCAL;
      }

      // distant
      RouteChoiceProperty distChoice = pickProperty(
          List.of(RouteChoiceProperty.USING_DISTANT, RouteChoiceProperty.NOT_USING_DISTANT),
          distantLandmarksMap);
      usingDistantLandmarks = (distChoice == RouteChoiceProperty.USING_DISTANT);
      if (usingDistantLandmarks)
        elementsActivated = true;
    }
  }
}
