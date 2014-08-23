/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.polyworld.voronoi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.terasology.math.delaunay.Voronoi;
import org.terasology.math.geom.LineSegment;
import org.terasology.math.geom.Rect2d;
import org.terasology.math.geom.Vector2d;

/**
 * VoronoiGraph.java
 *
 * @author Connor
 */
public class VoronoiGraph {

    private final List<Edge> edges = new ArrayList<>();
    private final List<Corner> corners = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    private final Rect2d bounds;

    public VoronoiGraph(Voronoi ov, int numLloydRelaxations, Random r) {
        Voronoi v = ov;

        bounds = v.getPlotBounds();
        for (int i = 0; i < numLloydRelaxations; i++) {
            List<Vector2d> points = v.siteCoords();
            for (Vector2d p : points) {
                List<Vector2d> region = v.region(p);
                double x = 0;
                double y = 0;
                for (Vector2d c : region) {
                    x += c.getX();
                    y += c.getY();
                }
                x /= region.size();
                y /= region.size();
                p.setX(x);
                p.setY(y);
            }
            v = new Voronoi(points, v.getPlotBounds());
        }
        buildGraph(v);
        improveCorners();
    }

    /**
     * Moving corners by averaging the nearby centers produces more uniform edge lengths,
     * although it occasionally worsens the polygon sizes. However, moving corners will
     * lose the Voronoi diagram properties.
     */
    private void improveCorners() {
        Vector2d[] newP = new Vector2d[corners.size()];
        int idx = 0;
        for (Corner c : corners) {
            if (c.isBorder()) {
                newP[idx] = c.getLocation();
            } else {
                double x = 0;
                double y = 0;
                for (Region region : c.getTouches()) {
                    x += region.getCenter().getX();
                    y += region.getCenter().getY();
                }
                newP[idx] = new Vector2d(x / c.getTouches().size(), y / c.getTouches().size());
            }
            idx++;
        }

        idx = 0;
        for (Corner c : corners) {
            c.setLocation(newP[idx++]);
        }
    }

    private static boolean liesOnAxes(Rect2d r, Vector2d p) {
        int diff = 1;
        return closeEnough(p.getX(), r.minX(), diff)
            || closeEnough(p.getY(), r.minY(), diff)
            || closeEnough(p.getX(), r.maxX(), diff)
            || closeEnough(p.getY(), r.maxY(), diff);
    }

    private static boolean closeEnough(double d1, double d2, double diff) {
        return Math.abs(d1 - d2) <= diff;
    }
    private void buildGraph(Voronoi v) {
        final Map<Vector2d, Region> pointCenterMap = new HashMap<>();
        final List<Vector2d> points = v.siteCoords();
        for (Vector2d p : points) {
            Region c = new Region(p);
            regions.add(c);
            pointCenterMap.put(p, c);
        }

        //bug fix
        for (Region c : regions) {
            v.region(c.getCenter());
        }

        final List<org.terasology.math.delaunay.Edge> libedges = v.edges();
        final Map<Integer, Corner> pointCornerMap = new HashMap<>();

        for (org.terasology.math.delaunay.Edge libedge : libedges) {
            final LineSegment vEdge = libedge.voronoiEdge();
            final LineSegment dEdge = libedge.delaunayLine();

            Corner c0 = makeCorner(pointCornerMap, vEdge.getP0());
            Corner c1 = makeCorner(pointCornerMap, vEdge.getP1());

            Region r0 = pointCenterMap.get(dEdge.getP0());
            Region r1 = pointCenterMap.get(dEdge.getP1());

            final Edge edge = new Edge(c0, c1, r0, r1);

            edges.add(edge);

            // Centers point to edges. Corners point to edges.

            if (r0 != null) {
                r0.addBorder(edge);
            }
            if (r1 != null) {
                r1.addBorder(edge);
            }
            if (edge.getCorner0() != null) {
                edge.getCorner0().addProtrudes(edge);
            }
            if (edge.getCorner1() != null) {
                edge.getCorner1().addProtrudes(edge);
            }

            // Centers point to centers.
            if (r0 != null && r1 != null) {
                addToCenterList(r0, r1);
                addToCenterList(r1, r0);
            }

            // Corners point to corners
            if (edge.getCorner0() != null && edge.getCorner1() != null) {
                addToCornerList(edge.getCorner0(), edge.getCorner1());
                addToCornerList(edge.getCorner1(), edge.getCorner0());
            }

            // Centers point to corners
            if (r0 != null) {
                addToCornerList(r0, edge.getCorner0());
                addToCornerList(r0, edge.getCorner1());
            }
            if (r1 != null) {
                addToCornerList(r1, edge.getCorner0());
                addToCornerList(r1, edge.getCorner1());
            }

            // Corners point to centers
            if (edge.getCorner0() != null) {
                addToCenterList(edge.getCorner0(), r0);
                addToCenterList(edge.getCorner0(), r1);
            }
            if (edge.getCorner1() != null) {
                addToCenterList(edge.getCorner1(), r0);
                addToCenterList(edge.getCorner1(), r1);
            }
        }

        // add corners
        for (Region region : regions) {
            boolean onLeft = false;
            boolean onRight = false;
            boolean onTop = false;
            boolean onBottom = false;

            int diff = 1;
            for (Corner corner : region.getCorners()) {
                Vector2d p = corner.getLocation();
                onLeft |= closeEnough(p.getX(), bounds.minX(), diff);
                onTop |= closeEnough(p.getY(), bounds.minY(), diff);
                onRight |= closeEnough(p.getX(), bounds.maxX(), diff);
                onBottom |= closeEnough(p.getY(), bounds.maxY(), diff);
            }

            if (onLeft && onTop) {
                Corner c = new Corner(new Vector2d(bounds.minX(), bounds.minY()));
                c.setBorder(true);
                corners.add(c);
                region.addCorner(c);
            }

            if (onLeft && onBottom) {
                Corner c = new Corner(new Vector2d(bounds.minX(), bounds.maxY()));
                c.setBorder(true);
                corners.add(c);
                region.addCorner(c);
            }

            if (onRight && onTop) {
                Corner c = new Corner(new Vector2d(bounds.maxX(), bounds.minY()));
                c.setBorder(true);
                corners.add(c);
                region.addCorner(c);
            }

            if (onRight && onBottom) {
                Corner c = new Corner(new Vector2d(bounds.maxX(), bounds.maxY()));
                c.setBorder(true);
                corners.add(c);
                region.addCorner(c);
            }

        }
    }

    private void addToCornerList(Corner corner, Corner c) {
        if (c != null && !corner.getAdjacent().contains(c)) {
            corner.addAdjacent(c);
        }
    }

    private void addToCornerList(Region region, Corner c) {
        if (c != null && !region.getCorners().contains(c)) {
            region.addCorner(c);
        }
    }

    private void addToCenterList(Region region, Region c) {
        if (c != null && !region.getNeighbors().contains(c)) {
            region.addNeigbor(c);
        }
    }

    private void addToCenterList(Corner v, Region c) {
        if (c != null && !v.getTouches().contains(c)) {
            v.addTouches(c);
        }
    }

    //ensures that each corner is represented by only one corner object
    private Corner makeCorner(Map<Integer, Corner> pointCornerMap, Vector2d p) {
        if (p == null) {
            return null;
        }
        int index = (int) ((int) p.getX() + (int) (p.getY()) * bounds.width() * 2);
        Corner c = pointCornerMap.get(index);
        if (c == null) {
            c = new Corner(p);
            c.setBorder(liesOnAxes(bounds, p));
            corners.add(c);
            pointCornerMap.put(index, c);
        }
        return c;
    }

    public Edge lookupEdgeFromCorner(Corner c, Corner downslope) {
        for (Edge e : c.getEdges()) {
            if (e.getCorner0() == downslope || e.getCorner1() == downslope) {
                return e;
            }
        }
        return null;
    }

    /**
     * @return
     */
    public List<Region> getRegions() {
        return Collections.unmodifiableList(regions);
    }

    /**
     * @return
     */
    public List<Edge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * @return the corners
     */
    public List<Corner> getCorners() {
        return Collections.unmodifiableList(corners);
    }

    /**
     * @return the bounds
     */
    public Rect2d getBounds() {
        return bounds;
    }
}