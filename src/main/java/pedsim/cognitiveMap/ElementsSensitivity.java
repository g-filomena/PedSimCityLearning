package pedsim.cognitiveMap;

import java.util.Random;
import pedsim.parameters.RouteChoicePars;

/**
 * HeuristicMixer: compute route-choice weights based on vividness and memory ability.
 */
public final class ElementsSensitivity {

  final Random random = new Random();

  // === Landmark recognition: anchors and shaping ===
  private static final double MIN_LANDMARK_SCORE = 0.25;

  // Threshold anchors (novice → expert)
  private static final double GLOBAL_LM_THRESHOLD_NOVICE = 0.90;
  private static final double GLOBAL_LM_THRESHOLD_EXPERT = 0.25;
  private static final double LOCAL_LM_THRESHOLD_NOVICE = 0.85;
  private static final double LOCAL_LM_THRESHOLD_EXPERT = 0.25;

  // Threshold curve exponents (how the drop happens as vividness ↑)
  // Global landmarks (cathedral tower, mountain).
  // EXP = 1.0 → linear decrease as vividness grows.
  // [█████████████.................] ← common for novices, rare for experts
  private static final double EXP_THRESHOLD_GLOBAL = 1.0;

  // Local landmarks (corner shop, statue, plaza).
  // EXP = 0.6 → slower decrease at first, then sharper near expert.
  // [..............█████████████...] ← rarely novices, steadily adopted by experts
  private static final double EXP_THRESHOLD_LOCAL = 0.6;

  // Public outputs you already had:
  double globalLandmarkThreshold = RouteChoicePars.globalLandmarkThresholdCommunity;
  double localLandmarkThreshold = RouteChoicePars.localLandmarkThresholdCommunity;

  /**
   * Derives landmark thresholds (recognition) and weights (influence on routing) as smooth
   * functions of effective vividness ∈ [0,1].
   */
  public void globalLandmarksThreshold(double vividness) {

    // --- Thresholds (interpolate novice→expert with exponent shaping) ---
    globalLandmarkThreshold = clampMin(MIN_LANDMARK_SCORE,
        GLOBAL_LM_THRESHOLD_NOVICE + (GLOBAL_LM_THRESHOLD_EXPERT - GLOBAL_LM_THRESHOLD_NOVICE)
            * Math.pow(vividness, EXP_THRESHOLD_GLOBAL));
  }

  /**
   * Derives landmark thresholds (recognition) and weights (influence on routing) as smooth
   * functions of effective vividness ∈ [0,1].
   * 
   * @return
   */
  public double calculatelocallLandmarksThreshold(double vividness) {

    return clampMin(MIN_LANDMARK_SCORE,
        LOCAL_LM_THRESHOLD_NOVICE + (LOCAL_LM_THRESHOLD_EXPERT - LOCAL_LM_THRESHOLD_NOVICE)
            * Math.pow(vividness, EXP_THRESHOLD_LOCAL));
  }

  /**
   * Derives the wayfinding easiness threshold for an agent as a function of effective vividness.
   *
   * - Novices (low vividness) → require very easy routes, high threshold (~0.7–0.8). - Experts
   * (high vividness) → can tolerate harder routes, low threshold (~0.3).
   *
   * @param effectiveVividness smoothed vividness [0,1] for this agent
   * @return threshold in [0,1], above which the route is considered “easy enough”
   */
  public static double deriveWayfindingEasinessThreshold(double vividness) {
    double v = Math.max(0.0, Math.min(1.0, vividness));
    double minThreshold = 0.3; // experts
    double maxThreshold = 0.95; // novices
    return maxThreshold - (maxThreshold - minThreshold) * v;
  }

  // Small helper to enforce minimum recognisability
  private static double clampMin(double min, double x) {
    return x < min ? min : x;
  }
}


