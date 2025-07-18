import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Network {
    public Map<String, Router> routers = new HashMap<>();
    public int messageCount = 0;
    public List<String> log = new ArrayList<>();

    private int convergenceCounter = 0;
    private int lastMessageCount = 0;
    private int stableRounds = 0;
    private final int STABLE_THRESHOLD = 3;
    private boolean simulationStopped = false;
    private ScheduledExecutorService scheduler;

    public void readTopology(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(" ");
            String r1 = parts[0], r2 = parts[1];
            int cost = Integer.parseInt(parts[2]);

            routers.putIfAbsent(r1, new Router(r1));
            routers.putIfAbsent(r2, new Router(r2));

            routers.get(r1).neighbors.put(r2, cost);
            routers.get(r2).neighbors.put(r1, cost);

            logChange("Link: " + r1 + " <-> " + r2 + " = " + cost);
        }
        reader.close();

        Set<String> allRouters = routers.keySet();
        for (Router router : routers.values()) {
            router.initializeRoutingTable(allRouters);
        }
    }

    public void startSimulation() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::sendDistanceVectors, 0, 5, TimeUnit.SECONDS);
    }

    public void sendDistanceVectors() {
        if (simulationStopped) return;

        Set<String> updatedThisRound = new HashSet<>();

        for (Router sender : routers.values()) {
            for (String neighborId : sender.neighbors.keySet()) {
                Router neighbor = routers.get(neighborId);
                Map<String, Integer> vector = sender.getDistanceVectorFor(neighborId);
                boolean changed = neighbor.receiveVector(sender.id, vector);
                messageCount++;
                logChange(sender.id + " sent vector to " + neighborId);

                if (changed) {
                    updatedThisRound.add(neighbor.id);
                    logChange("Routing table updated at " + neighbor.id);
                }
            }
        }

        int now = (int) (System.currentTimeMillis() / 1000);
        for (String routerId : updatedThisRound) {
            routers.get(routerId).printRoutingTable(now);
        }

        if (updatedThisRound.isEmpty()) {
            stableRounds++;
            logChange("Stable round " + stableRounds);
        } else {
            stableRounds = 0;
        }

        if (stableRounds >= STABLE_THRESHOLD && !simulationStopped) {
            logChange("✅ Network has converged. Stopping simulation.");
            simulationStopped = true;
            scheduler.shutdown();
            printLog();
        }

        if (updatedThisRound.isEmpty() && messageCount != lastMessageCount) {
            convergenceCounter++;
            lastMessageCount = messageCount;
            logChange("Convergence iteration " + convergenceCounter);
        }
    }

    public void logChange(String msg) {
        log.add("[LOG] " + msg);
    }

    public void printLog() {
        System.out.println("---- LOG ----");
        for (String entry : log) {
            System.out.println(entry);
        }

        System.out.println("Total messages exchanged: " + messageCount);
        System.out.println("Total convergence iterations after last cost change: " + convergenceCounter);

        System.out.println("\nAll-Pair Shortest Paths:");
        for (Router router : routers.values()) {
            router.printFinalPaths();
        }
    }

    public boolean isStopped() {
        return simulationStopped;
    }
}
