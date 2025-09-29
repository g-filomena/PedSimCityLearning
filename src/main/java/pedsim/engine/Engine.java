package pedsim.engine;

import java.util.logging.Logger;
import pedsim.parameters.Pars;
import pedsim.parameters.TimePars;
import pedsim.utilities.LoggerUtil;
import sim.util.geo.Utilities;

public class Engine {

  PedSimCity state;
  private double kmCurrentDay;
  AgentReleaseManager currentDayReleaseManager;
  private int currentDay;

  private static final Logger logger = LoggerUtil.getLogger();

  public Engine() {

  }

  /**
   * Executes the simulation job by controlling the flow of the simulation steps. It manages the
   * progression of the days and agent release.
   *
   * @param job The job ID for the simulation.
   * @throws Exception if an error occurs during the execution.
   */
  public void executeJob(int job) throws Exception {

    currentDay = 0;
    state = new PedSimCity(System.currentTimeMillis(), job);
    state.start();
    handleNewDay();

    double nextAgentRelease = 1.0;
    while (continueSimulation()) {
      double steps = state.schedule.getSteps();

      if (isNextDay(steps, currentDay)) {
        state.flowHandler.updateCognitiveMapsData(currentDay + 1, null);
        state.flowHandler.exportFlowsData(currentDay + 1);
        state.flowHandler.exportCognitiveMapsData(currentDay + 1);
        currentDay++;
        if (currentDay % 6 == 0) {
          handleEndWeek(state);
        } else {
          handleNewDay();
        }
      }

      if (nextAgentRelease == steps) {
        currentDayReleaseManager.releaseAgents(steps);
        nextAgentRelease += TimePars.releaseAgentsEverySteps;
      }
    }
    state.flowHandler.updateCognitiveMapsData(currentDay + 1, null);
    state.flowHandler.exportFlowsData(currentDay + 1);
    state.flowHandler.exportCognitiveMapsData(currentDay + 1);
    state.finish();
  }

  /**
   * Checks whether the simulation should continue based on the current state and steps.
   *
   * @return true if the simulation should continue, false otherwise.
   */
  private boolean continueSimulation() {
    return state.schedule.step(state)
        && (state.schedule.getSteps() <= TimePars.simulationDurationInSteps);
  }

  /**
   * Determines whether the simulation has moved to the next day based on the steps and current day.
   *
   * @param steps The current number of steps in the simulation.
   * @param currentDay The current day in the simulation.
   * @return true if the simulation is at the next day, false otherwise.
   */
  private boolean isNextDay(double steps, int currentDay) {
    return getDays(steps) > currentDay && (currentDay + 1 < TimePars.numberOfDays);
  }

  /**
   * Converts total steps into days based on the step duration and the total time.
   *
   * @param totalSteps The total number of steps.
   * @return The total number of days.
   */
  public static long getDays(double totalSteps) {

    long totalMinutes = (long) (totalSteps * (TimePars.STEP_DURATION / 60)); // Convert steps to
                                                                             // minutes based on
                                                                             // // the stepTimeUnit
    return totalMinutes / (24 * 60); // Calculate days
  }

  /**
   * Handles the start of a new day by calculating the km for the day and initialising the release
   * manager.
   */
  private void handleNewDay() {
    kmCurrentDay = calculateMetersCurrentDay();
    logger.info("---------- Beginning day Nr " + (currentDay + 1));
    currentDayReleaseManager = new AgentReleaseManager(state, kmCurrentDay);
  }

  /**
   * Calculates the kilometres for the current day based on predefined parameters.
   *
   * @return The total kilometres for the current day.
   */
  private double calculateMetersCurrentDay() {
    return Pars.metersPerDay * Utilities.fromDistribution(1.0, 0.10, null);
  }

  private void handleEndWeek(PedSimCity state) {

    // for (Agent agent : state.agentsList) {
    // if (agent.isLearner()) {
    // agent.learning.memoryDecay();
    // }
    // }
  }
}
