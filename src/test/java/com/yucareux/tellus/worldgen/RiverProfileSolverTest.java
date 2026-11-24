package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RiverProfileSolverTest {
   @Test
   void boundedTraversalMatchesIndependentShortestPathsOnBranchingNetworks() {
      Random random = new Random(78143L);
      for (int trial = 0; trial < 12; trial++) {
         RiverGraph.Builder builder = new RiverGraph.Builder(1);
         for (int line = 0; line < 40; line++) {
            double[] xs = new double[5], zs = new double[5];
            for (int point = 0; point < xs.length; point++) {
               xs[point] = random.nextInt(12) + 0.5;
               zs[point] = random.nextInt(12) + 0.5;
            }
            builder.addLine(xs, zs, line, 1.0, -100.0, -100.0, 100.0, 100.0);
         }
         RiverGraph graph = builder.build();
         RiverProfileSolver.Ball ball = new RiverProfileSolver.Ball(graph.count);
         for (int start = 0; start < graph.count; start += 3) {
            for (int mode = 1; mode <= 3; mode++) {
               boolean upstream = (mode & 1) != 0;
               boolean downstream = (mode & 2) != 0;
               double[] distances = referenceDistances(graph, start, upstream, downstream);
               for (double radius : new double[]{0.0, 2.5, 6.0, 12.5, 30.0}) {
                  boolean[] expected = new boolean[graph.count];
                  for (int c = 0; c < graph.count; c++) expected[c] = distances[c] <= radius;
                  boolean[] actual = new boolean[graph.count];
                  int count = ball.collect(graph, start, radius, upstream, downstream);
                  for (int i = 0; i < count; i++) {
                     int cell = ball.cells[i];
                     assertFalse(actual[cell], "duplicate visited cell");
                     actual[cell] = true;
                  }
                  assertArrayEquals(expected, actual, "trial " + trial + ", start " + start + ", mode " + mode + ", radius " + radius);
               }
            }
         }
      }
   }

   @Test
   void fixedRiverLevelsRemainUnchanged() {
      RiverGraph.Builder builder = new RiverGraph.Builder(1);
      builder.addLine(new double[]{0.5, 8.5}, new double[]{0.5, 0.5}, 1L, 1.0, -100.0, -100.0, 100.0, 100.0);
      RiverGraph graph = builder.build();
      double[] ground = new double[graph.count], fixed = new double[graph.count];
      Arrays.fill(ground, 80.0);
      Arrays.fill(fixed, Double.NaN);
      int outlet = graph.cell(8, 0);
      fixed[outlet] = 60.0;
      double[] solved = RiverProfileSolver.solve(graph, ground, fixed, new RiverProfileSolver.Parameters(2.0, 3.0, 4.0));
      assertEquals(60.0, solved[outlet], 0.0);
   }

   private record Node(int cell, double distance) {}

   private static double[] referenceDistances(RiverGraph graph, int start, boolean upstream, boolean downstream) {
      double[] distance = new double[graph.count];
      Arrays.fill(distance, Double.POSITIVE_INFINITY);
      PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(Node::distance));
      distance[start] = 0.0;
      queue.add(new Node(start, 0.0));
      while (!queue.isEmpty()) {
         Node node = queue.remove();
         int cell = node.cell();
         if (node.distance() != distance[cell]) continue;
         if (upstream) for (int k = graph.upStart[cell]; k < graph.upStart[cell + 1]; k++) {
            relax(graph, cell, graph.up[k], distance, queue);
         }
         if (downstream) for (int k = graph.downStart[cell]; k < graph.downStart[cell + 1]; k++) {
            relax(graph, cell, graph.down[k], distance, queue);
         }
      }
      return distance;
   }

   private static void relax(RiverGraph graph, int from, int to, double[] distance, PriorityQueue<Node> queue) {
      double candidate = distance[from] + graph.linkLength(from, to);
      if (candidate < distance[to]) {
         distance[to] = candidate;
         queue.add(new Node(to, candidate));
      }
   }
}
