// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.Splinex;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;

import javax.swing.AbstractAction;

import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.gui.util.ModifierExListener;
import org.openstreetmap.josm.plugins.Splinex.algorithm.ClosestPoint;
import org.openstreetmap.josm.plugins.Splinex.command.*;
import org.openstreetmap.josm.plugins.Splinex.listener.DatasetListener;
import org.openstreetmap.josm.plugins.Splinex.listener.LayerListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class DrawSplineAction extends MapMode implements MapViewPaintable, KeyPressReleaseListener, ModifierExListener {
    private final Cursor cursorJoinNode = ImageProvider.getCursor("crosshair", "joinnode");
    private final Cursor cursorJoinWay = ImageProvider.getCursor("crosshair", "joinway");

    private final Shortcut backspaceShortcut = Shortcut.registerShortcut(
        "mapmode:backspace",
        tr("Backspace in Add mode"),
        KeyEvent.VK_BACK_SPACE, Shortcut.DIRECT
    );
    private final BackSpaceAction backspaceAction = new BackSpaceAction();

    private final MapFrame mapFrame;
    private final NodeHighlight nodeHighlight = new NodeHighlight();
    private final DatasetListener dataSetListener = new DatasetListener();
    private final LayerListener layerListener = new LayerListener();

    public DrawSplineAction(MapFrame mapFrame) {
        super(tr("Spline drawing"), // name
                "spline2", // icon name
                tr("Draw a spline curve"), // tooltip
                Shortcut.registerShortcut("mapmode:spline",
                        tr("Mode: {0}", tr("Spline drawing")),
                        KeyEvent.VK_L, Shortcut.DIRECT),
                DrawSplineHelper.getCursor());

        layerListener.register();
        this.mapFrame = mapFrame;
        this.mapFrame.mapView.addTemporaryLayer(this);
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
        DrawSplineHelper.createSplineFromSelection(layerListener.getSpline());
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
    protected PointHandle pointHandle;
    protected Point helperEndpoint;
    protected Point clickPos;
    protected DragCommand commandOnDrag;

    @Override
    public void mousePressed(MouseEvent e) {
        mouseDownTime = null;
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
        pointHandle = spline.getNearestPointHandle(mapFrame.mapView, e.getPoint());
        clickPos = e.getPoint();
        if (e.getClickCount() == 2) {
            handleDoubleClick(spline);
        } else if (pointHandle != null) {
            handleClickOnPointHandle();
        } else if (spline.doesHit(e.getX(), e.getY(), mapFrame.mapView)) {
            handleClickOnSpline(spline, e);
        } else {
            handleClickOutsideSpline(spline, e);
        }
    }

    protected short direction;

    @Override
    public void mouseReleased(MouseEvent e) {
        mouseDownTime = null;
        clickPos = null;
        mouseMoved(e);
        if (direction == 0 && pointHandle != null && e.getClickCount() < 2) {
            if (pointHandle.idx >= pointHandle.getSpline().nodeCount() - 1)
                direction = 1;
            else if (pointHandle.idx == 0)
                direction = -1;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateKeyModifiers(e);
        if (mouseDownTime == null) return;
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        if (System.currentTimeMillis() - mouseDownTime < initialMoveDelay) return;
        Spline spline = layerListener.getSpline();
        if (spline == null || spline.isEmpty()) return;
        if (clickPos != null && clickPos.distanceSq(e.getPoint()) < initialMoveThreshold) return;
        clickPos = null;
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
        Spline spline = layerListener.getSpline();
        if (spline == null) return;
        Point oldHelperEndpoint = helperEndpoint;
        PointHandle oldPointHandle = pointHandle;
        boolean redraw = false;
        pointHandle = spline.getNearestPointHandle(mapFrame.mapView, e.getPoint());
        if (pointHandle == null)
            if (!ctrl && spline.doesHit(e.getX(), e.getY(), mapFrame.mapView)) {
                helperEndpoint = null;
                mapFrame.mapView.setNewCursor(Cursor.MOVE_CURSOR, this);
            } else {
                Node node = null;
                if (!ctrl)
                    node = mapFrame.mapView.getNearestNode(e.getPoint(), OsmPrimitive::isUsable);
                if (node == null) {
                    redraw = nodeHighlight.unset();
                    helperEndpoint = e.getPoint();
                    mapFrame.mapView.setNewCursor(cursor, this);
                } else {
                    redraw = nodeHighlight.set(node);
                    mapFrame.mapView.setNewCursor(cursorJoinNode, this);
                    helperEndpoint = mapFrame.mapView.getPoint(node);
                }
            }
        else {
            helperEndpoint = null;
            mapFrame.mapView.setNewCursor(cursorJoinWay, this);
            if (pointHandle.role == PointHandle.Role.NODE)
                redraw = nodeHighlight.set(pointHandle.sn.node);
            else
                redraw = nodeHighlight.unset();
        }
        if (!drawHelperLine || spline.isClosed() || direction == 0)
            helperEndpoint = null;

        if (redraw || oldHelperEndpoint != helperEndpoint || (oldPointHandle == null && pointHandle != null)
                || (oldPointHandle != null && !oldPointHandle.equals(pointHandle)))
            MainApplication.getLayerManager().invalidateEditLayer();
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

    protected void handleDoubleClick(Spline spline) {
        if (spline.isCloseable(pointHandle, direction)) {
            spline.close();
        } else {
            spline.finish();
        }
        direction = 0;
    }

    protected void handleClickOnPointHandle() {
        if (alt) {
            DrawSplineHelper.deleteSplineNode(pointHandle);
        } else if (pointHandle.role != PointHandle.Role.NODE) {
            commandOnDrag = new MoveSplinePointHandleCommand(pointHandle);
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
                EastNorth dragReference = mapFrame.mapView.getEastNorth(clickPos.x, clickPos.y);
                commandOnDrag = DragSplineCommand.create(splineHit, dragReference);
            }
        }
    }

    protected void handleClickOutsideSpline(Spline spline, MouseEvent e) {
        if (spline.isClosed()) return;
        if (direction == 0) {
            if (spline.nodeCount() < 2) {
                direction = 1;
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
        int idx = direction == -1 ? 0 : spline.nodeCount();
        UndoRedoHandler.getInstance().add(new AddSplineNodeCommand(spline, new SplineNode(node), existing, idx));
        pointHandle = new PointHandle(spline, idx, direction == -1 ? PointHandle.Role.CONTROL_PREV : PointHandle.Role.CONTROL_NEXT);
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void paint(Graphics2D graphics2D, MapView mapView, Bounds box) {
        Spline spline = layerListener.getSpline();
        if (spline == null) return;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        spline.paint(graphics2D, mapView, rubberLineColor, Color.green, helperEndpoint, direction);
        spline.paintProposedNodes(graphics2D, mapView);
        paintPointHandle(graphics2D, mapView);
    }

    protected void paintPointHandle(Graphics2D graphics2D, MapView mapView) {
        if (pointHandle != null && (pointHandle.role != PointHandle.Role.NODE || nodeHighlight.isNodeDeleted())) {
            graphics2D.setColor(MapPaintSettings.INSTANCE.getSelectedColor());
            Point point = mapView.getPoint(pointHandle.getPoint());
            graphics2D.fillRect(point.x - 1, point.y - 1, 3, 3);
        }
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
            DrawSplineHelper.deleteSplineNode(pointHandle);
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && direction != 0) {
            direction = 0;
            MainApplication.getLayerManager().invalidateEditLayer();
            e.consume();
        }
    }

    @Override
    public void doKeyReleased(KeyEvent e) {
    }

}
