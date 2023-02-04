package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
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

}
