// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.gui.util.ModifierExListener;
import org.openstreetmap.josm.plugins.Splinex.algorithm.ClosestPoint;
import org.openstreetmap.josm.plugins.Splinex.command.*;
import org.openstreetmap.josm.plugins.Splinex.listener.DatasetListener;
import org.openstreetmap.josm.plugins.Splinex.listener.LayerListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

public class DrawSplineAction extends MapMode implements KeyPressReleaseListener, ModifierExListener {
    private final Cursor cursorJoinNode = ImageProvider.getCursor("crosshair", "joinnode");
    private final Cursor cursorJoinWay = ImageProvider.getCursor("crosshair", "joinway");

    private final Shortcut backspaceShortcut = Shortcut.registerShortcut(
        "mapmode:backspace",
        tr("Backspace in Add mode"),
        KeyEvent.VK_BACK_SPACE, Shortcut.DIRECT
    );
    private final BackSpaceAction backspaceAction = new BackSpaceAction();

    protected final MapFrame mapFrame;
    protected final NodeHighlight nodeHighlight = new NodeHighlight();
    protected final DatasetListener dataSetListener = new DatasetListener();
    protected final LayerListener layerListener = new LayerListener();

    public DrawSplineAction(MapFrame mapFrame) {
        super(
            tr("Spline drawing"), // name
            "spline2", // icon name
            tr("Draw a spline curve"), // tooltip
            Shortcut.registerShortcut("mapmode:spline",
                tr("Mode: {0}", tr("Spline drawing")),
                KeyEvent.VK_L, Shortcut.DIRECT
            ),
            ImageProvider.getCursor("crosshair", "spline")
        );

        layerListener.register();
        this.mapFrame = mapFrame;
        readPreferences();
    }

    @Override
    public void enterMode() {
        if (!isEnabled()) return;
        super.enterMode();

        MainApplication.registerActionShortcut(backspaceAction, backspaceShortcut);

        mapFrame.mapView.addMouseListener(this);
        mapFrame.mapView.addMouseMotionListener(this);
        mapFrame.keyDetector.addModifierExListener(this);
        mapFrame.keyDetector.addKeyListener(this);
        dataSetListener.register();
        CreateSplineCommand.fromSelection(layerListener.getSpline());
    }

    protected Color rubberLineColor;
    protected int initialMoveDelay;
    protected int initialMoveThreshold;
    protected boolean drawHelperLine;

    @Override
    protected void readPreferences() {
        rubberLineColor = new NamedColorProperty(marktr("helper line"), Color.RED).get();
        initialMoveDelay = Config.getPref().getInt("edit.initial-move-", 200);
        initialMoveThreshold = Config.getPref().getInt("edit.initial-move-threshold", 5);
        initialMoveThreshold *= initialMoveThreshold;
        drawHelperLine = Config.getPref().getBoolean("draw.helper-line", true);
    }

    @Override
    public void exitMode() {
        super.exitMode();
        mapFrame.mapView.removeMouseListener(this);
        mapFrame.mapView.removeMouseMotionListener(this);
        MainApplication.unregisterActionShortcut(backspaceAction, backspaceShortcut);

        mapFrame.statusLine.activateAnglePanel(false);
        mapFrame.keyDetector.removeModifierExListener(this);
        mapFrame.keyDetector.removeKeyListener(this);
        nodeHighlight.unset();

        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void modifiersExChanged(int modifiers) {
        updateKeyModifiersEx(modifiers);
    }

    protected Long mouseDownTime;
    protected Point mouseDownPoint;
    protected PointHandle pointHandle;
    protected Point helperEndpoint;
    protected DragCommand commandOnDrag;

    @Override
    public void mousePressed(MouseEvent e) {
        updateKeyModifiers(e);
        if (e.getButton() != MouseEvent.BUTTON1) {
            helperEndpoint = null; // Hide helper line when panning
            return;
        }
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        Spline spline = layerListener.getSpline();
        if (spline == null) return;
        helperEndpoint = null;
        mouseDownTime = System.currentTimeMillis();
        mouseDownPoint = e.getPoint();
        pointHandle = spline.getNearestPointHandle(mapFrame.mapView, mouseDownPoint);
        if (pointHandle != null && e.getClickCount() != 2) {
            handleClickOnPointHandle();
        } else if (pointHandle == null && !spline.doesHit(e.getX(), e.getY(), mapFrame.mapView)) {
            handleClickOutsideSpline(spline, e);
        } else if (e.getClickCount() == 2) {
            handleDoubleClick(spline);
        } else {
            handleClickOnSpline(spline, e);
        }
    }

    enum Direction {
        NONE, FORWARD, BACKWARD
    }

    protected Direction direction = Direction.NONE;

    @Override
    public void mouseReleased(MouseEvent e) {
        mouseDownTime = null;
        mouseDownPoint = null;
        mouseMoved(e);
        if (direction == Direction.NONE && pointHandle != null && e.getClickCount() < 2) {
            if (pointHandle.idx >= pointHandle.getSpline().nodeCount() - 1)
                direction = Direction.FORWARD;
            else if (pointHandle.idx == 0)
                direction = Direction.BACKWARD;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateKeyModifiers(e);
        if (!isEnoughTimeAndMovement(e.getPoint())) return;
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        if (!layerListener.hasActiveSpline()) return;
        EastNorth en = mapFrame.mapView.getEastNorth(e.getX(), e.getY());
        if (new Node(en).isOutSideWorld()) return;
        if (commandOnDrag != null) {
            UndoRedoHandler.getInstance().add((Command) commandOnDrag);
            commandOnDrag = null;
        }
        Command cmd = UndoRedoHandler.getInstance().getLastCommand();
        if (cmd instanceof DragCommand) {
            DragCommand dragCommand = (DragCommand) cmd;
            dragCommand.dragTo(en, e);
            MainApplication.getLayerManager().invalidateEditLayer();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        updateKeyModifiers(e);
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        if (placeHelperEndpoint(e.getPoint())) {
            MainApplication.getLayerManager().invalidateEditLayer();
        }
    }

    /**
     * Repaint on mouse exit so that the helper line goes away.
     */
    @Override
    public void mouseExited(MouseEvent e) {
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        nodeHighlight.unset();
        helperEndpoint = null;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    protected boolean isEnoughTimeAndMovement(Point point) {
        if (mouseDownTime == null) return false;
        if (System.currentTimeMillis() - mouseDownTime < initialMoveDelay) return false;
        if (mouseDownPoint != null && mouseDownPoint.distanceSq(point) < initialMoveThreshold) return false;
        mouseDownPoint = null;
        return true;
    }

    protected void handleDoubleClick(Spline spline) {
        if (spline.isCloseable(pointHandle, direction)) {
            spline.close();
        } else {
            spline.finish();
        }
        direction = Direction.NONE;
    }

    protected void handleClickOnPointHandle() {
        if (alt && pointHandle.role == PointHandle.Role.NODE) {
            DeleteSplineNodeCommand.deleteSplineNode(pointHandle);
        } else if (alt) {
            EditSplineCommand.retractHandle(pointHandle);
        } else if (pointHandle.role != PointHandle.Role.NODE) {
            commandOnDrag = new MoveSplinePointHandleCommand(pointHandle, ctrl);
        } else if (ctrl) {
            pointHandle = pointHandle.getControlHandle();
            if (pointHandle != null) commandOnDrag = new MoveSplinePointHandleCommand(pointHandle, false);
        } else {
            commandOnDrag = new MoveSplineNodeCommand(pointHandle.sn.node);
        }
    }

    protected void handleClickOnSpline(Spline spline, MouseEvent e) {
        Optional<SplineHit> optionalSplineHit = ClosestPoint.findTime(e.getX(), e.getY(), spline, mapFrame.mapView);
        if (optionalSplineHit.isPresent()) {
            SplineHit splineHit = optionalSplineHit.get();
            if (ctrl) {
                spline.insertNode(splineHit);
            } else {
                EastNorth dragReference = mapFrame.mapView.getEastNorth(e.getX(), e.getY());
                commandOnDrag = DragSplineCommand.create(splineHit, dragReference);
            }
        }
    }

    protected void handleClickOutsideSpline(Spline spline, MouseEvent e) {
        if (spline.isClosed()) return;
        if (direction == Direction.NONE) {
            if (spline.nodeCount() < 2) {
                direction = Direction.FORWARD;
            } else {
                return;
            }
        }
        Node node = null;
        boolean existing = false;
        if (!ctrl) {
            node = mapFrame.mapView.getNearestNode(e.getPoint(), OsmPrimitive::isUsable);
            existing = true;
        }
        if (node == null) {
            node = new Node(mapFrame.mapView.getLatLon(e.getX(), e.getY()));
            existing = false;
        }
        int idx = direction == Direction.BACKWARD ? 0 : spline.nodeCount();
        UndoRedoHandler.getInstance().add(new AddSplineNodeCommand(spline, new SplineNode(node), existing, idx));
        pointHandle = new PointHandle(spline, idx, PointHandle.Role.matchingDirection(direction));
        commandOnDrag = new MoveSplinePointHandleCommand(pointHandle);
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    protected boolean placeHelperEndpoint(Point point) {
        Spline spline = layerListener.getSpline();
        if (spline == null) return false;
        boolean redraw = false;
        Point oldHelperEndpoint = helperEndpoint;
        PointHandle oldPointHandle = pointHandle;
        pointHandle = spline.getNearestPointHandle(mapFrame.mapView, point);
        if (pointHandle == null) {
            if (!ctrl && spline.doesHit(point.x, point.y, mapFrame.mapView)) {
                helperEndpoint = null;
                mapFrame.mapView.setNewCursor(Cursor.MOVE_CURSOR, this);
            } else {
                Node node = null;
                if (!ctrl)
                    node = mapFrame.mapView.getNearestNode(point, OsmPrimitive::isUsable);
                if (node == null) {
                    redraw = nodeHighlight.unset();
                    helperEndpoint = point;
                    mapFrame.mapView.setNewCursor(cursor, this);
                } else {
                    redraw = nodeHighlight.set(node);
                    mapFrame.mapView.setNewCursor(cursorJoinNode, this);
                    helperEndpoint = mapFrame.mapView.getPoint(node);
                }
            }
        } else {
            helperEndpoint = null;
            mapFrame.mapView.setNewCursor(cursorJoinWay, this);
            if (pointHandle.role == PointHandle.Role.NODE)
                redraw = nodeHighlight.set(pointHandle.sn.node);
            else
                redraw = nodeHighlight.unset();
        }
        if (!drawHelperLine || spline.isClosed() || direction == Direction.NONE) {
            helperEndpoint = null;
        }
        if (oldHelperEndpoint != helperEndpoint ||
            (oldPointHandle == null && pointHandle != null) ||
            (oldPointHandle != null && !oldPointHandle.equals(pointHandle))
        ) {
            redraw = true;
        }
        return redraw;
    }

    @Override
    public boolean layerIsSupported(Layer l) {
        return isEditableDataLayer(l);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditLayer() != null);
    }

    public static class BackSpaceAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            UndoRedoHandler.getInstance().undo();
        }
    }

    @Override
    public void doKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE && pointHandle != null) {
            DeleteSplineNodeCommand.deleteSplineNode(pointHandle);
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && direction != Direction.NONE) {
            direction = Direction.NONE;
            MainApplication.getLayerManager().invalidateEditLayer();
            e.consume();
        }
    }

    @Override
    public void doKeyReleased(KeyEvent e) {
    }

}
