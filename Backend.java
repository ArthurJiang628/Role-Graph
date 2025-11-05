import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backend implementation that loads a (weighted) graph from a DOT file and
 * provides shortest-path and reachability operations.
 * NOTE: This class must be in the default package (no package statement).
 *
 * Design notes:
 * - This implementation maintains its own adjacency list internally so it can
 *   run Dijkstra without depending on any specific GraphADT API beyond the
 *   assignment's BackendInterface.
 * - DOT parsing supports directed edges "A -> B" and undirected edges "A -- B".
 * - Edge weight can be specified using either [weight=1.23] or [label=1.23].
 *   If no weight is present, a default weight of 1.0 is used.
 */
public class Backend implements BackendInterface {

    /** Adjacency list: node -> list of edges (to, weight) */
    private final Map<String, List<Edge>> adj = new HashMap<>();

    /** Edge record used in adjacency lists */
    private static class Edge {
        final String to;
        final double w;
        Edge(String to, double w) { this.to = to; this.w = w; }
    }

    /**
     * Constructor (no dependencies required).
     * If your graders expect a constructor with GraphADT, you can add an
     * overloaded one later without changing this default constructor.
     */
    public Backend() {
        // nothing to initialize beyond 'adj'
    }

    // ---------------------------------------------------------------------
    // 1) loadGraphData
    // ---------------------------------------------------------------------
    /**
     * Load graph data from a DOT file.
     * Pre: filename refers to a readable DOT file.
     * Post: internal graph is cleared and then populated from file contents.
     */
    @Override
    public void loadGraphData(String filename) throws IOException {
        // Clear existing graph
        adj.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            // Patterns to match edges and optional attributes
            // Supports:   A -> B [weight=1.5];    or   A -- B [label=2.0];
            Pattern edgePat = Pattern.compile("\\s*([A-Za-z0-9_./-]+)\\s*(->|--)\\s*([A-Za-z0-9_./-]+)\\s*(\\[[^\\]]*\\])?\\s*;?.*");
            Pattern nodePat = Pattern.compile("\\s*([A-Za-z0-9_./-]+)\\s*(\\[[^\\]]*\\])?\\s*;\\s*");
            Pattern weightPat = Pattern.compile(".*(?:weight|label)\\s*=\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"?.*");

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("//") || line.startsWith("#")) continue;
                if (line.startsWith("graph") || line.startsWith("digraph") || line.startsWith("{") || line.startsWith("}"))
                    continue;

                Matcher em = edgePat.matcher(line);
                if (em.matches()) {
                    String u = em.group(1);
                    String op = em.group(2); // -> or --
                    String v = em.group(3);
                    String attr = em.group(4);

                    double w = 1.0;
                    if (attr != null) {
                        Matcher wm = weightPat.matcher(attr);
                        if (wm.matches()) {
                            w = Double.parseDouble(wm.group(1));
                        }
                    }

                    // ensure nodes exist
                    adj.computeIfAbsent(u, k -> new ArrayList<>());
                    adj.computeIfAbsent(v, k -> new ArrayList<>());

                    // add edge(s)
                    adj.get(u).add(new Edge(v, w));
                    if (op.equals("--")) { // undirected => add reverse
                        adj.get(v).add(new Edge(u, w));
                    }
                    continue;
                }

                // standalone node lines like:   A;   or   A [label="x"];
                Matcher nm = nodePat.matcher(line);
                if (nm.matches()) {
                    String node = nm.group(1);
                    adj.computeIfAbsent(node, k -> new ArrayList<>());
                }
                // Other lines are ignored (subgraphs, attributes, etc.)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 2) getListOfAllLocations
    // ---------------------------------------------------------------------
    /**
     * Return all node names in lexicographic order.
     */
    @Override
    public List<String> getListOfAllLocations() {
        List<String> list = new ArrayList<>(adj.keySet());
        Collections.sort(list);
        return list;
    }

    // ---------------------------------------------------------------------
    // 3) findLocationsOnShortestPath
    // ---------------------------------------------------------------------
    /**
     * Return the sequence of nodes along the shortest path from start to end.
     * If either node is missing or no path exists, return an empty list.
     */
    @Override
    public List<String> findLocationsOnShortestPath(String startLocation, String endLocation) {
        if (!adj.containsKey(startLocation) || !adj.containsKey(endLocation)) return Collections.emptyList();
        DijkstraResult dj = dijkstra(startLocation);
        if (!dj.dist.containsKey(endLocation) || Double.isInfinite(dj.dist.get(endLocation))) return Collections.emptyList();
        return reconstructPath(dj.prev, startLocation, endLocation);
    }

    // ---------------------------------------------------------------------
    // 4) findTimesOnShortestPath
    // ---------------------------------------------------------------------
    /**
     * Return edge-by-edge weights (times) along the shortest path from start to end.
     * If no path, return empty list.
     */
    @Override
    public List<Double> findTimesOnShortestPath(String startLocation, String endLocation) {
        List<String> path = findLocationsOnShortestPath(startLocation, endLocation);
        if (path.isEmpty()) return Collections.emptyList();
        List<Double> times = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            String u = path.get(i), v = path.get(i + 1);
            times.add(weightOf(u, v));
        }
        return times;
    }

    // ---------------------------------------------------------------------
    // 5) getReachableFromWithin
    // ---------------------------------------------------------------------
    /**
     * Return a list of nodes reachable from start within <= travelTime (seconds).
     * Throws NoSuchElementException if start does not exist.
     * The returned list is sorted lexicographically (excluding the start itself).
     */
    @Override
    public List<String> getReachableFromWithin(String startLocation, double travelTime) throws NoSuchElementException {
        if (!adj.containsKey(startLocation)) {
            throw new NoSuchElementException("Start location does not exist: " + startLocation);
        }
        DijkstraResult dj = dijkstra(startLocation);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : dj.dist.entrySet()) {
            String node = e.getKey();
            double d = e.getValue();
            if (!node.equals(startLocation) && d <= travelTime) {
                out.add(node);
            }
        }
        Collections.sort(out);
        return out;
    }

    // ==========================
    // Dijkstra helper machinery
    // ==========================

    /** Dijkstra result: distance and predecessor maps */
    private static class DijkstraResult {
        final Map<String, Double> dist = new HashMap<>();
        final Map<String, String> prev = new HashMap<>();
    }

    /**
     * Run Dijkstra from a given source on the internal adjacency list.
     * Returns maps of distances and predecessors.
     */
    private DijkstraResult dijkstra(String src) {
        DijkstraResult r = new DijkstraResult();
        // init distances
        for (String v : adj.keySet()) r.dist.put(v, Double.POSITIVE_INFINITY);
        r.dist.put(src, 0.0);

        // min-heap on (distance, node)
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(r.dist::get));
        pq.add(src);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            double du = r.dist.get(u);
            // relax edges
            for (Edge e : adj.getOrDefault(u, Collections.emptyList())) {
                double cand = du + e.w;
                Double old = r.dist.get(e.to);
                if (cand < old) {
                    r.dist.put(e.to, cand);
                    r.prev.put(e.to, u);
                    // update pq (remove+add pattern is fine given small inputs)
                    pq.remove(e.to);
                    pq.add(e.to);
                }
            }
        }
        return r;
    }

    /**
     * Reconstruct path [src ... dst] using predecessor map.
     * Returns an empty list if dst is unreachable.
     */
    private List<String> reconstructPath(Map<String, String> prev, String src, String dst) {
        LinkedList<String> path = new LinkedList<>();
        String cur = dst;
        while (cur != null) {
            path.addFirst(cur);
            if (cur.equals(src)) break;
            cur = prev.get(cur);
        }
        if (!path.isEmpty() && path.getFirst().equals(src)) return path;
        return Collections.emptyList();
    }

    /**
     * Get the weight of the edge u->v (or +INF if missing).
     */
    private double weightOf(String u, String v) {
        for (Edge e : adj.getOrDefault(u, Collections.emptyList())) {
            if (e.to.equals(v)) return e.w;
        }
        return Double.POSITIVE_INFINITY;
    }
}
