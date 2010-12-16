package oseam.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import oseam.panels.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MapView.EditLayerChangeListener;
import org.openstreetmap.josm.gui.MapView.LayerChangeListener;

import oseam.Messages;
import oseam.OSeaM;
import oseam.seamarks.SeaMark;

public class OSeaMAction {

	private OSeaMAction dia = null;
	private PanelMain panelMain = null;

	private SeaMark mark = null;
	private Collection<? extends OsmPrimitive> Selection = null;
	private OsmPrimitive SelNode = null;

	public SelectionChangedListener SmpListener = new SelectionChangedListener() {
		public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
			Node node;
			Selection = newSelection;

System.out.println("SmpListener");
			for (OsmPrimitive osm : Selection) {
				if (osm instanceof Node) {
					node = (Node) osm;
					if (Selection.size() == 1)
						if (node.compareTo(SelNode) != 0) {
							SelNode = node;
System.out.println(node);
//							parseSeaMark();
//							mark.paintSign();
						}
				}
			}
			Selection = null;
		}
	};

	public OSeaMAction() {

		dia = this;
		String str = Main.pref.get("mappaint.style.sources");
		if (!str.contains("dev.openseamap.org")) {
			if (!str.isEmpty())
				str += new String(new char[] { 0x1e });
			Main.pref.put("mappaint.style.sources", str
					+ "http://dev.openseamap.org/josm/seamark_styles.xml");
		}
		str = Main.pref.get("color.background");
		if (str.equals("#000000") || str.isEmpty())
			Main.pref.put("color.background", "#606060");
System.out.println("newOSeaMAction");
	}

	public JPanel getOSeaMPanel() {
		if (panelMain == null) {
			panelMain = new PanelMain();
			panelMain.setLayout(null);
			panelMain.setSize(new Dimension(400, 360));
		}
		return panelMain;
	}

	private void parseSeaMark() {

		int nodes = 0;
		Node node = null;
		Collection<Node> selection = null;
		Map<String, String> keys;
		DataSet ds;

		ds = Main.main.getCurrentDataSet();
/*
		if (ds == null) {
			mark = new MarkUkn(this, Messages.getString("SmpDialogAction.26"));
			mark.setNode(null);
			return;
		}

		selection = ds.getSelectedNodes();
		nodes = selection.size();

		if (nodes == 0) {
			mark = new MarkUkn(this, Messages.getString("SmpDialogAction.27"));
			mark.setNode(null);
			return;
		}

		if (nodes > 1) {
			mark = new MarkUkn(this, Messages.getString("SmpDialogAction.28"));
			mark.setNode(null);
			return;
		}

		Iterator<Node> it = selection.iterator();
		node = it.next();

		cM01IconVisible.setEnabled(true);
		cM01IconVisible.setIcon(new ImageIcon(getClass().getResource(
				"/images/Auge.png"))); //$NON-NLS-1$

		cbM01TypeOfMark.setEnabled(true);

		String type = "";
		String str = "";

		keys = node.getKeys();

		if (keys.containsKey("seamark:type"))
			type = keys.get("seamark:type");
		
		if (type.equals("buoy_lateral") || type.equals("beacon_lateral")) {
			mark = new MarkLat(this, node);
			return;

		} else if (type.equals("buoy_cardinal") || type.equals("beacon_cardinal")) {
			mark = new MarkCard(this, node);
			return;

		} else if (type.equals("buoy_safe_water") || type.equals("beacon_safe_water")) {
			mark = new MarkSaw(this, node);
			return;

		} else if (type.equals("buoy_special_purpose") || type.equals("beacon_special_purpose")) {
			mark = new MarkSpec(this, node);
			return;

		} else if (type.equals("buoy_isolated_danger") || type.equals("beacon_isolated_danger")) {
			mark = new MarkIsol(this, node);
			return;

		} else if (type.equals("landmark") || type.equals("light_vessel")
				|| type.equals("light_major") || type.equals("light_minor")) {
			mark = new MarkNota(this, node);
			return;

		} else if (type.equals("light_float")) {
			if (keys.containsKey("seamark:light_float:colour")) {
				str = keys.get("seamark:light_float:colour");
				if (str.equals("red") || str.equals("green")
						|| str.equals("red;green;red") || str.equals("green;red;green")) {
					mark = new MarkLat(this, node);
					return;
				} else if (str.equals("black;yellow")
						|| str.equals("black;yellow;black") || str.equals("yellow;black")
						|| str.equals("yellow;black;yellow")) {
					mark = new MarkCard(this, node);
					return;
				} else if (str.equals("black;red;black")) {
					mark = new MarkIsol(this, node);
					return;
				} else if (str.equals("red;white")) {
					mark = new MarkSaw(this, node);
					return;
				} else if (str.equals("yellow")) {
					mark = new MarkSpec(this, node);
					return;
				}
			} else if (keys.containsKey("seamark:light_float:topmark:shape")) {
				str = keys.get("seamark:light_float:topmark:shape");
				if (str.equals("cylinder") || str.equals("cone, point up")) {
					mark = new MarkLat(this, node);
					return;
				}
			} else if (keys.containsKey("seamark:light_float:topmark:colour")) {
				str = keys.get("seamark:light_float:topmark:colour");
				if (str.equals("red") || str.equals("green")) {
					mark = new MarkLat(this, node);
					return;
				}
			}
		}

		if (keys.containsKey("buoy_lateral:category") || keys.containsKey("beacon_lateral:category")) {
			mark = new MarkLat(this, node);
			return;
		} else if (keys.containsKey("buoy_cardinal:category") || keys.containsKey("beacon_cardinal:category")) {
			mark = new MarkCard(this, node);
			return;
		} else if (keys.containsKey("buoy_isolated_danger:category") || keys.containsKey("beacon_isolated_danger:category")) {
			mark = new MarkIsol(this, node);
			return;
		} else if (keys.containsKey("buoy_safe_water:category") || keys.containsKey("beacon_safe_water:category")) {
			mark = new MarkSaw(this, node);
			return;
		} else if (keys.containsKey("buoy_special_purpose:category") || keys.containsKey("beacon_special_purpose:category")) {
			mark = new MarkSpec(this, node);
			return;
		}

		if (keys.containsKey("buoy_lateral:shape") || keys.containsKey("beacon_lateral:shape")) {
			mark = new MarkLat(this, node);
			return;
		} else if (keys.containsKey("buoy_cardinal:shape") || keys.containsKey("beacon_cardinal:shape")) {
			mark = new MarkCard(this, node);
			return;
		} else if (keys.containsKey("buoy_isolated_danger:shape") || keys.containsKey("beacon_isolated_danger:shape")) {
			mark = new MarkIsol(this, node);
			return;
		} else if (keys.containsKey("buoy_safe_water:shape") || keys.containsKey("beacon_safe_water:shape")) {
			mark = new MarkSaw(this, node);
			return;
		} else if (keys.containsKey("buoy_special_purpose:shape") || keys.containsKey("beacon_special_purpose:shape")) {
			mark = new MarkSpec(this, node);
			return;
		}

		if (keys.containsKey("buoy_lateral:colour") || keys.containsKey("beacon_lateral:colour")) {
			mark = new MarkLat(this, node);
			return;
		} else if (keys.containsKey("buoy_cardinal:colour") || keys.containsKey("beacon_cardinal:colour")) {
			mark = new MarkCard(this, node);
			return;
		} else if (keys.containsKey("buoy_isolated_danger:colour") || keys.containsKey("beacon_isolated_danger:colour")) {
			mark = new MarkIsol(this, node);
			return;
		} else if (keys.containsKey("buoy_safe_water:colour") || keys.containsKey("beacon_safe_water:colour")) {
			mark = new MarkSaw(this, node);
			return;
		} else if (keys.containsKey("buoy_special_purpose:colour") || keys.containsKey("beacon_special_purpose:colour")) {
			mark = new MarkSpec(this, node);
			return;
		}

		mark = new MarkUkn(this, Messages.getString("SmpDialogAction.91"));
		mark.setNode(node);
		mark.paintSign();
		return;
*/	}

}
