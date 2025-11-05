import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * BackendTests for the backend role.
 * Each test creates a tiny DOT graph on-the-fly to exercise the API.
 * NOTE: This class must be in the default package (no package statement).
 *
 * Graph used across tests (directed):
 *   A -> B [weight=10];
 *   A -> C [weight=3];
 *   C -> B [weight=4];
 *   B -> D [weight=2];
 *   C -> D [weight=12];
 *   D -> E [weight=1];
 *
 * Shortest A->D: A -> C -> B -> D    (3 + 4 + 2 = 9)
 * Times list for A->D: [3.0, 4.0, 2.0]
 * Reachable from A within 7: {B? no (best 7 -> actually A->C->B = 7), C(3), B(7)} -> both C and B
 */
public class BackendTests {

    /**
     * Utility: write DOT content to a temp file and return its path.
     */
    private static Path writeTempDot(String content) throws IOException {
        Path f = Files.createTempFile("graph", ".dot");
        Files.writeString(f, content);
        return f;
    }

    /**
     * roleTest1 — Smoke test for loadGraphData() and getListOfAllLocations().
     * Goal: Ensure loading does not throw and that nodes are discoverable.
     */
    @Test
    public void roleTest1() throws IOException {
        String dot = "digraph G {\n" +
                "  A -> B [weight=10];\n" +
                "  A -> C [weight=3];\n" +
                "  C -> B [weight=4];\n" +
                "  B -> D [weight=2];\n" +
                "  C -> D [weight=12];\n" +
                "  D -> E [weight=1];\n" +
                "}\n";
        Path p = writeTempDot(dot);

        Backend backend = new Backend();
        backend.loadGraphData(p.toString());

        List<String> nodes = backend.getListOfAllLocations();
        assertTrue(nodes.contains("A") && nodes.contains("E"),
                "Loaded nodes should include A and E");
        assertEquals(5, nodes.size(), "Expect exactly 5 nodes (A,B,C,D,E)");
    }

    /**
     * roleTest2 — Verify shortest-path node sequence A->D.
     * Goal: Exercise findLocationsOnShortestPath() and ensure path is correct.
     */
    @Test
    public void roleTest2() throws IOException {
        String dot = "digraph G {\n" +
                "  A -> B [weight=10];\n" +
                "  A -> C [weight=3];\n" +
                "  C -> B [weight=4];\n" +
                "  B -> D [weight=2];\n" +
                "  C -> D [weight=12];\n" +
                "  D -> E [weight=1];\n" +
                "}\n";
        Path p = writeTempDot(dot);

        Backend backend = new Backend();
        backend.loadGraphData(p.toString());

        List<String> path = backend.findLocationsOnShortestPath("A", "D");
        assertEquals(List.of("A", "C", "B", "D"), path,
                "Shortest path A->D should be A -> C -> B -> D");
    }

    /**
     * roleTest3 — Verify times list and reachability within a budget.
     * Goal: Exercise findTimesOnShortestPath() and getReachableFromWithin().
     */
    @Test
    public void roleTest3() throws IOException {
        String dot = "digraph G {\n" +
                "  A -> B [weight=10];\n" +
                "  A -> C [weight=3];\n" +
                "  C -> B [weight=4];\n" +
                "  B -> D [weight=2];\n" +
                "  C -> D [weight=12];\n" +
                "  D -> E [weight=1];\n" +
                "}\n";
        Path p = writeTempDot(dot);

        Backend backend = new Backend();
        backend.loadGraphData(p.toString());

        // times along shortest A->D should be [3, 4, 2]
        List<Double> times = backend.findTimesOnShortestPath("A", "D");
        assertEquals(List.of(3.0, 4.0, 2.0), times,
                "Edge weights on shortest A->D should be [3.0, 4.0, 2.0]");

        // reachable from A within 7 seconds: C (3), B (7)  [D is 9, E is 10]
        List<String> reach7 = backend.getReachableFromWithin("A", 7.0);
        assertEquals(List.of("B", "C"), reach7,
                "Within 7 from A should include B and C, sorted lexicographically");
    }
}
