package pedsim.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.javatuples.Pair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.planargraph.DirectedEdge;

import pedsim.agents.Agent;
import pedsim.communityCognitiveMap.Barrier;
import pedsim.communityCognitiveMap.Gateway;
import pedsim.communityCognitiveMap.Region;
import pedsim.parameters.Pars;
import pedsim.utilities.LoggerUtil;
import pedsim.utilities.StringEnum;
import sim.engine.SimState;
import sim.engine.Stoppable;
import sim.field.geo.VectorLayer;
import sim.graph.Building;
import sim.graph.EdgeGraph;
import sim.graph.Graph;
import sim.graph.NodeGraph;
import sim.util.geo.MasonGeometry;

/**
 * The PedSimCity class represents the main simulation environment.
 */
public class PedSimCity extends SimState {
	private static final long serialVersionUID = 1L;

	// Urban elements: graphs, buildings, etc.
	public static VectorLayer roads = new VectorLayer();
	public static VectorLayer buildings = new VectorLayer();
	public static VectorLayer barriers = new VectorLayer();
	public static VectorLayer junctions = new VectorLayer();
	public static VectorLayer sightLines = new VectorLayer();

	final public static Graph network = new Graph();
	final public static Graph dualNetwork = new Graph();
	public static Envelope MBR = null;

	// dual graph
	public static VectorLayer intersectionsDual = new VectorLayer();
	public static VectorLayer centroids = new VectorLayer();

	// supporting HashMaps, bags and Lists
	public static Map<Integer, Building> buildingsMap = new HashMap<>();
	public static Map<Integer, Region> regionsMap = new HashMap<>();
	public static Map<Integer, Barrier> barriersMap = new HashMap<>();
	public static Map<Integer, Gateway> gatewaysMap = new HashMap<>();
	public static Map<Integer, NodeGraph> nodesMap = new HashMap<>();
	public static Map<Integer, EdgeGraph> edgesMap = new HashMap<>();
	public static Map<Integer, NodeGraph> centroidsMap = new HashMap<>();
	public static Set<EdgeGraph> edges = new HashSet<>();

	// OD related variables
	public static List<Float> distances = new ArrayList<>();
	public static List<MasonGeometry> startingNodes = new ArrayList<>();

	public static Map<DirectedEdge, LengthIndexedLine> indexedEdgeCache = new ConcurrentHashMap<>();
	public static Map<Pair<Coordinate, Coordinate>, Polygon> visibilityPolygonsCache = new ConcurrentHashMap<>();
	// cached alternative routes for night movement
	public static Map<Pair<NodeGraph, NodeGraph>, List<DirectedEdge>> alternativeRoutes = new ConcurrentHashMap<>();

	// used only when loading OD sets
	public int currentJob;
	public FlowHandler<?> flowHandler; // Using a wildcard since we don't know the exact type

	public VectorLayer agents;
	public Set<Agent> agentsAtHome = ConcurrentHashMap.newKeySet();
	public Set<Agent> agentsWalking = ConcurrentHashMap.newKeySet();
	public Set<Agent> agentsList = ConcurrentHashMap.newKeySet();

	/**
	 * Constructs a new instance of the PedSimCity simulation environment.
	 *
	 * @param seed The random seed for the simulation.
	 * @param job  The current job number for multi-run simulations.
	 */
	public PedSimCity(long seed, int job) {
		super(seed);
		this.currentJob = job;
		this.flowHandler = new FlowHandler<>(job, this, StringEnum.Learner.values(), null);
		this.agents = new VectorLayer(); // create a new vector layer for each job
	}

	/**
	 * Initialises the simulation by defining the simulation mode, initialising edge
	 * volumes, and preparing the simulation environment. It then proceeds to
	 * populate the environment with agents and starts the agent movement.
	 */
	@Override
	public void start() {
		super.start();
		prepareEnvironment();
		populateEnvironment();
		startMovingAgents();
	}

	/**
	 * Prepares the environment for the simulation. This method sets up the minimum
	 * bounding rectangle (MBR) to encompass both the road and building layers and
	 * updates the MBR of the road layer accordingly.
	 */
	private void prepareEnvironment() {
		MBR = roads.getMBR();
		if (!buildings.getGeometries().isEmpty()) {
			MBR.expandToInclude(buildings.getMBR());
		}
		if (!barriers.getGeometries().isEmpty()) {
			MBR.expandToInclude(barriers.getMBR());
		}
		roads.setMBR(MBR);
	}

	/**
	 * Populates the simulation environment with agents and other entities based on
	 * the selected simulation parameters. This method uses the Populate class to
	 * generate the agent population.
	 */
	/**
	 * Populates the simulation environment with agents and other entities based on
	 * the selected simulation parameters. This method uses the Populate class to
	 * generate the agent population.
	 */
	private void populateEnvironment() {

		Populate populate = new Populate();
		populate.populate(this);
	}

	/**
	 * Starts moving agents in the simulation. This method schedules agents for
	 * repeated movement updates and sets up the spatial index for agents.
	 */
	private void startMovingAgents() {
		for (Agent agent : agentsList) {
			Stoppable stop = schedule.scheduleRepeating(agent);
			agent.setStoppable(stop);
			schedule.scheduleRepeating(agents.scheduleSpatialIndexUpdater(), Integer.MAX_VALUE, 1.0);
		}
		agents.setMBR(MBR);
	}

	// ---------------------------------------------------
	// Shared simulation core (used by GUI + headless)
	// ---------------------------------------------------
	/**
	 * Completes the simulation by saving results and performing cleanup operations.
	 */
	@Override
	public void finish() {
		super.finish();
	}

	/**
	 * Runs the full simulation once all parameters are set.
	 */
	public static void runSimulation() throws Exception {
		// Parse args with ParameterManager

		Pars.setSimulationParameters();
		Import importer = new Import();
		importer.importFiles();
		LoggerUtil.getLogger().info("Running ABM with " + Pars.numAgents + " agents for "
				+ StringEnum.Learner.values().length + " scenarios.");

		Environment.prepare();
		LoggerUtil.getLogger().info("Environment prepared. Starting simulation...");

		Engine engine = new Engine();
		for (int jobNr = 0; jobNr < Pars.jobs; jobNr++) {
			LoggerUtil.getLogger().info("Executing Job: " + jobNr);
			engine.executeJob(jobNr);
		}
		LoggerUtil.getLogger().info("Simulation finished.");
	}

	/**
	 * The main function that allows the simulation to be run in stand-alone,
	 * non-GUI mode.
	 *
	 * @param args Command-line arguments.
	 * @throws Exception If an error occurs during simulation execution.
	 */
	public static void main(String[] args) throws Exception {
		// 1. Parse + apply all parameters
		runSimulation(); // local run, with whatever local defaults/logging you need
	}
}
