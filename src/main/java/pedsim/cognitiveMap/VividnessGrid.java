package pedsim.cognitiveMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import pedsim.parameters.LearningPars;
import sim.graph.NodeGraph;

public class VividnessGrid {
  private int width;
  private int height;
  private double cellSize;
  private double originX;
  private double originY;
  private Envelope envelope;
  float[] density; // flattened [y*width + x]
  private final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  public VividnessGrid(Envelope envelope, double cellSize) {
    this.cellSize = cellSize;
    this.originX = envelope.getMinX();
    this.originY = envelope.getMinY();
    this.width = (int) Math.ceil((envelope.getMaxX() - originX) / cellSize);
    this.height = (int) Math.ceil((envelope.getMaxY() - originY) / cellSize);
    this.envelope = new Envelope(envelope);
    this.density = new float[width * height];
  }

  public void addVisibilitySpace(Polygon polygon, double weight) {
    Envelope polyEnv = polygon.getEnvelopeInternal();
    if (!envelope.contains(polyEnv)) {
      expandToInclude(polyEnv);
    }

    PreparedPolygon prepPoly = (PreparedPolygon) PreparedGeometryFactory.prepare(polygon);

    int minX = Math.max(0, (int) ((polyEnv.getMinX() - originX) / cellSize));
    int maxX = Math.min(width - 1, (int) ((polyEnv.getMaxX() - originX) / cellSize));
    int minY = Math.max(0, (int) ((polyEnv.getMinY() - originY) / cellSize));
    int maxY = Math.min(height - 1, (int) ((polyEnv.getMaxY() - originY) / cellSize));

    for (int y = minY; y <= maxY; y++) {
      double worldY = originY + y * cellSize;
      for (int x = minX; x <= maxX; x++) {
        double worldX = originX + x * cellSize;
        if (prepPoly.contains(polygon.getFactory()
            .createPoint(new org.locationtech.jts.geom.Coordinate(worldX, worldY)))) {
          density[y * width + x] += (float) weight;
        }
      }
    }
  }

  private void expandToInclude(Envelope newEnv) {
    Envelope expanded = new Envelope(envelope);
    expanded.expandToInclude(newEnv);

    int newWidth = (int) Math.ceil((expanded.getMaxX() - expanded.getMinX()) / cellSize);
    int newHeight = (int) Math.ceil((expanded.getMaxY() - expanded.getMinY()) / cellSize);
    float[] newDensity = new float[newWidth * newHeight];

    // copy old density into new grid
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int oldIndex = y * width + x;
        double worldX = originX + x * cellSize;
        double worldY = originY + y * cellSize;
        int newX = (int) ((worldX - expanded.getMinX()) / cellSize);
        int newY = (int) ((worldY - expanded.getMinY()) / cellSize);
        int newIndex = newY * newWidth + newX;
        newDensity[newIndex] = density[oldIndex];
      }
    }

    // update fields
    this.width = newWidth;
    this.height = newHeight;
    this.originX = expanded.getMinX();
    this.originY = expanded.getMinY();
    this.envelope = expanded;
    this.density = newDensity;
  }

  public float getValueAt(double worldX, double worldY) {
    int gx = (int) ((worldX - originX) / cellSize);
    int gy = (int) ((worldY - originY) / cellSize);
    if (gx < 0 || gy < 0 || gx >= width || gy >= height)
      return 0f;
    return density[gy * width + gx];
  }

  public float[] getDensity() {
    return density;
  }

  public double getCellSize() {
    return cellSize;
  }

  /**
   * Updates the cognitive collage by converting grid cells with density values above a given
   * threshold into contiguous polygon regions.
   * 
   * The method works in three stages: - Iterates over the grid cells and collects all
   * cell-rectangles whose density is greater than or equal to {@code minThreshold}. - Unions those
   * rectangles into contiguous regions using JTS
   * {@link org.locationtech.jts.operation.union.UnaryUnionOp}. - Returns the resulting regions as a
   * list of {@link Polygon} objects.
   *
   * @param minThreshold the minimum density value for a grid cell to be considered active and
   *        included in the collage
   * @return a list of contiguous polygon regions representing areas with density above the
   *         threshold; may be empty if no cells meet the threshold
   */
  public List<Polygon> updateCollage(double minThreshold) {

    Envelope env = this.envelope;
    double cellSize = this.getCellSize();
    int width = (int) Math.round((env.getMaxX() - env.getMinX()) / cellSize);
    int height = (int) Math.round((env.getMaxY() - env.getMinY()) / cellSize);
    double originX = env.getMinX();
    double originY = env.getMinY();

    float[] density = this.getDensity();
    List<Polygon> cellPolys = new ArrayList<>();

    // Step 1: collect all cell-rectangles above threshold
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int idx = y * width + x;
        if (density[idx] >= minThreshold) {
          double minX = originX + x * cellSize;
          double minY = originY + y * cellSize;
          double maxX = minX + cellSize;
          double maxY = minY + cellSize;

          Coordinate[] coords = new Coordinate[] {new Coordinate(minX, minY),
              new Coordinate(maxX, minY), new Coordinate(maxX, maxY), new Coordinate(minX, maxY),
              new Coordinate(minX, minY)};
          cellPolys.add(GEOMETRY_FACTORY.createPolygon(coords));
        }
      }
    }

    if (cellPolys.isEmpty())
      return Collections.emptyList();

    // Step 2: union them all into contiguous regions
    Geometry unioned = UnaryUnionOp.union(cellPolys);

    // Step 3: return as list of polygons
    List<Polygon> result = new ArrayList<>();
    if (unioned instanceof Polygon) {
      result.add((Polygon) unioned);
    } else if (unioned instanceof MultiPolygon) {
      MultiPolygon mp = (MultiPolygon) unioned;
      for (int i = 0; i < mp.getNumGeometries(); i++) {
        result.add((Polygon) mp.getGeometryN(i));
      }
    }
    return result;
  }

  // Convert world → grid indices
  public int toGridX(double worldX) {
    return (int) ((worldX - originX) / cellSize);
  }

  public int toGridY(double worldY) {
    return (int) ((worldY - originY) / cellSize);
  }

  public boolean isInside(int gx, int gy) {
    return gx >= 0 && gy >= 0 && gx < width && gy < height;
  }

  public float getDensityAtCell(int gx, int gy) {
    return density[gy * width + gx];
  }

  /**
   * Compute raw vividness score for planning between an origin and destination.
   *
   * Strategy: - Sample a buffer around origin node - Sample a buffer around destination node -
   * Sample along straight-line corridor between them - Average the values
   *
   * @param originNode origin node
   * @param destinationNode destination node
   * @param bufferRadius radius (in meters) to sample around O/D
   * @param corridorStep spacing (in meters) for samples along O–D line
   * @return mean vividness in [0,1], or 0 if no cells sampled
   */
  public double vividnessBetweenSpace(NodeGraph originNode, NodeGraph destinationNode) {
    List<Double> samples = new ArrayList<>();

    double originX = originNode.getCoordinate().x;
    double originY = originNode.getCoordinate().y;
    double destinationX = destinationNode.getCoordinate().x;
    double destinationY = destinationNode.getCoordinate().y;

    // sample around origin
    samples.addAll(sampleBuffer(originNode, LearningPars.RouteVividnessRadius));

    // sample around destination
    samples.addAll(sampleBuffer(destinationNode, LearningPars.RouteVividnessRadius));

    // sample along corridor (straight line)
    double dx = destinationX - originX;
    double dy = destinationY - originY;
    double dist = Math.hypot(dx, dy);
    int steps = (int) Math.max(1, dist / LearningPars.RouteVividnessRadius);

    for (int i = 0; i <= steps; i++) {
      double t = i / (double) steps;
      double x = originX + t * dx;
      double y = originY + t * dy;
      samples.addAll(sampleBuffer(x, y, LearningPars.RouteVividnessRadius));
    }

    // mean
    return samples.isEmpty() ? 0.0
        : samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  /**
   * Collects vividness values from all grid cells within a circular buffer around a given node.
   *
   * Procedure: - Convert the buffer radius (meters) into grid cell units. - Iterate over all grid
   * cells in the square bounding box of the circle. - For each candidate cell: compute its center
   * in world coordinates and test whether it lies within the given radius of the node. - If inside
   * and the density at that cell is > 0, include it in the sample list.
   *
   * @param node the center node around which to sample
   * @param radius buffer radius (in meters)
   * @return list of positive vividness values within the buffer; may be empty if no cells qualify
   */
  private List<Double> sampleBuffer(NodeGraph node, double radius) {

    double x = node.getCoordinate().x;
    double y = node.getCoordinate().y;
    return sampleBuffer(x, y, radius);
  }

  /** Helper: sample all cells within a circular buffer around a coordinate. */
  private List<Double> sampleBuffer(double x, double y, double radius) {
    List<Double> vals = new ArrayList<>();
    int rCells = (int) Math.ceil(radius / cellSize);

    int cx = toGridX(x);
    int cy = toGridY(y);

    for (int dx = -rCells; dx <= rCells; dx++) {
      for (int dy = -rCells; dy <= rCells; dy++) {
        int gx = cx + dx;
        int gy = cy + dy;
        if (isInside(gx, gy)) {
          double px = originX + gx * cellSize;
          double py = originY + gy * cellSize;
          if (Math.hypot(px - x, py - y) <= radius) {
            float v = getDensityAtCell(gx, gy);
            if (v > 0)
              vals.add((double) v);
          }
        }
      }
    }
    return vals;
  }



  // Vividness smoothing factor: higher = adapts faster to new vividness
  // === Vividness & Memory Integration ===

  // Vividness smoothing factor: how much new vividness overrides the past.
  // 0.2 → 20% new vividness, 80% inertia from last route.
  // Too high (≈1.0) → jittery agents; too low (≈0.05) → agents "stuck".
  private static final double VIVIDNESS_SMOOTHING_FACTOR = 0.2;
  // Weighting between situational vividness vs. stable memory ability.
  // EffectiveVividness = 0.7 * smoothedVividness + 0.3 * memoryAbility.
  // → vividness dominates, memory provides stability.
  private static final double VIVIDNESS_WEIGHT = 0.7;
  private static final double MEMORY_ABILITY_WEIGHT = 0.3;

  private double smoothedVividness;

  /**
   * Compute the effective vividness for route planning.
   *
   * Steps: 1. Compute raw vividness from the grid (O/D buffers + corridor). 2. Smooth vividness
   * over time to reduce abrupt fluctuations. 3. Combine smoothed vividness with agent's stable
   * memory ability.
   *
   * @param vividnessGrid the agent's vividness grid
   * @param originNode origin of the planned route
   * @param destinationNode destination of the planned route
   * @param bufferRadius buffer radius around origin/destination (meters)
   * @param corridorStep spacing of corridor samples (meters)
   * @param agent the agent (provides memory ability + smoothed vividness state)
   * @return effective vividness in [0,1]
   */
  public double computeEffectiveVividness(double spaceRawVividness, double spatialAbility) {

    // Step 2: update smoothed vividness across routes
    smoothedVividness = smoothVividness(spaceRawVividness);

    // Step 3: combine smoothed vividness with stable memory ability
    return Math.max(0.0, Math.min(1.0,
        VIVIDNESS_WEIGHT * smoothedVividness + MEMORY_ABILITY_WEIGHT * spatialAbility));
  }

  /**
   * Smooth vividness values across routes to avoid abrupt flips in strategy.
   *
   * This implements exponential smoothing:
   *
   * smoothedVividness_t = (1 - smoothingFactor) * smoothedVividness_{t-1} + smoothingFactor *
   * currentRouteVividness
   *
   * where smoothingFactor ∈ [0,1]: - small values (e.g. 0.1) → slow adaptation, long memory of past
   * vividness - large values (e.g. 0.8) → rapid adaptation, short memory
   *
   * @param previousRouteSmoothedVividness smoothed vividness value after the last route
   * @param spaceRawVividness vividness measured for the current route (0–1)
   * @return updated smoothed vividness value for the agent
   */
  private double smoothVividness(double spaceRawVividness) {
    // clamp input to [0,1] to ensure stability
    spaceRawVividness = Math.max(0.0, Math.min(1.0, spaceRawVividness));

    return (1.0 - VIVIDNESS_SMOOTHING_FACTOR) * smoothedVividness
        + VIVIDNESS_SMOOTHING_FACTOR * spaceRawVividness;
  }


}
