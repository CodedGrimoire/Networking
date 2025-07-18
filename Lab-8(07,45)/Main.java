public class Main {
    public static void main(String[] args) {
        try {
            Network network = new Network();
            network.readTopology("topology.txt");
            network.startSimulation();

            while (!network.isStopped()) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
