package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.algorithm.Split;
import org.openstreetmap.josm.plugins.Splinex.command.*;
import org.openstreetmap.josm.plugins.Splinex.importer.SchneiderImporter;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.pow;
import static org.openstreetmap.josm.gui.MainApplication.getLayerManager;

class DrawSplineHelper {
    static Cursor getCursor() {
        try {
            return ImageProvider.getCursor("crosshair", "spline");
        } catch (Exception e) {
            Logging.error(e);
        }
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    static void createSplineFromSelection(Spline target) {
        DataSet ds = getLayerManager().getEditDataSet();
        if (ds == null) return;
        Way way = ds.getLastSelectedWay();
        if (way == null) return;
        if (way.getNodesCount() < 3) return;
        Spline spline = SchneiderImporter.fromNodes(way.getNodes(), 0.5, way.isClosed());
        UndoRedoHandler.getInstance().add(new CreateSplineCommand(target, spline.nodes, false));
    }

    static void deleteSplineNode(PointHandle pointHandle) {
        Spline spl = pointHandle.getSpline();
        if (spl.isClosed() && spl.nodeCount() <= 3)
            return; // Don't allow to delete node when spline is closed and points are few
        UndoRedoHandler.getInstance().add(new DeleteSplineNodeCommand(spl, pointHandle.idx));
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    static void insertSplineNode(Spline spline, SplineHit splineHit) {
        List<Command> cmds = new LinkedList<>();
        Split.Result result = Split.split(splineHit);
        Node node = new Node(result.a.pointB);
        SplineNode splineNode = new SplineNode(
            node,
            result.a.ctrlB.subtract(result.a.pointB),
            result.b.ctrlA.subtract(result.b.pointA)
        );
        cmds.add(new AddSplineNodeCommand(spline, splineNode, false, splineHit.index));
        cmds.add(new EditSplineCommand(splineHit.splineNodeA));
        cmds.add(new EditSplineCommand(splineHit.splineNodeB));
        UndoRedoHandler.getInstance().add(new InsertSplineNodeCommand(cmds));
        splineHit.splineNodeA.cnext = result.a.ctrlA.subtract(result.a.pointA);
        splineHit.splineNodeB.cprev = result.b.ctrlB.subtract(result.b.pointB);
    }

    static void dragSpline(SplineHit splineHit, EastNorth en, EastNorth dragReference, boolean keepHandleDirection) {
        double t = splineHit.time;
        double weight;
        if (t <= 1.0 / 6.0) {
            weight = 0;
        } else if (t <= 0.5) {
            weight = pow((6 * t - 1) / 2.0, 3) / 2;
        } else if (t <= 5.0 / 6.0) {
            weight = (1 - pow((6 * (1 - t) - 1) / 2.0, 3)) / 2 + 0.5;
        } else {
            weight = 1;
        }
        EastNorth delta = en.subtract(dragReference);
        double scale0 = (1-weight)/(3*t*(1-t)*(1-t));
        double scale1 = weight/(3*t*t*(1-t));
        EastNorth offset0 = delta.scale(scale0);
        EastNorth offset1 = delta.scale(scale1);
        if (keepHandleDirection) {
            splineHit.splineNodeA.cnext = splineHit.splineNodeA.cnext.scale(
                splineHit.splineNodeA.cnext.add(offset0).length() /
                    splineHit.splineNodeA.cnext.length()
            );
            splineHit.splineNodeB.cprev = splineHit.splineNodeB.cprev.scale(
                splineHit.splineNodeB.cprev.add(offset1).length() /
                    splineHit.splineNodeB.cprev.length()
            );
        } else {
            splineHit.splineNodeA.cnext = splineHit.splineNodeA.cnext.add(offset0);
            splineHit.splineNodeB.cprev = splineHit.splineNodeB.cprev.add(offset1);
            splineHit.splineNodeA.cprev = PointHandle.computeCounterpart(splineHit.splineNodeA.cprev, splineHit.splineNodeA.cnext, false);
            splineHit.splineNodeB.cnext = PointHandle.computeCounterpart(splineHit.splineNodeB.cnext, splineHit.splineNodeB.cprev, false);
        }
    }

}
