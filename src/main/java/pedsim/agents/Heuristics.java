package pedsim.agents;

import java.util.Random;

import pedsim.cognitiveMap.ElementsSensitivity;
import pedsim.cognitiveMap.VividnessGrid;
import pedsim.parameters.RouteChoicePars;
import sim.graph.NodeGraph;

/**
 * HeuristicMixer: compute route-choice weights based on vividness and memory ability.
 */
public final class Heuristics {

  // === Minimisation Heuristics (distance vs angular) ===
  // Baseline reliance on distance minimisation for novices (low vividness).
  private static final double WEIGHT_DISTANCE_NOVICE = 0.1;
  // Maximum reliance on distance minimisation for experts (high vividness).
  private static final double WEIGHT_DISTANCE_EXPERT = 0.9;
  // Threshold in vividness (0–1) where the sigmoid crosses 0.5.
  // e.g. 0.6 → below this, angular dominates; above this, distance dominates.
  private static final double VIVIDNESS_THRESHOLD = 0.6;
  // Controls steepness of the sigmoid transition.
  // Low (≈3.0) → gradual; high (≈10.0) → sharp expert/novice divide.
  private static final double SIGMOID_STEEPNESS = 10.0;

  /*
   * Curve behaviour (Distance Minimisation vs Angular Minimisation)
   * --------------------------------------------------------------- probDistanceMinimisation =
   * sigmoid(effectiveVividness) probAngularMinimisation = 1 - probDistanceMinimisation
   * 
   * effectiveVividness = 0.0 → Distance ≈ 0.1, Angular ≈ 0.9 effectiveVividness = 0.5 → Distance ≈
   * 0.5, Angular ≈ 0.5 effectiveVividness = 1.0 → Distance ≈ 0.9, Angular ≈ 0.1
   * 
   * Visual intuition: Distance Minimisation (experts ↑): [███▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒] → rises
   * with vividness Angular Minimisation (novices ↑): [▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒███] → fades with
   * vividness
   */

  // Exponents shaping how vividness influences probabilities
  // --- Exponents controlling sensitivity of each heuristic to effective
  // vividness ---
  // Effective vividness scale: 0 = novice-like (fragmented, vague knowledge),
  // 1 = expert-like (rich, structured knowledge).
  // A higher exponent → stronger non-linear response (steeper growth or decay).
  // Below each, [Novice..................Expert] shows how the curve looks
  // intuitively.

  // Barrier sub-goals (novices rely on barriers like rivers/walls).
  // EXP = 1.0 → linear decrease as vividness grows.
  // [█████████████.................] ← common for novices, rare for experts
  private static final double EXPONENT_BARRIERS = 1.0;

  // Global (distant) landmarks (cathedral tower, mountain).
  // EXP = 1.0 → linear decrease as vividness grows.
  // Global landmarks use Math.pow(1.0 - effectiveVividness,
  // EXPONENT_GLOBAL_LANDMARKS)
  // [██████████....................] ← common for novices, rare for experts
  private static final double EXPONENT_GLOBAL_LANDMARKS = 1.0;

  // Region-based segmentation (chunking the city).
  // EXP = 1.0 → linear increase.
  // [..............█████████████...] ← rarely novices, steadily adopted by
  // experts
  private static final double EXPONENT_REGIONS = 1.0;

  // Local landmarks (shops, corners, squares).
  // EXP = 1.5 → steeper growth than linear.
  // [..................████████████] ← weak for novices, strongly used by experts
  private static final double EXPONENT_LOCAL_LANDMARKS = 1.5;

  // Probabilities of activation (softmax-normalised)
  private double probabilityDistantLandmarks;
  private double probabilityUsingRegions;
  private double probabilityBarrierSubGoals;
  private double probabilityDistanceMinimisation;

  final Random random = new Random();

  private double spatialAbility;
  private double probabilityAngularMinimisation;
  private VividnessGrid vividnessGrid;
  private double effectiveVividness = 0.0;
  private AgentProperties ap;
  private ElementsSensitivity elementsSensitivity;
  private Agent agent;

  public Heuristics(Agent agent) {
    this.agent = agent;
    this.ap = agent.getProperties();
    this.elementsSensitivity = new ElementsSensitivity();
    this.spatialAbility = agent.getCognitiveMap().spatialAbility;
    this.vividnessGrid = agent.getCognitiveMap().vividnessGrid;
  }

  public void defineHeuristic(NodeGraph originNode, NodeGraph destinationNode) {

    if (!agent.isLearner() | vividnessGrid == null) {
      ((EmpiricalAgentProperties) ap).routeChoiceMechanismsFromProbs();
      return;
    }
    // Step 1: compute raw vividness from grid
    double currentRouteRawVividness =
        vividnessGrid.vividnessBetweenSpace(originNode, destinationNode);
    effectiveVividness =
        vividnessGrid.computeEffectiveVividness(currentRouteRawVividness, spatialAbility);

    deriveProbabilities();
    deriveWeights();
    defineRouteChoiceMechanisms();
  }

  public double getLocallLandmarksThreshold() {
    if (agent.isLearner())
      return elementsSensitivity.calculatelocallLandmarksThreshold(effectiveVividness);
    else
      return RouteChoicePars.localLandmarkThresholdCommunity;
  }

  /**
   * Compute probabilities for all heuristics given effective vividness.
   * 
   * Design: - Distance vs Angular minimisation → complementary (sigmoid split). - Region
   * segmentation → independent, increases with vividness. - Distant landmarks → independent,
   * increases when vividness is low. - Barrier sub-goals vs Local landmarks → mutually exclusive,
   * normalised.
   */
  private void deriveProbabilities() {
    // clamp vividness

    // --- Minimisation heuristics (complementary) ---
    // Distance Minimisation (Shortest Path) – sigmoid rising with vividness
    probabilityDistanceMinimisation =
        WEIGHT_DISTANCE_NOVICE + (WEIGHT_DISTANCE_EXPERT - WEIGHT_DISTANCE_NOVICE) * (1.0
            / (1.0 + Math.exp(-SIGMOID_STEEPNESS * (effectiveVividness - VIVIDNESS_THRESHOLD))));

    // Angular Minimisation is the complement
    probabilityAngularMinimisation = 1.0 - probabilityDistanceMinimisation;

    // --- Independent structure activations (do NOT normalise) ---
    // Region segmentation (experts) – increases with vividness
    probabilityUsingRegions = Math.pow(effectiveVividness, EXPONENT_REGIONS);

    // Distant landmarks (novices) – increases when vividness is low
    probabilityDistantLandmarks = Math.pow(1.0 - effectiveVividness, EXPONENT_GLOBAL_LANDMARKS);

    deriveCompetingSubGoalProbabilities();
  }

  /**
   * Compute probabilities for competing sub-goal strategies: - Barrier sub-goals (novices): follow
   * rivers/walls as wayfinding aids. - Local landmarks (experts): recognise and use salient
   * junctions/statues/plazas as anchors.
   *
   * These two strategies are treated as competing and are normalised so that:
   * probabilityBarrierSubGoals + probabilityLocalLandmarks = 1.0
   *
   * Mathematical form: rawBarrier = (1 - v)^EXPONENT_BARRIERS rawLocal = v^EXPONENT_LOCAL_LANDMARKS
   *
   * probabilityBarrierSubGoals = rawBarrier / (rawBarrier + rawLocal) probabilityLocalLandmarks =
   * rawLocal / (rawBarrier + rawLocal)
   *
   * Where: v ∈ [0,1] is effective vividness (low = novice, high = expert).
   *
   * Curve behaviour (examples with EXPONENT_BARRIERS=1.0, EXPONENT_LOCAL_LANDMARKS=1.5):
   * ---------------------------------------------------------------- v=0.0 → Barrier ≈ 1.0, Local ≈
   * 0.0 (novice: pure barrier reliance) v=0.5 → Barrier ≈ 0.6, Local ≈ 0.4 (transition phase) v=1.0
   * → Barrier ≈ 0.0, Local ≈ 1.0 (expert: pure local landmarks)
   *
   * Visual intuition:
   *
   * Barrier Sub-Goals (novices ↑) ██████████████................. → vividness Local Landmarks
   * (experts ↑) ...............████████████████ → vividness
   */
  private void deriveCompetingSubGoalProbabilities() {
    // Clamp vividness to [0,1]

    // --- Raw scores before normalisation ---
    double rawBarrier = Math.pow(1.0 - effectiveVividness, EXPONENT_BARRIERS);
    double rawLocal = Math.pow(effectiveVividness, EXPONENT_LOCAL_LANDMARKS);

    // --- Normalise so they sum to 1 ---
    double sum = rawBarrier + rawLocal;
    if (sum > 0.0) {
      probabilityBarrierSubGoals = rawBarrier / sum;
    } else {
      // Degenerate case (shouldn’t happen if v ∈ [0,1])
      probabilityBarrierSubGoals = 0.5;
    }
  }

  // === Landmark weights (influence on routing) ===
  // Exponents shape how weights move with vividness.
  // Global landmark weight (distance-based).
  // EXP = 1.0 → linear fade with vividness.
  // [██████████...................] ← novices strongly pulled, experts weakly
  private static final double EXP_WEIGHT_GLOBAL_DISTANCE = 1.0;

  // Global landmark weight (angular-based).
  // EXP = 1.2 → slightly sharper fade.
  // [████████.....................] ← very high for novices, low for experts
  private static final double EXP_WEIGHT_GLOBAL_ANGULAR = 1.2;

  // // Local landmark weight (distance-based).
  // // EXP = 1.2 → convex growth (weak until ~0.5, then strong).
  // // [.............██████████......] ← novices ignore, experts adopt steadily
  // private static final double EXP_WEIGHT_LOCAL_DISTANCE = 1.2;

  // // Local landmark weight (angular-based).
  // // EXP = 1.5 → even sharper convex growth.
  // // [..................█████████..] ← almost unused by novices, dominates for
  // experts
  // private static final double EXP_WEIGHT_LOCAL_ANGULAR = 1.5;

  // Public outputs you already had:
  private double globalLandmarknessWeightDistance =
      RouteChoicePars.globalLandmarknessWeightDistanceCommunity;
  private double globalLandmarknessWeightAngular =
      RouteChoicePars.globalLandmarknessWeightAngularCommunity;

  // private double weightLocalDistance;
  // private double weightLocalAngular;

  /**
   * Derives landmark thresholds (recognition) and weights (influence on routing) as smooth
   * functions of effective vividness ∈ [0,1].
   */
  public void deriveWeights() {

    // --- Weights (0.1), shaped by exponents ---
    globalLandmarknessWeightDistance =
        Math.pow(1.0 - effectiveVividness, EXP_WEIGHT_GLOBAL_DISTANCE);
    globalLandmarknessWeightAngular = Math.pow(1.0 - effectiveVividness, EXP_WEIGHT_GLOBAL_ANGULAR);
    // weightLocalDistance = Math.pow(effectiveVividness,
    // EXP_WEIGHT_LOCAL_DISTANCE);
    // weightLocalAngular = Math.pow(effectiveVividness, EXP_WEIGHT_LOCAL_ANGULAR);
  }

  public double getGlobalLandmarkWeight(boolean angular) {
    if (angular)
      return globalLandmarknessWeightAngular;
    return globalLandmarknessWeightDistance;
  }

  /**
   * Randomly assigns route choice parameters to the agent based on its derived vividness profile
   * (probabilities).
   * 
   * Uses the probabilities for heuristics/sub-goals/landmarks/regions that were pre-computed by
   * HeuristicMixer. Ensures consistency with novice ↔ expert gradient.
   */
  public void defineRouteChoiceMechanisms() {

    ap.reset(); // clear flags
    double r = random.nextDouble();

    // --- Global Minimisation heuristics (rare, no usage of Urban Elements)
    if (probabilityDistanceMinimisation > 0.90 || probabilityAngularMinimisation > 0.90) {
      // One heuristic dominates strongly → force assignment
      ap.minimisingDistance = probabilityDistanceMinimisation > probabilityAngularMinimisation;
      ap.minimisingAngular = !ap.minimisingDistance;
      return;
    }

    // Otherwise set them as local Minimisation heuristics
    ap.localHeuristicDistance = r < probabilityDistanceMinimisation;
    ap.localHeuristicAngular = !ap.localHeuristicDistance;

    // --- Barrier sub-goals vs Local landmarks (mutually exclusive) ---
    double rBL = random.nextDouble();
    if (rBL < probabilityBarrierSubGoals) {
      ap.barrierBasedNavigation = true;
      ap.usingLocalLandmarks = false;
    } else {
      ap.usingLocalLandmarks = true;
      ap.barrierBasedNavigation = false;
    }

    // --- Distant/global landmarks (orientation anchors) ---
    ap.usingDistantLandmarks = random.nextDouble() < probabilityDistantLandmarks;

    // --- Region-based segmentation (independent switch) ---
    ap.regionBasedNavigation = random.nextDouble() < probabilityUsingRegions;
  }
}
