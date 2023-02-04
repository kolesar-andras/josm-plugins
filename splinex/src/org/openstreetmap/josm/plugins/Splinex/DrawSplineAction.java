// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.Splinex;

import static java.lang.Math.pow;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

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
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.gui.util.ModifierExListener;
import org.openstreetmap.josm.plugins.Splinex.Spline.SplinePoint;
import org.openstreetmap.josm.plugins.Splinex.algorithm.ClosestPoint;
import org.openstreetmap.josm.plugins.Splinex.algorithm.Split;
import org.openstreetmap.josm.plugins.Splinex.command.*;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

@SuppressWarnings("serial")
public class DrawSplineAction extends MapMode implements MapViewPaintable, KeyPressReleaseListener, ModifierExListener,
        LayerChangeListener, ActiveLayerChangeListener {
    private final Cursor cursorJoinNode;
    private final Cursor cursorJoinWay;

    private Color rubberLineColor;

    private final Shortcut backspaceShortcut;
    private final BackSpaceAction backspaceAction;

    private final MapFrame mapFrame;
    private final NodeHighlight nodeHighlight = new NodeHighlight();
    private final DrawSplineDataSetListener drawSplineDataSetListener = new DrawSplineDataSetListener();

    boolean drawHelperLine;

    public DrawSplineAction(MapFrame mapFrame) {
        super(tr("Spline drawing"), // name
                "spline2", // icon name
                tr("Draw a spline curve"), // tooltip
                Shortcut.registerShortcut("mapmode:spline",
                        tr("Mode: {0}", tr("Spline drawing")),
                        KeyEvent.VK_L, Shortcut.DIRECT),
                DrawSplineHelper.getCursor());

        backspaceShortcut = Shortcut.registerShortcut("mapmode:backspace", tr("Backspace in Add mode"),
                KeyEvent.VK_BACK_SPACE, Shortcut.DIRECT);
        backspaceAction = new BackSpaceAction();
        cursorJoinNode = ImageProvider.getCursor("crosshair", "joinnode");
        cursorJoinWay = ImageProvider.getCursor("crosshair", "joinway");
        MainApplication.getLayerManager().addLayerChangeListener(this);
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);

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
        DrawSplineHelper.createSplineFromSelection(splCached);
    }

    int initialMoveDelay, initialMoveThreshold;

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
        drawSplineDataSetListener.unregister();

        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void modifiersExChanged(int modifiers) {
        updateKeyModifiersEx(modifiers);
    }

    private Long mouseDownTime;
    private PointHandle ph;
    private Point helperEndpoint;
    private Point clickPos;
    private EastNorth dragReference;
    boolean lockCounterpart;
    boolean lockCounterpartLength;
    private MoveCommand mc;
    private boolean dragControl;
    private boolean dragSpline;
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
        Spline spl = getSpline();
        if (spl == null) return;
        helperEndpoint = null;
        dragControl = false;
        dragSpline = false;
        mouseDownTime = System.currentTimeMillis();
        ph = spl.getNearestPoint(mapFrame.mapView, e.getPoint());
        if (e.getClickCount() == 2) {
            if (!spl.isClosed() && spl.nodeCount() > 1 && ph != null && ph.point == SplinePoint.ENDPOINT
                    && ((ph.idx == 0 && direction == 1) || (ph.idx == spl.nodeCount() - 1 && direction == -1))) {
                UndoRedoHandler.getInstance().add(new CloseSplineCommand(spl));
            } else {
                spl.finishSpline();
            }
            direction = 0;
            return;
        }
        clickPos = e.getPoint();
        dragReference = mapFrame.mapView.getEastNorth(clickPos.x, clickPos.y);
        if (ph != null) {
            if (ctrl) {
                if (ph.point == SplinePoint.ENDPOINT) {
                    ph = ph.otherPoint(SplinePoint.CONTROL_NEXT);
                    lockCounterpart = true;
                    lockCounterpartLength = true;
                } else
                    lockCounterpart = false;
            } else if (alt) {
                DrawSplineHelper.deleteSplineNode(ph);
            } else if (ph.point != SplinePoint.ENDPOINT) {
                lockCounterpart =
                        // TODO handle turnover at north
                        Math.abs(ph.sn.cprev.heading(EastNorth.ZERO) - EastNorth.ZERO.heading(ph.sn.cnext)) < 5/180.0*Math.PI;
                lockCounterpartLength =
                        Math.min(ph.sn.cprev.length(), ph.sn.cnext.length()) /
                        Math.max(ph.sn.cprev.length(), ph.sn.cnext.length()) > 0.95;
            }
            if (ph.point == SplinePoint.ENDPOINT && UndoRedoHandler.getInstance().hasUndoCommands()) {
                Command cmd = UndoRedoHandler.getInstance().getLastCommand();
                if (cmd instanceof MoveCommand) {
                    mc = (MoveCommand) cmd;
                    Collection<Node> pp = mc.getParticipatingPrimitives();
                    if (pp.size() != 1 || !pp.contains(ph.sn.node))
                        mc = null;
                    else
                        mc.changeStartPoint(ph.sn.node.getEastNorth());
                }
            }
            if (ph.point != SplinePoint.ENDPOINT && UndoRedoHandler.getInstance().hasUndoCommands()) {
                Command cmd = UndoRedoHandler.getInstance().getLastCommand();
                if (!(cmd instanceof EditSplineCommand && ((EditSplineCommand) cmd).sNodeIs(ph.sn)))
                    dragControl = true;
            }
            return;
        }
        if (spl.doesHit(e.getX(), e.getY(), mapFrame.mapView)) {
            Optional<SplineHit> optionalSplineHit = ClosestPoint.findTime(e.getX(), e.getY(), spl, mapFrame.mapView);
            if (optionalSplineHit.isPresent()) {
                splineHit = optionalSplineHit.get();
                if (ctrl) {
                    List<Command> cmds = new LinkedList<>();
                    Split.Result result = Split.split(splineHit);
                    Node node = new Node(result.a.pointB);
                    SplineNode splineNode = new SplineNode(
                        node,
                        result.a.ctrlB.subtract(result.a.pointB),
                        result.b.ctrlA.subtract(result.b.pointA)
                    );
                    cmds.add(new AddSplineNodeCommand(spl, splineNode, false, splineHit.index));
                    cmds.add(new EditSplineCommand(splineHit.splineNodeA));
                    cmds.add(new EditSplineCommand(splineHit.splineNodeB));
                    UndoRedoHandler.getInstance().add(new InsertSplineNodeCommand(cmds));
                    splineHit.splineNodeA.cnext = result.a.ctrlA.subtract(result.a.pointA);
                    splineHit.splineNodeB.cprev = result.b.ctrlB.subtract(result.b.pointB);
                } else {
                    DragSplineCommand.create(splineHit);
                    dragSpline = true;
                }
            }
            return;
        }
        if (spl.isClosed()) return;
        if (direction == 0) {
            if (spl.nodeCount() < 2) {
                direction = 1;
            } else {
                return;
            }
        }
        Node n = null;
        boolean existing = false;
        if (!ctrl) {
            n = mapFrame.mapView.getNearestNode(e.getPoint(), OsmPrimitive::isUsable);
            existing = true;
        }
        if (n == null) {
            n = new Node(mapFrame.mapView.getLatLon(e.getX(), e.getY()));
            existing = false;
        }
        int idx = direction == -1 ? 0 : spl.nodeCount();
        UndoRedoHandler.getInstance().add(new AddSplineNodeCommand(spl, new SplineNode(n), existing, idx));
        ph = new PointHandle(spl, idx, direction == -1 ? SplinePoint.CONTROL_PREV : SplinePoint.CONTROL_NEXT);
        lockCounterpart = true;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        mc = null;
        mouseDownTime = null;
        dragSpline = false;
        clickPos = null;
        mouseMoved(e);
        if (direction == 0 && ph != null && e.getClickCount() < 2) {
            if (ph.idx >= ph.getSpline().nodeCount() - 1)
                direction = 1;
            else if (ph.idx == 0)
                direction = -1;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateKeyModifiers(e);
        if (mouseDownTime == null) return;
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        if (System.currentTimeMillis() - mouseDownTime < initialMoveDelay) return;
        Spline spl = getSpline();
        if (spl == null) return;
        if (spl.isEmpty()) return;
        if (clickPos != null && clickPos.distanceSq(e.getPoint()) < initialMoveThreshold)
            return;
        EastNorth en = mapFrame.mapView.getEastNorth(e.getX(), e.getY());
        if (new Node(en).isOutSideWorld())
            return;
        if (dragSpline) {
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
            if (alt) {
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
            dragReference = en;
            MainApplication.getLayerManager().invalidateEditLayer();
            return;
        }
        clickPos = null;
        if (ph == null) return;
        if (ph.point == SplinePoint.ENDPOINT) {
            if (mc == null) {
                mc = new MoveCommand(ph.sn.node, ph.sn.node.getEastNorth(), en);
                UndoRedoHandler.getInstance().add(mc);
            } else
                mc.applyVectorTo(en);
        } else {
            if (dragControl) {
                UndoRedoHandler.getInstance().add(new EditSplineCommand(ph.sn));
                dragControl = false;
            }
            ph.movePoint(en);
            if (lockCounterpart) {
                ph.moveCounterpart(lockCounterpartLength);
            }
        }
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    short direction;

    @Override
    public void mouseMoved(MouseEvent e) {
        updateKeyModifiers(e);
        if (!mapFrame.mapView.isActiveLayerDrawable()) return;
        Spline spl = getSpline();
        if (spl == null) return;
        Point oldHelperEndpoint = helperEndpoint;
        PointHandle oldph = ph;
        boolean redraw = false;
        ph = spl.getNearestPoint(mapFrame.mapView, e.getPoint());
        if (ph == null)
            if (!ctrl && spl.doesHit(e.getX(), e.getY(), mapFrame.mapView)) {
                helperEndpoint = null;
                mapFrame.mapView.setNewCursor(Cursor.MOVE_CURSOR, this);
            } else {
                Node n = null;
                if (!ctrl)
                    n = mapFrame.mapView.getNearestNode(e.getPoint(), OsmPrimitive::isUsable);
                if (n == null) {
                    redraw = nodeHighlight.unset();
                    helperEndpoint = e.getPoint();
                    mapFrame.mapView.setNewCursor(cursor, this);
                } else {
                    redraw = nodeHighlight.set(n);
                    mapFrame.mapView.setNewCursor(cursorJoinNode, this);
                    helperEndpoint = mapFrame.mapView.getPoint(n);
                }
            }
        else {
            helperEndpoint = null;
            mapFrame.mapView.setNewCursor(cursorJoinWay, this);
            if (ph.point == SplinePoint.ENDPOINT)
                redraw = nodeHighlight.set(ph.sn.node);
            else
                redraw = nodeHighlight.unset();
        }
        if (!drawHelperLine || spl.isClosed() || direction == 0)
            helperEndpoint = null;

        if (redraw || oldHelperEndpoint != helperEndpoint || (oldph == null && ph != null)
                || (oldph != null && !oldph.equals(ph)))
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


    @Override
    public void paint(Graphics2D g, MapView mv, Bounds box) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Spline spl = getSpline();
        if (spl == null)
            return;
        spl.paint(g, mv, rubberLineColor, Color.green, helperEndpoint, direction);
        spl.paintProposedNodes(g, mv);
        if (ph != null && (ph.point != SplinePoint.ENDPOINT || (nodeHighlight.isNodeDeleted()))) {
            g.setColor(MapPaintSettings.INSTANCE.getSelectedColor());
            Point p = mv.getPoint(ph.getPoint());
            g.fillRect(p.x - 1, p.y - 1, 3, 3);
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

    private Spline splCached;

    Spline getSpline() {
        if (splCached != null)
            return splCached;
        Layer l = getLayerManager().getEditLayer();
        if (!(l instanceof OsmDataLayer))
            return null;
        splCached = layerSplines.get(l);
        if (splCached == null)
            splCached = new Spline();
        layerSplines.put(l, splCached);
        return splCached;
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        splCached = layerSplines.get(MainApplication.getLayerManager().getActiveLayer());
    }

    Map<Layer, Spline> layerSplines = new HashMap<>();

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        layerSplines.remove(e.getRemovedLayer());
        splCached = null;
    }

    @Override
    public void doKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE && ph != null) {
            DrawSplineHelper.deleteSplineNode(ph);
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
