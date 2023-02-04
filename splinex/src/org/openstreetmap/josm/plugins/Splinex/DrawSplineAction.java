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
import org.openstreetmap.josm.command.MoveCommand;
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
import org.openstreetmap.josm.plugins.Splinex.Spline.SplinePoint;
import org.openstreetmap.josm.plugins.Splinex.algorithm.ClosestPoint;
import org.openstreetmap.josm.plugins.Splinex.command.*;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

@SuppressWarnings("serial")
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
    private final DrawSplineDataSetListener drawSplineDataSetListener = new DrawSplineDataSetListener();
    private final DrawSplineLayerManager drawSplineLayerManager = new DrawSplineLayerManager();

    public DrawSplineAction(MapFrame mapFrame) {
        super(tr("Spline drawing"), // name
                "spline2", // icon name
                tr("Draw a spline curve"), // tooltip
                Shortcut.registerShortcut("mapmode:spline",
                        tr("Mode: {0}", tr("Spline drawing")),
                        KeyEvent.VK_L, Shortcut.DIRECT),
                DrawSplineHelper.getCursor());

        drawSplineLayerManager.register();
        this.mapFrame = mapFrame;
        this.mapFrame.mapView.addTemporaryLayer(this);
        readPreferences();
    }

    @Override
    public void enterMode() {
        if (!isEnabled())
            return;
        super.enterMode();

        MainApplication.registerActionShortcut(backspaceAction, backspaceShortcut);

        mapFrame.mapView.addMouseListener(this);
        mapFrame.mapView.addMouseMotionListener(this);
        mapFrame.keyDetector.addModifierExListener(this);
        mapFrame.keyDetector.addKeyListener(this);
        drawSplineDataSetListener.register();
        DrawSplineHelper.createSplineFromSelection(drawSplineLayerManager.getSpline());
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
    protected EastNorth dragReference;
    protected boolean lockCounterpart;
    protected boolean lockCounterpartLength;
    protected MoveCommand moveCommand;
    protected boolean dragControl;
    protected boolean dragSpline;
    protected SplineHit splineHit;

    @Override
    public void mousePressed(MouseEvent e) {
        mouseDownTime = null;
        updateKeyModifiers(e);
        if (e.getButton() != MouseEvent.BUTTON1) {
            helperEndpoint = null; // Hide helper line when panning
            return;
        }
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        Spline spline = drawSplineLayerManager.getSpline();
        if (spline == null) return;
        helperEndpoint = null;
        dragControl = false;
        dragSpline = false;
        mouseDownTime = System.currentTimeMillis();
        pointHandle = spline.getNearestPoint(mapFrame.mapView, e.getPoint());
        clickPos = e.getPoint();
        dragReference = mapFrame.mapView.getEastNorth(clickPos.x, clickPos.y);
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

    @Override
    public void mouseReleased(MouseEvent e) {
        moveCommand = null;
        mouseDownTime = null;
        dragSpline = false;
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
        Spline spline = drawSplineLayerManager.getSpline();
        if (spline == null) return;
        if (spline.isEmpty()) return;
        if (clickPos != null && clickPos.distanceSq(e.getPoint()) < initialMoveThreshold)
            return;
        EastNorth en = mapFrame.mapView.getEastNorth(e.getX(), e.getY());
        if (new Node(en).isOutSideWorld())
            return;
        if (dragSpline) {
            DrawSplineHelper.dragSpline(splineHit, en, dragReference, alt);
            dragReference = en;
            MainApplication.getLayerManager().invalidateEditLayer();
            return;
        }
        clickPos = null;
        if (pointHandle == null) return;
        if (pointHandle.point == SplinePoint.ENDPOINT) {
            if (moveCommand == null) {
                moveCommand = new MoveCommand(pointHandle.sn.node, pointHandle.sn.node.getEastNorth(), en);
                UndoRedoHandler.getInstance().add(moveCommand);
            } else
                moveCommand.applyVectorTo(en);
        } else {
            if (dragControl) {
                UndoRedoHandler.getInstance().add(new EditSplineCommand(pointHandle.sn));
                dragControl = false;
            }
            pointHandle.movePoint(en);
            if (lockCounterpart) {
                pointHandle.moveCounterpart(lockCounterpartLength);
            }
        }
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    protected short direction;

    @Override
    public void mouseMoved(MouseEvent e) {
        updateKeyModifiers(e);
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        Spline spline = drawSplineLayerManager.getSpline();
        if (spline == null) return;
        Point oldHelperEndpoint = helperEndpoint;
        PointHandle oldPointHandle = pointHandle;
        boolean redraw = false;
        pointHandle = spline.getNearestPoint(mapFrame.mapView, e.getPoint());
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
            if (pointHandle.point == SplinePoint.ENDPOINT)
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
        if (!mapFrame.mapView.isActiveLayerDrawable())
            return;
        nodeHighlight.unset();
        helperEndpoint = null;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    protected void handleDoubleClick(Spline spline) {
        if (!spline.isClosed() && spline.nodeCount() > 1 && pointHandle != null && pointHandle.point == Spline.SplinePoint.ENDPOINT
            && ((pointHandle.idx == 0 && direction == 1) || (pointHandle.idx == spline.nodeCount() - 1 && direction == -1))) {
            UndoRedoHandler.getInstance().add(new CloseSplineCommand(spline));
        } else {
            spline.finishSpline();
        }
        direction = 0;
    }

    protected void handleClickOnPointHandle() {
        if (ctrl) {
            if (pointHandle.point == Spline.SplinePoint.ENDPOINT) {
                pointHandle = pointHandle.otherPoint(Spline.SplinePoint.CONTROL_NEXT);
                lockCounterpart = true;
                lockCounterpartLength = true;
            } else
                lockCounterpart = false;
        } else if (alt) {
            DrawSplineHelper.deleteSplineNode(pointHandle);
        } else if (pointHandle.point != Spline.SplinePoint.ENDPOINT) {
            lockCounterpart =
                // TODO handle turnover at north
                Math.abs(pointHandle.sn.cprev.heading(EastNorth.ZERO) - EastNorth.ZERO.heading(pointHandle.sn.cnext)) < 5/180.0*Math.PI;
            lockCounterpartLength =
                Math.min(pointHandle.sn.cprev.length(), pointHandle.sn.cnext.length()) /
                    Math.max(pointHandle.sn.cprev.length(), pointHandle.sn.cnext.length()) > 0.95;
        }
        if (pointHandle.point == Spline.SplinePoint.ENDPOINT && UndoRedoHandler.getInstance().hasUndoCommands()) {
            Command cmd = UndoRedoHandler.getInstance().getLastCommand();
            if (cmd instanceof MoveCommand) {
                moveCommand = (MoveCommand) cmd;
                Collection<Node> pp = moveCommand.getParticipatingPrimitives();
                if (pp.size() != 1 || !pp.contains(pointHandle.sn.node))
                    moveCommand = null;
                else
                    moveCommand.changeStartPoint(pointHandle.sn.node.getEastNorth());
            }
        }
        if (pointHandle.point != Spline.SplinePoint.ENDPOINT && UndoRedoHandler.getInstance().hasUndoCommands()) {
            Command cmd = UndoRedoHandler.getInstance().getLastCommand();
            if (!(cmd instanceof EditSplineCommand && ((EditSplineCommand) cmd).sNodeIs(pointHandle.sn)))
                dragControl = true;
        }
    }

    protected void handleClickOnSpline(Spline spline, MouseEvent e) {
        Optional<SplineHit> optionalSplineHit = ClosestPoint.findTime(e.getX(), e.getY(), spline, mapFrame.mapView);
        if (optionalSplineHit.isPresent()) {
            splineHit = optionalSplineHit.get();
            if (ctrl) {
                DrawSplineHelper.insertSplineNode(spline, splineHit);
            } else {
                DragSplineCommand.create(splineHit);
                dragSpline = true;
            }
        }
    }

    protected void handleClickOutsideSpline(Spline spline, MouseEvent e) {
        if (spline.isClosed())
            return;

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
        pointHandle = new PointHandle(spline, idx, direction == -1 ? SplinePoint.CONTROL_PREV : SplinePoint.CONTROL_NEXT);
        lockCounterpart = true;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void paint(Graphics2D graphics2D, MapView mapView, Bounds box) {
        Spline spl = drawSplineLayerManager.getSpline();
        if (spl == null)
            return;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        spl.paint(graphics2D, mapView, rubberLineColor, Color.green, helperEndpoint, direction);
        spl.paintProposedNodes(graphics2D, mapView);
        paintPointHandle(graphics2D, mapView);
    }

    protected void paintPointHandle(Graphics2D graphics2D, MapView mapView) {
        if (pointHandle != null && (pointHandle.point != SplinePoint.ENDPOINT || nodeHighlight.isNodeDeleted())) {
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
