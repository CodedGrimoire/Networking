public class Main {
    public static void main(String[] args) {
        try {
            Network network = new Network();
            network.readTopology("topology.txt");
            network.startSimulation();

            // Let simulation run for 2 minutes then stop
            Thread.sleep(120_000);
            network.printLog();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
