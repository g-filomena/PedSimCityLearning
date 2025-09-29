package pedsim.routeMemorability;

import org.locationtech.jts.geom.Polygon;
import pedsim.engine.PedSimCity;
import sim.routing.Route;

public class MemoryTrace {

  Polygon visibilitySpace;
  double weight;
  long lastUpdated;
  Route route;

  public MemoryTrace(Polygon visibilitySpace, Route route, double weight, PedSimCity state) {

    this.visibilitySpace = visibilitySpace;
    this.weight = weight;
    this.lastUpdated = state.schedule.getSteps(); // or however you track steps
    this.route = route;
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }
}
